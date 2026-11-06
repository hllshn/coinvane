package com.abraxel.cryptocurrency.alerts;

import android.annotation.SuppressLint;
import android.os.Bundle;
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
import com.abraxel.cryptocurrency.data.Binance;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.Fx;
import com.abraxel.cryptocurrency.data.Market;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.data.Store;
import com.abraxel.cryptocurrency.databinding.ActivityAlertsBinding;
import com.abraxel.cryptocurrency.databinding.ItemAlertBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;

/** Tüm aktif fiyat alarmları (bildirim merkezi değil): hedef, güncel fiyat, silme ve coin detayına geçiş. */
public class AlertsActivity extends AppCompatActivity {
    private ActivityAlertsBinding binding;
    private final Adapter adapter = new Adapter();
    private final Map<String, Coin> prices = new HashMap<>();
    private Call pending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityAlertsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Ui.padForSystemBars(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.list.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        render(); // detayda alarm kurulmuş/silinmiş olabilir
        Set<String> symbols = new LinkedHashSet<>();
        for (PriceAlert a : Store.alerts(this)) symbols.add(a.symbol);
        if (symbols.isEmpty()) return;
        if (pending != null) pending.cancel();
        pending = Binance.fetchCoins(symbols, new Binance.Result<>() {
            @Override
            public void onSuccess(List<Coin> value) {
                for (Coin c : value) prices.put(c.symbol, c);
                render();
            }

            @Override
            public void onError(Exception e) {
                // güncel fiyat "—" kalır
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pending != null) pending.cancel();
    }

    private void render() {
        List<PriceAlert> alerts = Store.alerts(this);
        binding.empty.setVisibility(alerts.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.submit(alerts);
    }

    private void remove(PriceAlert alert) {
        Store.removeAlert(this, alert);
        Market.get(this).syncStreams();
        render();
        Snackbar.make(binding.getRoot(), R.string.alert_removed, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> {
                    Store.addAlert(this, alert);
                    render();
                })
                .show();
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private List<PriceAlert> rows = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged") // birkaç satır
        void submit(List<PriceAlert> rows) {
            this.rows = rows;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemAlertBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PriceAlert alert = rows.get(position);
            ItemAlertBinding b = holder.b;
            AlertsActivity context = AlertsActivity.this;
            Money alertMoney = new Money(alert.currency, 1, Ui.locale(context), getResources().getStringArray(R.array.compact_suffixes));
            String base = Coin.base(alert.symbol);
            b.title.setText(getString(R.string.alert_row_title, base, getString(alert.above
                    ? R.string.alert_chip_above : R.string.alert_chip_below, alertMoney.format(alert.target))));
            Coin c = prices.get(alert.symbol);
            double rate = Fx.rate(context, alert.currency);
            b.subtitle.setText(getString(R.string.alert_now,
                    c == null || Double.isNaN(rate) ? Ui.NO_VALUE : alertMoney.format(c.last * rate)));
            Ui.loadIcon(b.icon, base);
            b.getRoot().setOnClickListener(v -> startActivity(CoinDetailActivity.intent(context, alert.symbol)));
            b.delete.setOnClickListener(v -> remove(alert));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemAlertBinding b;

            Holder(ItemAlertBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
