# Third-party notices

This project redistributes the following prebuilt binaries in
`app/src/main/jniLibs/arm64-v8a/`. Each component remains under its own license.
Before publishing a release, copy the full license text of each component from its
upstream repository into a `licenses/` folder (the MIT, libpng and Apache-2.0
licenses require that the copyright and license notice accompany copies).

| Component | File | License | Upstream |
|---|---|---|---|
| NG-GL4ES (gl4es fork, branch `Openmw2`) | `libGL.so` | MIT (as upstream gl4es) | https://github.com/Sisah2/NG-GL4ES (based on https://github.com/ptitSeb/gl4es) |
| SDL3 | `libSDL3.so` | zlib | https://github.com/libsdl-org/SDL |
| libpng | `libpng16.so` | libpng license | https://github.com/pnggroup/libpng |
| SPIRV-Cross | `libspirv-cross-c-shared.so` | Apache-2.0 | https://github.com/KhronosGroup/SPIRV-Cross |

The Java files in `app/src/main/java/org/libsdl/app/` come from SDL3 (zlib license).

## Not redistributed

* **FMOD Studio** (`fmod.jar`, `libfmod.so`, headers) – proprietary, © Firelight Technologies Pty Ltd.
  Obtain it from https://www.fmod.com/download under the FMOD license. When you distribute an app that
  uses FMOD, the FMOD license requires attribution ("FMOD Studio by Firelight Technologies Pty Ltd.").
* **DOOM 64 game data** – © their respective owners.

The engine itself (`app/src/main/cpp/doom64ex/`) is under the Doom Source License; see `LICENSE`
and `app/src/main/cpp/doom64ex/AUTHORS`.
