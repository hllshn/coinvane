package com.abraxel.cryptocurrency;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReviewPromptTest {
    private static final long DAY = 24 * 60 * 60 * 1000L;
    private static final long NOW = 100 * DAY;

    @Test
    public void asksEngagedUserAfterEnoughLaunchesAndDays() {
        assertTrue(ReviewPrompt.shouldAsk(5, NOW - 3 * DAY, NOW, false, true));
    }

    @Test
    public void doesNotAskTooEarly() {
        assertFalse(ReviewPrompt.shouldAsk(4, NOW - 10 * DAY, NOW, false, true)); // az açılış
        assertFalse(ReviewPrompt.shouldAsk(20, NOW - 2 * DAY, NOW, false, true)); // çok yeni
    }

    @Test
    public void doesNotAskPassiveUserOrTwice() {
        assertFalse(ReviewPrompt.shouldAsk(20, NOW - 10 * DAY, NOW, false, false)); // hiç favori/alarm/portföy yok
        assertFalse(ReviewPrompt.shouldAsk(20, NOW - 10 * DAY, NOW, true, true));  // zaten soruldu
    }
}
