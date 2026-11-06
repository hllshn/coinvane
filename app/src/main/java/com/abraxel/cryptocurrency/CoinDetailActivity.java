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
import com.abraxel.cryptocurrency.data.Period;
import com.abraxel.cryptocurrency.data.PriceSocket;
import com.abraxel.cryptocurrency.data.Store;
import com.abraxel.cryptocurrency.data.WindowStat;
import com.abraxel.cryptocurrency.databinding.ActivityCoinDetailBinding;
import com.abraxel.cryptocurrency.databinding.DialogAlertBinding;
import com.abraxel.cryptocurrency.databinding.ItemPerfCellBinding;
import com.abraxel.cryptocurrency.portfolio.Holding;
import com.abraxel.cryptocurrency.portfolio.HoldingDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;

/**
 * Coin detayı: canlı fiyat ve seçili dönemin değişim rozeti, dönemsel performans (5dk–30g), 24s istatistikleri,
 * mum/hacim grafiği, portföy pozisyonu ve fiyat alarmları.
 */
public class CoinDetailActivity extends AppCompatActivity implements PriceSocket.Listener {
    private static final String EXTRA_SYMBOL = "symbol";
    private static final String STATE_INTERVAL = "interval";
    private static final String STATE_LINE = "line";
    private static final String STATE_BADGE = "badge";
    private static final Period[] PERFORMANCE = {Period.M5, Period.M15, Period.H1, Period.H4, Period.D1, Period.D7, Period.D30};

    private ActivityCoinDetailBinding binding;
    private CandleChart chart;
    private Money money;
    private String symbol;
    private String interval = "1h";
    private Coin coin;
    private final PriceSocket socket = new PriceSocket(this);
    private Call coinCall;
    private Call candleCall;
    private final List<Call> perfCalls = new ArrayList<>();
    /** Dönem → dönemin açılış fiyatı (USD); değişim canlı fiyatla hesaplanır. */
    private final Map<Period, Double> opens = new EnumMap<>(Period.class);
    private final Map<Period, ItemPerfCellBinding> perfCells = new EnumMap<>(Period.class);
    private Period badge = Period.D1;

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
        if (savedInstanceState != null) {
            interval = savedInstanceState.getString(STATE_INTERVAL, interval);
            badge = Period.parse(savedInstanceState.getString(STATE_BADGE), badge);
        }

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
        setupPerformance();
        loadPerformance();
        renderAlerts();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_INTERVAL, interval);
        outState.putBoolean(STATE_LINE, chart.isLineMode());
        outState.putString(STATE_BADGE, badge.name());
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
        for (Call c : perfCalls) c.cancel();
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
        if (c.open > 0) opens.put(Period.D1, c.open);
        binding.price.setText(money.price(c.last));
        binding.high.setText(money.price(c.high));
        binding.low.setText(money.price(c.low));
        binding.volume.setText(money.compact(c.quoteVolume));
        renderPerformance();
        renderHolding();
    }

    // --- Dönemsel performans ---

    private void setupPerformance() {
        for (Period p : PERFORMANCE) {
            ItemPerfCellBinding cell = ItemPerfCellBinding.inflate(getLayoutInflater(), binding.performance, true);
            cell.label.setText(Ui.periodLabel(p));
            cell.getRoot().setOnClickListener(v -> {
                badge = p;
                renderPerformance();
            });
            perfCells.put(p, cell);
        }
        renderPerformance();
    }

    /** 7 güne kadar Binance pencere özeti; 30g için 30 gün önceki saatlik mumun açılışı. */
    private void loadPerformance() {
        for (Period p : PERFORMANCE) {
            if (p == Period.D1) continue;
            if (p == Period.D30) {
                long start = System.currentTimeMillis() - p.durationMs();
                perfCalls.add(Binance.fetchCandlesFrom(symbol, "1h", start, 1, new Binance.Result<>() {
                    @Override
                    public void onSuccess(List<Candle> candles) {
                        // 30 günden yeni listelenmiş coinde ilk mum çok sonradır: değer üretilmez
                        if (!candles.isEmpty() && candles.get(0).time - start < 2 * 3_600_000L) {
                            opens.put(p, candles.get(0).open);
                        }
                        renderPerformance();
                    }

                    @Override
                    public void onError(Exception e) {
                    }
                }));
                continue;
            }
            perfCalls.add(Binance.fetchWindow(symbol, p.window, new Binance.Result<>() {
                @Override
                public void onSuccess(Map<String, WindowStat> value) {
                    WindowStat stat = value.get(symbol);
                    if (stat != null && stat.covers(p.durationMs())) opens.put(p, stat.open);
                    renderPerformance();
                }

                @Override
                public void onError(Exception e) {
                }
            }));
        }
    }

    private double change(Period p) {
        Double open = opens.get(p);
        return coin == null || open == null || open <= 0 ? Double.NaN : (coin.last - open) / open * 100;
    }

    private void renderPerformance() {
        for (Map.Entry<Period, ItemPerfCellBinding> e : perfCells.entrySet()) {
            ItemPerfCellBinding cell = e.getValue();
            double change = change(e.getKey());
            cell.value.setText(Double.isNaN(change) ? Ui.NO_VALUE : Format.percent(change, Ui.locale(this)));
            Ui.color(cell.value, com.abraxel.cryptocurrency.data.CoinFilter.direction(change));
            boolean selected = e.getKey() == badge;
            cell.getRoot().setBackgroundResource(selected ? R.drawable.bg_cell_selected : 0);
            cell.getRoot().setSelected(selected);
            cell.getRoot().setContentDescription(getString(R.string.change_period, getString(Ui.periodLabel(e.getKey())))
                    + ": " + cell.value.getText());
        }
        // Rozet hangi dönemi gösterdiğini açıkça yazar; grafik mum aralığından bağımsızdır
        Ui.bindChange(binding.change, change(badge));
        binding.changePeriod.setText(getString(R.string.change_period, getString(Ui.periodLabel(badge))));
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
        binding.holdingAmount.setText(getString(R.string.holding_amount_value, Format.amount(h.amount, Ui.locale(this)), coin.base));
        binding.holdingValue.setText(money.price(h.value(coin.last)));
        if (h.hasCost()) {
            binding.holdingProfit.setVisibility(View.VISIBLE);
            binding.holdingProfit.setText(getString(R.string.profit_value,
                    money.signed(money.convert(h.profit(coin.last))),
                    Format.percent(h.profitPercent(coin.last), Ui.locale(this))));
            Ui.tint(binding.holdingProfit, com.abraxel.cryptocurrency.data.CoinFilter.direction(h.profitPercent(coin.last)));
        } else {
            binding.holdingProfit.setVisibility(View.GONE);
        }
    }

    private void showHoldingDialog() {
        HoldingDialog.show(this, symbol, money, coin == null ? Double.NaN : coin.last, this::renderHolding);
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
        Ui.decimalInput(d.input);
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
