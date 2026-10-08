package com.lite.racer;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private GameView game;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new GameView(this);
        setContentView(game);
        hideBars();
    }

    private void hideBars() {
        game.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) { hideBars(); game.resumeClock(); }
    }

    @Override
    protected void onPause() {
        game.pauseGame();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        game.releaseAudio();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (game.handleKey(e)) return true;
        return super.onKeyDown(keyCode, e);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        if (game.handleKey(e)) return true;
        return super.onKeyUp(keyCode, e);
    }
}
