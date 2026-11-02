package com.abraxel.cryptocurrency.portfolio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TotalsTest {

    @Test
    public void holding_profitAndLoss() {
        Holding h = new Holding("BTCUSDT", 0.5, 60_000);
        assertEquals(40_000, h.value(80_000), 0);
        assertEquals(10_000, h.profit(80_000), 0);
        assertEquals(33.333, h.profitPercent(80_000), 0.001);
    }

    @Test
    public void holding_withoutCostHasNoProfit() {
        Holding h = new Holding("ETHUSDT", 2, 0);
        assertEquals(0, h.profit(3000), 0);
        assertEquals(0, h.profitPercent(3000), 0);
    }

    @Test
    public void totals_sumValueChangeAndProfit_skippingUnknownPrices() {
        List<Holding> holdings = Arrays.asList(
                new Holding("BTCUSDT", 0.5, 60_000),   // değer 40.000, maliyet 30.000
                new Holding("ETHUSDT", 2, 0),          // değer 6.000, maliyet yok
                new Holding("NEWUSDT", 100, 1));       // fiyat bilinmiyor
        Map<String, Double> prices = new HashMap<>();
        prices.put("BTCUSDT", 80_000.0);
        prices.put("ETHUSDT", 3_000.0);
        Map<String, Double> opens = new HashMap<>();
        opens.put("BTCUSDT", 76_000.0);
        opens.put("ETHUSDT", 3_100.0);

        Totals t = Totals.of(holdings, prices, opens);

        assertEquals(46_000, t.value, 0);
        assertEquals(30_000, t.cost, 0);
        assertEquals(10_000, t.profit, 0);
        assertEquals(2_000 - 200, t.change24h, 0); // BTC +2000, ETH -200
        assertEquals(33.333, t.profitPercent(), 0.001);
    }
}
