package com.abraxel.cryptocurrency;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.abraxel.cryptocurrency.alerts.PriceAlert;
import com.abraxel.cryptocurrency.alerts.Store;
import com.abraxel.cryptocurrency.chart.CandleChart;
import com.abraxel.cryptocurrency.data.Binance;
import com.abraxel.cryptocurrency.data.Candle;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.Format;
import com.abraxel.cryptocurrency.data.PriceSocket;
import com.abraxel.cryptocurrency.databinding.ActivityCoinDetailBinding;
import com.abraxel.cryptocurrency.databinding.DialogAlertBinding;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;

/** Coin detayı: canlı fiyat, 24s istatistikleri, mum grafiği ve fiyat alarmları. */
public class CoinDetailActivity extends AppCompatActivity implements PriceSocket.Listener {
    private static final String EXTRA_SYMBOL = "symbol";

    private ActivityCoinDetailBinding binding;
    private CandleChart chart;
    private String symbol;
    private String interval = "1h";
    private Coin coin;
    private PriceSocket socket;
    private Call coinCall;
    private Call candleCall;

    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) Snackbar.make(binding.getRoot(), R.string.notifications_denied, Snackbar.LENGTH_LONG).show();
            });

    public static Intent intent(Context context, String symbol) {
        return new Intent(context, CoinDetailActivity.class).putExtra(EXTRA_SYMBOL, symbol);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        symbol = getIntent().getStringExtra(EXTRA_SYMBOL);
        if (symbol == null) {
            finish();
            return;
        }
        binding = ActivityCoinDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Ui.padForSystemBars(binding.getRoot());

        binding.toolbar.setTitle(Coin.pairLabel(symbol));
        binding.toolbar.setSubtitle(Coin.name(symbol));
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.inflateMenu(R.menu.detail);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            Store.toggleFavorite(this, symbol);
            bindFavorite();
            return true;
        });
        bindFavorite();
        Ui.loadIcon(binding.icon, new Coin(symbol, 0, 0, 0, 0, 0).base);

        chart = new CandleChart(binding.chart);
        binding.intervals.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            interval = intervalFor(id);
            loadCandles();
            restartSocket();
        });
        binding.setAlert.setOnClickListener(v -> showAlertDialog());

        coinCall = Binance.fetchCoin(symbol, new Binance.Result<>() {
            @Override
            public void onSuccess(Coin value) {
                if (coin == null) bindCoin(value); // canlı veri önce geldiyse onu ezme
            }

            @Override
            public void onError(Exception e) {
                // Canlı akış header'ı yine dolduracak; ayrıca hata göstermeye gerek yok
            }
        });
        loadCandles();
        renderAlerts();
    }

    @Override
    protected void onStart() {
        super.onStart();
        restartSocket();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopSocket();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (coinCall != null) coinCall.cancel();
        if (candleCall != null) candleCall.cancel();
    }

    private static String intervalFor(int buttonId) {
        if (buttonId == R.id.interval_15m) return "15m";
        if (buttonId == R.id.interval_4h) return "4h";
        if (buttonId == R.id.interval_1d) return "1d";
        if (buttonId == R.id.interval_1w) return "1w";
        return "1h";
    }

    private void loadCandles() {
        if (candleCall != null) candleCall.cancel();
        chart.showMessage(getString(R.string.chart_loading));
        String requested = interval;
        candleCall = Binance.fetchCandles(symbol, requested, new Binance.Result<>() {
            @Override
            public void onSuccess(List<Candle> candles) {
                if (!requested.equals(interval)) return; // bu arada aralık değişti
                if (candles.isEmpty()) chart.showMessage(getString(R.string.chart_error));
                else chart.setCandles(candles, requested);
            }

            @Override
            public void onError(Exception e) {
                if (requested.equals(interval)) chart.showMessage(getString(R.string.chart_error));
            }
        });
    }

    private void restartSocket() {
        stopSocket();
        String s = symbol.toLowerCase(Locale.ROOT);
        socket = new PriceSocket(Arrays.asList(s + "@miniTicker", s + "@kline_" + interval), this);
        socket.open();
    }

    private void stopSocket() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    @Override
    public void onMessage(String stream, JSONObject data) {
        try {
            if (stream.endsWith("@miniTicker")) bindCoin(Binance.parseMiniTicker(data));
            else if (stream.endsWith("@kline_" + interval)) chart.update(Binance.parseKlineEvent(data));
        } catch (JSONException ignored) {
            // Beklenmeyen mesaj biçimi: atla
        }
    }

    @Override
    public void onStatusChanged(boolean live) {
        // Detay ekranında ayrı bir durum göstergesi yok; yeniden bağlanma otomatik
    }

    private void bindCoin(Coin c) {
        coin = c;
        binding.price.setText(Format.price(c.last));
        Ui.bindChange(binding.change, c.changePercent());
        binding.high.setText(Format.price(c.high));
        binding.low.setText(Format.price(c.low));
        binding.volume.setText(Format.compactPrice(c.quoteVolume));
    }

    private void bindFavorite() {
        boolean favorite = Store.favorites(this).contains(symbol);
        binding.toolbar.getMenu().findItem(R.id.action_favorite)
                .setIcon(favorite ? R.drawable.ic_star : R.drawable.ic_star_outline);
    }

    // --- Fiyat alarmları ---

    private void renderAlerts() {
        binding.alerts.removeAllViews();
        List<PriceAlert> alerts = Store.alerts(this, symbol);
        binding.alertsTitle.setVisibility(alerts.isEmpty() ? View.GONE : View.VISIBLE);
        for (PriceAlert alert : alerts) {
            Chip chip = new Chip(this);
            chip.setText(getString(alert.above ? R.string.alert_chip_above : R.string.alert_chip_below,
                    Format.price(alert.target)));
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                Store.removeAlert(this, alert);
                renderAlerts();
                Snackbar.make(binding.getRoot(), R.string.alert_removed, Snackbar.LENGTH_SHORT).show();
            });
            binding.alerts.addView(chip);
        }
    }

    private void showAlertDialog() {
        DialogAlertBinding d = DialogAlertBinding.inflate(getLayoutInflater());
        if (coin != null) d.input.setText(Format.plain(coin.last));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.set_alert)
                .setView(d.getRoot())
                .setPositiveButton(R.string.alert_save, null) // geçersiz girdide kapanmasın
                .setNegativeButton(R.string.cancel, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double target = Format.parse(String.valueOf(d.input.getText()));
            if (coin == null || Double.isNaN(target) || target <= 0 || target == coin.last) {
                d.inputLayout.setError(getString(R.string.alert_invalid));
                return;
            }
            Store.addAlert(this, PriceAlert.create(symbol, target, coin.last));
            renderAlerts();
            dialog.dismiss();
            Snackbar.make(binding.getRoot(), R.string.alert_saved, Snackbar.LENGTH_SHORT).show();
            requestNotificationPermission();
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
}
