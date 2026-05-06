package com.gaje48.lms.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gaje48.lms.model.AccountProblemException
import com.gaje48.lms.model.Assignment
import com.gaje48.lms.model.AttendancesByCourse
import com.gaje48.lms.model.Content
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.DashboardData
import com.gaje48.lms.model.Meeting
import com.gaje48.lms.model.MeetingsByCourse
import com.gaje48.lms.model.SessionExpiredException
import com.gaje48.lms.model.Student
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.plugin
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.InputStream

class InternetDataSource {
    private val webClient = HttpClient(CIO) {
        install(HttpCookies)
        install(HttpRedirect) {
            allowHttpsDowngrade = true
        }
    }.apply {
        plugin(HttpSend).intercept { request ->
            val call = execute(request)

            if (!request.url.pathSegments.contains("login_new") && call.response.request.url.segments.contains("login")) {
                throw SessionExpiredException()
            }

            call
        }
    }

    suspend fun loginStatus(nim: String, pwd: String) {
        val loginPageHtml = webClient.get("https://lms.unindra.ac.id/login_new").bodyAsText()
        val htmlPayload = Jsoup.parse(loginPageHtml)

        val (rawToken, randomInput) = htmlPayload.select("input[type=hidden]")
        val tCsrf = rawToken.attr("value")
        val randomName = randomInput.attr("name")
        val randomValue = randomInput.attr("value")

        repeat(3) { attempt ->
            val bytes = webClient.get("https://lms.unindra.ac.id/kapca").bodyAsBytes()

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            val aiAnswer = solveCaptcha(bitmap) ?: run {
                if (attempt == 2) error("Gagal membaca captcha setelah 3x percobaan")
                return@repeat
            }

            val response = webClient.submitForm(
                url = "https://lms.unindra.ac.id/login_new",
                formParameters = parameters {
                    append("csrf_token", tCsrf)
                    append(randomName, randomValue)
                    append("username", nim)
                    append("pswd", pwd)
                    append("kapca", aiAnswer)
                }
            )

            val location = response.headers["location"]
                ?: if (attempt == 2) error("Gagal login setelah menjawab captcha 3x") else return@repeat

            when {
                location.contains("member") -> return
                location.contains("login") -> throw AccountProblemException()
                else -> error("Ada yang salah, location: $location")
            }
        }
    }

    private suspend fun solveCaptcha(bitmap: Bitmap): String? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val visionText = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

        val cleanText = visionText.text.replace(" ", "")

        val mathMatch = Regex("(\\d+)\\+(\\d+)").find(cleanText) ?: return null

        val (num1, num2) = mathMatch.destructured
        return (num1.toInt() + num2.toInt()).toString()
    }

    suspend fun fetchInitialData(dashboardHtml: String? = null) = coroutineScope {
        val allPresences = async { fetchAllAttendances() }

        val dashboardHtml = dashboardHtml ?: webClient.get("https://lms.unindra.ac.id/member").bodyAsText()
        
        val allMeetings = async { parseAllMeetings(dashboardHtml) }
        val courses = async { parseCourses(dashboardHtml) }
        val student = async { parseStudent(dashboardHtml) }

        DashboardData(student.await(), courses.await(), allMeetings.await(), allPresences.await())
    }

    private fun parseStudent(dashboardHtml: String): Student {
        val dashboardParser = Jsoup.parse(dashboardHtml)

        val rawName = dashboardParser.selectFirst("div.pull-left.info p")!!.text()
        val studentName = rawName.lowercase().split(" ")
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        val npm = dashboardParser.selectFirst("li.user-body strong")!!.text()

        return Student(
            studentName = studentName,
            npm = npm,
            studyProgram = dashboardParser.selectFirst("span.Badge-info")!!.text(),
            classCode = dashboardParser.selectFirst("span.pull-right.text-bold.badge")!!.text(),
            studentProfilePictureUrl = "https://lms.unindra.ac.id/lms_publik/images/users/thumbs/$npm.png"
        )
    }

    private fun parseCourses(
        dashboardHtml: String
    ) = Jsoup.parse(dashboardHtml).select("div.box-widget").map { el ->
        val rawLecturer = el.selectFirst("h3.widget-user-username")!!.text()
        val lecturerName = rawLecturer.lowercase().split(" ")
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

        val (courseCode, rawCourseName) = el.selectFirst("span.header_badeg")!!.text()
            .split(" -")

        val courseName = when (rawCourseName) {
            "Arsitektur dan Organisasi Komput" -> "Arsitektur dan Organisasi Komputer"
            else -> rawCourseName.trimEnd(' ', '*', '#', ')')
        }

        val (_, room, time) = el.selectFirst("span.text-green")!!.text()
            .split("|").map { it.trim() }
        val (day, clock) = time.replace("Waktu: ", "").split(", ")

        Course(
            courseCode = courseCode,
            courseName = courseName,
            day = day,
            clock = clock,
            room = room.replace("Ruang: ", ""),
            lecturerName = lecturerName,
            lecturerPhoneNumber = el.selectFirst("h5.widget-user-desc")!!.text()
                .replace("HP :", "").trim().ifEmpty { "Nomor HP tidak tersedia" },
            lecturerProfilePictureUrl = el.selectFirst("img")!!.attr("src")
        )
    }

    private fun parseAllMeetings(dashboardHtml: String): List<MeetingsByCourse> {
        val dashboardParser = Jsoup.parse(dashboardHtml)

        val treeviews = dashboardParser.select("li.treeview")
        val widgets = dashboardParser.select("div.box-widget")

        val numberRegex = Regex("(\\d+)")

        return treeviews.zip(widgets) { tree, widget ->

            val meetings = tree.select("ul li a").map { aTag ->
                val title = aTag.text()
                val url = aTag.attr("href")

                val meetingNumber = numberRegex.find(title)!!.value.toInt()
                Meeting(meetingNumber - 1, url)
            }

            val courseCode = widget.selectFirst("span.header_badeg")!!.text().substringBefore(" -")

            MeetingsByCourse(courseCode, meetings)
        }
    }

    suspend fun fetchAllAttendances() = coroutineScope {
        val presenceHtml = webClient.get("https://lms.unindra.ac.id/presensi").bodyAsText()
        val presenceParser = Jsoup.parse(presenceHtml)

        val nimId = presenceParser.selectFirst("td[onclick*=absensi_mhs]")
            ?.attr("onclick")?.split("'")[3] ?: return@coroutineScope emptyList()

        presenceParser.select("table.table-striped tbody tr").map { row ->
            async {
                val colPresences = row.select("td")
                val courseCode = colPresences[1].text().trim()
                val kodeJadwalId = colPresences[2].attr("onclick").split("'")[1]

                val html = webClient.submitForm(
                    url = "https://lms.unindra.ac.id/presensi/rekap_presensi_mhs",
                    formParameters = parameters {
                        append("kd_jdw", kodeJadwalId)
                        append("nim", nimId)
                    }
                ).bodyAsText()

                val parser = Jsoup.parse(html)
                val barisMahasiswa = parser.selectFirst("table.table-bordered tbody tr")
                    ?: return@async AttendancesByCourse(courseCode, emptyList())

                val cols = barisMahasiswa.select("td")
                val listStatusHadir = (3..<cols.size - 1).map { i ->
                    cols[i].selectFirst("i.fa-calendar-check-o") != null
                }

                AttendancesByCourse(courseCode, listStatusHadir)
            }
        }.awaitAll()
    }

    suspend fun fetchContents(meetingUrl: String): List<Content> {
        val html = webClient.get(meetingUrl).bodyAsText()
        val document = Jsoup.parse(html)

        val urlRegex = """(https?://[^\s'"]+)""".toRegex()

        return document.select("tbody tr").map { row ->
            val (div1, div2) = row.select("div.col-md-4")

            val link = urlRegex.find(div1.html())!!.value
            val realLink =
                if (link.contains("member_url")) webClient.get(link).bodyAsText()
                else link

            Content(
                type = div1.selectFirst("a i")!!.className().split(" ")
                    .find { it.startsWith("fa-") }!!,
                title = div2.text().trim().substringBeforeLast(".")
                    .ifEmpty { div1.text().trim() },
                contentUrl = realLink
            )
        }
    }

    suspend fun fetchAssignments(contentUrl: String): Assignment {
        val taskHtml = webClient.get(contentUrl).bodyAsText()
        val taskParser = Jsoup.parse(taskHtml)

        val message = taskParser.selectFirst("div[style*=padding-left]")?.text()

        val taskFile = extractFileUrl(taskParser.selectFirst("div.callout-white-default")!!)

        val deadline: String = taskParser.select("table.table-bordered tr")[1]
            .selectFirst("td")!!.text()

        val viewUrl = extractFileUrl(taskParser.selectFirst("div.callout-white-warning")!!)

        val isSubmitted = taskHtml.contains("Sudah Submit")
        val isExpired = taskHtml.contains("Waktu Submit sudah berakhir")

        return Assignment(
            contentUrl,
            message,
            taskFile,
            deadline,
            viewUrl,
            isSubmitted,
            isExpired
        )
    }

    private fun extractFileUrl(container: Element): String? {
        val pdfId = container.selectFirst("a[onclick*=lihat_pdf]")?.attr("onclick")
            ?.substringAfter("'", "")?.substringBefore("'")

        val pictId = container.selectFirst("a[onclick*=lihat_gambar]")?.attr("onclick")
            ?.substringAfter("'", "")?.substringBefore("'")

        val others = container.selectFirst("a[href*=force_download]")?.attr("href")

        return when {
            pdfId != null -> "https://lms.unindra.ac.id/media_public/lihat_pdf/$pdfId"
            pictId != null -> "https://lms.unindra.ac.id/media_public/lihat_gambar/$pictId"
            else -> others
        }
    }

    suspend fun executeAttendance(fileUrl: String) = webClient.get(fileUrl)

    suspend fun downloadFile(fileUrl: String) = webClient.get(fileUrl)

    suspend fun uploadSubmission(
        fileName: String,
        fileSize: Long,
        stream: InputStream,
        taskUrl: String,
        onProgress: (fileName: String, progress: Float) -> Unit
    ) {
        val htmlForm = webClient.get(taskUrl).bodyAsText()
        val parserForm = Jsoup.parse(htmlForm)

        val idTugas = parserForm.selectFirst("input[name=h_id_tugas]")?.attr("value")
            ?: error("Payload form tidak ditemukan")
        val hKode = parserForm.selectFirst("input[name=h_kode]")!!.attr("value")
        val idAktifitas = parserForm.selectFirst("input[name=h_id_aktifitas]")!!.attr("value")

        webClient.post("https://lms.unindra.ac.id/member_tugas/mhs_upload_file_proses") {
            onUpload { uploaded, total ->
                total?.let { onProgress(fileName, uploaded.toFloat() / it) }
            }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("h_id_tugas", idTugas)
                        append("h_kode", hKode)
                        append("h_id_aktifitas", idAktifitas)
                        append("myfile", InputProvider(fileSize) { stream.asInput() }, Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=$fileName")
                        })
                    }
                )
            )
        }

        onProgress(fileName, 1f)
    }
}
