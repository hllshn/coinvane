package com.abraxel.cryptocurrency.alerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PriceAlertTest {

    @Test
    public void targetAboveCurrent_firesWhenPriceRisesToTarget() {
        PriceAlert alert = PriceAlert.create("BTCTRY", 4_000_000, 3_800_000);

        assertTrue(alert.above);
        assertFalse(alert.isTriggered(3_999_999));
        assertTrue(alert.isTriggered(4_000_000));
        assertTrue(alert.isTriggered(4_100_000));
    }

    @Test
    public void targetBelowCurrent_firesWhenPriceDropsToTarget() {
        PriceAlert alert = PriceAlert.create("BTCTRY", 3_500_000, 3_800_000);

        assertFalse(alert.above);
        assertFalse(alert.isTriggered(3_500_001));
        assertTrue(alert.isTriggered(3_500_000));
        assertTrue(alert.isTriggered(3_000_000));
    }

    @Test
    public void storeEncoding_roundTrips() {
        List<PriceAlert> alerts = Arrays.asList(
                new PriceAlert("BTCTRY", 4_000_000, true),
                new PriceAlert("PEPETRY", 0.00041, false));

        assertEquals(alerts, Store.decode(Store.encode(alerts)));
    }

    @Test
    public void storeDecoding_corruptDataGivesEmptyList() {
        assertTrue(Store.decode("not json").isEmpty());
    }
}
