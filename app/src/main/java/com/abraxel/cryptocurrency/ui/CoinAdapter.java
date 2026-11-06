package com.abraxel.cryptocurrency.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.Ui;
import com.abraxel.cryptocurrency.data.CoinFilter;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.databinding.ItemCoinBinding;

import java.util.List;

/**
 * Ortak coin satırı: logo → kısaltma ve hacim → mini grafik → fiyat ve değişim → favori yıldızı.
 * Fark hesabı arka planda; canlı tick'te yalnızca değerler yenilenir, fiyat kısa süre renklenir.
 */
public class CoinAdapter extends ListAdapter<CoinRow, CoinAdapter.Holder> {
    public interface Listener {
        void onCoinClick(CoinRow row);

        void onFavoriteClick(CoinRow row);
    }

    private static final Object VALUES = new Object();
    private static final Object PRICE_UP = new Object();
    private static final Object PRICE_DOWN = new Object();

    private final Listener listener;
    private Money money;

    public CoinAdapter(Listener listener, Money money) {
        super(DIFF);
        this.listener = listener;
        this.money = money;
    }

    public void setMoney(Money money) {
        if (money.code.equals(this.money.code) && money.rate == this.money.rate) return;
        this.money = money;
        notifyItemRangeChanged(0, getItemCount(), VALUES);
    }

    /** Satırlar arasına ince, düşük kontrastlı ayırıcı (kenar boşluklu). */
    public static void addDividers(RecyclerView list) {
        androidx.recyclerview.widget.DividerItemDecoration d = new androidx.recyclerview.widget.DividerItemDecoration(
                list.getContext(), androidx.recyclerview.widget.DividerItemDecoration.VERTICAL);
        android.graphics.drawable.Drawable drawable = ContextCompat.getDrawable(list.getContext(), R.drawable.divider_inset);
        if (drawable != null) d.setDrawable(drawable);
        list.addItemDecoration(d);
    }

    /**
     * Mini grafik için yer var mı? Dar ekranda ya da büyük yazı tipinde gizlenir; logo/coin ve fiyat/yüzde
     * önceliklidir (yazı ölçeğine göre düzeltilmiş genişlik en az 400dp olmalı).
     */
    public static boolean sparkFits(Context context) {
        android.content.res.Configuration c = context.getResources().getConfiguration();
        return c.screenWidthDp / Math.max(1f, c.fontScale) >= 400;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCoinBinding b = ItemCoinBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        b.spark.setVisibility(sparkFits(parent.getContext()) ? View.VISIBLE : View.GONE);
        return new Holder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CoinRow row = getItem(position);
        ItemCoinBinding b = holder.binding;
        b.symbol.setText(row.coin.base);
        Ui.loadIcon(b.icon, row.coin.base);
        b.getRoot().setOnClickListener(v -> {
            int p = holder.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onCoinClick(getItem(p));
        });
        b.favorite.setOnClickListener(v -> {
            int p = holder.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onFavoriteClick(getItem(p));
        });
        bindValues(b, row);
    }

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
        b.price.postDelayed(() -> b.price.setTextColor(ContextCompat.getColor(b.price.getContext(), R.color.text_primary)), 700);
    }

    private void bindValues(ItemCoinBinding b, CoinRow row) {
        Context c = b.getRoot().getContext();
        b.subtitle.setText(row.note != null ? row.note : c.getString(R.string.volume_short, money.compact(row.coin.quoteVolume)));
        b.price.setText(money.price(row.coin.last));
        b.price.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
        Ui.bindChange(b.change, row.change);
        int direction = CoinFilter.direction(row.change);
        int sparkColor = ContextCompat.getColor(c, direction > 0 ? R.color.up : direction < 0 ? R.color.down : R.color.neutral);
        b.spark.setData(row.spark, sparkColor);
        b.favorite.setImageResource(row.favorite ? R.drawable.ic_star : R.drawable.ic_star_outline);
        b.favorite.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c,
                row.favorite ? R.color.brand : R.color.text_secondary)));
        b.favorite.setContentDescription(c.getString(row.favorite ? R.string.favorite_remove : R.string.favorite_add));
        b.getRoot().setContentDescription(row.coin.base + ", " + b.price.getText() + ", " + b.change.getText());
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        final ItemCoinBinding binding;

        Holder(ItemCoinBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final DiffUtil.ItemCallback<CoinRow> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull CoinRow a, @NonNull CoinRow b) {
            return a.coin.symbol.equals(b.coin.symbol);
        }

        @Override
        public boolean areContentsTheSame(@NonNull CoinRow a, @NonNull CoinRow b) {
            return a.equals(b);
        }

        @Override
        public Object getChangePayload(@NonNull CoinRow a, @NonNull CoinRow b) {
            if (b.coin.last > a.coin.last) return PRICE_UP;
            if (b.coin.last < a.coin.last) return PRICE_DOWN;
            return VALUES;
        }
    };
}
