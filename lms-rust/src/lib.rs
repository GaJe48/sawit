uniffi::setup_scaffolding!();

use std::{
    collections::HashMap,
    sync::{Arc, LazyLock},
};

use std::os::unix::io::FromRawFd;
use tokio::io::AsyncWriteExt;

use futures::{StreamExt, future::try_join_all};
use reqwest::Client;
use scraper::{ElementRef, Html, Selector};

#[derive(Debug, uniffi::Record)]
struct Student {
    name: String,
    npm: String,
    study: String,
    class_name: Option<String>,
    profile_picture_url: Option<String>,
}

#[derive(Debug, uniffi::Record)]
struct Course {
    code: String,
    name: String,
    day: String,
    clock: String,
    room: String,
    lecturer_name: String,
    lecturer_phone_number: Option<String>,
    lecturer_profile_picture_url: Option<String>,
}

#[derive(Debug, uniffi::Record)]
struct MeetingEntity {
    course_code: String,
    url: String,
    number: i8,
}

#[derive(Debug, uniffi::Record)]
struct AttendanceEntity {
    course_code: String,
    index: i8,
    is_attended: bool,
}

#[derive(Debug, uniffi::Record)]
struct ContentEntity {
    meeting_url: String,
    title: String,
    content_type: String,
    url: String,
}

#[derive(Debug, uniffi::Record)]
struct AssignmentEntity {
    meeting_url: String,
    url: String,
    message: Option<String>,
    question_url: Option<String>,
    answer_url: Option<String>,
    deadline: String,
    is_submitted: bool,
    is_overdue: bool,
}

#[derive(Debug, uniffi::Record)]
struct LmsEntity {
    student: Student,
    courses: Vec<Course>,
    meetings: Vec<MeetingEntity>,
    attendances: Vec<AttendanceEntity>,
    contents: Vec<ContentEntity>,
    assignments: Vec<AssignmentEntity>,
}

#[derive(uniffi::Object)]
struct InternetDataSource {
    web: Client,
    base_url: String,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
enum LmsError {
    #[error("Storage error: {msg}")]
    StorageError { msg: String },
    #[error("Network error: {msg}")]
    NetworkError { msg: String },
    #[error("Network status error ({status_code}): {msg}")]
    NetworkStatusError { status_code: u16, msg: String },
    #[error("Credential error: Invalid username or password")]
    CredentialError,
    #[error("Captcha error: Incorrect CAPTCHA answer")]
    CaptchaError,
    #[error("Captcha solver error: {msg}")]
    CaptchaSolverError { msg: String },
    #[error("Parser error: {msg}")]
    ParserError { msg: String },
}

impl From<reqwest::Error> for LmsError {
    fn from(err: reqwest::Error) -> Self {
        LmsError::NetworkError {
            msg: err.to_string(),
        }
    }
}

#[uniffi::export(callback_interface)]
#[async_trait::async_trait]
trait DownloadCallback: Send + Sync {
    async fn on_start(&self, file_name: String) -> Result<i32, LmsError>;
    fn on_progress(&self, file_name: String, progress: f32);
}

#[uniffi::export(callback_interface)]
trait UploadCallback: Send + Sync {
    fn on_progress(&self, file_name: String, progress: f32);
}

#[uniffi::export(async_runtime = "tokio")]
impl InternetDataSource {
    #[uniffi::constructor]
    fn new() -> Self {
        let root_store =
            rustls::RootCertStore::from_iter(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
        let tls_config = rustls::ClientConfig::builder()
            .with_root_certificates(root_store)
            .with_no_client_auth();

        let web = Client::builder()
            .use_preconfigured_tls(tls_config)
            .cookie_store(true)
            .build()
            .unwrap();

        let base_url = option_env!("LMS_BASE_URL")
            .unwrap_or("https://lms.unindra.ac.id")
            .to_string();

        Self { web, base_url }
    }

    async fn cookie_renewed(&self, nim: String, pwd: String) -> Result<(), LmsError> {
        static INPUT: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("[type=hidden]").unwrap());

        let html = self
            .web
            .get(format!("{}/login_new", self.base_url))
            .send()
            .await?
            .text()
            .await?;

        let (t_csrf, random_name, random_value) = {
            let document = Html::parse_document(&html);

            let mut inputs = document.select(&INPUT);

            let t = inputs
                .next()
                .and_then(|el| el.attr("value"))
                .ok_or_else(|| LmsError::ParserError {
                    msg: "CSRF token hidden input field not found".to_string(),
                })?
                .to_string();

            let (rn, rv) = inputs
                .next()
                .and_then(|el| {
                    let rn = el.attr("name")?.to_string();
                    let rv = el.attr("value")?.to_string();

                    Some((rn, rv))
                })
                .ok_or_else(|| LmsError::ParserError {
                    msg: "Random check hidden input field not found".to_string(),
                })?;

            (t, rn, rv)
        };

        let bytes = self
            .web
            .get(format!("{}/kapca", self.base_url))
            .send()
            .await?
            .bytes()
            .await?;

        let kapca_answer = solve_captcha(bytes).await?.to_string();

        let form_data = [
            ("csrf_token", t_csrf),
            (&random_name, random_value),
            ("username", nim),
            ("pswd", pwd),
            ("kapca", kapca_answer),
        ];

        let response = self
            .web
            .post(format!("{}/login_new", self.base_url))
            .form(&form_data)
            .send()
            .await?
            .text()
            .await?;

        if response.contains("Username atau password salah") {
            return Err(LmsError::CredentialError);
        }
        if response.contains("Jawaban Captcha Salah") {
            return Err(LmsError::CaptchaError);
        }

        Ok(())
    }

    async fn fetch_all(&self) -> Result<LmsEntity, LmsError> {
        let dashboard_html_future = async {
            self.web
                .get(format!("{}/member", self.base_url))
                .send()
                .await?
                .text()
                .await
        };

        let attendances_future = self.fetch_attendances();

        let (dashboard_raw_res, attendances_res) =
            tokio::join!(dashboard_html_future, attendances_future);
        let dashboard_raw = dashboard_raw_res?;
        let attendances = attendances_res?;

        let (student, mut courses, meetings) = Self::scrape_dashboard(dashboard_raw)?;

        let content_results = try_join_all(
            meetings
                .iter()
                .map(|meeting| self.fetch_contents(&meeting.url)),
        )
        .await?;

        let (suitcase_contents, regular_contents): (Vec<_>, Vec<_>) = content_results
            .into_iter()
            .flatten()
            .partition(|content| content.content_type == "fa-suitcase");

        let scrape_results = try_join_all(
            suitcase_contents
                .into_iter()
                .map(|assignment| self.scrape_assignment(assignment.meeting_url, assignment.url)),
        )
        .await?;

        let capacity = scrape_results.len();

        let (assignments, lecturer_map) = scrape_results.into_iter().fold(
            (Vec::with_capacity(capacity), HashMap::new()),
            |(mut assigs, mut map), (assignment, lecturer_opt)| {
                assigs.push(assignment);

                if let Some((name, pic_url)) = lecturer_opt {
                    map.insert(name, pic_url);
                }

                (assigs, map)
            },
        );

        for course in &mut courses {
            if course.lecturer_profile_picture_url.is_none() {
                course.lecturer_profile_picture_url =
                    lecturer_map.get(&course.lecturer_name).cloned();
            }
        }

        Ok(LmsEntity {
            student,
            courses,
            meetings,
            attendances,
            contents: regular_contents,
            assignments,
        })
    }

    async fn fetch_attendances_by_course(
        &self,
        course_code: String,
    ) -> Result<Vec<AttendanceEntity>, LmsError> {
        static ROW: LazyLock<Selector> = LazyLock::new(|| Selector::parse("tbody tr").unwrap());
        static CELL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("td:nth-child(n+2):nth-child(-n+3)").unwrap());
        static ATTEND: LazyLock<Selector> = LazyLock::new(|| Selector::parse(".fa").unwrap());

        let attend_html = self
            .web
            .get(format!("{}/presensi", self.base_url))
            .send()
            .await?
            .text()
            .await?;

        let (kode_jadwal_id, nim_id) = {
            let attend_parser = Html::parse_document(&attend_html);

            attend_parser
                .select(&ROW)
                .find_map(|row| {
                    let mut cells = row.select(&CELL);

                    let target_course = cells.next().and_then(|el| el.text().next())?.trim_ascii();

                    if target_course != course_code {
                        return None;
                    }

                    cells.next().and_then(|el| {
                        let mut parts = el.attr("onclick")?.split('\'');

                        let kode = parts.nth(1)?.to_string();
                        let nim = parts.nth(1)?.to_string();

                        Some((kode, nim))
                    })
                })
                .ok_or_else(|| LmsError::ParserError {
                    msg: format!(
                        "Course code '{}' not found in attendance table",
                        course_code
                    ),
                })?
        };

        let html = self
            .web
            .post(format!("{}/presensi/rekap_presensi_mhs", self.base_url))
            .form(&[("kd_jdw", kode_jadwal_id), ("nim", nim_id)])
            .send()
            .await?
            .text()
            .await?;

        let parser = Html::parse_document(&html);

        let list = (1i8..)
            .zip(parser.select(&ATTEND))
            .map(|(index, cell)| AttendanceEntity {
                course_code: course_code.to_string(),
                index,
                is_attended: cell.value().classes().any(|c| c == "fa-calendar-check-o"),
            })
            .collect::<Vec<_>>();

        Ok(list)
    }

    async fn fetch_assignment(
        &self,
        assignment_url: String,
        meeting_url: String,
    ) -> Result<AssignmentEntity, LmsError> {
        static MSG: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.callout-white-default p").unwrap());
        static QUESTION: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.callout-white-default a").unwrap());
        static DEADLINE: LazyLock<Selector> = LazyLock::new(|| {
            Selector::parse("div.callout-white-default tr:nth-child(3) td").unwrap()
        });
        static ANSWER: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.callout-white-warning a").unwrap());

        let html = self.web.get(&assignment_url).send().await?.text().await?;

        let document = Html::parse_document(&html);

        let message = document
            .select(&MSG)
            .next()
            .and_then(|el| el.text().next())
            .map(String::from);

        let question_url = document
            .select(&QUESTION)
            .next()
            .and_then(|el| extract_file_url(el));

        let deadline = document
            .select(&DEADLINE)
            .next()
            .and_then(|el| el.text().next())
            .ok_or_else(|| LmsError::ParserError {
                msg: "Assignment deadline text not found in page".to_string(),
            })?
            .to_string();

        let answer_url = document
            .select(&ANSWER)
            .next()
            .and_then(|el| extract_file_url(el));

        let is_submitted = html.contains("Sudah Submit");
        let is_overdue = html.contains("Waktu Submit sudah berakhir");

        Ok(AssignmentEntity {
            meeting_url: meeting_url,
            url: assignment_url,
            message,
            question_url,
            deadline,
            answer_url,
            is_submitted,
            is_overdue,
        })
    }

    async fn execute_attendances(&self, urls: Vec<String>) -> Result<(), LmsError> {
        let mut last_status = 0u16;
        for url in &urls {
            let response = self.web.get(url).send().await?;
            let status = response.status();

            if status.is_success() {
                return Ok(());
            }
            last_status = status.as_u16();
        }

        Err(LmsError::NetworkStatusError {
            status_code: last_status,
            msg: format!(
                "Failed to record attendance after trying {} URL(s).",
                urls.len()
            ),
        })
    }

    async fn download_file(
        &self,
        file_url: String,
        raw_file_name: String,
        callback: Box<dyn DownloadCallback>,
    ) -> Result<String, LmsError> {
        let mut response = self.web.get(&file_url).send().await?;

        if !response.status().is_success() {
            return Err(LmsError::NetworkStatusError {
                status_code: response.status().as_u16(),
                msg: format!("File is not available on the server at {}", file_url),
            });
        }

        let content_type_header = response
            .headers()
            .get("content-type")
            .and_then(|v| v.to_str().ok());

        let ext = content_type_header
            .and_then(|ct| {
                let base_type = ct.split(';').next()?.trim_ascii();
                mime_guess::get_mime_extensions_str(base_type)?.first()
            })
            .ok_or_else(|| LmsError::ParserError {
                msg: match content_type_header {
                    Some(ct) => format!("Unrecognized file format for Content-Type: '{}'", ct),
                    None => "Missing Content-Type header in server response".to_string(),
                },
            })?;

        let file_name = format!("{}.{}", raw_file_name, ext);

        let total_size = response
            .headers()
            .get("content-length")
            .and_then(|v| v.to_str().ok()?.parse::<u64>().ok())
            .unwrap_or(0);

        let raw_fd = callback.on_start(file_name.clone()).await?;

        let file = unsafe { std::fs::File::from_raw_fd(raw_fd) };
        let mut tokio_file = tokio::fs::File::from_std(file);

        let mut downloaded = 0u64;
        while let Some(chunk) = response.chunk().await? {
            tokio_file
                .write_all(&chunk)
                .await
                .map_err(|e| LmsError::StorageError {
                    msg: format!("Failed to write file chunk to disk: {}", e),
                })?;
            downloaded += chunk.len() as u64;
            if total_size > 0 {
                let progress = downloaded as f32 / total_size as f32;
                callback.on_progress(file_name.clone(), progress);
            }
        }

        tokio_file
            .sync_all()
            .await
            .map_err(|e| LmsError::StorageError {
                msg: format!("Failed to sync downloaded file to storage: {}", e),
            })?;

        callback.on_progress(file_name.clone(), 1.0);
        Ok(file_name)
    }

    async fn upload_submission(
        &self,
        assignment_url: String,
        file_name: String,
        file_size: u64,
        fd: i32,
        callback: Box<dyn UploadCallback>,
    ) -> Result<String, LmsError> {
        let html_form = self.web.get(&assignment_url).send().await?.text().await?;

        let (id_tugas, h_kode, id_aktifitas) = {
            static ID_TUGAS_SEL: LazyLock<Selector> =
                LazyLock::new(|| Selector::parse("input[name=h_id_tugas]").unwrap());
            static H_KODE_SEL: LazyLock<Selector> =
                LazyLock::new(|| Selector::parse("input[name=h_kode]").unwrap());
            static ID_AKTIFITAS_SEL: LazyLock<Selector> =
                LazyLock::new(|| Selector::parse("input[name=h_id_aktifitas]").unwrap());

            let document = Html::parse_document(&html_form);

            let id_tugas = document
                .select(&ID_TUGAS_SEL)
                .next()
                .and_then(|el| el.attr("value"))
                .ok_or(LmsError::ParserError {
                    msg: "Submission form payload 'h_id_tugas' not found in HTML".to_string(),
                })?
                .to_string();

            let h_kode = document
                .select(&H_KODE_SEL)
                .next()
                .and_then(|el| el.attr("value"))
                .ok_or(LmsError::ParserError {
                    msg: "Submission form payload 'h_kode' not found in HTML".to_string(),
                })?
                .to_string();

            let id_aktifitas = document
                .select(&ID_AKTIFITAS_SEL)
                .next()
                .and_then(|el| el.attr("value"))
                .ok_or(LmsError::ParserError {
                    msg: "Submission form payload 'h_id_aktifitas' not found in HTML".to_string(),
                })?
                .to_string();

            (id_tugas, h_kode, id_aktifitas)
        };

        let file = unsafe { std::fs::File::from_raw_fd(fd) };
        let tokio_file = tokio::fs::File::from_std(file);
        let reader_stream = tokio_util::io::ReaderStream::new(tokio_file);

        let callback_arc = Arc::new(callback);
        let stream_callback = Arc::clone(&callback_arc);
        let stream_file_name = file_name.clone();

        let mut uploaded: u64 = 0;

        let progress_stream = reader_stream.inspect(move |chunk| {
            if let Ok(bytes) = chunk {
                uploaded += bytes.len() as u64;

                if file_size > 0 {
                    let progress = (uploaded as f32) / (file_size as f32);
                    stream_callback.on_progress(stream_file_name.clone(), progress);
                }
            }
        });

        let body = reqwest::Body::wrap_stream(progress_stream);
        let part = reqwest::multipart::Part::stream_with_length(body, file_size)
            .file_name(file_name.clone());

        let form = reqwest::multipart::Form::new()
            .text("h_id_tugas", id_tugas)
            .text("h_kode", h_kode)
            .text("h_id_aktifitas", id_aktifitas)
            .part("myfile", part);

        let res = self
            .web
            .post("https://lms.unindra.ac.id/member_tugas/mhs_upload_file_proses")
            .multipart(form)
            .send()
            .await?;

        if res.status().is_success() {
            callback_arc.on_progress(file_name.clone(), 1.0);
            Ok(file_name)
        } else {
            Err(LmsError::NetworkStatusError {
                status_code: res.status().as_u16(),
                msg: "Failed to upload submission".to_string(),
            })
        }
    }
}

impl InternetDataSource {
    fn scrape_dashboard(
        dashboard_page: String,
    ) -> Result<(Student, Vec<Course>, Vec<MeetingEntity>), LmsError> {
        let dashboard_html = Html::parse_document(&dashboard_page);

        Ok((
            Self::parse_student(&dashboard_html)?,
            Self::parse_courses(&dashboard_html)?,
            Self::parse_meetings(&dashboard_html)?,
        ))
    }

    fn parse_student(dashboard_html: &Html) -> Result<Student, LmsError> {
        const NO_PIC_Z: &str = "Nk12TWFuRTNGbVdVMmk0S2ErU3EyNlk5SHovVTBzcjA2SVRMc3JjQXZPWE5jY0JKMzdXRDZlN1BtNlJaUGZNVTUvUVVyMngwNzVhdExrbTM1Vjl4b";

        static NAME: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse(".user-header p").unwrap());
        static NPM: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse(".user-body strong").unwrap());
        static STUDY: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse(".Badge-info").unwrap());
        static CLASS: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse(".pull-right.text-bold").unwrap());
        static PROFILE: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse(".user-menu .dropdown-toggle").unwrap());
        static IMG: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("img:nth-child(2)").unwrap());

        let raw_name = dashboard_html
            .select(&NAME)
            .next()
            .and_then(|el| el.text().next())
            .ok_or_else(|| LmsError::ParserError {
                msg: "Student name element not found in dashboard".to_string(),
            })?
            .trim_ascii();

        let name = format_title_case(raw_name);

        let npm = dashboard_html
            .select(&NPM)
            .next()
            .and_then(|el| el.text().next())
            .ok_or_else(|| LmsError::ParserError {
                msg: "Student NPM element not found in dashboard".to_string(),
            })?
            .to_string();

        let study = dashboard_html
            .select(&STUDY)
            .next()
            .and_then(|el| el.text().next())
            .ok_or_else(|| LmsError::ParserError {
                msg: "Student study program element not found in dashboard".to_string(),
            })?
            .trim_ascii()
            .to_string();

        let class_name = dashboard_html
            .select(&CLASS)
            .next()
            .and_then(|el| el.text().next())
            .map(String::from);

        let profile_picture_url = dashboard_html
            .select(&PROFILE)
            .next()
            .map(|el| el.inner_html().replace("<!--", "").replace("-->", ""))
            .and_then(|cleaned_html| {
                Html::parse_fragment(&cleaned_html)
                    .select(&IMG)
                    .next()
                    .and_then(|img| img.attr("src"))
                    .filter(|url| !url.contains(NO_PIC_Z))
                    .map(String::from)
            });

        Ok(Student {
            name,
            npm,
            study,
            class_name,
            profile_picture_url,
        })
    }

    fn parse_courses(dashboard_html: &Html) -> Result<Vec<Course>, LmsError> {
        const NO_PIC: &str = "UnMvTVFFTjJFWDFuYkkvSE1pWEhFMVBBRlFtRkpKQm9KeDNaQlZ1L0U3OTBXbDVhZUxQWmtDVkpYVDEwbFdaSg==";
        const NO_PIC_Z: &str = "Nk12TWFuRTNGbVdVMmk0S2ErU3EyNlk5SHovVTBzcjA2SVRMc3JjQXZPWE5jY0JKMzdXRDZlN1BtNlJaUGZNVTUvUVVyMngwNzVhdExrbTM1Vjl4b";

        static CARD: LazyLock<Selector> = LazyLock::new(|| Selector::parse(".box-widget").unwrap());
        static LECTURER: LazyLock<Selector> = LazyLock::new(|| Selector::parse("h3").unwrap());
        static HP: LazyLock<Selector> = LazyLock::new(|| Selector::parse("h5").unwrap());
        static INFO: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse(".isi_badge span").unwrap());
        static IMG: LazyLock<Selector> = LazyLock::new(|| Selector::parse("img").unwrap());

        dashboard_html
            .select(&CARD)
            .map(|el| {
                let raw_lecturer = el
                    .select(&LECTURER)
                    .next()
                    .and_then(|el| el.text().next())
                    .ok_or_else(|| LmsError::ParserError {
                        msg: "Lecturer name element not found in course card".to_string(),
                    })?;

                let lecturer_name = format_title_case(raw_lecturer);

                let lecturer_phone_number = el
                    .select(&HP)
                    .next()
                    .and_then(|el| el.text().next()?.strip_prefix("HP : "))
                    .filter(|s| !s.is_empty())
                    .map(String::from);

                let lecturer_profile_picture_url = el
                    .select(&IMG)
                    .next()
                    .and_then(|img| img.attr("src"))
                    .filter(|url| !url.ends_with(NO_PIC) && !url.contains(NO_PIC_Z))
                    .map(String::from);

                let mut spans = el.select(&INFO);

                let first_span = spans
                    .next()
                    .and_then(|el| el.text().next())
                    .ok_or_else(|| LmsError::ParserError {
                        msg: "Course header text (code and name) not found in course card".to_string(),
                    })?
                    .replace("\n                         ", "");

                let (code_raw, name_raw) = first_span.split_once(" -").ok_or_else(
                    || LmsError::ParserError {
                        msg: format!(
                            "Failed to split course code and name using separator ' -' from: '{}'",
                            first_span
                        ),
                    },
                )?;

                let code = code_raw.to_string();

                let name = match name_raw {
                    "Arsitektur dan Organisasi Komput" => {
                        "Arsitektur dan Organisasi Komputer".to_string()
                    }
                    other => other
                        .trim_end_matches(&[' ', '*', '#', ')'][..])
                        .to_string(),
                };

                let sec_span = spans
                    .next()
                    .and_then(|el| el.text().next())
                    .ok_or_else(|| LmsError::ParserError {
                        msg: "Course schedule text not found in course card".to_string(),
                    })?;

                let mut parts = sec_span.split('|').map(|s| s.trim_ascii());

                let room = parts
                    .nth(1)
                    .and_then(|el| el.strip_prefix("Ruang: "))
                    .ok_or_else(|| LmsError::ParserError {
                        msg: format!(
                            "Failed to parse room segment (expected format 'Ruang: <room_name>') from schedule string: '{}'",
                            sec_span
                        ),
                    })?
                    .to_string();

                let schedule_details = parts
                    .next()
                    .and_then(|el| el.strip_prefix("Waktu: "))
                    .ok_or_else(|| LmsError::ParserError {
                        msg: format!(
                            "Failed to parse schedule details segment (expected format 'Waktu: <day, clock>') from schedule string: '{}'",
                            sec_span
                        ),
                    })?;

                let (day_raw, clock_raw) =
                    schedule_details
                        .split_once(", ")
                        .ok_or_else(|| LmsError::ParserError {
                            msg: format!(
                                "Failed to split day and clock using ', ' from: '{}'",
                                schedule_details
                            ),
                        })?;

                let day = day_raw.to_string();
                let clock = clock_raw.to_string();

                Ok(Course {
                    code,
                    name,
                    day,
                    clock,
                    room,
                    lecturer_name,
                    lecturer_phone_number,
                    lecturer_profile_picture_url,
                })
            }).collect()
    }

    fn parse_meetings(dashboard_html: &Html) -> Result<Vec<MeetingEntity>, LmsError> {
        static TREE: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse(".treeview-menu").unwrap());
        static CARD: LazyLock<Selector> = LazyLock::new(|| Selector::parse(".box-widget").unwrap());
        static MEETING: LazyLock<Selector> = LazyLock::new(|| Selector::parse("a").unwrap());
        static COURSE_CODE: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("span.header_badeg").unwrap());

        let meetings = dashboard_html
            .select(&TREE)
            .zip(dashboard_html.select(&CARD))
            .map(|(tree, card)| {
                let course_code = card
                    .select(&COURSE_CODE)
                    .next()
                    .and_then(|el| el.text().next()?.split(" -").next())
                    .ok_or_else(|| LmsError::ParserError {
                        msg: "Failed to parse course code from badge text (expected format '<course_code> - <course_name>')".to_string(),
                    })?
                    .to_string();

                tree.select(&MEETING)
                    .map(|a_tag| {
                        let url = a_tag
                            .attr("href")
                            .ok_or_else(|| LmsError::ParserError {
                                msg: "Meeting link element 'a' is missing the 'href' attribute".to_string(),
                            })?
                            .to_string();

                        let number = a_tag
                            .text()
                            .nth(1)
                            .and_then(|s| s.strip_prefix("Pertemuan ")?.parse::<i8>().ok())
                            .ok_or_else(|| LmsError::ParserError {
                                msg: "Failed to parse meeting number from text nodes (expected second text node to be 'Pertemuan <number>')".to_string(),
                            })?;

                        Ok(MeetingEntity {
                            course_code: course_code.clone(),
                            url,
                            number,
                        })
                    })
                    .collect::<Result<Vec<_>, LmsError>>()
            })
            .collect::<Result<Vec<_>, LmsError>>()?;

        Ok(meetings.into_iter().flatten().collect())
    }

    async fn fetch_attendances(&self) -> Result<Vec<AttendanceEntity>, LmsError> {
        static ROW: LazyLock<Selector> = LazyLock::new(|| Selector::parse("tbody tr").unwrap());
        static CELL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("td:nth-child(n+2):nth-child(-n+3)").unwrap());
        static ATTEND: LazyLock<Selector> = LazyLock::new(|| Selector::parse(".fa").unwrap());

        let attend_html = self
            .web
            .get(format!("{}/presensi", self.base_url))
            .send()
            .await?
            .text()
            .await?;

        let futures = {
            let attend_parser = Html::parse_document(&attend_html);

            attend_parser
                .select(&ROW)
                .map(|row| {
                    let mut cells = row.select(&CELL);

                    let course_code = cells
                        .next()
                        .and_then(|el| el.text().next())
                        .ok_or_else(|| LmsError::ParserError {
                            msg: "Failed to parse course code from attendance table row".to_string(),
                        })?
                        .trim_ascii()
                        .to_string();

                    let (kode_jadwal_id, nim_id) = cells
                        .next()
                        .and_then(|el| {
                            let mut parts = el.attr("onclick")?.split('\'');

                            let kode = parts.nth(1)?.to_string();
                            let nim = parts.nth(1)?.to_string();

                            Some((kode, nim))
                        })
                        .ok_or_else(|| LmsError::ParserError {
                            msg: "Failed to parse course schedule ID and student NIM from row onclick attribute".to_string(),
                        })?;

                    let client = &self.web;

                    Ok(async move {
                        let html = client
                            .post(format!("{}/presensi/rekap_presensi_mhs", self.base_url))
                            .form(&[("kd_jdw", kode_jadwal_id), ("nim", nim_id)])
                            .send()
                            .await?
                            .text()
                            .await?;

                        let parser = Html::parse_document(&html);

                        let list = (1i8..)
                            .zip(parser.select(&ATTEND))
                            .map(|(index, cell)| AttendanceEntity {
                                course_code: course_code.clone(),
                                index,
                                is_attended: cell
                                    .value()
                                    .classes()
                                    .any(|c| c == "fa-calendar-check-o"),
                            })
                            .collect::<Vec<_>>();

                        Ok::<Vec<_>, LmsError>(list)
                    })
                })
                .collect::<Result<Vec<_>, LmsError>>()?
        };

        let results = try_join_all(futures).await?;

        Ok(results.into_iter().flatten().collect())
    }

    async fn fetch_contents(&self, meeting_url: &str) -> Result<Vec<ContentEntity>, LmsError> {
        static ROW: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("tr div:nth-child(1)").unwrap());
        static LINK: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("a:nth-child(2)").unwrap());
        static ICON: LazyLock<Selector> = LazyLock::new(|| Selector::parse("i").unwrap());

        let html = self.web.get(meeting_url).send().await?.text().await?;

        let futures = {
            let document = Html::parse_document(&html);

            document
                .select(&ROW)
                .map(|row| {
                    let a_tag = row
                        .select(&LINK)
                        .next()
                        .ok_or_else(|| LmsError::ParserError {
                            msg: "Link element ('a' tag) not found in content row".to_string(),
                        })?;

                    let link = a_tag
                        .attr("href")
                        .filter(|href| href.starts_with("http"))
                        .or_else(|| {
                            a_tag
                                .attr("onclick")
                                .and_then(|attr| attr.split('\'').nth(1))
                        })
                        .ok_or_else(|| LmsError::ParserError {
                            msg: "No valid link found in content row (missing 'http' in href and no 'onclick')".to_string(),
                        })?
                        .to_string();

                    let content_type = row
                        .select(&ICON)
                        .next()
                        .and_then(|el| el.value().classes().find(|c| c.starts_with("fa-")))
                        .unwrap_or_else(|| {
                            if link.contains("member_tugas") {
                                "fa-suitcase"
                            } else {
                                "fa-pdf"
                            }
                        })
                        .to_string();

                    let title = row
                        .text()
                        .nth(5)
                        .ok_or_else(|| LmsError::ParserError {
                            msg: "Failed to parse content title from row".to_string(),
                        })?
                        .trim_ascii()
                        .to_string();

                    let client = &self.web;
                    let meeting_url_str = meeting_url.to_string();

                    Ok(async move {
                        let real_link = if link.contains("member_url")
                        {
                            client.get(&link).send().await?.text().await?
                        } else {
                            link
                        };

                        Ok::<ContentEntity, LmsError>(ContentEntity {
                            meeting_url: meeting_url_str,
                            content_type,
                            title,
                            url: real_link,
                        })
                    })
                })
                .collect::<Result<Vec<_>, LmsError>>()?
        };

        let results = try_join_all(futures).await?;

        Ok(results)
    }

    async fn scrape_assignment(
        &self,
        fk_meeting_url: String,
        pk_assignment_url: String,
    ) -> Result<(AssignmentEntity, Option<(String, String)>), LmsError> {
        const POSTFIX_NO_PIC_A: &str = "dWRUTHJSbmpwZDlBYm4xMit2ckl1Vg==";

        static MSG: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.callout-white-default p").unwrap());
        static QUESTION: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.callout-white-default a").unwrap());
        static DEADLINE: LazyLock<Selector> = LazyLock::new(|| {
            Selector::parse("div.callout-white-default tr:nth-child(3) td").unwrap()
        });
        static ANSWER: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.callout-white-warning a").unwrap());
        static NAME: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse(".user-block a").unwrap());
        static IMAGE: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse(".user-block img").unwrap());

        let html = self
            .web
            .get(&pk_assignment_url)
            .send()
            .await?
            .text()
            .await?;

        let document = Html::parse_document(&html);

        let message = document
            .select(&MSG)
            .next()
            .and_then(|el| el.text().next())
            .map(String::from);

        let question_url = document
            .select(&QUESTION)
            .next()
            .and_then(|el| extract_file_url(el));

        let deadline = document
            .select(&DEADLINE)
            .next()
            .and_then(|el| el.text().next())
            .ok_or_else(|| LmsError::ParserError {
                msg: "Assignment deadline text not found in page".to_string(),
            })?
            .to_string();

        let answer_url = document
            .select(&ANSWER)
            .next()
            .and_then(|el| extract_file_url(el));

        let is_submitted = html.contains("Sudah Submit");
        let is_overdue = html.contains("Waktu Submit sudah berakhir");

        let name = document
            .select(&NAME)
            .next()
            .and_then(|el| el.text().next()?.split(',').next())
            .map(String::from);

        let profile_picture_url = document
            .select(&IMAGE)
            .next()
            .and_then(|el| el.attr("src"))
            .filter(|url| !url.ends_with(POSTFIX_NO_PIC_A))
            .map(String::from);

        let lecturer_key_value = name.zip(profile_picture_url);

        Ok((
            AssignmentEntity {
                meeting_url: fk_meeting_url,
                url: pk_assignment_url,
                message,
                question_url,
                deadline,
                answer_url,
                is_submitted,
                is_overdue,
            },
            lecturer_key_value,
        ))
    }
}

static REC_MODEL: LazyLock<ocr_rs::RecModel> = LazyLock::new(|| {
    let model_bytes = include_bytes!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/assets/model/en_PP-OCRv5_mobile_rec_infer.mnn"
    ));
    let charset_bytes = include_bytes!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/assets/model/ppocr_keys_en.txt"
    ));

    ocr_rs::RecModel::from_bytes_with_charset(model_bytes, charset_bytes, None).unwrap()
});

async fn solve_captcha(bytes: bytes::Bytes) -> Result<u8, LmsError> {
    tokio::task::spawn_blocking(move || {
        let mut image =
            image::load_from_memory(&bytes).map_err(|e| LmsError::CaptchaSolverError {
                msg: format!("Failed to load captcha image from bytes: {}", e),
            })?;

        image = image.crop(47, 16, 48, 14);
        image = image.grayscale();
        image.invert();

        let raw_text =
            REC_MODEL
                .recognize_text(&image)
                .map_err(|e| LmsError::CaptchaSolverError {
                    msg: format!("OCR text recognition failed: {:?}", e),
                })?;

        let numbers: Vec<u8> = raw_text
            .split(|c: char| !c.is_ascii_digit())
            .filter_map(|s| s.parse::<u8>().ok())
            .collect();

        if numbers.len() >= 2 {
            Ok(numbers[0] + numbers[1])
        } else {
            Err(LmsError::CaptchaSolverError {
                msg: format!(
                    "OCR failed to extract two numbers from captcha. Raw OCR text: '{}'",
                    raw_text
                ),
            })
        }
    })
    .await
    .map_err(|e| LmsError::CaptchaSolverError {
        msg: format!("Captcha solving task failed to join: {}", e),
    })?
}

fn extract_file_url(a_tag: ElementRef) -> Option<String> {
    if let Some(onclick) = a_tag.attr("onclick") {
        for media_type in ["lihat_pdf", "lihat_gambar"] {
            if !onclick.starts_with(media_type) {
                continue;
            }

            let Some(id) = onclick.split('\'').nth(1) else {
                continue;
            };

            return Some(format!(
                "https://lms.unindra.ac.id/media_public/{}/{}",
                media_type, id
            ));
        }
    }

    a_tag
        .attr("href")
        .filter(|href| href.contains("force_download"))
        .map(String::from)
}

fn format_title_case(s: &str) -> String {
    let mut result = String::with_capacity(s.len());

    let mut capitalize_next = true;

    for c in s.chars() {
        if c.is_whitespace() {
            capitalize_next = true;
            result.push(c);
        } else if capitalize_next {
            result.push(c.to_ascii_uppercase());
            capitalize_next = false;
        } else {
            result.push(c.to_ascii_lowercase());
        }
    }

    result
}

#[cfg(test)]
mod tests {
    use super::*;

    async fn login_helper(internet_data_source: &InternetDataSource) {
        let nim = std::env::var("LMS_NPM").unwrap_or_else(|_| "dummy_npm".to_string());
        let pwd = std::env::var("LMS_PASSWORD").unwrap_or_else(|_| "dummy_password".to_string());
        let _ = internet_data_source.cookie_renewed(nim, pwd).await;
    }

    #[tokio::test]
    #[ignore = "membutuhkan file captcha dan model OCR; jalankan manual dengan --ignored --nocapture"]
    async fn solve_captcha_local() {
        let captcha =
            std::fs::read(concat!(env!("CARGO_MANIFEST_DIR"), "/assets/captcha.png")).unwrap();
        let captcha_bytes = bytes::Bytes::from(captcha);
        let answer = solve_captcha(captcha_bytes).await.unwrap();

        println!("captcha answer: {answer}");

        assert!(answer == 7);
    }

    #[tokio::test]
    #[ignore = "membutuhkan jaringan, captcha OCR, dan model OCR; jalankan manual dengan --ignored --nocapture"]
    async fn test_ocr_captcha_raw_output() {
        let client = reqwest::Client::builder().build().unwrap();

        println!("=== Running OCR Model 100 Times (fetching new captcha each iteration) ===");
        for i in 1..=100 {
            let start = std::time::Instant::now();
            let response = client
                .get("https://lms.unindra.ac.id/kapca")
                .send()
                .await
                .unwrap()
                .bytes()
                .await
                .unwrap();

            let mut image = image::load_from_memory(&response).unwrap();

            image = image.crop(47, 16, 48, 14);

            image = image.grayscale();

            image.invert();

            let raw_text = REC_MODEL.recognize_text(&image).unwrap();
            let duration = start.elapsed();
            let answer = solve_captcha(response).await.unwrap();
            println!(
                "Iteration {:02}: Raw OCR Output = {:?}, Answer = {:?}, Duration = {:?}",
                i, raw_text, answer, duration
            );

            let _ = image.save(concat!(env!("CARGO_MANIFEST_DIR"), "/apa.png"));
        }
        println!("=======================================================================");
    }

    #[tokio::test]
    #[ignore = "membutuhkan jaringan, captcha OCR, dan model OCR; jalankan manual dengan --ignored --nocapture"]
    async fn test_ocr_raw_output() {
        let client = reqwest::Client::builder().build().unwrap();

        let response = client
            .get("https://lms.unindra.ac.id/media_public/get_gambar/UnMvTVFFTjJFWDFuYkkvSE1pWEhFMVBBRlFtRkpKQm9KeDNaQlZ1L0U3OTBXbDVhZUxQWmtDVkpYVDEwbFdaSg==")
            .send()
            .await
            .unwrap()
            .bytes()
            .await
            .unwrap();

        let mut image = image::load_from_memory(&response).unwrap();

        image = image.grayscale();

        image.invert();

        let raw_text = REC_MODEL.recognize_text(&image).unwrap();
        println!("Answer: {:?}", raw_text);
    }

    #[tokio::test]
    #[ignore = "login ke LMS asli, membutuhkan jaringan, captcha OCR, dan kredensial di cookie_renewed"]
    async fn parse_student_with_cookie() {
        let internet_data_source = InternetDataSource::new();

        login_helper(&internet_data_source).await;

        let a = internet_data_source
            .web
            .get("https://lms.unindra.ac.id/member")
            .send()
            .await
            .unwrap()
            .text()
            .await
            .unwrap();

        let b = Html::parse_document(&a);

        let c = InternetDataSource::parse_student(&b);

        println!("{:#?}", c);
    }

    #[test]
    #[ignore = "membaca dashboard.html; jalankan manual dengan --ignored --nocapture"]
    fn parse_courses_with_local_dashboard_html() {
        let real_dashboard_html = include_str!("../assets/html/dashboard.html");

        let result =
            InternetDataSource::parse_courses(&Html::parse_document(real_dashboard_html)).unwrap();

        println!("parsed {} courses", result.len());
        for course in &result {
            println!("{course:#?}");
        }

        assert!(
            !result.is_empty(),
            "seharusnya ada minimal satu mata kuliah"
        );
        assert_eq!(result.len(), 8);
        assert!(
            result.iter().all(|course| !course.code.trim().is_empty()),
            "semua course_code harus terisi"
        );
        assert!(
            result.iter().all(|course| !course.name.trim().is_empty()),
            "semua course_name harus terisi"
        );
        assert!(
            result.iter().all(|course| !course.day.trim().is_empty()),
            "semua day harus terisi"
        );
        assert!(
            result.iter().all(|course| !course.clock.trim().is_empty()),
            "semua clock harus terisi"
        );
        assert!(
            result.iter().all(|course| !course.room.trim().is_empty()),
            "semua room harus terisi"
        );
        assert!(
            result
                .iter()
                .all(|course| !course.lecturer_name.trim().is_empty()),
            "semua lecturer_name harus terisi"
        );
        assert!(
            result
                .iter()
                .all(|course| course.lecturer_phone_number.as_deref() != Some("")),
            "lecturer_phone_number kalau Some tidak boleh kosong"
        );
        assert!(
            result
                .iter()
                .all(|course| course.lecturer_profile_picture_url.as_deref() != Some("https://lms.unindra.ac.id/media_public/get_gambar/UnMvTVFFTjJFWDFuYkkvSE1pWEhFMVBBRlFtRkpKQm9KeDNaQlZ1L0U3OTBXbDVhZUxQWmtDVkpYVDEwbFdaSg==") && course.lecturer_profile_picture_url.as_deref() != Some("https://lms.unindra.ac.id/media_public/get_gambar/Nk12TWFuRTNGbVdVMmk0S2ErU3EyNlk5SHovVTBzcjA2SVRMc3JjQXZPWE5jY0JKMzdXRDZlN1BtNlJaUGZNVTUvUVVyMngwNzVhdExrbTM1Vjl4bVc2eDRBUjRzSlZvdDlyK0NudUp5RnhRTXpjcm1DTm5vRVQvMkdGcldWSkw=")),
            "lecturer_profile_picture_url tidak boleh berisi gambar default / kosong"
        );

        assert_eq!(result[0].code, "KB43J435");
        assert_eq!(result[0].name, "Sistem Basis Data");
        assert_eq!(result[0].day, "Selasa");
        assert_eq!(result[0].clock, "14:30-16:10");
        assert_eq!(result[0].room, "R.5.5-2");
        assert_eq!(result[0].lecturer_name, "Eko Tri Asmoro");
        assert_eq!(result[0].lecturer_phone_number, None);

        assert_eq!(result[5].code, "KB43J425");
        assert_eq!(result[5].name, "Arsitektur dan Organisasi Komputer");
        assert_eq!(
            result[5].lecturer_phone_number.as_deref(),
            Some("085323454664")
        );
    }

    #[test]
    #[ignore = "membaca dump.html; jalankan manual dengan --ignored"]
    fn parse_meetings_with_local_dashboard_html() {
        let real_dashboard_html = include_str!("../assets/html/dashboard.html");

        let result =
            InternetDataSource::parse_meetings(&Html::parse_document(real_dashboard_html)).unwrap();

        for meeting in &result {
            println!(
                "course_code: {} -> {} ({})",
                meeting.course_code, meeting.number, meeting.url
            );
        }

        assert!(!result.is_empty(), "seharusnya ada minimal satu pertemuan");
        assert!(
            result.iter().all(|m| !m.course_code.trim().is_empty()),
            "semua course_code harus terisi"
        );
    }

    #[tokio::test(flavor = "multi_thread", worker_threads = 12)]
    #[ignore = "login ke LMS asli, membutuhkan jaringan, captcha OCR, dan kredensial di cookie_renewed"]
    async fn fetch_attendances_with_cookie() {
        let internet_data_source = InternetDataSource::new();

        login_helper(&internet_data_source).await;
        let result = internet_data_source.fetch_attendances().await.unwrap();

        let mut grouped: Vec<(String, Vec<bool>)> = Vec::new();
        for att in &result {
            if let Some(pos) = grouped
                .iter()
                .position(|(code, _)| code == &att.course_code)
            {
                grouped[pos].1.push(att.is_attended);
            } else {
                grouped.push((att.course_code.clone(), vec![att.is_attended]));
            }
        }

        println!("parsed {} attendance courses", grouped.len());
        for (course_code, attendances) in &grouped {
            println!(
                "{} -> {} attendances: {:?}",
                course_code,
                attendances.len(),
                attendances
            );
        }

        assert!(
            !result.is_empty(),
            "seharusnya ada minimal satu rekap presensi setelah login"
        );
        assert!(
            result
                .iter()
                .all(|attendance| !attendance.course_code.trim().is_empty()),
            "semua course_code harus terisi"
        );
    }

    #[tokio::test]
    #[ignore = "login ke LMS asli, membutuhkan jaringan, captcha OCR, dan kredensial di cookie_renewed"]
    async fn fetch_attendances_by_course_with_cookie() {
        let internet_data_source = InternetDataSource::new();

        login_helper(&internet_data_source).await;

        let result = internet_data_source
            .fetch_attendances_by_course("KB43J435".to_string())
            .await
            .unwrap();

        println!("parsed {} attendances for course KB43J435", result.len());

        for att in &result {
            println!("index: {}, attended: {}", att.index, att.is_attended);
        }

        assert!(
            !result.is_empty(),
            "seharusnya ada minimal satu rekap presensi mata kuliah setelah login"
        );
        assert!(
            result
                .iter()
                .all(|attendance| attendance.course_code == "KB43J435"),
            "course_code harus sesuai dengan yang di-request"
        );
    }

    #[tokio::test]
    #[ignore = "login ke LMS asli, membutuhkan jaringan, captcha OCR, dan kredensial di cookie_renewed"]
    async fn fetch_contents_with_cookie() {
        let internet_data_source = InternetDataSource::new();

        login_helper(&internet_data_source).await;

        let result = internet_data_source.fetch_contents("https://lms.unindra.ac.id/pertemuan/pke/ZGNaTjQ1ZTZpV0xOcWdBVU1vbjZoS2VSWkxseFp5djZUWjhORkZDdDRXST0=").await.unwrap();

        println!("parsed {} contents", result.len());
        for content in &result {
            println!(
                "{} | {} -> {}",
                content.content_type, content.title, content.url
            );
        }

        assert!(
            !result.is_empty(),
            "seharusnya ada minimal satu konten di dalam pertemuan"
        );
        assert!(
            result.iter().all(|c| !c.title.trim().is_empty()),
            "semua judul konten harus terisi"
        );
    }

    #[tokio::test]
    #[ignore = "login ke LMS asli, membutuhkan jaringan, captcha OCR, dan kredensial di cookie_renewed"]
    async fn fetch_assignments_with_cookie() {
        let internet_data_source = InternetDataSource::new();

        login_helper(&internet_data_source).await;

        let result = internet_data_source.scrape_assignment(
            "https://lms.unindra.ac.id/pertemuan/pke/ZGNaTjQ1ZTZpV0xOcWdBVU1vbjZoS2VSWkxseFp5djZUWjhORkZDdDRXST0=".to_string(),
            "https://lms.unindra.ac.id/member_tugas/kelas/ZGNaTjQ1ZTZpV0xOcWdBVU1vbjZoTGRJUzJxc3FQTDNIQ0thK0hmc3A4cUhSRVZOL0tiLy9ic09xWmJNM0VnRHc2anlhMnFDN0YzbVA0aDFWcnltRGwxMW04ZFBLNjF2MU9BdDU1OVBrcGc9".to_string()
        ).await.unwrap().0;

        println!("{:#?}", result);

        assert!(!result.deadline.is_empty(), "Deadline tidak boleh kosong");
        assert_eq!(
            result.url,
            "https://lms.unindra.ac.id/member_tugas/kelas/ZGNaTjQ1ZTZpV0xOcWdBVU1vbjZoTGRJUzJxc3FQTDNIQ0thK0hmc3A4cUhSRVZOL0tiLy9ic09xWmJNM0VnRHc2anlhMnFDN0YzbVA0aDFWcnltRGwxMW04ZFBLNjF2MU9BdDU1OVBrcGc9"
        );
    }
    #[tokio::test(flavor = "multi_thread", worker_threads = 12)]
    #[ignore = "login ke LMS asli, membutuhkan jaringan, captcha OCR, dan kredensial di cookie_renewed"]
    async fn fetch_all_with_cookie() {
        let internet_data_source = InternetDataSource::new();

        login_helper(&internet_data_source).await;

        let result = internet_data_source.fetch_all().await.unwrap();

        std::fs::write("target/lms_result.txt", format!("{:#?}", result))
            .expect("Gagal menulis ke file");
        println!("Data berhasil ditulis ke target/lms_result.txt");

        assert!(
            !result.courses.is_empty(),
            "Harusnya ada minimal satu mata kuliah"
        );
    }
}
