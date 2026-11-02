package com.abraxel.cryptocurrency;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.abraxel.cryptocurrency.alerts.Alerts;
import com.abraxel.cryptocurrency.alerts.PriceAlert;
import com.abraxel.cryptocurrency.data.Binance;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.CoinFilter;
import com.abraxel.cryptocurrency.data.Fx;
import com.abraxel.cryptocurrency.data.Money;
import com.abraxel.cryptocurrency.data.PriceSocket;
import com.abraxel.cryptocurrency.data.Store;
import com.abraxel.cryptocurrency.databinding.ActivityMainBinding;
import com.abraxel.cryptocurrency.portfolio.PortfolioActivity;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;

public class MainActivity extends AppCompatActivity implements CoinAdapter.Listener, PriceSocket.Listener {
    /** Gelen tick'ler toplanır; liste en fazla bu sıklıkta yenilenir. */
    private static final long RENDER_INTERVAL_MS = 1000;
    /** Ekran dışındaki coinler (sıralama için) bu aralıkla REST'ten tazelenir. */
    private static final long REFRESH_INTERVAL_MS = 60_000;
    /** Görünür satırların üstünde/altında da canlı tutulacak satır sayısı. */
    private static final int PREFETCH_ROWS = 6;

    private ActivityMainBinding binding;
    private CoinAdapter adapter;
    private final Map<String, Coin> coins = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PriceSocket socket = new PriceSocket(this);
    private Call pending;
    private Set<String> favorites;
    private CoinFilter.Mode mode = CoinFilter.Mode.ALL;
    private String query = "";
    private boolean renderScheduled;
    private boolean live;

    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            load(false);
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };
    private final Runnable updateStreams = this::updateStreams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Ui.padForSystemBars(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        adapter = new CoinAdapter(this, Money.current(this));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null); // canlı güncellemelerde titremeyi önler
        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                scheduleStreamUpdate();
            }
        });
        binding.refresh.setOnRefreshListener(() -> load(true));
        binding.tabs.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? View.NO_ID : ids.get(0);
            if (id == R.id.tab_favorites) mode = CoinFilter.Mode.FAVORITES;
            else if (id == R.id.tab_gainers) mode = CoinFilter.Mode.GAINERS;
            else if (id == R.id.tab_losers) mode = CoinFilter.Mode.LOSERS;
            else mode = CoinFilter.Mode.ALL;
            render();
            binding.list.scrollToPosition(0);
        });
        binding.refresh.setRefreshing(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Ayarlar/detay ekranında değişmiş olabilir
        favorites = Store.favorites(this);
        adapter.setFavorites(favorites);
        adapter.setMoney(Money.current(this));
        if (Fx.isStale(this)) {
            Fx.refresh(this, new Binance.Result<>() {
                @Override
                public void onSuccess(Void value) {
                    adapter.setMoney(Money.current(MainActivity.this));
                }

                @Override
                public void onError(Exception e) {
                    // eski kurlar (ya da USD) ile devam
                }
            });
        }
        render();
        socket.open();
        handler.post(periodicRefresh);
    }

    @Override
    protected void onStop() {
        super.onStop();
        socket.close();
        handler.removeCallbacks(periodicRefresh);
        handler.removeCallbacks(updateStreams);
        live = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pending != null) pending.cancel();
        handler.removeCallbacksAndMessages(null);
    }

    private void load(boolean userInitiated) {
        if (pending != null) pending.cancel();
        pending = Binance.fetchCoins(new Binance.Result<>() {
            @Override
            public void onSuccess(List<Coin> list) {
                binding.refresh.setRefreshing(false);
                coins.clear();
                for (Coin c : list) coins.put(c.symbol, c);
                render();
                updateSubtitle();
            }

            @Override
            public void onError(Exception e) {
                binding.refresh.setRefreshing(false);
                if (!userInitiated && !coins.isEmpty()) return; // arka plan tazelemesi: sessizce geç
                Snackbar.make(binding.getRoot(), R.string.error_load, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry, v -> {
                            binding.refresh.setRefreshing(true);
                            load(true);
                        })
                        .show();
            }
        });
    }

    // --- Canlı akış: yalnızca ekrandaki satırlar + alarmı olan coinler ---

    private void scheduleStreamUpdate() {
        handler.removeCallbacks(updateStreams);
        handler.postDelayed(updateStreams, 300);
    }

    private void updateStreams() {
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
        Set<String> symbols = new LinkedHashSet<>();
        LinearLayoutManager lm = (LinearLayoutManager) binding.list.getLayoutManager();
        List<Coin> shown = adapter.getCurrentList();
        if (lm != null && !shown.isEmpty()) {
            int first = Math.max(0, lm.findFirstVisibleItemPosition() - PREFETCH_ROWS);
            int last = Math.min(shown.size() - 1, Math.max(lm.findLastVisibleItemPosition(), 0) + PREFETCH_ROWS);
            for (int i = first; i <= last; i++) symbols.add(shown.get(i).symbol);
        }
        for (PriceAlert a : Store.alerts(this)) symbols.add(a.symbol);
        Set<String> streams = new LinkedHashSet<>();
        for (String s : symbols) streams.add(s.toLowerCase(Locale.ROOT) + "@miniTicker");
        socket.setStreams(streams);
    }

    @Override
    public void onMessage(String stream, JSONObject data) {
        Coin coin;
        try {
            coin = Binance.parseMiniTicker(data);
        } catch (JSONException e) {
            return;
        }
        if (coins.containsKey(coin.symbol)) coins.put(coin.symbol, coin);
        Alerts.check(this, coin);
        if (!renderScheduled) {
            renderScheduled = true;
            handler.postDelayed(() -> {
                renderScheduled = false;
                render();
            }, RENDER_INTERVAL_MS);
        }
    }

    @Override
    public void onStatusChanged(boolean live) {
        this.live = live;
        updateSubtitle();
    }

    private void updateSubtitle() {
        binding.toolbar.setSubtitle(live
                ? getResources().getQuantityString(R.plurals.status_live, coins.size(), coins.size())
                : getString(R.string.status_connecting));
    }

    private void render() {
        if (favorites == null) return;
        List<Coin> visible = CoinFilter.apply(coins.values(), query, mode, favorites);
        adapter.submitList(visible, this::scheduleStreamUpdate);
        boolean empty = visible.isEmpty() && !coins.isEmpty();
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.empty.setText(mode == CoinFilter.Mode.FAVORITES && query.isEmpty()
                ? R.string.empty_favorites : R.string.empty_results);
    }

    @Override
    public void onCoinClick(Coin coin) {
        startActivity(CoinDetailActivity.intent(this, coin.symbol));
    }

    @Override
    public void onFavoriteClick(Coin coin) {
        Store.toggleFavorite(this, coin.symbol);
        favorites = Store.favorites(this);
        adapter.setFavorites(favorites);
        if (mode == CoinFilter.Mode.FAVORITES) render();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) item.getActionView();
        searchView.setQueryHint(getString(R.string.search));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String text) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String text) {
                query = text;
                render();
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_portfolio) {
            startActivity(new Intent(this, PortfolioActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
