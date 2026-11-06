package com.abraxel.cryptocurrency.ui;

import androidx.annotation.Nullable;

import com.abraxel.cryptocurrency.data.Coin;

import java.util.Arrays;
import java.util.Objects;

/** Listelerde (Ana Sayfa, Piyasalar, Keşfet) aynı satır bileşeninin verisi. */
public final class CoinRow {
    public final Coin coin;
    /** Seçili dönemdeki yüzde değişim; bilinmiyorsa NaN. */
    public final double change;
    public final boolean favorite;
    @Nullable
    public final float[] spark;
    /** Hacim satırının yerine gösterilecek açıklama (ör. Keşfet'te hacim oranı); null ise 24s hacim. */
    @Nullable
    public final String note;

    public CoinRow(Coin coin, double change, boolean favorite, @Nullable float[] spark, @Nullable String note) {
        this.coin = coin;
        this.change = change;
        this.favorite = favorite;
        this.spark = spark;
        this.note = note;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CoinRow)) return false;
        CoinRow r = (CoinRow) o;
        return coin.equals(r.coin) && Double.compare(change, r.change) == 0 && favorite == r.favorite
                && Arrays.equals(spark, r.spark) && Objects.equals(note, r.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coin, change, favorite, Arrays.hashCode(spark), note);
    }
}
