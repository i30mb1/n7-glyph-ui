"""Creates a new n7 Glyph Matrix app from the template in ../template.

Usage:
  python new_app.py --dir D:\\AndroidProject\\glyph-flame --package n7.glyphflame --name "Glyph Flame" --repo glyph-flame

Copies the template (without build outputs, IDE files and local.properties), gives it the new package, app name,
headline, theme/class names and repo name, writes local.properties, and checks that nothing of the template's
identity is left. The new project must sit next to the n7-glyph-ui checkout, which it includes as a local build.
"""
import argparse
import os
import re
import shutil
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
LIBRARY = TOOLS.parent
TEMPLATE = LIBRARY / "template"
REFERENCE_LOCAL_PROPERTIES = Path(r"D:\AndroidProject\glyph-pet\local.properties")

# The template's identity, replaced everywhere (file contents and package directories).
T_PACKAGE = "n7.glyphtemplate"
T_NAME = "Glyph Template"
T_PASCAL = "GlyphTemplate"
T_REPO = "glyph-template"
T_HEADLINE = '"GLYPH", "TEMPLATE"'          # DotHeadline lines in MainActivity.kt and the feature title in store.json

SKIP_DIRS = {"build", ".gradle", ".kotlin", ".idea", "captures", ".cxx", ".externalNativeBuild"}
SKIP_FILES = {"local.properties", "play-service-account.json", ".DS_Store"}
SKIP_PATHS = {Path("store/out")}
BINARY_SUFFIXES = {".jar", ".aar", ".png", ".webp", ".jpg", ".jpeg", ".gif", ".ttf", ".otf", ".keystore", ".jks", ".zip"}

PACKAGE_RE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")
REPO_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")
KOTLIN_KEYWORDS = {"as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
                   "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof",
                   "val", "var", "when", "while"}


def fail(message):
    print(f"new_app: {message}", file=sys.stderr)
    sys.exit(1)


def words_of(name):
    return re.findall(r"[A-Za-z0-9]+", name)


def check_args(args):
    if not PACKAGE_RE.match(args.package):
        fail(f'--package "{args.package}" must be lower-case segments separated by dots, like n7.glyphflame')
    bad = [s for s in args.package.split(".") if s in KOTLIN_KEYWORDS]
    if bad:
        fail(f'--package "{args.package}" uses a Kotlin keyword as a segment: {", ".join(bad)}')
    if not REPO_RE.match(args.repo):
        fail(f'--repo "{args.repo}" must be lower case letters, digits and dashes, like glyph-flame')
    name = args.name.strip()
    # The first character itself: a leading @ or ? would make the Android string resource a reference.
    if not re.match(r"[A-Za-z]", name):
        fail(f'--name "{args.name}" must start with a Latin letter, like "Glyph Flame"')
    # It goes as is into XML, JSON and Kotlin strings ($ starts a template) and is Gradle's rootProject.name, which
    # must not contain / \ : < > " ? * |.
    if re.search(r"[<>&\"'\\$/:?*|\x00-\x1f\x7f]", name):
        fail(f'--name "{args.name}" must not contain < > & " \' \\ $ / : ? * | or control characters '
             "(it goes into XML, JSON, Kotlin strings and the Gradle project name as is)")
    if len(name) > 30:
        fail(f'--name "{args.name}" is longer than 30 characters, the Google Play title limit')


def is_skipped(rel):
    if any(part in SKIP_DIRS for part in rel.parts[:-1]) or rel.name in SKIP_FILES:
        return True
    return any(rel == p or p in rel.parents for p in SKIP_PATHS)


def copy_template(dest):
    copied = []
    for root, dirs, files in os.walk(TEMPLATE):
        rel_root = Path(root).relative_to(TEMPLATE)
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not is_skipped(rel_root / d / "x")]
        for f in files:
            rel = rel_root / f
            if is_skipped(rel):
                continue
            target = dest / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(Path(root) / f, target)
            copied.append(rel)
    return copied


def move_package_dirs(dest, package):
    old = Path(*T_PACKAGE.split("."))
    new = Path(*package.split("."))
    for source_set in sorted((dest / "app" / "src").iterdir()):
        for lang in ("kotlin", "java"):
            src = source_set / lang / old
            if not src.is_dir():
                continue
            dst = source_set / lang / new
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(src), str(dst))
            # Remove the template's now empty parent folders (n7/ stays when the new package also starts with n7).
            parent = src.parent
            while parent != source_set / lang and parent.is_dir() and not any(parent.iterdir()):
                parent.rmdir()
                parent = parent.parent


def text_of(path):
    if path.suffix.lower() in BINARY_SUFFIXES:
        return None
    data = path.read_bytes()
    if b"\0" in data:
        return None
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        return None


def replacements(args):
    words = words_of(args.name)
    pascal = "".join(w[:1].upper() + w[1:] for w in words)
    headline = ", ".join(f'"{w.upper()}"' for w in args.name.split())
    # Most specific first: the package contains "glyphtemplate", the headline contains "TEMPLATE".
    return [
        (T_PACKAGE, args.package),
        (T_HEADLINE, headline),
        (T_NAME, args.name.strip()),
        (T_PASCAL, pascal),
        (T_REPO, args.repo),
    ]


def rewrite(dest, pairs):
    changed = 0
    for path in sorted(p for p in dest.rglob("*") if p.is_file()):
        text = text_of(path)
        if text is None:
            continue
        new = text
        for old, value in pairs:
            new = new.replace(old, value)
        if new != text:
            path.write_bytes(new.encode("utf-8"))
            changed += 1
    return changed


def leftovers(dest, args):
    """Every trace of the template's identity still in the new project (paths and contents)."""
    marks = [T_PACKAGE, T_NAME, T_PASCAL, T_REPO, "glyphtemplate", '"TEMPLATE"']
    new_values = " ".join([args.package, args.name, args.repo]).lower()
    marks = [m for m in marks if m.lower().strip('"') not in new_values]
    pattern = re.compile("|".join(re.escape(m) for m in marks), re.IGNORECASE)
    found = []
    for path in sorted(dest.rglob("*")):
        rel = path.relative_to(dest)
        if pattern.search(rel.as_posix()):
            found.append(f"{rel.as_posix()}: in the path")
        if path.is_file():
            text = text_of(path)
            if text is None:
                continue
            for i, line in enumerate(text.splitlines(), 1):
                if pattern.search(line):
                    found.append(f"{rel.as_posix()}:{i}: {line.strip()[:120]}")
    return found


def sdk_dir():
    """sdk.dir as glyph-pet has it, else the usual environment variables, else the default install folder."""
    if REFERENCE_LOCAL_PROPERTIES.is_file():
        for line in REFERENCE_LOCAL_PROPERTIES.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                return line[len("sdk.dir="):]
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(var):
            return os.environ[var].replace("\\", "\\\\").replace(":", "\\:")
    local = os.environ.get("LOCALAPPDATA")
    if local:
        return str(Path(local) / "Android" / "Sdk").replace("\\", "\\\\").replace(":", "\\:")
    return None


def main(argv=None):
    parser = argparse.ArgumentParser(description="Create a new n7 Glyph Matrix app from n7-glyph-ui/template.")
    parser.add_argument("--dir", required=True, help="the new project folder (must not exist), next to n7-glyph-ui")
    parser.add_argument("--package", required=True, help="Kotlin package and application id, like n7.glyphflame")
    parser.add_argument("--name", required=True, help='app name, like "Glyph Flame"; its words become the headline')
    parser.add_argument("--repo", required=True, help="repo and folder name, like glyph-flame")
    args = parser.parse_args(argv)
    check_args(args)

    if not (TEMPLATE / "settings.gradle.kts").is_file():
        fail(f"no template at {TEMPLATE}")
    dest = Path(args.dir).resolve()
    if dest.exists():
        fail(f"{dest} already exists; refusing to overwrite it")
    if dest == TEMPLATE or TEMPLATE in dest.parents or LIBRARY in dest.parents or dest == LIBRARY:
        fail(f"{dest} is inside the library checkout; put the app next to it")
    if len(args.name.split()) > 3:
        print(f'new_app: warning: "{args.name}" makes a headline of {len(args.name.split())} lines; '
              "the house headline has 1 to 3. Edit DotHeadline in MainActivity.kt and store/store.json.")

    dest.mkdir(parents=True)
    try:
        files = copy_template(dest)
        move_package_dirs(dest, args.package)
        changed = rewrite(dest, replacements(args))
        left = leftovers(dest, args)
        if left:
            raise RuntimeError("the template's name or package is still in the new project:\n  " + "\n  ".join(left))
    except Exception:
        shutil.rmtree(dest, ignore_errors=True)
        raise

    sdk = sdk_dir()
    if sdk:
        (dest / "local.properties").write_text(f"sdk.dir={sdk}\n", encoding="utf-8")
    else:
        print("new_app: warning: Android SDK not found; write sdk.dir into local.properties yourself")

    print(f"Created {dest}: {len(files)} files, {changed} rewritten for {args.package} / {args.name} / {args.repo}.")
    library_next_to_it = (dest.parent / "n7-glyph-ui" / "settings.gradle.kts").is_file()
    if not library_next_to_it:
        print(f"new_app: warning: no n7-glyph-ui checkout at {dest.parent / 'n7-glyph-ui'}; the build will try JitPack instead.")
    gw = TOOLS / "gw.py"
    print("\nNext steps:")
    print(f"  1. Build and test (Gradle only through the lock wrapper, 10-minute timeout):")
    print(f"       python {gw} {dest} :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease")
    print(f"  2. Replace game/DemoEngine.kt and toy/DemoToyService.kt with the real toy; keep toy/GlyphToyService.kt as it is.")
    print(f"     One <service> per toy in AndroidManifest.xml. Strings in res/values and res/values-ru.")
    print(f"  3. Point StoreFramesTest and store/store.json at the real frames, run the tests, then the store kit:")
    print(f"       python {LIBRARY / 'store' / 'render.py'} {dest} --only drawables")
    print(f"  4. Write app/src/main/play/ (listing texts, release notes) and docs/privacy.html for this app.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
