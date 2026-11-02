package com.abraxel.cryptocurrency.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

public class FormatTest {

    @Test
    public void percent_followsLocaleAndAlwaysShowsSign() {
        assertEquals("+1.38%", Format.percent(1.381, Locale.US));
        assertEquals("-0,50%", Format.percent(-0.5, Locale.forLanguageTag("tr-TR")));
        assertEquals("+0.00%", Format.percent(0, Locale.US));
    }

    @Test
    public void plain_isEditableNumber() {
        assertEquals("3842059", Format.plain(3842059.0));
        assertEquals("3842059.12", Format.plain(3842059.1234));
        assertEquals("0.00041", Format.plain(0.00041));
    }

    @Test
    public void parse_acceptsInputFromAnyLocale() {
        assertEquals(4_000_000.5, Format.parse("4.000.000,5"), 0);   // tr, de
        assertEquals(4_000_000.5, Format.parse("4,000,000.5"), 0);   // en
        assertEquals(4_000_000, Format.parse("4.000.000"), 0);
        assertEquals(4_000_000, Format.parse("4,000,000"), 0);
        assertEquals(4_000_000.5, Format.parse("4000000.5"), 0);
        assertEquals(4_000_000.5, Format.parse("4000000,5"), 0);
        assertEquals(12.5, Format.parse(" 12,5 € "), 0);
        assertEquals(0.00041, Format.parse("0,00041"), 0);
        assertTrue(Double.isNaN(Format.parse("abc")));
        assertTrue(Double.isNaN(Format.parse("")));
    }
}
