package com.abraxel.cryptocurrency.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.abraxel.cryptocurrency.alerts.Alerts;
import com.abraxel.cryptocurrency.alerts.PriceAlert;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;

/**
 * Ana ekranın (Ana Sayfa / Piyasalar / Keşfet / Portföy sekmeleri) ortak piyasa verisi. Ana aktivite görünürken
 * çalışır ({@link #start} / {@link #stop}); tek WebSocket yalnızca o an görünen sekmenin istediği coinleri
 * (+ alarmı olanları) dinler. Dönemsel değişimler istendiğinde ve ağırlık bütçesi izin verdiğinde yüklenir.
 * Tüm çağrılar ana thread'dedir.
 */
public final class Market implements PriceSocket.Listener {
    public enum Change {
        /** Yalnızca canlı fiyatlar değişti: satır sırası korunmalı. */
        PRICES,
        /** REST verisi (24s listesi ya da bir dönem) geldi: kontrollü yeniden sıralama yapılabilir. */
        DATA,
        /** Bağlantı durumu / hata. */
        STATUS
    }

    public interface Listener {
        void onMarketChanged(Change change);
    }

    /** Liste REST'ten bu aralıkla tazelenir (canlı akış yalnızca görünen satırlar içindir). */
    private static final long REFRESH_MS = 60_000L;
    private static final long RENDER_THROTTLE_MS = 1000L;

    private static Market instance;

    private final Context app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PriceSocket socket = new PriceSocket(this);
    private final Map<String, Coin> coins = new LinkedHashMap<>();
    private final Map<Period, Window> windows = new EnumMap<>(Period.class);
    private final Map<Object, Collection<String>> streamOwners = new HashMap<>();
    private final Map<Object, Period> periodOwners = new HashMap<>();
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private Call coinsCall;
    private boolean started;
    private boolean live;
    private boolean loading;
    private boolean failed;
    private long loadedAt;
    private boolean pricesScheduled;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            load();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private final Runnable notifyPrices = () -> {
        pricesScheduled = false;
        dispatch(Change.PRICES);
    };

    private Market(Context context) {
        app = context.getApplicationContext();
    }

    public static Market get(Context context) {
        if (instance == null) instance = new Market(context);
        return instance;
    }

    // --- Yaşam döngüsü ---

    public void start() {
        if (started) return;
        started = true;
        socket.open();
        long age = System.currentTimeMillis() - loadedAt;
        handler.postDelayed(refresh, coins.isEmpty() || age >= REFRESH_MS ? 0 : REFRESH_MS - age);
        syncStreams();
        for (Period p : new LinkedHashSet<>(periodOwners.values())) ensure(p);
    }

    public void stop() {
        if (!started) return;
        started = false;
        socket.close();
        handler.removeCallbacksAndMessages(null);
        pricesScheduled = false;
        for (Window w : windows.values()) w.cancel();
        if (coinsCall != null) coinsCall.cancel();
        loading = false;
        live = false;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void dispatch(Change change) {
        for (Listener l : new ArrayList<>(listeners)) l.onMarketChanged(change);
    }

    // --- 24 saatlik liste ---

    /** Kullanıcı yenilemesi: 24s listesi ve istenen dönemler hemen tazelenir. */
    public void refreshNow() {
        for (Window w : windows.values()) w.fetchedAt = 0;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    private void load() {
        if (loading) return;
        loading = true;
        coinsCall = Binance.fetchCoins(new Binance.Result<>() {
            @Override
            public void onSuccess(List<Coin> list) {
                loading = false;
                failed = false;
                loadedAt = System.currentTimeMillis();
                coins.clear();
                for (Coin c : list) coins.put(c.symbol, c);
                dispatch(Change.DATA);
                for (Period p : new LinkedHashSet<>(periodOwners.values())) ensure(p);
            }

            @Override
            public void onError(Exception e) {
                loading = false;
                failed = true;
                dispatch(Change.STATUS);
            }
        });
    }

    public boolean hasCoins() {
        return !coins.isEmpty();
    }

    public boolean isLoading() {
        return loading && coins.isEmpty();
    }

    /** İlk yükleme başarısız ve elde veri yok (bağlantı yok). */
    public boolean isOffline() {
        return failed && coins.isEmpty();
    }

    /** Canlı akış bağlı mı (sahte "canlı" göstermemek için). */
    public boolean isLive() {
        return live;
    }

    /** Hacme göre sıralı tüm pariteler. */
    public Collection<Coin> coins() {
        return Collections.unmodifiableCollection(coins.values());
    }

    public Coin coin(String symbol) {
        return coins.get(symbol);
    }

    public int size() {
        return coins.size();
    }

    // --- Dönemsel değişim ---

    /** Görünen ekranın istediği dönem; null ile bırakılır. Gerekirse yükleme başlatır. */
    public void setPeriod(Object owner, Period period) {
        if (period == null || period == Period.D1) periodOwners.remove(owner);
        else periodOwners.put(owner, period);
        if (period != null) ensure(period);
    }

    /** Seçili dönemdeki yüzde değişim (güncel fiyatla); bilinmiyorsa NaN. */
    public double change(Coin coin, Period period) {
        if (period == Period.D1) return coin.open > 0 ? (coin.last - coin.open) / coin.open * 100 : Double.NaN;
        Window w = windows.get(period);
        WindowStat stat = w == null ? null : w.stats.get(coin.symbol);
        return stat == null || !stat.covers(period.durationMs()) ? Double.NaN : stat.changePercent(coin.last);
    }

    /** Son pencere hacmi (USD); bilinmiyorsa NaN. */
    public double windowVolume(Coin coin, Period period) {
        if (period == Period.D1) return coin.quoteVolume;
        Window w = windows.get(period);
        WindowStat stat = w == null ? null : w.stats.get(coin.symbol);
        return stat == null ? Double.NaN : stat.quoteVolume;
    }

    /** Dönem verisi hiç gelmedi ve şu an yükleniyor. */
    public boolean isLoading(Period period) {
        if (period == Period.D1) return isLoading();
        if (isLoading()) return true;
        Window w = windows.get(period);
        return w == null || (w.stats.isEmpty() && (w.pending > 0 || w.waiting));
    }

    /** Dönem verisi en az bir kez geldi. */
    public boolean hasData(Period period) {
        if (period == Period.D1) return hasCoins();
        Window w = windows.get(period);
        return w != null && !w.stats.isEmpty();
    }

    private void ensure(Period period) {
        if (period == Period.D1 || period == Period.D30 || coins.isEmpty() || !started) return;
        Window w = windows.get(period);
        if (w == null) {
            w = new Window(period);
            windows.put(period, w);
        }
        w.fetchIfStale();
    }

    /** Bir dönemin tüm pariteler için penceresi; 100'lük gruplar halinde yüklenir. */
    private final class Window {
        final Period period;
        Map<String, WindowStat> stats = new HashMap<>();
        final List<Call> calls = new ArrayList<>();
        long fetchedAt;
        int pending;
        boolean waiting; // ağırlık bütçesi için bekliyor
        final Runnable retry;

        Window(Period period) {
            this.period = period;
            this.retry = this::fetchIfStale;
        }

        void cancel() {
            for (Call c : calls) c.cancel();
            calls.clear();
            pending = 0;
            handler.removeCallbacks(retry);
        }

        void fetchIfStale() {
            if (pending > 0 || !started || !periodOwners.containsValue(period)) return;
            if (System.currentTimeMillis() - fetchedAt < period.ttlMs) return;
            List<String> symbols = new ArrayList<>(coins.keySet());
            int weight = 0;
            for (int i = 0; i < symbols.size(); i += Binance.WINDOW_BATCH) {
                weight += Binance.windowWeight(Math.min(Binance.WINDOW_BATCH, symbols.size() - i));
            }
            if (!Binance.canSpend(weight)) {
                waiting = true;
                handler.removeCallbacks(retry);
                handler.postDelayed(retry, Binance.retryDelayMs());
                return;
            }
            waiting = false;
            Map<String, WindowStat> next = new HashMap<>();
            calls.clear();
            for (int i = 0; i < symbols.size(); i += Binance.WINDOW_BATCH) {
                List<String> batch = symbols.subList(i, Math.min(symbols.size(), i + Binance.WINDOW_BATCH));
                pending++;
                calls.add(Binance.fetchWindow(new ArrayList<>(batch), period.window, new Binance.Result<>() {
                    @Override
                    public void onSuccess(Map<String, WindowStat> value) {
                        next.putAll(value);
                        done();
                    }

                    @Override
                    public void onError(Exception e) {
                        done();
                    }

                    private void done() {
                        if (--pending > 0) return;
                        calls.clear();
                        if (!next.isEmpty()) {
                            stats = next;
                            fetchedAt = System.currentTimeMillis();
                        } else {
                            // Hiçbiri gelmedi: kısa süre sonra yeniden dene, eski veri (varsa) kalsın
                            handler.postDelayed(retry, 15_000L);
                        }
                        dispatch(Change.DATA);
                    }
                }));
            }
        }
    }

    // --- Canlı akış ---

    /** Görünen ekranın canlı izlemek istediği semboller (null/boş: bırak). */
    public void setStreams(Object owner, Collection<String> symbols) {
        if (symbols == null || symbols.isEmpty()) streamOwners.remove(owner);
        else streamOwners.put(owner, new ArrayList<>(symbols));
        syncStreams();
    }

    /** Alarm eklenip silindiğinde çağrılır. */
    public void syncStreams() {
        Set<String> symbols = new LinkedHashSet<>();
        for (Collection<String> s : streamOwners.values()) symbols.addAll(s);
        for (PriceAlert a : Store.alerts(app)) symbols.add(a.symbol);
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
        Alerts.check(app, coin);
        if (!pricesScheduled) {
            pricesScheduled = true;
            handler.postDelayed(notifyPrices, RENDER_THROTTLE_MS);
        }
    }

    @Override
    public void onStatusChanged(boolean live) {
        this.live = live;
        dispatch(Change.STATUS);
    }
}
