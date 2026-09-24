use std::{
    io::Read,
    path::Path,
    process::{Child, Command, Stdio},
    thread,
    time::{Duration, Instant},
};

pub fn hidden_command(executable: &Path) -> Command {
    let mut command = Command::new(executable);
    // 仅保留操作系统运行所需变量。全局 Spring / Java 注入参数不能覆盖回环地址或带入别的配置。
    let inherited: Vec<_> = std::env::vars_os()
        .filter(|(key, _)| {
            matches!(
                key.to_string_lossy().to_ascii_uppercase().as_str(),
                "PATH"
                    | "SYSTEMROOT"
                    | "WINDIR"
                    | "TEMP"
                    | "TMP"
                    | "USERPROFILE"
                    | "LOCALAPPDATA"
                    | "APPDATA"
                    | "COMSPEC"
                    | "PROGRAMDATA"
                    | "PROGRAMFILES"
                    | "PROGRAMFILES(X86)"
                    | "SYSTEMDRIVE"
            )
        })
        .collect();
    command.env_clear().envs(inherited).stdin(Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000); // CREATE_NO_WINDOW：不闪现 Java 控制台。
    }
    command
}

pub fn java_major(output: &str) -> Option<u32> {
    output.lines().find_map(|line| {
        let mut words = line.split_whitespace();
        if !matches!(words.next()?, "openjdk" | "java") {
            return None;
        }
        let mut version = words.next()?;
        if version == "version" {
            version = words.next()?;
        }
        let version = version.trim_matches('"');
        let mut parts = version.split(['.', '-', '+']);
        let first = parts.next()?.parse().ok()?;
        if first == 1 {
            parts.next()?.parse().ok()
        } else {
            Some(first)
        }
    })
}

pub fn check_java(java: &Path) -> Result<(), String> {
    let mut command = hidden_command(java);
    command
        .arg("--version")
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    let mut child = OwnedChild::spawn(&mut command)
        .map_err(|_| "Life1000 需要 Java 21。请安装 Java 21 或更新 JAVA_HOME。")?;
    let deadline = Instant::now() + Duration::from_secs(10);
    loop {
        if let Some(status) = child.child.try_wait().map_err(|_| "无法检查 Java 版本。")? {
            let mut output = String::new();
            if let Some(mut pipe) = child.child.stdout.take() {
                let _ = pipe.read_to_string(&mut output);
            }
            if let Some(mut pipe) = child.child.stderr.take() {
                let _ = pipe.read_to_string(&mut output);
            }
            return if status.success() && java_major(&output).is_some_and(|v| v >= 21) {
                Ok(())
            } else {
                Err("Life1000 需要 Java 21。请安装 Java 21 或更新 JAVA_HOME。".into())
            };
        }
        if Instant::now() >= deadline {
            return Err("检查 Java 版本超时。Life1000 需要 Java 21。".into());
        }
        thread::sleep(Duration::from_millis(50));
    }
}

pub struct OwnedChild {
    pub child: Child,
    #[cfg(windows)]
    _job: Job,
}
impl OwnedChild {
    pub fn spawn(command: &mut Command) -> Result<Self, String> {
        #[cfg(windows)]
        let job = Job::new()?;
        let mut child = command
            .spawn()
            .map_err(|_| "无法启动后端进程，请检查 Java 与文件访问权限。")?;
        #[cfg(windows)]
        if job.assign(&child).is_err() {
            let _ = child.kill();
            let _ = child.wait();
            return Err("无法建立桌面后端的进程保护，已停止启动。".into());
        }
        Ok(Self {
            child,
            #[cfg(windows)]
            _job: job,
        })
    }
}
impl Drop for OwnedChild {
    fn drop(&mut self) {
        // 操作持有的进程句柄，不根据名字扫描 Java，也不信任可复用的 PID 文件。
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

#[cfg(windows)]
struct Job(windows_sys::Win32::Foundation::HANDLE);
#[cfg(windows)]
unsafe impl Send for Job {}
#[cfg(windows)]
impl Job {
    fn new() -> Result<Self, String> {
        use windows_sys::Win32::System::JobObjects::*;
        unsafe {
            let handle = CreateJobObjectW(std::ptr::null(), std::ptr::null());
            if handle.is_null() {
                return Err("无法创建后端进程保护。".into());
            }
            let job = Self(handle);
            let mut limits: JOBOBJECT_EXTENDED_LIMIT_INFORMATION = std::mem::zeroed();
            limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
            if SetInformationJobObject(
                handle,
                JobObjectExtendedLimitInformation,
                &limits as *const _ as *const _,
                std::mem::size_of_val(&limits) as u32,
            ) == 0
            {
                return Err("无法配置后端进程保护。".into());
            }
            Ok(job)
        }
    }
    fn assign(&self, child: &Child) -> Result<(), ()> {
        use std::os::windows::io::AsRawHandle;
        if unsafe {
            windows_sys::Win32::System::JobObjects::AssignProcessToJobObject(
                self.0,
                child.as_raw_handle(),
            )
        } == 0
        {
            Err(())
        } else {
            Ok(())
        }
    }
}
#[cfg(windows)]
impl Drop for Job {
    fn drop(&mut self) {
        unsafe {
            windows_sys::Win32::Foundation::CloseHandle(self.0);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn java_versions() {
        for (output, expected) in [
            ("openjdk 21.0.8 2025-07-15", Some(21)),
            ("java 25 2025-09-16", Some(25)),
            ("java version \"1.8.0_401\"", Some(8)),
            ("openjdk 17-ea", Some(17)),
            ("invalid 21", None),
        ] {
            assert_eq!(java_major(output), expected);
        }
    }
    #[test]
    fn dangerous_environment_is_not_inherited() {
        let command = hidden_command(Path::new("java"));
        for (key, _) in command.get_envs() {
            let key = key.to_string_lossy().to_ascii_uppercase();
            assert!(
                !key.starts_with("SPRING_")
                    && !key.starts_with("LIFE1000_")
                    && !key.contains("JAVA_OPTIONS")
            );
        }
    }
    #[cfg(windows)]
    #[test]
    fn owned_child_is_reaped_without_killing_another_process() {
        use windows_sys::Win32::System::Threading::{
            GetExitCodeProcess, OpenProcess, PROCESS_QUERY_LIMITED_INFORMATION,
        };
        let exe = Path::new("ping.exe");
        let mut other = hidden_command(exe)
            .args(["-n", "60", "127.0.0.1"])
            .stdout(Stdio::null())
            .spawn()
            .unwrap();
        let child = OwnedChild::spawn(
            hidden_command(exe)
                .args(["-n", "60", "127.0.0.1"])
                .stdout(Stdio::null()),
        )
        .unwrap();
        let handle = unsafe { OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, 0, child.child.id()) };
        assert!(!handle.is_null());
        drop(child);
        let mut code = 259;
        unsafe {
            assert_ne!(GetExitCodeProcess(handle, &mut code), 0);
            windows_sys::Win32::Foundation::CloseHandle(handle);
        }
        assert_ne!(code, 259);
        assert!(other.try_wait().unwrap().is_none());
        other.kill().unwrap();
        other.wait().unwrap();
    }
}

#[cfg(test)]
mod real_java_test {
    use super::*;
    #[test]
    #[ignore = "Requires LIFE1000_DESKTOP_CONFIG pointing to life1000_test and a built jar"]
    fn java21_in_windows_job_starts_http_and_stops() {
        let config = crate::config::Config::read(&crate::config::config_path().unwrap()).unwrap();
        assert!(config.environment["DB_URL"]
            .split('?')
            .next()
            .unwrap()
            .ends_with("/life1000_test"));
        drop(
            std::net::TcpListener::bind("127.0.0.1:8080")
                .expect("Do not interrupt another backend"),
        );
        check_java(&config.java).unwrap();
        let jar = Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("resources/backend.jar")
            .canonicalize()
            .unwrap();
        let mut command = hidden_command(&config.java);
        command
            .current_dir(&config.directory)
            .envs(&config.environment)
            .arg("-jar")
            .arg(dunce::canonicalize(jar).unwrap())
            .args([
                "--spring.profiles.active=mysql,desktop",
                "--server.address=127.0.0.1",
                "--server.port=8080",
                "--spring.config.location=classpath:/",
            ])
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        let mut child = OwnedChild::spawn(&mut command).unwrap();
        let mut stdout = child.child.stdout.take().unwrap();
        let mut stderr = child.child.stderr.take().unwrap();
        let out = thread::spawn(move || {
            let mut text = String::new();
            let _ = stdout.read_to_string(&mut text);
            text
        });
        let err = thread::spawn(move || {
            let mut text = String::new();
            let _ = stderr.read_to_string(&mut text);
            text
        });
        let client = crate::runtime::health_client(Duration::from_secs(2)).unwrap();
        let start = Instant::now();
        let ready = loop {
            if child.child.try_wait().unwrap().is_some() {
                break false;
            }
            if crate::runtime::http_ready(&client, "http://127.0.0.1:8080/api/health") {
                break true;
            }
            if start.elapsed() > Duration::from_secs(120) {
                break false;
            }
            thread::sleep(Duration::from_millis(250));
        };
        drop(child);
        let mut diagnostic = out.join().unwrap() + &err.join().unwrap();
        for value in config.environment.values() {
            diagnostic = diagnostic.replace(value, "[redacted]");
        }
        assert!(ready, "Isolated Java startup failed:\n{diagnostic}");
        assert!(!crate::runtime::http_ready(
            &client,
            "http://127.0.0.1:8080/api/health"
        ));
    }
}
