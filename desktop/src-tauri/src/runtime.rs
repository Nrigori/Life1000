use crate::{
    config::{config_path, Config},
    process::{check_java, hidden_command, OwnedChild},
};
use std::{
    net::TcpListener,
    path::Path,
    process::Stdio,
    sync::{
        atomic::{AtomicBool, Ordering},
        Mutex,
    },
    thread,
    time::{Duration, Instant},
};
pub struct Runtime {
    child: Mutex<Option<OwnedChild>>,
    closing: AtomicBool,
}
impl Default for Runtime {
    fn default() -> Self {
        Self {
            child: Mutex::new(None),
            closing: AtomicBool::new(false),
        }
    }
}
pub fn http_ready(client: &reqwest::blocking::Client, url: &str) -> bool {
    // TCP 能连通不代表 Spring 已能响应。禁用代理和重定向，且单次请求有截止时间。
    client
        .get(url)
        .send()
        .is_ok_and(|r| matches!(r.status().as_u16(), 200 | 401 | 403))
}
pub fn health_client(timeout: Duration) -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .no_proxy()
        .redirect(reqwest::redirect::Policy::none())
        .timeout(timeout)
        .build()
        .map_err(|_| "无法建立本地健康检查。".into())
}
impl Runtime {
    pub fn start(&self, jar: &Path) -> Result<(), String> {
        let mut owned = self.child.lock().map_err(|_| "无法读取后端进程状态。")?;
        if self.closing.load(Ordering::Acquire) {
            return Err("应用正在关闭。".into());
        }
        let client = health_client(Duration::from_secs(2))?;
        if let Some(child) = owned.as_mut() {
            if child
                .child
                .try_wait()
                .map_err(|_| "无法检查后端进程。")?
                .is_none()
            {
                return if http_ready(&client, "http://127.0.0.1:8080/api/health") {
                    Ok(())
                } else {
                    Err("本实例后端尚未响应，请稍后重试或关闭应用后重新打开。".into())
                };
            }
        }
        *owned = None;
        let config = Config::read(&config_path()?)?;
        if !jar.is_file() {
            return Err("缺少后端 jar，请重新构建或安装 Life1000。".into());
        }
        // Tauri 资源目录含 Windows 的 \\?\ 前缀；Java -jar 不能可靠读取这种路径。
        let jar = dunce::canonicalize(jar).map_err(|_| "无法读取后端 jar 路径。")?;
        check_java(&config.java)?;
        let listener = TcpListener::bind("127.0.0.1:8080")
            .map_err(|_| "8080 已被其他程序占用。请先停止 Web 版后端或另一实例；Life1000 不会关闭其他 Java 进程。")?;
        if self.closing.load(Ordering::Acquire) {
            return Err("应用正在关闭。".into());
        }
        let mut command = hidden_command(&config.java);
        command
            .current_dir(&config.directory)
            .envs(&config.environment)
            .args(["-jar"])
            .arg(jar)
            .args([
                "--spring.profiles.active=mysql,desktop",
                "--server.address=127.0.0.1",
                "--server.port=8080",
                "--spring.config.location=classpath:/",
            ])
            .stdout(Stdio::null())
            .stderr(Stdio::null());
        drop(listener);
        *owned = Some(OwnedChild::spawn(&mut command)?);
        let deadline = Instant::now() + Duration::from_secs(120);
        let result = loop {
            if self.closing.load(Ordering::Acquire) {
                break Err("应用正在关闭。".into());
            }
            match owned.as_mut().unwrap().child.try_wait() {
                Ok(Some(_)) => {
                    break Err("后端启动失败。请检查 MySQL 是否运行、私有配置和数据库权限。".into())
                }
                Err(_) => break Err("无法检查后端子进程。".into()),
                Ok(None) => {}
            }
            if http_ready(&client, "http://127.0.0.1:8080/api/health")
                && owned.as_mut().unwrap().child.try_wait().ok() == Some(None)
            {
                break Ok(());
            }
            if Instant::now() >= deadline {
                break Err("后端在 120 秒内未返回有效 HTTP 响应，已停止本次后端。请检查 MySQL 和本机配置后重试。".into());
            }
            thread::sleep(Duration::from_millis(250));
        };
        if result.is_err() {
            *owned = None;
        }
        result
    }
    pub fn stop(&self) {
        self.closing.store(true, Ordering::Release);
        if let Ok(mut child) = self.child.lock() {
            *child = None;
        }
    }
}
impl Drop for Runtime {
    fn drop(&mut self) {
        self.stop();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    #[test]
    fn requires_http_not_just_an_open_socket() {
        let server = TcpListener::bind("127.0.0.1:0").unwrap();
        let url = format!("http://{}/api/health", server.local_addr().unwrap());
        let worker = thread::spawn(move || {
            let (_socket, _) = server.accept().unwrap();
            thread::sleep(Duration::from_millis(300));
        });
        assert!(!http_ready(
            &health_client(Duration::from_millis(50)).unwrap(),
            &url
        ));
        worker.join().unwrap();
    }
    #[test]
    fn valid_health_statuses_and_failures() {
        for status in [200, 401, 403, 302, 500] {
            let server = TcpListener::bind("127.0.0.1:0").unwrap();
            let url = format!("http://{}/api/health", server.local_addr().unwrap());
            let worker = thread::spawn(move || {
                let (mut socket, _) = server.accept().unwrap();
                let mut request = [0; 2048];
                let _ = socket.read(&mut request);
                write!(
                    socket,
                    "HTTP/1.1 {status} Test\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                )
                .unwrap();
            });
            assert_eq!(
                http_ready(&health_client(Duration::from_secs(1)).unwrap(), &url),
                matches!(status, 200 | 401 | 403)
            );
            worker.join().unwrap();
        }
    }
}
