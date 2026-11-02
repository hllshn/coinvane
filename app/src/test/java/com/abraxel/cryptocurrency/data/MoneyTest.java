package com.abraxel.cryptocurrency.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

/** Not: JVM, JDK'nın CLDR verisini kullanır; Android ICU ile boşluk karakterleri küçük farklılık gösterebilir. */
public class MoneyTest {
    private static final String[] EN = {"K", "M", "B", "T"};
    private static final String[] TR = {" Bin", " Mn", " Mr", " Tn"};

    private static Money money(String code, double rate, String tag, String[] suffixes) {
        return new Money(code, rate, Locale.forLanguageTag(tag), suffixes);
    }

    /** Dar boşlukları (NBSP, NNBSP) normal boşluğa çevirir. */
    private static String norm(String s) {
        return s.replace(' ', ' ').replace(' ', ' ');
    }

    @Test
    public void usd_inEnglish() {
        Money usd = money("USD", 1, "en-US", EN);
        assertEquals("$77,123.45", usd.price(77123.45));
        assertEquals("$0.00041235", usd.price(0.00041235));
        assertEquals("$0.5000", usd.price(0.5));
    }

    @Test
    public void convertsWithRate_andUsesLocaleRules() {
        Money tryInTurkish = money("TRY", 49.94, "tr-TR", TR);
        assertEquals("₺3.851.545,09", norm(tryInTurkish.price(77123.45)));

        Money eurInGerman = money("EUR", 0.9, "de-DE", EN);
        assertEquals("90.000,00 €", norm(eurInGerman.price(100_000)));
    }

    @Test
    public void currenciesWithoutDecimals() {
        Money jpy = money("JPY", 157.53, "ja-JP", EN);
        String s = jpy.price(77123.45); // 12149257.07 → kuruş yok
        assertTrue(s, s.endsWith("12,149,257"));
    }

    @Test
    public void compact_putsUnitRightAfterTheNumber() {
        assertEquals("$1.2B", money("USD", 1, "en-US", EN).compact(1_200_000_000));
        assertEquals("$950.00", money("USD", 1, "en-US", EN).compact(950));
        assertEquals("₺337,35 Mn", norm(money("TRY", 1, "tr-TR", TR).compact(337_347_794.8)));
        assertEquals("12,5 Bin €", norm(money("EUR", 1, "de-DE", TR).compact(12_500)));
    }

    @Test
    public void signed_alwaysShowsDirection() {
        Money usd = money("USD", 1, "en-US", EN);
        assertEquals("+$12.00", usd.signed(12));
        assertEquals("−$3.50", usd.signed(-3.5));
    }
}
