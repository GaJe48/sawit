uniffi::setup_scaffolding!();

#[cfg(target_os = "android")]
#[allow(non_snake_case)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_gaje48_lms_LmsApplication_initRustTls(
    mut unowned_env: jni::EnvUnowned,
    _class: jni::objects::JClass,
    context: jni::objects::JObject,
) {
    let _ = unowned_env.with_env(|env| -> Result<(), jni::errors::Error> {
        rustls_platform_verifier::android::init_with_env(env, context)
            .expect("Fatal: Gagal menginisialisasi rustls-platform-verifier dari Kotlin");
        Ok(())
    });
}

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
    study_program: String,
    class_name: String,
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

struct Lecturer {
    name: String,
    profile_picture_url: String,
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
    assign_file_url: Option<String>,
    view_submit_file_url: Option<String>,
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
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
enum LmsError {
    #[error("Storage error: {msg}")]
    StorageError { msg: String },
    #[error("Network error: {msg}")]
    NetworkError { msg: String },
    #[error("Credential error: Username atau password salah")]
    CredentialError,
    #[error("Captcha error: Jawaban Captcha Salah")]
    CaptchaError,
    #[error("Parser error: {msg}")]
    ParserError { msg: String },
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
        let web = Client::builder().cookie_store(true).build().unwrap();

        Self { web }
    }

    async fn fetch_all(&self) -> Result<LmsEntity, LmsError> {
        let dashboard_html_future = async {
            self.web
                .get("https://lms.unindra.ac.id/member")
                .send()
                .await
                .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
                .text()
                .await
                .map_err(|e| LmsError::NetworkError { msg: e.to_string() })
        };

        let attendances_future = self.fetch_attendances();

        let (dashboard_raw_res, attendances_res) =
            tokio::join!(dashboard_html_future, attendances_future);
        let dashboard_raw = dashboard_raw_res?;
        let attendances = attendances_res?;

        let (student, mut courses, meetings) = Self::scrape_dashboard(&dashboard_raw)?;

        let content_results = try_join_all(
            meetings
                .iter()
                .map(|meeting| self.fetch_contents(&meeting.url)),
        )
        .await?;

        let all_contents: Vec<ContentEntity> = content_results.into_iter().flatten().collect();

        let (suitcase_contents, regular_contents): (Vec<_>, Vec<_>) = all_contents
            .into_iter()
            .partition(|content| content.content_type == "fa-suitcase");

        let (assignments, lecturers): (Vec<AssignmentEntity>, Vec<Option<Lecturer>>) =
            try_join_all(suitcase_contents.iter().map(|assignment| {
                self.scrape_assignment(&assignment.meeting_url, &assignment.url)
            }))
            .await?
            .into_iter()
            .unzip();

        let lecturer_map: HashMap<String, String> = lecturers
            .into_iter()
            .flatten()
            .map(|lecturer| (lecturer.name, lecturer.profile_picture_url))
            .collect();

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

    async fn cookie_renewed(&self, nim: &str, pwd: &str) -> Result<(), LmsError> {
        let html = self
            .web
            .get("https://lms.unindra.ac.id/login_new")
            .send()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
            .text()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?;

        let (t_csrf, random_name, random_value) = {
            static INPUT_SEL: LazyLock<Selector> =
                LazyLock::new(|| Selector::parse("input[type=hidden]").unwrap());
            let document = Html::parse_document(&html);
            let mut inputs = document.select(&INPUT_SEL);

            let csrf_input = inputs.next().ok_or_else(|| LmsError::ParserError {
                msg: "CSRF token hidden input field not found".into(),
            })?;
            let t = csrf_input
                .attr("value")
                .ok_or_else(|| LmsError::ParserError {
                    msg: "CSRF token hidden input field is missing 'value' attribute".into(),
                })?
                .to_string();

            let random_check_input = inputs.next().ok_or_else(|| LmsError::ParserError {
                msg: "Random check hidden input field not found".into(),
            })?;
            let rn = random_check_input
                .attr("name")
                .ok_or_else(|| LmsError::ParserError {
                    msg: "Random check hidden input field is missing 'name' attribute".into(),
                })?
                .to_string();
            let rv = random_check_input
                .attr("value")
                .ok_or_else(|| LmsError::ParserError {
                    msg: "Random check hidden input field is missing 'value' attribute".into(),
                })?
                .to_string();

            (t, rn, rv)
        };

        let bytes = self
            .web
            .get("https://lms.unindra.ac.id/kapca")
            .send()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
            .bytes()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?;

        let kapca_answer = solve_captcha(bytes).await?;

        let form_data = [
            ("csrf_token", t_csrf),
            (&random_name, random_value),
            ("username", nim.to_string()),
            ("pswd", pwd.to_string()),
            ("kapca", kapca_answer),
        ];

        let response = self
            .web
            .post("https://lms.unindra.ac.id/login_new")
            .form(&form_data)
            .send()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
            .text()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?;

        if response.contains("Username atau password salah") {
            return Err(LmsError::CredentialError);
        }
        if response.contains("Jawaban Captcha Salah") {
            return Err(LmsError::CaptchaError);
        }

        Ok(())
    }

    async fn execute_attendances(&self, urls: Vec<String>) -> Result<(), LmsError> {
        let results = try_join_all(urls.into_iter().map(|url| async move {
            let res = self.web.get(&url).send().await;
            match res {
                Ok(response) if response.status().is_success() => Ok(Ok(())),
                Ok(response) => Ok(Err(format!("Gagal absensi, status: {}", response.status()))),
                Err(e) => Err(LmsError::NetworkError { msg: e.to_string() }),
            }
        }))
        .await?;

        let has_success = results.iter().any(|r| r.is_ok());
        if has_success || results.is_empty() {
            Ok(())
        } else {
            let err_msg = results
                .into_iter()
                .filter_map(|r| r.err())
                .collect::<Vec<_>>()
                .join("; ");
            Err(LmsError::NetworkError { msg: err_msg })
        }
    }

    async fn download_file(
        &self,
        file_url: String,
        raw_file_name: String,
        callback: Box<dyn DownloadCallback>,
    ) -> Result<String, LmsError> {
        let mut response = self
            .web
            .get(&file_url)
            .send()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?;

        if !response.status().is_success() {
            return Err(LmsError::NetworkError {
                msg: format!("File tidak ada di server ({})", response.status().as_u16()),
            });
        }

        let content_type = response
            .headers()
            .get("content-type")
            .and_then(|v| v.to_str().ok())
            .map(|s| s.split(';').next().unwrap_or(s).trim())
            .unwrap_or("application/octet-stream")
            .to_string();

        let ext = mime_guess::get_mime_extensions_str(&content_type)
            .and_then(|exts| exts.first())
            .unwrap_or(&"bin");

        let file_name = format!("{}.{}", raw_file_name, ext);

        let total_size = response
            .headers()
            .get("content-length")
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.parse::<u64>().ok())
            .unwrap_or(0);

        let raw_fd = callback.on_start(file_name.clone()).await?;

        let file = unsafe { std::fs::File::from_raw_fd(raw_fd) };
        let mut tokio_file = tokio::fs::File::from_std(file);

        let mut downloaded = 0u64;
        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
        {
            tokio_file
                .write_all(&chunk)
                .await
                .map_err(|e| LmsError::StorageError { msg: e.to_string() })?;
            downloaded += chunk.len() as u64;
            if total_size > 0 {
                let progress = downloaded as f32 / total_size as f32;
                callback.on_progress(file_name.clone(), progress);
            }
        }

        tokio_file
            .sync_all()
            .await
            .map_err(|e| LmsError::StorageError { msg: e.to_string() })?;

        callback.on_progress(file_name.clone(), 1.0);
        Ok(file_name)
    }

    async fn upload_submission(
        &self,
        task_url: String,
        file_name: String,
        file_size: u64,
        fd: i32,
        callback: Box<dyn UploadCallback>,
    ) -> Result<String, LmsError> {
        let html_form = self
            .web
            .get(&task_url)
            .send()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
            .text()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?;

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
                .ok_or(LmsError::NetworkError {
                    msg: "Payload form h_id_tugas tidak ditemukan".into(),
                })?
                .to_string();

            let h_kode = document
                .select(&H_KODE_SEL)
                .next()
                .and_then(|el| el.attr("value"))
                .ok_or(LmsError::NetworkError {
                    msg: "Payload form h_kode tidak ditemukan".into(),
                })?
                .to_string();

            let id_aktifitas = document
                .select(&ID_AKTIFITAS_SEL)
                .next()
                .and_then(|el| el.attr("value"))
                .ok_or(LmsError::NetworkError {
                    msg: "Payload form h_id_aktifitas tidak ditemukan".into(),
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
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?;

        if res.status().is_success() {
            callback_arc.on_progress(file_name.clone(), 1.0);
            Ok(file_name)
        } else {
            Err(LmsError::NetworkError {
                msg: format!("Upload gagal, status: {}", res.status()),
            })
        }
    }
}

impl InternetDataSource {
    fn scrape_dashboard(
        dashboard_page: &str,
    ) -> Result<(Student, Vec<Course>, Vec<MeetingEntity>), LmsError> {
        let dashboard_html = Html::parse_document(dashboard_page);

        Ok((
            Self::parse_student(&dashboard_html)?,
            Self::parse_courses(&dashboard_html)?,
            Self::parse_meetings(&dashboard_html)?,
        ))
    }

    fn parse_student(dashboard_html: &Html) -> Result<Student, LmsError> {
        let base64_no_pic_z = "Nk12TWFuRTNGbVdVMmk0S2ErU3EyNlk5SHovVTBzcjA2SVRMc3JjQXZPWE5jY0JKMzdXRDZlN1BtNlJaUGZNVTUvUVVyMngwNzVhdExrbTM1Vjl4b";

        static NAME_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.pull-left.info p").unwrap());
        static NPM_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("li.user-body strong").unwrap());
        static PROGRAM_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("span.Badge-info").unwrap());
        static CLASS_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("span.pull-right.text-bold.badge").unwrap());
        static TOGGLE_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("li.user-menu a.dropdown-toggle").unwrap());
        static IMG_SEL: LazyLock<Selector> = LazyLock::new(|| Selector::parse("img").unwrap());

        let raw_name = dashboard_html
            .select(&NAME_SEL)
            .next()
            .and_then(|el| el.text().next())
            .ok_or_else(|| LmsError::ParserError {
                msg: "Student name not found".into(),
            })?;

        let name = format_title_case(raw_name);

        let npm = dashboard_html
            .select(&NPM_SEL)
            .next()
            .and_then(|el| el.text().next())
            .ok_or_else(|| LmsError::ParserError {
                msg: "Student NPM not found".into(),
            })?
            .to_string();

        let study_program = dashboard_html
            .select(&PROGRAM_SEL)
            .next()
            .and_then(|el| el.text().next())
            .ok_or_else(|| LmsError::ParserError {
                msg: "Student study program not found".into(),
            })?
            .trim()
            .to_string();

        let class_name = dashboard_html
            .select(&CLASS_SEL)
            .next()
            .and_then(|el| el.text().next())
            .ok_or_else(|| LmsError::ParserError {
                msg: "Student class code not found".into(),
            })?
            .to_string();

        let wrap = dashboard_html
            .select(&TOGGLE_SEL)
            .next()
            .ok_or_else(|| LmsError::ParserError {
                msg: "Student user menu toggle not found".into(),
            })?
            .inner_html()
            .replace("<!--", "")
            .replace("-->", "");

        let frag = Html::parse_fragment(&wrap);

        let profile_picture_url = frag
            .select(&IMG_SEL)
            .nth(1)
            .and_then(|el| el.attr("src"))
            .filter(|url| !url.contains(base64_no_pic_z))
            .map(String::from);

        Ok(Student {
            name,
            npm,
            study_program,
            class_name,
            profile_picture_url,
        })
    }

    fn parse_courses(dashboard_html: &Html) -> Result<Vec<Course>, LmsError> {
        let base64_no_pic = "UnMvTVFFTjJFWDFuYkkvSE1pWEhFMVBBRlFtRkpKQm9KeDNaQlZ1L0U3OTBXbDVhZUxQWmtDVkpYVDEwbFdaSg==";
        let base64_no_pic_z = "Nk12TWFuRTNGbVdVMmk0S2ErU3EyNlk5SHovVTBzcjA2SVRMc3JjQXZPWE5jY0JKMzdXRDZlN1BtNlJaUGZNVTUvUVVyMngwNzVhdExrbTM1Vjl4b";

        static WIDGET_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.box-widget").unwrap());
        static LECTURER_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("h3.widget-user-username").unwrap());
        static HEADER_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("span.header_badeg").unwrap());
        static GREEN_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("span.text-green").unwrap());
        static DESC_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("h5.widget-user-desc").unwrap());
        static IMG_SEL: LazyLock<Selector> = LazyLock::new(|| Selector::parse("img").unwrap());

        dashboard_html.select(&WIDGET_SEL).map(|el| {
                let raw_lecturer = el
                    .select(&LECTURER_SEL)
                    .next()
                    .and_then(|el| el.text().next())
                    .ok_or_else(|| LmsError::ParserError {
                        msg: "Lecturer username text is empty".into(),
                    })?;

                let lecturer_name = format_title_case(raw_lecturer);

                let header_text = el
                    .select(&HEADER_SEL)
                    .next()
                    .and_then(|el| el.text().next())
                    .ok_or_else(|| LmsError::ParserError {
                        msg: "Course header text is empty".into(),
                    })?
                    .replace("\n                         ", "");

                let (course_code_raw, course_name_raw) = header_text.split_once(" -").ok_or_else(
                    || LmsError::ParserError {
                        msg: format!(
                            "Failed to split course code and name using separator ' -' from: '{}'",
                            header_text
                        ),
                    },
                )?;

                let code = course_code_raw.to_string();

                let name = match course_name_raw {
                    "Arsitektur dan Organisasi Komput" => {
                        "Arsitektur dan Organisasi Komputer".to_string()
                    }
                    other => other
                        .trim_end_matches(&[' ', '*', '#', ')'][..])
                        .to_string(),
                };

                let green_text = el
                    .select(&GREEN_SEL)
                    .next()
                    .and_then(|el| el.text().next())
                    .ok_or_else(|| LmsError::ParserError {
                        msg: "Course schedule text is empty".into(),
                    })?;

                let mut parts = green_text.split('|').map(|s| s.trim());

                let room = parts
                    .nth(1)
                    .and_then(|el| el.split_once(' '))
                    .ok_or_else(|| LmsError::ParserError {
                        msg: format!(
                            "Failed to parse room segment (expected format 'Ruang <room_name>') from schedule string: '{}'",
                            green_text
                        ),
                    })?
                    .1
                    .to_string();

                let schedule_details = parts
                    .next()
                    .and_then(|el| el.split_once(' '))
                    .ok_or_else(|| LmsError::ParserError {
                        msg: format!(
                            "Failed to parse schedule details segment (expected format 'Waktu <day, clock>') from schedule string: '{}'",
                            green_text
                        ),
                    })?
                    .1;

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

                let lecturer_phone_number = el
                    .select(&DESC_SEL)
                    .next()
                    .and_then(|el| el.text().next()?.split_once(" : "))
                    .map(|(_, hp)| hp)
                    .filter(|s| !s.is_empty())
                    .map(String::from);

                let lecturer_profile_picture_url = el
                    .select(&IMG_SEL)
                    .next()
                    .and_then(|img| img.attr("src"))
                    .filter(|url| !url.ends_with(base64_no_pic) && !url.contains(base64_no_pic_z))
                    .map(String::from);

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
        static TREE_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("ul.treeview-menu").unwrap());
        static WIDGET_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.box-widget").unwrap());
        static A_SEL: LazyLock<Selector> = LazyLock::new(|| Selector::parse("ul li a").unwrap());
        static BADGE_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("span.header_badeg").unwrap());

        let treeviews = dashboard_html.select(&TREE_SEL);
        let widgets = dashboard_html.select(&WIDGET_SEL);

        let mut meetings = Vec::new();

        for (tree, widget) in treeviews.zip(widgets) {
            let course_code = widget
                .select(&BADGE_SEL)
                .next()
                .and_then(|el| el.text().next()?.split_once(" -"))
                .ok_or_else(|| LmsError::ParserError { msg: "Failed to parse course code from badge text (expected format '<course_code> - <course_name>')".to_string() })?
                .0
                .to_string();

            for a_tag in tree.select(&A_SEL) {
                let url = a_tag
                    .attr("href")
                    .ok_or_else(|| LmsError::ParserError {
                        msg: "Meeting link element 'a' is missing the 'href' attribute".into(),
                    })?
                    .to_string();

                let number = a_tag
                    .text()
                    .nth(1)
                    .and_then(|s| s.split_once(' ')?.1.parse::<i8>().ok())
                    .ok_or_else(|| LmsError::ParserError { msg: "Failed to parse meeting number from text nodes (expected second text node to be 'Pertemuan <number>')".into() })?;

                meetings.push(MeetingEntity {
                    course_code: course_code.clone(),
                    url,
                    number,
                });
            }
        }
        Ok(meetings)
    }

    async fn fetch_attendances(&self) -> Result<Vec<AttendanceEntity>, LmsError> {
        static CLICK: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("td[onclick^=absensi]").unwrap());
        static ROW: LazyLock<Selector> = LazyLock::new(|| Selector::parse("tbody tr").unwrap());
        static CELL_CC: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("td:nth-child(2)").unwrap());
        static CELL_ATTEND: LazyLock<Selector> = LazyLock::new(|| Selector::parse(".fa").unwrap());

        let attend_html = self
            .web
            .get("https://lms.unindra.ac.id/presensi")
            .send()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
            .text()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?;

        let futures = {
            let attend_parser = Html::parse_document(&attend_html);

            let Some(nim_id) = attend_parser
                .select(&CLICK)
                .next()
                .and_then(|el| el.attr("onclick")?.split('\'').nth(3))
            else {
                return Ok(Vec::new());
            };

            attend_parser
                .select(&ROW)
                .map(|row| {
                    let course_code = row
                        .select(&CELL_CC)
                        .next()
                        .and_then(|el| el.text().next())
                        .ok_or_else(|| LmsError::ParserError {
                            msg: "Gagal memparsing kode mata kuliah".to_string(),
                        })?
                        .trim()
                        .to_string();

                    let kode_jadwal_id = row
                        .select(&CLICK)
                        .next()
                        .and_then(|el| el.attr("onclick")?.split('\'').nth(1))
                        .ok_or_else(|| LmsError::ParserError {
                            msg: "Gagal memparsing kode jadwal".to_string(),
                        })?
                        .to_string();

                    let client = &self.web;
                    let nim = nim_id.to_string();

                    Ok(async move {
                        let html = client
                            .post("https://lms.unindra.ac.id/presensi/rekap_presensi_mhs")
                            .form(&[("kd_jdw", kode_jadwal_id), ("nim", nim)])
                            .send()
                            .await
                            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
                            .text()
                            .await
                            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?;

                        let parser = Html::parse_document(&html);

                        let list = parser
                            .select(&CELL_ATTEND)
                            .enumerate()
                            .map(|(index, cell)| AttendanceEntity {
                                course_code: course_code.clone(),
                                index: index as i8 + 1,
                                is_attended: cell
                                    .value()
                                    .classes()
                                    .any(|c| c == "fa-calendar-check-o"),
                            })
                            .collect::<Vec<_>>();

                        Ok::<Vec<AttendanceEntity>, LmsError>(list)
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

        let html = self
            .web
            .get(meeting_url)
            .send()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
            .text()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?;

        let futures = {
            let document = Html::parse_document(&html);

            document
                .select(&ROW)
                .map(|row| {
                    let a_tag = row
                        .select(&LINK)
                        .next()
                        .ok_or_else(|| LmsError::ParserError {
                            msg: "Link element ('a' tag) not found in row".into(),
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
                            msg: "No valid link found (missing 'http' in href and no 'onclick')"
                                .into(),
                        })?
                        .to_string();

                    let content_type = row
                        .select(&ICON)
                        .next()
                        .and_then(|el| el.value().classes().find(|c| c.starts_with("fa-")))
                        .unwrap_or_else(|| {
                            if link.starts_with("https://lms.unindra.ac.id/member_tugas") {
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
                            msg: "Failed to parse content title".into(),
                        })?
                        .trim()
                        .to_string();

                    let client = &self.web;
                    let meeting_url_str = meeting_url.to_string();

                    Ok(async move {
                        let real_link = if link.starts_with("https://lms.unindra.ac.id/member_url")
                        {
                            client
                                .get(&link)
                                .send()
                                .await
                                .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
                                .text()
                                .await
                                .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
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
        fk_meeting_url: &str,
        pk_assignment_url: &str,
    ) -> Result<(AssignmentEntity, Option<Lecturer>), LmsError> {
        let html = self
            .web
            .get(pk_assignment_url)
            .send()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?
            .text()
            .await
            .map_err(|e| LmsError::NetworkError { msg: e.to_string() })?;

        let document = Html::parse_document(&html);

        static MSG_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div[style*=padding-left]").unwrap());
        static DEFAULT_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.callout-white-default").unwrap());
        static WARNING_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.callout-white-warning").unwrap());
        static DEADLINE_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.callout-white-default table tr td").unwrap());
        static USER_BLOCK_SEL: LazyLock<Selector> =
            LazyLock::new(|| Selector::parse("div.user-block").unwrap());
        static NAME_SEL: LazyLock<Selector> = LazyLock::new(|| Selector::parse("span").unwrap());
        static IMAGE_SEL: LazyLock<Selector> = LazyLock::new(|| Selector::parse("img").unwrap());

        let message = document
            .select(&MSG_SEL)
            .next()
            .and_then(|element| element.text().next())
            .map(String::from);

        let assign_file_url = document
            .select(&DEFAULT_SEL)
            .next()
            .and_then(|el| extract_file_url(el));

        let deadline = document
            .select(&DEADLINE_SEL)
            .nth(2)
            .and_then(|el| el.text().next())
            .ok_or_else(|| LmsError::ParserError {
                msg: "Deadline text not found".into(),
            })?
            .to_string();

        let view_submit_file_url = document
            .select(&WARNING_SEL)
            .next()
            .and_then(|el| extract_file_url(el));

        let is_submitted = html.contains("Sudah Submit");
        let is_expired = html.contains("Waktu Submit sudah berakhir");

        let lecturer_opt = document
            .select(&USER_BLOCK_SEL)
            .next()
            .and_then(|user_block| {
                let name = user_block
                    .select(&NAME_SEL)
                    .next()?
                    .text()
                    .next()?
                    .split(',')
                    .next()?
                    .to_string();

                let profile_picture_url = user_block
                    .select(&IMAGE_SEL)
                    .next()?
                    .attr("src")?
                    .to_string();

                Some(Lecturer {
                    name,
                    profile_picture_url,
                })
            });

        Ok((
            AssignmentEntity {
                meeting_url: fk_meeting_url.to_string(),
                url: pk_assignment_url.to_string(),
                message,
                assign_file_url,
                deadline,
                view_submit_file_url,
                is_submitted,
                is_overdue: is_expired,
            },
            lecturer_opt,
        ))
    }
}

static REC_MODEL: std::sync::LazyLock<ocr_rs::RecModel> = std::sync::LazyLock::new(|| {
    let model_bytes = include_bytes!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/en_PP-OCRv5_mobile_rec_infer.mnn"
    ));
    let charset_bytes = include_bytes!(concat!(env!("CARGO_MANIFEST_DIR"), "/ppocr_keys_en.txt"));

    ocr_rs::RecModel::from_bytes_with_charset(model_bytes, charset_bytes, None).unwrap()
});

async fn solve_captcha(bytes: bytes::Bytes) -> Result<String, LmsError> {
    tokio::task::spawn_blocking(move || {
        let image = image::load_from_memory(&bytes).map_err(|e| LmsError::ParserError {
            msg: format!("Failed to load captcha image: {}", e),
        })?;

        let raw_text = REC_MODEL
            .recognize_text(&image)
            .map_err(|e| LmsError::ParserError {
                msg: format!("OCR text recognition failed: {:?}", e),
            })?;

        let (val1, val2) = raw_text
            .split_once("+")
            .and_then(|(left, right)| {
                let left_val = left.trim().parse::<i8>().ok()?;
                let right_val = right
                    .split_once('=')
                    .map(|(r, _)| r)?
                    .trim()
                    .parse::<i8>()
                    .ok()?;
                Some((left_val, right_val))
            })
            .ok_or_else(|| LmsError::ParserError {
                msg: format!("Failed to parse captcha equation from: {}", raw_text),
            })?;

        Ok((val1 + val2).to_string())
    })
    .await
    .map_err(|e| LmsError::ParserError {
        msg: format!("Captcha solving task joined with error: {}", e),
    })?
}

fn extract_file_url(container: ElementRef) -> Option<String> {
    static PDF_SEL: LazyLock<Selector> =
        LazyLock::new(|| Selector::parse("a[onclick*=lihat_pdf]").unwrap());
    static PICT_SEL: LazyLock<Selector> =
        LazyLock::new(|| Selector::parse("a[onclick*=lihat_gambar]").unwrap());
    static OTHER_SEL: LazyLock<Selector> =
        LazyLock::new(|| Selector::parse("a[href*=force_download]").unwrap());

    // Cek PDF
    if let Some(id) = container.select(&PDF_SEL).next().and_then(|a| {
        let onclick = a.attr("onclick")?;
        onclick.split('\'').nth(1)
    }) {
        return Some(format!(
            "https://lms.unindra.ac.id/media_public/lihat_pdf/{}",
            id
        ));
    }

    // Cek Gambar
    if let Some(id) = container.select(&PICT_SEL).next().and_then(|a| {
        let onclick = a.attr("onclick")?;
        onclick.split('\'').nth(1)
    }) {
        return Some(format!(
            "https://lms.unindra.ac.id/media_public/lihat_gambar/{}",
            id
        ));
    }

    // Cek Lainnya
    if let Some(href) = container
        .select(&OTHER_SEL)
        .next()
        .and_then(|a| a.attr("href"))
    {
        return Some(href.to_string());
    }

    None
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

    #[tokio::test]
    #[ignore = "membutuhkan file captcha dan model OCR; jalankan manual dengan --ignored --nocapture"]
    async fn solve_captcha_local() {
        let captcha = std::fs::read(concat!(env!("CARGO_MANIFEST_DIR"), "/captcha.png")).unwrap();
        let captcha_bytes = bytes::Bytes::from(captcha);
        let answer = solve_captcha(captcha_bytes).await.unwrap();

        println!("captcha answer: {answer}");

        assert!(answer == "7");
    }

    #[tokio::test]
    #[ignore = "login ke LMS asli, membutuhkan jaringan, captcha OCR, dan kredensial di cookie_renewed"]
    async fn parse_student_with_cookie() {
        let internet_data_source = InternetDataSource::new();

        let _ = internet_data_source
            .cookie_renewed("202443500660", "GamerJeniusN")
            .await;

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
        let real_dashboard_html = include_str!("../html/dashboard.html");

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
        let real_dashboard_html = include_str!("../html/dashboard.html");

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

        let _ = internet_data_source
            .cookie_renewed("202443500660", "GamerJeniusN")
            .await;
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
    async fn fetch_contents_with_cookie() {
        let internet_data_source = InternetDataSource::new();

        let _ = internet_data_source
            .cookie_renewed("202443500660", "GamerJeniusN")
            .await;

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

        let _ = internet_data_source
            .cookie_renewed("202443500660", "GamerJeniusN")
            .await;

        let result = internet_data_source.scrape_assignment(
            "https://lms.unindra.ac.id/pertemuan/pke/ZGNaTjQ1ZTZpV0xOcWdBVU1vbjZoS2VSWkxseFp5djZUWjhORkZDdDRXST0=",
            "https://lms.unindra.ac.id/member_tugas/kelas/ZGNaTjQ1ZTZpV0xOcWdBVU1vbjZoTGRJUzJxc3FQTDNIQ0thK0hmc3A4cUhSRVZOL0tiLy9ic09xWmJNM0VnRHc2anlhMnFDN0YzbVA0aDFWcnltRGwxMW04ZFBLNjF2MU9BdDU1OVBrcGc9"
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

        let _ = internet_data_source
            .cookie_renewed("202443500660", "GamerJeniusN")
            .await;

        let result = internet_data_source.fetch_all().await.unwrap();

        std::fs::write("lms_result.txt", format!("{:#?}", result)).expect("Gagal menulis ke file");
        println!("Data berhasil ditulis ke lms_result.txt");

        assert!(
            !result.courses.is_empty(),
            "Harusnya ada minimal satu mata kuliah"
        );
    }
}
