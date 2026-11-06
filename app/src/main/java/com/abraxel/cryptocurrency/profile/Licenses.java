package com.abraxel.cryptocurrency.profile;

import android.content.Context;

import com.abraxel.cryptocurrency.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Uygulamada kullanılan açık kaynak kütüphaneler ve lisansları (adlar çevrilmez). */
final class Licenses {
    private static final String[][] LIBRARIES = {
            {"AndroidX (AppCompat, Activity, Fragment, RecyclerView, SwipeRefreshLayout, WorkManager, ProfileInstaller)", "Apache License 2.0"},
            {"Material Components for Android", "Apache License 2.0"},
            {"OkHttp", "Apache License 2.0"},
            {"Glide", "BSD, MIT, Apache License 2.0"},
            {"MPAndroidChart", "Apache License 2.0"},
            {"Google Play In-App Review", "Play Core Software Development Kit Terms of Service"},
            {"cryptocurrency-icons (spothq)", "CC0 1.0"},
    };

    private Licenses() {
    }

    static void show(Context context) {
        StringBuilder text = new StringBuilder();
        for (String[] lib : LIBRARIES) {
            if (text.length() > 0) text.append("\n\n");
            text.append(lib[0]).append('\n').append(lib[1]);
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.licenses)
                .setMessage(text)
                .setPositiveButton(R.string.done, null)
                .show();
    }
}
