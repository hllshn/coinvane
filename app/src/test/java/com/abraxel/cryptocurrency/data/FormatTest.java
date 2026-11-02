package com.abraxel.cryptocurrency.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FormatTest {

    @Test
    public void price_usesTurkishSeparators() {
        assertEquals("3.842.059,00 ₺", Format.price(3842059));
        assertEquals("12,50 ₺", Format.price(12.5));
    }

    @Test
    public void price_keepsSignificantDigitsForTinyPrices() {
        assertEquals("0,00041235", Format.number(0.00041235));
        assertEquals("0,5000", Format.number(0.5));
    }

    @Test
    public void percent_alwaysShowsSign() {
        assertEquals("+1,38%", Format.percent(1.381));
        assertEquals("-0,50%", Format.percent(-0.5));
        assertEquals("+0,00%", Format.percent(0));
    }

    @Test
    public void compactPrice_usesTurkishUnits() {
        assertEquals("337,35 Mn ₺", Format.compactPrice(337_347_794.8));
        assertEquals("1,2 Mr ₺", Format.compactPrice(1_200_000_000));
        assertEquals("950 ₺", Format.compactPrice(950));
        assertEquals("12,5 Bin ₺", Format.compactPrice(12_500));
    }

    @Test
    public void plain_isEditableNumber() {
        assertEquals("3842059", Format.plain(3842059.0));
        assertEquals("0.00041", Format.plain(0.00041));
    }

    @Test
    public void parse_acceptsCommonInputs() {
        assertEquals(4_000_000.5, Format.parse("4.000.000,5"), 0);
        assertEquals(4_000_000, Format.parse("4.000.000"), 0);
        assertEquals(4_000_000.5, Format.parse("4000000.5"), 0);
        assertEquals(4_000_000.5, Format.parse("4000000,5"), 0);
        assertEquals(12.5, Format.parse(" 12,5 ₺ "), 0);
        assertTrue(Double.isNaN(Format.parse("abc")));
        assertTrue(Double.isNaN(Format.parse("")));
    }
}
