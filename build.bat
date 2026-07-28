@echo off
REM Builds MapathonQA.jar from src/, compiled against lib/josm-tested.jar
REM Run from within the MapathonQA/ folder.
REM lib/josm-tested.jar is gitignored - download it from https://josm.openstreetmap.de/josm-tested.jar

if not exist lib\josm-tested.jar (
    echo lib\josm-tested.jar not found. Download it from https://josm.openstreetmap.de/josm-tested.jar
    exit /b 1
)

if not exist build mkdir build

echo Compiling...
javac --release 17 -cp lib\josm-tested.jar -d build src\*.java
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)

echo Packaging...
jar cfm MapathonQA.jar MANIFEST.MF -C build .

echo Done. Copy MapathonQA.jar to %%APPDATA%%\JOSM\plugins\ and restart JOSM.
