# 版本发布与回滚操作说明

## 先讲人话：这套东西分三层

| 东西 | 类比 | 会不会自动上传 |
| --- | --- | --- |
| 工作区改动 | 草稿纸上随便写 | **不会** |
| 本地 `git commit` | 给草稿拍一张编号存档，随便拍多少张 | **不会** |
| `--publish` | 挑一张合格的存档，盖章成「正式版 vN」发出去 | **只有这一步会** |

所以「改动很多、但并非每次都合格」是被支持的：不合格的改动就留在本地，永远不盖章。云端只记录被盖章的版本，每个版本都有编号（tag），随时能取回旧的。

**为什么不用 `git push`**：本地前几次提交的历史里曾含已清理掉的本机信息，推整条历史会把它们留在云端可翻的记录里；而且本机代理拦截 GitHub 的 Git 协议。所以发布走 GitHub API，只传「某个合格提交的文件内容」，形成一条**独立的干净版本链**。本地 9 次阶段提交与云端版本链是两套，互不干扰。

## 日常流程（照着做）

1. 正常改代码，`git add` + `git commit`，**想提交几次就几次**。
2. 觉得这版可能合格了 → 双击 `publish-github.bat` 选 **1**（看状态）或 **3**（看差异）。
3. 跑测试确认合格 → 选 **4** 演练（隐私复查 + 内容检查，不写云端）。
4. 确认没问题 → 选 **5** 发布，脚本自动追加一条提交并打上 `v2`、`v3`… 标签。
5. 万一发出去发现问题 → 选 **2** 看版本列表，选 **6** 输入旧版本号回滚。

## 命令速查

| 目的 | 命令 |
| --- | --- |
| 看状态（默认，只读） | `python publish-github.py` |
| 列出云端所有版本 | `python publish-github.py --list` |
| 本地 HEAD 与云端差异 | `python publish-github.py --diff` |
| 演练（不写云端） | `python publish-github.py --dry-run` |
| 发布当前已提交版本 | `python publish-github.py --publish` |
| 发布某个旧提交 | `python publish-github.py --publish --ref 1309bcd --note "修好预算图表"` |
| 自定义版本号 | `python publish-github.py --publish --version v5` |
| 演练发布（读云端、不写） | `python publish-github.py --publish --dry-run` |
| 安全回滚 | `python publish-github.py --rollback v1` |
| 演练回滚 | `python publish-github.py --rollback v1 --dry-run` |
| 强制回退 main 指针 | `python publish-github.py --rollback v1 --hard` |
| 给现有内容补 v1 基线 | `python publish-github.py --baseline` |
| 校验 GitHub 登录 | `python publish-github.py --check` |
| 导出快照到临时目录 | `python publish-github.py --dry-run --export` |

## 发布前的三个自检提问

- **这一版跑过测试了吗？**（`python run-tests-ascii.py`，目前 57 项）
- **改动都 `git commit` 了吗？** 未提交的改动**不会**被发布，发布前状态页会明确告诉你有几个。
- **隐私复查通过了吗？** 每次发布都会扫设备序列号、本机路径、密钥；命中就直接中止。

## 两种回滚的区别

| 方式 | 做了什么 | 适用 |
| --- | --- | --- |
| `--rollback v1`（**默认，推荐**） | 新建一条提交，内容恢复到 v1，`main` 继续往前走；打上新标签 | 线上版本有问题，要退回旧内容但保留完整历史、还能再切回来 |
| `--rollback v1 --hard` | 直接把 `main` 指针移回 v1，不新增提交 | 想让分支看起来「从没发过后面那几版」；之后的提交仍可通过版本标签找到 |

安全回滚后，旧版本依然在 `--list` 里，可以再回滚回新版本，不会丢东西。

## 必须知道的坑

1. **不要直接 `git push`**：本地历史含早期未脱敏内容，直接推会污染公开仓库。要更新云端只用本工具。
2. **发布的是「已提交版本」，不是工作区**：`--ref HEAD` 取的是 HEAD 的文件树；未提交、未 `git add` 的改动一律不带。这是刻意设计，避免不合格草稿混进正式版。
3. **每次发布是全量内容 + 增量上传**：只上传内容变化的 blob，未变文件复用云端对象；但每条提交代表该版本的完整文件树，所以回滚能精确还原。
4. **版本号自动递增**：从云端最大的 `vN` 加一；首次发布若云端还没有标签，会自动给当前内容补 `v1` 基线，再发 `v2`。
5. **凭据**：优先 `GITHUB_TOKEN` → `.github-token` → 本机 `local_auth.py`（读 GitHub Desktop 登录态）。`local_auth.py` 只在开发机存在，已加入 `.gitignore`，不会被提交或上传。
6. **本机代理**：放行 `api.github.com`、拦截 `github.com` 的 Git 协议；工具走 REST API，所以 `--check` 通但 `git push` 不通是正常现象。
7. **云端与本地提交哈希不互通**：云端提交是 API 新建的，message 里记录了对应的本地提交短哈希（`local xxxxx`），用于追溯「这个正式版来自哪次本地提交」。
