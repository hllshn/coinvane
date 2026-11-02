package com.abraxel.cryptocurrency;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.abraxel.cryptocurrency.data.Store;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

/**
 * Play uygulama içi puanlama. Uygulamayı gerçekten kullanan birine (en az 5 açılış, 3 gün ve bir favori/alarm/
 * portföy kaydı) bir kez sorulur. Pencereyi gösterip göstermemeye ayrıca Google'ın kendi kotası karar verir;
 * kullanıcı puan verse de vermese de uygulama bunu öğrenemez ve akış aynı devam eder.
 */
final class ReviewPrompt {
    static final int MIN_LAUNCHES = 5;
    static final long MIN_AGE_MS = 3L * 24 * 60 * 60 * 1000;
    private static final String PREFS = "review";

    private ReviewPrompt() {
    }

    static boolean shouldAsk(int launches, long firstLaunchMs, long nowMs, boolean asked, boolean engaged) {
        return !asked && engaged && launches >= MIN_LAUNCHES && nowMs - firstLaunchMs >= MIN_AGE_MS;
    }

    /** Uygulama her açıldığında (dönüşlerde değil) çağrılır; açılışı sayar. */
    static void countLaunch(Context context) {
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor e = p.edit().putInt("launches", p.getInt("launches", 0) + 1);
        if (p.getLong("first", 0) == 0) e.putLong("first", System.currentTimeMillis());
        e.apply();
    }

    /** Koşullar uygunsa puanlama penceresini ister. Ekran oturduktan sonra çağrılmalı. */
    static void maybeAsk(Activity activity) {
        SharedPreferences p = prefs(activity);
        boolean engaged = !Store.favorites(activity).isEmpty() || !Store.alerts(activity).isEmpty()
                || !Store.holdings(activity).isEmpty();
        if (!shouldAsk(p.getInt("launches", 0), p.getLong("first", 0), System.currentTimeMillis(),
                p.getBoolean("asked", false), engaged)) {
            return;
        }
        ReviewManager manager = ReviewManagerFactory.create(activity);
        manager.requestReviewFlow().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || activity.isFinishing() || activity.isDestroyed()) return;
            p.edit().putBoolean("asked", true).apply();
            manager.launchReviewFlow(activity, task.getResult());
        });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
