#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
mod config;
mod process;
mod runtime;

use std::sync::Arc;
use tauri::{webview::DownloadEvent, Manager, RunEvent, WebviewUrl, WebviewWindowBuilder};
use tauri_plugin_window_state::StateFlags;

#[tauri::command]
async fn start_backend(
    app: tauri::AppHandle,
    state: tauri::State<'_, Arc<runtime::Runtime>>,
) -> Result<(), String> {
    let jar = app
        .path()
        .resource_dir()
        .map_err(|_| "找不到桌面资源目录。")?
        .join("backend/life1000-backend.jar");
    let state = state.inner().clone();
    tauri::async_runtime::spawn_blocking(move || state.start(&jar))
        .await
        .map_err(|_| "后端启动任务异常结束，请关闭后重试。".to_string())?
}

fn main() {
    let app = tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.set_focus();
            }
        }))
        .plugin(
            tauri_plugin_window_state::Builder::default()
                .with_state_flags(StateFlags::SIZE | StateFlags::POSITION | StateFlags::MAXIMIZED)
                .build(),
        )
        .manage(Arc::new(runtime::Runtime::default()))
        .invoke_handler(tauri::generate_handler![start_backend])
        .setup(|app| {
            let (width, height) = app
                .primary_monitor()?
                .map(|monitor| {
                    let area = monitor
                        .work_area()
                        .size
                        .to_logical::<f64>(monitor.scale_factor());
                    (
                        1366.0_f64.min(area.width - 40.0),
                        900.0_f64.min(area.height - 80.0),
                    )
                })
                .unwrap_or((1280.0, 800.0));
            WebviewWindowBuilder::new(app, "main", WebviewUrl::App("index.html".into()))
                .title("Life1000")
                .decorations(false)
                .shadow(true)
                .inner_size(width.max(900.0), height.max(600.0))
                .center()
                .min_inner_size(900.0, 600.0)
                .resizable(true)
                .on_navigation(|url| {
                    let allowed = url.origin().ascii_serialization() == "http://tauri.localhost"
                        || url.as_str().starts_with("blob:http://tauri.localhost/")
                        || url.as_str() == "about:blank";
                    allowed
                })
                .on_download(|_, event| {
                    if let DownloadEvent::Requested { url, destination } = event {
                        // 附件与备份仍由前端带 JWT 获取 Blob；原生层仅接管用户选择保存位置。
                        if !url.as_str().starts_with("blob:http://tauri.localhost/") {
                            return false;
                        }
                        let name = destination
                            .file_name()
                            .map(|n| n.to_string_lossy().into_owned())
                            .unwrap_or("Life1000-download".into());
                        if let Some(path) = rfd::FileDialog::new()
                            .set_title("保存 Life1000 文件")
                            .set_file_name(name)
                            .save_file()
                        {
                            *destination = path;
                            return true;
                        }
                        return false;
                    }
                    true
                })
                .build()?;
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("无法创建 Life1000 桌面窗口");
    app.run(|app, event| {
        if matches!(event, RunEvent::ExitRequested { .. } | RunEvent::Exit) {
            app.state::<Arc<runtime::Runtime>>().stop();
        }
    });
}
