#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "Starting EduTrace Desktop Focus Suite..."

if [ -f "EduTrace-Desktop-all.jar" ]; then
    java -jar "EduTrace-Desktop-all.jar" "$@"
elif [ -f "build/libs/EduTrace-Desktop-all.jar" ]; then
    java -jar "build/libs/EduTrace-Desktop-all.jar" "$@"
else
    echo "EduTrace JAR not found. Building with Gradle..."
    ../gradlew :desktop:fatJar
    if [ -f "build/libs/EduTrace-Desktop-all.jar" ]; then
        java -jar "build/libs/EduTrace-Desktop-all.jar" "$@"
    else
        echo "Failed to launch. Please run: ./gradlew :desktop:run"
        exit 1
    fi
fi
