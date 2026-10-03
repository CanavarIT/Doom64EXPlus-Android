#ifndef DOOM64_PATCH_H
#define DOOM64_PATCH_H

/*
 * Патч для Doom64EX-Plus при компиляции как shared-библиотеки на Android.
 *
 * 1) i_audio.h включает <fmod_common.h> (только типы), но i_fmod_sfx.c
 *    вызывает FMOD_System_CreateSound, которая объявлена в <fmod.h>.
 *
 * 2) Переменная `sound` определена в i_audio.c (struct Sound sound;),
 *    но не объявлена extern в i_audio.h — i_fmod_sfx.c её не видит.
 *
 * 3) Некоторые файлы (p_pspr.c, r_bsp.c) используют SDL_INLINE,
 *    не включая никакой SDL3-заголовок. SDL_INLINE определён в
 *    <SDL3/SDL_begin_code.h>, который подтягивается через SDL_stdinc.h.
 */

#include <SDL3/SDL_stdinc.h>
#include <fmod.h>

struct Sound;
extern struct Sound sound;

#endif /* DOOM64_PATCH_H */
