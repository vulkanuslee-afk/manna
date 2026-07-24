package com.hyunmin.manna;

import android.os.Build;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;

import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView web;
    private AdView adView;
    private FrameLayout adContainer;
    private BillingManager billing;
    private TtsManager tts;
    private String lang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        adContainer = findViewById(R.id.adContainer);
        applySystemBarInsets();
        web = findViewById(R.id.web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectPurchaseButton();
            }
        });
        web.addJavascriptInterface(new JsBridge(this), "MannaNative");
        lang = resolveLang();
        web.loadUrl("file:///android_asset/" + (lang.equals("en") ? "index_en.html" : "index.html"));

        // 음성 엔진 (폰 내장 TTS 사용)
        tts = new TtsManager(this, lang);

        // 결제 매니저 (광고 제거 상품)
        billing = new BillingManager(this, isAdFree -> runOnUiThread(() -> {
            applyAdFree(isAdFree);
            injectPurchaseButton();
        }));

        // 광고 초기화
        // ── 내 폰에서는 테스트 광고만 나오게 (실수로 내 광고를 눌러 계정 정지되는 것 방지) ──
        // 앱을 한 번 실행한 뒤 Logcat에서 "Use RequestConfiguration.Builder.setTestDeviceIds"
        // 로 검색하면 내 기기 ID가 나온다. 그 값을 아래에 넣을 것.
        MobileAds.setRequestConfiguration(
                new RequestConfiguration.Builder()
                        .setTestDeviceIds(Arrays.asList(
                                "34CF815670BAD0FF2CDEE5F14E1B44F7"   // 이현민 갤럭시
                        ))
                        .build());

        MobileAds.initialize(this, initializationStatus -> {});
        if (!Prefs.isAdFree(this)) {
            setupBanner();
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
            }
        }
    }

    /**
     * 제스처 네비게이션 바(화면 맨 아래 막대) 영역만큼
     * 광고 컨테이너 아래에 여백을 줘서 겹치지 않게 한다.
     */
    private void applySystemBarInsets() {
        View root = findViewById(R.id.rootLayout);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupBanner() {
        if (adView != null) return;
        adView = new AdView(this);
        adView.setAdSize(AdSize.BANNER);
        adView.setAdUnitId(getString(R.string.admob_banner_id));
        adContainer.removeAllViews();
        adContainer.addView(adView);
        adContainer.setVisibility(View.VISIBLE);
        adView.loadAd(new AdRequest.Builder().build());
    }

    /** 광고 제거 상태 반영 */
    public void applyAdFree(boolean adFree) {
        Prefs.setAdFree(this, adFree);
        if (adFree) {
            adContainer.setVisibility(View.GONE);
            adContainer.removeAllViews();
            if (adView != null) {
                adView.destroy();
                adView = null;
            }
        } else {
            setupBanner();
        }
    }

    /**
     * HTML을 고치지 않고, 로딩이 끝난 뒤 작은 "광고 제거" 버튼만 삽입한다.
     * 이미 구매했으면 버튼을 지운다.
     */
    private void injectPurchaseButton() {
        boolean adFree = Prefs.isAdFree(this);
        String js;
        if (adFree) {
            js = "(function(){var b=document.getElementById('mannaAdFreeBtn');if(b)b.remove();})();";
        } else {
            js =
                "(function(){" +
                "if(document.getElementById('mannaAdFreeBtn'))return;" +
                "var b=document.createElement('button');" +
                "b.id='mannaAdFreeBtn';" +
                "b.textContent='\\uAD11\\uACE0 \\uC81C\\uAC70';" +   // 광고 제거
                "b.style.cssText='position:fixed;top:10px;right:10px;z-index:99999;" +
                "background:rgba(212,169,78,.18);color:#e8c87e;border:1px solid rgba(212,169,78,.5);" +
                "border-radius:99px;padding:6px 13px;font-size:11.5px;font-weight:700;" +
                "font-family:inherit;cursor:pointer;';" +
                "b.onclick=function(){ if(window.MannaNative) MannaNative.buyRemoveAds(); };" +
                "b.oncontextmenu=function(e){e.preventDefault(); if(window.MannaNative) MannaNative.restorePurchase(); return false;};" +
                "document.body.appendChild(b);" +
                "})();";
        }
        web.evaluateJavascript(js, null);
        injectLangButton();
    }

    /** 좌측 상단에 작은 언어 전환 버튼(한/EN)을 얹는다. HTML은 건드리지 않는다. */
    private void injectLangButton() {
        String label = "ko".equals(lang) ? "EN" : "\\uD55C";   // 한
        String js =
            "(function(){" +
            "var b=document.getElementById('mannaLangBtn');" +
            "if(!b){b=document.createElement('button');b.id='mannaLangBtn';" +
            "b.style.cssText='position:fixed;top:10px;left:10px;z-index:99999;" +
            "background:rgba(212,169,78,.18);color:#e8c87e;border:1px solid rgba(212,169,78,.5);" +
            "border-radius:99px;padding:6px 13px;font-size:11.5px;font-weight:700;" +
            "font-family:inherit;cursor:pointer;';" +
            "b.onclick=function(){ if(window.MannaNative) MannaNative.switchLang(); };" +
            "document.body.appendChild(b);}" +
            "b.textContent='" + label + "';" +
            "})();";
        web.evaluateJavascript(js, null);
    }

    public TtsManager getTts() { return tts; }

    /** 사용자가 고른 언어 우선, 없으면 폰 언어 기준 */
    private String resolveLang() {
        String saved = Prefs.getLang(this);
        if (saved != null) return saved;
        String sys = Locale.getDefault().getLanguage();
        return "ko".equals(sys) ? "ko" : "en";
    }

    public String getLang() { return lang; }

    /** 한글 <-> 영어 전환 후 화면 다시 불러오기 */
    public void switchLang() {
        String next = "ko".equals(lang) ? "en" : "ko";
        Prefs.setLang(this, next);
        recreate();
    }

    public void startPurchase() {
        if (billing != null) billing.launchPurchase(this);
    }

    public void restorePurchase() {
        if (billing != null) billing.queryExisting();
    }

    @Override public void onResume() { super.onResume(); if (adView != null) adView.resume(); }
    @Override public void onPause() {
        if (adView != null) adView.pause();
        if (tts != null) tts.stop();
        super.onPause();
    }
    @Override public void onDestroy() {
        if (adView != null) adView.destroy();
        if (billing != null) billing.end();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
