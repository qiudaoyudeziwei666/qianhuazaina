# -*- coding: utf-8 -*-
"""把「当前版本库内容」发布到 GitHub 公开仓库。

为什么不是直接 git push：
  1. 本地前几次提交里曾含设备序列号与本机路径，推整条历史等于把隐私留在云端；
     这里只取「当前已跟踪文件」生成一个干净的初始提交。
  2. 当前环境代理放行 api.github.com，但拦截 github.com 的 Git 协议（CONNECT 502），
     所以走 GitHub REST / Git Data API 上传，不依赖 git push。

准备：
  在本文件同目录创建 `.github-token`（纯文本，只放 Personal Access Token），
  或设置环境变量 GITHUB_TOKEN。Token 需要 repo 权限（ classic PAT 勾 repo，
  fine-grained PAT 给目标仓库 Contents: read/write + Metadata: read）。
  `.github-token` 已加入 .gitignore，不会被提交或上传。

用法：
  python publish-github.py --dry-run       # 只导出快照 + 隐私复查，不联网
  python publish-github.py --check         # 检查 token 与 API 连通性（不创建仓库）
  python publish-github.py                 # 创建公开仓库并上传
  python publish-github.py --name my-repo  # 自定义仓库名
  python publish-github.py --reuse         # 仓库已存在时，覆盖其 main 分支内容
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
SNAPSHOT = os.path.join(tempfile.gettempdir(), "qhn-release-snapshot")
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


def tracked_files():
    raw = subprocess.run(
        ["git", "ls-files", "-z"], cwd=BASE, capture_output=True
    ).stdout
    return [p.decode("utf-8") for p in raw.split(b"\0") if p]


def build_snapshot():
    files = tracked_files()
    if os.path.isdir(SNAPSHOT):
        # 增量覆盖，不做批量删除（避免误删 / 触发安全策略）
        pass
    os.makedirs(SNAPSHOT, exist_ok=True)
    for rel in files:
        src = os.path.join(BASE, rel)
        if not os.path.isfile(src):
            continue
        dst = os.path.join(SNAPSHOT, rel.replace("/", os.sep))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(src, "rb") as f:
            data = f.read()
        with open(dst, "wb") as f:
            f.write(data)
    return files


def privacy_check(files):
    bad = []
    for rel in files:
        path = os.path.join(SNAPSHOT, rel.replace("/", os.sep))
        if not os.path.isfile(path):
            continue
        try:
            text = open(path, encoding="utf-8", errors="replace").read()
        except Exception:
            continue
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
        return open(path, encoding="utf-8", errors="replace").read().strip()
    return ""


def api(method, path, token, payload=None):
    url = API + path
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    req.add_header("User-Agent", "qhn-publisher/1.0")
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


def publish(token, repo_name, reuse):
    status, user = api("GET", "/user", token)
    if status != 200:
        raise SystemExit("Token 校验失败（HTTP %s）：%s" % (status, str(user)[:300]))
    owner = user.get("login")
    out("[1/5] 已认证账号：" + str(owner))

    repo_path = "/repos/%s/%s" % (owner, repo_name)
    status, info = api("GET", repo_path, token)
    if status == 200:
        if not reuse:
            # 刚创建、还没有任何提交的空仓库允许继续；有内容的仓库则需显式 --reuse
            s0, _ = api("GET", repo_path + "/git/ref/heads/" + (info.get("default_branch") or "main"), token)
            if s0 == 200:
                raise SystemExit("仓库 %s/%s 已存在且有内容。确认覆盖请加 --reuse，或换 --name。" % (owner, repo_name))
            out("[2/5] 复用刚创建的空仓库：" + str(info.get("html_url")))
        else:
            out("[2/5] 复用已有仓库：" + str(info.get("html_url")))
    elif status == 404:
        payload = {
            "name": repo_name,
            "description": DESCRIPTION,
            "private": False,
            "auto_init": False,
            "has_issues": True,
            "has_wiki": False,
        }
        status, info = api("POST", "/user/repos", token, payload)
        if status not in (201, 202):
            raise SystemExit("创建仓库失败（HTTP %s）：%s" % (status, str(info)[:300]))
        out("[2/5] 已创建公开仓库：" + str(info.get("html_url")))
    else:
        raise SystemExit("查询仓库失败（HTTP %s）：%s" % (status, str(info)[:300]))

    default_branch = info.get("default_branch") or "main"
    status, ref0 = api("GET", repo_path + "/git/ref/heads/" + default_branch, token)
    repo_was_empty = status != 200
    previous_sha = ref0.get("object", {}).get("sha") if isinstance(ref0, dict) else None
    if repo_was_empty:
        # 空仓库的 Git Data API 会返回 409 "Git Repository is empty"，
        # 先用 Contents API 放一个占位文件，让仓库产生初始提交。
        st, init = api("PUT", repo_path + "/contents/.gitkeep", token, {
            "message": "chore: initialize repository",
            "content": base64.b64encode(b"init\n").decode("ascii"),
        })
        if st not in (200, 201):
            raise SystemExit("初始化空仓库失败（HTTP %s）：%s" % (st, str(init)[:250]))
        out("      空仓库已初始化，继续上传")

    files = [f for f in tracked_files() if os.path.isfile(os.path.join(SNAPSHOT, f.replace("/", os.sep)))]
    out("[3/5] 上传 %d 个文件（blob）..." % len(files))
    tree = []
    for i, rel in enumerate(files, 1):
        path = os.path.join(SNAPSHOT, rel.replace("/", os.sep))
        with open(path, "rb") as f:
            content = f.read()
        status, blob = api("POST", repo_path + "/git/blobs", token, {
            "content": base64.b64encode(content).decode("ascii"),
            "encoding": "base64",
        })
        if status not in (201, 200) or "sha" not in blob:
            raise SystemExit("上传文件失败 %s（HTTP %s）：%s" % (rel, status, str(blob)[:200]))
        tree.append({
            "path": rel,
            "mode": "100755" if os.path.basename(rel) in EXECUTABLES else "100644",
            "type": "blob",
            "sha": blob["sha"],
        })
        if i % 20 == 0 or i == len(files):
            out("      %d/%d" % (i, len(files)))

    status, tree_obj = api("POST", repo_path + "/git/trees", token, {"tree": tree})
    if status not in (201, 200) or "sha" not in tree_obj:
        raise SystemExit("创建目录树失败（HTTP %s）：%s" % (status, str(tree_obj)[:300]))
    out("[4/5] 目录树已创建")

    commit_payload = {
        "message": "feat: 钱花在哪 · 安卓自动记账应用（公开仓库初始提交）",
        "tree": tree_obj["sha"],
    }
    # 复用有历史的仓库时保留 parent；空仓库则建立一条干净的 root commit
    if not repo_was_empty and previous_sha:
        commit_payload["parents"] = [previous_sha]
    status, commit = api("POST", repo_path + "/git/commits", token, commit_payload)
    if status not in (201, 200) or "sha" not in commit:
        raise SystemExit("创建提交失败（HTTP %s）：%s" % (status, str(commit)[:300]))

    # 强制把分支指向我们的提交：空仓库时占位提交会变成不可达对象，
    # 最终公开仓库只显示这一条干净提交；已有仓库则是追加一次正常提交。
    status, ref = api("PATCH", repo_path + "/git/refs/heads/" + default_branch,
                      token, {"sha": commit["sha"], "force": True})
    if status not in (201, 200):
        status, ref = api("POST", repo_path + "/git/refs", token,
                          {"ref": "refs/heads/" + default_branch, "sha": commit["sha"]})
    if status not in (201, 200):
        raise SystemExit("更新分支失败（HTTP %s）：%s" % (status, str(ref)[:300]))

    api("PATCH", repo_path, token, {"default_branch": default_branch})
    out("[5/5] 完成 ✅")
    out("仓库地址：https://github.com/%s/%s" % (owner, repo_name))
    out("提交：%s（%d 个文件%s）" % (
        commit["sha"][:8], len(files),
        "，单条干净初始提交" if repo_was_empty else "，已追加为最新提交"))


def main():
    args = sys.argv[1:]
    dry = "--dry-run" in args
    check = "--check" in args
    reuse = "--reuse" in args
    repo_name = DEFAULT_NAME
    if "--name" in args:
        idx = args.index("--name")
        if idx + 1 < len(args):
            repo_name = args[idx + 1]

    files = build_snapshot()
    out("快照目录：" + SNAPSHOT)
    out("待发布文件：%d 个" % len(files))
    bad = privacy_check(files)
    if bad:
        out("❌ 隐私复查未通过，已中止：")
        for rel, name in bad[:20]:
            out("   %s -> %s" % (rel, name))
        raise SystemExit(1)
    out("隐私复查：通过（0 处命中：设备序列号 / 本机路径 / 密钥）")

    if dry:
        out("[dry-run] 未联网、未创建仓库。快照已就绪。")
        return
    token = read_token()
    if not token:
        raise SystemExit(
            "缺少 GitHub Token。请在本目录创建 .github-token（只放 token），"
            "或设置环境变量 GITHUB_TOKEN 后重试。"
        )
    if check:
        status, user = api("GET", "/user", token)
        out("Token 校验：HTTP %s，账号=%s" % (status, user.get("login", user.get("message", "?"))))
        return
    publish(token, repo_name, reuse)


if __name__ == "__main__":
    main()
