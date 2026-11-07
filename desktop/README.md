# EduTrace Desktop - Focus & Study Suite

**EduTrace Desktop** brings the complete education-first, focus-driven experience to desktop platforms (Windows, Linux, and macOS) written entirely in **pure Java** with a modern FlatLaf Dark UI.

---

## Key Features

1. **Focus Dashboard & Daily Flow:**
   - Real-time day in progress tracker & visual completion percentage.
   - Live clock and daily greeting.
   - Inspiring daily quotes with seamless English and Bengali toggle (`quotes_en.json` & `quotes_bn.json`).
   - Interactive daily focus checklist (persisted locally).
   - **Spotlight Math & Search:** Instant calculation evaluator directly from the dashboard search bar.

2. **Study Timer & Pomodoro Engine:**
   - **Intervals:** 🍅 Pomodoro (25m), ☕ Short Break (5m), 🌴 Long Break (15m), and ⏱ Continuous Study Stopwatch.
   - Subject tagging (Mathematics, Physics, Chemistry, CS, Literature, etc.).
   - Session logging with custom notes.
   - **Procedural Ambient Audio Engine:** Real-time synthesized study soundscapes (Soft Pink Noise, Calming Rain, 40Hz Gamma Focus, and Gentle Clock Tick) using standard Java Sound synthesis without external media dependencies.

3. **High-Performance Scientific Calculator:**
   - Full scientific evaluation powered by recursive-descent parser (`ScientificMathEvaluator`).
   - Trigonometry (`sin`, `cos`, `tan`, `asin`, `acos`, `atan` in Degree or Radian modes).
   - Logarithms (`ln`, `log`), powers (`^`), factorials (`!`), square root (`sqrt`, `cbrt`), constants ($\pi, e$).
   - Calculation history with single-click recall.

4. **Analytics & Study Breakdown:**
   - Today's study time, all-time study hours, and total sessions completed.
   - Visual percentage breakdown per subject.
   - Complete session history table with CSV export.

5. **Local & Private Storage:**
   - All session data, checklist items, and preferences are saved locally to `~/.edutrace/desktop_data.json`.
   - No cloud account required.

---

## How to Run & Build

### Requirements
- **Java SE 11** or newer (Java 17, 21, etc. supported).

### Run from Source
```bash
# Using the Gradle Wrapper
./gradlew :desktop:run
```

### Build Standalone Executable Fat JAR
```bash
./gradlew :desktop:fatJar
```
The executable JAR will be located at:
`desktop/build/libs/EduTrace-Desktop-all.jar`

Run it directly on any OS:
```bash
java -jar desktop/build/libs/EduTrace-Desktop-all.jar
```

Or on Windows:
Double-click `desktop/EduTrace.bat` or run it from CMD/PowerShell.

---

## Releases & Automated CI/CD
Every commit pushed to the `desktop` branch is automatically built, packaged into Windows ZIP, Linux TAR.GZ, and standalone JARs, and published to GitHub Releases via GitHub Actions (`.github/workflows/desktop_release.yml`).
