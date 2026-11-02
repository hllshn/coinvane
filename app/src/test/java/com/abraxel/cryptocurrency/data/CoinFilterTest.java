package com.abraxel.cryptocurrency.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CoinFilterTest {
    // sembol, son, açılış (değişim %), hacim
    private final Coin btc = new Coin("BTCTRY", 110, 100, 0, 0, 1000); // +10%
    private final Coin eth = new Coin("ETHTRY", 95, 100, 0, 0, 500);   // -5%
    private final Coin sol = new Coin("SOLTRY", 102, 100, 0, 0, 800);  // +2%
    private final List<Coin> all = Arrays.asList(eth, sol, btc);
    private final Set<String> none = Collections.emptySet();

    private static List<String> symbols(List<Coin> coins) {
        String[] s = new String[coins.size()];
        for (int i = 0; i < s.length; i++) s[i] = coins.get(i).base;
        return Arrays.asList(s);
    }

    @Test
    public void all_sortsByVolume() {
        assertEquals(Arrays.asList("BTC", "SOL", "ETH"), symbols(CoinFilter.apply(all, "", CoinFilter.Mode.ALL, none)));
    }

    @Test
    public void gainersAndLosers_sortByChange() {
        assertEquals(Arrays.asList("BTC", "SOL", "ETH"), symbols(CoinFilter.apply(all, "", CoinFilter.Mode.GAINERS, none)));
        assertEquals(Arrays.asList("ETH", "SOL", "BTC"), symbols(CoinFilter.apply(all, "", CoinFilter.Mode.LOSERS, none)));
    }

    @Test
    public void favorites_onlyFavorites() {
        Set<String> favorites = new HashSet<>(Arrays.asList("ETHTRY", "SOLTRY"));
        assertEquals(Arrays.asList("SOL", "ETH"), symbols(CoinFilter.apply(all, null, CoinFilter.Mode.FAVORITES, favorites)));
        assertEquals(Collections.emptyList(), symbols(CoinFilter.apply(all, "", CoinFilter.Mode.FAVORITES, none)));
    }

    @Test
    public void query_matchesSymbolOrName_caseInsensitive() {
        assertEquals(Collections.singletonList("BTC"), symbols(CoinFilter.apply(all, "  bitc ", CoinFilter.Mode.ALL, none)));
        assertEquals(Collections.singletonList("ETH"), symbols(CoinFilter.apply(all, "ETH", CoinFilter.Mode.ALL, none)));
        assertEquals(Collections.emptyList(), symbols(CoinFilter.apply(all, "doge", CoinFilter.Mode.ALL, none)));
    }
}
