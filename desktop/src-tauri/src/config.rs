use base64::Engine;
use std::{
    collections::BTreeMap,
    env, fs,
    path::{Path, PathBuf},
};

const REQUIRED: [&str; 7] = [
    "DB_URL",
    "DB_USERNAME",
    "DB_PASSWORD",
    "LIFE1000_USERNAME",
    "LIFE1000_PASSWORD",
    "LIFE1000_JWT_SECRET",
    "LIFE1000_UPLOAD_DIRECTORY",
];

// 不派生 Debug，避免配置在诊断输出中意外泄露。
pub struct Config {
    pub java: PathBuf,
    pub environment: BTreeMap<String, String>,
    pub directory: PathBuf,
}

pub fn config_path() -> Result<PathBuf, String> {
    if let Some(path) = env::var_os("LIFE1000_DESKTOP_CONFIG") {
        let path = PathBuf::from(path);
        return if path.is_absolute() {
            Ok(path)
        } else {
            Err("LIFE1000_DESKTOP_CONFIG 必须是绝对路径。".into())
        };
    }
    Ok(
        PathBuf::from(env::var_os("LOCALAPPDATA").ok_or("找不到本机应用配置目录。")?)
            .join("Life1000")
            .join("desktop-local-config.json"),
    )
}

impl Config {
    pub fn read(path: &Path) -> Result<Self, String> {
        let text = fs::read_to_string(path).map_err(|_| "找不到桌面私有配置。请按 docs/DESKTOP-RUN.md 配置 %LOCALAPPDATA%\\Life1000\\desktop-local-config.json。")?;
        let values: BTreeMap<String, String> =
            serde_json::from_str(text.trim_start_matches('\u{feff}')).map_err(|_| {
                "桌面配置不是有效的 JSON 字符串字典；Windows 路径请使用双反斜杠或 /。"
            })?;
        for key in values.keys() {
            if key != "JAVA_HOME" && !REQUIRED.contains(&key.as_str()) {
                return Err(format!("桌面配置包含不支持的键：{key}。"));
            }
        }
        for key in REQUIRED {
            if values
                .get(key)
                .is_none_or(|value| value.trim().is_empty() || value == "REPLACE_ME")
            {
                return Err(format!("请填写桌面配置中的 {key}。"));
            }
        }
        let secret = base64::engine::general_purpose::STANDARD
            .decode(&values["LIFE1000_JWT_SECRET"])
            .map_err(|_| "LIFE1000_JWT_SECRET 必须是 Base64 字符串。")?;
        if secret.len() < 32 {
            return Err("LIFE1000_JWT_SECRET 解码后至少需要 32 字节。".into());
        }
        let uploads = Path::new(&values["LIFE1000_UPLOAD_DIRECTORY"]);
        if !uploads.is_absolute() || !uploads.is_dir() {
            return Err("LIFE1000_UPLOAD_DIRECTORY 必须指向原有附件目录的绝对路径；不会自动创建空目录替代用户数据。".into());
        }
        let home = values
            .get("JAVA_HOME")
            .filter(|v| !v.trim().is_empty())
            .cloned()
            .or_else(|| env::var("JAVA_HOME").ok().filter(|v| !v.trim().is_empty()));
        let java = home.map_or_else(
            || PathBuf::from("java"),
            |home| PathBuf::from(home).join("bin/java.exe"),
        );
        let environment = values
            .into_iter()
            .filter(|(key, _)| REQUIRED.contains(&key.as_str()))
            .collect();
        Ok(Self {
            java,
            environment,
            directory: path.parent().ok_or("无效配置路径。")?.to_owned(),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn config_validates_secrets_and_existing_upload_directory() {
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path().join("config.json");
        assert!(Config::read(&path).is_err());
        let mut data = serde_json::json!({
            "DB_URL":"jdbc:mysql://127.0.0.1/life1000_test", "DB_USERNAME":"test-user", "DB_PASSWORD":"test-only",
            "LIFE1000_USERNAME":"test-user", "LIFE1000_PASSWORD":"test-only",
            "LIFE1000_JWT_SECRET":base64::engine::general_purpose::STANDARD.encode([1;32]),
            "LIFE1000_UPLOAD_DIRECTORY":directory.path().to_string_lossy()
        });
        fs::write(&path, data.to_string()).unwrap();
        let config = Config::read(&path).unwrap();
        assert_eq!(config.environment.len(), 7);
        data["SERVER_ADDRESS"] = "0.0.0.0".into();
        fs::write(&path, data.to_string()).unwrap();
        assert!(Config::read(&path).is_err());
        data.as_object_mut().unwrap().remove("SERVER_ADDRESS");
        data["LIFE1000_UPLOAD_DIRECTORY"] = "relative/uploads".into();
        fs::write(&path, data.to_string()).unwrap();
        assert!(Config::read(&path).is_err());
        data["LIFE1000_UPLOAD_DIRECTORY"] = directory.path().to_string_lossy().to_string().into();
        data["LIFE1000_JWT_SECRET"] = "invalid".into();
        fs::write(&path, data.to_string()).unwrap();
        assert!(Config::read(&path).is_err());
    }
}
