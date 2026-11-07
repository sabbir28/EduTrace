#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "Starting EduTrace Desktop Focus Suite..."

for jar in EduTrace-Desktop*.jar build/libs/EduTrace-Desktop*.jar; do
    if [ -f "$jar" ]; then
        exec java -jar "$jar" "$@"
    fi
done

echo "EduTrace JAR not found. Building with Gradle..."
../gradlew :desktop:fatJar

for jar in build/libs/EduTrace-Desktop*.jar; do
    if [ -f "$jar" ]; then
        exec java -jar "$jar" "$@"
    fi
done

echo "Failed to launch. Please run: ./gradlew :desktop:run"
exit 1

