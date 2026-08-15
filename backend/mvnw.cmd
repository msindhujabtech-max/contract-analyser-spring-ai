@REM Maven Wrapper script for Windows
@echo off
setlocal

set "MAVEN_WRAPPER_PROPERTIES=.mvn\wrapper\maven-wrapper.properties"
set "DIST_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip"

if exist "%MAVEN_WRAPPER_PROPERTIES%" (
    for /f "tokens=1,* delims==" %%a in ('findstr "distributionUrl" "%MAVEN_WRAPPER_PROPERTIES%"') do set "DIST_URL=%%b"
)

set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo Downloading Maven...
    mkdir "%MAVEN_HOME%" 2>nul
    powershell -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%MAVEN_HOME%\maven.zip'"
    powershell -Command "Expand-Archive -Path '%MAVEN_HOME%\maven.zip' -DestinationPath '%MAVEN_HOME%' -Force"
    del "%MAVEN_HOME%\maven.zip"
)

for /f "delims=" %%i in ('dir /s /b "%MAVEN_HOME%\mvn.cmd" 2^>nul') do set "MVN_CMD=%%i"

if "%MVN_CMD%"=="" (
    echo Error: Could not find Maven executable
    exit /b 1
)

"%MVN_CMD%" %*
