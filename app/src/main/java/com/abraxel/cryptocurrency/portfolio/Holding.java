package com.abraxel.cryptocurrency.portfolio;

import org.json.JSONException;
import org.json.JSONObject;

/** Portföydeki bir coin: adet ve USD cinsinden ortalama alış fiyatı (0 = bilinmiyor). */
public final class Holding {
    public final String symbol;
    public final double amount;
    public final double avgCostUsd;

    public Holding(String symbol, double amount, double avgCostUsd) {
        this.symbol = symbol;
        this.amount = amount;
        this.avgCostUsd = avgCostUsd;
    }

    public double value(double priceUsd) {
        return amount * priceUsd;
    }

    public boolean hasCost() {
        return avgCostUsd > 0;
    }

    public double cost() {
        return amount * avgCostUsd;
    }

    /** Kâr/zarar (USD). Alış fiyatı yoksa 0. */
    public double profit(double priceUsd) {
        return hasCost() ? value(priceUsd) - cost() : 0;
    }

    public double profitPercent(double priceUsd) {
        return hasCost() ? profit(priceUsd) / cost() * 100 : 0;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("symbol", symbol).put("amount", amount).put("avgCostUsd", avgCostUsd);
    }

    public static Holding fromJson(JSONObject o) throws JSONException {
        return new Holding(o.getString("symbol"), o.getDouble("amount"), o.optDouble("avgCostUsd", 0));
    }
}
