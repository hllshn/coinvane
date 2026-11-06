package com.abraxel.cryptocurrency.explore;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.abraxel.cryptocurrency.CoinDetailActivity;
import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.Ui;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.CoinFilter;
import com.abraxel.cryptocurrency.data.Market;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.data.Period;
import com.abraxel.cryptocurrency.data.Sparks;
import com.abraxel.cryptocurrency.data.Store;
import com.abraxel.cryptocurrency.databinding.FragmentExploreBinding;
import com.abraxel.cryptocurrency.databinding.ViewRowBinding;
import com.abraxel.cryptocurrency.ui.CoinAdapter;
import com.abraxel.cryptocurrency.ui.CoinRow;
import com.abraxel.cryptocurrency.ui.TabFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Keşfet: verilerde gerçekten dikkat çeken coin grupları. Yalnızca mevcut veriyle hesaplanabilenler gösterilir:
 * öne çıkan listeler (Piyasalar filtrelerine kısayol), son 1 saatin yükselenleri ve son 1 saatlik hacmi 24s
 * ortalamasının üstüne çıkanlar. Sektör sınıflandırması, listelenme tarihi ve haber servisi olmadığı için
 * bunlar için bölüm yoktur.
 */
public class ExploreFragment extends TabFragment {
    private static final int ROWS = 5;
    /** Hacim hareketlenmesinde gürültüyü önlemek için en az 24s hacim (USD). */
    private static final double SURGE_MIN_VOLUME_USD = 1_000_000;

    private FragmentExploreBinding binding;
    private CoinAdapter movers;
    private CoinAdapter surge;
    private List<String> moversOrder = Collections.emptyList();
    private List<String> surgeOrder = Collections.emptyList();
    private boolean resortPending = true;
    private final Runnable render = this::render;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentExploreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        bindFeatured(binding.featuredGainers, R.drawable.ic_trending_up, R.string.featured_fast_gainers, Period.H1,
                CoinFilter.Type.GAINERS);
        bindFeatured(binding.featuredLosers, R.drawable.ic_trending_down, R.string.featured_top_losers, Period.D1,
                CoinFilter.Type.LOSERS);
        bindFeatured(binding.featuredVolume, R.drawable.ic_volume, R.string.featured_volume_leaders, Period.D1,
                CoinFilter.Type.VOLUME);

        CoinAdapter.Listener listener = new CoinAdapter.Listener() {
            @Override
            public void onCoinClick(CoinRow row) {
                startActivity(CoinDetailActivity.intent(requireContext(), row.coin.symbol));
            }

            @Override
            public void onFavoriteClick(CoinRow row) {
                Store.toggleFavorite(requireContext(), row.coin.symbol);
                render();
            }
        };
        movers = new CoinAdapter(listener, Money.current(requireContext()));
        surge = new CoinAdapter(listener, Money.current(requireContext()));
        binding.movers.setAdapter(movers);
        binding.surge.setAdapter(surge);
        binding.movers.setItemAnimator(null);
        CoinAdapter.addDividers(binding.movers);
        binding.surge.setItemAnimator(null);
        CoinAdapter.addDividers(binding.surge);
        binding.moversAll.setOnClickListener(v -> host().openMarkets(CoinFilter.Type.GAINERS, Period.H1, false));
        binding.search.setOnClickListener(v -> host().openMarkets(CoinFilter.Type.VOLUME, Period.D1, true));
        binding.refresh.setOnRefreshListener(() -> {
            resortPending = true;
            market.refreshNow();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.getRoot().removeCallbacks(render);
        binding = null;
    }

    private void bindFeatured(ViewRowBinding row, @DrawableRes int icon, @StringRes int title, Period period,
                              CoinFilter.Type type) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
        row.subtitle.setVisibility(View.VISIBLE);
        row.subtitle.setText(type == CoinFilter.Type.VOLUME
                ? getString(R.string.volume_basis)
                : getString(R.string.period_last, getString(Ui.periodLabel(period))));
        row.value.setVisibility(View.GONE);
        row.row.setOnClickListener(v -> host().openMarkets(type, period, false));
    }

    @Override
    protected void onActivated() {
        resortPending = true;
        market.setPeriod(this, Period.H1);
        render();
    }

    @Override
    public void onMarketChanged(Market.Change change) {
        if (binding == null) return;
        if (change != Market.Change.PRICES) {
            resortPending = true;
            binding.refresh.setRefreshing(false);
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
        movers.setMoney(money);
        surge.setMoney(money);
        Set<String> favorites = Store.favorites(requireContext());
        Locale locale = Ui.locale(requireContext());

        if (resortPending) {
            resortPending = false;
            List<Coin> gainers = CoinFilter.apply(market.coins(), "", CoinFilter.Type.GAINERS, favorites,
                    c -> market.change(c, Period.H1), CoinFilter.Sort.NATURAL, 0);
            moversOrder = symbols(gainers.subList(0, Math.min(ROWS, gainers.size())));
            surgeOrder = symbols(CoinFilter.volumeSurge(market.coins(), c -> market.windowVolume(c, Period.H1),
                    SURGE_MIN_VOLUME_USD, ROWS));
        }

        List<CoinRow> moverRows = new ArrayList<>();
        for (String s : moversOrder) {
            Coin c = market.coin(s);
            if (c != null) moverRows.add(new CoinRow(c, market.change(c, Period.H1), favorites.contains(s), Sparks.get(s, Period.H1), null));
        }
        List<CoinRow> surgeRows = new ArrayList<>();
        for (String s : surgeOrder) {
            Coin c = market.coin(s);
            if (c == null) continue;
            double ratio = CoinFilter.surgeRatio(c, market.windowVolume(c, Period.H1));
            String note = getString(R.string.volume_ratio, String.format(locale, "%.1f", ratio));
            surgeRows.add(new CoinRow(c, market.change(c, Period.H1), favorites.contains(s), Sparks.get(s, Period.H1), note));
        }
        movers.submitList(moverRows);
        surge.submitList(surgeRows);

        boolean loading = market.isLoading(Period.H1);
        int emptyText = loading ? R.string.loading : market.isOffline() ? R.string.error_load : R.string.empty_period;
        binding.moversEmpty.setVisibility(moverRows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.moversEmpty.setText(emptyText);
        binding.moversAll.setVisibility(moverRows.isEmpty() ? View.GONE : View.VISIBLE);
        binding.surgeEmpty.setVisibility(surgeRows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.surgeEmpty.setText(emptyText);

        List<String> live = new ArrayList<>(moversOrder);
        live.addAll(surgeOrder);
        market.setStreams(this, live);
        if (CoinAdapter.sparkFits(requireContext())) Sparks.request(live, Period.H1, this::scheduleRender);
    }

    private static List<String> symbols(List<Coin> coins) {
        List<String> list = new ArrayList<>(coins.size());
        for (Coin c : coins) list.add(c.symbol);
        return list;
    }
}
