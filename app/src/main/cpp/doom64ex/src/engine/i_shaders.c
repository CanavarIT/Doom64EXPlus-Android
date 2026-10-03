//
// i_shaders.c — GLES 3.0 адаптация шейдеров Doom64EX-Plus
//
// Оригинальные шейдеры (GLSL 1.20) переписаны под #version 300 es.
//

#include <SDL3/SDL.h>          /* SDL_GL_GetProcAddress: БЕЗ этого указатели режутся до 32 бит на arm64 */
#include <SDL3/SDL_video.h>
#include <SDL3/SDL_opengl.h>
#include <string.h>
#include <stdio.h>
#include "i_shaders.h"
#include "i_system.h"

// ============================================================
// Загрузка функций GLES 3.0
// ============================================================

static GLuint (APIENTRY* pglCreateShader)(GLenum) = NULL;
static void   (APIENTRY* pglShaderSource)(GLuint, GLsizei, const GLchar* const*, const GLint*) = NULL;
static void   (APIENTRY* pglCompileShader)(GLuint) = NULL;
static void   (APIENTRY* pglGetShaderiv)(GLuint, GLenum, GLint*) = NULL;
static void   (APIENTRY* pglGetShaderInfoLog)(GLuint, GLsizei, GLsizei*, GLchar*) = NULL;
static GLuint (APIENTRY* pglCreateProgram)(void) = NULL;
static void   (APIENTRY* pglAttachShader)(GLuint, GLuint) = NULL;
static void   (APIENTRY* pglLinkProgram)(GLuint) = NULL;
static void   (APIENTRY* pglGetProgramiv)(GLuint, GLenum, GLint*) = NULL;
static void   (APIENTRY* pglGetProgramInfoLog)(GLuint, GLsizei, GLsizei*, GLchar*) = NULL;
static void   (APIENTRY* pglUseProgram)(GLuint) = NULL;
static GLint  (APIENTRY* pglGetUniformLocation)(GLuint, const GLchar*) = NULL;
static void   (APIENTRY* pglUniform1i)(GLint, GLint) = NULL;
static void   (APIENTRY* pglUniform1f)(GLint, GLfloat) = NULL;
static void   (APIENTRY* pglUniform2f)(GLint, GLfloat, GLfloat) = NULL;
static void   (APIENTRY* pglUniform3f)(GLint, GLfloat, GLfloat, GLfloat) = NULL;
static void   (APIENTRY* pglUniform4f)(GLint, GLfloat, GLfloat, GLfloat, GLfloat) = NULL;
static void   (APIENTRY* pglUniform1fv)(GLint, GLsizei, const GLfloat*) = NULL;
static void   (APIENTRY* pglUniform1iv)(GLint, GLsizei, const GLint*) = NULL;
static void   (APIENTRY* pglUniform4fv)(GLint, GLsizei, const GLfloat*) = NULL;
static void   (APIENTRY* pglUniformMatrix4fv)(GLint, GLsizei, GLboolean, const GLfloat*) = NULL;
static void   (APIENTRY* pglVertexAttribPointer)(GLuint, GLint, GLenum, GLboolean, GLsizei, const void*) = NULL;
static void   (APIENTRY* pglEnableVertexAttribArray)(GLuint) = NULL;
static void   (APIENTRY* pglDisableVertexAttribArray)(GLuint) = NULL;
static void   (APIENTRY* pglBindAttribLocation)(GLuint, GLuint, const GLchar*) = NULL;

static int g_procs_ok = 0;   /* 1 = все нужные GL-функции найдены */

#define GL_GET(name) do { \
    *(void**)(&p##name) = (void*)SDL_GL_GetProcAddress(#name); \
    if (!p##name) { \
        I_Printf("I_ShaderInitFunctions: proc not found: %s\n", #name); \
        missing++; \
    } \
} while (0)

void I_ShaderInitFunctions(void) {
    int missing = 0;

    GL_GET(glCreateShader);
    GL_GET(glShaderSource);
    GL_GET(glCompileShader);
    GL_GET(glGetShaderiv);
    GL_GET(glGetShaderInfoLog);
    GL_GET(glCreateProgram);
    GL_GET(glAttachShader);
    GL_GET(glLinkProgram);
    GL_GET(glGetProgramiv);
    GL_GET(glGetProgramInfoLog);
    GL_GET(glUseProgram);
    GL_GET(glGetUniformLocation);
    GL_GET(glUniform1i);
    GL_GET(glUniform1f);
    GL_GET(glUniform2f);
    GL_GET(glUniform3f);
    GL_GET(glUniform4f);
    GL_GET(glUniform1fv);
    GL_GET(glUniform1iv);
    GL_GET(glUniform4fv);
    GL_GET(glUniformMatrix4fv);
    GL_GET(glVertexAttribPointer);
    GL_GET(glEnableVertexAttribArray);
    GL_GET(glDisableVertexAttribArray);
    GL_GET(glBindAttribLocation);

    g_procs_ok = (missing == 0);
    I_Printf("I_ShaderInitFunctions: %s\n", g_procs_ok ? "OK" : "some procs missing, shaders disabled");
}

// ============================================================
// GLES 3.0 шейдеры
// ============================================================

// ---------- Vertex shader: 3-point bilateral ----------
static const char* vertex_shader_3point =
"#version 300 es\n"
"in vec4 aVertex;\n"
"in vec2 aTexCoord;\n"
"in vec4 aColor;\n"
"out vec2 vUV;\n"
"out vec4 vColor;\n"
"out float vEyeDist;\n"
"uniform mat4 uMV;\n"
"uniform mat4 uMVP;\n"
"void main() {\n"
"    vec4 eye = uMV * aVertex;\n"
"    vEyeDist = length(eye.xyz);\n"
"    gl_Position = uMVP * aVertex;\n"
"    vUV = aTexCoord;\n"
"    vColor = aColor;\n"
"}\n";

// ---------- Fragment shader: 3-point bilateral ----------
static const char* fragment_shader_bilateral_3point =
"#version 300 es\n"
"precision highp float;\n"
"#define ADD_SCALE 0.60\n"
"in vec2 vUV;\n"
"in vec4 vColor;\n"
"in float vEyeDist;\n"
"out vec4 fragColor;\n"
"uniform sampler2D uTex;\n"
"uniform vec2  uTexel;\n"
"uniform float uStrength;\n"
"uniform float uBleed;\n"
"uniform float uSeamFix;\n"
"uniform float uSnap;\n"
"uniform int   uUseTex;\n"
"uniform int   uPassCount;\n"
"uniform int   uPassMode[4];\n"
"uniform vec4  uPassColor[4];\n"
"uniform float uPassFactor[4];\n"
"uniform int   uFogEnabled;\n"
"uniform vec3  uFogColor;\n"
"uniform float uFogFactor;\n"
"uniform int   uFogMode;\n"
"uniform float uFogStart;\n"
"uniform float uFogEnd;\n"
"uniform float uFogDensity;\n"
"float _lum(vec3 c){ return dot(c, vec3(0.2126,0.7152,0.0722)); }\n"
"vec3 apply_combiner(vec3 rgb){\n"
"    for (int i=0;i<4;i++){\n"
"        if (i>=uPassCount) break;\n"
"        vec3  src = uPassColor[i].rgb;\n"
"        float fac = uPassFactor[i];\n"
"        int   mode= uPassMode[i];\n"
"        if (mode==8448) rgb = rgb*src;\n"
"        else if (mode==260) rgb = rgb+src;\n"
"        else if (mode==34165) rgb = mix(rgb,src,clamp(fac,0.0,1.0));\n"
"        else if (mode==7681) rgb = src;\n"
"    }\n"
"    return rgb;\n"
"}\n"
"void main(){\n"
"    vec4 col = texture(uTex, vUV);\n"
"    vec3 rgb = col.rgb * vColor.rgb;\n"
"    rgb = apply_combiner(rgb);\n"
"    if (uFogEnabled!=0) {\n"
"        float fogF = 1.0;\n"
"        if (uFogMode == 1) {\n"
"            float denom = max(uFogEnd - uFogStart, 0.0001);\n"
"            fogF = clamp((uFogEnd - vEyeDist) / denom, 0.0, 1.0);\n"
"        } else if (uFogMode == 2) {\n"
"            fogF = clamp(exp(-uFogDensity * vEyeDist), 0.0, 1.0);\n"
"        } else if (uFogMode == 3) {\n"
"            float d = uFogDensity * vEyeDist;\n"
"            fogF = clamp(exp(-(d*d)), 0.0, 1.0);\n"
"        } else {\n"
"            fogF = clamp(1.0 - uFogFactor, 0.0, 1.0);\n"
"        }\n"
"        rgb = mix(uFogColor, rgb, fogF);\n"
"    }\n"
"    fragColor = vec4(clamp(rgb,0.0,1.0), col.a * vColor.a);\n"
"}\n";

// ---------- Vertex shader: overlay tint ----------
static const char* vertex_shader_overlay =
"#version 300 es\n"
"in vec4 aVertex;\n"
"uniform mat4 uMVP;\n"
"void main(){\n"
"    gl_Position = uMVP * aVertex;\n"
"}\n";

// ---------- Fragment shader: overlay tint ----------
static const char* fragment_shader_overlay =
"#version 300 es\n"
"precision mediump float;\n"
"uniform vec4 uColor;\n"
"out vec4 fragColor;\n"
"void main(){\n"
"    fragColor = uColor;\n"
"}\n";

// ============================================================
// Компиляция и линковка
// ============================================================

static GLuint I_ShaderCompile(GLenum type, const char* src)
{
    GLuint shader;

    if (!g_procs_ok) {
        return 0;
    }

    shader = pglCreateShader(type);
    if (!shader) {
        I_Printf("I_ShaderCompile: glCreateShader failed\n");
        return 0;
    }

    pglShaderSource(shader, 1, &src, NULL);
    pglCompileShader(shader);

    GLint ok = 0;
    pglGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[4096];
        GLsizei n = 0;
        pglGetShaderInfoLog(shader, sizeof(log), &n, log);
        I_Printf("I_ShaderCompile: compilation error:\n%.*s\n", (int)n, log);
        return 0;
    }
    return shader;
}

// ============================================================
// 3-point bilateral program
// ============================================================

GLuint I_3PointShaderProgram = 0;

GLint I_3Point_uMVP = -1;
GLint I_3Point_uMV = -1;
GLint I_3Point_uTex = -1;
GLint I_3Point_uTexel = -1;
GLint I_3Point_uPassCount = -1;
GLint I_3Point_uPassMode = -1;
GLint I_3Point_uPassColor = -1;
GLint I_3Point_uPassFactor = -1;
GLint I_3Point_uFogEnabled = -1;
GLint I_3Point_uFogColor = -1;
GLint I_3Point_uFogFactor = -1;
GLint I_3Point_uFogMode = -1;
GLint I_3Point_uFogStart = -1;
GLint I_3Point_uFogEnd = -1;
GLint I_3Point_uFogDensity = -1;

void I_3PointShaderInit(void)
{
    if (I_3PointShaderProgram) return;
    if (!g_procs_ok) return;

    I_Printf("I_3PointShaderInit: creating vertex shader\n");
    GLuint vs = I_ShaderCompile(GL_VERTEX_SHADER, vertex_shader_3point);
    I_Printf("I_3PointShaderInit: creating fragment shader\n");
    GLuint fs = I_ShaderCompile(GL_FRAGMENT_SHADER, fragment_shader_bilateral_3point);
    if (!vs || !fs) {
        I_Printf("I_3PointShaderInit: shader compile failed, using fixed pipeline\n");
        return;
    }
    I_Printf("I_3PointShaderInit: shaders OK\n");

    GLuint prog = pglCreateProgram();
    pglAttachShader(prog, vs);
    pglAttachShader(prog, fs);

    pglBindAttribLocation(prog, 0, "aVertex");
    pglBindAttribLocation(prog, 1, "aTexCoord");
    pglBindAttribLocation(prog, 2, "aColor");

    pglLinkProgram(prog);

    GLint ok = 0;
    pglGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[4096];
        GLsizei n = 0;
        pglGetProgramInfoLog(prog, sizeof(log), &n, log);
        I_Printf("I_3PointShaderInit: Linkage error:\n%.*s\n", (int)n, log);
        return;
    }

    I_3PointShaderProgram = prog;

    I_3Point_uMVP = pglGetUniformLocation(prog, "uMVP");
    I_3Point_uMV  = pglGetUniformLocation(prog, "uMV");
    I_3Point_uTex = pglGetUniformLocation(prog, "uTex");
    I_3Point_uTexel = pglGetUniformLocation(prog, "uTexel");
    I_3Point_uPassCount = pglGetUniformLocation(prog, "uPassCount");
    I_3Point_uPassMode = pglGetUniformLocation(prog, "uPassMode");
    I_3Point_uPassColor = pglGetUniformLocation(prog, "uPassColor");
    I_3Point_uPassFactor = pglGetUniformLocation(prog, "uPassFactor");
    I_3Point_uFogEnabled = pglGetUniformLocation(prog, "uFogEnabled");
    I_3Point_uFogColor = pglGetUniformLocation(prog, "uFogColor");
    I_3Point_uFogFactor = pglGetUniformLocation(prog, "uFogFactor");
    I_3Point_uFogMode = pglGetUniformLocation(prog, "uFogMode");
    I_3Point_uFogStart = pglGetUniformLocation(prog, "uFogStart");
    I_3Point_uFogEnd = pglGetUniformLocation(prog, "uFogEnd");
    I_3Point_uFogDensity = pglGetUniformLocation(prog, "uFogDensity");
}

// ============================================================
// Overlay tint program
// ============================================================

GLuint I_OverlayTintProgram = 0;
GLint  I_OverlayTint_uMVP = -1;
GLint  I_OverlayTint_uColor = -1;

void I_OverlayTintShaderInit(void)
{
    if (I_OverlayTintProgram) return;
    if (!g_procs_ok) return;

    GLuint vs = I_ShaderCompile(GL_VERTEX_SHADER, vertex_shader_overlay);
    GLuint fs = I_ShaderCompile(GL_FRAGMENT_SHADER, fragment_shader_overlay);
    if (!vs || !fs) {
        return;
    }

    GLuint prog = pglCreateProgram();
    pglAttachShader(prog, vs);
    pglAttachShader(prog, fs);

    pglBindAttribLocation(prog, 0, "aVertex");

    pglLinkProgram(prog);

    GLint ok = 0;
    pglGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[4096];
        GLsizei n = 0;
        pglGetProgramInfoLog(prog, sizeof(log), &n, log);
        I_Printf("I_OverlayTintShaderInit: Linkage error:\n%.*s\n", (int)n, log);
        return;
    }

    I_OverlayTintProgram = prog;
    I_OverlayTint_uMVP = pglGetUniformLocation(prog, "uMVP");
    I_OverlayTint_uColor = pglGetUniformLocation(prog, "uColor");
}

// ============================================================
// Заглушки для функций, которые нужны движку
// ============================================================

static int g_program_bound = 0;

void I_ShaderBind(void)
{
    if (g_procs_ok && pglUseProgram && I_3PointShaderProgram) {
        pglUseProgram(I_3PointShaderProgram);
        g_program_bound = 1;
    }
}

void I_ShaderUnBind(void)
{
    /* glUseProgram(0) вызываем только если мы сами что-то биндили */
    if (g_procs_ok && pglUseProgram && g_program_bound) {
        pglUseProgram(0);
        g_program_bound = 0;
    }
}

void I_ShaderSetUseTexture(int use)
{
    if (g_procs_ok && I_3PointShaderProgram && I_3Point_uTex >= 0) {
        pglUniform1i(I_3Point_uTex, use);
    }
}

void I_ShaderSetTextureSize(int w, int h)
{
    if (g_procs_ok && I_3PointShaderProgram && I_3Point_uTexel >= 0) {
        pglUniform2f(I_3Point_uTexel, (float)w, (float)h);
    }
}

// ============================================================
// Fullscreen tint (оставлено из оригинала)
// ============================================================

void I_ShaderFullscreenTint(float r, float g, float b, float a)
{
    if (a <= 0.0f) return;
    if (!g_procs_ok) return;
    I_OverlayTintShaderInit();
    if (!I_OverlayTintProgram) return;

    GLint oldProg = 0;
    glGetIntegerv(GL_CURRENT_PROGRAM, &oldProg);

    pglUseProgram(I_OverlayTintProgram);
    pglUniform4f(I_OverlayTint_uColor, r, g, b, a);
    pglUseProgram(oldProg);
}