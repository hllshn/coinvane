package com.abraxel.cryptocurrency;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.abraxel.cryptocurrency.data.Settings;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(Settings.theme(this));
        // Android 12+ "sistem renkleri" (Material You). Ayar her ekran oluşturulurken okunur, anında uygulanır.
        DynamicColors.applyToActivitiesIfAvailable(this, new DynamicColorsOptions.Builder()
                .setPrecondition((activity, theme) -> Settings.dynamicColors(activity))
                .build());
    }
}
