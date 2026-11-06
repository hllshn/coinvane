package com.abraxel.cryptocurrency.markets;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.abraxel.cryptocurrency.CoinDetailActivity;
import com.abraxel.cryptocurrency.MainActivity;
import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.Ui;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.CoinFilter;
import com.abraxel.cryptocurrency.data.Market;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.data.Period;
import com.abraxel.cryptocurrency.data.Settings;
import com.abraxel.cryptocurrency.data.Sparks;
import com.abraxel.cryptocurrency.data.Store;
import com.abraxel.cryptocurrency.databinding.FragmentMarketsBinding;
import com.abraxel.cryptocurrency.databinding.SheetFiltersBinding;
import com.abraxel.cryptocurrency.ui.CoinAdapter;
import com.abraxel.cryptocurrency.ui.CoinRow;
import com.abraxel.cryptocurrency.ui.Selectors;
import com.abraxel.cryptocurrency.ui.TabFragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Piyasalar: v7'nin dört listesinin (Hacim, Favoriler, Yükselenler, Düşenler) tam ekran, dönem bazlı ve
 * sıralanabilir hali. Canlı fiyatlar satırları yerinde günceller; yeni sıralama yalnızca kullanıcı
 * kaydırmıyorken ya da filtre/dönem/sıralama değiştiğinde uygulanır.
 */
public class MarketsFragment extends TabFragment implements CoinAdapter.Listener, MainActivity.Reselectable {
    private static final String STATE_TYPE = "type";
    private static final String STATE_QUERY = "query";
    private static final String STATE_SEARCH = "search";
    private static final int PREFETCH_ROWS = 6;
    private static final String KEY_PERIOD = "markets_period";
    private static final String KEY_SORT = "markets_sort";
    private static final String KEY_MIN_VOLUME = "markets_min_volume";

    private FragmentMarketsBinding binding;
    private CoinAdapter adapter;
    private CoinFilter.Type type = CoinFilter.Type.VOLUME;
    private Period period = Period.D1;
    private CoinFilter.Sort sort = CoinFilter.Sort.NATURAL;
    private int minVolumeStep;
    private String query = "";
    private boolean searchOpen;
    private List<String> order = new ArrayList<>();
    private boolean resortPending = true;
    private boolean touching;
    private long lastResort;
    /** Seçim değişti: yeni liste uygulanınca başa dön. */
    private boolean scrollTopPending;
    private long lastTouchEnd;
    /** Canlı fiyatlarla sıra kayarsa, kullanıcı dokunmuyorken en fazla bu sıklıkta yeniden sıralanır. */
    private static final long LIVE_RESORT_MS = 5000;
    private Set<String> favorites;
    private final Runnable render = this::render;
    private final Runnable updateVisible = this::updateVisible;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentMarketsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Context context = requireContext();
        period = Period.parse(Settings.uiString(context, KEY_PERIOD, null), Period.D1);
        sort = parseSort(Settings.uiString(context, KEY_SORT, null));
        minVolumeStep = Math.max(0, Math.min(Settings.MIN_VOLUME_STEPS.length - 1, Settings.uiInt(context, KEY_MIN_VOLUME, 0)));
        if (state != null) {
            type = CoinFilter.Type.valueOf(state.getString(STATE_TYPE, type.name()));
            query = state.getString(STATE_QUERY, "");
            searchOpen = state.getBoolean(STATE_SEARCH);
        }

        adapter = new CoinAdapter(this, Money.current(context));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null); // canlı güncellemelerde titremeyi önler
        CoinAdapter.addDividers(binding.list);
        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                touching = newState != RecyclerView.SCROLL_STATE_IDLE;
                if (!touching) lastTouchEnd = android.os.SystemClock.uptimeMillis();
                if (!touching && resortPending) render();
            }

            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                scheduleVisibleUpdate();
            }
        });
        binding.refresh.setOnRefreshListener(() -> {
            resortPending = true;
            market.refreshNow();
        });

        Selectors.addFilter(binding.types, CoinFilter.Type.VOLUME, R.string.tab_all, R.drawable.ic_volume);
        Selectors.addFilter(binding.types, CoinFilter.Type.FAVORITES, R.string.tab_favorites, R.drawable.ic_star_outline);
        Selectors.addFilter(binding.types, CoinFilter.Type.GAINERS, R.string.tab_gainers, R.drawable.ic_trending_up);
        Selectors.addFilter(binding.types, CoinFilter.Type.LOSERS, R.string.tab_losers, R.drawable.ic_trending_down);
        Selectors.check(binding.types, type);
        Selectors.<CoinFilter.Type>listen(binding.types, t -> {
            type = t;
            selectionChanged();
        });
        Selectors.setupPeriods(binding.periods.periodGroup, Period.LIST);
        Selectors.check(binding.periods.periodGroup, period);
        Selectors.<Period>listen(binding.periods.periodGroup, p -> {
            period = p;
            Settings.setUiString(requireContext(), KEY_PERIOD, p.name());
            selectionChanged();
        });

        binding.colPrice.setOnClickListener(v -> setSort(sort == CoinFilter.Sort.PRICE_DESC ? CoinFilter.Sort.PRICE_ASC
                : sort == CoinFilter.Sort.PRICE_ASC ? CoinFilter.Sort.NATURAL : CoinFilter.Sort.PRICE_DESC));
        binding.colChange.setOnClickListener(v -> setSort(sort == CoinFilter.Sort.CHANGE_DESC ? CoinFilter.Sort.CHANGE_ASC
                : sort == CoinFilter.Sort.CHANGE_ASC ? CoinFilter.Sort.NATURAL : CoinFilter.Sort.CHANGE_DESC));
        binding.filter.setOnClickListener(v -> showFilters());

        binding.search.setOnClickListener(v -> setSearchOpen(true));
        binding.closeSearch.setOnClickListener(v -> setSearchOpen(false));
        binding.query.setText(query);
        binding.query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString();
                if (q.equals(query)) return;
                query = q;
                selectionChanged();
            }
        });
        binding.query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) hideKeyboard();
            return actionId == EditorInfo.IME_ACTION_SEARCH;
        });
        bindSearch(false);
        bindControls();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_TYPE, type.name());
        out.putString(STATE_QUERY, query);
        out.putBoolean(STATE_SEARCH, searchOpen);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.getRoot().removeCallbacks(render);
        binding.getRoot().removeCallbacks(updateVisible);
        binding = null;
    }

    private static CoinFilter.Sort parseSort(String name) {
        if (name != null) for (CoinFilter.Sort s : CoinFilter.Sort.values()) if (s.name().equals(name)) return s;
        return CoinFilter.Sort.NATURAL;
    }

    // --- Dışarıdan açılış (Ana Sayfa / Keşfet) ---

    /** Belirli liste ve dönemle göster; search: arama alanını aç. */
    public void open(CoinFilter.Type type, Period period, boolean search) {
        this.type = type;
        if (type != CoinFilter.Type.VOLUME) {
            this.period = period;
            Settings.setUiString(requireContext(), KEY_PERIOD, period.name());
        }
        if (binding == null) return;
        Selectors.check(binding.types, type);
        Selectors.check(binding.periods.periodGroup, this.period);
        Selectors.reveal(binding.types);
        Selectors.reveal(binding.periods.periodGroup);
        if (search) setSearchOpen(true);
        else if (searchOpen && !query.isEmpty()) setSearchOpen(false); // farklı liste istendi: önceki arama kalmasın
        selectionChanged();
    }

    @Override
    public void onReselected() {
        if (binding != null) binding.list.scrollToPosition(0);
    }

    // --- Seçimler ---

    private Period listPeriod() {
        return type == CoinFilter.Type.VOLUME ? Period.D1 : period;
    }

    private void setSort(CoinFilter.Sort sort) {
        this.sort = sort;
        Settings.setUiString(requireContext(), KEY_SORT, sort.name());
        selectionChanged();
    }

    private void selectionChanged() {
        if (binding == null) return;
        bindControls();
        resortPending = true;
        touching = false;
        scrollTopPending = true;
        market.setPeriod(this, listPeriod());
        render();
    }

    private void bindControls() {
        boolean volume = type == CoinFilter.Type.VOLUME;
        binding.periods.getRoot().setVisibility(volume ? View.GONE : View.VISIBLE);
        binding.volumeBasis.setVisibility(volume ? View.VISIBLE : View.GONE);
        String label = getString(Ui.periodLabel(listPeriod()));
        binding.colChange.setText(getString(R.string.col_change, label));
        bindSortIcon(binding.colPrice, sort == CoinFilter.Sort.PRICE_DESC, sort == CoinFilter.Sort.PRICE_ASC);
        bindSortIcon(binding.colChange, sort == CoinFilter.Sort.CHANGE_DESC, sort == CoinFilter.Sort.CHANGE_ASC);
        binding.colPrice.setContentDescription(getString(R.string.sort_by, getString(R.string.col_price)));
        binding.colChange.setContentDescription(getString(R.string.sort_by, binding.colChange.getText()));

        boolean filtered = minVolumeStep > 0;
        binding.filter.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                filtered || sort != CoinFilter.Sort.NATURAL ? R.color.brand : R.color.text_primary)));
        binding.activeFilter.setVisibility(filtered ? View.VISIBLE : View.GONE);
        if (filtered) binding.activeFilter.setText(getString(R.string.active_min_volume, minVolumeLabel(minVolumeStep)));
    }

    private void bindSortIcon(TextView view, boolean desc, boolean asc) {
        @DrawableRes int icon = desc ? R.drawable.ic_arrow_down_small : asc ? R.drawable.ic_arrow_up_small : R.drawable.ic_sort;
        int size = Ui.dp(view.getContext(), 14);
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(view.getContext(), icon);
        if (d != null) d.setBounds(0, 0, size, size);
        view.setCompoundDrawablesRelative(null, null, d, null);
        int color = ContextCompat.getColor(view.getContext(), desc || asc ? R.color.brand : R.color.text_secondary);
        TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(color));
        view.setTextColor(color);
    }

    private double minVolumeUsd() {
        double local = Settings.MIN_VOLUME_STEPS[minVolumeStep];
        Money money = Money.current(requireContext());
        return local <= 0 ? 0 : local / money.rate;
    }

    private String minVolumeLabel(int step) {
        Money money = Money.current(requireContext());
        return money.compact(Settings.MIN_VOLUME_STEPS[step] / money.rate);
    }

    // --- Arama ---

    private void setSearchOpen(boolean open) {
        searchOpen = open;
        if (!open && !query.isEmpty()) {
            query = "";
            binding.query.setText("");
            selectionChanged();
        }
        bindSearch(open);
    }

    private void bindSearch(boolean focus) {
        binding.header.setVisibility(searchOpen ? View.GONE : View.VISIBLE);
        binding.searchBar.setVisibility(searchOpen ? View.VISIBLE : View.GONE);
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (searchOpen && focus) {
            binding.query.requestFocus();
            binding.query.post(() -> imm.showSoftInput(binding.query, InputMethodManager.SHOW_IMPLICIT));
        } else if (!searchOpen) {
            hideKeyboard();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(binding.query.getWindowToken(), 0);
        binding.query.clearFocus();
    }

    // --- Filtre menüsü ---

    private void showFilters() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        SheetFiltersBinding s = SheetFiltersBinding.inflate(getLayoutInflater());
        Money money = Money.current(requireContext());
        s.minVolumeTitle.setText(getString(R.string.filter_min_volume, money.code));
        for (int i = 0; i < Settings.MIN_VOLUME_STEPS.length; i++) {
            String label = i == 0 ? getString(R.string.filter_any) : "≥ " + minVolumeLabel(i);
            Selectors.addFilter(s.minVolume, i, 0, 0).setText(label);
        }
        Selectors.check(s.minVolume, minVolumeStep);
        Selectors.<Integer>listen(s.minVolume, step -> {
            minVolumeStep = step;
            Settings.setUiInt(requireContext(), KEY_MIN_VOLUME, step);
            selectionChanged();
        });

        Selectors.addFilter(s.sort, CoinFilter.Sort.NATURAL, R.string.sort_default, 0);
        Selectors.addFilter(s.sort, CoinFilter.Sort.PRICE_DESC, R.string.sort_price_desc, 0);
        Selectors.addFilter(s.sort, CoinFilter.Sort.PRICE_ASC, R.string.sort_price_asc, 0);
        Selectors.addFilter(s.sort, CoinFilter.Sort.CHANGE_DESC, R.string.sort_change_desc, 0);
        Selectors.addFilter(s.sort, CoinFilter.Sort.CHANGE_ASC, R.string.sort_change_asc, 0);
        Selectors.check(s.sort, sort);
        Selectors.<CoinFilter.Sort>listen(s.sort, this::setSort);
        s.sortNote.setText(getString(R.string.filter_sort_note, getString(Ui.periodLabel(listPeriod()))));

        s.reset.setOnClickListener(v -> {
            minVolumeStep = 0;
            Settings.setUiInt(requireContext(), KEY_MIN_VOLUME, 0);
            setSort(CoinFilter.Sort.NATURAL);
            sheet.dismiss();
        });
        s.done.setOnClickListener(v -> sheet.dismiss());
        sheet.setContentView(s.getRoot());
        sheet.show();
    }

    // --- Veri ---

    @Override
    protected void onActivated() {
        resortPending = true;
        bindControls();
        market.setPeriod(this, listPeriod());
        render();
    }

    @Override
    public void onMarketChanged(Market.Change change) {
        if (binding == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (change != Market.Change.PRICES
                || (now - lastResort > LIVE_RESORT_MS && now - lastTouchEnd > 2000 && !touching)) {
            resortPending = true;
        }
        if (!market.isLoading()) binding.refresh.setRefreshing(false);
        render();
    }

    private void scheduleRender() {
        if (binding == null) return;
        binding.getRoot().removeCallbacks(render);
        binding.getRoot().postDelayed(render, 250);
    }

    private void render() {
        if (binding == null || !isActive()) return;
        Context context = requireContext();
        adapter.setMoney(Money.current(context));
        Set<String> newFavorites = Store.favorites(context);
        if (type == CoinFilter.Type.FAVORITES && !newFavorites.equals(favorites)) resortPending = true;
        favorites = newFavorites;
        Period p = listPeriod();

        if (resortPending && !touching) {
            resortPending = false;
            lastResort = android.os.SystemClock.uptimeMillis();
            List<Coin> sorted = CoinFilter.apply(market.coins(), query, type, favorites, c -> market.change(c, p),
                    sort, minVolumeUsd());
            order = new ArrayList<>(sorted.size());
            for (Coin c : sorted) order.add(c.symbol);
        }
        List<CoinRow> rows = new ArrayList<>(order.size());
        for (String symbol : order) {
            Coin c = market.coin(symbol);
            if (c == null) continue;
            if (type == CoinFilter.Type.FAVORITES && !favorites.contains(symbol)) continue;
            rows.add(new CoinRow(c, market.change(c, p), favorites.contains(symbol), Sparks.get(symbol, p), null));
        }
        boolean toTop = scrollTopPending && !resortPending;
        if (toTop) scrollTopPending = false;
        adapter.submitList(rows, () -> {
            if (toTop && binding != null) binding.list.scrollToPosition(0);
            scheduleVisibleUpdate();
        });
        if (market.isLoading() && !binding.refresh.isRefreshing()) binding.refresh.setRefreshing(true);
        renderEmpty(rows.isEmpty(), p);
    }

    private void renderEmpty(boolean empty, Period p) {
        binding.empty.setVisibility(empty && !market.isLoading() ? View.VISIBLE : View.GONE);
        binding.emptyAction.setVisibility(View.GONE);
        if (!empty) return;
        if (market.isOffline()) {
            binding.emptyText.setText(R.string.error_offline);
            showEmptyAction(R.string.retry, () -> {
                binding.refresh.setRefreshing(true);
                market.refreshNow();
            });
        } else if (!query.trim().isEmpty()) {
            binding.emptyText.setText(R.string.empty_results);
        } else if (type == CoinFilter.Type.FAVORITES && favorites.isEmpty()) {
            binding.emptyText.setText(R.string.empty_favorites);
            showEmptyAction(R.string.browse_markets, () -> open(CoinFilter.Type.VOLUME, period, false));
        } else if (p != Period.D1 && market.isLoading(p)) {
            binding.emptyText.setText(getString(R.string.loading_period, getString(Ui.periodLabel(p))));
        } else if (p != Period.D1 && !market.hasData(p)) {
            binding.emptyText.setText(R.string.empty_period);
            showEmptyAction(R.string.retry, market::refreshNow);
        } else if (minVolumeStep > 0) {
            binding.emptyText.setText(R.string.empty_filtered);
        } else if (type == CoinFilter.Type.GAINERS) {
            binding.emptyText.setText(R.string.empty_gainers);
        } else if (type == CoinFilter.Type.LOSERS) {
            binding.emptyText.setText(R.string.empty_losers);
        } else {
            binding.emptyText.setText(R.string.empty_period);
        }
    }

    private void showEmptyAction(int text, Runnable action) {
        binding.emptyAction.setVisibility(View.VISIBLE);
        binding.emptyAction.setText(text);
        binding.emptyAction.setOnClickListener(v -> action.run());
    }

    /** Canlı akış ve mini grafikler yalnızca ekrandaki (+ yakın) satırlar için. */
    private void scheduleVisibleUpdate() {
        if (binding == null) return;
        binding.getRoot().removeCallbacks(updateVisible);
        binding.getRoot().postDelayed(updateVisible, 300);
    }

    private void updateVisible() {
        if (binding == null || !isActive()) return;
        LinearLayoutManager lm = (LinearLayoutManager) binding.list.getLayoutManager();
        List<CoinRow> shown = adapter.getCurrentList();
        Set<String> live = new LinkedHashSet<>();
        List<String> visible = new ArrayList<>();
        if (lm != null && !shown.isEmpty()) {
            int firstVisible = Math.max(0, lm.findFirstVisibleItemPosition());
            int lastVisible = Math.min(shown.size() - 1, Math.max(lm.findLastVisibleItemPosition(), 0));
            int first = Math.max(0, firstVisible - PREFETCH_ROWS);
            int last = Math.min(shown.size() - 1, lastVisible + PREFETCH_ROWS);
            for (int i = first; i <= last; i++) live.add(shown.get(i).coin.symbol);
            for (int i = firstVisible; i <= lastVisible; i++) visible.add(shown.get(i).coin.symbol);
        }
        market.setStreams(this, live);
        if (!touching && CoinAdapter.sparkFits(requireContext())) {
            Sparks.request(visible, listPeriod(), this::scheduleRender);
        }
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
