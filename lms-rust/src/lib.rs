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

use std::{collections::HashMap, sync::Arc};

use std::os::unix::io::FromRawFd;
use tokio::io::AsyncWriteExt;

use futures::{StreamExt, future::join_all};
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

    async fn fetch_all(&self) -> LmsEntity {
        let dashboard_html_future = async {
            self.web
                .get("https://lms.unindra.ac.id/member")
                .send()
                .await
                .unwrap()
                .text()
                .await
                .unwrap()
        };

        let attendances_future = self.fetch_attendances();

        let (dashboard_raw, attendances) = tokio::join!(dashboard_html_future, attendances_future);

        let (student, mut courses, meetings) = Self::scrape_dashboard(&dashboard_raw);

        let mut content_futures = Vec::new();

        for meeting in &meetings {
            let future = self.fetch_contents(&meeting.url);
            content_futures.push(future);
        }

        let (suitcase_contents, regular_contents): (Vec<_>, Vec<_>) = join_all(content_futures)
            .await
            .into_iter()
            .flatten()
            .partition(|content| content.content_type == "fa-suitcase");

        let mut assignment_futures = Vec::new();

        for assignment in &suitcase_contents {
            let future = self.scrape_assignment(&assignment.meeting_url, &assignment.url);
            assignment_futures.push(future);
        }

        let (assignments, lecturers): (Vec<_>, Vec<_>) =
            join_all(assignment_futures).await.into_iter().unzip();

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

        LmsEntity {
            student,
            courses,
            meetings,
            attendances,
            contents: regular_contents,
            assignments,
        }
    }

    async fn cookie_renewed(&self, nim: &str, pwd: &str) {
        let html = self
            .web
            .get("https://lms.unindra.ac.id/login_new")
            .send()
            .await
            .unwrap()
            .text()
            .await
            .unwrap();

        let (t_csrf, random_name, random_value) = {
            let document = Html::parse_document(&html);
            let input_sel = Selector::parse("input[type=hidden]").unwrap();
            let mut inputs = document.select(&input_sel);

            let t = inputs.next().unwrap().attr("value").unwrap().to_string();
            let random_input = inputs.next().unwrap();
            let rn = random_input.attr("name").unwrap().to_string();
            let rv = random_input.attr("value").unwrap().to_string();

            (t, rn, rv)
        };

        let bytes = self
            .web
            .get("https://lms.unindra.ac.id/kapca")
            .send()
            .await
            .unwrap()
            .bytes()
            .await
            .unwrap();

        let kapca_answer = solve_captcha(bytes).await;

        let form_data = [
            ("csrf_token", t_csrf),
            (&random_name, random_value),
            ("username", nim.to_string()),
            ("pswd", pwd.to_string()),
            ("kapca", kapca_answer),
        ];

        let _response = self
            .web
            .post("https://lms.unindra.ac.id/login_new")
            .form(&form_data)
            .send()
            .await
            .unwrap();
    }

    async fn execute_attendances(&self, urls: Vec<String>) -> Vec<String> {
        let futures = urls.into_iter().map(|url| async move {
            let result = self.web.get(&url).send().await;

            match result {
                Ok(response) if response.status().is_success() => None,
                Ok(response) => Some(format!("Gagal absensi, status: {}", response.status())),
                Err(e) => Some(e.to_string()),
            }
        });

        join_all(futures).await.into_iter().flatten().collect()
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
            let document = Html::parse_document(&html_form);
            let id_tugas_sel = Selector::parse("input[name=h_id_tugas]").unwrap();
            let h_kode_sel = Selector::parse("input[name=h_kode]").unwrap();
            let id_aktifitas_sel = Selector::parse("input[name=h_id_aktifitas]").unwrap();

            let id_tugas = document
                .select(&id_tugas_sel)
                .next()
                .and_then(|el| el.attr("value"))
                .ok_or(LmsError::NetworkError {
                    msg: "Payload form h_id_tugas tidak ditemukan".into(),
                })?
                .to_string();

            let h_kode = document
                .select(&h_kode_sel)
                .next()
                .and_then(|el| el.attr("value"))
                .ok_or(LmsError::NetworkError {
                    msg: "Payload form h_kode tidak ditemukan".into(),
                })?
                .to_string();

            let id_aktifitas = document
                .select(&id_aktifitas_sel)
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
        let part = reqwest::multipart::Part::stream_with_length(body, file_size as u64)
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
    fn scrape_dashboard(dashboard_page: &str) -> (Student, Vec<Course>, Vec<MeetingEntity>) {
        let dashboard_html = Html::parse_document(dashboard_page);

        (
            Self::parse_student(&dashboard_html),
            Self::parse_courses(&dashboard_html),
            Self::parse_meetings(&dashboard_html),
        )
    }

    fn parse_student(dashboard_html: &Html) -> Student {
        let name_selector = Selector::parse("div.pull-left.info p").unwrap();
        let npm_selector = Selector::parse("li.user-body strong").unwrap();
        let program_selector = Selector::parse("span.Badge-info").unwrap();
        let class_selector = Selector::parse("span.pull-right.text-bold.badge").unwrap();
        let toggle_sel = Selector::parse("li.user-menu a.dropdown-toggle").unwrap();
        let img_sel = Selector::parse("img").unwrap();

        let base64_no_pic_z = "Nk12TWFuRTNGbVdVMmk0S2ErU3EyNlk5SHovVTBzcjA2SVRMc3JjQXZPWE5jY0JKMzdXRDZlN1BtNlJaUGZNVTUvUVVyMngwNzVhdExrbTM1Vjl4b";

        let raw_name = dashboard_html
            .select(&name_selector)
            .next()
            .unwrap()
            .text()
            .next()
            .unwrap();

        let npm = dashboard_html
            .select(&npm_selector)
            .next()
            .unwrap()
            .text()
            .next()
            .unwrap();

        let study_program = dashboard_html
            .select(&program_selector)
            .next()
            .unwrap()
            .text()
            .next()
            .unwrap()
            .trim();

        let class_code = dashboard_html
            .select(&class_selector)
            .next()
            .unwrap()
            .text()
            .next()
            .unwrap();

        let wrap = dashboard_html
            .select(&toggle_sel)
            .next()
            .unwrap()
            .inner_html()
            .replace("<!--", "")
            .replace("-->", "");

        let frag = Html::parse_fragment(&wrap);

        let pp = frag
            .select(&img_sel)
            .nth(1)
            .unwrap()
            .attr("src")
            .filter(|url| !url.contains(base64_no_pic_z));

        Student {
            name: format_title_case(raw_name),
            npm: npm.to_string(),
            study_program: study_program.to_string(),
            class_name: class_code.to_string(),
            profile_picture_url: pp.map(|s| s.to_string()),
        }
    }

    fn parse_courses(dashboard_html: &Html) -> Vec<Course> {
        let widget_sel = Selector::parse("div.box-widget").unwrap();
        let lecturer_sel = Selector::parse("h3.widget-user-username").unwrap();
        let header_sel = Selector::parse("span.header_badeg").unwrap();
        let green_sel = Selector::parse("span.text-green").unwrap();
        let desc_sel = Selector::parse("h5.widget-user-desc").unwrap();
        let img_sel = Selector::parse("img").unwrap();

        let base64_no_pic = "UnMvTVFFTjJFWDFuYkkvSE1pWEhFMVBBRlFtRkpKQm9KeDNaQlZ1L0U3OTBXbDVhZUxQWmtDVkpYVDEwbFdaSg==";
        let base64_no_pic_z = "Nk12TWFuRTNGbVdVMmk0S2ErU3EyNlk5SHovVTBzcjA2SVRMc3JjQXZPWE5jY0JKMzdXRDZlN1BtNlJaUGZNVTUvUVVyMngwNzVhdExrbTM1Vjl4b";

        dashboard_html
            .select(&widget_sel)
            .map(|el| {
                let raw_lecturer = el
                    .select(&lecturer_sel)
                    .next()
                    .unwrap()
                    .text()
                    .next()
                    .unwrap();

                let header_text = el
                    .select(&header_sel)
                    .next()
                    .unwrap()
                    .text()
                    .next()
                    .unwrap()
                    .replace("\n                         ", "");

                let (course_code, raw_course_name) = header_text.split_once(" -").unwrap();

                let course_name = match raw_course_name {
                    "Arsitektur dan Organisasi Komput" => "Arsitektur dan Organisasi Komputer",
                    other => other.trim_end_matches(&[' ', '*', '#', ')'][..]),
                };

                let green_text = el.select(&green_sel).next().unwrap().text().next().unwrap();

                let mut parts = green_text.split('|').map(|s| s.trim());
                let room = parts.nth(1).unwrap().split_once(" ").unwrap().1;
                let time_raw = parts.next().unwrap().split_once(" ").unwrap().1;

                let (day, clock) = time_raw.split_once(", ").unwrap();

                let lecturer_phone_number = el
                    .select(&desc_sel)
                    .next()
                    .unwrap()
                    .text()
                    .next()
                    .unwrap()
                    .split_once(" : ")
                    .map(|(_, hp)| hp)
                    .filter(|s| !s.is_empty());

                let lecturer_profile_picture_url = el
                    .select(&img_sel)
                    .next()
                    .unwrap()
                    .attr("src")
                    .filter(|url| !url.ends_with(base64_no_pic) && !url.contains(base64_no_pic_z));

                Course {
                    code: course_code.to_string(),
                    name: course_name.to_string(),
                    day: day.to_string(),
                    clock: clock.to_string(),
                    room: room.to_string(),
                    lecturer_name: format_title_case(raw_lecturer),
                    lecturer_phone_number: lecturer_phone_number.map(|s| s.to_string()),
                    lecturer_profile_picture_url: lecturer_profile_picture_url
                        .map(|s| s.to_string()),
                }
            })
            .collect::<Vec<_>>()
    }

    fn parse_meetings(dashboard_html: &Html) -> Vec<MeetingEntity> {
        let tree_sel = Selector::parse("ul.treeview-menu").unwrap();
        let widget_sel = Selector::parse("div.box-widget").unwrap();
        let a_sel = Selector::parse("ul li a").unwrap();
        let badge_sel = Selector::parse("span.header_badeg").unwrap();

        let treeviews = dashboard_html.select(&tree_sel);
        let widgets = dashboard_html.select(&widget_sel);

        treeviews
            .zip(widgets)
            .map(|(tree, widget)| {
                let course_code = widget
                    .select(&badge_sel)
                    .next()
                    .unwrap()
                    .text()
                    .next()
                    .unwrap()
                    .split_once(" -")
                    .unwrap()
                    .0;

                tree.select(&a_sel)
                    .map(|a_tag| {
                        let url = a_tag.attr("href").unwrap();

                        let meeting_number = a_tag
                            .text()
                            .nth(1)
                            .unwrap()
                            .split_once(" ")
                            .unwrap()
                            .1
                            .parse::<i8>()
                            .unwrap();

                        MeetingEntity {
                            course_code: course_code.to_string(),
                            url: url.to_string(),
                            number: meeting_number,
                        }
                    })
                    .collect::<Vec<_>>()
            })
            .flatten()
            .collect::<Vec<_>>()
    }

    async fn fetch_attendances(&self) -> Vec<AttendanceEntity> {
        let presence_html = self
            .web
            .get("https://lms.unindra.ac.id/presensi")
            .send()
            .await
            .unwrap()
            .text()
            .await
            .unwrap();

        let mut tasks = Vec::new();

        {
            let document = Html::parse_document(&presence_html);

            let Some(nim_id) = document
                .select(&Selector::parse("td[onclick*=absensi_mhs]").unwrap())
                .next()
                .map(|el| {
                    el.attr("onclick")
                        .unwrap()
                        .split('\'')
                        .nth(3)
                        .unwrap()
                        .to_string()
                })
            else {
                return Vec::new();
            };

            let nim_id_arc = Arc::new(nim_id);

            let td_sel = Selector::parse("td").unwrap();
            let row_sel_arc = Arc::new(Selector::parse("table.table-bordered tbody tr").unwrap());
            let td_center_sel_arc = Arc::new(Selector::parse("td.text-center").unwrap());
            let attended_sel_arc = Arc::new(Selector::parse("i.fa-calendar-check-o").unwrap());

            for row in document.select(&*row_sel_arc) {
                let mut cols = row.select(&td_sel);

                let course_code = cols
                    .nth(1)
                    .unwrap()
                    .text()
                    .next()
                    .unwrap()
                    .trim()
                    .to_string();

                let kode_jadwal_id = cols
                    .next()
                    .unwrap()
                    .attr("onclick")
                    .unwrap()
                    .split('\'')
                    .nth(1)
                    .unwrap()
                    .to_string();

                let client_clone = self.web.clone();
                let nim_id_shared = Arc::clone(&nim_id_arc);
                let row_sel_shared = Arc::clone(&row_sel_arc);
                let td_center_sel_shared = Arc::clone(&td_center_sel_arc);
                let attended_sel_shared = Arc::clone(&attended_sel_arc);

                let task = tokio::spawn(async move {
                    let html = client_clone
                        .post("https://lms.unindra.ac.id/presensi/rekap_presensi_mhs")
                        .form(&[("kd_jdw", &kode_jadwal_id), ("nim", &*nim_id_shared)])
                        .send()
                        .await
                        .unwrap()
                        .text()
                        .await
                        .unwrap();

                    Html::parse_document(&html)
                        .select(&*row_sel_shared)
                        .next()
                        .unwrap()
                        .select(&*td_center_sel_shared)
                        .enumerate()
                        .map(|(index, col)| AttendanceEntity {
                            course_code: course_code.clone(),
                            index: index as i8 + 1,
                            is_attended: col.select(&*attended_sel_shared).next().is_some(),
                        })
                        .collect::<Vec<_>>()
                });

                tasks.push(task);
            }
        }

        join_all(tasks)
            .await
            .into_iter()
            .map(|res| res.unwrap())
            .flatten()
            .collect()
    }

    async fn fetch_contents(&self, meeting_url: &str) -> Vec<ContentEntity> {
        let html = self
            .web
            .get(meeting_url)
            .send()
            .await
            .unwrap()
            .text()
            .await
            .unwrap();

        let mut tasks = Vec::new();

        {
            let document = Html::parse_document(&html);
            let row_sel = Selector::parse("tbody tr").unwrap();
            let div_sel = Selector::parse("div.col-md-4").unwrap();
            let icon_sel = Selector::parse("a i").unwrap();
            let a_sel = Selector::parse("a").unwrap();

            for row in document.select(&row_sel) {
                let mut divs = row.select(&div_sel);
                let div1 = divs.next().unwrap();
                let div2 = divs.next().unwrap();

                let mut a_tags = div1.select(&a_sel);
                let a_first = a_tags.next().unwrap();
                let a_second = a_tags.next().unwrap();

                let href = a_second.attr("href").unwrap();

                let link = if href.starts_with("http") {
                    href.to_string()
                } else {
                    a_first
                        .attr("onclick")
                        .unwrap()
                        .split('\'')
                        .nth(1)
                        .unwrap()
                        .to_string()
                };

                let content_type = div1
                    .select(&icon_sel)
                    .next()
                    .unwrap()
                    .attr("class")
                    .unwrap()
                    .split_whitespace()
                    .find(|c| c.starts_with("fa-"))
                    .unwrap_or_else(|| {
                        if link.starts_with("https://lms.unindra.ac.id/member_tugas") {
                            "fa-suitcase"
                        } else {
                            "fa-pdf"
                        }
                    })
                    .to_string();

                let title = if let Some(text) = div2
                    .text()
                    .next()
                    .map(|s| s.trim())
                    .filter(|s| !s.is_empty())
                {
                    text.rsplit_once('.')
                        .map(|(before_dot, _)| before_dot)
                        .unwrap_or(text)
                        .to_string()
                } else {
                    div1.text().nth(5).unwrap().to_string()
                };

                let client_clone = self.web.clone();

                let meeting_url_clone = meeting_url.to_string();

                let task = tokio::spawn(async move {
                    let real_link = if link.contains("member_url") {
                        client_clone
                            .get(&link)
                            .send()
                            .await
                            .unwrap()
                            .text()
                            .await
                            .unwrap()
                    } else {
                        link
                    };

                    ContentEntity {
                        meeting_url: meeting_url_clone,
                        content_type,
                        title,
                        url: real_link,
                    }
                });

                tasks.push(task);
            }
        }

        join_all(tasks)
            .await
            .into_iter()
            .map(|res| res.unwrap())
            .collect()
    }

    async fn scrape_assignment(
        &self,
        fk_meeting_url: &str,
        pk_assignment_url: &str,
    ) -> (AssignmentEntity, Option<Lecturer>) {
        let html = self
            .web
            .get(pk_assignment_url)
            .send()
            .await
            .unwrap()
            .text()
            .await
            .unwrap();

        let document = Html::parse_document(&html);

        let msg_sel = Selector::parse("div[style*=padding-left]").unwrap();
        let default_sel = Selector::parse("div.callout-white-default").unwrap();
        let warning_sel = Selector::parse("div.callout-white-warning").unwrap();

        let message = document
            .select(&msg_sel)
            .next()
            .and_then(|element| element.text().next());

        let assign_file_url = document
            .select(&default_sel)
            .next()
            .and_then(|el| extract_file_url(el));

        let deadline = document
            .select(&Selector::parse("div.callout-white-default table tr td").unwrap())
            .nth(2)
            .unwrap()
            .text()
            .next()
            .unwrap();

        let view_submit_file_url = document
            .select(&warning_sel)
            .next()
            .and_then(|el| extract_file_url(el));

        let is_submitted = html.contains("Sudah Submit");
        let is_expired = html.contains("Waktu Submit sudah berakhir");

        let lecturer_opt = document
            .select(&Selector::parse("div.user-block").unwrap())
            .next()
            .and_then(|user_block| {
                let name = user_block
                    .select(&Selector::parse("span").unwrap())
                    .next()?
                    .text()
                    .next()?
                    .split(',')
                    .next()?
                    .to_string();

                let profile_picture_url = user_block
                    .select(&Selector::parse("img").unwrap())
                    .next()?
                    .attr("src")?
                    .to_string();

                Some(Lecturer {
                    name,
                    profile_picture_url,
                })
            });

        (
            AssignmentEntity {
                meeting_url: fk_meeting_url.to_string(),
                url: pk_assignment_url.to_string(),
                message: message.map(|s| s.to_string()),
                assign_file_url,
                deadline: deadline.to_string(),
                view_submit_file_url,
                is_submitted,
                is_overdue: is_expired,
            },
            lecturer_opt,
        )
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

async fn solve_captcha(bytes: bytes::Bytes) -> String {
    tokio::task::spawn_blocking(move || {
        let image = image::load_from_memory(&bytes).unwrap();

        let raw_text = REC_MODEL.recognize_text(&image).unwrap();

        let (num1, raw_num2) = raw_text.split_once("+").unwrap();

        let val1 = num1.parse::<i8>().unwrap();
        let val2 = raw_num2.split_once('=').unwrap().0.parse::<i8>().unwrap();

        (val1 + val2).to_string()
    })
    .await
    .unwrap()
}

fn extract_file_url(container: ElementRef) -> Option<String> {
    let pdf_sel = Selector::parse("a[onclick*=lihat_pdf]").unwrap();
    let pict_sel = Selector::parse("a[onclick*=lihat_gambar]").unwrap();
    let other_sel = Selector::parse("a[href*=force_download]").unwrap();

    // Cek PDF
    if let Some(a_tag) = container.select(&pdf_sel).next() {
        if let Some(onclick) = a_tag.value().attr("onclick") {
            // Pengganti substringAfter("'").substringBefore("'")
            if let Some(id) = onclick.split('\'').nth(1) {
                return Some(format!(
                    "https://lms.unindra.ac.id/media_public/lihat_pdf/{}",
                    id
                ));
            }
        }
    }

    // Cek Gambar
    if let Some(a_tag) = container.select(&pict_sel).next() {
        if let Some(onclick) = a_tag.value().attr("onclick") {
            if let Some(id) = onclick.split('\'').nth(1) {
                return Some(format!(
                    "https://lms.unindra.ac.id/media_public/lihat_gambar/{}",
                    id
                ));
            }
        }
    }

    // Cek Lainnya
    if let Some(a_tag) = container.select(&other_sel).next() {
        if let Some(href) = a_tag.value().attr("href") {
            return Some(href.to_string());
        }
    }

    None
}

fn format_title_case(s: &str) -> String {
    s.to_lowercase()
        .split_whitespace()
        .map(|word| {
            let mut c = word.chars();
            c.next().unwrap().to_ascii_uppercase().to_string() + c.as_str()
        })
        .collect::<Vec<_>>()
        .join(" ")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    #[ignore = "membutuhkan file captcha dan model OCR; jalankan manual dengan --ignored --nocapture"]
    async fn solve_captcha_local() {
        let captcha = std::fs::read(concat!(env!("CARGO_MANIFEST_DIR"), "/captcha.png")).unwrap();
        let captcha_bytes = bytes::Bytes::from(captcha);
        let answer = solve_captcha(captcha_bytes).await;

        println!("captcha answer: {answer}");

        assert!(answer == "7");
    }

    #[tokio::test]
    #[ignore = "login ke LMS asli, membutuhkan jaringan, captcha OCR, dan kredensial di cookie_renewed"]
    async fn parse_student_with_cookie() {
        let internet_data_source = InternetDataSource::new();

        internet_data_source
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

        let result = InternetDataSource::parse_courses(&Html::parse_document(real_dashboard_html));

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

        let result = InternetDataSource::parse_meetings(&Html::parse_document(real_dashboard_html));

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

        internet_data_source
            .cookie_renewed("202443500660", "GamerJeniusN")
            .await;
        let result = internet_data_source.fetch_attendances().await;

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

        internet_data_source
            .cookie_renewed("202443500660", "GamerJeniusN")
            .await;

        let result = internet_data_source.fetch_contents("https://lms.unindra.ac.id/pertemuan/pke/ZGNaTjQ1ZTZpV0xOcWdBVU1vbjZoS2VSWkxseFp5djZUWjhORkZDdDRXST0=").await;

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

        internet_data_source
            .cookie_renewed("202443500660", "GamerJeniusN")
            .await;

        let result = internet_data_source.scrape_assignment(
            "https://lms.unindra.ac.id/pertemuan/pke/ZGNaTjQ1ZTZpV0xOcWdBVU1vbjZoS2VSWkxseFp5djZUWjhORkZDdDRXST0=",
            "https://lms.unindra.ac.id/member_tugas/kelas/ZGNaTjQ1ZTZpV0xOcWdBVU1vbjZoTGRJUzJxc3FQTDNIQ0thK0hmc3A4cUhSRVZOL0tiLy9ic09xWmJNM0VnRHc2anlhMnFDN0YzbVA0aDFWcnltRGwxMW04ZFBLNjF2MU9BdDU1OVBrcGc9"
        ).await.0;

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

        internet_data_source
            .cookie_renewed("202443500660", "GamerJeniusN")
            .await;

        let result = internet_data_source.fetch_all().await;

        std::fs::write("lms_result.txt", format!("{:#?}", result)).expect("Gagal menulis ke file");
        println!("Data berhasil ditulis ke lms_result.txt");

        assert!(
            !result.courses.is_empty(),
            "Harusnya ada minimal satu mata kuliah"
        );
    }
}
