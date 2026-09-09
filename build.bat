@echo off
REM Builds MapathonQA.jar from src/, compiled against lib\josm-tested.jar.
REM The PDF report needs OpenPDF - it is fetched into lib\ on first build and
REM unpacked into the plugin jar (JOSM plugins are self-contained fat jars).
REM lib\josm-tested.jar is gitignored - download it from https://josm.openstreetmap.de/josm-tested.jar
setlocal

set OPENPDF_VERSION=1.3.30
set OPENPDF_JAR=lib\openpdf-%OPENPDF_VERSION%.jar
set OPENPDF_URL=https://repo1.maven.org/maven2/com/github/librepdf/openpdf/%OPENPDF_VERSION%/openpdf-%OPENPDF_VERSION%.jar

if not exist lib\josm-tested.jar (
    echo lib\josm-tested.jar not found. Download it from https://josm.openstreetmap.de/josm-tested.jar
    exit /b 1
)

if not exist "%OPENPDF_JAR%" (
    echo Fetching OpenPDF %OPENPDF_VERSION%...
    curl -fL -o "%OPENPDF_JAR%" "%OPENPDF_URL%"
    if errorlevel 1 (
        echo Failed to download OpenPDF. Put openpdf-%OPENPDF_VERSION%.jar in lib\ manually.
        exit /b 1
    )
)

if exist build rmdir /s /q build
mkdir build

echo Compiling...
javac --release 17 -cp "lib\josm-tested.jar;%OPENPDF_JAR%" -d build src\*.java
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)

echo Copying resources...
xcopy /Y /I /E images build\images >nul
xcopy /Y /I /E fonts build\fonts >nul

echo Unpacking OpenPDF into the plugin jar...
REM keep OpenPDF's bundled licence texts (LGPL/MPL/Apache) under META-INF for
REM compliance; drop only its manifest and build metadata so ours is used.
pushd build
jar xf "..\%OPENPDF_JAR%"
if exist META-INF\MANIFEST.MF del /q META-INF\MANIFEST.MF
if exist META-INF\maven rmdir /s /q META-INF\maven
if exist META-INF\versions rmdir /s /q META-INF\versions
popd

echo Packaging...
jar cfm MapathonQA.jar MANIFEST.MF -C build .

echo Done. Copy MapathonQA.jar to %%APPDATA%%\JOSM\plugins\ and restart JOSM.
