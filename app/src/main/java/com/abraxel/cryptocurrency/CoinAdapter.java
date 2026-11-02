package com.abraxel.cryptocurrency;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.databinding.ItemCoinBinding;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ana liste. Fark hesabı (DiffUtil) arka planda yapılır; fiyat değişince fiyat kısa süre renklenir. */
class CoinAdapter extends ListAdapter<Coin, CoinAdapter.Holder> {
    interface Listener {
        void onCoinClick(Coin coin);

        void onFavoriteClick(Coin coin);
    }

    private static final Object PRICE_UP = new Object();
    private static final Object PRICE_DOWN = new Object();
    private static final Object PRICE_SAME = new Object();

    private final Listener listener;
    private Set<String> favorites = new HashSet<>();
    private Money money;

    CoinAdapter(Listener listener, Money money) {
        super(DIFF);
        this.listener = listener;
        this.money = money;
    }

    void setFavorites(Set<String> favorites) {
        this.favorites = favorites;
        notifyItemRangeChanged(0, getItemCount());
    }

    void setMoney(Money money) {
        this.money = money;
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCoinBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Coin coin = getItem(position);
        ItemCoinBinding b = holder.binding;
        b.symbol.setText(coin.base);
        Ui.loadIcon(b.icon, coin.base);
        boolean favorite = favorites.contains(coin.symbol);
        b.favorite.setImageResource(favorite ? R.drawable.ic_star : R.drawable.ic_star_outline);
        b.favorite.setContentDescription(b.getRoot().getContext().getString(
                favorite ? R.string.favorite_remove : R.string.favorite_add));
        b.getRoot().setOnClickListener(v -> listener.onCoinClick(coin));
        b.favorite.setOnClickListener(v -> listener.onFavoriteClick(coin));
        bindValues(b, coin);
    }

    /** Canlı tick'te yalnızca değerler güncellenir; logo ve dinleyiciler yeniden kurulmaz. */
    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        ItemCoinBinding b = holder.binding;
        bindValues(b, getItem(position));
        if (!payloads.contains(PRICE_UP) && !payloads.contains(PRICE_DOWN)) return;
        int color = payloads.contains(PRICE_UP) ? R.color.up : R.color.down;
        b.price.setTextColor(ContextCompat.getColor(b.getRoot().getContext(), color));
        b.price.postDelayed(() -> b.price.setTextColor(b.symbol.getCurrentTextColor()), 700);
    }

    private void bindValues(ItemCoinBinding b, Coin coin) {
        String volume = b.getRoot().getContext().getString(R.string.volume_short, money.compact(coin.quoteVolume));
        b.subtitle.setText(coin.name().equals(coin.base) ? volume : coin.name() + " · " + volume);
        b.price.setText(money.price(coin.last));
        b.price.setTextColor(b.symbol.getCurrentTextColor());
        Ui.bindChange(b.change, coin.changePercent());
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemCoinBinding binding;

        Holder(ItemCoinBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final DiffUtil.ItemCallback<Coin> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Coin a, @NonNull Coin b) {
            return a.symbol.equals(b.symbol);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Coin a, @NonNull Coin b) {
            return a.equals(b);
        }

        @Override
        public Object getChangePayload(@NonNull Coin a, @NonNull Coin b) {
            if (b.last > a.last) return PRICE_UP;
            if (b.last < a.last) return PRICE_DOWN;
            return PRICE_SAME;
        }
    };
}
