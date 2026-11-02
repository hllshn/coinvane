package com.abraxel.cryptocurrency.data;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Türkçe biçimlendirme: 3.842.059,00 ₺ · +1,38% · 337,35 Mn ₺ */
public final class Format {
    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(TR);

    private Format() {
    }

    public static String price(double value) {
        return number(value) + " ₺";
    }

    /** ≥1 için 2 hane; küçük fiyatlarda (ör. PEPE) anlamlı haneler kaybolmasın diye 8 haneye kadar. */
    public static String number(double value) {
        String pattern = Math.abs(value) >= 1 ? "#,##0.00" : "#,##0.0000####";
        return new DecimalFormat(pattern, SYMBOLS).format(value);
    }

    public static String percent(double value) {
        return String.format(TR, "%+.2f%%", value);
    }

    public static String compactPrice(double value) {
        double abs = Math.abs(value);
        if (abs >= 1e9) return compact(value / 1e9) + " Mr ₺";
        if (abs >= 1e6) return compact(value / 1e6) + " Mn ₺";
        if (abs >= 1e3) return compact(value / 1e3) + " Bin ₺";
        return compact(value) + " ₺";
    }

    /** Kullanıcının düzenleyebileceği sade sayı: gruplama yok, ondalık ayırıcı nokta. */
    public static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** "4.000.000,5", "4.000.000", "4000000.5", "4000000,5" girdilerini kabul eder. Geçersizse NaN. */
    public static double parse(String text) {
        String s = text.trim().replace(" ", "").replace("₺", "");
        if (s.contains(",")) s = s.replace(".", "").replace(',', '.');
        else if (s.indexOf('.') != s.lastIndexOf('.')) s = s.replace(".", ""); // 4.000.000
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String compact(double value) {
        return new DecimalFormat("#,##0.##", SYMBOLS).format(value);
    }
}
