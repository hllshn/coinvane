package com.abraxel.cryptocurrency.data;

import android.content.Context;

import com.abraxel.cryptocurrency.R;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/**
 * USD tutarları kullanıcının para birimine çevirip dilinin kurallarıyla biçimlendirir:
 * en-US "$64,210.50", de-DE "59.871,12 €", ja-JP "￥10,115,033", tr-TR "₺3.842.059,00".
 */
public final class Money {
    public final String code;
    public final double rate;
    private final Locale locale;
    private final String[] compactSuffixes; // bin, milyon, milyar, trilyon

    public Money(String code, double rate, Locale locale, String[] compactSuffixes) {
        this.code = code;
        this.rate = rate;
        this.locale = locale;
        this.compactSuffixes = compactSuffixes;
    }

    /** Ayarlardaki para birimi; kuru henüz bilinmiyorsa USD. */
    public static Money current(Context context) {
        String code = Settings.currency(context);
        double rate = Fx.rate(context, code);
        if (Double.isNaN(rate)) {
            code = "USD";
            rate = 1;
        }
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return new Money(code, rate, locale, context.getResources().getStringArray(R.array.compact_suffixes));
    }

    public double convert(double usd) {
        return usd * rate;
    }

    /** USD tutarı kullanıcının para biriminde. */
    public String price(double usd) {
        return format(convert(usd));
    }

    /** Zaten kullanıcının para biriminde olan tutar. */
    public String format(double value) {
        NumberFormat f = currencyFormat();
        if (Math.abs(value) < 1 && value != 0) {
            // Küçük fiyatlarda (ör. PEPE, BTTC) en az 4 anlamlı hane kalsın; sıfıra yuvarlanmasın
            int leadingZeros = (int) Math.ceil(-Math.log10(Math.abs(value)));
            f.setMinimumFractionDigits(4);
            f.setMaximumFractionDigits(Math.min(12, Math.max(4, leadingZeros + 3)));
        }
        return f.format(value);
    }

    /** Eksende/kart başlığında kullanılan sembolsüz sayı. */
    public String number(double value) {
        NumberFormat f = NumberFormat.getNumberInstance(locale);
        int digits = Math.abs(value) >= 1000 ? 0 : Math.abs(value) >= 1 ? 2 : 6;
        f.setMinimumFractionDigits(Math.min(digits, 2));
        f.setMaximumFractionDigits(digits);
        return f.format(value);
    }

    /** Hacim gibi büyük tutarlar: "$337.35M", "337,35 Mn ₺" */
    public String compact(double usd) {
        double value = convert(usd);
        double abs = Math.abs(value);
        int unit = abs >= 1e12 ? 4 : abs >= 1e9 ? 3 : abs >= 1e6 ? 2 : abs >= 1e3 ? 1 : 0;
        if (unit == 0) return format(value);
        NumberFormat f = currencyFormat();
        f.setMinimumFractionDigits(0);
        f.setMaximumFractionDigits(2);
        String number = f.format(value / Math.pow(1000, unit));
        return withSuffix(number, compactSuffixes[unit - 1]);
    }

    public String signed(double value) {
        String s = format(Math.abs(value));
        return (value < 0 ? "−" : "+") + s;
    }

    private NumberFormat currencyFormat() {
        NumberFormat f = NumberFormat.getCurrencyInstance(locale);
        Currency currency = Currency.getInstance(code);
        f.setCurrency(currency);
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        f.setMinimumFractionDigits(digits);
        f.setMaximumFractionDigits(digits);
        if (f instanceof DecimalFormat) ((DecimalFormat) f).setRoundingMode(java.math.RoundingMode.HALF_UP);
        return f;
    }

    /** Son ekin rakamın hemen ardından gelmesi için sembol sonda ise ekten önce yerleştirilir. */
    private static String withSuffix(String formatted, String suffix) {
        int lastDigit = -1;
        for (int i = formatted.length() - 1; i >= 0; i--) {
            if (Character.isDigit(formatted.charAt(i))) {
                lastDigit = i;
                break;
            }
        }
        if (lastDigit < 0) return formatted + suffix;
        return formatted.substring(0, lastDigit + 1) + suffix + formatted.substring(lastDigit + 1);
    }
}
