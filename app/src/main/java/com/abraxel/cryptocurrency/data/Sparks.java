package com.abraxel.cryptocurrency.data;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mini grafik verisi: seçili dönemi kapsayan gerçek mumların kapanışları (ilk nokta dönemin açılışı).
 * Yalnızca ekrandaki satırlar için, ağırlık bütçesi izin verdikçe istenir; veri yoksa grafik çizilmez.
 */
public final class Sparks {
    private static final int MAX_ENTRIES = 400;
    private static final long MIN_TTL_MS = 120_000L;

    private static final Map<String, Point> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Point> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private static final Set<String> IN_FLIGHT = new HashSet<>();

    private static final class Point {
        final float[] points;
        final long time;

        Point(float[] points, long time) {
            this.points = points;
            this.time = time;
        }
    }

    private Sparks() {
    }

    private static String key(String symbol, Period period) {
        return symbol + '|' + period.name();
    }

    /** Önbellekteki noktalar (USD); yoksa null. */
    public static float[] get(String symbol, Period period) {
        Point e = CACHE.get(key(symbol, period));
        return e == null ? null : e.points;
    }

    /** Eksik ya da eskimiş olanları yükler; her yeni veri geldiğinde onLoaded çağrılır (ana thread). */
    public static void request(Collection<String> symbols, Period period, Runnable onLoaded) {
        long ttl = Math.max(MIN_TTL_MS, period.ttlMs);
        long now = System.currentTimeMillis();
        for (String symbol : symbols) {
            String key = key(symbol, period);
            Point e = CACHE.get(key);
            if (e != null && now - e.time < ttl) continue;
            if (IN_FLIGHT.contains(key)) continue;
            if (!Binance.canSpend(2)) return; // bütçe dolu: sonraki kaydırmada tekrar denenir
            IN_FLIGHT.add(key);
            Binance.fetchCandles(symbol, period.sparkInterval, period.sparkPoints, new Binance.Result<>() {
                @Override
                public void onSuccess(List<Candle> candles) {
                    IN_FLIGHT.remove(key);
                    float[] points = points(candles);
                    if (points == null) return;
                    CACHE.put(key, new Point(points, System.currentTimeMillis()));
                    onLoaded.run();
                }

                @Override
                public void onError(Exception e) {
                    IN_FLIGHT.remove(key);
                }
            });
        }
    }

    /** İlk mumun açılışı + her mumun kapanışı. İki noktadan azsa null. */
    static float[] points(List<Candle> candles) {
        if (candles.size() < 2) return null;
        float[] p = new float[candles.size() + 1];
        p[0] = (float) candles.get(0).open;
        for (int i = 0; i < candles.size(); i++) p[i + 1] = (float) candles.get(i).close;
        return p;
    }
}
