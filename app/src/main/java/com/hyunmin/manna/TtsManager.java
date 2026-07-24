package com.hyunmin.manna;

import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Toast;

import java.util.Locale;

/**
 * 폰에 설치된 음성엔진(보통 구글 TTS)을 사용한다.
 * 별도 음성 파일 없이 모든 말씀을 읽을 수 있고, 앱 용량이 늘지 않는다.
 */
public class TtsManager implements TextToSpeech.OnInitListener {

    private final Context ctx;
    private final Locale locale;
    private TextToSpeech tts;
    private boolean ready = false;
    private boolean koreanOk = false;
    private boolean speaking = false;
    private String pending = null;   // 엔진 준비 전에 누른 경우 대기

    public TtsManager(Context context, String lang) {
        this.ctx = context.getApplicationContext();
        this.locale = "en".equals(lang) ? Locale.US : Locale.KOREAN;
        tts = new TextToSpeech(ctx, this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            ready = false;
            return;
        }
        ready = true;

        int r = tts.setLanguage(locale);
        koreanOk = !(r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED);

        tts.setSpeechRate(0.92f);
        tts.setPitch(1.0f);

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { speaking = true; }
            @Override public void onDone(String id) { speaking = false; }
            @Override public void onError(String id) { speaking = false; }
        });

        if (pending != null) {
            String t = pending;
            pending = null;
            speak(t);
        }
    }

    /** 읽기 시작. 이미 읽는 중이면 멈추고 새로 읽는다. */
    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;

        if (!ready) {
            pending = text;   // 엔진 준비되면 자동 실행
            return;
        }

        if (!koreanOk) {
            toast(Locale.US.equals(locale)
                    ? "English voice data is not installed. Please install it in settings."
                    : "한국어 음성 데이터가 없어. 설정에서 설치해줘.");
            openTtsSettings();
            return;
        }

        tts.stop();
        speaking = true;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "manna");
    }

    public void stop() {
        speaking = false;
        if (tts != null) tts.stop();
    }

    public boolean isSpeaking() {
        if (tts == null) return false;
        try {
            return speaking || tts.isSpeaking();
        } catch (Exception e) {
            return speaking;
        }
    }

    /** 음성엔진 자체를 쓸 수 있는 상태인지 */
    public boolean isAvailable() {
        return tts != null;
    }

    private void openTtsSettings() {
        try {
            Intent i = new Intent("com.android.settings.TTS_SETTINGS");
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) { }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    private void toast(String m) {
        Toast.makeText(ctx, m, Toast.LENGTH_LONG).show();
    }
}
