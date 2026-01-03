import subprocess
import shutil
import sys
import time
import os
import signal
from pathlib import Path


# For find project root
def find_project_root(start: Path) -> Path:
    for path in [start] + list(start.parents):
        if (path / "gradlew.bat").exists() or (path / "gradlew").exists():
            return path
        if (path / "settings.gradle.kts").exists():
            return path
        if (path / "pom.xml").exists():
            return path
    raise RuntimeError("❌ Project root not found")

# ========= CONFIG =========
SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = find_project_root(SCRIPT_DIR)
SERVER_DIR = Path("C:/Users/HongMingWang/Desktop/Mc server")        # change for Linux/macOS
PLUGINS_DIR = SERVER_DIR / "plugins"
PID_FILE = SERVER_DIR / "server.pid"

GRADLE_CMD = [str(PROJECT_ROOT / "gradlew.bat"), "shadowJar"]

JAVA_CMD = [
    "cmd", 
    "/k", 
    "java",
    "-Xms2G",
    "-Xmx2G", 
    "-jar", 
    "paper-1.21.4-230.jar", 
    "nogui"
]
# ==========================

def run(cmd, cwd=None):
    print("▶", " ".join(map(str, cmd)))
    subprocess.run(cmd, cwd=cwd, check=True)




# 1️⃣ Stop server

def stop_server():
    if not PID_FILE.exists():
        print("ℹ No running server found")
        return

    pid = int(PID_FILE.read_text())
    print(f"▶ Stopping server (PID {pid})...")

    if os.name == "nt":
        subprocess.run(
            ["taskkill", "/PID", str(pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            shell=True
        )
    else:
        os.kill(pid, signal.SIGTERM)

    PID_FILE.unlink(missing_ok=True)
    time.sleep(3)




# 2️⃣ Build plugin
def build_plugin():
    run(GRADLE_CMD, cwd=PROJECT_ROOT)

# 3️⃣ Copy plugin jar
def copy_plugin():
    jars = list((PROJECT_ROOT / "build" / "libs").glob("*-all.jar"))
    if not jars:
        print("❌ No shadow jar found")
        sys.exit(1)

    jar = max(jars, key=lambda f: f.stat().st_mtime)
    shutil.copy2(jar, PLUGINS_DIR / jar.name)
    print(f"✔ Copied {jar.name} to plugins")

# 4️⃣ Start server
def start_server():
    print("▶ Starting server...")
    process = subprocess.Popen(JAVA_CMD,
                                cwd=SERVER_DIR,
                                creationflags=subprocess.CREATE_NEW_CONSOLE
                                )
    PID_FILE.write_text(str(process.pid))
    print(f"✔ Server started (PID {process.pid})")
    process.wait()

def main():
    stop_server()
    build_plugin()
    copy_plugin()
    start_server()

if __name__ == "__main__":
    main()
