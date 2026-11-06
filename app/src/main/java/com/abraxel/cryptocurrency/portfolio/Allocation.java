package com.abraxel.cryptocurrency.portfolio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Portföy dağılımı (saf, birim testli): değere göre büyükten küçüğe pay; küçük paylar "Diğer"de toplanır. */
public final class Allocation {
    public static final String OTHER = "";

    public static final class Slice {
        /** Sembol; birleşik dilim için {@link #OTHER}. */
        public final String symbol;
        public final double value;
        public final double percent;

        Slice(String symbol, double value, double percent) {
            this.symbol = symbol;
            this.value = value;
            this.percent = percent;
        }
    }

    private Allocation() {
    }

    /** prices: sembol → USD fiyat; fiyatı bilinmeyen pozisyon dağılıma katılmaz. En fazla maxSlices dilim. */
    public static List<Slice> of(Collection<Holding> holdings, Map<String, Double> prices, int maxSlices) {
        List<Slice> slices = new ArrayList<>();
        double total = 0;
        for (Holding h : holdings) {
            Double price = prices.get(h.symbol);
            if (price == null) continue;
            double value = h.value(price);
            if (value <= 0) continue;
            total += value;
            slices.add(new Slice(h.symbol, value, 0));
        }
        if (total <= 0) return new ArrayList<>();
        slices.sort((a, b) -> Double.compare(b.value, a.value));
        List<Slice> result = new ArrayList<>();
        double other = 0;
        for (int i = 0; i < slices.size(); i++) {
            Slice s = slices.get(i);
            boolean fits = slices.size() <= maxSlices || i < maxSlices - 1;
            if (fits) result.add(new Slice(s.symbol, s.value, s.value / total * 100));
            else other += s.value;
        }
        if (other > 0) result.add(new Slice(OTHER, other, other / total * 100));
        return result;
    }
}
