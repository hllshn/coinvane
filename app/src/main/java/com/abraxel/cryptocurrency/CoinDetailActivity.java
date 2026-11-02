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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.abraxel.cryptocurrency.alerts.Alerts;
import com.abraxel.cryptocurrency.alerts.PriceAlert;
import com.abraxel.cryptocurrency.chart.CandleChart;
import com.abraxel.cryptocurrency.data.Binance;
import com.abraxel.cryptocurrency.data.Candle;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.Format;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.data.PriceSocket;
import com.abraxel.cryptocurrency.data.Store;
import com.abraxel.cryptocurrency.databinding.ActivityCoinDetailBinding;
import com.abraxel.cryptocurrency.databinding.DialogAlertBinding;
import com.abraxel.cryptocurrency.databinding.DialogHoldingBinding;
import com.abraxel.cryptocurrency.portfolio.Holding;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;

/** Coin detayı: canlı fiyat, 24s istatistikleri, grafik, portföy pozisyonu ve fiyat alarmları. */
public class CoinDetailActivity extends AppCompatActivity implements PriceSocket.Listener {
    private static final String EXTRA_SYMBOL = "symbol";
    private static final String STATE_INTERVAL = "interval";
    private static final String STATE_LINE = "line";

    private ActivityCoinDetailBinding binding;
    private CandleChart chart;
    private Money money;
    private String symbol;
    private String interval = "1h";
    private Coin coin;
    private final PriceSocket socket = new PriceSocket(this);
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
        if (savedInstanceState != null) interval = savedInstanceState.getString(STATE_INTERVAL, interval);

        binding = ActivityCoinDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Ui.padForSystemBars(binding.getRoot());
        money = Money.current(this);

        binding.toolbar.setTitle(Coin.name(symbol));
        binding.toolbar.setSubtitle(Coin.base(symbol));
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.inflateMenu(R.menu.detail);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            Store.toggleFavorite(this, symbol);
            bindFavorite();
            return true;
        });
        bindFavorite();
        Ui.loadIcon(binding.icon, Coin.base(symbol));

        chart = new CandleChart(binding.chart, money);
        chart.setLineMode(savedInstanceState != null && savedInstanceState.getBoolean(STATE_LINE));
        bindChartType();
        binding.chartType.setOnClickListener(v -> {
            chart.setLineMode(!chart.isLineMode());
            bindChartType();
        });
        binding.intervals.check(buttonFor(interval));
        binding.intervals.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            interval = intervalFor(id);
            loadCandles();
            updateStreams();
        });
        binding.setAlert.setOnClickListener(v -> showAlertDialog());
        binding.editHolding.setOnClickListener(v -> showHoldingDialog());

        coinCall = Binance.fetchCoins(Collections.singletonList(symbol), new Binance.Result<>() {
            @Override
            public void onSuccess(List<Coin> value) {
                if (coin == null && !value.isEmpty()) bindCoin(value.get(0)); // canlı veri önce geldiyse ezme
            }

            @Override
            public void onError(Exception e) {
                // Canlı akış başlığı yine dolduracak
            }
        });
        loadCandles();
        renderAlerts();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_INTERVAL, interval);
        outState.putBoolean(STATE_LINE, chart.isLineMode());
    }

    @Override
    protected void onStart() {
        super.onStart();
        socket.open();
        updateStreams();
    }

    @Override
    protected void onStop() {
        super.onStop();
        socket.close();
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

    private static int buttonFor(String interval) {
        switch (interval) {
            case "15m": return R.id.interval_15m;
            case "4h": return R.id.interval_4h;
            case "1d": return R.id.interval_1d;
            case "1w": return R.id.interval_1w;
            default: return R.id.interval_1h;
        }
    }

    private void bindChartType() {
        boolean line = chart.isLineMode();
        binding.chartType.setIconResource(line ? R.drawable.ic_chart_candle : R.drawable.ic_chart_line);
        binding.chartType.setContentDescription(getString(line ? R.string.chart_show_candles : R.string.chart_show_line));
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

    private void updateStreams() {
        String s = symbol.toLowerCase(Locale.ROOT);
        socket.setStreams(Arrays.asList(s + "@miniTicker", s + "@kline_" + interval));
    }

    @Override
    public void onMessage(String stream, JSONObject data) {
        try {
            if (stream.endsWith("@miniTicker")) {
                Coin c = Binance.parseMiniTicker(data);
                bindCoin(c);
                if (Alerts.check(this, c)) renderAlerts();
            } else if (stream.endsWith("@kline_" + interval)) {
                chart.update(Binance.parseKlineEvent(data));
            }
        } catch (JSONException ignored) {
            // Beklenmeyen mesaj biçimi: atla
        }
    }

    @Override
    public void onStatusChanged(boolean live) {
        // Yeniden bağlanma otomatik; detay ekranında ayrı gösterge yok
    }

    private void bindCoin(Coin c) {
        coin = c;
        binding.price.setText(money.price(c.last));
        Ui.bindChange(binding.change, c.changePercent());
        binding.high.setText(money.price(c.high));
        binding.low.setText(money.price(c.low));
        binding.volume.setText(money.compact(c.quoteVolume));
        renderHolding();
    }

    private void bindFavorite() {
        boolean favorite = Store.favorites(this).contains(symbol);
        binding.toolbar.getMenu().findItem(R.id.action_favorite)
                .setIcon(favorite ? R.drawable.ic_star : R.drawable.ic_star_outline)
                .setTitle(favorite ? R.string.favorite_remove : R.string.favorite_add);
    }

    // --- Portföy ---

    private void renderHolding() {
        Holding h = Store.holdings(this).get(symbol);
        binding.holdingCard.setVisibility(h == null ? View.GONE : View.VISIBLE);
        binding.editHolding.setText(h == null ? R.string.holding_add : R.string.holding_edit);
        if (h == null || coin == null) return;
        binding.holdingAmount.setText(getString(R.string.holding_amount_value, Format.plain(h.amount), coin.base));
        binding.holdingValue.setText(money.price(h.value(coin.last)));
        if (h.hasCost()) {
            binding.holdingProfit.setVisibility(View.VISIBLE);
            binding.holdingProfit.setText(getString(R.string.profit_value,
                    money.signed(money.convert(h.profit(coin.last))),
                    Format.percent(h.profitPercent(coin.last), Ui.locale(this))));
            Ui.tint(binding.holdingProfit, h.profit(coin.last) >= 0);
        } else {
            binding.holdingProfit.setVisibility(View.GONE);
        }
    }

    private void showHoldingDialog() {
        Holding existing = Store.holdings(this).get(symbol);
        DialogHoldingBinding d = DialogHoldingBinding.inflate(getLayoutInflater());
        d.amountLayout.setSuffixText(Coin.base(symbol));
        d.costLayout.setSuffixText(money.code);
        if (existing != null) {
            d.amount.setText(Format.plain(existing.amount));
            if (existing.hasCost()) d.cost.setText(Format.plain(money.convert(existing.avgCostUsd)));
        } else if (coin != null) {
            d.cost.setText(Format.plain(money.convert(coin.last)));
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.holding_add : R.string.holding_edit)
                .setView(d.getRoot())
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.remove, (dlg, w) -> {
                Store.setHolding(this, new Holding(symbol, 0, 0));
                renderHolding();
            });
        }
        AlertDialog dialog = builder.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double amount = Format.parse(String.valueOf(d.amount.getText()));
            String costText = String.valueOf(d.cost.getText()).trim();
            double cost = costText.isEmpty() ? 0 : Format.parse(costText);
            if (Double.isNaN(amount) || amount <= 0) {
                d.amountLayout.setError(getString(R.string.invalid_number));
                return;
            }
            if (Double.isNaN(cost) || cost < 0) {
                d.costLayout.setError(getString(R.string.invalid_number));
                return;
            }
            Store.setHolding(this, new Holding(symbol, amount, cost / money.rate));
            renderHolding();
            dialog.dismiss();
        });
    }

    // --- Fiyat alarmları ---

    private void renderAlerts() {
        binding.alerts.removeAllViews();
        List<PriceAlert> alerts = Store.alerts(this, symbol);
        binding.alertsTitle.setVisibility(alerts.isEmpty() ? View.GONE : View.VISIBLE);
        Locale locale = Ui.locale(this);
        for (PriceAlert alert : alerts) {
            Money alertMoney = new Money(alert.currency, 1, locale, getResources().getStringArray(R.array.compact_suffixes));
            Chip chip = new Chip(this);
            chip.setText(getString(alert.above ? R.string.alert_chip_above : R.string.alert_chip_below,
                    alertMoney.format(alert.target)));
            chip.setCloseIconVisible(true);
            chip.setCloseIconContentDescription(getString(R.string.remove));
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
        d.inputLayout.setSuffixText(money.code);
        if (coin != null) d.input.setText(Format.plain(money.convert(coin.last)));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.set_alert)
                .setView(d.getRoot())
                .setPositiveButton(R.string.alert_save, null) // geçersiz girdide kapanmasın
                .setNegativeButton(R.string.cancel, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double target = Format.parse(String.valueOf(d.input.getText()));
            double current = coin == null ? Double.NaN : money.convert(coin.last);
            if (Double.isNaN(current) || Double.isNaN(target) || target <= 0 || target == current) {
                d.inputLayout.setError(getString(R.string.alert_invalid));
                return;
            }
            Store.addAlert(this, PriceAlert.create(symbol, target, money.code, current));
            renderAlerts();
            updateStreams();
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
