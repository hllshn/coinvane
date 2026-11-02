package com.abraxel.cryptocurrency.data;

import java.math.BigDecimal;
import java.util.Locale;

/** Para biriminden bağımsız biçimlendirme ve kullanıcı girdisi ayrıştırma. */
public final class Format {
    private Format() {
    }

    public static String percent(double value, Locale locale) {
        return String.format(locale, "%+.2f%%", value);
    }

    /** Kullanıcının düzenleyebileceği sade sayı: gruplama yok, ondalık ayırıcı nokta. */
    public static String plain(double value) {
        BigDecimal d = BigDecimal.valueOf(value);
        d = d.setScale(Math.abs(value) >= 1 ? 2 : 8, java.math.RoundingMode.HALF_UP);
        return d.stripTrailingZeros().toPlainString();
    }

    /**
     * Dil fark etmeksizin yaygın girdileri kabul eder: "4,000,000.5", "4.000.000,5", "4000000,5", "4.000.000".
     * Son ayırıcı ondalık kabul edilir; aynı ayırıcı birden çok kez geçiyorsa binlik ayırıcıdır. Geçersizse NaN.
     */
    public static double parse(String text) {
        String s = text.trim().replaceAll("[^0-9.,\\-]", "");
        int dot = s.lastIndexOf('.');
        int comma = s.lastIndexOf(',');
        if (dot >= 0 && comma >= 0) {
            char decimal = dot > comma ? '.' : ',';
            char group = decimal == '.' ? ',' : '.';
            s = s.replace(String.valueOf(group), "").replace(decimal, '.');
        } else if (comma >= 0) {
            s = s.indexOf(',') != comma ? s.replace(",", "") : s.replace(',', '.');
        } else if (dot >= 0 && s.indexOf('.') != dot) {
            s = s.replace(".", "");
        }
        try {
            return s.isEmpty() ? Double.NaN : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
