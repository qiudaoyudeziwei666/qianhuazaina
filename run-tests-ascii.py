# -*- coding: utf-8 -*-
"""在纯 ASCII 路径的构建镜像里运行 JVM 单元测试。

原因：项目位于中文路径，AGP 可用 android.overridePathCheck=true 正常编译，
但 Gradle 的 test worker 通过 URL 编码的 classpath 加载测试类会全部
ClassNotFoundException。镜像只同步源码与构建配置（不复制项目本体、不动真实数据），
测试完把 test-results 复制回真实项目，便于归档。

用法：python run-tests-ascii.py
"""
import os
import shutil
import subprocess
import sys

SRC = os.path.dirname(os.path.abspath(__file__))
from device_config import find_gradle_bat, find_java_home, test_mirror_dir

MIRROR = test_mirror_dir()
GRADLE_BAT = find_gradle_bat()
JAVA_HOME = find_java_home()
if not JAVA_HOME:
    raise SystemExit("未找到 JDK，请设置 JAVA_HOME 或 ZHIZHANG_JAVA_HOME 后重试。")

TOP_FILES = [
    "settings.gradle.kts", "build.gradle.kts", "gradle.properties", "local.properties",
]
APP_FILES = ["app/build.gradle.kts", "app/proguard-rules.pro"]


def sync():
    os.makedirs(MIRROR, exist_ok=True)
    os.makedirs(os.path.join(MIRROR, "app"), exist_ok=True)
    os.makedirs(os.path.join(MIRROR, "gradle", "wrapper"), exist_ok=True)
    for f in TOP_FILES + APP_FILES:
        s = os.path.join(SRC, f)
        if os.path.exists(s):
            shutil.copy2(s, os.path.join(MIRROR, f))
    for f in os.listdir(os.path.join(SRC, "gradle", "wrapper")):
        shutil.copy2(os.path.join(SRC, "gradle", "wrapper", f),
                     os.path.join(MIRROR, "gradle", "wrapper", f))
    # 增量覆盖，不做任何删除：避免误删镜像内容，也避免触发批量删除保护
    dst_src = os.path.join(MIRROR, "app", "src")
    shutil.copytree(os.path.join(SRC, "app", "src"), dst_src, dirs_exist_ok=True)
    return dst_src


def main():
    print("[1/3] 同步源码到 ASCII 镜像:", MIRROR, flush=True)
    sync()
    env = dict(os.environ)
    env["JAVA_HOME"] = JAVA_HOME
    env["PATH"] = JAVA_HOME + r"\bin;" + env.get("PATH", "")
    print("[2/3] 运行单元测试", flush=True)
    r = subprocess.run(["cmd", "/c", GRADLE_BAT, ":app:testDebugUnitTest"], cwd=MIRROR, env=env,
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    tail = "\n".join((r.stdout or "").splitlines()[-25:])
    print(tail, flush=True)
    if r.returncode != 0:
        print((r.stderr or "")[-1500:], flush=True)
        return r.returncode
    print("[3/3] 复制测试结果回真实项目", flush=True)
    rs = os.path.join(MIRROR, "app", "build", "test-results", "testDebugUnitTest")
    rd = os.path.join(SRC, "app", "build", "test-results", "testDebugUnitTest")
    if os.path.isdir(rs):
        os.makedirs(rd, exist_ok=True)
        for f in os.listdir(rs):
            if f.endswith(".xml"):
                shutil.copy2(os.path.join(rs, f), os.path.join(rd, f))
    import glob
    import xml.etree.ElementTree as ET
    total = failed = errors = 0
    for p in glob.glob(os.path.join(rd, "*.xml")):
        root = ET.parse(p).getroot()
        total += int(root.get("tests", 0))
        failed += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
    print("单元测试：%d 项，失败 %d，错误 %d" % (total, failed, errors), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
