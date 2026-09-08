# 附件在线预览验证

本次为 V1 附件展示增强；数据库、Flyway、上传方式、目录、附件阶段及 JWT 配置均未修改。

## 支持与安全边界

- JPEG/PNG/WebP/GIF/BMP：认证 fetch 获取 Blob 后在弹窗中按比例展示。
- Markdown：UTF-8 文本，经 marked 解析、DOMPurify 排版标签白名单清理后展示。支持标题、段落、引用、列表、强调、代码块、分割线及简单表格；HTML 事件、脚本、表单、样式及资源属性不保留，链接仅展示文字，不自动加载外部图片。
- TXT：UTF-8 纯文本，Vue 文本插值保证 HTML 只作为文字显示；保留换行并可滚动。
- PDF：认证 Blob 转为 application/pdf 对象 URL 后交给浏览器内置 iframe 阅读器；无 Office 转换服务。
- DOC/DOCX、XLS/XLSX、ZIP 和未知二进制继续下载，不显示预览按钮。

marked 本身不清理 HTML，因此清理发生在解析之后、插入 DOM 之前。依据：[Marked 安全说明](https://marked.js.org/#usage)、[DOMPurify 文档](https://github.com/cure53/DOMPurify)。

旧附件可按原文件名扩展名识别，不回写 MIME 或 is_image 元数据。因此 WebP 可预览不代表改变了原有上传时的图片识别或封面资格。

## 实现范围

- AttachmentPreviewDialog.vue：统一弹窗、文本渲染、PDF/图片展示、下载与错误提示。关闭、换附件或卸载时取消请求并释放 URL；过期请求不再创建预览。
- attachments/preview.ts：前端类型白名单。
- GoalDetailView.vue：全部附件、过程记录、完成证明及封面入口。
- CompletionDialog.vue：保留的完成证明可预览，关闭子弹窗不关闭编辑窗口。
- GoalDetailController.content：保留原 content API、数据库定位与 LocalFiles 路径检查，增加安全预览类型的 inline 响应。download=true 始终为 attachment，继续 no-store / nosniff。未公开 uploads。
- 新增依赖：marked 18.0.12、dompurify 3.4.15，锁文件记录实际安装版本。

## 自动验证（2026-09-08）

| 项目 | 结果与性质 |
| --- | --- |
| npm run build | 通过，包含 TypeScript 检查 |
| 前端现有与新增测试 | 35 通过：原 27 + 新 8。页面测试使用模拟 API，不是真实 MySQL 链路 |
| 新增后端测试 | 15 通过：真实 Spring Security JWT、MockMvc、临时物理文件；附件 Service 为模拟对象 |
| mvn verify 全量 | 90 项中 82 通过、8 项真实 MySQL skipped；完整 jar 打包成功 |
| 隔离说明 | 使用同一份 backend/src 和 pom，在 .cache 下独立目录构建，避免覆盖本机运行中的 jar |
| 真实链路 | 本轮未执行真实 MySQL / 本地持久化附件 / Nginx 浏览器验收 |

新增测试包含 Markdown 常见语法和恶意 HTML、TXT 换行与下载、PDF Blob iframe、图片/WebP 解码、关闭后 URL 释放、未知格式、过程与证明入口、嵌套弹窗、1366px 尺寸、读取失败、未登录拒绝、download=true、缺失文件和路径越界拒绝。

PDF 自动测试验证认证读取、对象 URL 与 iframe 建立；没有将模拟 PDF 响应当作真实 PDF 阅读器完整渲染验收。

## 本机验收

1. 按 LOCAL-RUN.md 停止应用后构建并重启；打开 http://localhost 中一个可用于验收的事项。
2. 上传实际 MD、TXT、PDF、PNG/WebP/GIF、DOCX 和 ZIP。分别确认预览按钮范围和原下载功能。
3. MD 检查标题、列表、代码块和长内容滚动；加入 script、onerror、javascript 链接，确认不会执行或加载外部资源。TXT 中 HTML 应按原文显示。
4. 用真实多页 PDF 确認浏览器内置阅读器显示、滚动与缩放。若浏览器策略关闭 PDF 内联阅读，使用弹窗下载按钮。
5. 在全部附件、过程记录、完成证明和编辑完成档案中打开预览。Escape 或关闭按钮应只关当前预览。
6. Network 确认请求仍为 /api/attachments/{id}/content，携带 Authorization，URL 无 JWT；下载请求带 download=true。
7. 不带 Token 读取 content 应为 401；删除附件后旧入口应给出可读错误；正常预览不修改附件记录。
