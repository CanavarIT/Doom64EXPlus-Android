/*
 * Android gl4es initialization helper (stub).
 *
 * NG-GL4ES (ветка Openmw2) инициализируется автоматически через
 * встроенный конструктор при загрузке libGL.so. Экспорта initialize_gl4es
 * в этой сборке нет, поэтому вызывать её вручную не нужно.
 */

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "Doom64"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

void Doom64_InitGl4es(void)
{
    LOGI("Doom64_InitGl4es: NG-GL4ES auto-init (no-op)");
}
#else
void Doom64_InitGl4es(void) { /* no-op */ }
#endif
