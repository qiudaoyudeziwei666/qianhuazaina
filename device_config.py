# -*- coding: utf-8 -*-
"""本机环境自动探测。

设计目的：辅助脚本不写死任何个人路径或设备序列号，这样项目推送到公开仓库后，
别人克隆下来也能直接运行；同时避免把本机信息带进版本历史。

可用环境变量覆盖（不设置则自动探测）：
  ZHIZHANG_ADB           adb 可执行文件完整路径
  ZHIZHANG_SERIAL        安卓设备序列号（默认取 adb devices 里第一个 device）
  ZHIZHANG_APK           待安装 APK 路径
  ZHIZHANG_GRADLE        gradle.bat 路径
  ZHIZHANG_JAVA_HOME     JDK 目录
  ZHIZHANG_TEST_MIRROR   ASCII 单元测试镜像目录
"""
import glob
import os
import shutil
import subprocess
import tempfile


def _first_file(paths):
    for path in paths:
        if path and os.path.isfile(path):
            return path
    return None


def find_adb():
    override = os.environ.get("ZHIZHANG_ADB")
    if override:
        return override
    candidates = []
    for base in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")):
        if base:
            candidates.append(os.path.join(base, "platform-tools", "adb.exe"))
            candidates.append(os.path.join(base, "platform-tools", "adb"))
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        candidates.append(os.path.join(local_app_data, "Android", "Sdk", "platform-tools", "adb.exe"))
    return _first_file(candidates) or shutil.which("adb") or "adb"


def detect_serial(adb=None):
    override = os.environ.get("ZHIZHANG_SERIAL")
    if override:
        return override
    adb = adb or find_adb()
    try:
        output = subprocess.run(
            [adb, "devices"], capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=15
        ).stdout
        for line in output.splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 2 and parts[1] == "device":
                return parts[0]
    except Exception:
        pass
    return ""


def require_serial(adb=None):
    serial = detect_serial(adb)
    if not serial:
        raise SystemExit(
            "未检测到已授权的安卓设备。请连接手机并允许 USB 调试，"
            "或设置环境变量 ZHIZHANG_SERIAL=<设备序列号> 后重试。"
        )
    return serial


def find_apk(name="qianhuazaina-debug.apk"):
    override = os.environ.get("ZHIZHANG_APK")
    if override:
        return override
    return os.path.join(os.path.dirname(os.path.abspath(__file__)), name)


def find_gradle_bat():
    override = os.environ.get("ZHIZHANG_GRADLE")
    if override:
        return override
    home = os.path.expanduser("~")
    pattern = os.path.join(
        home, ".gradle", "wrapper", "dists", "gradle-8.9-bin", "*",
        "gradle-8.9", "bin", "gradle.bat"
    )
    hits = sorted(glob.glob(pattern))
    if hits:
        return hits[-1]
    return shutil.which("gradle.bat") or "gradle.bat"


def find_java_home():
    for key in ("ZHIZHANG_JAVA_HOME", "JAVA_HOME"):
        value = os.environ.get(key)
        if value and os.path.isdir(value):
            return value
    for path in (
        r"C:\Program Files\Android\Android Studio\jbr",
        os.path.join(os.environ.get("LOCALAPPDATA", ""), "Programs", "Android Studio", "jbr"),
    ):
        if path and os.path.isdir(path):
            return path
    return ""


def test_mirror_dir():
    override = os.environ.get("ZHIZHANG_TEST_MIRROR")
    if override:
        return override
    return os.path.join(tempfile.gettempdir(), "zhizhang-test-mirror")
