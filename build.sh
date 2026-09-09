#!/bin/bash
# Builds MapathonQA.jar from src/, compiled against lib/josm-tested.jar.
# The PDF report needs OpenPDF - it is fetched into lib/ on first build and
# unpacked into the plugin jar (JOSM plugins are self-contained fat jars).
# lib/josm-tested.jar is gitignored - download it from https://josm.openstreetmap.de/josm-tested.jar
set -e

OPENPDF_VERSION=1.3.30
OPENPDF_JAR="lib/openpdf-${OPENPDF_VERSION}.jar"
OPENPDF_URL="https://repo1.maven.org/maven2/com/github/librepdf/openpdf/${OPENPDF_VERSION}/openpdf-${OPENPDF_VERSION}.jar"

if [ ! -f lib/josm-tested.jar ]; then
    echo "lib/josm-tested.jar not found. Download it from https://josm.openstreetmap.de/josm-tested.jar"
    exit 1
fi

if [ ! -f "$OPENPDF_JAR" ]; then
    echo "Fetching OpenPDF ${OPENPDF_VERSION}..."
    curl -fL -o "$OPENPDF_JAR" "$OPENPDF_URL"
fi

rm -rf build
mkdir -p build

echo "Compiling..."
javac --release 17 -cp "lib/josm-tested.jar:$OPENPDF_JAR" -d build src/*.java

echo "Copying resources..."
cp -r images build/images
cp -r fonts build/fonts

echo "Unpacking OpenPDF into the plugin jar..."
# keep OpenPDF's bundled licence texts (LGPL/MPL/Apache) under META-INF for
# compliance; drop only its manifest and build metadata so ours is used.
(cd build && jar xf "../$OPENPDF_JAR" \
    && rm -f META-INF/MANIFEST.MF \
    && rm -rf META-INF/maven META-INF/versions)

echo "Packaging..."
jar cfm MapathonQA.jar MANIFEST.MF -C build .

echo "Done. Copy MapathonQA.jar to ~/.local/share/JOSM/plugins/ and restart JOSM."
