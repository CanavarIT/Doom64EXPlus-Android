/*
 * touch_jni.c
 *
 * JNI-мост для тач-управления:
 *  - nativeGetUiState: где игрок — в уровне (0) или в меню/титрах/интермиссии (1)
 *  - nativeGetAutorun / nativeSetAutorun: настройка движка «Always Run» (p_autorun)
 */

#ifdef __ANDROID__

#include <jni.h>

#include "doomdef.h"
#include "doomstat.h"
#include "con_cvar.h"

CVAR_EXTERNAL(p_autorun);

JNIEXPORT jint JNICALL
Java_com_doom_doom64_MainActivity_nativeGetUiState(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;

    return (menuactive || gamestate != GS_LEVEL) ? 1 : 0;
}

JNIEXPORT jint JNICALL
Java_com_doom_doom64_MainActivity_nativeGetAutorun(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;

    return p_autorun.value != 0.0f ? 1 : 0;
}

JNIEXPORT void JNICALL
Java_com_doom_doom64_MainActivity_nativeSetAutorun(JNIEnv *env, jclass cls, jint value)
{
    (void)env;
    (void)cls;

    CON_CvarSetValue(p_autorun.name, value ? 1.0f : 0.0f);
}

#endif /* __ANDROID__ */