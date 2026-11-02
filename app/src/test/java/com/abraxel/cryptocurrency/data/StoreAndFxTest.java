package com.abraxel.cryptocurrency.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.abraxel.cryptocurrency.alerts.PriceAlert;

import org.json.JSONException;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class StoreAndFxTest {

    @Test
    public void alerts_roundTrip() {
        List<PriceAlert> alerts = Arrays.asList(
                new PriceAlert("BTCUSDT", 80_000, "USD", true),
                new PriceAlert("PEPEUSDT", 0.00041, "EUR", false));

        assertEquals(alerts, Store.decodeAlerts(Store.encode(alerts, PriceAlert::toJson)));
    }

    @Test
    public void alerts_fromV6AreMigratedToUsdtPairsKeepingTryTarget() {
        List<PriceAlert> alerts = Store.decodeAlerts(
                "[{\"symbol\":\"BTCTRY\",\"target\":4000000,\"above\":true},"
                        + "{\"symbol\":\"USDTTRY\",\"target\":50,\"above\":true}]"); // USDT/USDT anlamsız → atılır

        assertEquals(1, alerts.size());
        assertEquals(new PriceAlert("BTCUSDT", 4_000_000, "TRY", true), alerts.get(0));
    }

    @Test
    public void alerts_corruptDataGivesEmptyList() {
        assertTrue(Store.decodeAlerts("not json").isEmpty());
    }

    @Test
    public void favorites_fromV6AreMigrated() {
        Set<String> migrated = Store.migrateFavorites(new HashSet<>(Arrays.asList("BTCTRY", "ETHUSDT", "USDTTRY")));
        assertEquals(new HashSet<>(Arrays.asList("BTCUSDT", "ETHUSDT")), migrated);
    }

    @Test
    public void alert_direction() {
        PriceAlert up = PriceAlert.create("BTCUSDT", 80_000, "USD", 77_000);
        assertTrue(up.above);
        assertFalse(up.isTriggered(79_999));
        assertTrue(up.isTriggered(80_000));

        PriceAlert down = PriceAlert.create("BTCUSDT", 70_000, "USD", 77_000);
        assertFalse(down.above);
        assertTrue(down.isTriggered(69_000));
    }

    @Test
    public void fx_parsesRates() throws Exception {
        Map<String, Double> rates = Fx.parse("{\"result\":\"success\",\"base_code\":\"USD\","
                + "\"rates\":{\"USD\":1,\"TRY\":49.94,\"EUR\":0.896}}");
        assertEquals(49.94, rates.get("TRY"), 0);
        assertEquals(3, rates.size());
    }

    @Test(expected = JSONException.class)
    public void fx_rejectsErrorResponse() throws Exception {
        Fx.parse("{\"result\":\"error\",\"error-type\":\"unsupported-code\"}");
    }

    @Test
    public void defaultCurrency_followsRegion() {
        assertEquals("TRY", Settings.defaultCurrency(Locale.forLanguageTag("tr-TR")));
        assertEquals("EUR", Settings.defaultCurrency(Locale.GERMANY));
        assertEquals("USD", Settings.defaultCurrency(Locale.ENGLISH));       // bölgesiz
        assertEquals("USD", Settings.defaultCurrency(Locale.forLanguageTag("is-IS"))); // desteklenmeyen ISK
    }
}
