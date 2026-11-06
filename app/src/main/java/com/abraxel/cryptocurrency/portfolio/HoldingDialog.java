package com.abraxel.cryptocurrency.portfolio;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.Ui;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.Format;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.data.Store;
import com.abraxel.cryptocurrency.databinding.DialogHoldingBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Pozisyon ekleme/düzenleme (adet + isteğe bağlı ortalama alış fiyatı). Coin detayı ve Portföy kullanır. */
public final class HoldingDialog {
    private HoldingDialog() {
    }

    /**
     * @param priceUsd güncel fiyat (yeni pozisyonda alış fiyatı önerisi); bilinmiyorsa NaN
     * @param onChanged kaydedildi ya da silindi
     */
    public static void show(Activity activity, String symbol, Money money, double priceUsd, Runnable onChanged) {
        Holding existing = Store.holdings(activity).get(symbol);
        DialogHoldingBinding d = DialogHoldingBinding.inflate(activity.getLayoutInflater());
        Ui.decimalInput(d.amount);
        Ui.decimalInput(d.cost);
        d.amountLayout.setSuffixText(Coin.base(symbol));
        d.costLayout.setSuffixText(money.code);
        if (existing != null) {
            d.amount.setText(Format.plain(existing.amount));
            if (existing.hasCost()) d.cost.setText(Format.plain(money.convert(existing.avgCostUsd)));
        } else if (!Double.isNaN(priceUsd)) {
            d.cost.setText(Format.plain(money.convert(priceUsd)));
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(existing == null ? R.string.holding_add_coin : R.string.holding_edit_coin, Coin.base(symbol)))
                .setView(d.getRoot())
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.remove, (dlg, w) -> {
                Store.setHolding(activity, new Holding(symbol, 0, 0));
                onChanged.run();
            });
        }
        AlertDialog dialog = builder.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double amount = Format.parse(String.valueOf(d.amount.getText()));
            String costText = String.valueOf(d.cost.getText()).trim();
            double cost = costText.isEmpty() ? 0 : Format.parse(costText);
            if (Double.isNaN(amount) || amount <= 0) {
                d.amountLayout.setError(activity.getString(R.string.invalid_number));
                return;
            }
            if (Double.isNaN(cost) || cost < 0) {
                d.costLayout.setError(activity.getString(R.string.invalid_number));
                return;
            }
            Store.setHolding(activity, new Holding(symbol, amount, cost / money.rate));
            onChanged.run();
            dialog.dismiss();
        });
    }
}
