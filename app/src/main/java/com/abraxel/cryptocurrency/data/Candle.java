package com.abraxel.cryptocurrency.data;

/** Tek bir OHLC mumu (USD). time = mumun açılış zamanı (epoch ms), volume = coin adedi. */
public final class Candle {
    public final long time;
    public final double open;
    public final double high;
    public final double low;
    public final double close;
    public final double volume;

    public Candle(long time, double open, double high, double low, double close, double volume) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }
}
