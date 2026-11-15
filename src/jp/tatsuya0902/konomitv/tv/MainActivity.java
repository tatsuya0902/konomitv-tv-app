package jp.tatsuya0902.konomitv.tv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * KonomiTV を全画面で表示するだけの薄い WebView ラッパー。
 * 操作は KonomiTV 側の D-pad 空間ナビゲーション (方向キー) に任せ、
 * ここではキーイベントを WebView へ素通しする。
 *
 * 戻るボタンは2段階: 1回目はページへ Escape キーを送る
 * (全画面の終了・ダイアログを閉じる・操作モードの終了)、
 * 2秒以内の2回目で実際に前の画面へ戻る。
 *
 * リモコンのメディアキー (再生・一時停止・停止・早送り・巻き戻し・前/次) は
 * ここで受けて、ページ内の video 要素を直接操作する (WebView には届けない)。
 * 前/次はまずページ側が公開するチャプター移動 (CM 区間の境目) を試み、
 * CM 区間がない録画では従来どおり「前の話/次の話」ボタンへのクリックに落とす。
 *
 * 開く URL は既定では HOME_URL (作者宅の KonomiTV)。サーバーの場所が違う場合は
 * 起動 Intent に url を付けると、その URL が保存されて以後はそれを開く:
 *   adb shell am start -n jp.tatsuya0902.konomitv.tv/.MainActivity -e url https://<host>:7000/
 */
public class MainActivity extends Activity {

    private static final String HOME_URL = "https://192-168-100-6.local.konomi.tv:7000/";
    private static final String PREFS_NAME = "settings";
    private static final String PREF_KEY_URL = "home_url";
    private static final long BACK_DOUBLE_PRESS_MS = 2000;

    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private long lastBackPressedAt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        // 自分のテレビで使う個人用アプリのため、WebView のデバッグを有効化しておく
        WebView.setWebContentsDebuggingEnabled(true);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        WebSettings settings = webView.getSettings();
        // KonomiTV 側の空間ナビがテレビ向けラッパーだと判別できるよう UA に印を付ける
        settings.setUserAgentString(settings.getUserAgentString() + " KonomiTV-TV/1.5");
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setSupportZoom(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            // HTML5 フルスクリーン (プレイヤーの全画面ボタン) に対応する
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.addView(customView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
                hideSystemUi();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) {
                    return;
                }
                FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.removeView(customView);
                customView = null;
                customViewCallback = null;
                webView.setVisibility(View.VISIBLE);
                webView.requestFocus();
            }
        });
        setContentView(webView);
        hideSystemUi();

        if (savedInstanceState == null) {
            webView.loadUrl(resolveHomeUrl());
        } else {
            webView.restoreState(savedInstanceState);
        }
        webView.requestFocus();
    }

    /**
     * 開く URL を決める。起動 Intent に url が付いていればそれを保存して使い、
     * 以後は保存した URL を開く。どちらも無ければ HOME_URL。
     */
    private String resolveHomeUrl() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String extra = getIntent() != null ? getIntent().getStringExtra("url") : null;
        if (extra != null && !extra.isEmpty()) {
            prefs.edit().putString(PREF_KEY_URL, extra).apply();
            return extra;
        }
        return prefs.getString(PREF_KEY_URL, HOME_URL);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // メディアキーは WebView に届く前にここで処理する
        if (event.getAction() == KeyEvent.ACTION_DOWN && handleMediaKey(event.getKeyCode())) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * リモコンのメディアキーを、ページ内の video 要素への操作に変換する。
     * 動画がない画面では何も起きない。処理した場合は true を返す。
     */
    private boolean handleMediaKey(int keyCode) {
        String action;
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                action = "play";
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                action = "pause";
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                action = "toggle";
                break;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                action = "pause";
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                action = "ff";
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                action = "rew";
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                action = "next";
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                action = "prev";
                break;
            default:
                return false;
        }
        String js = "(function(){var v=document.querySelector('video');if(!v)return;"
                + "var a='" + action + "';"
                + "if(a==='play'){v.play();}"
                + "else if(a==='pause'){v.pause();}"
                + "else if(a==='toggle'){if(v.paused){v.play();}else{v.pause();}}"
                + "else if(a==='ff'){v.currentTime=Math.min((v.duration||1e9),v.currentTime+60);}"
                + "else if(a==='rew'){v.currentTime=Math.max(0,v.currentTime-60);}"
                + "else{if(window.__konomiChapterSeek&&window.__konomiChapterSeek(a)){return;}"
                + "var t=a==='next'?'\u6b21\u306e\u8a71':'\u524d\u306e\u8a71';"
                + "var els=document.querySelectorAll('a,button');var hit=null;"
                + "for(var i=0;i<els.length;i++){if(els[i].textContent.indexOf(t)>=0){hit=els[i];break;}}"
                + "if(hit){hit.click();}else{v.currentTime=Math.max(0,v.currentTime+(a==='next'?60:-60));}}"
                + "})()";
        webView.evaluateJavascript(js, null);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 本物のフルスクリーン中はまずそれを終了する
            if (customView != null) {
                webView.getWebChromeClient().onHideCustomView();
                return true;
            }
            long now = SystemClock.uptimeMillis();
            if (now - lastBackPressedAt <= BACK_DOUBLE_PRESS_MS) {
                lastBackPressedAt = 0;
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            } else {
                lastBackPressedAt = now;
                // ページへ Escape を送る (疑似フルスクリーン終了・ダイアログを閉じる等)
                webView.evaluateJavascript(
                        "document.dispatchEvent(new KeyboardEvent('keydown',"
                                + "{key:'Escape',code:'Escape',bubbles:true,cancelable:true}))",
                        null);
                Toast.makeText(this, "もう一度「戻る」で前の画面へ", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
