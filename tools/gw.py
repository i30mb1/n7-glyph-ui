"""Runs Gradle for one project while holding a machine-wide lock.

Every n7 Glyph app includes the sibling ../n7-glyph-ui checkout as a composite build, so two Gradle builds
running at the same time would both write into that library's build directory. This wrapper makes them queue.

Usage: python gw.py <project-dir> <gradle args...>
The exit code is Gradle's.
"""
import ctypes
import json
import os
import subprocess
import sys
import tempfile
import time

LOCK = os.path.join(tempfile.gettempdir(), "n7-gradle.lock")
OWNER = os.path.join(LOCK, "owner.json")


def alive(pid):
    if os.name != "nt":
        try:
            os.kill(pid, 0)
            return True
        except OSError:
            return False
    handle = ctypes.windll.kernel32.OpenProcess(0x1000, False, pid)  # PROCESS_QUERY_LIMITED_INFORMATION
    if not handle:
        return False
    code = ctypes.c_ulong()
    ok = ctypes.windll.kernel32.GetExitCodeProcess(handle, ctypes.byref(code))
    ctypes.windll.kernel32.CloseHandle(handle)
    return bool(ok) and code.value == 259  # STILL_ACTIVE


def release():
    try:
        os.remove(OWNER)
    except OSError:
        pass
    try:
        os.rmdir(LOCK)
    except OSError:
        pass


def acquire():
    waited = 0
    while True:
        try:
            os.mkdir(LOCK)
            with open(OWNER, "w", encoding="utf-8") as f:
                json.dump({"pid": os.getpid(), "since": time.time(), "args": sys.argv[1:]}, f)
            return
        except FileExistsError:
            pass
        try:
            with open(OWNER, encoding="utf-8") as f:
                owner = json.load(f)
            if not alive(int(owner["pid"])):
                print("[gw] the previous build died without unlocking; taking over", flush=True)
                release()
                continue
        except (OSError, ValueError, KeyError):
            # The owner file is being written, or the lock was just released.
            try:
                if time.time() - os.path.getmtime(LOCK) > 30:
                    release()
                    continue
            except OSError:
                continue
        if waited % 30 == 0:
            print("[gw] another Gradle build is running, waiting...", flush=True)
        time.sleep(2)
        waited += 2


def warn_about_real_devices():
    """The owner's own phone is often plugged in. Builds are fine; adb commands must not reach it by accident."""
    adb = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe")
    if not os.path.exists(adb):
        return
    try:
        out = subprocess.run([adb, "devices"], capture_output=True, text=True, timeout=10).stdout
    except (OSError, subprocess.SubprocessError):
        return
    real = [line.split()[0] for line in out.splitlines()[1:] if "\tdevice" in line and not line.startswith("emulator-")]
    if real:
        print(
            "[gw] NOTICE: the owner's real phone is connected (" + ", ".join(real) + "). "
            "Never send adb commands to it: no install, uninstall, input or settings. "
            "For emulator tests pass the emulator serial explicitly, for example: adb -s emulator-5554 ...",
            flush=True,
        )


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    warn_about_real_devices()
    project = os.path.abspath(sys.argv[1])
    wrapper = os.path.join(project, "gradlew.bat" if os.name == "nt" else "gradlew")
    if not os.path.exists(wrapper):
        print(f"[gw] no Gradle wrapper in {project}")
        return 2
    args = sys.argv[2:]
    if not any(a.startswith("--console") for a in args):
        args.append("--console=plain")
    command = (["cmd", "/c", wrapper] if os.name == "nt" else [wrapper]) + args
    acquire()
    try:
        return subprocess.call(command, cwd=project)
    finally:
        release()


if __name__ == "__main__":
    sys.exit(main())
