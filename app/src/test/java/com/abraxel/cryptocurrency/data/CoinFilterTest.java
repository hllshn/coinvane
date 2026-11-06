package com.abraxel.cryptocurrency.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CoinFilterTest {
    // sembol, son, açılış (24s değişim %), yüksek, düşük, hacim
    private final Coin btc = new Coin("BTCUSDT", 110, 100, 0, 0, 1000);  // +10%
    private final Coin eth = new Coin("ETHUSDT", 95, 100, 0, 0, 500);    // -5%
    private final Coin sol = new Coin("SOLUSDT", 102, 100, 0, 0, 800);   // +2%
    private final Coin usdc = new Coin("USDCUSDT", 1, 1, 0, 0, 900);     // 0%
    private final List<Coin> all = Arrays.asList(eth, sol, btc, usdc);
    private final Set<String> none = Collections.emptySet();
    private final CoinFilter.Changes day = Coin::changePercent;

    private static List<String> symbols(List<Coin> coins) {
        String[] s = new String[coins.size()];
        for (int i = 0; i < s.length; i++) s[i] = coins.get(i).base;
        return Arrays.asList(s);
    }

    private List<String> apply(CoinFilter.Type type, CoinFilter.Changes changes, CoinFilter.Sort sort) {
        return symbols(CoinFilter.apply(all, "", type, none, changes, sort, 0));
    }

    @Test
    public void volume_sortsByVolume() {
        assertEquals(Arrays.asList("BTC", "USDC", "SOL", "ETH"), apply(CoinFilter.Type.VOLUME, day, CoinFilter.Sort.NATURAL));
    }

    @Test
    public void gainers_onlyPositive_highestFirst_losers_onlyNegative_mostLossFirst() {
        assertEquals(Arrays.asList("BTC", "SOL"), apply(CoinFilter.Type.GAINERS, day, CoinFilter.Sort.NATURAL));
        assertEquals(Collections.singletonList("ETH"), apply(CoinFilter.Type.LOSERS, day, CoinFilter.Sort.NATURAL));
    }

    @Test
    public void periodChange_drivesRanking_andUnknownChangeIsExcluded() {
        // 1 saatlik dönemde ETH en çok yükselen; SOL'ün dönem verisi yok (NaN) → sıralamaya girmez
        Map<String, Double> hour = new HashMap<>();
        hour.put("BTCUSDT", 0.5);
        hour.put("ETHUSDT", 3.0);
        hour.put("USDCUSDT", -0.2);
        CoinFilter.Changes changes = c -> hour.containsKey(c.symbol) ? hour.get(c.symbol) : Double.NaN;
        assertEquals(Arrays.asList("ETH", "BTC"), apply(CoinFilter.Type.GAINERS, changes, CoinFilter.Sort.NATURAL));
        assertEquals(Collections.singletonList("USDC"), apply(CoinFilter.Type.LOSERS, changes, CoinFilter.Sort.NATURAL));
        // Hacim listesinde değişim sıralaması: bilinmeyen sona
        assertEquals(Arrays.asList("ETH", "BTC", "USDC", "SOL"), apply(CoinFilter.Type.VOLUME, changes, CoinFilter.Sort.CHANGE_DESC));
        assertEquals(Arrays.asList("USDC", "BTC", "ETH", "SOL"), apply(CoinFilter.Type.VOLUME, changes, CoinFilter.Sort.CHANGE_ASC));
    }

    @Test
    public void columnSort_overridesNaturalOrder() {
        assertEquals(Arrays.asList("BTC", "SOL", "ETH", "USDC"), apply(CoinFilter.Type.VOLUME, day, CoinFilter.Sort.PRICE_DESC));
        assertEquals(Arrays.asList("USDC", "ETH", "SOL", "BTC"), apply(CoinFilter.Type.VOLUME, day, CoinFilter.Sort.PRICE_ASC));
        assertEquals(Arrays.asList("SOL", "BTC"), apply(CoinFilter.Type.GAINERS, day, CoinFilter.Sort.CHANGE_ASC));
    }

    @Test
    public void favorites_onlyFavorites() {
        Set<String> favorites = new HashSet<>(Arrays.asList("ETHUSDT", "SOLUSDT"));
        assertEquals(Arrays.asList("SOL", "ETH"), symbols(CoinFilter.apply(all, null, CoinFilter.Type.FAVORITES, favorites, day,
                CoinFilter.Sort.NATURAL, 0)));
        assertEquals(Collections.emptyList(), apply(CoinFilter.Type.FAVORITES, day, CoinFilter.Sort.NATURAL));
    }

    @Test
    public void query_matchesSymbolOrName_caseInsensitive() {
        assertEquals(Collections.singletonList("BTC"), symbols(CoinFilter.apply(all, "  bitc ", CoinFilter.Type.VOLUME, none, day,
                CoinFilter.Sort.NATURAL, 0)));
        assertEquals(Collections.emptyList(), symbols(CoinFilter.apply(all, "doge", CoinFilter.Type.VOLUME, none, day,
                CoinFilter.Sort.NATURAL, 0)));
    }

    @Test
    public void minVolume_filtersLowVolume() {
        assertEquals(Arrays.asList("BTC", "USDC"), symbols(CoinFilter.apply(all, "", CoinFilter.Type.VOLUME, none, day,
                CoinFilter.Sort.NATURAL, 850)));
    }

    @Test
    public void direction_matchesTwoDecimalDisplay() {
        assertEquals(0, CoinFilter.direction(0.004));
        assertEquals(0, CoinFilter.direction(-0.004));
        assertEquals(1, CoinFilter.direction(0.005));
        assertEquals(-1, CoinFilter.direction(-0.01));
        assertEquals(0, CoinFilter.direction(Double.NaN));
    }

    @Test
    public void movement_countsRisingFallingFlat_ignoringUnknown() {
        assertArrayEquals(new int[]{2, 1, 1}, CoinFilter.movement(all, day));
        assertArrayEquals(new int[]{1, 0, 0}, CoinFilter.movement(all, c -> c == btc ? 1.0 : Double.NaN));
        assertNull(CoinFilter.movement(all, c -> Double.NaN)); // hesaplanamıyorsa sahte 0'lar değil
    }

    @Test
    public void volumeSurge_ranksByHourVsAverage_andSkipsIlliquid() {
        Map<String, Double> hour = new HashMap<>();
        hour.put("BTCUSDT", 1000 / 24.0 * 3);   // ortalamanın 3 katı
        hour.put("ETHUSDT", 500 / 24.0 * 5);    // 5 katı, ama min hacim altında
        hour.put("SOLUSDT", 800 / 24.0 * 1.5);  // 1,5 katı
        hour.put("USDCUSDT", 900 / 24.0 * 0.5); // ortalamanın altında → yok
        List<Coin> surge = CoinFilter.volumeSurge(all, c -> hour.getOrDefault(c.symbol, Double.NaN), 600, 5);
        assertEquals(Arrays.asList("BTC", "SOL"), symbols(surge));
        assertEquals(3.0, CoinFilter.surgeRatio(btc, hour.get("BTCUSDT")), 1e-9);
        assertTrue(Double.isNaN(CoinFilter.surgeRatio(btc, Double.NaN)));
    }
}
