package com.abraxel.cryptocurrency.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class PeriodDataTest {

    @Test
    public void window_parsesOpenVolumeAndTimes_skipsUntraded() throws Exception {
        String json = "[{\"symbol\":\"BTCUSDT\",\"openPrice\":\"75747.9\",\"lastPrice\":\"75694.01\",\"quoteVolume\":\"2581172.5\","
                + "\"openTime\":1793944500000,\"closeTime\":1793944816066},"
                + "{\"symbol\":\"DEADUSDT\",\"openPrice\":\"0.00000000\",\"lastPrice\":\"0\",\"quoteVolume\":\"0\","
                + "\"openTime\":0,\"closeTime\":0}]";
        Map<String, WindowStat> map = Binance.parseWindow(json);
        assertEquals(1, map.size());
        WindowStat s = map.get("BTCUSDT");
        assertEquals(75747.9, s.open, 0);
        assertEquals(2581172.5, s.quoteVolume, 0);
        assertEquals(1793944500000L, s.openTime);
        assertEquals(-0.0711, s.changePercent(75694.01), 0.0001);
    }

    @Test
    public void window_coverage_rejectsRecentlyListed() {
        long day = 24 * 3_600_000L;
        WindowStat full = new WindowStat(1, 1, 0, 0L, 7 * day - 1000);
        WindowStat young = new WindowStat(1, 2, 0, 5 * day, 7 * day); // 7g penceresinde yalnızca 2 gün işlem
        assertTrue(full.covers(Period.D7.durationMs()));
        assertFalse(young.covers(Period.D7.durationMs()));
    }

    @Test
    public void period_parseFallsBack_andListExcludes30d() {
        assertEquals(Period.H4, Period.parse("H4", Period.D1));
        assertEquals(Period.D1, Period.parse("bogus", Period.D1));
        assertEquals(Period.D1, Period.parse(null, Period.D1));
        assertFalse(Arrays.asList(Period.LIST).contains(Period.D30));
        assertEquals("7d", Period.D7.window);
    }

    @Test
    public void sparkline_startsAtFirstOpen_thenCloses() {
        Candle a = new Candle(1, 10, 12, 9, 11, 1);
        Candle b = new Candle(2, 11, 13, 10, 12.5, 1);
        assertArrayEquals(new float[]{10f, 11f, 12.5f}, Sparks.points(Arrays.asList(a, b)), 0f);
        assertNull(Sparks.points(Collections.singletonList(a))); // tek mumla sahte çizgi yok
    }
}
