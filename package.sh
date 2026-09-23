#!/bin/bash
# Turns dist/InfiniteLauncher.jar into the three downloads, each with its own Java 8:
#
#   dist/InfiniteLauncher-Setup.exe         Windows installer (per-user, no admin prompt)
#   dist/InfiniteLauncher-macOS.zip         Infinite Launcher.app, Apple Silicon and Intel in one
#   dist/InfiniteLauncher-Linux.tar.gz      folder with a start script, for Linux and Steam Deck
#
# Runtimes come from Eclipse Temurin (Adoptium) and, for Apple Silicon where Temurin has no
# Java 8, Azul Zulu. Both are GPLv2+Classpath Exception and free to redistribute. Downloads are
# checked against the vendor's SHA-256 and cached in .cache/.
set -euo pipefail
cd "$(dirname "$0")"
[ -f dist/InfiniteLauncher.jar ] || ./build.sh
VERSION=$(tr -d ' \n' < VERSION)
command -v makensis >/dev/null || { echo "makensis is needed for the Windows installer (apt install nsis)"; exit 1; }
mkdir -p .cache
rm -rf build/pkg && mkdir -p build/pkg

python3 - "$VERSION" <<'PY'
import hashlib, json, os, shutil, stat, subprocess, sys, tarfile, urllib.request, zipfile
VERSION = sys.argv[1]
CACHE, PKG, DIST = ".cache", "build/pkg", "dist"
UA = {"User-Agent": "InfiniteLauncher-build"}

def get_json(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))

def fetch(url, name, sha256):
    path = os.path.join(CACHE, name)
    if not (os.path.exists(path) and hashlib.sha256(open(path, "rb").read()).hexdigest() == sha256):
        print("  downloading", name)
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA)) as r, open(path + ".part", "wb") as f:
            shutil.copyfileobj(r, f)
        got = hashlib.sha256(open(path + ".part", "rb").read()).hexdigest()
        if got != sha256:
            sys.exit(f"checksum mismatch for {name}: {got} != {sha256}")
        os.replace(path + ".part", path)
    return path

def temurin(os_, arch):
    a = get_json(f"https://api.adoptium.net/v3/assets/latest/8/hotspot?os={os_}&architecture={arch}&image_type=jre&vendor=eclipse")[0]
    p = a["binary"]["package"]
    return fetch(p["link"], p["name"], p["checksum"]), a["release_name"]

def zulu_mac_arm():
    l = get_json("https://api.azul.com/metadata/v1/zulu/packages/?java_version=8&os=macos&arch=aarch64&java_package_type=jre"
                 "&archive_type=tar.gz&javafx_bundled=false&latest=true&release_status=ga&availability_types=CA")[0]
    d = get_json("https://api.azul.com/metadata/v1/zulu/packages/" + l["package_uuid"])
    return fetch(d["download_url"], d["name"], d["sha256_hash"]), d["name"]

def unpack(archive, into):
    shutil.rmtree(into, ignore_errors=True)
    os.makedirs(into)
    if archive.endswith(".zip"):
        with zipfile.ZipFile(archive) as z:
            z.extractall(into)
    else:
        with tarfile.open(archive) as t:
            t.extractall(into)

def java_home(root, exe):
    for d, dirs, files in os.walk(root):
        if os.path.basename(d) == "bin" and exe in files:
            return os.path.dirname(d)
    sys.exit("no java in " + root)

def copy_home(src_root, exe, dest):
    home = java_home(src_root, exe)
    shutil.copytree(home, dest, symlinks=True)

def zip_tree(src_dir, arc_root, out):
    """Zip keeping Unix modes and symlinks, which the macOS Archive Utility restores."""
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for d, dirs, files in os.walk(src_dir):
            dirs.sort()
            for name in sorted(files) + [x for x in dirs if os.path.islink(os.path.join(d, x))]:
                full = os.path.join(d, name)
                arc = os.path.join(arc_root, os.path.relpath(full, src_dir))
                info = zipfile.ZipInfo(arc)
                info.create_system = 3
                if os.path.islink(full):
                    info.external_attr = (stat.S_IFLNK | 0o777) << 16
                    z.writestr(info, os.readlink(full))
                else:
                    mode = os.stat(full).st_mode
                    info.external_attr = (stat.S_IFREG | (0o755 if mode & 0o111 else 0o644)) << 16
                    info.compress_type = zipfile.ZIP_DEFLATED
                    with open(full, "rb") as f:
                        z.writestr(info, f.read())

jar = os.path.join(DIST, "InfiniteLauncher.jar")
icons = "build/packaging"

# ---------------------------------------------------------------- Windows
print("==> Windows")
arc, rel = temurin("windows", "x64")
unpack(arc, f"{PKG}/win-jre")
stage = f"{PKG}/win"
os.makedirs(stage)
copy_home(f"{PKG}/win-jre", "javaw.exe", f"{stage}/runtime")
shutil.copy(jar, stage)
shutil.copy(f"{icons}/icon.ico", stage)
out = os.path.abspath(f"{DIST}/InfiniteLauncher-Setup.exe")
subprocess.check_call(["makensis", "-V2", f"-DVERSION={VERSION}", f"-DSTAGE={os.path.abspath(stage)}", f"-DOUTFILE={out}",
                       "packaging/windows/installer.nsi"])
print("   ", rel, "->", out)

# ---------------------------------------------------------------- macOS (universal)
print("==> macOS")
app = f"{PKG}/mac/Infinite Launcher.app/Contents"
os.makedirs(f"{app}/MacOS")
os.makedirs(f"{app}/Resources")
arc, rel_x = temurin("mac", "x64")
unpack(arc, f"{PKG}/mac-x64")
copy_home(f"{PKG}/mac-x64", "java", f"{app}/Resources/jre-x64")
arc, rel_a = zulu_mac_arm()
unpack(arc, f"{PKG}/mac-arm")
copy_home(f"{PKG}/mac-arm", "java", f"{app}/Resources/jre-arm64")
plist = open("packaging/macos/Info.plist").read().replace("@VERSION@", VERSION)
open(f"{app}/Info.plist", "w").write(plist)
open(f"{app}/PkgInfo", "w").write("APPL????")
shutil.copy("packaging/macos/infinite-launcher", f"{app}/MacOS/infinite-launcher")
os.chmod(f"{app}/MacOS/infinite-launcher", 0o755)
shutil.copy(jar, f"{app}/Resources/")
shutil.copy(f"{icons}/icon.icns", f"{app}/Resources/")
shutil.copy(f"{icons}/icon-512.png", f"{app}/Resources/")
out = f"{DIST}/InfiniteLauncher-macOS.zip"
zip_tree(f"{PKG}/mac/Infinite Launcher.app", "Infinite Launcher.app", out)
print("   ", rel_x, "+", rel_a, "->", out)

# ---------------------------------------------------------------- Linux
print("==> Linux")
arc, rel = temurin("linux", "x64")
unpack(arc, f"{PKG}/linux-jre")
stage = f"{PKG}/linux/InfiniteLauncher"
os.makedirs(stage)
copy_home(f"{PKG}/linux-jre", "java", f"{stage}/runtime")
shutil.copy(jar, stage)
shutil.copy("packaging/linux/infinite-launcher", stage)
os.chmod(f"{stage}/infinite-launcher", 0o755)
shutil.copy(f"{icons}/icon-512.png", f"{stage}/icon.png")
out = f"{DIST}/InfiniteLauncher-Linux.tar.gz"
with tarfile.open(out, "w:gz") as t:
    t.add(stage, arcname="InfiniteLauncher")
print("   ", rel, "->", out)
PY
ls -la dist/ | awk 'NR>1{printf "%10s  %s\n", $5, $9}'
