package com.hyunmin.manna;

import android.webkit.JavascriptInterface;

public class JsBridge {
    private final MainActivity act;

    public JsBridge(MainActivity a) { act = a; }

    /* ---------- 광고 제거 ---------- */

    @JavascriptInterface
    public boolean isAdFree() {
        return Prefs.isAdFree(act);
    }

    @JavascriptInterface
    public void buyRemoveAds() {
        act.runOnUiThread(act::startPurchase);
    }

    @JavascriptInterface
    public void restorePurchase() {
        act.runOnUiThread(act::restorePurchase);
    }

    /* ---------- 말씀 듣기 (네이티브 음성) ---------- */

    /** 웹뷰에서 음성 사용 가능 여부 확인 */
    @JavascriptInterface
    public boolean ttsAvailable() {
        return act.getTts() != null && act.getTts().isAvailable();
    }

    @JavascriptInterface
    public void ttsSpeak(final String text) {
        act.runOnUiThread(() -> {
            if (act.getTts() != null) act.getTts().speak(text);
        });
    }

    @JavascriptInterface
    public void ttsStop() {
        act.runOnUiThread(() -> {
            if (act.getTts() != null) act.getTts().stop();
        });
    }

    @JavascriptInterface
    public boolean ttsIsSpeaking() {
        return act.getTts() != null && act.getTts().isSpeaking();
    }

    /* ---------- 언어 ---------- */

    @JavascriptInterface
    public String getLang() {
        return act.getLang();
    }

    @JavascriptInterface
    public void switchLang() {
        act.runOnUiThread(act::switchLang);
    }
}
