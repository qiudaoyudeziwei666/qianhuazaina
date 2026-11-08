# -*- coding: utf-8 -*-
"""「钱花在哪」GitHub 版本发布工具 —— 选择性发布 + 版本标签 + 回滚。

核心约定（重要）：
  * 本地可以随便改、随便 git commit，**不合格的改动不会自动上传**。
  * 只有执行 --publish 才发布，且发布的是「某个已提交的 Git 版本」的内容，
    不是工作区当前文件；未提交的改动天然不会混进去。
  * 每次发布在云端追加一条提交（parent 指向上一版），并打一个递增版本标签 vN，
    因此任何历史版本都能被找到和回滚。
  * 本地前几次提交里曾含已清理的隐私内容，所以**不推本地整条历史**；
    云端是一条从干净初始提交开始的独立版本链。

为什么不用 git push：
  本机代理放行 api.github.com，但拦截 github.com 的 Git 协议（CONNECT 502）。
  所以走 GitHub REST / Git Data API。

凭据（按优先级）：
  1. 环境变量 GITHUB_TOKEN
  2. 同目录 .github-token（纯文本，已 gitignore）
  3. 同目录 local_auth.py 的 token() —— 读取本机 GitHub Desktop 已登录凭据，
     该文件只存在于本机、不提交、不上传。

常用命令：
  python publish-github.py                       # 只看状态（默认不改任何东西）
  python publish-github.py --status              # 同上：云端版本 / 本地 HEAD / 差异
  python publish-github.py --diff                # 列出本地 HEAD 与云端的差异文件
  python publish-github.py --list                # 列出云端所有版本标签
  python publish-github.py --dry-run             # 演练发布：导出+隐私复查，不联网写
  python publish-github.py --publish             # 发布当前 HEAD 为新版本
  python publish-github.py --publish --ref 1309bcd --note "修好预算图表"
  python publish-github.py --publish --version v5
  python publish-github.py --rollback v1         # 安全回滚：新提交，内容恢复到 v1
  python publish-github.py --rollback v1 --hard  # 强制把 main 指回 v1（不新增提交）
  python publish-github.py --rollback v1 --dry-run
  python publish-github.py --baseline            # 给云端当前 main 打 v1 基线标签
  python publish-github.py --check               # 校验 token 与 API
"""
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = os.path.dirname(os.path.abspath(__file__))
SNAPSHOT_ROOT = os.path.join(tempfile.gettempdir(), "qhn-release")
API = "https://api.github.com"
DEFAULT_NAME = "qianhuazaina"
DESCRIPTION = "钱花在哪 · 本地优先的安卓自动记账应用（通知识别 / 智能分类 / 可视化分析 / 预算 / 储蓄）"

PRIVACY_PATTERNS = {
    # 拼接构造，避免本文件自身出现完整字面串而被隐私扫描误判
    "设备序列号": r"7340" r"343b",
    "本机用户路径": r"[Cc]:[\\/]+Users[\\/]+fen",
    "项目绝对路径": r"[Cc]:[\\/]+github项目",
    "疑似 GitHub Token": r"(?i)gh[pousr]_[A-Za-z0-9]{20,}",
    "疑似 OpenAI Key": r"(?i)sk-[A-Za-z0-9]{20,}",
}
EXECUTABLES = ("gradlew",)


def out(*args):
    print(*args, flush=True)


def arg_value(flag, default=None):
    if flag in sys.argv:
        i = sys.argv.index(flag)
        if i + 1 < len(sys.argv):
            return sys.argv[i + 1]
    return default


def git_bytes(*args):
    r = subprocess.run(["git"] + list(args), cwd=BASE, capture_output=True)
    if r.returncode != 0:
        raise SystemExit("git %s 失败：%s" % (" ".join(args), r.stderr.decode("utf-8", "replace")[:200]))
    return r.stdout


def git_text(*args):
    return git_bytes(*args).decode("utf-8", "replace").strip()


def local_tree(ref):
    """读取某个已提交 Git 版本的全部文件：path -> (mode, blob_sha, content)。"""
    raw = git_bytes("ls-tree", "-r", "-z", ref)
    entries = {}
    for item in raw.split(b"\0"):
        if not item:
            continue
        meta, path_bytes = item.split(b"\t", 1)
        mode, _typ, sha = meta.split()
        path = path_bytes.decode("utf-8", "replace")
        content = git_bytes("show", "%s:%s" % (ref, path))
        entries[path] = (mode.decode(), sha.decode(), content)
    return entries


def privacy_check(entries):
    bad = []
    for rel, (_mode, _sha, content) in entries.items():
        text = content.decode("utf-8", "replace")
        for name, pattern in PRIVACY_PATTERNS.items():
            if re.search(pattern, text):
                bad.append((rel, name))
    return bad


def read_token():
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if token:
        return token
    path = os.path.join(BASE, ".github-token")
    if os.path.isfile(path):
        token = open(path, encoding="utf-8", errors="replace").read().strip()
        if token:
            return token
    try:
        from local_auth import token as desktop_token
        return (desktop_token() or "").strip()
    except Exception:
        return ""


def api(method, path, token, payload=None):
    url = API + path
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    req.add_header("User-Agent", "qhn-publisher/2.0")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, (json.loads(body) if body else {})
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        try:
            detail = json.loads(body)
        except Exception:
            detail = body
        return e.code, detail
    except Exception as e:
        return 0, {"error": "%s: %s" % (type(e).__name__, e)}


def repo_context(token, repo_name):
    status, user = api("GET", "/user", token)
    if status != 200:
        raise SystemExit("Token 校验失败（HTTP %s）：%s" % (status, str(user)[:300]))
    owner = user.get("login")
    repo_path = "/repos/%s/%s" % (owner, repo_name)
    status, info = api("GET", repo_path, token)
    if status != 200:
        raise SystemExit("仓库不存在或不可访问（HTTP %s）：%s" % (status, str(info)[:200]))
    return owner, repo_path, (info.get("default_branch") or "main")


def main_ref(repo_path, token, branch):
    status, ref = api("GET", repo_path + "/git/ref/heads/" + branch, token)
    if status != 200 or not isinstance(ref, dict):
        return None
    return ref.get("object", {}).get("sha")


def cloud_tree(repo_path, token, commit_sha):
    status, commit = api("GET", repo_path + "/git/commits/" + commit_sha, token)
    if status != 200:
        return {}, None
    tree_sha = commit.get("tree", {}).get("sha")
    status, tree = api("GET", repo_path + "/git/trees/" + tree_sha + "?recursive=1", token)
    if status != 200:
        return {}, tree_sha
    return {e["path"]: e["sha"] for e in tree.get("tree", []) if e.get("type") == "blob"}, tree_sha


def list_tags(repo_path, token):
    status, refs = api("GET", repo_path + "/git/refs/tags", token)
    if status != 200 or not isinstance(refs, list):
        return []
    result = []
    for r in refs:
        name = r.get("ref", "").split("/")[-1]
        sha = r.get("object", {}).get("sha", "")
        status, c = api("GET", repo_path + "/git/commits/" + sha, token)
        result.append({
            "name": name,
            "sha": sha,
            "message": (c.get("message", "").splitlines() or [""])[0] if isinstance(c, dict) else "",
            "date": c.get("committer", {}).get("date", "") if isinstance(c, dict) else "",
        })
    return sorted(result, key=lambda x: x["name"])


def next_version(tags, explicit=None):
    if explicit:
        return explicit if explicit.startswith("v") else "v" + explicit
    nums = [int(m.group(1)) for t in tags if (m := re.match(r"^v(\d+)$", t["name"]))]
    return "v%d" % (max(nums) + 1 if nums else 1)


def create_tag(repo_path, token, name, sha):
    status, res = api("POST", repo_path + "/git/refs", token,
                      {"ref": "refs/tags/" + name, "sha": sha})
    return status in (200, 201)


def diff_summary(local, cloud):
    added = sorted(p for p in local if p not in cloud)
    removed = sorted(p for p in cloud if p not in local)
    changed = sorted(p for p in local if p in cloud and local[p][1] != cloud[p])
    return added, changed, removed


# ---------------------------------------------------------------- 命令实现

def cmd_status(token, repo_name, ref):
    owner, repo_path, branch = repo_context(token, repo_name)
    sha = main_ref(repo_path, token, branch)
    tags = list_tags(repo_path, token)
    out("=== 发布状态（只读） ===")
    out("  云端仓库 : https://github.com/%s/%s" % (owner, repo_name))
    out("  当前分支 : %s @ %s" % (branch, (sha or "?")[:10]))
    out("  版本标签 : %s" % (", ".join(t["name"] for t in tags) if tags else "（尚无，建议先 --baseline）"))
    head = git_text("rev-parse", "HEAD")
    subject = git_text("log", "-1", "--format=%s")
    dirty = len([l for l in git_text("status", "--porcelain").splitlines() if l])
    out("  本地 HEAD: %s  %s" % (head[:10], subject[:60]))
    out("  未提交改动: %d 个文件（这些不会被发布）" % dirty)
    if sha:
        cloud, _ = cloud_tree(repo_path, token, sha)
        local = local_tree(ref)
        added, changed, removed = diff_summary(local, cloud)
        out("  与云端差异: 新增 %d / 修改 %d / 删除 %d" % (len(added), len(changed), len(removed)))
        if added or changed or removed:
            out("  提示：确认合格后再 --publish；不合格的改动留在本地即可。")
    return tags


def cmd_diff(token, repo_name, ref):
    owner, repo_path, branch = repo_context(token, repo_name)
    sha = main_ref(repo_path, token, branch)
    cloud, _ = cloud_tree(repo_path, token, sha)
    local = local_tree(ref)
    added, changed, removed = diff_summary(local, cloud)
    out("=== 本地 %s 与云端 main 的差异 ===" % ref)
    out("  本地 %d 个文件 / 云端 %d 个文件" % (len(local), len(cloud)))
    out("  新增 %d、修改 %d、删除 %d" % (len(added), len(changed), len(removed)))
    for label, items in (("新增", added), ("修改", changed), ("删除", removed)):
        for p in items[:20]:
            out("    %s  %s" % (label, p))
        if len(items) > 20:
            out("    ... 另有 %d 个" % (len(items) - 20))


def cmd_list(token, repo_name):
    owner, repo_path, branch = repo_context(token, repo_name)
    sha = main_ref(repo_path, token, branch)
    tags = list_tags(repo_path, token)
    out("=== 云端版本（仓库 %s/%s） ===" % (owner, repo_name))
    if not tags:
        out("  尚无版本标签。执行 --baseline 给当前内容打 v1。")
    for t in tags:
        mark = " ← 当前" if t["sha"] == sha else ""
        out("  %-6s %s  %s  %s%s" % (t["name"], t["sha"][:8], t["date"][:19], t["message"][:52], mark))
    out("  回滚：python publish-github.py --rollback vN")


def cmd_baseline(token, repo_name, dry):
    owner, repo_path, branch = repo_context(token, repo_name)
    sha = main_ref(repo_path, token, branch)
    if not sha:
        raise SystemExit("云端还没有任何提交，无法打基线标签。")
    tags = list_tags(repo_path, token)
    if any(t["name"] == "v1" for t in tags):
        out("v1 基线标签已存在，无需重复执行。")
        return
    if dry:
        out("[dry-run] 将给 %s 打上 v1 基线标签" % sha[:8])
        return
    ok = create_tag(repo_path, token, "v1", sha)
    out(("已打基线标签 v1 → %s" % sha[:8]) if ok else "打标签失败（可能权限不足）")


def cmd_publish(token, repo_name, ref, note, version, dry):
    owner, repo_path, branch = repo_context(token, repo_name)
    resolved = git_text("rev-parse", ref)
    subject = git_text("log", "-1", "--format=%s", ref)
    entries = local_tree(ref)
    out("=== 发布准备 ===")
    out("  来源提交: %s  %s" % (resolved[:10], subject[:60]))
    out("  文件数量: %d（只来自这一个 Git 提交，未提交改动不含在内）" % len(entries))
    bad = privacy_check(entries)
    if bad:
        out("❌ 隐私复查未通过，已中止：")
        for rel, name in bad[:20]:
            out("   %s -> %s" % (rel, name))
        raise SystemExit(1)
    out("  隐私复查: 通过（0 处命中）")

    if "--export" in sys.argv:
        dest = os.path.join(SNAPSHOT_ROOT, resolved[:10])
        for rel, (_m, _s, content) in entries.items():
            p = os.path.join(dest, rel.replace("/", os.sep))
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, "wb") as f:
                f.write(content)
        out("  快照已导出: " + dest)

    tags = list_tags(repo_path, token)
    if not tags:
        out("  云端尚无版本标签，稍后会自动补 v1 基线。")
    current_sha = main_ref(repo_path, token, branch)
    cloud, _ = cloud_tree(repo_path, token, current_sha) if current_sha else ({}, None)
    added, changed, removed = diff_summary(entries, cloud)
    out("  与云端差异: 新增 %d / 修改 %d / 删除 %d" % (len(added), len(changed), len(removed)))
    if not (added or changed or removed):
        out("  内容没有变化，无需发布新版本。")
        return

    ver = next_version(tags, version)
    # 云端尚无标签时，先给现有内容留 v1 基线，本次发布顺延为 v2
    if not tags and current_sha and not version:
        ver = "v2"
    if dry:
        out("  [dry-run] 将发布为 %s（commit 基于 %s，%d 个变化文件），不写入云端。" %
            (ver, (current_sha or "root")[:8], len(added) + len(changed) + len(removed)))
        if not tags and current_sha:
            out("           同时会把现有内容标为 v1 基线。")
        return

    # 第一次给已有内容补基线标签，保证版本链从 v1 开始
    if not tags and current_sha:
        create_tag(repo_path, token, "v1", current_sha)
        out("  已补基线标签 v1")

    out("  上传变化文件（未变化文件直接复用云端 blob）...")
    tree = []
    uploaded = 0
    for i, (rel, (mode, sha, content)) in enumerate(sorted(entries.items()), 1):
        if cloud.get(rel) == sha:
            blob_sha = sha
        else:
            status, blob = api("POST", repo_path + "/git/blobs", token, {
                "content": base64.b64encode(content).decode("ascii"),
                "encoding": "base64",
            })
            if status not in (200, 201) or "sha" not in blob:
                raise SystemExit("上传文件失败 %s（HTTP %s）：%s" % (rel, status, str(blob)[:200]))
            blob_sha = blob["sha"]
            uploaded += 1
        tree.append({
            "path": rel,
            "mode": "100755" if os.path.basename(rel) in EXECUTABLES else mode,
            "type": "blob",
            "sha": blob_sha,
        })
        if i % 30 == 0 or i == len(entries):
            out("      %d/%d（新上传 %d）" % (i, len(entries), uploaded))

    status, tree_obj = api("POST", repo_path + "/git/trees", token, {"tree": tree})
    if status not in (200, 201) or "sha" not in tree_obj:
        raise SystemExit("创建目录树失败（HTTP %s）：%s" % (status, str(tree_obj)[:250]))
    message = "release %s | local %s | %s" % (ver, resolved[:10], note or subject[:60])
    payload = {"message": message, "tree": tree_obj["sha"]}
    if current_sha:
        payload["parents"] = [current_sha]
    status, commit = api("POST", repo_path + "/git/commits", token, payload)
    if status not in (200, 201) or "sha" not in commit:
        raise SystemExit("创建提交失败（HTTP %s）：%s" % (status, str(commit)[:250]))

    if current_sha:
        status, ref_res = api("PATCH", repo_path + "/git/refs/heads/" + branch,
                              token, {"sha": commit["sha"], "force": False})
    else:
        status, ref_res = api("POST", repo_path + "/git/refs", token,
                              {"ref": "refs/heads/" + branch, "sha": commit["sha"]})
    if status not in (200, 201):
        raise SystemExit("更新分支失败（HTTP %s）：%s" % (status, str(ref_res)[:250]))

    create_tag(repo_path, token, ver, commit["sha"])
    out("✅ 已发布 %s → %s" % (ver, commit["sha"][:8]))
    out("   仓库：https://github.com/%s/%s" % (owner, repo_name))
    out("   回滚：python publish-github.py --rollback %s" % ver)


def cmd_rollback(token, repo_name, target, hard, dry, note):
    owner, repo_path, branch = repo_context(token, repo_name)
    sha = main_ref(repo_path, token, branch)
    tags = list_tags(repo_path, token)
    by_name = {t["name"]: t["sha"] for t in tags}
    target_sha = by_name.get(target, target)
    status, commit = api("GET", repo_path + "/git/commits/" + target_sha, token)
    if status != 200 or not isinstance(commit, dict):
        raise SystemExit("找不到目标版本 %s（用 --list 查看可用版本）" % target)
    tree_sha = commit.get("tree", {}).get("sha")
    out("=== 回滚 ===")
    out("  目标版本: %s @ %s" % (target, target_sha[:8]))
    out("  当前 main: %s" % sha[:8])
    if hard:
        out("  模式: 强制移动 main 指针（不新增提交，之后的提交不再出现在 main 历史里）")
        if dry:
            out("  [dry-run] 不写入云端。")
            return
        st, res = api("PATCH", repo_path + "/git/refs/heads/" + branch, token,
                      {"sha": target_sha, "force": True})
        if st not in (200, 201):
            raise SystemExit("强制回滚失败（HTTP %s）：%s" % (st, str(res)[:250]))
        out("✅ main 已指回 %s（%s）" % (target, target_sha[:8]))
        return

    ver = next_version(tags)
    out("  模式: 安全回滚 —— 新建一条提交，内容恢复到 %s，历史线性不丢" % target)
    if dry:
        out("  [dry-run] 将创建 %s（内容=%s，parent=%s），不写入云端。" % (ver, target, sha[:8]))
        return
    payload = {
        "message": "rollback %s -> %s | %s" % (ver, target, note or "内容恢复到历史版本"),
        "tree": tree_sha,
        "parents": [sha],
    }
    st, new_commit = api("POST", repo_path + "/git/commits", token, payload)
    if st not in (200, 201) or "sha" not in new_commit:
        raise SystemExit("创建回滚提交失败（HTTP %s）：%s" % (st, str(new_commit)[:250]))
    st, res = api("PATCH", repo_path + "/git/refs/heads/" + branch, token,
                  {"sha": new_commit["sha"], "force": False})
    if st not in (200, 201):
        raise SystemExit("更新分支失败（HTTP %s）：%s" % (st, str(res)[:250]))
    create_tag(repo_path, token, ver, new_commit["sha"])
    out("✅ 已回滚到 %s 的内容，新版本 %s @ %s" % (target, ver, new_commit["sha"][:8]))
    out("   （之前的版本仍可通过 --list 找到，随时可再回滚回去）")


def main():
    argv = sys.argv[1:]
    repo_name = arg_value("--name", DEFAULT_NAME)
    ref = arg_value("--ref", "HEAD")
    note = arg_value("--note", "")
    version = arg_value("--version")
    dry = "--dry-run" in argv
    hard = "--hard" in argv
    check = "--check" in argv

    if check:
        token = read_token()
        if not token:
            raise SystemExit("缺少 GitHub 凭据（GITHUB_TOKEN / .github-token / local_auth.py）。")
        status, user = api("GET", "/user", token)
        out("Token 校验：HTTP %s，账号=%s" % (status, user.get("login", user.get("message", "?"))))
        return

    # 纯本地演练：不联网、不写云端
    if "--dry-run" in argv and not any(a in argv for a in ("--publish", "--rollback", "--baseline")):
        entries = local_tree(ref)
        out("=== 本地演练（不联网） ===")
        out("  来源提交: %s" % git_text("rev-parse", ref)[:10])
        out("  文件数量: %d" % len(entries))
        bad = privacy_check(entries)
        if bad:
            out("❌ 隐私复查未通过：")
            for rel, name in bad[:20]:
                out("   %s -> %s" % (rel, name))
            raise SystemExit(1)
        out("  隐私复查: 通过（0 处命中）")
        out("  [dry-run] 未联网、未创建任何版本。合格后再执行 --publish。")
        return

    token = read_token()
    if not token:
        raise SystemExit("缺少 GitHub 凭据：设置 GITHUB_TOKEN、创建 .github-token，或放置 local_auth.py。")

    if "--list" in argv:
        cmd_list(token, repo_name)
    elif "--diff" in argv:
        cmd_diff(token, repo_name, ref)
    elif "--baseline" in argv:
        cmd_baseline(token, repo_name, dry)
    elif "--rollback" in argv:
        target = arg_value("--rollback")
        if not target or target.startswith("--"):
            raise SystemExit("请指定版本，例如 --rollback v1（可用 --list 查看）")
        cmd_rollback(token, repo_name, target, hard, dry, note)
    elif "--publish" in argv:
        cmd_publish(token, repo_name, ref, note, version, dry)
    else:
        cmd_status(token, repo_name, ref)


if __name__ == "__main__":
    main()
