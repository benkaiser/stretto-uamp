# AGENTS.md

## Project Overview

This is **Stretto Android Auto** — a fork of Google's [Universal Android Music Player (UAMP)](https://github.com/android/uamp) customized to serve as the Android Auto / Android Automotive OS client for Stretto, a personal music streaming service. It loads a music catalog from a remote Stretto server and provides playback across Android phones, Android Auto, and Android Automotive OS.

## Repository Structure

This is a multi-module Android Gradle project with three modules:

```
stretto-android-auto/
├── app/            # Mobile phone app (Android application)
├── common/         # Shared library (media service, playback, browse tree, data layer)
├── automotive/     # Android Automotive OS app (car head unit, includes auth flows)
├── docs/           # Architecture guide and FAQs
└── .github/        # CI workflows and scripts
```

- **`:common`** is the core module — it contains `MusicService`, `BrowseTree`, `JsonSource`, and all shared media logic. Both `:app` and `:automotive` depend on it.
- **`:app`** is the mobile UI using MVVM (ViewModels + LiveData + Fragments).
- **`:automotive`** extends `MusicService` with sign-in/auth flows for in-car use.

## Language and Key Technologies

- **Kotlin** (1.9.21) — all application code
- **AndroidX Media3** (1.2.1) — `MediaLibraryService`, `MediaBrowser`, ExoPlayer, Cast
- **AndroidX Lifecycle** — ViewModel, LiveData
- **Kotlin Coroutines** (1.6.4)
- **Glide** (4.12.0) — image loading
- **GSON** (2.10) — JSON catalog parsing
- **Android SDK**: compileSdk 34, minSdk 19 (app/common) / 21 (automotive), targetSdk 34
- **Java compatibility**: Java 1.8

## Build System

This project uses **Gradle 8.5** with **Android Gradle Plugin 8.0.1**.

### Common Build Commands

```bash
# Build all modules (debug)
./gradlew assembleDebug

# Build specific module
./gradlew :app:assembleDebug
./gradlew :automotive:assembleDebug

# Full build
./gradlew build

# Clean
./gradlew clean
```

All dependency versions are centralized as `ext` properties in the root `build.gradle`.

## Testing

### Frameworks
- **JUnit 4** (4.13.2) — unit tests
- **Robolectric** (4.11) — Android unit tests on JVM (used in `common`)
- **Espresso** (3.5.1) — instrumentation tests (configured in `automotive`)

### Test Locations
- `common/src/test/` — unit tests (e.g., `MusicSourceTest.kt`)
- `automotive/src/test/` — unit tests (placeholder)
- `automotive/src/androidTest/` — instrumentation tests (placeholder)
- `app/` — no tests currently

### Running Tests
```bash
# All unit tests
./gradlew test

# Common module tests only
./gradlew :common:test

# Automotive instrumentation tests (requires device/emulator)
./gradlew :automotive:connectedAndroidTest
```

## Code Style

- **Kotlin Official** code style (configured in `.idea/codeStyles/Project.xml`)
- No star imports (star import threshold set to max int)
- Follows the [Android Code Style Guide](https://source.android.com/source/code-style.html)
- No automated linter (no ktlint, detekt, or spotless configured)

## Architecture

The app follows a **client/server architecture** using Android's media framework:

### Server Side (Background Service)
- `MusicService` (`MediaLibraryService`) in `:common` — handles catalog loading, browse tree, playback (ExoPlayer), Cast sessions, and caller validation
- `JsonSource` loads the music catalog from the remote Stretto server
- `BrowseTree` constructs the browsable media tree (albums, playlists with MRU sorting)
- `PackageValidator` authorizes connecting media browser clients

### Client Side (UI)
- MVVM pattern with manual dependency injection (`InjectorUtils`)
- `MusicServiceConnection` wraps `MediaBrowser`/`MediaController` as a singleton
- ViewModels expose `LiveData` to Fragments
- `MainActivity` hosts `MediaItemFragment` (browse) and `NowPlayingFragment` (playback)

### Key Customizations from Stock UAMP
- Remote catalog URL points to the Stretto server
- Playlist support with Most Recently Used (MRU) sorting
- Shuffled playback queue starting from the selected song
- Pagination support in `onGetChildren`

## CI/CD

GitHub Actions (`.github/workflows/android.yml`):
- Triggers on push/PR to `main`
- Builds `assembleDebug` and uploads APK artifacts

## Important Files

| File | Purpose |
|------|---------|
| `common/.../MusicService.kt` | Core media service — playback, session, browse tree |
| `common/.../library/BrowseTree.kt` | Media catalog tree structure |
| `common/.../library/JsonSource.kt` | Remote JSON catalog loader |
| `common/.../library/MusicSource.kt` | Music source interface |
| `common/.../MusicServiceConnection.kt` | Client-side MediaBrowser wrapper |
| `app/.../MainActivity.kt` | Mobile app entry point |
| `automotive/.../AutomotiveMusicService.kt` | Automotive service with auth |
| `build.gradle` (root) | Centralized dependency versions |
| `settings.gradle` | Module includes, optional local Media3 linking |

## License

Apache 2.0 — see `LICENSE` file.
