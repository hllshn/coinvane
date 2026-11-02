package com.abraxel.cryptocurrency.portfolio;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.abraxel.cryptocurrency.CoinDetailActivity;
import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.Ui;
import com.abraxel.cryptocurrency.alerts.Alerts;
import com.abraxel.cryptocurrency.data.Binance;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.Format;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.data.PriceSocket;
import com.abraxel.cryptocurrency.data.Store;
import com.abraxel.cryptocurrency.databinding.ActivityPortfolioBinding;
import com.abraxel.cryptocurrency.databinding.ItemHoldingBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;

/** Portföy: toplam değer, 24s değişim, kâr/zarar ve pozisyonlar (canlı). Pozisyonlar coin detayından eklenir. */
public class PortfolioActivity extends AppCompatActivity implements PriceSocket.Listener {
    private ActivityPortfolioBinding binding;
    private Money money;
    private Map<String, Holding> holdings = new HashMap<>();
    private final Map<String, Coin> coins = new HashMap<>();
    private final PriceSocket socket = new PriceSocket(this);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HoldingAdapter adapter = new HoldingAdapter();
    private boolean renderScheduled;
    private Call pending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityPortfolioBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Ui.padForSystemBars(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.list.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        money = Money.current(this);
        holdings = Store.holdings(this); // detay ekranında değişmiş olabilir
        render();
        if (holdings.isEmpty()) return;
        if (pending != null) pending.cancel();
        pending = Binance.fetchCoins(holdings.keySet(), new Binance.Result<>() {
            @Override
            public void onSuccess(List<Coin> list) {
                for (Coin c : list) coins.put(c.symbol, c);
                render();
            }

            @Override
            public void onError(Exception e) {
                // canlı akış dolduracak
            }
        });
        List<String> streams = new ArrayList<>();
        for (String s : holdings.keySet()) streams.add(s.toLowerCase(Locale.ROOT) + "@miniTicker");
        socket.setStreams(streams);
        socket.open();
    }

    @Override
    protected void onStop() {
        super.onStop();
        socket.close();
        handler.removeCallbacksAndMessages(null);
        renderScheduled = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pending != null) pending.cancel();
    }

    @Override
    public void onMessage(String stream, JSONObject data) {
        try {
            Coin c = Binance.parseMiniTicker(data);
            coins.put(c.symbol, c);
            Alerts.check(this, c);
        } catch (JSONException e) {
            return;
        }
        if (!renderScheduled) {
            renderScheduled = true;
            handler.postDelayed(() -> {
                renderScheduled = false;
                render();
            }, 1000);
        }
    }

    @Override
    public void onStatusChanged(boolean live) {
    }

    private void render() {
        boolean empty = holdings.isEmpty();
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.summary.setVisibility(empty ? View.GONE : View.VISIBLE);

        Map<String, Double> prices = new HashMap<>();
        Map<String, Double> opens = new HashMap<>();
        for (Coin c : coins.values()) {
            prices.put(c.symbol, c.last);
            opens.put(c.symbol, c.open);
        }
        Totals t = Totals.of(holdings.values(), prices, opens);
        Locale locale = Ui.locale(PortfolioActivity.this);
        binding.totalValue.setText(money.price(t.value));
        double previous = t.value - t.change24h;
        binding.change24h.setText(getString(R.string.change_24h_value, money.signed(money.convert(t.change24h)),
                Format.percent(previous > 0 ? t.change24h / previous * 100 : 0, locale)));
        Ui.tint(binding.change24h, t.change24h >= 0);
        boolean hasCost = t.cost > 0;
        binding.profit.setVisibility(hasCost ? View.VISIBLE : View.GONE);
        if (hasCost) {
            binding.profit.setText(getString(R.string.profit_value, money.signed(money.convert(t.profit)),
                    Format.percent(t.profitPercent(), locale)));
            Ui.tint(binding.profit, t.profit >= 0);
        }

        List<Holding> rows = new ArrayList<>(holdings.values());
        rows.sort((a, b) -> Double.compare(value(b), value(a)));
        adapter.submit(rows);
    }

    private double value(Holding h) {
        Coin c = coins.get(h.symbol);
        return c == null ? 0 : h.value(c.last);
    }

    private final class HoldingAdapter extends RecyclerView.Adapter<HoldingAdapter.Holder> {
        private List<Holding> rows = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged") // en fazla birkaç düzine satır, saniyede bir
        void submit(List<Holding> rows) {
            this.rows = rows;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemHoldingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Holding h = rows.get(position);
            ItemHoldingBinding b = holder.binding;
            String base = Coin.base(h.symbol);
            b.symbol.setText(base);
            b.amount.setText(getString(R.string.holding_amount_value, Format.plain(h.amount), base));
            Ui.loadIcon(b.icon, base);
            Coin c = coins.get(h.symbol);
            b.value.setText(c == null ? "—" : money.price(h.value(c.last)));
            if (c != null && h.hasCost()) {
                b.profit.setVisibility(View.VISIBLE);
                b.profit.setText(Format.percent(h.profitPercent(c.last), Ui.locale(PortfolioActivity.this)));
                Ui.tint(b.profit, h.profit(c.last) >= 0);
            } else {
                b.profit.setVisibility(View.GONE);
            }
            b.getRoot().setOnClickListener(v -> startActivity(CoinDetailActivity.intent(PortfolioActivity.this, h.symbol)));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemHoldingBinding binding;

            Holder(ItemHoldingBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
