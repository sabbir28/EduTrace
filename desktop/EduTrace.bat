@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
echo Starting EduTrace Desktop Focus Suite...

for %%f in ("EduTrace-Desktop*.jar") do (
    java -jar "%%f"
    goto :end
)
for %%f in ("build\libs\EduTrace-Desktop*.jar") do (
    java -jar "%%f"
    goto :end
)

echo EduTrace JAR not found. Building with Gradle...
call ..\gradlew :desktop:fatJar
for %%f in ("build\libs\EduTrace-Desktop*.jar") do (
    java -jar "%%f"
    goto :end
)

echo Failed to launch. Please run: ./gradlew :desktop:run
pause

:end
endlocal

