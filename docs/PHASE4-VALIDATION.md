# Phase 4 验证：完成系统与年度时间轴

本文件说明实现及验收方法，不修改 PRD。Phase 4 完成后停止，未实现 Phase 5 / Phase 6。

## 当前自动化结果

| 验证类型 | 结果与边界 |
| --- | --- |
| 前端生产构建 | `npm run build` 通过，包含 TypeScript 检查 |
| 模拟 API 浏览器测试 | Edge 中 15 项通过：原有 Phase 2 的 9 项、Phase 3 的 3 项、新增 Phase 4 的 3 项 |
| 自动化后端测试 | `mvn verify` 构建成功；52 项中 47 项通过、5 项 skipped |
| 本地磁盘文件测试 | GoalDetailServiceTest 的 9 项使用真实临时目录、真实文件、模拟 Mapper；包含 COMPLETION 关联与提交保留、回滚、重启清理和路径安全 |
| 真实 MySQL 测试 | 本进程无 DB_USERNAME / DB_PASSWORD，Phase 1 的 3 项、Phase 3 的 1 项、Phase 4 的 1 项明确跳过 |

**模拟 API 浏览器测试不等于真实浏览器 → 后端 → MySQL → 磁盘验收。** 本次没有使用 skeleton 或内存数据库代替 MySQL。Phase 1～3 的真实链路验收由用户在本机确认；Phase 4 的真实链路需按下文验证。

已查看 1440px 完成弹窗和年度时间轴截图，使用暖米白与暖棕灰，无游戏化效果。浏览器测试也检查 390px 时间轴无水平溢出。

## 完成动作与数据一致性

- POST complete 新建或复用现有 goal_completion；goal_id 唯一约束继续使用原表定义。
- 完成前以 `SELECT ... FOR UPDATE` 锁定事项，保存完成日期、可空感想、可空 1～5 评分和完成前状态，再写 status=COMPLETED。
- 同一数据库事务调用 Phase 3 GoalDetailService.upload 保存选中的证明。文件 stage=COMPLETION、record_id=NULL、goal_id 为当前事项。
- 完成弹窗选择文件时不发送上传请求；取消不会生成附件。确认才提交一个 JSON 或 multipart 请求。
- multipart 包含 application/json 类型的 `completion` 部分及零到多个 `files` 部分。
- 本次选择的文件合计最多 50 MB，沿用既有 51 MB 请求限制；可以完成后通过“编辑完成档案”分次补充。
- 数据库异常或 IOException 导致整笔完成操作回滚。文件沿用 Phase 3 的日志、事务回调及重试清理；不新增第二套存储机制。
- 已完成时重复调用 complete 返回 409，避免重复提交证明；编辑使用 PUT completion。
- 如果请求因网络中断无法判断结果，先取消弹窗并刷新查看当前档案，再决定是否重试。
- 原基础状态修改接口仍不能偷偷完成或撤销完成；基础编辑现在也锁定事项，避免并发写入覆盖正式完成状态。
- GET goals/range、search 和单项响应增加只读 completedDate；它从当前完成档案读取，并非 life_goal 新增数据库列。

## 撤销与再次完成

POST uncomplete 仅恢复 status_before_completion。若缺少可靠历史状态，安全回退 NOT_STARTED。

不删除 goal_completion，不删除 COMPLETION 附件。未完成状态下卡片不再显示完成日期，时间轴不再包含此事项；保留的证明仍在全部附件中，仍可作为封面。

再次完成复用同一条档案，预填旧感想、评分并展示保留证明，默认完成日期为当天。新的 status_before_completion 取这次完成前的状态。修改已完成档案不会覆盖它的原完成前状态。

完成全部可选条件不会自动完成事项。

## 时间轴

- GET /api/timeline 返回年份及数量。
- GET /api/timeline/{year} 返回 year、completedDate、slotNo、title。
- 查询 goal_completion JOIN life_goal，只选 status=COMPLETED。
- 年份包含有数据的年份与当前年份，降序；默认展开当前年。
- 年内按 completed_date ASC、slot_no ASC，保证同一天顺序稳定。
- 页面只展示日期、编号、标题，整行跳转详情；没有照片、评分、感想和年度总结。
- 直接查询当前档案，没有冗余时间轴表或缓存。修改日期、撤销或删除事项后，重新查询自然反映变化。

## Windows 真实 MySQL 测试

在已经设置 DB_USERNAME / DB_PASSWORD 的 PowerShell 会话执行；不需要把凭据写入项目文件。建议 DB_URL 指向专用测试数据库。

```powershell
cd D:\code\myself\Life1000\backend
mvn '-Dmaven.repo.local=../.cache/maven' '-Dtest=Phase4MysqlIntegrationTest' test
```

完整回归：

```powershell
mvn '-Dmaven.repo.local=../.cache/maven' verify
```

Phase 4 测试明确使用 mysql profile，要求 996、997 为空；如果被占用，先失败，不删除原有数据。全量测试还需要 Phase 1 的 001、002、1000，以及 Phase 3 的 998、999 为空。

Phase 4 测试实际提交事务，使用临时上传目录，在 finally 清理自己创建的事项；登录测试凭据在内存生成。若强制终止测试，finally 可能无法执行，需确认剩余事项确为测试数据后人工清理。必须确认 Phase4MysqlIntegrationTest 的 1 项实际执行且通过，skipped 不能算真实验收。

真实测试覆盖：

1. 日期必填，感想/评分可空，非法评分被拒绝。
2. 图片/文档证明、stage、record_id、goal_id 和真实预览下载。
3. 状态与 status_before_completion 正确，卡片响应包含完成日期。
4. 撤销恢复原状态，档案 ID 与文件保留。
5. 再次完成不新增重复档案，重复完成请求返回 409。
6. 年份数量与年内稳定排序；修改完成日期迁移年份。
7. 完成后基础编辑和过程记录仍可使用。
8. 删除完成图片清理文件并解除封面，删除已完成事项清理档案/附件/时间轴。
9. 在完成档案、状态及证明文件写入后，主动触发真实 MySQL CHECK 失败，验证整笔事务回滚且新文件被清理。

## Windows 启动

沿用 README 中 Phase 1～3 已配置的数据库和登录环境变量，以及原上传目录：

```powershell
cd D:\code\myself\Life1000\backend
# 如此前设置过该变量，继续使用相同目录，避免旧附件无法读取。
# $env:LIFE1000_UPLOAD_DIRECTORY = 'D:\Life1000Data\uploads'
mvn '-Dmaven.repo.local=../.cache/maven' spring-boot:run '-Dspring-boot.run.profiles=mysql'
```

另开前端终端：

```powershell
cd D:\code\myself\Life1000\frontend
npm run dev
```

登录后进入 http://127.0.0.1:5173/goals，时间轴为 /timeline。

## 本机人工验收

选择一个空白编号（例如 027，若已有事项请换空白编号），准备图片和 PDF/TXT 等文档。

1. 写下事项，进入详情改为“进行中”，添加过程记录。
2. 回到千事页，点击卡片 ○。选择文件后取消，检查事项仍进行中且无新增完成附件；列表入口应打开同样弹窗。
3. 再打开弹窗，清空日期，应不能提交。只填写日期完成一次，验证可不填写感想、评分、证明。
4. 检查原编号卡片变为日期与 ✓，短反馈出现后消失。刷新，状态和日期仍保留。
5. 进入详情，“完成之后”只显示已填写内容；没有空星级或巨大空感想区。
6. 编辑完成档案，填写感想、评分，上传图片和文档。刷新，检查证明在“完成之后”和“全部附件”均存在。
7. 图片预览、文档下载应正常；完成图片可以自动成为最新封面、手动选为封面，并切换背景候选标记。
8. 在已完成事项上修改标题、分类、为什么想做，增加过程记录及普通/过程附件；仍应正常。
9. 更多 → 撤销完成，先取消再确认。状态恢复进行中，完成档案和证明保留，卡片不再显示完成日期。
10. 打开时间轴，确认该编号不在任何年度节点中。
11. 再次标记完成，检查之前的感想、评分、证明已载入；确认后数据库仍只有一条该 goal_id 的 goal_completion。
12. 修改完成日期到另一个年份，进入时间轴验证旧年减少、新年增加。再将感想和评分清空，刷新确认确实清空。
13. 同一天完成另一个空白编号，反复展开或刷新，节点始终按固定编号排序。
14. 检查年份最新在上、当前年默认展开、其他年默认收起；无记录的当前年显示“这一年没有完成记录。”
15. 点击时间轴整行，确认跳转原 slot_no 的事项详情。
16. 删除一份完成证明并确认，检查元数据和磁盘文件消失；删除指定封面时应正确回退。
17. 清空已完成编号并确认，检查 goal_completion、goal_record、goal_check_item、goal_attachment 无残留，对应物理文件删除，时间轴节点消失，原编号恢复空白、其他编号不移动。

可用 SQL（替换 27 和记录下的 goal_id）：

```sql
SELECT g.id, g.slot_no, g.status, c.id AS completion_id, c.completed_date,
       c.completion_note, c.rating, c.status_before_completion
FROM life_goal g LEFT JOIN goal_completion c ON c.goal_id=g.id
WHERE g.slot_no=27;

SELECT id, goal_id, record_id, stage, file_path FROM goal_attachment WHERE goal_id=123;

SELECT YEAR(c.completed_date) AS year, COUNT(*) AS count
FROM goal_completion c JOIN life_goal g ON g.id=c.goal_id
WHERE g.status='COMPLETED'
GROUP BY YEAR(c.completed_date) ORDER BY year DESC;
```

Windows 文件被占用时沿用每 60 秒重试清理机制；释放占用后确认文件被删除。本阶段不新增备份恢复、部署或一键启动功能。

## 后续范围

未实现 Phase 5 的完整首页、固定/随机背景使用、随机金句、年度时间比例、金句 CRUD 和数字统计；未实现 Phase 6 的设置、分类管理 UI、文件占用统计、ZIP 导出及统一打磨。

备份导入、AI、提醒和 Deadline 仍是 V1 明确不做的功能，不作为本阶段或后续默认实现项。
