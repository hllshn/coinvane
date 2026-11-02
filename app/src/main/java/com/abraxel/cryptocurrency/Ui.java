package com.abraxel.cryptocurrency;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.abraxel.cryptocurrency.data.Format;
import com.bumptech.glide.Glide;

import java.util.Locale;

/** Ekranların paylaştığı küçük görünüm yardımcıları. */
public final class Ui {
    private Ui() {
    }

    /** Edge-to-edge (API 35+ zorunlu): içerik sistem çubuklarının altında kalmasın. */
    public static void padForSystemBars(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    public static Locale locale(Context context) {
        return context.getResources().getConfiguration().getLocales().get(0);
    }

    /** Coin logosu: CoinCap CDN, yoksa spothq ikon seti, o da yoksa nötr yer tutucu. */
    public static void loadIcon(ImageView view, String base) {
        String b = base.toLowerCase(Locale.ROOT);
        Glide.with(view)
                .load("https://assets.coincap.io/assets/icons/" + b + "@2x.png")
                .placeholder(R.drawable.ic_coin_placeholder)
                .error(Glide.with(view)
                        .load("https://cdn.jsdelivr.net/gh/spothq/cryptocurrency-icons@master/128/color/" + b + ".png")
                        .error(R.drawable.ic_coin_placeholder))
                .into(view);
    }

    /** Yüzde değişim etiketi: yeşil/kırmızı zemin. */
    public static void bindChange(TextView view, double percent) {
        view.setText(Format.percent(percent, locale(view.getContext())));
        tint(view, percent >= 0);
    }

    public static void tint(TextView view, boolean up) {
        Context c = view.getContext();
        view.setTextColor(ContextCompat.getColor(c, up ? R.color.up : R.color.down));
        view.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, up ? R.color.up_container : R.color.down_container)));
    }
}
