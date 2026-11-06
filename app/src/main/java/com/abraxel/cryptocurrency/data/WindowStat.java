package com.abraxel.cryptocurrency.data;

/** Bir sembolün kayan penceredeki özeti (USD). */
public final class WindowStat {
    public final double open;
    public final double last;
    public final double quoteVolume;
    /** Penceredeki ilk ve son işlemin zamanı (ms). */
    public final long openTime;
    public final long closeTime;

    public WindowStat(double open, double last, double quoteVolume, long openTime, long closeTime) {
        this.open = open;
        this.last = last;
        this.quoteVolume = quoteVolume;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    /**
     * Pencerenin en az yarısı işlem verisiyle dolu mu? Yeni listelenen bir coinin "7g" değişimi aslında
     * listelenmeden beriki değişimdir; böyle değerler dönem sıralamasına girmez.
     */
    public boolean covers(long durationMs) {
        return openTime <= 0 || closeTime - openTime >= durationMs / 2;
    }

    /** Güncel (canlı) fiyata göre pencere boyunca yüzde değişim; açılış bilinmiyorsa NaN. */
    public double changePercent(double price) {
        return open > 0 ? (price - open) / open * 100 : Double.NaN;
    }
}
