package com.abraxel.cryptocurrency;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.abraxel.cryptocurrency.data.CoinFilter;
import com.abraxel.cryptocurrency.data.Format;
import com.abraxel.cryptocurrency.data.Period;
import com.abraxel.cryptocurrency.ui.InitialsDrawable;
import com.bumptech.glide.Glide;

import java.util.Locale;

/** Ekranların paylaştığı küçük görünüm yardımcıları. */
public final class Ui {
    /** Değer bilinmiyor. */
    public static final String NO_VALUE = "—";

    private Ui() {
    }

    /** Edge-to-edge (API 35+ zorunlu): içerik sistem çubuklarının altında kalmasın. */
    public static void padForSystemBars(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    public static Locale locale(Context context) {
        return context.getResources().getConfiguration().getLocales().get(0);
    }

    public static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Coin logosu: önce Binance'in logo CDN'i (fiyatların kaynağı; yeni listelenenler dahil neredeyse tüm pariteler),
     * yoksa CoinCap, o da yoksa spothq ikon seti; hiçbiri yoksa baş harfli nötr yer tutucu.
     */
    public static void loadIcon(ImageView view, String base) {
        String lower = base.toLowerCase(Locale.ROOT);
        Context c = view.getContext();
        InitialsDrawable initials = new InitialsDrawable(base,
                ContextCompat.getColor(c, R.color.surface_card_high), ContextCompat.getColor(c, R.color.text_secondary));
        Glide.with(view)
                .load("https://bin.bnbstatic.com/static/assets/logos/" + android.net.Uri.encode(base.toUpperCase(Locale.ROOT)) + ".png")
                .placeholder(R.drawable.ic_coin_placeholder)
                .error(Glide.with(view)
                        .load("https://assets.coincap.io/assets/icons/" + lower + "@2x.png")
                        .error(Glide.with(view)
                                .load("https://cdn.jsdelivr.net/gh/spothq/cryptocurrency-icons@master/128/color/" + lower + ".png")
                                .error(initials)))
                .into(view);
    }

    /** Yüzde değişim etiketi: + yeşil, − kırmızı, 0 nötr gri; bilinmiyorsa "—". İşaret rengi tek başına bırakmaz. */
    public static void bindChange(TextView view, double percent) {
        if (Double.isNaN(percent)) {
            view.setText(NO_VALUE);
            tint(view, 0);
            return;
        }
        view.setText(Format.percent(percent, locale(view.getContext())));
        tint(view, CoinFilter.direction(percent));
    }

    public static void tint(TextView view, boolean up) {
        tint(view, up ? 1 : -1);
    }

    /** direction: +1 yeşil, -1 kırmızı, 0 nötr. */
    public static void tint(TextView view, int direction) {
        Context c = view.getContext();
        int text = direction > 0 ? R.color.up : direction < 0 ? R.color.down : R.color.neutral;
        int bg = direction > 0 ? R.color.up_container : direction < 0 ? R.color.down_container : R.color.neutral_container;
        view.setTextColor(ContextCompat.getColor(c, text));
        view.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, bg)));
    }

    /** Arka plansız renkli metin (kart içi değerler). */
    public static void color(TextView view, int direction) {
        int text = direction > 0 ? R.color.up : direction < 0 ? R.color.down : R.color.neutral;
        view.setTextColor(ContextCompat.getColor(view.getContext(), text));
    }

    /**
     * Sayı alanı: "numberDecimal" klavyesi korunur ama hem nokta hem virgül kabul edilir. Varsayılan dinleyici
     * yalnızca noktayı kabul ettiği için Türkçe/Almanca klavyede "0,05" sessizce "005" olurdu.
     */
    public static void decimalInput(android.widget.EditText input) {
        // setInputType dinleyiciyi değiştirdiği için önce o, sonra dinleyici
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
    }

    /** Dönemin kısa adı: 5dk, 15dk, 1s, 4s, 24s, 7g, 30g. */
    @StringRes
    public static int periodLabel(Period period) {
        switch (period) {
            case M5: return R.string.period_5m;
            case M15: return R.string.period_15m;
            case H1: return R.string.period_1h;
            case H4: return R.string.period_4h;
            case D7: return R.string.period_7d;
            case D30: return R.string.period_30d;
            default: return R.string.period_24h;
        }
    }
}
