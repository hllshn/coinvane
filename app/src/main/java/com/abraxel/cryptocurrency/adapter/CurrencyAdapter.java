package com.abraxel.cryptocurrency.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.abraxel.cryptocurrency.ChartDataActivity;
import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.RemindMeActivity;
import com.abraxel.cryptocurrency.constants.Constants;
import com.abraxel.cryptocurrency.model.CryptoCurrencies;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class CurrencyAdapter extends RecyclerView.Adapter<CurrencyAdapter.ViewHolder> implements Filterable {

    private List<CryptoCurrencies> cryptoCurrencies;
    private List<CryptoCurrencies> allCurrencies;
    private final Context context;
    private OnItemClickListener listener;

    // Tıklama olayları için arayüz
    public interface OnItemClickListener {
        void onItemClick(CryptoCurrencies cryptoCurrency);
    }

    // Tıklama dinleyicisini ayarlama metodu
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public CurrencyAdapter(List<CryptoCurrencies> cryptoCurrencies, Context context) {
        this.cryptoCurrencies = new ArrayList<>(cryptoCurrencies);
        this.context = context;
        this.allCurrencies = new ArrayList<>(cryptoCurrencies);
        // Performansı artırmak için sabit ID'leri etkinleştir
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        // Benzersiz ID olarak para birimi çiftinin hash kodunu kullan
        return cryptoCurrencies.get(position).getPair().hashCode();
    }

    public void updateData(List<CryptoCurrencies> newData) {
        if (newData == null) {
            return;
        }
        
        // DiffUtil kullanarak verimli güncelleme
        CryptoDiffCallback diffCallback = new CryptoDiffCallback(this.cryptoCurrencies, newData);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        
        // Veri listeleri güncelleniyor
        this.cryptoCurrencies.clear();
        this.cryptoCurrencies.addAll(newData);
        this.allCurrencies = new ArrayList<>(newData);
        
        // Adapter'a değişiklikleri bildir
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.crypto_item_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final CryptoCurrencies currency = cryptoCurrencies.get(position);
        holder.bindCurrency(currency, listener);
    }

    @Override
    public int getItemCount() {
        return cryptoCurrencies == null ? 0 : cryptoCurrencies.size();
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

    final Filter filter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<CryptoCurrencies> filteredList = new ArrayList<>();
            final FilterResults filterResults = new FilterResults();

            if (constraint == null || constraint.toString().isEmpty()) {
                filteredList.addAll(allCurrencies);
            } else {
                final String filterPattern = constraint.toString().toLowerCase().trim();
                for (CryptoCurrencies currency : allCurrencies) {
                    if (currency.getCoinName() != null && 
                        currency.getCoinName().toLowerCase().contains(filterPattern)) {
                        filteredList.add(currency);
                    }
                }
            }

            filterResults.values = filteredList;
            return filterResults;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            cryptoCurrencies.clear();
            cryptoCurrencies.addAll((List<CryptoCurrencies>) results.values);
            notifyDataSetChanged();
        }
    };

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView currencyImageView;
        MaterialButton remindMe;
        TextView coinName;
        TextView pair;
        TextView last;
        TextView percent;
        TextView high;
        TextView low;
        TextView askBid;
        TextView volume;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            currencyImageView = itemView.findViewById(R.id.currencyLogo);
            coinName = itemView.findViewById(R.id.coinName);
            pair = itemView.findViewById(R.id.pair);
            last = itemView.findViewById(R.id.card_price);
            percent = itemView.findViewById(R.id.percent);
            high = itemView.findViewById(R.id.high);
            low = itemView.findViewById(R.id.low);
            remindMe = itemView.findViewById(R.id.remind_me);
            askBid = itemView.findViewById(R.id.ask_bid);
            volume = itemView.findViewById(R.id.volume);
        }
        
        // Bu metot, ViewHolder'a veri bağlamak için kullanılır
        public void bindCurrency(CryptoCurrencies currency, final OnItemClickListener listener) {
            // Logo ve isim bilgisini ayarla
            currencyImageView.setImageResource(currency.getLogoResourceId());
            coinName.setText(currency.getCoinName());
            
            // Çift (pair) bilgisini göster - detay ekranında görüntülenecek
            pair.setText(currency.getPair());
            
            // Güncel fiyat kartta gösterilir
            last.setText(String.format("%s ₺", currency.getFormattedPrice()));
            last.setVisibility(View.VISIBLE);

            // Diğer detaylar detay ekranında gösterilecek
            percent.setVisibility(View.GONE);
            high.setVisibility(View.GONE);
            low.setVisibility(View.GONE);
            askBid.setVisibility(View.GONE);
            volume.setVisibility(View.GONE);
    
            // Hatırlatıcı butonu
            remindMe.setOnClickListener(view -> {
                Bundle bundle = new Bundle();
                bundle.putSerializable("reminderData", currency);
                Intent remindMeActivity = new Intent(view.getContext(), RemindMeActivity.class);
                remindMeActivity.putExtras(bundle);
                view.getContext().startActivity(remindMeActivity);
            });
    
            // Öğeye tıklama 
            itemView.setOnClickListener(view -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(currency);
                }
            });
        }
    }
    
    // DiffUtil için callback sınıfı
    private static class CryptoDiffCallback extends DiffUtil.Callback {
        private final List<CryptoCurrencies> oldList;
        private final List<CryptoCurrencies> newList;
        
        public CryptoDiffCallback(List<CryptoCurrencies> oldList, List<CryptoCurrencies> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }
        
        @Override
        public int getOldListSize() {
            return oldList.size();
        }
        
        @Override
        public int getNewListSize() {
            return newList.size();
        }
        
        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            // Para birimi çifti aynı mı kontrolü (benzersiz kimlik olarak kullanılır)
            String oldPair = oldList.get(oldItemPosition).getPair();
            String newPair = newList.get(newItemPosition).getPair();
            return oldPair != null && oldPair.equals(newPair);
        }
        
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            // İçerik aynı mı kontrolü (priz değerleri değişti mi)
            CryptoCurrencies oldItem = oldList.get(oldItemPosition);
            CryptoCurrencies newItem = newList.get(newItemPosition);
            
            return oldItem.getLast().equals(newItem.getLast()) &&
                   oldItem.getDailyPercent().equals(newItem.getDailyPercent()) &&
                   oldItem.getHigh().equals(newItem.getHigh()) &&
                   oldItem.getLow().equals(newItem.getLow());
        }
    }
}
