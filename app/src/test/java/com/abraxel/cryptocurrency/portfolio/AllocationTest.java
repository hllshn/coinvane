package com.abraxel.cryptocurrency.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllocationTest {

    @Test
    public void slicesSortedByValue_smallOnesGroupedAsOther_unknownPriceSkipped() {
        List<Holding> holdings = Arrays.asList(
                new Holding("BTCUSDT", 1, 0),   // 600
                new Holding("ETHUSDT", 2, 0),   // 300
                new Holding("SOLUSDT", 1, 0),   // 60
                new Holding("DOGEUSDT", 100, 0),// 40
                new Holding("NEWUSDT", 5, 0));  // fiyat bilinmiyor
        Map<String, Double> prices = new HashMap<>();
        prices.put("BTCUSDT", 600.0);
        prices.put("ETHUSDT", 150.0);
        prices.put("SOLUSDT", 60.0);
        prices.put("DOGEUSDT", 0.4);

        List<Allocation.Slice> slices = Allocation.of(holdings, prices, 3);
        assertEquals(3, slices.size());
        assertEquals("BTCUSDT", slices.get(0).symbol);
        assertEquals(60.0, slices.get(0).percent, 1e-9);
        assertEquals("ETHUSDT", slices.get(1).symbol);
        assertEquals(Allocation.OTHER, slices.get(2).symbol);
        assertEquals(10.0, slices.get(2).percent, 1e-9);

        double sum = 0;
        for (Allocation.Slice s : Allocation.of(holdings, prices, 10)) sum += s.percent;
        assertEquals(100.0, sum, 1e-9);
        assertTrue(Allocation.of(holdings, new HashMap<>(), 3).isEmpty());
    }
}
