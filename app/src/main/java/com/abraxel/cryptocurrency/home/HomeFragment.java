package com.abraxel.cryptocurrency.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.abraxel.cryptocurrency.CoinDetailActivity;
import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.Ui;
import com.abraxel.cryptocurrency.alerts.AlertsActivity;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.CoinFilter;
import com.abraxel.cryptocurrency.data.Market;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.data.Period;
import com.abraxel.cryptocurrency.data.Sparks;
import com.abraxel.cryptocurrency.data.Store;
import com.abraxel.cryptocurrency.databinding.FragmentHomeBinding;
import com.abraxel.cryptocurrency.databinding.ViewMovementCellBinding;
import com.abraxel.cryptocurrency.databinding.ViewOverviewCoinBinding;
import com.abraxel.cryptocurrency.ui.CoinAdapter;
import com.abraxel.cryptocurrency.ui.CoinRow;
import com.abraxel.cryptocurrency.ui.Selectors;
import com.abraxel.cryptocurrency.ui.TabFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ana Sayfa: piyasanın tek bakışta özeti. BTC/ETH genel görünümü, seçili döneme göre yükselen/düşen/değişmeyen
 * sayıları ve en hareketli 5 coinin ön izlemesi. Yüzlerce satırlık liste Piyasalar'dadır.
 */
public class HomeFragment extends TabFragment implements CoinAdapter.Listener {
    private static final String STATE_TYPE = "type";
    private static final String STATE_PERIOD = "period";
    private static final String BTC = "BTCUSDT";
    private static final String ETH = "ETHUSDT";
    private static final int PREVIEW_ROWS = 5;
    /** Kullanıcı kaydırırken ön izleme satırları yer değiştirmez. */
    private static final long SCROLL_SETTLE_MS = 1200;

    private FragmentHomeBinding binding;
    private CoinAdapter adapter;
    private CoinFilter.Type type = CoinFilter.Type.GAINERS;
    private Period period = Period.D1;
    private List<String> order = new ArrayList<>();
    private boolean resortPending = true;
    private long lastScroll;
    private long lastResort;
    private Set<String> favorites;
    private final Runnable render = this::render;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        if (state != null) {
            type = CoinFilter.Type.valueOf(state.getString(STATE_TYPE, type.name()));
            period = Period.parse(state.getString(STATE_PERIOD), period);
        }
        adapter = new CoinAdapter(this, Money.current(requireContext()));
        binding.preview.setAdapter(adapter);
        binding.preview.setItemAnimator(null);
        CoinAdapter.addDividers(binding.preview);

        Selectors.addFilter(binding.types, CoinFilter.Type.GAINERS, R.string.tab_gainers, R.drawable.ic_trending_up);
        Selectors.addFilter(binding.types, CoinFilter.Type.LOSERS, R.string.tab_losers, R.drawable.ic_trending_down);
        Selectors.addFilter(binding.types, CoinFilter.Type.VOLUME, R.string.tab_all, R.drawable.ic_volume);
        Selectors.check(binding.types, type);
        Selectors.<CoinFilter.Type>listen(binding.types, t -> {
            type = t;
            selectionChanged();
        });
        Selectors.setupPeriods(binding.periods.periodGroup, Period.LIST);
        Selectors.check(binding.periods.periodGroup, period);
        Selectors.reveal(binding.periods.periodGroup);
        Selectors.<Period>listen(binding.periods.periodGroup, p -> {
            period = p;
            selectionChanged();
        });

        bindMovementCell(binding.rising, R.drawable.ic_arrow_up_small, R.color.up, R.string.movement_up);
        bindMovementCell(binding.falling, R.drawable.ic_arrow_down_small, R.color.down, R.string.movement_down);
        bindMovementCell(binding.flat, R.drawable.ic_minus_small, R.color.neutral, R.string.movement_flat);

        binding.search.setOnClickListener(v -> host().openMarkets(CoinFilter.Type.VOLUME, Period.D1, true));
        binding.alerts.setOnClickListener(v -> startActivity(new Intent(requireContext(), AlertsActivity.class)));
        binding.movementHeader.setOnClickListener(v -> host().openMarkets(CoinFilter.Type.GAINERS, listPeriod(), false));
        binding.seeAll.setOnClickListener(v -> host().openMarkets(type, listPeriod(), false));
        binding.btc.cell.setOnClickListener(v -> startActivity(CoinDetailActivity.intent(requireContext(), BTC)));
        binding.eth.cell.setOnClickListener(v -> startActivity(CoinDetailActivity.intent(requireContext(), ETH)));
        binding.refresh.setOnRefreshListener(() -> market.refreshNow());
        binding.scroll.setOnScrollChangeListener((View.OnScrollChangeListener) (v, x, y, ox, oy) ->
                lastScroll = SystemClock.uptimeMillis());
        bindPeriodVisibility();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_TYPE, type.name());
        out.putString(STATE_PERIOD, period.name());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.getRoot().removeCallbacks(render);
        binding = null;
    }

    private static void bindMovementCell(ViewMovementCellBinding cell, int icon, int color, int label) {
        cell.icon.setImageResource(icon);
        int c = ContextCompat.getColor(cell.getRoot().getContext(), color);
        cell.icon.setImageTintList(android.content.res.ColorStateList.valueOf(c));
        cell.count.setTextColor(c);
        cell.label.setText(label);
    }

    /** Hacim listesinde dönem seçilemez; değişim ve hareket 24s'e göredir. */
    private Period listPeriod() {
        return type == CoinFilter.Type.VOLUME ? Period.D1 : period;
    }

    private void selectionChanged() {
        bindPeriodVisibility();
        resortPending = true;
        market.setPeriod(this, listPeriod());
        render();
    }

    private void bindPeriodVisibility() {
        boolean volume = type == CoinFilter.Type.VOLUME;
        binding.periods.getRoot().setVisibility(volume ? View.GONE : View.VISIBLE);
        binding.volumeBasis.setVisibility(volume ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivated() {
        resortPending = true;
        market.setPeriod(this, listPeriod());
        render();
    }

    @Override
    public void onMarketChanged(Market.Change change) {
        if (binding == null) return;
        if (change != Market.Change.PRICES) {
            resortPending = true;
            binding.refresh.setRefreshing(false);
        } else if (SystemClock.uptimeMillis() - lastResort > 5000) {
            resortPending = true; // kaydırma yoksa kontrollü yeniden sıralama (renderPreview kaydırmayı bekler)
        }
        render();
    }

    private void scheduleRender() {
        if (binding == null) return;
        binding.getRoot().removeCallbacks(render);
        binding.getRoot().postDelayed(render, 250);
    }

    private void render() {
        if (binding == null || !isActive()) return;
        Money money = Money.current(requireContext());
        adapter.setMoney(money);
        favorites = Store.favorites(requireContext());

        // Bağlantı durumu: yalnızca canlı akış gerçekten bağlıyken "Canlı"
        if (market.isLive() && market.hasCoins()) {
            binding.status.setText(getResources().getQuantityString(R.plurals.status_live, market.size(), market.size()));
        } else {
            binding.status.setText(market.isOffline() ? R.string.status_offline : R.string.status_connecting);
        }

        bindOverview(binding.btc, BTC, money);
        bindOverview(binding.eth, ETH, money);
        renderMovement();
        renderPreview(money);
    }

    private void bindOverview(ViewOverviewCoinBinding cell, String symbol, Money money) {
        cell.symbol.setText(Coin.base(symbol));
        if (cell.icon.getTag() == null) {
            Ui.loadIcon(cell.icon, Coin.base(symbol));
            cell.icon.setTag(symbol);
        }
        Coin c = market.coin(symbol);
        cell.price.setText(c == null ? Ui.NO_VALUE : money.price(c.last));
        Ui.bindChange(cell.change, c == null ? Double.NaN : market.change(c, Period.D1));
    }

    private void renderMovement() {
        Period p = listPeriod();
        String label = getString(Ui.periodLabel(p));
        int[] counts = market.isLoading(p) ? null : CoinFilter.movement(market.coins(), c -> market.change(c, p));
        if (counts == null) {
            binding.movement.setVisibility(View.INVISIBLE);
            binding.movementEmpty.setVisibility(View.VISIBLE);
            binding.movementEmpty.setText(market.isOffline() ? getString(R.string.error_offline)
                    : market.isLoading(p) || market.isLoading() ? getString(R.string.loading) : getString(R.string.empty_period));
            binding.movementBasis.setText(getString(R.string.movement_basis_short, label));
            return;
        }
        binding.movement.setVisibility(View.VISIBLE);
        binding.movementEmpty.setVisibility(View.GONE);
        int total = counts[0] + counts[1] + counts[2];
        binding.movementBasis.setText(getResources().getQuantityString(R.plurals.movement_basis, total, label, total));
        binding.rising.count.setText(String.valueOf(counts[0]));
        binding.falling.count.setText(String.valueOf(counts[1]));
        binding.flat.count.setText(String.valueOf(counts[2]));
    }

    private void renderPreview(Money money) {
        Period p = listPeriod();
        boolean settled = SystemClock.uptimeMillis() - lastScroll > SCROLL_SETTLE_MS;
        if (resortPending && settled) {
            resortPending = false;
            lastResort = SystemClock.uptimeMillis();
            List<Coin> sorted = CoinFilter.apply(market.coins(), "", type, favorites, c -> market.change(c, p),
                    CoinFilter.Sort.NATURAL, 0);
            order = new ArrayList<>();
            for (int i = 0; i < Math.min(PREVIEW_ROWS, sorted.size()); i++) order.add(sorted.get(i).symbol);
        } else if (resortPending) {
            binding.getRoot().removeCallbacks(render);
            binding.getRoot().postDelayed(render, SCROLL_SETTLE_MS); // kaydırma bitince sırala
        }
        List<CoinRow> rows = new ArrayList<>();
        for (String symbol : order) {
            Coin c = market.coin(symbol);
            if (c == null) continue;
            rows.add(new CoinRow(c, market.change(c, p), favorites.contains(symbol), Sparks.get(symbol, p), null));
        }
        adapter.submitList(rows);

        boolean loading = market.isLoading(p);
        binding.previewEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        if (rows.isEmpty()) {
            binding.previewEmpty.setText(loading ? R.string.loading
                    : market.isOffline() ? R.string.error_load : R.string.empty_period);
        }
        binding.seeAll.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);

        List<String> streams = new ArrayList<>(order);
        streams.add(BTC);
        streams.add(ETH);
        market.setStreams(this, streams);
        if (CoinAdapter.sparkFits(requireContext())) Sparks.request(order, p, this::scheduleRender);
    }

    @Override
    public void onCoinClick(CoinRow row) {
        startActivity(CoinDetailActivity.intent(requireContext(), row.coin.symbol));
    }

    @Override
    public void onFavoriteClick(CoinRow row) {
        Store.toggleFavorite(requireContext(), row.coin.symbol);
        render();
    }
}
