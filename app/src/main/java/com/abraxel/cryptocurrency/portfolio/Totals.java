package com.abraxel.cryptocurrency.portfolio;

import java.util.Collection;
import java.util.Map;

/** Portföy özeti (USD). Fiyatı henüz bilinmeyen coinler toplamaya katılmaz. */
public final class Totals {
    public final double value;
    public final double cost;      // yalnızca alış fiyatı girilmiş coinler
    public final double profit;    // yalnızca alış fiyatı girilmiş coinler
    public final double change24h; // portföyün 24 saatlik değer değişimi

    private Totals(double value, double cost, double profit, double change24h) {
        this.value = value;
        this.cost = cost;
        this.profit = profit;
        this.change24h = change24h;
    }

    public double profitPercent() {
        return cost > 0 ? profit / cost * 100 : 0;
    }

    /** prices / opens: sembol → USD son fiyat / 24 saat önceki fiyat. */
    public static Totals of(Collection<Holding> holdings, Map<String, Double> prices, Map<String, Double> opens) {
        double value = 0, cost = 0, profit = 0, change = 0;
        for (Holding h : holdings) {
            Double price = prices.get(h.symbol);
            if (price == null) continue;
            value += h.value(price);
            Double open = opens.get(h.symbol);
            if (open != null) change += h.value(price) - h.value(open);
            if (h.hasCost()) {
                cost += h.cost();
                profit += h.profit(price);
            }
        }
        return new Totals(value, cost, profit, change);
    }
}
