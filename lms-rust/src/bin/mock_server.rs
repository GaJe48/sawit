use std::io::{Read, Write};
use std::net::TcpListener;

fn main() {
    let listener = TcpListener::bind("0.0.0.0:8080").unwrap();
    println!("====================================================");
    println!("  LMS Mock Server running on http://localhost:8080  ");
    println!("  Press Ctrl+C to stop.                             ");
    println!("====================================================");

    for stream in listener.incoming() {
        let mut stream = match stream {
            Ok(s) => s,
            Err(e) => {
                eprintln!("Failed to accept connection: {}", e);
                continue;
            }
        };

        std::thread::spawn(move || {
            // Set a read timeout to prevent slow/half-open connections from hanging the thread
            let _ = stream.set_read_timeout(Some(std::time::Duration::from_secs(5)));

            let mut buffer = [0; 4096];
            let bytes_read = match stream.read(&mut buffer) {
                Ok(n) => n,
                Err(e) => {
                    if e.kind() != std::io::ErrorKind::WouldBlock
                        && e.kind() != std::io::ErrorKind::TimedOut
                    {
                        eprintln!("Failed to read request: {}", e);
                    }
                    return;
                }
            };

            if bytes_read == 0 {
                return;
            }

            let request = String::from_utf8_lossy(&buffer[..bytes_read]);
            let request_line = request.lines().next().unwrap_or("");
            println!("Request: {}", request_line);

            // Routing request
            let (status, content_type, body_bytes) = if request_line.contains("GET /login_new") {
                println!("-> Serving mock Login Page");
                let html = "
                        <input type=hidden value>
                        <input type=hidden name value>
                        ";
                ("HTTP/1.1 200 OK", "text/html", html.as_bytes().to_vec())
            } else if request_line.contains("POST /login_new") {
                println!("-> Serving mock Login Success");
                let html = "
                        
                        ";
                ("HTTP/1.1 200 OK", "text/html", html.as_bytes().to_vec())
            } else if request_line.contains("GET /kapca") {
                println!("-> Serving mock CAPTCHA");
                let bytes =
                    include_bytes!(concat!(env!("CARGO_MANIFEST_DIR"), "/assets/captcha.png"))
                        .to_vec();
                ("HTTP/1.1 200 OK", "image/png", bytes)
            } else if request_line.contains("GET /member") {
                println!("-> Serving mock Dashboard HTML");
                let html = r#"
                        <div class=user-header>
                            <p>Wowokk</p>
                        </div>
                        <div class=user-body>
                            <strong>202443500660</strong>
                        </div>
                        <span class=Badge-info>Teknik Ragebait</span>

                        <ul class="treeview-menu">
                            <li><a href="http://10.0.2.2:8080/pertemuan/11">
                                    <span>Pertemuan 01</span>
                                </a>
                            </li>
                        </ul>

                        <div class=box-widget>
                            <h3>Agus Wilson</h3>
                            <h5>HP : 081511229565</h5>
                            <div class=isi_badge>
                                <span class=header_badeg>K43J406 -Sejarah Pendidikan dan PGRI</span>
                                <span>Kelas: RJ | Ruang: R.7.4-7 | Waktu: Rabu, 12:30-14:10</span>
                            </div>
                        </div>
                        "#;
                ("HTTP/1.1 200 OK", "text/html", html.as_bytes().to_vec())
            } else {
                println!("-> Serving 404 Not Found");
                let text = "Endpoint Mock Not Found";
                (
                    "HTTP/1.1 404 NOT FOUND",
                    "text/plain",
                    text.as_bytes().to_vec(),
                )
            };

            let headers = format!(
                "{}\r\nContent-Type: {}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                status,
                content_type,
                body_bytes.len()
            );

            if let Err(e) = stream.write_all(headers.as_bytes()) {
                eprintln!("Failed to write headers: {}", e);
            }
            if let Err(e) = stream.write_all(&body_bytes) {
                eprintln!("Failed to write body: {}", e);
            }
            let _ = stream.flush();
        });
    }
}
