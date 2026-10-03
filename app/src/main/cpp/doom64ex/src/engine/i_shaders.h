#ifndef __I_SHADERS_H__
#define __I_SHADERS_H__

#include <SDL3/SDL_opengl.h>

void I_ShaderInitFunctions(void);

void I_3PointShaderInit(void);
void I_OverlayTintShaderInit(void);
void I_ShaderFullscreenTint(float r, float g, float b, float a);

extern GLuint I_3PointShaderProgram;
extern GLuint I_OverlayTintProgram;

extern GLint I_3Point_uMVP;
extern GLint I_3Point_uMV;
extern GLint I_3Point_uTex;
extern GLint I_3Point_uTexel;
extern GLint I_3Point_uPassCount;
extern GLint I_3Point_uPassMode;
extern GLint I_3Point_uPassColor;
extern GLint I_3Point_uPassFactor;
extern GLint I_3Point_uFogEnabled;
extern GLint I_3Point_uFogColor;
extern GLint I_3Point_uFogFactor;
extern GLint I_3Point_uFogMode;
extern GLint I_3Point_uFogStart;
extern GLint I_3Point_uFogEnd;
extern GLint I_3Point_uFogDensity;

extern GLint I_OverlayTint_uMVP;
extern GLint I_OverlayTint_uColor;

void I_ShaderBind(void);
void I_ShaderUnBind(void);
void I_ShaderSetUseTexture(int use);
void I_ShaderSetTextureSize(int w, int h);

#endif
