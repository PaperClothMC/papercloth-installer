#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ -n "${JAVA_HOME:-}" ]; then
	bin="$JAVA_HOME/bin/"
else
	bin=""
fi
version="$(sed -n 's/.*static final String VERSION = "\(.*\)";/\1/p' src/main/java/io/github/paperclothmc/installer/Installer.java)"
rm -rf build
mkdir -p build/classes
find src/main/java -name '*.java' > build/sources.txt
"${bin}javac" --release 8 -Xlint:-options -encoding UTF-8 -d build/classes @build/sources.txt
cp LICENSE build/classes/LICENSE
"${bin}jar" --create --file "build/papercloth-installer-$version.jar" --main-class io.github.paperclothmc.installer.Installer -C build/classes .
echo "build/papercloth-installer-$version.jar"
