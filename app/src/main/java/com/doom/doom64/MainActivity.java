package com.doom.doom64;

import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import org.fmod.FMOD;
import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends SDLActivity {

    private static final String TAG = "Doom64";
    private static final int BUFFER_SIZE = 65536;

    private static final String[] GAME_FILES = {
        "Doom64.kpf",
        "DOOM64.WAD",
        "DOOMSND.DLS",
        "doom64ex-plus.wad"
    };

    private TouchControlsView touchControls;
    private static boolean sUiStateWarned = false;

    /**
     * Реализация в touch_jni.c (src/engine).
     * 0 — игрок в уровне, 1 — меню / титры / интермиссия.
     */
    private static native int nativeGetUiState();

    /** Безопасная обёртка: -1, если нативная функция недоступна. */
    static int queryUiState() {
        try {
            return nativeGetUiState();
        } catch (UnsatisfiedLinkError e) {
            if (!sUiStateWarned) {
                sUiStateWarned = true;
                Log.w(TAG, "nativeGetUiState not found (touch_jni.c not built?)", e);
            }
            return -1;
        }
    }

    /** p_autorun (Always Run) из движка: 1 / 0. Реализация в touch_jni.c */
    private static native int nativeGetAutorun();

    private static native void nativeSetAutorun(int value);

    /** 1 / 0, либо -1, если нативная функция недоступна. */
    static int queryAutorun() {
        try {
            return nativeGetAutorun();
        } catch (UnsatisfiedLinkError e) {
            return -1;
        }
    }

    static void setAutorun(boolean on) {
        try {
            nativeSetAutorun(on ? 1 : 0);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "nativeSetAutorun not found", e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        copyGameFilesIfNeeded();
        FMOD.init(this);

        // NG-GL4ES (ветка Openmw2) собран с -DDEFAULT_ES=3 → использует GLES 3.0 сам.
        // Активируем кастомный конвертер шейдеров OpenMW для работы с #version 120.
        // LIBGL_ES и LIBGL_GL НЕ нужны — флаг DEFAULT_ES уже встроен в библиотеку.
        try {
            android.system.Os.setenv("LIBGL_SIMPLE_SHADERCONV", "1", true);
            Log.i(TAG, "LIBGL_SIMPLE_SHADERCONV=1 set OK");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to set LIBGL_SIMPLE_SHADERCONV", t);
        }

        super.onCreate(savedInstanceState);

        attachTouchControls();
    }

    /** Добавляет экранное управление поверх SDL-поверхности. */
    private void attachTouchControls() {
        try {
            ViewGroup layout = mLayout;
            if (layout == null) {
                Log.w(TAG, "mLayout is null, touch controls not attached");
                return;
            }
            touchControls = new TouchControlsView(this);
            layout.addView(touchControls, new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            Log.i(TAG, "Touch controls attached");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to attach touch controls", t);
        }
    }

    @Override
    protected void onPause() {
        if (touchControls != null) {
            touchControls.onHostPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (touchControls != null) {
            touchControls.onHostResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (touchControls != null) {
            touchControls.onHostPause();
            touchControls = null;
        }
        super.onDestroy();
        try {
            FMOD.close();
        } catch (Throwable t) {
            Log.w(TAG, "FMOD.close failed", t);
        }
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "GL", "SDL3", "fmod", "png16", "doom64" };
    }

    @Override
    protected String getMainSharedObject() {
        return "libdoom64.so";
    }

    @Override
    protected String getMainFunction() {
        return "SDL_main";
    }

    private void copyGameFilesIfNeeded() {
        File filesDir = getFilesDir();

        for (String name : GAME_FILES) {
            File target = new File(filesDir, name);

            if (target.exists() && target.length() > 0) {
                Log.i(TAG, name + " already exists: " + target.length() + " bytes");
                continue;
            }

            Log.i(TAG, "Copying " + name + " from assets...");
            long startTime = System.currentTimeMillis();

            InputStream in = null;
            OutputStream out = null;
            try {
                in = getAssets().open(name);
                out = new FileOutputStream(target);

                byte[] buffer = new byte[BUFFER_SIZE];
                int n;
                long total = 0;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    total += n;
                }
                out.flush();

                long elapsed = System.currentTimeMillis() - startTime;
                Log.i(TAG, name + " copied: " + total + " bytes in " + elapsed + " ms");

            } catch (IOException e) {
                Log.e(TAG, "Error copying " + name, e);
                if (target.exists()) target.delete();
            } finally {
                try { if (in != null) in.close(); } catch (IOException ignored) {}
                try { if (out != null) out.close(); } catch (IOException ignored) {}
            }
        }
    }
}
