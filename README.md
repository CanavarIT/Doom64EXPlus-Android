# Doom 64 for Android

A native Android port of **[Doom 64 EX+](https://github.com/atsb/Doom64EX-Plus)** (itself a continuation of Samuel "Kaiser" Villarreal's Doom64EX), with on-screen touch controls, an on-screen keyboard for save names, true full-screen rendering and several Android-specific rendering fixes.

> **This repository contains no game data.** You need your own legally owned copy of *DOOM 64* (Nightdive remaster, Steam or GOG) to play. See [Game data](#game-data-not-included).
>
> This is an unofficial fan project. It is not affiliated with or endorsed by id Software, Bethesda, Nightdive Studios, or the authors of Doom64EX / Doom64 EX+.

## Features

- Native engine (C, built with the NDK) running through **SDL3** and an **OpenGL ES** translation layer (**NG-GL4ES**)
- **Touch controls** drawn as an overlay on top of the SDL surface:
  - floating virtual joystick (movement and running)
  - swipe the right side of the screen to look around
  - hold the fire button and drag the same finger to aim while shooting
  - FIRE, second FIRE (left side), USE, previous / next weapon, automap, pause
  - menu mode with D-pad, OK and BACK buttons (switches automatically between menu and gameplay)
  - settings dialog: camera sensitivity, button opacity, button size, Always Run, second fire button, haptics
  - layout respects display cutouts and system gesture areas
- **Saving through the in-game menu** with the Android on-screen keyboard for the save name
- **Full-screen** rendering (immersive mode) with the logical video size synchronised to the real surface, so the HUD is never cut off
- **Natural weapon colours** (the weapon is no longer tinted red / orange / blue by coloured sector lights)
- Correct **Always Run** handling (see [Running](#running))

## How it was made

The project is an Android Studio / Gradle project that wraps the Doom 64 EX+ engine:

1. **Engine** – the Doom 64 EX+ sources (`app/src/main/cpp/doom64ex`) are a trimmed copy of the upstream repository. Desktop-only parts (Windows / macOS / Linux packaging) were removed.
2. **Java shell** – `org.libsdl.app.*` is the standard SDL3 Android glue. `MainActivity` extends `SDLActivity`, copies the game files from the APK assets into the app's private storage on first launch, initialises FMOD, and loads the native libraries (`GL`, `SDL3`, `fmod`, `png16`, `doom64`).
3. **Native build** – `app/src/main/cpp/CMakeLists.txt` compiles all engine sources into `libdoom64.so` and links against prebuilt `libSDL3.so`, `libfmod.so`, `libpng16.so` and `libGL.so`. `main` is renamed to `SDL_main` so that `SDLActivity` can start it.
4. **Graphics** – Doom 64 EX+ uses desktop OpenGL (fixed function plus GLSL 1.20 shaders). On Android it runs through **NG-GL4ES**, a gl4es fork that translates desktop GL to GLES 3. The engine was adapted to initialise gl4es (`i_gl4es_init.c`) and the shader code (`i_shaders.c/.h`) was adjusted for it.
5. **Audio** – FMOD Studio, as in upstream.
6. **Touch controls** – `TouchControlsView` is a custom `View` added on top of the SDL surface. It does not change the engine's input model: it injects Android key events and relative mouse movement through `SDLActivity.onNativeKeyDown/Up` and `onNativeMouse`. A tiny JNI bridge (`touch_jni.c`) tells Java whether the player is in a level or in a menu and lets the overlay read and change the engine's *Always Run* setting.
7. **Development environment** – the port was developed and built entirely on an Android phone with AndroidIDE (NDK 29, CMake 3.31.6). The touch controls and several fixes were written with the help of an AI assistant (Claude by Anthropic).

### Engine changes compared with upstream Doom 64 EX+

| Area | Files | What changed |
|---|---|---|
| Android entry point, logging, paths | `i_main.c`, `d_main.c` | `SDL_main` entry, `logcat` output, Android data paths |
| GL / shader adaptation | `gl_main.c`, `dgl.h`, `i_shaders.c/.h`, `i_gl4es_init.c`, `r_main.c`, `r_drawlist.c` | gl4es initialisation and GLES-friendly shaders |
| Audio | `i_fmod_sfx.c` | FMOD on Android |
| Input | `i_sdlinput.c`, `m_menu.c`, `touch_jni.c` | text input events for the on-screen keyboard, `M_IsTextInputActive()`, UI-state and Always Run JNI bridge |
| Video | `i_video.c/.h` | forced full-screen on Android, logical size synchronised with the real surface, resize handling |
| Rendering | `r_things.c` | simplified weapon shading on Android (natural colours) |

## Repository layout

```
app/
  libs/                      fmod.jar                (NOT in the repo – see below)
  src/main/
    AndroidManifest.xml
    assets/                  game data               (NOT in the repo – see below)
    jniLibs/arm64-v8a/       prebuilt .so libraries  (NOT in the repo – see below)
    java/
      com/doom/doom64/       MainActivity, TouchControlsView
      org/libsdl/app/        SDL3 Android glue
    cpp/
      CMakeLists.txt
      doom64_patch.h
      doom64ex/              Doom 64 EX+ engine sources (+ Android patches)
      sdl3/include/          SDL3 headers
      png/include/           libpng headers
      gl4es/include/         GL / GLES headers
      gl4es_sdl_shim/
      fmod/include/          FMOD headers            (NOT in the repo – see below)
```

## Game data (not included)

The game files come from your own purchased copy of *DOOM 64* (Nightdive remaster, Steam or GOG). Put these files into `app/src/main/assets/` before building:

| File | Source |
|---|---|
| `DOOM64.WAD` | your *DOOM 64* installation |
| `Doom64.kpf` | your *DOOM 64* installation |
| `DOOMSND.DLS` | your *DOOM 64* installation |
| `doom64ex-plus.wad` | the [Doom64EX-Plus](https://github.com/atsb/Doom64EX-Plus) repository |

On first launch `MainActivity` copies them to the app's private storage.

> **Please do not publish APKs that contain these files.** They are copyrighted and may not be redistributed.

## Third-party binaries (not included)

| Library | Where to get it | Put it in |
|---|---|---|
| **FMOD Studio API for Android** (proprietary) | [fmod.com/download](https://www.fmod.com/download) (free account) | `fmod.jar` → `app/libs/`, `libfmod.so` → `app/src/main/jniLibs/arm64-v8a/`, headers → `app/src/main/cpp/fmod/include/` |
| **SDL3** (`libSDL3.so`) | build from [libsdl-org/SDL](https://github.com/libsdl-org/SDL); the version must match the Java glue in `org.libsdl.app` | `app/src/main/jniLibs/arm64-v8a/` |
| **libpng** (`libpng16.so`) | build from [pnggroup/libpng](https://github.com/pnggroup/libpng) | `app/src/main/jniLibs/arm64-v8a/` |
| **NG-GL4ES** (`libGL.so`) | build a gl4es fork with `-DDEFAULT_ES=3`, together with its runtime dependencies (for example `libspirv-cross-c-shared.so`) | `app/src/main/jniLibs/arm64-v8a/` |

Only `arm64-v8a` is built.

## Building

Requirements: Android SDK (compileSdk 36), NDK `29.0.14033849`, CMake `3.31.6`, JDK 17, Gradle 9 (wrapper included), Android Gradle Plugin 8.13.

1. Clone the repository.
2. Add the FMOD files, the prebuilt libraries and the game data as described above.
3. Build:
   ```bash
   ./gradlew assembleDebug
   ```
   The APK is written to `app/build/outputs/apk/debug/`.
4. Install it on an arm64 device running Android 10 (API 29) or newer.

**Building on a phone:** the linker (`lld`) can run out of memory. `CMakeLists.txt` therefore compiles with `-g0` and links with `-Wl,--threads=1`. If the build still aborts during linking, close other apps and build again.

## Controls

| Control | Action |
|---|---|
| Left half of the screen (touch and drag) | Floating joystick: move / strafe |
| Right half of the screen (swipe) | Look around |
| FIRE (hold) | Shoot; drag the same finger to aim while shooting |
| Left FIRE button | Second fire button (can be turned off in settings) |
| USE | Open doors, press switches |
| «  /  » | Previous / next weapon |
| Pause icon | Pause / game menu |
| Map icon | Automap |
| In menus | D-pad, OK, BACK; the cog icon opens the touch-control settings |

### Running

In Doom 64 EX+ the running speed is *"Run key XOR Always Run"*:

- **Always Run ON** (default): you always run; the overlay never presses the run key.
- **Always Run OFF**: you walk; push the joystick all the way to the edge (the knob turns red) to run.

The checkbox in the touch settings is the same setting as *Always Run* in the game menu.

### Saving

Pause → **Save Game** → pick a slot → type the name with the on-screen keyboard → Enter. There is no quick-save button. Only printable ASCII characters can be used in save names (the game font has no other glyphs).

## Known limitations

- `arm64-v8a` only.
- Landscape orientation only.
- The game keeps its original 4:3 proportions; on very wide screens black bars may appear on the sides.
- Game data must be bundled at build time (there is no in-app file picker yet).

## Credits

- **id Software** – original Doom engine
- **Midway / Nightdive Studios** – *DOOM 64* and its remaster
- **Samuel "Kaiser" Villarreal** – Doom64EX
- **atsb ("Gibbon") and contributors** – [Doom 64 EX+](https://github.com/atsb/Doom64EX-Plus)
- **SDL** – [libsdl.org](https://libsdl.org)
- **gl4es / NG-GL4ES** – OpenGL to OpenGL ES translation
- **libpng**, **zlib**, **SPIRV-Cross**
- **FMOD Studio by Firelight Technologies Pty Ltd.**

See `app/src/main/cpp/doom64ex/AUTHORS` for the full list of engine contributors.

## License

The engine code, and therefore this port as a derivative work, is distributed under the terms of the original **Doom Source License** (the *id Software Limited Use Software License Agreement*). The full text is in [`LICENSE`](LICENSE) (identical to `app/src/main/cpp/doom64ex/COPYING`). In short, the license is **non-commercial**: the software may not be sold, rented, or distributed for money or any other consideration. Read the full text before using or redistributing anything from this repository.

Third-party components keep their own licenses:

| Component | License |
|---|---|
| SDL3 and the Java glue in `org.libsdl.app` | zlib |
| libpng | libpng license |
| zlib | zlib |
| gl4es / NG-GL4ES | MIT |
| SPIRV-Cross | Apache-2.0 |
| OpenGL / GLES headers | MIT (Mesa / Khronos headers) |
| FMOD Studio | proprietary, © Firelight Technologies – not included, you must obtain it under its own license |
| *DOOM 64* game data | © their respective owners – not included |

*DOOM*, *DOOM 64* and related names are trademarks of their respective owners.
