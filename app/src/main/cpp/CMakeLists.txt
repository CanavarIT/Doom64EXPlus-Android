cmake_minimum_required(VERSION 3.22.1)
project(doom64 C)

set(CMAKE_C_STANDARD 11)

# ---------- Пути ----------
set(CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR})
set(DOOM_DIR ${CPP_DIR}/doom64ex)
set(DOOM_SRC ${DOOM_DIR}/src/engine)

# ---------- IMPORTED: SDL3 ----------
add_library(SDL3 SHARED IMPORTED)
set_target_properties(SDL3 PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libSDL3.so
    INTERFACE_INCLUDE_DIRECTORIES ${CPP_DIR}/sdl3/include
)

# ---------- IMPORTED: FMOD ----------
add_library(fmod SHARED IMPORTED)
set_target_properties(fmod PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libfmod.so
    INTERFACE_INCLUDE_DIRECTORIES ${CPP_DIR}/fmod/include
)

# ---------- IMPORTED: libpng ----------
add_library(png SHARED IMPORTED)
set_target_properties(png PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpng16.so
    IMPORTED_SONAME "libpng16.so"
    INTERFACE_INCLUDE_DIRECTORIES ${CPP_DIR}/png/include
)

# ---------- IMPORTED: gl4es ----------
add_library(GL SHARED IMPORTED)
set_target_properties(GL PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libGL.so
    IMPORTED_SONAME "libGL.so"
    INTERFACE_INCLUDE_DIRECTORIES ${CPP_DIR}/gl4es/include
)

# ---------- Системные библиотеки NDK ----------
find_library(z-lib z)
find_library(EGL-lib EGL)
find_library(GLES-lib GLESv3)
find_library(log-lib log)
find_library(android-lib android)

# ---------- Исходники движка ----------
file(GLOB DOOM_SOURCES ${DOOM_SRC}/*.c)

# ---------- Цель: libdoom64.so ----------
add_library(doom64 SHARED
    ${DOOM_SOURCES}
)

target_include_directories(doom64 PRIVATE
    ${CPP_DIR}/gl4es_sdl_shim
    ${CPP_DIR}/sdl3/include
    ${CPP_DIR}/fmod/include
    ${CPP_DIR}/png/include
    ${CPP_DIR}/gl4es/include
    ${DOOM_SRC}
)

# -Dmain=SDL_main переименовывает точку входа движка,
# чтобы SDLActivity мог её вызвать через nativeRunMain("libdoom64.so", "SDL_main", ...)
target_compile_definitions(doom64 PRIVATE
    __ANDROID__
    SDL_MAIN_HANDLED
    GL_GLEXT_PROTOTYPES
    main=SDL_main
)

target_compile_options(doom64 PRIVATE
    -include ${CPP_DIR}/doom64_patch.h
    -fwrapv
    -g0
    -Wno-unknown-pragmas
    -Wno-int-conversion
    -Wno-incompatible-pointer-types
    -Wno-implicit-function-declaration
    -Wno-implicit-int
    -Wno-pointer-sign
    -Wno-error=implicit-function-declaration
)

# lld на телефоне падает по памяти: линкуем в один поток
target_link_options(doom64 PRIVATE
    -Wl,--threads=1
)

target_link_libraries(doom64
    SDL3
    fmod
    png
    GL
    ${z-lib}
    ${EGL-lib}
    ${GLES-lib}
    ${log-lib}
    ${android-lib}
    m
)