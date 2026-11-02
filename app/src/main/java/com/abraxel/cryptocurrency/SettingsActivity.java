package com.abraxel.cryptocurrency;

import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.text.HtmlCompat;

import com.abraxel.cryptocurrency.data.Settings;
import com.abraxel.cryptocurrency.databinding.ActivitySettingsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Currency;
import java.util.Locale;

/** Dil, para birimi, tema ve sistem renkleri. */
public class SettingsActivity extends AppCompatActivity {
    private static final int[] THEMES = {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES};

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Ui.padForSystemBars(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.languageRow.setOnClickListener(v -> chooseLanguage());
        binding.currencyRow.setOnClickListener(v -> chooseCurrency());
        binding.themeRow.setOnClickListener(v -> chooseTheme());

        boolean dynamicSupported = Build.VERSION.SDK_INT >= 31;
        binding.dynamicColors.setVisibility(dynamicSupported ? View.VISIBLE : View.GONE);
        binding.dynamicColors.setChecked(Settings.dynamicColors(this));
        binding.dynamicColors.setOnCheckedChangeListener((b, checked) -> {
            Settings.setDynamicColors(this, checked);
            recreate();
        });

        binding.about.setText(HtmlCompat.fromHtml(getString(R.string.about_text, BuildConfig.VERSION_NAME),
                HtmlCompat.FROM_HTML_MODE_COMPACT));
        binding.about.setMovementMethod(LinkMovementMethod.getInstance());
        bind();
    }

    private void bind() {
        binding.languageValue.setText(languageName(currentLanguageTag()));
        String code = Settings.currency(this);
        binding.currencyValue.setText(currencyLabel(code));
        int theme = Settings.theme(this);
        binding.themeValue.setText(getResources().getStringArray(R.array.theme_options)[indexOf(THEMES, theme)]);
    }

    private String currentLanguageTag() {
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
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    // AppCompat dili saklar ve açık ekranları yeniden oluşturur
                    AppCompatDelegate.setApplicationLocales(which == 0
                            ? LocaleListCompat.getEmptyLocaleList()
                            : LocaleListCompat.forLanguageTags(Settings.LANGUAGES[which - 1][0]));
                })
                .show();
    }

    private String currencyLabel(String code) {
        Currency c = Currency.getInstance(code);
        return code + " · " + c.getDisplayName(Ui.locale(this)) + " (" + c.getSymbol(Ui.locale(this)) + ")";
    }

    private void chooseCurrency() {
        String[] labels = new String[Settings.CURRENCIES.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = currencyLabel(Settings.CURRENCIES.get(i));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_currency)
                .setSingleChoiceItems(labels, Settings.CURRENCIES.indexOf(Settings.currency(this)), (dialog, which) -> {
                    dialog.dismiss();
                    Settings.setCurrency(this, Settings.CURRENCIES.get(which));
                    bind();
                })
                .show();
    }

    private void chooseTheme() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(R.array.theme_options, indexOf(THEMES, Settings.theme(this)), (dialog, which) -> {
                    dialog.dismiss();
                    Settings.setTheme(this, THEMES[which]);
                })
                .show();
    }

    private static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) if (array[i] == value) return i;
        return 0;
    }
}
