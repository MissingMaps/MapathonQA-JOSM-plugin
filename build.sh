#!/bin/bash
# Builds MapathonQA.jar from src/, compiled against lib/josm-tested.jar
# Run from within the MapathonQA/ folder.
# lib/josm-tested.jar is gitignored - download it from https://josm.openstreetmap.de/josm-tested.jar
set -e

if [ ! -f lib/josm-tested.jar ]; then
    echo "lib/josm-tested.jar not found. Download it from https://josm.openstreetmap.de/josm-tested.jar"
    exit 1
fi

mkdir -p build

echo "Compiling..."
javac --release 17 -cp lib/josm-tested.jar -d build src/*.java

echo "Packaging..."
jar cfm MapathonQA.jar MANIFEST.MF -C build .

echo "Done. Copy MapathonQA.jar to ~/.local/share/JOSM/plugins/ and restart JOSM."
