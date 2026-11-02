package com.abraxel.cryptocurrency.alerts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.abraxel.cryptocurrency.data.Binance;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.Fx;
import com.abraxel.cryptocurrency.data.Store;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Uygulama kapalıyken alarmları ~15 dakikada bir (Android'in izin verdiği en kısa periyot) kontrol eder.
 * Uygulama açıkken alarmlar canlı akıştan anında tetiklenir ({@link Alerts#check}).
 */
public class PriceAlertWorker extends Worker {
    private static final String WORK_NAME = "price-alerts";

    public PriceAlertWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context, boolean hasAlerts) {
        WorkManager wm = WorkManager.getInstance(context);
        if (!hasAlerts) {
            wm.cancelUniqueWork(WORK_NAME);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(PriceAlertWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        List<PriceAlert> alerts = Store.alerts(context);
        if (alerts.isEmpty()) return Result.success();

        Set<String> symbols = new LinkedHashSet<>();
        for (PriceAlert a : alerts) symbols.add(a.symbol);
        try {
            Fx.refreshIfStaleBlocking(context);
            for (Coin coin : Binance.fetchCoinsBlocking(symbols)) Alerts.check(context, coin);
        } catch (IOException e) {
            return Result.retry();
        }
        return Result.success();
    }
}
