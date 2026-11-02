package com.abraxel.cryptocurrency.alerts;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.abraxel.cryptocurrency.CoinDetailActivity;
import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.data.Binance;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.Format;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Alarmları ~15 dakikada bir (Android'in izin verdiği en kısa periyot) kontrol eder.
 * Uygulama kapalıyken de çalışır; tetiklenen alarm bildirim gönderip silinir.
 */
public class PriceAlertWorker extends Worker {
    private static final String WORK_NAME = "price-alerts";
    private static final String CHANNEL_ID = "price_alerts";

    public PriceAlertWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static void schedule(Context context, boolean hasAlerts) {
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

        Set<String> symbols = new HashSet<>();
        for (PriceAlert a : alerts) symbols.add(a.symbol);

        Map<String, Double> prices;
        try {
            prices = Binance.fetchPricesBlocking(symbols);
        } catch (IOException e) {
            return Result.retry();
        }

        for (PriceAlert alert : alerts) {
            Double price = prices.get(alert.symbol);
            if (price != null && alert.isTriggered(price)) {
                notify(context, alert, price);
                Store.removeAlert(context, alert);
            }
        }
        return Result.success();
    }

    private static void notify(Context context, PriceAlert alert, double price) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.alert_channel_name), NotificationManager.IMPORTANCE_HIGH);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        String pair = Coin.pairLabel(alert.symbol);
        String text = context.getString(alert.above ? R.string.alert_text_above : R.string.alert_text_below,
                Format.price(alert.target), Format.price(price));
        PendingIntent open = PendingIntent.getActivity(context, alert.symbol.hashCode(),
                CoinDetailActivity.intent(context, alert.symbol),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManagerCompat.from(context).notify(alert.hashCode(),
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_monetization_on)
                        .setContentTitle(context.getString(R.string.alert_title, pair))
                        .setContentText(text)
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build());
    }
}
