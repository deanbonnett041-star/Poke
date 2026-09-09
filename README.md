# Slabrate (Android)

Photograph a Pokémon card and get an **AI-estimated grade**, presented in the
style of the four major grading companies: **PSA**, **Beckett (BGS)**,
**CGC**, and **SGC**.

## Important disclaimer

None of PSA, Beckett/BGS, CGC, or SGC offer a public API that grades cards
from a photo — they grade physically submitted cards by hand. This app is
**not affiliated with, endorsed by, or connected to** any of those
companies. It runs its own on-device computer-vision analysis (centering,
corner sharpness, edge wear, surface condition) and converts the result into
an **estimate**, formatted using each company's public 1–10 scale and label
vocabulary. The category weightings are independently derived
approximations of each company's publicly described grading philosophy —
**not** their real proprietary formulas. Treat every grade in this app as a
rough, photo-based estimate, never as an official or certified grade.

## How grading works

1. You photograph the front (and optionally back) of the card against an
   on-screen alignment guide.
2. `GradingEngine` (in `grading/`) snaps the guide to the card's real edges,
   then measures:
   - **Centering** — border thickness on all four sides.
   - **Corners** — whitening/fraying and sharpness at each of the four
     corners.
   - **Edges** — whitening/nicks sampled along all four edges.
   - **Surface** — glare and scratch-like anomalies across the card face
     (the least reliable signal from a phone photo alone).
3. `CompanyGradeMapper` converts those four subgrades into an estimated
   overall grade and label for each company.
4. Everything runs locally on the device — no photo or network upload is
   required to produce a grade.
5. `CardTextRecognizer` (in `ocr/`) also runs on-device text recognition
   (Google ML Kit) over the front photo and suggests a card name, always
   shown as an editable pre-filled field — never treated as a confirmed
   identification. The recognition model downloads once over the network
   on first use; after that it runs fully offline like the rest of the app.

The whole grading engine (`app/src/main/java/com/aicardgrader/app/grading/`)
is plain Kotlin with no Android dependencies, so it can be unit-tested on a
desktop JVM independently of the Android toolchain.

## Features

- CameraX-based capture flow with a card-shaped alignment guide.
- On-device grading engine — works fully offline.
- On-device OCR name suggestion for the graded card (editable, never
  auto-trusted).
- Results screen with a per-category breakdown and a badge per grading
  company.
- History (Room database) of previously graded cards, stored locally.
- Tips screen for getting a more accurate read (lighting, glare, alignment).
- Gallery import (e.g. a photo saved from an eBay listing), with a
  pinch/pan crop step before grading.
- A "Grade a Card" home-screen widget that jumps straight into the capture
  flow — a widget can't embed a live camera itself, but skipping the app's
  home screen is exactly the kind of tap saved that matters at a card show.

## Signing

`keystore/debug.keystore` is committed on purpose — it's a debug-only key
(never sensitive), and sharing it keeps every CI build signed identically.
Without it, each fresh CI runner would generate its own random debug key,
and installing a new APK signed with a different key over an old one fails
silently on-device rather than showing a clear error.

## Building

This project can't be compiled inside some restricted/offline sandboxes
because it needs the Android SDK/Gradle plugin from Google's servers. To
build it yourself:

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

A GitHub Actions workflow (`.github/workflows/build-apk.yml`) also builds
the debug APK automatically on every push and uploads it as a workflow
artifact — check the **Actions** tab of this repository for a ready-to-download
build.

### Requirements

- JDK 17
- Android SDK (compileSdk 34, minSdk 24)
