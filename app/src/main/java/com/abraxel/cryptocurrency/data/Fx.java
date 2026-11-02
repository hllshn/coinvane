package com.abraxel.cryptocurrency.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * USD → diğer para birimleri kurları. Kaynak: ExchangeRate-API açık uç noktası (anahtarsız, günlük güncellenir;
 * kullanım koşulu gereği Ayarlar ekranında kaynak gösterilir). Son kurlar cihazda saklanır, 6 saatten eskiyse yenilenir.
 */
public final class Fx {
    static final String URL = "https://open.er-api.com/v6/latest/USD";
    private static final long MAX_AGE_MS = 6 * 60 * 60 * 1000L;
    private static final String PREFS = "fx";

    private static Map<String, Double> memory;

    private Fx() {
    }

    /** Bilinen kur; yoksa NaN (USD her zaman 1). */
    public static double rate(Context context, String code) {
        if ("USD".equals(code)) return 1;
        Double rate = cached(context).get(code);
        return rate != null ? rate : Double.NaN;
    }

    public static boolean isStale(Context context) {
        long fetchedAt = prefs(context).getLong("time", 0);
        return System.currentTimeMillis() - fetchedAt > MAX_AGE_MS;
    }

    public static void refresh(Context context, Binance.Result<Void> result) {
        Context app = context.getApplicationContext();
        Binance.get(URL, Fx::parse, new Binance.Result<Map<String, Double>>() {
            @Override
            public void onSuccess(Map<String, Double> rates) {
                save(app, rates);
                result.onSuccess(null);
            }

            @Override
            public void onError(Exception e) {
                result.onError(e);
            }
        });
    }

    /** Arka plan işçileri için: gerekiyorsa senkron yeniler; hata olursa eski kurlarla devam edilir. */
    public static void refreshIfStaleBlocking(Context context) {
        if (!isStale(context)) return;
        try {
            save(context, parse(Binance.getBlocking(Binance.client(), URL)));
        } catch (IOException | JSONException ignored) {
            // eski kurlar kullanılır
        }
    }

    private static synchronized Map<String, Double> cached(Context context) {
        if (memory == null) {
            try {
                memory = parseRates(new JSONObject(prefs(context).getString("rates", "{}")));
            } catch (JSONException e) {
                memory = new HashMap<>();
            }
        }
        return memory;
    }

    private static synchronized void save(Context context, Map<String, Double> rates) {
        memory = rates;
        prefs(context).edit()
                .putString("rates", new JSONObject(rates).toString())
                .putLong("time", System.currentTimeMillis())
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Map<String, Double> parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        if (!"success".equals(o.optString("result"))) throw new JSONException("FX: " + o.optString("error-type"));
        return parseRates(o.getJSONObject("rates"));
    }

    private static Map<String, Double> parseRates(JSONObject rates) throws JSONException {
        Map<String, Double> map = new HashMap<>();
        for (Iterator<String> it = rates.keys(); it.hasNext(); ) {
            String code = it.next();
            map.put(code, rates.getDouble(code));
        }
        return map;
    }
}
