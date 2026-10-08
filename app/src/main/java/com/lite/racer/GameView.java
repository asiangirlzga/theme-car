package com.lite.racer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/**
 * Kid-friendly racing game. Everything is drawn with code (no image files),
 * so storage and RAM use stay tiny.
 */
public class GameView extends View {
    private static final int READY = 0, PLAY = 1, PAUSE = 2, OVER = 3;
    private static final int MAXC = 5;      // max cars
    private static final int MAXS = 3;      // max stars (coins)
    private static final int NSCN = 10;     // scenery items

    private static final String[] NAMES = {"MEADOW", "DESERT", "SNOW", "SPACE"};
    private static final int[] GROUND = {0xFF7CB342, 0xFFFFCC80, 0xFFCFE8F7, 0xFF1A237E};
    private static final int[] ROAD   = {0xFF546E7A, 0xFF8D6E63, 0xFF607D8B, 0xFF311B92};
    private static final int[] LINE   = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF80DEEA};
    private static final int[] CAR_COLORS =
            {0xFFFDD835, 0xFF43A047, 0xFF8E24AA, 0xFFFB8C00, 0xFF00ACC1, 0xFFEC407A};

    private final Paint p = new Paint();
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final Random rnd = new Random();
    private final SharedPreferences prefs;
    private ToneGenerator tg;
    private Canvas canvas;

    // cars
    private final boolean[] ea = new boolean[MAXC];
    private final int[] el = new int[MAXC];
    private final int[] ec = new int[MAXC];
    private final float[] ey = new float[MAXC];
    // stars
    private final boolean[] sa = new boolean[MAXS];
    private final int[] sl = new int[MAXS];
    private final float[] sy = new float[MAXS];
    // scenery
    private final float[] nx = new float[NSCN];
    private final float[] ny = new float[NSCN];
    private final float[] nz = new float[NSCN];
    private final int[] nt = new int[NSCN];
    private final int[] nv = new int[NSCN];

    private int state = READY, lane = 1, best, theme, lives, stars;
    private boolean accel, brake, newBest;
    private float w, h, roadX, roadW, laneW, carW, carH, playerY;
    private float px, dist, bonus, score, speed, markOff, spawnGap, inv;
    private float minSpd, cruise, maxSpd;
    private long last;

    public GameView(Context c) {
        super(c);
        setFocusable(true);
        prefs = c.getSharedPreferences("lr", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        theme = prefs.getInt("theme", 0);
        try { tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 50); } catch (Exception e) { tg = null; }
    }

    public void releaseAudio() {
        if (tg != null) { tg.release(); tg = null; }
    }

    private void beep(int tone, int ms) {
        if (tg != null) { try { tg.startTone(tone, ms); } catch (Exception ignored) { } }
    }

    public void resumeClock() { last = 0; invalidate(); }

    public void pauseGame() { if (state == PLAY) state = PAUSE; accel = false; brake = false; }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        w = nw; h = nh;
        roadW = Math.min(w * 0.5f, h * 0.95f);
        roadX = (w - roadW) / 2f;
        laneW = roadW / 3f;
        carW = laneW * 0.55f;
        carH = carW * 1.8f;
        playerY = h - carH * 0.9f;
        minSpd = h * 0.30f; cruise = h * 0.50f; maxSpd = h * 1.30f;
        speed = cruise;
        px = laneX(lane);
        for (int i = 0; i < NSCN; i++) { respawn(i); ny[i] = rnd.nextFloat() * h; }
    }

    private float laneX(int l) { return roadX + laneW * (l + 0.5f); }

    private void respawn(int i) {
        ny[i] = -h * 0.1f;
        float side = roadX;
        nx[i] = (i % 2 == 0) ? side * (0.18f + 0.64f * rnd.nextFloat())
                             : roadX + roadW + side * (0.18f + 0.64f * rnd.nextFloat());
        nz[i] = 0.8f + rnd.nextFloat() * 0.5f;
        nt[i] = rnd.nextInt(2);
        nv[i] = rnd.nextInt(3);
    }

    private void start() {
        state = PLAY; lane = 1; px = laneX(1);
        dist = 0; bonus = 0; score = 0; stars = 0; lives = 3; inv = 0;
        speed = cruise; spawnGap = carH * 2.5f; newBest = false;
        accel = false; brake = false;
        for (int i = 0; i < MAXC; i++) ea[i] = false;
        for (int i = 0; i < MAXS; i++) sa[i] = false;
    }

    private void changeTheme(int d) {
        theme = (theme + d + NAMES.length) % NAMES.length;
        prefs.edit().putInt("theme", theme).apply();
    }

    /** Called from the Activity for key down AND key up. */
    public boolean handleKey(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean first = down && e.getRepeatCount() == 0;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L1:
                if (first) {
                    if (state == PLAY) { if (lane > 0) lane--; }
                    else if (state == READY || state == OVER) changeTheme(-1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R1:
                if (first) {
                    if (state == PLAY) { if (lane < 2) lane++; }
                    else if (state == READY || state == OVER) changeTheme(1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_BUTTON_R2:
                accel = down;
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_BUTTON_L2:
                brake = down;
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_SPACE:
                if (first) {
                    if (state == PLAY) state = PAUSE;
                    else if (state == PAUSE) { state = PLAY; last = 0; }
                    else start();
                }
                return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            if (state == PLAY) {
                if (e.getX() < w / 2f) { if (lane > 0) lane--; }
                else { if (lane < 2) lane++; }
            } else if (state == PAUSE) { state = PLAY; last = 0; }
            else start();
        }
        return true;
    }

    // ------------------------------------------------------------ update
    private void update(float dt) {
        float scroll;
        if (state == PLAY) {
            float target = accel ? maxSpd : (brake ? minSpd : cruise);
            float rate = (accel || brake) ? h * 0.8f : h * 0.35f;
            float d = target - speed, m = rate * dt;
            speed += d > m ? m : (d < -m ? -m : d);
            scroll = speed;
        } else if (state == PAUSE) scroll = 0;
        else scroll = h * 0.2f;

        markOff = (markOff + scroll * dt) % (h / 5f);
        for (int i = 0; i < NSCN; i++) {
            ny[i] += scroll * dt;
            if (ny[i] > h * 1.12f) respawn(i);
        }
        if (state != PLAY) return;

        if (inv > 0) inv -= dt;
        dist += speed * dt;
        score = dist / h * 10f + bonus;
        px += (laneX(lane) - px) * Math.min(1f, dt * 14f);

        float rel = speed * 0.7f;
        spawnGap -= rel * dt;
        if (spawnGap <= 0) {
            int carLane = rnd.nextInt(3);
            for (int i = 0; i < MAXC; i++) {
                if (!ea[i]) {
                    ea[i] = true; el[i] = carLane;
                    ec[i] = CAR_COLORS[rnd.nextInt(CAR_COLORS.length)];
                    ey[i] = -carH;
                    break;
                }
            }
            if (rnd.nextInt(100) < 55) {
                for (int i = 0; i < MAXS; i++) {
                    if (!sa[i]) {
                        sa[i] = true;
                        sl[i] = (carLane + 1 + rnd.nextInt(2)) % 3;
                        sy[i] = -carH * 2f;
                        break;
                    }
                }
            }
            spawnGap = carH * (3.0f + rnd.nextFloat() * 2f);
        }

        for (int i = 0; i < MAXS; i++) {
            if (!sa[i]) continue;
            sy[i] += rel * dt;
            if (sy[i] > h + carH) { sa[i] = false; continue; }
            if (Math.abs(laneX(sl[i]) - px) < carW * 0.8f && Math.abs(sy[i] - playerY) < carH * 0.8f) {
                sa[i] = false; stars++; bonus += 10;
                beep(ToneGenerator.TONE_PROP_BEEP, 80);
            }
        }

        for (int i = 0; i < MAXC; i++) {
            if (!ea[i]) continue;
            ey[i] += rel * dt;
            if (ey[i] > h + carH) { ea[i] = false; continue; }
            if (inv <= 0 && Math.abs(laneX(el[i]) - px) < carW * 0.9f
                    && Math.abs(ey[i] - playerY) < carH * 0.9f) {
                ea[i] = false;
                lives--;
                inv = 2f;
                speed = minSpd;
                beep(ToneGenerator.TONE_PROP_NACK, 200);
                if (lives <= 0) endGame();
            }
        }
    }

    private void endGame() {
        state = OVER;
        accel = false; brake = false;
        int s = (int) score;
        if (s > best) { best = s; newBest = true; prefs.edit().putInt("best", best).apply(); }
    }

    // ------------------------------------------------------------ drawing helpers
    private void star(float cx, float cy, float R, int color) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            float a = (float) (-Math.PI / 2 + i * Math.PI / 5);
            float rr = (i & 1) == 0 ? R : R * 0.45f;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        p.setColor(color);
        canvas.drawPath(path, p);
    }

    private void heart(float cx, float cy, float s, int color) {
        p.setColor(color);
        canvas.drawCircle(cx - s * 0.5f, cy - s * 0.3f, s * 0.55f, p);
        canvas.drawCircle(cx + s * 0.5f, cy - s * 0.3f, s * 0.55f, p);
        path.reset();
        path.moveTo(cx - s * 1.02f, cy - s * 0.05f);
        path.lineTo(cx + s * 1.02f, cy - s * 0.05f);
        path.lineTo(cx, cy + s * 1.0f);
        path.close();
        canvas.drawPath(path, p);
    }

    private void tri(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        path.reset();
        path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close();
        p.setColor(color);
        canvas.drawPath(path, p);
    }

    private void panel(float l, float t, float rr, float b) {
        p.setColor(0xB0000000);
        r.set(l, t, rr, b);
        canvas.drawRoundRect(r, h * 0.04f, h * 0.04f, p);
    }

    private void text(String s, float x, float y, float size, int color) {
        p.setColor(color);
        p.setTextSize(size);
        canvas.drawText(s, x, y, p);
    }

    private void car(float cx, float cy, int color) {
        float hw = carW / 2f, hh = carH / 2f, t = carW * 0.14f;
        p.setColor(0xFF111111);
        r.set(cx - hw - t * 0.5f, cy - hh * 0.8f, cx - hw + t, cy - hh * 0.3f); canvas.drawRect(r, p);
        r.set(cx + hw - t, cy - hh * 0.8f, cx + hw + t * 0.5f, cy - hh * 0.3f); canvas.drawRect(r, p);
        r.set(cx - hw - t * 0.5f, cy + hh * 0.3f, cx - hw + t, cy + hh * 0.8f); canvas.drawRect(r, p);
        r.set(cx + hw - t, cy + hh * 0.3f, cx + hw + t * 0.5f, cy + hh * 0.8f); canvas.drawRect(r, p);
        p.setColor(color);
        r.set(cx - hw, cy - hh, cx + hw, cy + hh);
        canvas.drawRoundRect(r, t * 1.5f, t * 1.5f, p);
        // windshield
        p.setColor(0xFFBBDEFB);
        r.set(cx - hw * 0.75f, cy - hh * 0.6f, cx + hw * 0.75f, cy - hh * 0.05f);
        canvas.drawRoundRect(r, t, t, p);
        // cartoon eyes
        float er = carW * 0.11f;
        p.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx - carW * 0.2f, cy - hh * 0.33f, er, p);
        canvas.drawCircle(cx + carW * 0.2f, cy - hh * 0.33f, er, p);
        p.setColor(0xFF000000);
        canvas.drawCircle(cx - carW * 0.2f, cy - hh * 0.30f, er * 0.5f, p);
        canvas.drawCircle(cx + carW * 0.2f, cy - hh * 0.30f, er * 0.5f, p);
        // smile
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, carW * 0.05f));
        p.setColor(0xFF000000);
        r.set(cx - carW * 0.22f, cy + hh * 0.05f, cx + carW * 0.22f, cy + hh * 0.5f);
        canvas.drawArc(r, 20, 140, false, p);
        p.setStyle(Paint.Style.FILL);
        // headlights
        p.setColor(0xFFFFF59D);
        canvas.drawCircle(cx - hw * 0.6f, cy - hh * 0.9f, carW * 0.07f, p);
        canvas.drawCircle(cx + hw * 0.6f, cy - hh * 0.9f, carW * 0.07f, p);
    }

    private void sky() {
        float cx = w * 0.92f, cy = h * 0.14f, rad = h * 0.06f;
        if (theme == 3) {                       // moon
            p.setColor(0xFFECEFF1);
            canvas.drawCircle(cx, cy, rad, p);
            p.setColor(0xFFCFD8DC);
            canvas.drawCircle(cx - rad * 0.3f, cy - rad * 0.2f, rad * 0.22f, p);
            canvas.drawCircle(cx + rad * 0.3f, cy + rad * 0.3f, rad * 0.15f, p);
        } else {                                // sun
            p.setColor(0xFFFFEB3B);
            p.setStrokeWidth(h * 0.008f);
            for (int i = 0; i < 8; i++) {
                float a = (float) (i * Math.PI / 4);
                canvas.drawLine(cx + (float) Math.cos(a) * rad * 1.2f, cy + (float) Math.sin(a) * rad * 1.2f,
                        cx + (float) Math.cos(a) * rad * 1.7f, cy + (float) Math.sin(a) * rad * 1.7f, p);
            }
            canvas.drawCircle(cx, cy, rad, p);
            p.setColor(0xFF000000);
            canvas.drawCircle(cx - rad * 0.3f, cy - rad * 0.15f, rad * 0.09f, p);
            canvas.drawCircle(cx + rad * 0.3f, cy - rad * 0.15f, rad * 0.09f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(h * 0.006f);
            r.set(cx - rad * 0.45f, cy - rad * 0.1f, cx + rad * 0.45f, cy + rad * 0.5f);
            canvas.drawArc(r, 20, 140, false, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void scenery(int i) {
        float x = nx[i], y = ny[i], u = h * 0.055f * nz[i];
        int type = nt[i], v = nv[i];
        switch (theme) {
            case 0: // MEADOW
                if (type == 0) {
                    p.setColor(0xFF6D4C41);
                    r.set(x - u * .15f, y, x + u * .15f, y + u * .9f); canvas.drawRect(r, p);
                    p.setColor(0xFF2E7D32); canvas.drawCircle(x, y - u * .2f, u, p);
                    p.setColor(0xFF43A047); canvas.drawCircle(x - u * .25f, y - u * .45f, u * .5f, p);
                    if (v == 0) { p.setColor(0xFFE53935); canvas.drawCircle(x + u * .4f, y, u * .12f, p); }
                } else {
                    p.setColor(v == 0 ? 0xFFF48FB1 : (v == 1 ? 0xFFFFFFFF : 0xFFCE93D8));
                    canvas.drawCircle(x - u * .3f, y, u * .28f, p);
                    canvas.drawCircle(x + u * .3f, y, u * .28f, p);
                    canvas.drawCircle(x, y - u * .3f, u * .28f, p);
                    canvas.drawCircle(x, y + u * .3f, u * .28f, p);
                    p.setColor(0xFFFFEB3B); canvas.drawCircle(x, y, u * .2f, p);
                }
                break;
            case 1: // DESERT
                if (type == 0) {
                    p.setColor(0xFF2E7D32);
                    r.set(x - u * .2f, y - u, x + u * .2f, y + u * .8f); canvas.drawRoundRect(r, u * .2f, u * .2f, p);
                    r.set(x - u * .65f, y - u * .35f, x - u * .1f, y - u * .1f); canvas.drawRoundRect(r, u * .12f, u * .12f, p);
                    r.set(x - u * .65f, y - u * .8f, x - u * .42f, y - u * .2f); canvas.drawRoundRect(r, u * .12f, u * .12f, p);
                    r.set(x + u * .1f, y - u * .1f, x + u * .65f, y + u * .15f); canvas.drawRoundRect(r, u * .12f, u * .12f, p);
                    r.set(x + u * .42f, y - u * .6f, x + u * .65f, y); canvas.drawRoundRect(r, u * .12f, u * .12f, p);
                    if (v == 0) { p.setColor(0xFFF06292); canvas.drawCircle(x, y - u, u * .15f, p); }
                } else {
                    p.setColor(0xFF8D6E63);
                    r.set(x - u * .6f, y - u * .3f, x + u * .6f, y + u * .4f); canvas.drawOval(r, p);
                    p.setColor(0xFFA1887F);
                    r.set(x - u * .35f, y - u * .35f, x + u * .2f, y + u * .05f); canvas.drawOval(r, p);
                }
                break;
            case 2: // SNOW
                if (type == 0) {
                    p.setColor(0xFF5D4037);
                    r.set(x - u * .12f, y + u * .4f, x + u * .12f, y + u * .8f); canvas.drawRect(r, p);
                    tri(x, y - u * 1.2f, x - u * .85f, y + u * .5f, x + u * .85f, y + u * .5f, 0xFF1B5E20);
                    tri(x, y - u * 1.2f, x - u * .4f, y - u * .5f, x + u * .4f, y - u * .5f, 0xFFFFFFFF);
                } else {
                    p.setColor(0xFF90A4AE);
                    canvas.drawCircle(x, y + u * .3f, u * .54f, p);
                    canvas.drawCircle(x, y - u * .4f, u * .39f, p);
                    p.setColor(0xFFFFFFFF);
                    canvas.drawCircle(x, y + u * .3f, u * .5f, p);
                    canvas.drawCircle(x, y - u * .4f, u * .35f, p);
                    p.setColor(0xFF000000);
                    canvas.drawCircle(x - u * .12f, y - u * .48f, u * .05f, p);
                    canvas.drawCircle(x + u * .12f, y - u * .48f, u * .05f, p);
                    tri(x, y - u * .4f, x + u * .35f, y - u * .35f, x, y - u * .3f, 0xFFFF7043);
                    p.setColor(v == 0 ? 0xFFE53935 : 0xFF1E88E5);
                    r.set(x - u * .3f, y - u * .12f, x + u * .3f, y); canvas.drawRect(r, p);
                }
                break;
            default: // SPACE
                if (type == 0) {
                    p.setColor(v == 0 ? 0xFFFF7043 : (v == 1 ? 0xFFAB47BC : 0xFF26C6DA));
                    canvas.drawCircle(x, y, u * .8f, p);
                    p.setColor(0x33FFFFFF);
                    canvas.drawCircle(x - u * .25f, y - u * .25f, u * .35f, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(u * .12f);
                    p.setColor(0xFFFFF59D);
                    r.set(x - u * 1.2f, y - u * .3f, x + u * 1.2f, y + u * .3f);
                    canvas.drawOval(r, p);
                    p.setStyle(Paint.Style.FILL);
                } else {
                    star(x, y, u * .5f, v == 0 ? 0xFFFFF176 : 0xFFFFFFFF);
                }
        }
    }

    // ------------------------------------------------------------ draw
    @Override
    protected void onDraw(Canvas c) {
        canvas = c;
        long now = System.nanoTime();
        float dt = last == 0 ? 0f : Math.min((now - last) / 1e9f, 0.05f);
        last = now;
        update(dt);

        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawColor(GROUND[theme]);
        sky();
        for (int i = 0; i < NSCN; i++) scenery(i);

        // road
        p.setColor(ROAD[theme]);
        c.drawRect(roadX, 0, roadX + roadW, h, p);
        p.setColor(LINE[theme]);
        c.drawRect(roadX, 0, roadX + w * 0.006f, h, p);
        c.drawRect(roadX + roadW - w * 0.006f, 0, roadX + roadW, h, p);
        float seg = h / 5f, dw = w * 0.005f;
        for (int l = 1; l < 3; l++) {
            float x = roadX + laneW * l;
            for (float y = markOff - seg; y < h; y += seg)
                c.drawRect(x - dw, y, x + dw, y + seg * 0.5f, p);
        }

        for (int i = 0; i < MAXS; i++) if (sa[i]) star(laneX(sl[i]), sy[i], carW * 0.45f, 0xFFFFC107);
        for (int i = 0; i < MAXC; i++) if (ea[i]) car(laneX(el[i]), ey[i], ec[i]);
        if (inv <= 0 || ((int) (inv * 10f) & 1) == 0) car(px, playerY, 0xFFE53935);

        hud();
        overlay();
        postInvalidateOnAnimation();
    }

    private void hud() {
        float ts = Math.min(h * 0.06f, roadX * 0.11f);
        float lx = roadX * 0.5f;
        text("SCORE " + (int) score, lx, h * 0.10f, ts, theme == 2 ? 0xFF263238 : 0xFFFFFFFF);
        star(lx - ts * 2.2f, h * 0.19f - ts * 0.3f, ts * 0.5f, 0xFFFFC107);
        text("x " + stars, lx + ts * 0.3f, h * 0.19f, ts, theme == 2 ? 0xFF263238 : 0xFFFFFFFF);
        text("BEST " + best, lx, h * 0.28f, ts * 0.8f, theme == 2 ? 0xFF263238 : 0xFFFFFFFF);
        for (int i = 0; i < 3; i++)
            heart(lx + (i - 1) * ts * 2.2f, h * 0.38f, ts * 0.6f, i < lives ? 0xFFE53935 : 0x66000000);

        // speed bar
        float bw = roadX * 0.7f, bh = h * 0.035f, bx = lx - bw / 2f, by = h * 0.62f;
        text("SPEED", lx, by - bh * 0.6f, ts * 0.7f, theme == 2 ? 0xFF263238 : 0xFFFFFFFF);
        p.setColor(0x88000000);
        r.set(bx, by, bx + bw, by + bh); canvas.drawRoundRect(r, bh / 2, bh / 2, p);
        float f = Math.max(0f, Math.min(1f, (speed - minSpd) / (maxSpd - minSpd)));
        p.setColor(f < 0.4f ? 0xFF66BB6A : (f < 0.75f ? 0xFFFFA726 : 0xFFEF5350));
        r.set(bx, by, bx + Math.max(bh, bw * f), by + bh); canvas.drawRoundRect(r, bh / 2, bh / 2, p);

        // help on the right side
        float rx = roadX + roadW + roadX * 0.5f, hs = Math.min(h * 0.04f, roadX * 0.085f);
        int hc = theme == 2 ? 0xFF263238 : 0xFFFFFFFF;
        text("UP = FASTER", rx, h * 0.80f, hs, hc);
        text("DOWN = SLOWER", rx, h * 0.87f, hs, hc);
        text("LEFT / RIGHT = STEER", rx, h * 0.94f, hs, hc);
    }

    private void overlay() {
        if (state == PLAY) return;
        float cx = w / 2f;
        if (state == READY) {
            panel(w * 0.27f, h * 0.10f, w * 0.73f, h * 0.90f);
            text("LITE RACER", cx, h * 0.26f, h * 0.10f, 0xFFFFEB3B);
            text("<  " + NAMES[theme] + "  >", cx, h * 0.39f, h * 0.07f, 0xFF80DEEA);
            text("Pick a world with LEFT / RIGHT", cx, h * 0.46f, h * 0.035f, 0xFFFFFFFF);
            text("Press OK to start!", cx, h * 0.58f, h * 0.065f, 0xFF69F0AE);
            text("UP : go faster    DOWN : slow down", cx, h * 0.69f, h * 0.04f, 0xFFFFFFFF);
            text("Collect the stars. Avoid the cars!", cx, h * 0.76f, h * 0.04f, 0xFFFFFFFF);
            text("Best: " + best, cx, h * 0.84f, h * 0.045f, 0xFFFFC107);
        } else if (state == PAUSE) {
            panel(w * 0.33f, h * 0.30f, w * 0.67f, h * 0.70f);
            text("PAUSED", cx, h * 0.46f, h * 0.09f, 0xFFFFEB3B);
            text("Press OK to continue", cx, h * 0.60f, h * 0.05f, 0xFFFFFFFF);
        } else {
            panel(w * 0.27f, h * 0.15f, w * 0.73f, h * 0.85f);
            text("GREAT DRIVING!", cx, h * 0.31f, h * 0.085f, 0xFFFFEB3B);
            text("Score: " + (int) score, cx, h * 0.43f, h * 0.06f, 0xFFFFFFFF);
            text("Stars: " + stars, cx, h * 0.51f, h * 0.06f, 0xFFFFC107);
            if (newBest) text("NEW BEST SCORE!", cx, h * 0.60f, h * 0.06f, 0xFF69F0AE);
            text("Press OK to play again", cx, h * 0.71f, h * 0.055f, 0xFF69F0AE);
            text("< " + NAMES[theme] + " >  change world with LEFT / RIGHT", cx, h * 0.79f, h * 0.035f, 0xFF80DEEA);
        }
    }
}
