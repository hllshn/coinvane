package com.abraxel.cryptocurrency.alerts;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.abraxel.cryptocurrency.CoinDetailActivity;
import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.Fx;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.data.Store;

import java.util.Locale;

/** Alarm tetikleme: hem arka plan işçisi hem de uygulama açıkken canlı akış buradan geçer. */
public final class Alerts {
    private static final String CHANNEL_ID = "price_alerts";

    private Alerts() {
    }

    /** Canlı fiyat geldiğinde çağrılır; tetiklenen alarmları bildirir ve siler. @return en az biri tetiklendiyse true */
    public static boolean check(Context context, Coin coin) {
        boolean fired = false;
        for (PriceAlert alert : Store.alerts(context, coin.symbol)) {
            double rate = Fx.rate(context, alert.currency);
            if (Double.isNaN(rate)) continue;
            double price = coin.last * rate;
            if (alert.isTriggered(price) && Store.removeAlert(context, alert)) {
                notify(context, alert, price);
                fired = true;
            }
        }
        return fired;
    }

    private static void notify(Context context, PriceAlert alert, double price) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.alert_channel_name), NotificationManager.IMPORTANCE_HIGH));

        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        Money money = new Money(alert.currency, 1, locale, context.getResources().getStringArray(R.array.compact_suffixes));
        String text = context.getString(alert.above ? R.string.alert_text_above : R.string.alert_text_below,
                money.format(alert.target), money.format(price));
        PendingIntent open = PendingIntent.getActivity(context, alert.symbol.hashCode(),
                CoinDetailActivity.intent(context, alert.symbol),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManagerCompat.from(context).notify(alert.hashCode(),
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_coinvane)
                        .setContentTitle(context.getString(R.string.alert_title, Coin.base(alert.symbol)))
                        .setContentText(text)
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build());
    }
}
