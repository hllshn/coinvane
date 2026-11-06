package com.abraxel.cryptocurrency.portfolio;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.abraxel.cryptocurrency.Ui;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.CoinFilter;
import com.abraxel.cryptocurrency.data.Market;
import com.abraxel.cryptocurrency.databinding.DialogCoinPickerBinding;
import com.abraxel.cryptocurrency.databinding.ItemCoinPickBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** "Varlık Ekle": aranabilir coin listesi (hacme göre). Seçilen coin için pozisyon diyaloğu açılır. */
public final class CoinPicker {
    public interface OnPick {
        void onPick(Coin coin);
    }

    private CoinPicker() {
    }

    public static void show(Activity activity, OnPick onPick) {
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        DialogCoinPickerBinding b = DialogCoinPickerBinding.inflate(activity.getLayoutInflater());
        Adapter adapter = new Adapter(coin -> {
            sheet.dismiss();
            onPick.onPick(coin);
        });
        b.list.setAdapter(adapter);
        List<Coin> all = new ArrayList<>(Market.get(activity).coins());
        adapter.submit(all);
        b.empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        b.query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                List<Coin> found = CoinFilter.apply(all, s.toString(), CoinFilter.Type.VOLUME, Collections.emptySet(),
                        c -> Double.NaN, CoinFilter.Sort.NATURAL, 0);
                adapter.submit(found);
                b.empty.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
        sheet.setContentView(b.getRoot());
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final OnPick onPick;
        private List<Coin> coins = new ArrayList<>();

        Adapter(OnPick onPick) {
            this.onPick = onPick;
        }

        @android.annotation.SuppressLint("NotifyDataSetChanged") // arama her harfte listeyi değiştirir
        void submit(List<Coin> coins) {
            this.coins = coins;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemCoinPickBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Coin c = coins.get(position);
            holder.b.symbol.setText(c.base);
            holder.b.name.setText(c.name());
            holder.b.name.setVisibility(c.name().equals(c.base) ? View.GONE : View.VISIBLE);
            Ui.loadIcon(holder.b.icon, c.base);
            holder.b.getRoot().setOnClickListener(v -> onPick.onPick(c));
        }

        @Override
        public int getItemCount() {
            return coins.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemCoinPickBinding b;

            Holder(ItemCoinPickBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
