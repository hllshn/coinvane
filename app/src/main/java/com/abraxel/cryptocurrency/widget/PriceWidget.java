package com.abraxel.cryptocurrency.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.abraxel.cryptocurrency.MainActivity;
import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.data.Binance;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.Format;
import com.abraxel.cryptocurrency.data.Fx;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.data.Store;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Ana ekran widget'ı: favorilerden ilk 4 coin (favori yoksa BTC, ETH, SOL, BNB).
 * Sistem 30 dakikada bir, uygulama da favoriler değişince yeniler.
 */
public class PriceWidget extends AppWidgetProvider {
    private static final int ROWS = 4;
    private static final List<String> DEFAULTS = Arrays.asList("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT");
    private static final int[][] ROW_IDS = {
            {R.id.row1, R.id.base1, R.id.price1, R.id.change1},
            {R.id.row2, R.id.base2, R.id.price2, R.id.change2},
            {R.id.row3, R.id.base3, R.id.price3, R.id.change3},
            {R.id.row4, R.id.base4, R.id.price4, R.id.change4},
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        refresh(context);
    }

    /** Widget eklenmişse fiyatları arka planda yeniler. */
    public static void refresh(Context context) {
        int[] ids = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, PriceWidget.class));
        if (ids.length == 0) return;
        WorkManager.getInstance(context).enqueueUniqueWork("widget", ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(UpdateWorker.class)
                        .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build());
    }

    static List<String> symbols(Context context) {
        List<String> favorites = new ArrayList<>(Store.favorites(context));
        if (favorites.isEmpty()) return DEFAULTS;
        favorites.sort(null);
        return favorites.subList(0, Math.min(ROWS, favorites.size()));
    }

    public static class UpdateWorker extends Worker {
        public UpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            Context context = getApplicationContext();
            List<Coin> coins;
            try {
                Fx.refreshIfStaleBlocking(context);
                coins = Binance.fetchCoinsBlocking(symbols(context));
            } catch (IOException e) {
                return Result.retry();
            }
            RemoteViews views = render(context, coins);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            manager.updateAppWidget(new ComponentName(context, PriceWidget.class), views);
            return Result.success();
        }
    }

    private static RemoteViews render(Context context, List<Coin> coins) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_prices);
        Money money = Money.current(context);
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        for (int i = 0; i < ROWS; i++) {
            int[] ids = ROW_IDS[i];
            if (i >= coins.size()) {
                views.setViewVisibility(ids[0], View.GONE);
                continue;
            }
            Coin c = coins.get(i);
            views.setViewVisibility(ids[0], View.VISIBLE);
            views.setTextViewText(ids[1], c.base);
            views.setTextViewText(ids[2], money.price(c.last));
            views.setTextViewText(ids[3], Format.percent(c.changePercent(), locale));
            views.setTextColor(ids[3], ContextCompat.getColor(context, c.changePercent() >= 0 ? R.color.up : R.color.down));
        }
        views.setTextViewText(R.id.widget_time, DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(new Date()));
        PendingIntent open = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, open);
        return views;
    }
}
