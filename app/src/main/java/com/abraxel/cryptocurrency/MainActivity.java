package com.abraxel.cryptocurrency;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.abraxel.cryptocurrency.data.Binance;
import com.abraxel.cryptocurrency.data.CoinFilter;
import com.abraxel.cryptocurrency.data.Fx;
import com.abraxel.cryptocurrency.data.Market;
import com.abraxel.cryptocurrency.data.Period;
import com.abraxel.cryptocurrency.databinding.ActivityMainBinding;
import com.abraxel.cryptocurrency.explore.ExploreFragment;
import com.abraxel.cryptocurrency.home.HomeFragment;
import com.abraxel.cryptocurrency.markets.MarketsFragment;
import com.abraxel.cryptocurrency.portfolio.PortfolioFragment;
import com.abraxel.cryptocurrency.profile.ProfileFragment;

/**
 * Uygulamanın kabuğu: sabit alt navigasyon (Ana Sayfa, Piyasalar, Keşfet, Portföy, Profil) ve sekmeler.
 * Sekmeler gizlenip gösterilir; piyasa verisi ({@link Market}) bu ekran görünürken çalışır.
 */
public class MainActivity extends AppCompatActivity {
    private static final String STATE_TAB = "tab";
    private static final int[] TAB_IDS = {R.id.nav_home, R.id.nav_markets, R.id.nav_explore, R.id.nav_portfolio, R.id.nav_profile};
    private static final String[] TAB_TAGS = {"home", "markets", "explore", "portfolio", "profile"};

    private ActivityMainBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @IdRes
    private int current = R.id.nav_home;

    private final OnBackPressedCallback backToHome = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            binding.nav.setSelectedItemId(R.id.nav_home);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();

        binding.nav.setItemActiveIndicatorEnabled(false);
        binding.nav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });
        binding.nav.setOnItemReselectedListener(item -> {
            Fragment f = getSupportFragmentManager().findFragmentByTag(tagOf(item.getItemId()));
            if (f instanceof Reselectable) ((Reselectable) f).onReselected();
        });
        getOnBackPressedDispatcher().addCallback(this, backToHome);

        if (savedInstanceState != null) current = savedInstanceState.getInt(STATE_TAB, R.id.nav_home);
        show(current);
        binding.nav.setSelectedItemId(current);

        if (savedInstanceState == null) { // ekran döndürme değil, gerçek açılış
            ReviewPrompt.countLaunch(this);
            handler.postDelayed(() -> ReviewPrompt.maybeAsk(this), 4000); // liste oturduktan sonra
        }
    }

    /** Sistem çubukları için boşluk; klavye açıkken alt menü gizlenir ve içerik klavyenin üstünde kalır. */
    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            boolean ime = insets.isVisible(WindowInsetsCompat.Type.ime());
            int bottom = ime ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : bars.bottom;
            v.setPadding(bars.left, bars.top, bars.right, bottom);
            binding.nav.setVisibility(ime ? View.GONE : View.VISIBLE);
            binding.navDivider.setVisibility(ime ? View.GONE : View.VISIBLE);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onStart() {
        // Sekmeler görünür olurken (super.onStart) veri katmanı çalışıyor olmalı
        Market.get(this).start();
        super.onStart();
        if (Fx.isStale(this)) {
            Fx.refresh(this, new Binance.Result<>() {
                @Override
                public void onSuccess(Void value) {
                    Market.get(MainActivity.this).refreshNow(); // yeni kurla yeniden çiz
                }

                @Override
                public void onError(Exception e) {
                    // eski kurlar (ya da USD) ile devam
                }
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Market.get(this).stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, current);
    }

    // --- Sekmeler ---

    private static String tagOf(@IdRes int id) {
        for (int i = 0; i < TAB_IDS.length; i++) if (TAB_IDS[i] == id) return TAB_TAGS[i];
        return TAB_TAGS[0];
    }

    private static Fragment create(@IdRes int id) {
        if (id == R.id.nav_markets) return new MarketsFragment();
        if (id == R.id.nav_explore) return new ExploreFragment();
        if (id == R.id.nav_portfolio) return new PortfolioFragment();
        if (id == R.id.nav_profile) return new ProfileFragment();
        return new HomeFragment();
    }

    private void show(@IdRes int id) {
        current = id;
        backToHome.setEnabled(id != R.id.nav_home);
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction t = fm.beginTransaction().setReorderingAllowed(true);
        for (int tabId : TAB_IDS) {
            Fragment f = fm.findFragmentByTag(tagOf(tabId));
            if (tabId == id) {
                if (f == null) t.add(R.id.container, create(tabId), tagOf(tabId));
                else t.show(f);
            } else if (f != null && !f.isHidden()) {
                t.hide(f);
            }
        }
        t.commitNow();
    }

    /** Piyasalar'ı belirli liste ve dönemle açar (Ana Sayfa / Keşfet'ten "Tümünü gör"). */
    public void openMarkets(CoinFilter.Type type, Period period, boolean search) {
        binding.nav.setSelectedItemId(R.id.nav_markets);
        Fragment f = getSupportFragmentManager().findFragmentByTag(tagOf(R.id.nav_markets));
        if (f instanceof MarketsFragment) ((MarketsFragment) f).open(type, period, search);
    }

    public void openTab(@IdRes int id) {
        binding.nav.setSelectedItemId(id);
    }

    /** Seçili sekmeye tekrar dokunulunca (ör. listeyi başa kaydır). */
    public interface Reselectable {
        void onReselected();
    }
}
