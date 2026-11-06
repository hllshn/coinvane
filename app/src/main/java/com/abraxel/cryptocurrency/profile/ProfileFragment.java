package com.abraxel.cryptocurrency.profile;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.text.HtmlCompat;

import com.abraxel.cryptocurrency.BuildConfig;
import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.Ui;
import com.abraxel.cryptocurrency.alerts.AlertsActivity;
import com.abraxel.cryptocurrency.data.Market;
import com.abraxel.cryptocurrency.data.Settings;
import com.abraxel.cryptocurrency.data.Store;
import com.abraxel.cryptocurrency.databinding.FragmentProfileBinding;
import com.abraxel.cryptocurrency.databinding.ViewRowBinding;
import com.abraxel.cryptocurrency.ui.TabFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Currency;
import java.util.Locale;

/**
 * Profil: sosyal profil ya da hesap değil; mevcut ayarların giriş noktası. Sırasıyla tercihler (dil, para birimi,
 * tema, sistem renkleri), bildirimler ve fiyat alarmları, hakkında (sürüm, lisanslar, puanlama, veri kaynakları).
 */
public class ProfileFragment extends TabFragment {
    private static final int[] THEMES = {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES};

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        setup(binding.language, R.drawable.ic_language, R.string.settings_language, v -> chooseLanguage());
        setup(binding.currency, R.drawable.ic_currency, R.string.settings_currency, v -> chooseCurrency());
        setup(binding.theme, R.drawable.ic_theme, R.string.settings_theme, v -> chooseTheme());
        setup(binding.alerts, R.drawable.ic_bell, R.string.price_alerts,
                v -> startActivity(new Intent(requireContext(), AlertsActivity.class)));
        setup(binding.notificationSettings, R.drawable.ic_settings_notifications, R.string.notification_settings,
                v -> openNotificationSettings());
        setup(binding.version, R.drawable.ic_info, R.string.version, null);
        binding.version.value.setText(BuildConfig.VERSION_NAME);
        setup(binding.licenses, R.drawable.ic_code, R.string.licenses, v -> Licenses.show(requireContext()));
        setup(binding.rate, R.drawable.ic_rate, R.string.rate_app, v -> openStore());

        boolean dynamicSupported = Build.VERSION.SDK_INT >= 31;
        binding.dynamicRow.setVisibility(dynamicSupported ? View.VISIBLE : View.GONE);
        binding.dynamicColors.setChecked(Settings.dynamicColors(requireContext()));
        binding.dynamicColors.setOnCheckedChangeListener((b, checked) -> {
            Settings.setDynamicColors(requireContext(), checked);
            requireActivity().recreate();
        });

        binding.about.setText(HtmlCompat.fromHtml(getString(R.string.about_text, BuildConfig.VERSION_NAME),
                HtmlCompat.FROM_HTML_MODE_COMPACT));
        binding.about.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static void setup(ViewRowBinding row, @DrawableRes int icon, @StringRes int title, @Nullable View.OnClickListener click) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
        if (click == null) {
            row.chevron.setVisibility(View.GONE);
            row.row.setBackground(null);
        } else {
            row.row.setOnClickListener(click);
        }
    }

    @Override
    protected void onActivated() {
        bind();
    }

    @Override
    public void onMarketChanged(Market.Change change) {
        // Profil piyasa verisi göstermez
    }

    private void bind() {
        if (binding == null) return;
        binding.language.value.setText(languageName(currentLanguageTag()));
        binding.currency.value.setText(currencyLabel(Settings.currency(requireContext())));
        binding.theme.value.setText(getResources().getStringArray(R.array.theme_options)[indexOf(THEMES, Settings.theme(requireContext()))]);
        int alerts = Store.alerts(requireContext()).size();
        binding.alerts.value.setText(alerts == 0 ? getString(R.string.alerts_none)
                : getResources().getQuantityString(R.plurals.alerts_active, alerts, alerts));
    }

    // --- Dil ---

    private static String currentLanguageTag() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        return locales.isEmpty() ? "" : locales.get(0).toLanguageTag();
    }

    private String languageName(String tag) {
        if (tag.isEmpty()) return getString(R.string.language_system);
        for (String[] l : Settings.LANGUAGES) if (tag.equalsIgnoreCase(l[0]) || tag.startsWith(l[0] + "-")) return l[1];
        return Locale.forLanguageTag(tag).getDisplayName(Locale.forLanguageTag(tag));
    }

    private void chooseLanguage() {
        String[] names = new String[Settings.LANGUAGES.length + 1];
        names[0] = getString(R.string.language_system);
        int checked = 0;
        String current = currentLanguageTag();
        for (int i = 0; i < Settings.LANGUAGES.length; i++) {
            names[i + 1] = Settings.LANGUAGES[i][1];
            if (current.equalsIgnoreCase(Settings.LANGUAGES[i][0])) checked = i + 1;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    // AppCompat dili saklar ve açık ekranları yeniden oluşturur (Profil sekmesi korunur)
                    AppCompatDelegate.setApplicationLocales(which == 0
                            ? LocaleListCompat.getEmptyLocaleList()
                            : LocaleListCompat.forLanguageTags(Settings.LANGUAGES[which - 1][0]));
                })
                .show();
    }

    // --- Para birimi ---

    private String currencyLabel(String code) {
        Currency c = Currency.getInstance(code);
        Locale locale = Ui.locale(requireContext());
        return code + " · " + c.getDisplayName(locale) + " (" + c.getSymbol(locale) + ")";
    }

    private void chooseCurrency() {
        String[] labels = new String[Settings.CURRENCIES.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = currencyLabel(Settings.CURRENCIES.get(i));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_currency)
                .setSingleChoiceItems(labels, Settings.CURRENCIES.indexOf(Settings.currency(requireContext())), (dialog, which) -> {
                    dialog.dismiss();
                    Settings.setCurrency(requireContext(), Settings.CURRENCIES.get(which));
                    bind();
                })
                .show();
    }

    // --- Tema ---

    private void chooseTheme() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(R.array.theme_options, indexOf(THEMES, Settings.theme(requireContext())), (dialog, which) -> {
                    dialog.dismiss();
                    Settings.setTheme(requireContext(), THEMES[which]);
                })
                .show();
    }

    private static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) if (array[i] == value) return i;
        return 0;
    }

    // --- Bildirimler, puanlama ---

    private void openNotificationSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().getPackageName(), null)));
        }
    }

    private void openStore() {
        String pkg = requireContext().getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        }
    }
}
