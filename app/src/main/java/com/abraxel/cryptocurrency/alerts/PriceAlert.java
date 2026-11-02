package com.abraxel.cryptocurrency.alerts;

import com.abraxel.cryptocurrency.data.Coin;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/** "BTC 4.000.000 ₺ üzerine çıkınca haber ver" gibi tek seferlik fiyat alarmı. Hedef, kurulduğu para birimindedir. */
public final class PriceAlert {
    public final String symbol;
    public final double target;
    public final String currency;
    public final boolean above; // true: hedefin üzerine çıkınca, false: altına inince

    public PriceAlert(String symbol, double target, String currency, boolean above) {
        this.symbol = symbol;
        this.target = target;
        this.currency = currency;
        this.above = above;
    }

    /** Yön, alarm kurulurken geçerli fiyata göre belirlenir. */
    public static PriceAlert create(String symbol, double target, String currency, double currentPrice) {
        return new PriceAlert(symbol, target, currency, target > currentPrice);
    }

    /** price: alarmın para birimindeki güncel fiyat. */
    public boolean isTriggered(double price) {
        return above ? price >= target : price <= target;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("symbol", symbol).put("target", target)
                .put("currency", currency).put("above", above);
    }

    /** v7 öncesi kayıtlar TRY paritesi (BTCTRY) ve TRY hedefiyle tutuluyordu; USDT paritesine taşınır. */
    public static PriceAlert fromJson(JSONObject o) throws JSONException {
        String symbol = o.getString("symbol");
        String currency = o.optString("currency", "TRY");
        if (symbol.endsWith("TRY")) symbol = Coin.symbolOf(symbol.substring(0, symbol.length() - 3));
        return new PriceAlert(symbol, o.getDouble("target"), currency, o.getBoolean("above"));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PriceAlert)) return false;
        PriceAlert a = (PriceAlert) o;
        return symbol.equals(a.symbol) && target == a.target && currency.equals(a.currency) && above == a.above;
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, target, currency, above);
    }
}
