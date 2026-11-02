package com.abraxel.cryptocurrency.alerts;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/** "BTCTRY 4.000.000 ₺ üzerine çıkınca haber ver" gibi tek seferlik fiyat alarmı. */
public final class PriceAlert {
    public final String symbol;
    public final double target;
    public final boolean above; // true: hedefin üzerine çıkınca, false: altına inince

    public PriceAlert(String symbol, double target, boolean above) {
        this.symbol = symbol;
        this.target = target;
        this.above = above;
    }

    /** Hedef, alarm kurulurken geçerli fiyata göre yön belirler. */
    public static PriceAlert create(String symbol, double target, double currentPrice) {
        return new PriceAlert(symbol, target, target > currentPrice);
    }

    public boolean isTriggered(double price) {
        return above ? price >= target : price <= target;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("symbol", symbol).put("target", target).put("above", above);
    }

    static PriceAlert fromJson(JSONObject o) throws JSONException {
        return new PriceAlert(o.getString("symbol"), o.getDouble("target"), o.getBoolean("above"));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PriceAlert)) return false;
        PriceAlert a = (PriceAlert) o;
        return symbol.equals(a.symbol) && target == a.target && above == a.above;
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, target, above);
    }
}
