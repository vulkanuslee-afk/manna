package com.hyunmin.manna;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String FILE = "manna_prefs";
    private static final String KEY_AD_FREE = "ad_free";
    private static final String KEY_LANG = "lang";   // "ko" | "en" | null(자동)

    public static boolean isAdFree(Context c) {
        return sp(c).getBoolean(KEY_AD_FREE, false);
    }

    public static void setAdFree(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_AD_FREE, v).apply();
    }

    /** 사용자가 직접 고른 언어. 안 골랐으면 null */
    public static String getLang(Context c) {
        return sp(c).getString(KEY_LANG, null);
    }

    public static void setLang(Context c, String lang) {
        sp(c).edit().putString(KEY_LANG, lang).apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
