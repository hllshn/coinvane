package com.abraxel.cryptocurrency;

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

import com.abraxel.cryptocurrency.alerts.Store;
import com.abraxel.cryptocurrency.data.Binance;
import com.abraxel.cryptocurrency.data.Coin;
import com.abraxel.cryptocurrency.data.CoinFilter;
import com.abraxel.cryptocurrency.data.PriceSocket;
import com.abraxel.cryptocurrency.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;

public class MainActivity extends AppCompatActivity implements CoinAdapter.Listener, PriceSocket.Listener {
    /** Saniyede yüzlerce tick gelebilir; liste en fazla bu sıklıkta yenilenir. */
    private static final long RENDER_INTERVAL_MS = 1000;

    private ActivityMainBinding binding;
    private CoinAdapter adapter;
    private final Map<String, Coin> coins = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PriceSocket socket;
    private Call pending;
    private Set<String> favorites;
    private CoinFilter.Mode mode = CoinFilter.Mode.ALL;
    private String query = "";
    private boolean renderScheduled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Ui.padForSystemBars(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        adapter = new CoinAdapter(this);
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(null); // canlı güncellemelerde titremeyi önler
        binding.refresh.setOnRefreshListener(this::load);
        binding.tabs.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? View.NO_ID : ids.get(0);
            if (id == R.id.tab_favorites) mode = CoinFilter.Mode.FAVORITES;
            else if (id == R.id.tab_gainers) mode = CoinFilter.Mode.GAINERS;
            else if (id == R.id.tab_losers) mode = CoinFilter.Mode.LOSERS;
            else mode = CoinFilter.Mode.ALL;
            render();
            binding.list.scrollToPosition(0);
        });
        load();
    }

    @Override
    protected void onStart() {
        super.onStart();
        favorites = Store.favorites(this); // detay ekranında değişmiş olabilir
        adapter.setFavorites(favorites);
        render();
        startSocket();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopSocket();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pending != null) pending.cancel();
        handler.removeCallbacksAndMessages(null);
    }

    private void load() {
        binding.refresh.setRefreshing(true);
        if (pending != null) pending.cancel();
        pending = Binance.fetchTryCoins(new Binance.Result<>() {
            @Override
            public void onSuccess(List<Coin> list) {
                binding.refresh.setRefreshing(false);
                boolean symbolsChanged = !coins.keySet().equals(symbolsOf(list));
                coins.clear();
                for (Coin c : list) coins.put(c.symbol, c);
                render();
                if (symbolsChanged) {
                    stopSocket();
                    startSocket();
                }
            }

            @Override
            public void onError(Exception e) {
                binding.refresh.setRefreshing(false);
                Snackbar.make(binding.getRoot(), R.string.error_load, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry, v -> load())
                        .show();
            }
        });
    }

    private static Set<String> symbolsOf(List<Coin> list) {
        Set<String> set = new HashSet<>();
        for (Coin c : list) set.add(c.symbol);
        return set;
    }

    private void startSocket() {
        if (socket != null || coins.isEmpty() || !isStarted()) return;
        List<String> streams = new ArrayList<>();
        for (String symbol : coins.keySet()) streams.add(symbol.toLowerCase(Locale.ROOT) + "@miniTicker");
        socket = new PriceSocket(streams, this);
        socket.open();
    }

    private void stopSocket() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    private boolean isStarted() {
        return getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    @Override
    public void onMessage(String stream, JSONObject data) {
        try {
            Coin coin = Binance.parseMiniTicker(data);
            if (coins.containsKey(coin.symbol)) coins.put(coin.symbol, coin);
        } catch (JSONException ignored) {
            return;
        }
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
        binding.toolbar.setSubtitle(live ? getString(R.string.status_live, coins.size()) : getString(R.string.status_connecting));
    }

    private void render() {
        if (favorites == null) return;
        List<Coin> visible = CoinFilter.apply(coins.values(), query, mode, favorites);
        adapter.submitList(visible);
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
}
