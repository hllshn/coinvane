package com.abraxel.cryptocurrency.alerts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Favoriler ve fiyat alarmları için küçük yerel depo (SharedPreferences). */
public final class Store {
    private static final String PREFS = "kripto";
    private static final String FAVORITES = "favorites";
    private static final String ALERTS = "alerts";

    private Store() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // --- Favoriler ---

    public static Set<String> favorites(Context context) {
        return new HashSet<>(prefs(context).getStringSet(FAVORITES, new HashSet<>()));
    }

    /** @return favoriye eklendiyse true, çıkarıldıysa false */
    public static boolean toggleFavorite(Context context, String symbol) {
        Set<String> favorites = favorites(context);
        boolean added = favorites.add(symbol);
        if (!added) favorites.remove(symbol);
        prefs(context).edit().putStringSet(FAVORITES, favorites).apply();
        return added;
    }

    // --- Alarmlar ---

    public static synchronized List<PriceAlert> alerts(Context context) {
        return decode(prefs(context).getString(ALERTS, "[]"));
    }

    public static List<PriceAlert> alerts(Context context, String symbol) {
        List<PriceAlert> result = new ArrayList<>();
        for (PriceAlert a : alerts(context)) if (a.symbol.equals(symbol)) result.add(a);
        return result;
    }

    public static synchronized void addAlert(Context context, PriceAlert alert) {
        List<PriceAlert> alerts = alerts(context);
        if (!alerts.contains(alert)) alerts.add(alert);
        save(context, alerts);
    }

    public static synchronized void removeAlert(Context context, PriceAlert alert) {
        List<PriceAlert> alerts = alerts(context);
        alerts.remove(alert);
        save(context, alerts);
    }

    @SuppressLint("ApplySharedPref") // işçi süreci hemen kapanabilir; yazım senkron olmalı
    private static void save(Context context, List<PriceAlert> alerts) {
        prefs(context).edit().putString(ALERTS, encode(alerts)).commit();
        PriceAlertWorker.schedule(context, !alerts.isEmpty());
    }

    static String encode(List<PriceAlert> alerts) {
        JSONArray array = new JSONArray();
        try {
            for (PriceAlert a : alerts) array.put(a.toJson());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return array.toString();
    }

    static List<PriceAlert> decode(String json) {
        List<PriceAlert> alerts = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) alerts.add(PriceAlert.fromJson(array.getJSONObject(i)));
        } catch (JSONException ignored) {
            // Bozuk kayıt: boş liste ile devam et
        }
        return alerts;
    }
}
