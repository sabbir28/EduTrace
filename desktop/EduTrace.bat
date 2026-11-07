@echo off
setlocal
cd /d "%~dp0"
echo Starting EduTrace Desktop Focus Suite...

if exist "EduTrace-Desktop-all.jar" (
    java -jar "EduTrace-Desktop-all.jar"
) else if exist "build\libs\EduTrace-Desktop-all.jar" (
    java -jar "build\libs\EduTrace-Desktop-all.jar"
) else (
    echo EduTrace JAR not found. Building with Gradle...
    call ..\gradlew :desktop:fatJar
    if exist "build\libs\EduTrace-Desktop-all.jar" (
        java -jar "build\libs\EduTrace-Desktop-all.jar"
    ) else (
        echo Failed to launch. Please run: ./gradlew :desktop:run
        pause
    )
)
endlocal
