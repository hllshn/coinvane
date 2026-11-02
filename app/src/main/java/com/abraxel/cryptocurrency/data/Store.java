package com.abraxel.cryptocurrency.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.abraxel.cryptocurrency.alerts.PriceAlert;
import com.abraxel.cryptocurrency.alerts.PriceAlertWorker;
import com.abraxel.cryptocurrency.portfolio.Holding;
import com.abraxel.cryptocurrency.widget.PriceWidget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cihazdaki kullanıcı verisi: favoriler, fiyat alarmları, portföy (SharedPreferences).
 * Alarmlar her canlı tick'te okunduğu için bellekte de tutulur.
 */
public final class Store {
    private static final String PREFS = "kripto";
    private static final String FAVORITES = "favorites";
    private static final String ALERTS = "alerts";
    private static final String HOLDINGS = "holdings";

    private static List<PriceAlert> alertCache;

    private Store() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // --- Favoriler ---

    public static Set<String> favorites(Context context) {
        return migrateFavorites(prefs(context).getStringSet(FAVORITES, new HashSet<>()));
    }

    /** v7 öncesi favoriler TRY paritesiyle (BTCTRY) saklanıyordu. */
    static Set<String> migrateFavorites(Set<String> saved) {
        Set<String> result = new HashSet<>();
        for (String s : saved) {
            String symbol = s.endsWith("TRY") ? Coin.symbolOf(s.substring(0, s.length() - 3)) : s;
            if (Coin.isListable(symbol)) result.add(symbol);
        }
        return result;
    }

    /** @return favoriye eklendiyse true, çıkarıldıysa false */
    public static boolean toggleFavorite(Context context, String symbol) {
        Set<String> favorites = favorites(context);
        boolean added = favorites.add(symbol);
        if (!added) favorites.remove(symbol);
        prefs(context).edit().putStringSet(FAVORITES, favorites).apply();
        PriceWidget.refresh(context);
        return added;
    }

    // --- Alarmlar ---

    public static synchronized List<PriceAlert> alerts(Context context) {
        if (alertCache == null) alertCache = decodeAlerts(prefs(context).getString(ALERTS, "[]"));
        return new ArrayList<>(alertCache);
    }

    public static List<PriceAlert> alerts(Context context, String symbol) {
        List<PriceAlert> result = new ArrayList<>();
        for (PriceAlert a : alerts(context)) if (a.symbol.equals(symbol)) result.add(a);
        return result;
    }

    public static synchronized void addAlert(Context context, PriceAlert alert) {
        List<PriceAlert> alerts = alerts(context);
        if (!alerts.contains(alert)) alerts.add(alert);
        saveAlerts(context, alerts);
    }

    /** @return alarm hâlâ kayıtlıydı ve silindiyse true (aynı alarm iki yerden tetiklenmesin diye) */
    public static synchronized boolean removeAlert(Context context, PriceAlert alert) {
        List<PriceAlert> alerts = alerts(context);
        boolean removed = alerts.remove(alert);
        if (removed) saveAlerts(context, alerts);
        return removed;
    }

    @SuppressLint("ApplySharedPref") // işçi süreci hemen kapanabilir; yazım senkron olmalı
    private static void saveAlerts(Context context, List<PriceAlert> alerts) {
        alertCache = new ArrayList<>(alerts);
        prefs(context).edit().putString(ALERTS, encode(alerts, PriceAlert::toJson)).commit();
        PriceAlertWorker.schedule(context, !alerts.isEmpty());
    }

    static List<PriceAlert> decodeAlerts(String json) {
        List<PriceAlert> alerts = new ArrayList<>();
        for (JSONObject o : objects(json)) {
            try {
                PriceAlert a = PriceAlert.fromJson(o);
                if (Coin.isListable(a.symbol)) alerts.add(a);
            } catch (JSONException ignored) {
                // bozuk kayıt atlanır
            }
        }
        return alerts;
    }

    // --- Portföy ---

    /** Sembol → pozisyon, eklenme sırasıyla. */
    public static Map<String, Holding> holdings(Context context) {
        Map<String, Holding> map = new LinkedHashMap<>();
        for (JSONObject o : objects(prefs(context).getString(HOLDINGS, "[]"))) {
            try {
                Holding h = Holding.fromJson(o);
                map.put(h.symbol, h);
            } catch (JSONException ignored) {
                // bozuk kayıt atlanır
            }
        }
        return map;
    }

    /** amount 0 ise pozisyon silinir. */
    public static void setHolding(Context context, Holding holding) {
        Map<String, Holding> map = holdings(context);
        if (holding.amount > 0) map.put(holding.symbol, holding);
        else map.remove(holding.symbol);
        prefs(context).edit().putString(HOLDINGS, encode(map.values(), Holding::toJson)).apply();
    }

    // --- JSON yardımcıları ---

    interface ToJson<T> {
        JSONObject toJson(T item) throws JSONException;
    }

    static <T> String encode(Iterable<T> items, ToJson<T> toJson) {
        JSONArray array = new JSONArray();
        try {
            for (T item : items) array.put(toJson.toJson(item));
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return array.toString();
    }

    private static List<JSONObject> objects(String json) {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null) list.add(o);
            }
        } catch (JSONException ignored) {
            // bozuk kayıt: boş liste
        }
        return list;
    }
}
