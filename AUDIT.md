# 「钱花在哪」项目审计（第一阶段）

审计对象：本 Git 仓库根目录（2026-11-08）
参考原型：用户提供的单文件 HTML 交互原型（薄荷绿 / 首页 / 分析 / 预算 / 储蓄 / 订阅）

## 1. 技术栈（不得更换）

| 项 | 现状 |
| --- | --- |
| 语言 / UI | Kotlin 2.0.21 + Jetpack Compose（BOM 2024.10.01）+ Material3 |
| 数据库 | Room 2.6.1，KSP，`zhizhang.db`，**version = 1**，`exportSchema = true` |
| 异步 | Coroutines + Flow + `viewModelScope` |
| 架构 | 单 Activity + `MainViewModel` + `LedgerRepository` + `SettingsStore`(SharedPreferences) |
| 后台 | `NotificationListenerService` + 前台服务 `GuardForegroundService` + `BootReceiver` + 桌面小组件 |
| 网络 | `HttpURLConnection`（仅用户手动批量 AI 分类时） |
| 构建 | Gradle 8.9 / AGP + Kotlin Android + Compose compiler + KSP，minSdk 26 / targetSdk 34 |
| 测试 | JUnit4 + kotlin-test，现有 29 项纯 JVM 单元测试 |

## 2. 已有功能与缺失功能对照

| 需求模块 | 现状 | 缺口 |
| --- | --- | --- |
| 自动记账 | ✅ 通知白名单、金额/商户/方向提取、去重、待确认、诊断日志、ADB 授权、常驻服务、开机自启 | 批量确认/忽略/修改、规则学习、异常恢复 UI |
| 智能分类 | ✅ 本地关键词、手动批量 AI（单次请求，≤20 笔）、Key 加密 | 分层优先级、置信度、修改历史、常用商户规则管理、API 调用统计 |
| 首页 | ✅ 本月支出/收入/已确认笔数、待确认合计、自动记账状态、最近流水 | 结余、环比、日均、预算剩余、趋势迷你图、储蓄进度、卡片自定义 |
| 统计分析 | ❌ 无独立页面 | 柱/线/环/热力/排行、筛选、点击联动、导出、待确认隔离 |
| 预算 | ⚠️ 只有 `monthlyBudgetCents` 一个存储值，UI 未使用 | 总预算、分类预算、使用率、剩余、超支预测、阈值提醒 |
| 储蓄目标 | ❌ | CRUD、进度、达成日期推算、月建议储蓄 |
| 订阅管理 | ❌ | CRUD、月/季/年、年化成本、到期提醒 |
| 消费洞察 | ❌ | 习惯变化、异常大额、非必要占比、固定支出、结余率、建议 |
| 账单管理 | ⚠️ 搜索 + 单笔编辑/删除 + CSV 导出 | 组合筛选、批量编辑、CSV 导入、JSON 备份恢复、去重、撤销删除、完整性校验 |
| UI/UX | ⚠️ 薄荷绿浅色、卡片圆角、底部 5 tab、FAB 安全间距 | 深色模式、图表、信息层级统一、卡片顺序配置 |
| 数据模型 | ✅ `amountCents: Long`（分，精确）、status/type/source 齐全 | 退款/转账语义未统一；统计口径散落在页面里 |

## 3. 现有架构问题

1. **统计口径分散**：`HomeScreen` 里直接 `filter/sumOf`，按月用 `Calendar` 手算；将来多页面统计必然不一致。→ 抽到纯函数 `core/FinanceStats.kt`，统一「只统计 CONFIRMED」。
2. **主题硬编码**：`Ink/Muted/Green/Mint` 是全局常量，所有页面直接引用 `Color.White`，**深色模式无法生效**。→ 改为 Material3 colorScheme 语义色 + 兼容别名。
3. **ViewModel 职责膨胀**：`MainViewModel` 同时管流水、权限、AI、导出；新增模块需按 repository 分层，避免继续堆积。
4. **数据库无迁移**：`version=1` 且无 `Migration`，直接加实体会清空用户真实账本。→ 必须 `MIGRATION_1_2`，只 `CREATE TABLE`，不改 `ledger_entries`。
5. **删除不可撤销**、导入无去重策略、备份只有 CSV（丢元数据）。
6. **图表库缺失**：Compose 项目不兼容 MPAndroidChart；用 `Canvas` 自绘（零新依赖、性能可控）比引入重型图表框架更合适。

## 4. 可复用模块（不重构）

- `core/PaymentParser.kt` + `SupportedApps.kt` + `CategoryClassifier.kt`：解析与关键词规则，保留并作为分层分类的第 2 层。
- `data/LedgerEntry.kt` / `LedgerDao.kt` / `LedgerRepository.kt`：金额分单位、去重键、Flow 观察，全部保留。
- `service/*`：通知监听、前台常驻、自愈、开机恢复、抓到提醒，全部保留。
- `ui/Components.kt` 的 `AppCard / EntryRow / SourceIcon / money / formatTime`：作为设计系统基础扩展。
- `util/CsvExporter.kt`：导出逻辑扩展为「导出 + 导入」共用字段定义。
- `widget/LedgerWidgetProvider.kt`、`ai/AiClassifier.kt`：保留。

## 5. 数据库兼容风险

| 风险 | 处理 |
| --- | --- |
| 直接改 `@Database(version=2)` 但无迁移 → Room 抛 `IllegalStateException`，用户账本丢失 | 显式 `MIGRATION_1_2`，仅 `CREATE TABLE IF NOT EXISTS` 新表 |
| 修改 `ledger_entries` 列 → 需要 ALTER/重建表，风险高 | **本轮不改旧表**，新功能全部放新表；设置类放 SharedPreferences |
| `exportSchema=true` 但 schema 目录未纳入版本管理 | 提交 `app/schemas/`，并补 v2 schema |
| 导入/恢复覆盖真实数据 | 一律「预览 + 用户确认」才写入，且先自动备份；不提供静默清空 |
| 金额浮点误差 | 全部 `Long` 分；CSV/JSON 用字符串解析为分，禁止 Double 参与汇总 |

## 6. 推荐开发优先级（本轮按此执行）

| 优先级 | 内容 | 理由 |
| --- | --- | --- |
| P0 | 设计系统（深色/语义色/图表基础） | 所有新页面的地基 |
| P0 | Room 1→2 安全迁移 + `FinanceStats` 纯函数 + 分层分类引擎 | 统计口径统一，且可用 JVM 单测验证 |
| P1 | 首页升级（可配置卡片）+ 独立统计页 | 用户最直观的价值，图表可交互 |
| P1 | 预算 / 储蓄 / 订阅 / 洞察页面 | 依赖前述数据与组件 |
| P2 | 分类页增强、CSV 导入、JSON 备份恢复、去重、撤销删除、组合筛选 | 数据完整性 |
| P2 | 测试与验收（编译、单测、设备端验证、完成清单） | 不放过未验证项 |

## 7. 约束

- 不更换框架、不引入重型图表库、不强制付费 API。
- 不对真实账本做任何破坏性写入；演示数据不得注入生产数据库。
- 未实现的功能在最终清单中如实标注，不标记为完成。
