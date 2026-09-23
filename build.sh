#!/bin/bash
# Builds dist/InfiniteLauncher.jar. ./package.sh turns it into the Windows, macOS and Linux downloads.
#
# The Microsoft app ID is read from msa-client-id.txt (or $INFINITE_MSA_CLIENT_ID). It isn't a
# secret -- every public launcher ships its ID -- but it has to be yours: see README.md.
set -euo pipefail
cd "$(dirname "$0")"
VERSION=$(tr -d ' \n' < VERSION)
JDK8=${JDK8:-/usr/lib/jvm/temurin-8-jdk-amd64}
CLIENT_ID=${INFINITE_MSA_CLIENT_ID:-}
if [ -z "$CLIENT_ID" ] && [ -f msa-client-id.txt ]; then CLIENT_ID=$(tr -d ' \n' < msa-client-id.txt); fi
REPO=${INFINITE_REPO:-HamBone999/Minecraft-Infinite-Reborn}
LAUNCHER_REPO=${INFINITE_LAUNCHER_REPO:-HamBone999/Infinite-Launcher}

rm -rf build
mkdir -p build/classes build/tools build/packaging dist
"$JDK8/bin/javac" -d build/tools tools/MakeIcons.java
"$JDK8/bin/java" -Djava.awt.headless=true -cp build/tools MakeIcons build/classes/infinite/launcher build/packaging branding/logo.png

cp -r resources/. build/classes/
{
   echo "version=$VERSION"
   echo "msa.clientId=$CLIENT_ID"
   echo "repo=$REPO"
   echo "launcherRepo=$LAUNCHER_REPO"
} > build/classes/infinite/launcher/launcher.properties

"$JDK8/bin/javac" -source 8 -target 8 -encoding UTF-8 -Xlint:-options -nowarn -d build/classes $(find src -name '*.java')

cat > build/MANIFEST.MF <<MF
Manifest-Version: 1.0
Main-Class: infinite.launcher.Main
Implementation-Title: Infinite Launcher
Implementation-Version: $VERSION
MF
rm -f dist/InfiniteLauncher.jar
"$JDK8/bin/jar" cfm dist/InfiniteLauncher.jar build/MANIFEST.MF -C build/classes .
echo "built dist/InfiniteLauncher.jar ($VERSION, $(du -k dist/InfiniteLauncher.jar | cut -f1) KB, sign-in $([ -n "$CLIENT_ID" ] && echo configured || echo 'NOT configured'))"
