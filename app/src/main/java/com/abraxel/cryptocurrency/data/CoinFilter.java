package com.abraxel.cryptocurrency.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Piyasa listelerinin saf mantığı (birim testli): liste türü (Hacim / Favoriler / Yükselenler / Düşenler),
 * arama, minimum hacim, kolon sıralaması, piyasa hareketi sayıları ve hacim hareketlenmesi.
 * Dönemsel değişimi bilinmeyen (NaN) coin, o dönemin sıralamasına yanlış yüzdeyle girmez.
 */
public final class CoinFilter {
    public enum Type { VOLUME, FAVORITES, GAINERS, LOSERS }

    public enum Sort { NATURAL, PRICE_DESC, PRICE_ASC, CHANGE_DESC, CHANGE_ASC }

    /** Seçili dönemdeki yüzde değişim; bilinmiyorsa NaN. */
    public interface Changes {
        double of(Coin coin);
    }

    /** Ekranda iki ondalığa yuvarlanınca 0,00 görünen değişim "değişmedi" sayılır. */
    public static final double FLAT = 0.005;

    private CoinFilter() {
    }

    /** +1 yükseliş, -1 düşüş, 0 değişmedi (gösterimle tutarlı); NaN için 0. */
    public static int direction(double change) {
        if (Double.isNaN(change) || Math.abs(change) < FLAT) return 0;
        return change > 0 ? 1 : -1;
    }

    public static List<Coin> apply(Collection<Coin> coins, String query, Type type, Set<String> favorites,
                                   Changes changes, Sort sort, double minVolumeUsd) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Coin> result = new ArrayList<>();
        for (Coin c : coins) {
            if (type == Type.FAVORITES && !favorites.contains(c.symbol)) continue;
            if (minVolumeUsd > 0 && c.quoteVolume < minVolumeUsd) continue;
            if (!q.isEmpty() && !c.base.toLowerCase(Locale.ROOT).contains(q)
                    && !c.name().toLowerCase(Locale.ROOT).contains(q)) continue;
            if (type == Type.GAINERS && direction(changes.of(c)) <= 0) continue;
            if (type == Type.LOSERS && direction(changes.of(c)) >= 0) continue;
            result.add(c);
        }
        result.sort(comparator(type, sort, changes));
        return result;
    }

    static Comparator<Coin> comparator(Type type, Sort sort, Changes changes) {
        Comparator<Coin> byVolume = (a, b) -> Double.compare(b.quoteVolume, a.quoteVolume);
        // Değişimi bilinmeyenler her iki yönde de sona
        Comparator<Coin> changeDesc = (a, b) -> compareKnownFirst(changes.of(a), changes.of(b), true);
        Comparator<Coin> changeAsc = (a, b) -> compareKnownFirst(changes.of(a), changes.of(b), false);
        Comparator<Coin> primary;
        switch (sort) {
            case PRICE_DESC: primary = (a, b) -> Double.compare(b.last, a.last); break;
            case PRICE_ASC: primary = (a, b) -> Double.compare(a.last, b.last); break;
            case CHANGE_DESC: primary = changeDesc; break;
            case CHANGE_ASC: primary = changeAsc; break;
            default:
                // Doğal sıralama: Yükselenler en çok kazanan, Düşenler en çok kaybeden önce; diğerleri hacme göre
                primary = type == Type.GAINERS ? changeDesc : type == Type.LOSERS ? changeAsc : byVolume;
        }
        return primary.thenComparing(byVolume).thenComparing(c -> c.symbol);
    }

    /** NaN'lar yönden bağımsız olarak her zaman sonda. */
    private static int compareKnownFirst(double a, double b, boolean descending) {
        boolean na = Double.isNaN(a), nb = Double.isNaN(b);
        if (na || nb) return na == nb ? 0 : na ? 1 : -1;
        return descending ? Double.compare(b, a) : Double.compare(a, b);
    }

    /** Piyasa hareketi: {yükselen, düşen, değişmeyen}. Değişimi hesaplanabilen coin yoksa null. */
    public static int[] movement(Collection<Coin> coins, Changes changes) {
        int up = 0, down = 0, flat = 0;
        for (Coin c : coins) {
            double change = changes.of(c);
            if (Double.isNaN(change)) continue;
            int d = direction(change);
            if (d > 0) up++;
            else if (d < 0) down++;
            else flat++;
        }
        return up + down + flat == 0 ? null : new int[]{up, down, flat};
    }

    /**
     * Hacim hareketlenmesi: son 1 saatlik hacmin, 24 saatlik hacmin saatlik ortalamasına oranı.
     * Çok düşük hacimli coinlerin gürültüsünü önlemek için 24s hacmi minVolumeUsd altındakiler katılmaz.
     */
    public interface HourVolume {
        /** Son 1 saatlik USD hacmi; bilinmiyorsa NaN. */
        double of(Coin coin);
    }

    public static double surgeRatio(Coin coin, double hourVolume) {
        if (Double.isNaN(hourVolume) || coin.quoteVolume <= 0) return Double.NaN;
        return hourVolume / (coin.quoteVolume / 24.0);
    }

    public static List<Coin> volumeSurge(Collection<Coin> coins, HourVolume hourVolume, double minVolumeUsd, int limit) {
        List<Coin> result = new ArrayList<>();
        for (Coin c : coins) {
            if (c.quoteVolume < minVolumeUsd) continue;
            double ratio = surgeRatio(c, hourVolume.of(c));
            if (!Double.isNaN(ratio) && ratio > 1) result.add(c);
        }
        result.sort((a, b) -> Double.compare(surgeRatio(b, hourVolume.of(b)), surgeRatio(a, hourVolume.of(a))));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }
}
