package com.abraxel.cryptocurrency.data;

/**
 * Yüzde değişim dönemi. 24s değişim zaten 24 saatlik ticker'da (ve canlı akışta) vardır; diğerleri Binance'in
 * kayan pencereli ticker'ından ({@code /api/v3/ticker?windowSize=}) gelir. Binance en fazla 7 günlük pencere
 * sunduğu için 30g yalnızca tek coin için (detay ekranında, saatlik mumlardan) hesaplanır; listelerde yoktur.
 */
public enum Period {
    M5("5m", 60_000L, "1m", 6),
    M15("15m", 60_000L, "1m", 16),
    H1("1h", 120_000L, "5m", 13),
    H4("4h", 300_000L, "15m", 17),
    D1("1d", 0, "1h", 25),
    D7("7d", 900_000L, "4h", 43),
    D30("30d", 3_600_000L, "1d", 31);

    /** Listelerde (Ana Sayfa, Piyasalar) seçilebilen dönemler. */
    public static final Period[] LIST = {M5, M15, H1, H4, D1, D7};

    /** Binance windowSize değeri. */
    public final String window;
    /** Pencere verisinin tazelenme aralığı (kısa pencereler daha sık). */
    public final long ttlMs;
    /** Mini grafik için mum aralığı ve sayısı (dönemi kapsar). */
    public final String sparkInterval;
    public final int sparkPoints;

    Period(String window, long ttlMs, String sparkInterval, int sparkPoints) {
        this.window = window;
        this.ttlMs = ttlMs;
        this.sparkInterval = sparkInterval;
        this.sparkPoints = sparkPoints;
    }

    /** Kayıtlı ad → dönem; tanınmazsa yedek. */
    public static Period parse(String name, Period fallback) {
        if (name == null) return fallback;
        for (Period p : values()) if (p.name().equals(name)) return p;
        return fallback;
    }

    public long durationMs() {
        switch (this) {
            case M5: return 5 * 60_000L;
            case M15: return 15 * 60_000L;
            case H1: return 3_600_000L;
            case H4: return 4 * 3_600_000L;
            case D1: return 24 * 3_600_000L;
            case D7: return 7 * 24 * 3_600_000L;
            default: return 30 * 24 * 3_600_000L;
        }
    }
}
