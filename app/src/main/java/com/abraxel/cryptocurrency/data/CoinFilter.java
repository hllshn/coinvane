package com.abraxel.cryptocurrency.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Ana listedeki arama + sekme (Tümü / Favoriler / Yükselenler / Düşenler) mantığı. */
public final class CoinFilter {
    public enum Mode { ALL, FAVORITES, GAINERS, LOSERS }

    private CoinFilter() {
    }

    public static List<Coin> apply(Collection<Coin> coins, String query, Mode mode, Set<String> favorites) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Coin> result = new ArrayList<>();
        for (Coin c : coins) {
            if (mode == Mode.FAVORITES && !favorites.contains(c.symbol)) continue;
            if (!q.isEmpty() && !c.base.toLowerCase(Locale.ROOT).contains(q)
                    && !c.name().toLowerCase(Locale.ROOT).contains(q)) continue;
            result.add(c);
        }
        Comparator<Coin> byVolume = (a, b) -> Double.compare(b.quoteVolume, a.quoteVolume);
        Comparator<Coin> byChange = Comparator.comparingDouble(Coin::changePercent);
        if (mode == Mode.GAINERS) result.sort(byChange.reversed());
        else if (mode == Mode.LOSERS) result.sort(byChange);
        else result.sort(byVolume);
        return result;
    }
}
