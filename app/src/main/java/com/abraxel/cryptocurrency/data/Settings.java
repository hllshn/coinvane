package com.abraxel.cryptocurrency.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/** Kullanıcı tercihleri: para birimi, tema, sistem renkleri. Dil seçimi AppCompat tarafından saklanır. */
public final class Settings {
    /** Seçilebilir para birimleri; hepsi ExchangeRate-API'de mevcut. */
    public static final List<String> CURRENCIES = Arrays.asList(
            "USD", "EUR", "GBP", "TRY", "JPY", "KRW", "CNY", "INR", "BRL", "RUB", "UAH", "PLN", "CHF", "SEK",
            "NOK", "CAD", "AUD", "MXN", "ARS", "SAR", "AED", "IDR", "VND", "NGN", "ZAR");

    /** Dil etiketi → kendi dilinde adı. Boş etiket = sistem dili. */
    public static final String[][] LANGUAGES = {
            {"en", "English"}, {"tr", "Türkçe"}, {"de", "Deutsch"}, {"fr", "Français"}, {"es", "Español"},
            {"pt-BR", "Português (Brasil)"}, {"ru", "Русский"}, {"ar", "العربية"}, {"ja", "日本語"},
            {"ko", "한국어"}, {"zh-CN", "简体中文"},
    };

    private static final String PREFS = "settings";

    private Settings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String currency(Context context) {
        String saved = prefs(context).getString("currency", null);
        return saved != null ? saved : defaultCurrency(Locale.getDefault());
    }

    public static void setCurrency(Context context, String code) {
        prefs(context).edit().putString("currency", code).apply();
    }

    /** İlk açılışta cihaz bölgesinin parası (destekleniyorsa), yoksa USD. */
    static String defaultCurrency(Locale locale) {
        try {
            String code = Currency.getInstance(locale).getCurrencyCode();
            return CURRENCIES.contains(code) ? code : "USD";
        } catch (IllegalArgumentException | NullPointerException e) {
            return "USD"; // bölgesiz dil (ör. "en")
        }
    }

    /** AppCompatDelegate.MODE_NIGHT_*. v8'den itibaren seçim yapmamış kullanıcı için varsayılan koyu tema. */
    public static int theme(Context context) {
        return prefs(context).getInt("theme", AppCompatDelegate.MODE_NIGHT_YES);
    }

    public static void setTheme(Context context, int mode) {
        prefs(context).edit().putInt("theme", mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static boolean dynamicColors(Context context) {
        return prefs(context).getBoolean("dynamic_colors", false);
    }

    public static void setDynamicColors(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("dynamic_colors", enabled).apply();
    }

    // --- Ekranlar arası hatırlanan seçimler (liste türü, dönem, filtre) ---

    /** Piyasalar'daki minimum 24s hacim eşikleri, kullanıcının para biriminde (0 = filtre yok). */
    public static final double[] MIN_VOLUME_STEPS = {0, 100_000, 1_000_000, 10_000_000, 100_000_000};

    public static String uiString(Context context, String key, String fallback) {
        return prefs(context).getString("ui_" + key, fallback);
    }

    public static void setUiString(Context context, String key, String value) {
        prefs(context).edit().putString("ui_" + key, value).apply();
    }

    public static int uiInt(Context context, String key, int fallback) {
        return prefs(context).getInt("ui_" + key, fallback);
    }

    public static void setUiInt(Context context, String key, int value) {
        prefs(context).edit().putInt("ui_" + key, value).apply();
    }
}
