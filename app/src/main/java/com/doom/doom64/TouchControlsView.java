package com.doom.doom64;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.DisplayCutout;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.libsdl.app.SDLActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Экранное управление для Doom 64.
 *
 * Игра:   плавающий джойстик слева, камера свайпом справа, кнопки FIRE / USE / оружие / карта / пауза.
 * Меню:   D-pad + OK + BACK + кнопка настроек (касания мимо кнопок уходят в SDL как обычная мышь).
 *
 * Всё отправляется в движок через SDLActivity.onNativeKeyDown/Up и onNativeMouse,
 * поэтому нативный код движка менять не нужно (кроме крошечного touch_jni.c).
 */
public class TouchControlsView extends View {

    // ------------------------------------------------------------------ режимы
    private static final int MODE_GAME = 0;
    private static final int MODE_MENU = 1;

    // ------------------------------------------------------------------ id кнопок
    private static final int B_FIRE = 0;
    private static final int B_FIRE2 = 1;
    private static final int B_USE = 2;
    private static final int B_NEXT = 3;
    private static final int B_PREV = 4;
    private static final int B_MAP = 5;
    private static final int B_PAUSE = 6;
    private static final int B_UP = 7;
    private static final int B_DOWN = 8;
    private static final int B_LEFT = 9;
    private static final int B_RIGHT = 10;
    private static final int B_OK = 11;
    private static final int B_BACK = 12;
    private static final int B_SETTINGS = 13;
    private static final int B_COUNT = 14;

    // ------------------------------------------------------------------ цвета
    private static final int C_RED = 0xFFFF4336;
    private static final int C_AMBER = 0xFFFFB300;
    private static final int C_CYAN = 0xFF29D6E8;
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_GREEN = 0xFF66E07A;

    // ------------------------------------------------------------------ настройки
    private static final String PREFS = "doom64_touch";
    private static final float LOOK_BASE = 0.55f;      // базовый множитель свайпа камеры
    private static final float LOOK_ZONE_X = 0.42f;    // левее — джойстик, правее — камера
    private static final float STICK_DIR_THRESHOLD = 0.33f;
    private static final float STICK_DEADZONE = 0.20f;
    private static final float STICK_RUN_THRESHOLD = 0.90f;

    private float lookSens = 1.0f;     // 0.2 .. 3.0
    private float opacity = 0.80f;     // 0.2 .. 1.0
    private float sizeScale = 1.0f;    // 0.7 .. 1.4
    private boolean leftFire = true;
    private boolean haptics = true;

    private final SharedPreferences prefs;

    // ------------------------------------------------------------------ кнопка
    private static final class Btn {
        final int id;
        final int key;        // Android KeyEvent.KEYCODE_*, 0 — нет
        final int wheel;      // +1 / -1 — прокрутка колеса мыши (смена оружия)
        final boolean repeat; // автоповтор при удержании
        final int accent;

        boolean visible;
        float cx, cy, r;
        int pointer = -1;
        long nextRepeat;
        float lastX, lastY;   // для поворота камеры пальцем, удерживающим кнопку огня

        Btn(int id, int key, int wheel, boolean repeat, int accent) {
            this.id = id;
            this.key = key;
            this.wheel = wheel;
            this.repeat = repeat;
            this.accent = accent;
        }

        boolean pressed() {
            return pointer != -1;
        }

        boolean hit(float x, float y) {
            float dx = x - cx, dy = y - cy;
            float rr = r * 1.15f;
            return dx * dx + dy * dy <= rr * rr;
        }
    }

    private final Btn[] btns = new Btn[B_COUNT];

    // ------------------------------------------------------------------ состояние
    private final float dp;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int mode = MODE_GAME;
    private boolean attached = false;

    private float padL, padR, padT, padB;

    // джойстик
    private int joyPointer = -1;
    private float joyCx, joyCy, joyRestX, joyRestY, joyR;
    private float joyKx, joyKy;
    private boolean jF, jB, jL, jR;
    private boolean jFar;          // стик отклонён до упора
    private boolean jRun;          // для отрисовки: игрок сейчас бежит
    private boolean shiftHeld;     // удерживаем Shift для движка

    // «Always Run» из движка (p_autorun). В движке при включённом autorun Shift ЗАМЕДЛЯЕТ,
    // поэтому Shift нужно посылать только когда autorun выключен и стик отклонён до упора.
    private boolean engineAutorun = true;

    // камера
    private int lookPointer = -1;
    private float lookX, lookY;

    // нажатые клавиши (счётчик, чтобы две кнопки FIRE не мешали друг другу)
    private final int[] keyRef = new int[512];

    // краски
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rectF = new RectF();

    // ------------------------------------------------------------------ init
    public TouchControlsView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        loadPrefs();

        btns[B_FIRE] = new Btn(B_FIRE, KeyEvent.KEYCODE_CTRL_LEFT, 0, false, C_RED);
        btns[B_FIRE2] = new Btn(B_FIRE2, KeyEvent.KEYCODE_CTRL_LEFT, 0, false, C_RED);
        btns[B_USE] = new Btn(B_USE, KeyEvent.KEYCODE_E, 0, false, C_AMBER);
        btns[B_NEXT] = new Btn(B_NEXT, 0, +1, true, C_CYAN);
        btns[B_PREV] = new Btn(B_PREV, 0, -1, true, C_CYAN);
        btns[B_MAP] = new Btn(B_MAP, KeyEvent.KEYCODE_TAB, 0, false, C_WHITE);
        btns[B_PAUSE] = new Btn(B_PAUSE, KeyEvent.KEYCODE_ESCAPE, 0, false, C_WHITE);
        btns[B_UP] = new Btn(B_UP, KeyEvent.KEYCODE_DPAD_UP, 0, true, C_CYAN);
        btns[B_DOWN] = new Btn(B_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0, true, C_CYAN);
        btns[B_LEFT] = new Btn(B_LEFT, KeyEvent.KEYCODE_DPAD_LEFT, 0, true, C_CYAN);
        btns[B_RIGHT] = new Btn(B_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, 0, true, C_CYAN);
        btns[B_OK] = new Btn(B_OK, KeyEvent.KEYCODE_ENTER, 0, false, C_GREEN);
        btns[B_BACK] = new Btn(B_BACK, KeyEvent.KEYCODE_ESCAPE, 0, false, C_AMBER);
        btns[B_SETTINGS] = new Btn(B_SETTINGS, 0, 0, false, C_WHITE);

        stroke.setStyle(Paint.Style.STROKE);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);

        setWillNotDraw(false);
        setFocusable(false);
        updateVisibility();
    }

    private void loadPrefs() {
        lookSens = prefs.getFloat("look", 1.0f);
        opacity = prefs.getFloat("opacity", 0.80f);
        sizeScale = prefs.getFloat("size", 1.0f);
        leftFire = prefs.getBoolean("leftfire", true);
        haptics = prefs.getBoolean("haptics", true);
    }

    // ------------------------------------------------------------------ жизненный цикл
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        computeInsets();
        layoutControls();
        ui.removeCallbacks(pollRunnable);
        ui.post(pollRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        ui.removeCallbacks(pollRunnable);
        ui.removeCallbacks(repeatRunnable);
        releaseAll();
        super.onDetachedFromWindow();
    }

    /** Вызывать из Activity.onPause(). */
    public void onHostPause() {
        ui.removeCallbacks(pollRunnable);
        ui.removeCallbacks(repeatRunnable);
        releaseAll();
    }

    /** Вызывать из Activity.onResume(). */
    public void onHostResume() {
        if (attached) {
            ui.removeCallbacks(pollRunnable);
            ui.post(pollRunnable);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            releaseAll();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeInsets();
        layoutControls();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (Build.VERSION.SDK_INT >= 29) {
            // чтобы системный жест «назад» по краям не мешал игре
            int ex = (int) (28 * dp);
            int eh = (int) (200 * dp);
            int w = r - l, h = b - t;
            int top = Math.max(0, (h - eh) / 2);
            List<Rect> rects = new ArrayList<>();
            rects.add(new Rect(0, top, ex, Math.min(h, top + eh)));
            rects.add(new Rect(Math.max(0, w - ex), top, w, Math.min(h, top + eh)));
            setSystemGestureExclusionRects(rects);
        }
    }

    // ------------------------------------------------------------------ опрос состояния игры
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            int st = MainActivity.queryUiState();
            int newMode = (st == 1) ? MODE_MENU : MODE_GAME;
            if (newMode != mode) {
                setMode(newMode);
            }

            int ar = MainActivity.queryAutorun();
            if (ar >= 0 && (ar == 1) != engineAutorun) {
                engineAutorun = (ar == 1);
                syncRun();
                invalidate();
            }
            ui.postDelayed(this, 100);
        }
    };

    private void setMode(int newMode) {
        releaseAll();
        mode = newMode;
        updateVisibility();
        invalidate();
    }

    private void updateVisibility() {
        boolean game = (mode == MODE_GAME);
        btns[B_FIRE].visible = game;
        btns[B_FIRE2].visible = game && leftFire;
        btns[B_USE].visible = game;
        btns[B_NEXT].visible = game;
        btns[B_PREV].visible = game;
        btns[B_MAP].visible = game;
        btns[B_PAUSE].visible = game;

        btns[B_UP].visible = !game;
        btns[B_DOWN].visible = !game;
        btns[B_LEFT].visible = !game;
        btns[B_RIGHT].visible = !game;
        btns[B_OK].visible = !game;
        btns[B_BACK].visible = !game;
        btns[B_SETTINGS].visible = !game;
    }

    // ------------------------------------------------------------------ раскладка
    private void computeInsets() {
        padL = padR = padT = padB = 0;
        WindowInsets wi = getRootWindowInsets();
        if (wi == null) return;

        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets in = wi.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            padL = in.left;
            padR = in.right;
            padT = in.top;
            padB = in.bottom;
        } else {
            DisplayCutout dc = wi.getDisplayCutout();
            if (dc != null) {
                padL = dc.getSafeInsetLeft();
                padR = dc.getSafeInsetRight();
                padT = dc.getSafeInsetTop();
                padB = dc.getSafeInsetBottom();
            }
        }
    }

    private void layoutControls() {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float u = dp * sizeScale;
        float m = 20 * u;
        float left = padL + m;
        float right = w - padR - m;
        float top = padT + 8 * u;
        float bottom = h - padB - m;

        // --- джойстик (положение «покоя»)
        joyR = 60 * u;
        joyRestX = left + joyR + 10 * u;
        joyRestY = bottom - joyR;
        if (joyPointer == -1) {
            joyCx = joyRestX;
            joyCy = joyRestY;
        }

        // --- игровые кнопки
        Btn fire = btns[B_FIRE];
        fire.r = 46 * u;
        fire.cx = right - fire.r;
        fire.cy = bottom - fire.r;

        Btn use = btns[B_USE];
        use.r = 32 * u;
        use.cx = fire.cx - 82 * u;
        use.cy = fire.cy + 10 * u;

        Btn next = btns[B_NEXT];
        next.r = 26 * u;
        next.cx = fire.cx - 6 * u;
        next.cy = fire.cy - 92 * u;

        Btn prev = btns[B_PREV];
        prev.r = 26 * u;
        prev.cx = fire.cx - 66 * u;
        prev.cy = fire.cy - 74 * u;

        Btn fire2 = btns[B_FIRE2];
        fire2.r = 34 * u;
        fire2.cx = left + fire2.r;
        fire2.cy = Math.max(top + fire2.r, joyRestY - joyR - 64 * u);

        Btn pause = btns[B_PAUSE];
        pause.r = 20 * u;
        pause.cx = w / 2f - 30 * u;
        pause.cy = top + pause.r;

        Btn map = btns[B_MAP];
        map.r = 20 * u;
        map.cx = w / 2f + 30 * u;
        map.cy = top + map.r;

        // --- меню
        float dx = left + 100 * u;
        float dy = bottom - 100 * u;
        float step = 62 * u;
        setCircle(btns[B_UP], dx, dy - step, 30 * u);
        setCircle(btns[B_DOWN], dx, dy + step, 30 * u);
        setCircle(btns[B_LEFT], dx - step, dy, 30 * u);
        setCircle(btns[B_RIGHT], dx + step, dy, 30 * u);

        setCircle(btns[B_OK], right - 40 * u, bottom - 50 * u, 40 * u);
        setCircle(btns[B_BACK], right - 40 * u - 90 * u, bottom - 42 * u, 32 * u);
        setCircle(btns[B_SETTINGS], right - 20 * u, top + 20 * u, 20 * u);

        invalidate();
    }

    private static void setCircle(Btn b, float cx, float cy, float r) {
        b.cx = cx;
        b.cy = cy;
        b.r = r;
    }

    // ------------------------------------------------------------------ отрисовка
    @Override
    protected void onDraw(Canvas c) {
        if (mode == MODE_GAME) {
            drawJoystick(c);
        }
        for (Btn b : btns) {
            if (b.visible) drawButton(c, b);
        }
    }

    private static int withAlpha(int color, float a) {
        int ai = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        return (ai << 24) | (color & 0x00FFFFFF);
    }

    private void drawJoystick(Canvas c) {
        boolean active = joyPointer != -1;
        float a = opacity * (active ? 1f : 0.55f);
        float cx = joyCx, cy = joyCy, r = joyR;

        // основание
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(withAlpha(0xFF101014, a * 0.45f));
        c.drawCircle(cx, cy, r, fill);

        stroke.setStrokeWidth(2.4f * dp);
        stroke.setColor(withAlpha(C_WHITE, a * 0.75f));
        c.drawCircle(cx, cy, r - dp, stroke);

        stroke.setStrokeWidth(1f * dp);
        stroke.setColor(withAlpha(C_WHITE, a * 0.18f));
        c.drawCircle(cx, cy, r * STICK_RUN_THRESHOLD * 0.72f, stroke);

        // стрелки направлений
        drawChevron(c, cx, cy - r * 0.74f, r * 0.13f, 0, jF, a);
        drawChevron(c, cx, cy + r * 0.74f, r * 0.13f, 2, jB, a);
        drawChevron(c, cx - r * 0.74f, cy, r * 0.13f, 3, jL, a);
        drawChevron(c, cx + r * 0.74f, cy, r * 0.13f, 1, jR, a);

        // ручка
        float kx = cx + joyKx, ky = cy + joyKy;
        int accent = jRun ? C_RED : C_WHITE;
        fill.setColor(withAlpha(accent, a * (active ? 0.42f : 0.28f)));
        c.drawCircle(kx, ky, r * 0.42f, fill);
        stroke.setStrokeWidth(2.4f * dp);
        stroke.setColor(withAlpha(accent, a * 0.95f));
        c.drawCircle(kx, ky, r * 0.42f - dp, stroke);
        fill.setColor(withAlpha(C_WHITE, a * 0.55f));
        c.drawCircle(kx, ky, r * 0.10f, fill);
    }

    /** dir: 0 — вверх, 1 — вправо, 2 — вниз, 3 — влево. */
    private void drawChevron(Canvas c, float x, float y, float s, int dir, boolean on, float a) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(withAlpha(on ? C_CYAN : C_WHITE, a * (on ? 1f : 0.45f)));
        drawTriangle(c, x, y, s, dir, fill);
    }

    private void drawTriangle(Canvas c, float x, float y, float s, int dir, Paint p) {
        path.reset();
        switch (dir) {
            case 0: // вверх
                path.moveTo(x, y - s);
                path.lineTo(x + s * 1.1f, y + s * 0.8f);
                path.lineTo(x - s * 1.1f, y + s * 0.8f);
                break;
            case 1: // вправо
                path.moveTo(x + s, y);
                path.lineTo(x - s * 0.8f, y + s * 1.1f);
                path.lineTo(x - s * 0.8f, y - s * 1.1f);
                break;
            case 2: // вниз
                path.moveTo(x, y + s);
                path.lineTo(x + s * 1.1f, y - s * 0.8f);
                path.lineTo(x - s * 1.1f, y - s * 0.8f);
                break;
            default: // влево
                path.moveTo(x - s, y);
                path.lineTo(x + s * 0.8f, y + s * 1.1f);
                path.lineTo(x + s * 0.8f, y - s * 1.1f);
                break;
        }
        path.close();
        c.drawPath(path, p);
    }

    private void drawButton(Canvas c, Btn b) {
        boolean pr = b.pressed();
        float a = opacity;
        float r = b.r * (pr ? 0.93f : 1f);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(withAlpha(0xFF101014, a * (pr ? 0.62f : 0.40f)));
        c.drawCircle(b.cx, b.cy, r, fill);

        if (pr) {
            fill.setColor(withAlpha(b.accent, a * 0.42f));
            c.drawCircle(b.cx, b.cy, r, fill);
        }

        stroke.setStrokeWidth(2.4f * dp);
        stroke.setColor(withAlpha(b.accent, a * (pr ? 1f : 0.85f)));
        c.drawCircle(b.cx, b.cy, r - dp, stroke);

        stroke.setStrokeWidth(1f * dp);
        stroke.setColor(withAlpha(C_WHITE, a * 0.14f));
        c.drawCircle(b.cx, b.cy, r * 0.84f, stroke);

        drawIcon(c, b, r, a);
    }

    private void drawIcon(Canvas c, Btn b, float r, float a) {
        float x = b.cx, y = b.cy;
        int col = withAlpha(C_WHITE, a * 0.96f);
        icon.setColor(col);
        icon.setStrokeWidth(Math.max(2f * dp, r * 0.085f));
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(col);

        switch (b.id) {
            case B_FIRE:
            case B_FIRE2: {
                icon.setColor(withAlpha(b.pressed() ? C_WHITE : 0xFFFFD2CC, a));
                c.drawCircle(x, y, r * 0.40f, icon);
                float in = r * 0.40f, out = r * 0.68f;
                c.drawLine(x, y - in, x, y - out, icon);
                c.drawLine(x, y + in, x, y + out, icon);
                c.drawLine(x - in, y, x - out, y, icon);
                c.drawLine(x + in, y, x + out, y, icon);
                fill.setColor(withAlpha(C_RED, a));
                c.drawCircle(x, y, r * 0.12f, fill);
                break;
            }
            case B_USE:
                drawLabel(c, "USE", x, y, r * 0.42f, col);
                break;
            case B_NEXT:
                drawChevronPair(c, x, y, r * 0.30f, +1);
                break;
            case B_PREV:
                drawChevronPair(c, x, y, r * 0.30f, -1);
                break;
            case B_MAP: {
                float k = r * 0.62f;
                path.reset();
                path.moveTo(x - k, y - k * 0.55f);
                path.lineTo(x - k * 0.34f, y - k * 0.85f);
                path.lineTo(x + k * 0.34f, y - k * 0.55f);
                path.lineTo(x + k, y - k * 0.85f);
                path.lineTo(x + k, y + k * 0.55f);
                path.lineTo(x + k * 0.34f, y + k * 0.85f);
                path.lineTo(x - k * 0.34f, y + k * 0.55f);
                path.lineTo(x - k, y + k * 0.85f);
                path.close();
                c.drawPath(path, icon);
                c.drawLine(x - k * 0.34f, y - k * 0.85f, x - k * 0.34f, y + k * 0.55f, icon);
                c.drawLine(x + k * 0.34f, y - k * 0.55f, x + k * 0.34f, y + k * 0.85f, icon);
                break;
            }
            case B_PAUSE: {
                float bw = r * 0.16f, bh = r * 0.52f, gap = r * 0.18f;
                rectF.set(x - gap - bw, y - bh, x - gap, y + bh);
                c.drawRoundRect(rectF, bw * 0.4f, bw * 0.4f, fill);
                rectF.set(x + gap, y - bh, x + gap + bw, y + bh);
                c.drawRoundRect(rectF, bw * 0.4f, bw * 0.4f, fill);
                break;
            }
            case B_UP:
                fill.setColor(withAlpha(C_CYAN, a));
                drawTriangle(c, x, y, r * 0.34f, 0, fill);
                break;
            case B_RIGHT:
                fill.setColor(withAlpha(C_CYAN, a));
                drawTriangle(c, x, y, r * 0.34f, 1, fill);
                break;
            case B_DOWN:
                fill.setColor(withAlpha(C_CYAN, a));
                drawTriangle(c, x, y, r * 0.34f, 2, fill);
                break;
            case B_LEFT:
                fill.setColor(withAlpha(C_CYAN, a));
                drawTriangle(c, x, y, r * 0.34f, 3, fill);
                break;
            case B_OK:
                drawLabel(c, "OK", x, y, r * 0.52f, col);
                break;
            case B_BACK:
                drawLabel(c, "BACK", x, y, r * 0.36f, col);
                break;
            case B_SETTINGS: {
                icon.setStrokeWidth(Math.max(2f * dp, r * 0.11f));
                c.drawCircle(x, y, r * 0.26f, icon);
                icon.setStrokeWidth(Math.max(2.5f * dp, r * 0.20f));
                for (int i = 0; i < 8; i++) {
                    double ang = Math.PI * 2.0 * i / 8.0;
                    float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                    c.drawLine(x + cs * r * 0.46f, y + sn * r * 0.46f,
                            x + cs * r * 0.62f, y + sn * r * 0.62f, icon);
                }
                break;
            }
            default:
                break;
        }
    }

    private void drawChevronPair(Canvas c, float x, float y, float s, int dir) {
        icon.setColor(withAlpha(C_CYAN, opacity));
        float off = s * 0.55f;
        for (int i = -1; i <= 1; i += 2) {
            float ox = x + i * off * 0.9f * dir;
            path.reset();
            path.moveTo(ox - dir * s * 0.55f, y - s);
            path.lineTo(ox + dir * s * 0.55f, y);
            path.lineTo(ox - dir * s * 0.55f, y + s);
            c.drawPath(path, icon);
        }
    }

    private void drawLabel(Canvas c, String s, float x, float y, float size, int color) {
        text.setColor(color);
        text.setTextSize(size);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, text);
    }

    // ------------------------------------------------------------------ касания
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                boolean handled = handleDown(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                if (action == MotionEvent.ACTION_DOWN && !handled) {
                    return false; // в меню: отдаём касание SDL (эмуляция мыши)
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    handleMove(e.getPointerId(i), e.getX(i), e.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                handleUp(e.getPointerId(idx));
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                invalidate();
                break;
            default:
                break;
        }
        return true;
    }

    private boolean handleDown(int id, float x, float y) {
        Btn b = hitButton(x, y);
        if (b != null) {
            pressButton(b, id);
            b.lastX = x;
            b.lastY = y;
            invalidate();
            return true;
        }
        if (mode != MODE_GAME) {
            return false;
        }

        float zone = getWidth() * LOOK_ZONE_X;
        if (x < zone) {
            if (joyPointer == -1) {
                startJoystick(id, x, y);
            }
        } else if (lookPointer == -1) {
            lookPointer = id;
            lookX = x;
            lookY = y;
        }
        invalidate();
        return true;
    }

    private void handleMove(int id, float x, float y) {
        // зажатая кнопка огня: этим же пальцем можно водить и поворачивать камеру (прицеливание)
        Btn fb = fireButtonForPointer(id);
        if (fb != null) {
            float fdx = (x - fb.lastX) * LOOK_BASE * lookSens;
            float fdy = (y - fb.lastY) * LOOK_BASE * lookSens;
            fb.lastX = x;
            fb.lastY = y;
            if (fdx != 0f || fdy != 0f) {
                SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, fdx, fdy, true);
            }
            return;
        }

        if (id == joyPointer) {
            updateJoystick(x, y);
            invalidate();
        } else if (id == lookPointer) {
            float dx = (x - lookX) * LOOK_BASE * lookSens;
            float dy = (y - lookY) * LOOK_BASE * lookSens;
            lookX = x;
            lookY = y;
            if (dx != 0f || dy != 0f) {
                SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, dx, dy, true);
            }
        }
    }

    private Btn fireButtonForPointer(int id) {
        Btn a = btns[B_FIRE];
        if (a.pointer == id) return a;
        Btn b = btns[B_FIRE2];
        if (b.pointer == id) return b;
        return null;
    }

    private void handleUp(int id) {
        if (id == joyPointer) {
            releaseJoystick();
        } else if (id == lookPointer) {
            lookPointer = -1;
        } else {
            for (Btn b : btns) {
                if (b.pointer == id) {
                    releaseButton(b);
                    break;
                }
            }
        }
        invalidate();
    }

    private Btn hitButton(float x, float y) {
        Btn best = null;
        float bestD = Float.MAX_VALUE;
        for (Btn b : btns) {
            if (!b.visible || b.pressed() || !b.hit(x, y)) continue;
            float dx = x - b.cx, dy = y - b.cy;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ кнопки
    private void pressButton(Btn b, int pointerId) {
        b.pointer = pointerId;
        haptic();

        if (b.id == B_SETTINGS) {
            post(new Runnable() {
                @Override
                public void run() {
                    showSettings();
                }
            });
            return;
        }

        trigger(b, true);

        if (b.repeat) {
            b.nextRepeat = System.currentTimeMillis() + 350;
            ui.removeCallbacks(repeatRunnable);
            ui.postDelayed(repeatRunnable, 60);
        }
    }

    private void releaseButton(Btn b) {
        b.pointer = -1;
        if (b.key != 0 && b.wheel == 0) {
            keyRelease(b.key);
        }
    }

    /** first == true — первое нажатие, иначе автоповтор. */
    private void trigger(Btn b, boolean first) {
        if (b.wheel != 0) {
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_SCROLL, 0f, (float) b.wheel, false);
        } else if (b.key != 0) {
            if (first) {
                keyPress(b.key);
            } else {
                // движок игнорирует key.repeat, поэтому повторяем «отпустил-нажал»
                SDLActivity.onNativeKeyUp(b.key);
                SDLActivity.onNativeKeyDown(b.key);
            }
        }
    }

    private final Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            boolean any = false;
            for (Btn b : btns) {
                if (b.repeat && b.pressed()) {
                    any = true;
                    if (now >= b.nextRepeat) {
                        trigger(b, false);
                        b.nextRepeat = now + 110;
                    }
                }
            }
            if (any) ui.postDelayed(this, 40);
        }
    };

    // ------------------------------------------------------------------ джойстик
    private void startJoystick(int id, float x, float y) {
        joyPointer = id;
        float minX = padL + joyR;
        float maxX = Math.max(minX, getWidth() * LOOK_ZONE_X - joyR * 0.3f);
        float minY = padT + joyR;
        float maxY = Math.max(minY, getHeight() - padB - joyR * 0.6f);
        joyCx = Math.max(minX, Math.min(maxX, x));
        joyCy = Math.max(minY, Math.min(maxY, y));
        joyKx = joyKy = 0f;
        haptic();
        updateJoystick(x, y);
    }

    private void updateJoystick(float x, float y) {
        float dx = x - joyCx, dy = y - joyCy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > joyR) {
            dx = dx / d * joyR;
            dy = dy / d * joyR;
            d = joyR;
        }
        joyKx = dx;
        joyKy = dy;

        float m = d / joyR;
        float nx = dx / joyR, ny = dy / joyR;

        boolean f = false, bk = false, l = false, rt = false;
        if (m > STICK_DEADZONE) {
            f = ny < -STICK_DIR_THRESHOLD;
            bk = ny > STICK_DIR_THRESHOLD;
            l = nx < -STICK_DIR_THRESHOLD;
            rt = nx > STICK_DIR_THRESHOLD;
        }
        applyJoystick(f, bk, l, rt, m > STICK_RUN_THRESHOLD);
    }

    private void releaseJoystick() {
        applyJoystick(false, false, false, false, false);
        joyPointer = -1;
        joyKx = joyKy = 0f;
        joyCx = joyRestX;
        joyCy = joyRestY;
    }

    private void applyJoystick(boolean f, boolean b, boolean l, boolean r, boolean far) {
        if (f != jF) { jF = f; setKey(KeyEvent.KEYCODE_W, f); }
        if (b != jB) { jB = b; setKey(KeyEvent.KEYCODE_S, b); }
        if (l != jL) { jL = l; setKey(KeyEvent.KEYCODE_A, l); }
        if (r != jR) { jR = r; setKey(KeyEvent.KEYCODE_D, r); }
        jFar = far;
        syncRun();
    }

    /**
     * Бег. В движке скорость = (Shift нажат) XOR (Always Run), поэтому:
     *  - Always Run включён  -> бежим всегда, Shift НЕ нажимаем (иначе игрок пойдёт шагом);
     *  - Always Run выключен -> идём шагом, а при отклонении стика до упора зажимаем Shift.
     */
    private void syncRun() {
        boolean moving = jF || jB || jL || jR;
        jRun = moving && (engineAutorun || jFar);

        boolean wantShift = moving && !engineAutorun && jFar;
        if (wantShift != shiftHeld) {
            shiftHeld = wantShift;
            setKey(KeyEvent.KEYCODE_SHIFT_LEFT, wantShift);
        }
    }

    // ------------------------------------------------------------------ клавиши
    private void setKey(int key, boolean down) {
        if (down) keyPress(key);
        else keyRelease(key);
    }

    private void keyPress(int key) {
        if (key <= 0 || key >= keyRef.length) return;
        if (keyRef[key]++ == 0) {
            SDLActivity.onNativeKeyDown(key);
        }
    }

    private void keyRelease(int key) {
        if (key <= 0 || key >= keyRef.length) return;
        if (keyRef[key] > 0 && --keyRef[key] == 0) {
            SDLActivity.onNativeKeyUp(key);
        }
    }

    /** Отпускает всё: кнопки, джойстик, камеру, залипшие клавиши. */
    public void releaseAll() {
        for (Btn b : btns) {
            b.pointer = -1;
        }
        joyPointer = -1;
        lookPointer = -1;
        joyKx = joyKy = 0f;
        joyCx = joyRestX;
        joyCy = joyRestY;
        jF = jB = jL = jR = jRun = jFar = false;
        shiftHeld = false;

        for (int k = 0; k < keyRef.length; k++) {
            if (keyRef[k] > 0) {
                keyRef[k] = 0;
                SDLActivity.onNativeKeyUp(k);
            }
        }
        invalidate();
    }

    private void haptic() {
        if (haptics) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    // ------------------------------------------------------------------ настройки
    private interface IntCb {
        void on(int value);
    }

    private interface BoolCb {
        void on(boolean value);
    }

    private int px(float v) {
        return (int) (v * dp + 0.5f);
    }

    private void addSlider(LinearLayout root, final String title, final int min, int max,
                           int current, final IntCb cb) {
        final TextView label = new TextView(getContext());
        label.setTextColor(Color.WHITE);
        label.setText(title + ": " + current + "%");
        label.setPadding(0, px(10), 0, px(2));
        root.addView(label);

        SeekBar bar = new SeekBar(getContext());
        bar.setMax(max - min);
        bar.setProgress(current - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int v = min + progress;
                label.setText(title + ": " + v + "%");
                cb.on(v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(bar);
    }

    private void addCheck(LinearLayout root, String title, boolean checked, final BoolCb cb) {
        CheckBox box = new CheckBox(getContext());
        box.setTextColor(Color.WHITE);
        box.setText(title);
        box.setChecked(checked);
        box.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton v, boolean isChecked) {
                cb.on(isChecked);
            }
        });
        root.addView(box);
    }

    private void showSettings() {
        Context ctx = getContext();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(20), px(8), px(20), px(8));

        addSlider(root, "Чувствительность камеры", 20, 300, Math.round(lookSens * 100f), new IntCb() {
            @Override
            public void on(int v) {
                lookSens = v / 100f;
                prefs.edit().putFloat("look", lookSens).apply();
            }
        });

        addSlider(root, "Прозрачность кнопок", 20, 100, Math.round(opacity * 100f), new IntCb() {
            @Override
            public void on(int v) {
                opacity = v / 100f;
                prefs.edit().putFloat("opacity", opacity).apply();
                invalidate();
            }
        });

        addSlider(root, "Размер кнопок", 70, 140, Math.round(sizeScale * 100f), new IntCb() {
            @Override
            public void on(int v) {
                sizeScale = v / 100f;
                prefs.edit().putFloat("size", sizeScale).apply();
                layoutControls();
            }
        });

        int arNow = MainActivity.queryAutorun();
        if (arNow >= 0) {
            engineAutorun = (arNow == 1);
        }
        addCheck(root, "Всегда бегать (Always Run)", engineAutorun, new BoolCb() {
            @Override
            public void on(boolean v) {
                engineAutorun = v;
                MainActivity.setAutorun(v);
                syncRun();
                invalidate();
            }
        });

        addCheck(root, "Вторая кнопка огня слева", leftFire, new BoolCb() {
            @Override
            public void on(boolean v) {
                leftFire = v;
                prefs.edit().putBoolean("leftfire", v).apply();
                updateVisibility();
                invalidate();
            }
        });

        addCheck(root, "Вибрация при нажатии", haptics, new BoolCb() {
            @Override
            public void on(boolean v) {
                haptics = v;
                prefs.edit().putBoolean("haptics", v).apply();
            }
        });

        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(root);

        new AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Тач-управление")
                .setView(scroll)
                .setPositiveButton("OK", null)
                .show();
    }
}
