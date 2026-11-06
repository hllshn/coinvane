package com.abraxel.cryptocurrency.ui;

import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.Ui;
import com.abraxel.cryptocurrency.data.Period;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/** Liste türü ve dönem seçicileri: tek seçimli, seçim zorunlu çip grupları. Değer çipin tag'inde durur. */
public final class Selectors {
    public interface OnSelect<T> {
        void onSelect(T value);
    }

    private Selectors() {
    }

    /** Liste türü çipi (Hacim, Favoriler...). */
    public static Chip addFilter(ChipGroup group, Object value, @StringRes int text, @DrawableRes int icon) {
        Chip chip = (Chip) LayoutInflater.from(group.getContext()).inflate(R.layout.chip_filter, group, false);
        chip.setId(View.generateViewId());
        chip.setTag(value);
        if (text != 0) chip.setText(text);
        if (icon != 0) chip.setChipIconResource(icon);
        chip.setChipIconVisible(icon != 0);
        group.addView(chip);
        return chip;
    }

    /** Dönem bandını (5dk | 15dk | 1s | 4s | 24s | 7g) doldurur. */
    public static void setupPeriods(ChipGroup group, Period[] periods) {
        group.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(group.getContext());
        for (Period p : periods) {
            Chip chip = (Chip) inflater.inflate(R.layout.chip_period, group, false);
            chip.setId(View.generateViewId());
            chip.setTag(p);
            chip.setText(Ui.periodLabel(p));
            group.addView(chip);
        }
    }

    /** value'ya karşılık gelen çipi seçer (dinleyiciyi tetiklemeden önce çağrılmalı). */
    public static void check(ChipGroup group, Object value) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (value.equals(v.getTag())) {
                group.check(v.getId());
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> void listen(ChipGroup group, OnSelect<T> listener) {
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            View v = g.findViewById(ids.get(0));
            if (v != null && v.getTag() != null) listener.onSelect((T) v.getTag());
        });
    }

    /** Seçili çipi yatay kaydırmada görünür yapar. */
    public static void reveal(ChipGroup group) {
        View checked = group.findViewById(group.getCheckedChipId());
        if (checked != null) checked.post(() -> checked.requestRectangleOnScreen(
                new android.graphics.Rect(0, 0, checked.getWidth(), checked.getHeight()), false));
    }
}
