package com.abraxel.cryptocurrency.portfolio;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.abraxel.cryptocurrency.CoinDetailActivity;
import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.Ui;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.CoinFilter;
import com.abraxel.cryptocurrency.data.Format;
import com.abraxel.cryptocurrency.data.Market;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.data.Store;
import com.abraxel.cryptocurrency.databinding.FragmentPortfolioBinding;
import com.abraxel.cryptocurrency.databinding.ItemAllocationBinding;
import com.abraxel.cryptocurrency.databinding.ItemHoldingBinding;
import com.abraxel.cryptocurrency.ui.Selectors;
import com.abraxel.cryptocurrency.ui.TabFragment;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Portföy: toplam bakiye, 24s değişim ve maliyet bazlı toplam K/Z (ayrı ayrı), varlık listesi ve dağılım.
 * Tarihsel portföy değeri saklanmadığı için "Performans" görünümü yoktur.
 */
public class PortfolioFragment extends TabFragment {
    private static final String STATE_VIEW = "view";
    private static final String VIEW_ASSETS = "assets";
    private static final String VIEW_ALLOCATION = "allocation";
    private static final int MAX_SLICES = 7;

    private FragmentPortfolioBinding binding;
    private final HoldingAdapter adapter = new HoldingAdapter();
    private Map<String, Holding> holdings = new HashMap<>();
    private Money money;
    private String view = VIEW_ASSETS;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentPortfolioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        if (state != null) view = state.getString(STATE_VIEW, VIEW_ASSETS);
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null);
        com.abraxel.cryptocurrency.ui.CoinAdapter.addDividers(binding.list);
        Selectors.addFilter(binding.views, VIEW_ASSETS, R.string.assets, 0);
        Selectors.addFilter(binding.views, VIEW_ALLOCATION, R.string.allocation, 0);
        Selectors.check(binding.views, view);
        Selectors.<String>listen(binding.views, value -> {
            view = value;
            render();
        });
        binding.add.setOnClickListener(x -> addAsset());
        binding.emptyAdd.setOnClickListener(x -> addAsset());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_VIEW, view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void addAsset() {
        CoinPicker.show(requireActivity(), coin -> HoldingDialog.show(requireActivity(), coin.symbol,
                Money.current(requireContext()), coin.last, this::reload));
    }

    private void reload() {
        if (binding == null) return;
        holdings = Store.holdings(requireContext());
        market.setStreams(this, new ArrayList<>(holdings.keySet()));
        render();
    }

    @Override
    protected void onActivated() {
        reload(); // detay ekranında değişmiş olabilir
    }

    @Override
    public void onMarketChanged(Market.Change change) {
        render();
    }

    private void render() {
        if (binding == null || !isActive()) return;
        money = Money.current(requireContext());
        Locale locale = Ui.locale(requireContext());
        Map<String, Double> prices = new HashMap<>();
        Map<String, Double> opens = new HashMap<>();
        for (String symbol : holdings.keySet()) {
            Coin c = market.coin(symbol);
            if (c == null) continue;
            prices.put(symbol, c.last);
            if (c.open > 0) opens.put(symbol, c.open);
        }
        boolean known = !prices.isEmpty() || holdings.isEmpty();
        Totals t = Totals.of(holdings.values(), prices, opens);

        binding.currency.setText(money.code);
        binding.totalValue.setText(known ? money.price(t.value) : Ui.NO_VALUE);
        // 24s değişim: eldeki varlıkların piyasa değerindeki 24 saatlik hareket
        double previous = t.value - t.change24h;
        if (known && previous > 0) {
            binding.change24h.setText(getString(R.string.amount_percent, money.signed(money.convert(t.change24h)),
                    Format.percent(t.change24h / previous * 100, locale)));
            Ui.color(binding.change24h, CoinFilter.direction(t.change24h / previous * 100));
        } else {
            binding.change24h.setText(Ui.NO_VALUE);
            Ui.color(binding.change24h, 0);
        }
        // Toplam K/Z: yalnızca alış fiyatı girilmiş varlıkların maliyetine göre; maliyet yoksa değer üretilmez
        boolean anyCost = false, allCost = true;
        for (Holding h : holdings.values()) {
            if (h.hasCost()) anyCost = true;
            else allCost = false;
        }
        if (known && t.cost > 0) {
            binding.profit.setText(getString(R.string.amount_percent, money.signed(money.convert(t.profit)),
                    Format.percent(t.profitPercent(), locale)));
            Ui.color(binding.profit, CoinFilter.direction(t.profitPercent()));
        } else {
            binding.profit.setText(Ui.NO_VALUE);
            Ui.color(binding.profit, 0);
        }
        binding.profitNote.setVisibility(!holdings.isEmpty() && !allCost ? View.VISIBLE : View.GONE);
        binding.profitNote.setText(anyCost ? R.string.pl_partial_cost : R.string.pl_no_cost);

        boolean empty = holdings.isEmpty();
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.sectionBar.setVisibility(empty ? View.GONE : View.VISIBLE);
        boolean allocation = VIEW_ALLOCATION.equals(view) && !empty;
        binding.list.setVisibility(!empty && !allocation ? View.VISIBLE : View.GONE);
        binding.allocation.setVisibility(allocation ? View.VISIBLE : View.GONE);

        List<Holding> rows = new ArrayList<>(holdings.values());
        rows.sort((a, b) -> Double.compare(value(b), value(a)));
        adapter.submit(rows);
        if (allocation) renderAllocation(prices);
    }

    private void renderAllocation(Map<String, Double> prices) {
        List<Allocation.Slice> slices = Allocation.of(holdings.values(), prices, MAX_SLICES);
        List<Double> percents = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        binding.allocationLegend.removeAllViews();
        Locale locale = Ui.locale(requireContext());
        for (int i = 0; i < slices.size(); i++) {
            Allocation.Slice s = slices.get(i);
            boolean other = Allocation.OTHER.equals(s.symbol);
            int color = AllocationBar.color(i, other);
            percents.add(s.percent);
            colors.add(color);
            ItemAllocationBinding row = ItemAllocationBinding.inflate(getLayoutInflater(), binding.allocationLegend, true);
            row.swatch.setBackgroundTintList(ColorStateList.valueOf(color));
            row.name.setText(other ? getString(R.string.other) : Coin.base(s.symbol));
            row.value.setText(money.price(s.value));
            row.percent.setText(String.format(locale, "%.1f%%", s.percent));
        }
        binding.allocationBar.setPercents(percents);
        binding.allocationBar.setColors(colors);
    }

    private double value(Holding h) {
        Coin c = market.coin(h.symbol);
        return c == null ? 0 : h.value(c.last);
    }

    private void showMenu(View anchor, Holding h) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.holding_edit);
        menu.getMenu().add(0, 2, 1, R.string.remove);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                Coin c = market.coin(h.symbol);
                HoldingDialog.show(requireActivity(), h.symbol, Money.current(requireContext()),
                        c == null ? Double.NaN : c.last, this::reload);
            } else {
                Store.setHolding(requireContext(), new Holding(h.symbol, 0, 0));
                reload();
                Snackbar.make(binding.getRoot(), R.string.holding_removed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, x -> {
                            Store.setHolding(requireContext(), h);
                            reload();
                        })
                        .show();
            }
            return true;
        });
        menu.show();
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
            Locale locale = Ui.locale(b.getRoot().getContext());
            String base = Coin.base(h.symbol);
            b.symbol.setText(base);
            b.amount.setText(getString(R.string.holding_amount_value, Format.amount(h.amount, locale), base));
            if (!base.equals(b.icon.getTag())) {
                Ui.loadIcon(b.icon, base);
                b.icon.setTag(base);
            }
            Coin c = market.coin(h.symbol);
            b.value.setText(c == null ? Ui.NO_VALUE : money.price(h.value(c.last)));
            if (c != null && h.hasCost()) {
                b.profit.setVisibility(View.VISIBLE);
                Ui.bindChange(b.profit, h.profitPercent(c.last));
            } else {
                b.profit.setVisibility(View.GONE);
            }
            b.getRoot().setOnClickListener(v -> startActivity(CoinDetailActivity.intent(requireContext(), h.symbol)));
            b.more.setOnClickListener(v -> showMenu(v, h));
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
