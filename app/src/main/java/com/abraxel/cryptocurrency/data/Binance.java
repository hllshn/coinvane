package com.abraxel.cryptocurrency.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Binance'in anahtarsız, ücretsiz public market-data uç noktaları.
 * data-api.binance.vision yalnızca piyasa verisi sunar ve bölge kısıtlaması yoktur.
 */
public final class Binance {
    static final String REST = "https://data-api.binance.vision/api/v3/";
    static final String WS = "wss://data-stream.binance.vision/stream";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Dönemsel ticker isteğindeki en fazla sembol (Binance sınırı). */
    public static final int WINDOW_BATCH = 100;
    /** IP başına dakikalık ağırlık sınırı 6000; isteğe bağlı yüklerin (dönem, mini grafik) bırakacağı pay. */
    private static final int WEIGHT_BUDGET = 4200;

    private static volatile int usedWeight;
    private static volatile long usedWeightAt;
    private static volatile long backoffUntil;

    private Binance() {
    }

    static OkHttpClient client() {
        return CLIENT;
    }

    public interface Result<T> {
        void onSuccess(T value);

        void onError(Exception e);
    }

    interface Parser<T> {
        T parse(String body) throws JSONException;
    }

    /** Tüm USDT paritelerinin 24 saatlik özeti, hacme göre sıralı. */
    public static Call fetchCoins(Result<List<Coin>> result) {
        return get(REST + "ticker/24hr?type=MINI", Binance::parseCoins, result);
    }

    /** Belirli pariteler (portföy, detay). */
    public static Call fetchCoins(Collection<String> symbols, Result<List<Coin>> result) {
        return get(symbolsUrl(symbols), Binance::parseCoins, result);
    }

    /** Arka plan işçileri (alarm, widget) için senkron sorgu. */
    public static List<Coin> fetchCoinsBlocking(Collection<String> symbols) throws IOException {
        try {
            return parseCoins(getBlocking(CLIENT, symbolsUrl(symbols)));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    /** interval: Binance kline aralığı (15m, 1h, 4h, 1d, 1w). */
    public static Call fetchCandles(String symbol, String interval, Result<List<Candle>> result) {
        return fetchCandles(symbol, interval, 200, result);
    }

    public static Call fetchCandles(String symbol, String interval, int limit, Result<List<Candle>> result) {
        return get(REST + "klines?symbol=" + symbol + "&interval=" + interval + "&limit=" + limit,
                Binance::parseCandles, result);
    }

    /** startMs'ten itibaren mumlar (7 günden uzun dönemlerin açılış fiyatı için). Veri yoksa boş liste. */
    public static Call fetchCandlesFrom(String symbol, String interval, long startMs, int limit, Result<List<Candle>> result) {
        return get(REST + "klines?symbol=" + symbol + "&interval=" + interval + "&startTime=" + startMs + "&limit=" + limit,
                Binance::parseCandles, result);
    }

    /**
     * Kayan pencereli özet (windowSize: 5m, 15m, 1h, 4h, 7d...): sembol → pencerenin açılış fiyatı ve hacmi.
     * En fazla {@link #WINDOW_BATCH} sembol; ağırlığı sembol başına 4, istek başına en çok 200.
     */
    public static Call fetchWindow(Collection<String> symbols, String window, Result<Map<String, WindowStat>> result) {
        return get(REST + "ticker?type=MINI&windowSize=" + window + "&symbols=" + encodeSymbols(symbols),
                Binance::parseWindow, result);
    }

    /** Tek sembol için pencere özeti (detay ekranı). */
    public static Call fetchWindow(String symbol, String window, Result<Map<String, WindowStat>> result) {
        return get(REST + "ticker?type=MINI&windowSize=" + window + "&symbol=" + symbol, body -> {
            String json = body.trim();
            return parseWindow(json.startsWith("[") ? json : "[" + json + "]");
        }, result);
    }

    /** Dönemsel ticker isteğinin tahmini ağırlığı. */
    public static int windowWeight(int symbols) {
        return Math.min(200, symbols * 4);
    }

    /**
     * Ağırlık bütçesi: son yanıttaki "x-mbx-used-weight-1m" bu kadar ek isteğe izin veriyor mu? Binance 429/418
     * döndürdüyse Retry-After süresince hiçbir isteğe bağlı yük gönderilmez (IP yasağını önlemek için).
     */
    public static boolean canSpend(int weight) {
        long now = System.currentTimeMillis();
        if (now < backoffUntil) return false;
        int used = now - usedWeightAt > 60_000L ? 0 : usedWeight;
        return used + weight <= WEIGHT_BUDGET;
    }

    /** Bütçe dolduğunda tekrar denemeden önce beklenecek süre. */
    public static long retryDelayMs() {
        long now = System.currentTimeMillis();
        if (now < backoffUntil) return backoffUntil - now + 500;
        return Math.max(5_000L, 61_000L - (now - usedWeightAt));
    }

    private static void track(Response response) {
        String used = response.header("x-mbx-used-weight-1m");
        if (used != null) {
            try {
                usedWeight = Integer.parseInt(used.trim());
                usedWeightAt = System.currentTimeMillis();
            } catch (NumberFormatException ignored) {
                // başlık beklenmedik biçimde
            }
        }
        if (response.code() == 429 || response.code() == 418) {
            long seconds = 60;
            try {
                String retry = response.header("Retry-After");
                if (retry != null) seconds = Long.parseLong(retry.trim());
            } catch (NumberFormatException ignored) {
                // varsayılan 60 sn
            }
            backoffUntil = System.currentTimeMillis() + seconds * 1000L;
        }
    }

    private static String symbolsUrl(Collection<String> symbols) {
        return REST + "ticker/24hr?type=MINI&symbols=" + encodeSymbols(symbols);
    }

    private static String encodeSymbols(Collection<String> symbols) {
        try {
            return URLEncoder.encode(new JSONArray(symbols).toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String getBlocking(OkHttpClient client, String url) throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
            track(response);
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            return response.body().string();
        }
    }

    static <T> Call get(String url, Parser<T> parser, Result<T> result) {
        Call call = CLIENT.newCall(new Request.Builder().url(url).build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!call.isCanceled()) MAIN.post(() -> result.onError(e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody body = response.body()) {
                    track(response);
                    if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
                    T value = parser.parse(body.string());
                    if (!call.isCanceled()) MAIN.post(() -> result.onSuccess(value));
                } catch (IOException | JSONException e) {
                    if (!call.isCanceled()) MAIN.post(() -> result.onError(e));
                }
            }
        });
        return call;
    }

    // --- Ayrıştırıcılar (birim testli) ---

    /** Son işlemi bundan eski olan ticker donmuştur (Binance delist edilen paritelerin eski özetini döndürmeye devam eder). */
    static final long STALE_MS = 6 * 3_600_000L;

    static List<Coin> parseCoins(String json) throws JSONException {
        return parseCoins(json, System.currentTimeMillis());
    }

    static List<Coin> parseCoins(String json, long nowMs) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<Coin> coins = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            // Son 24 saatte hiç işlem görmeyenler (askıya alınmış/delist) gösterilmez
            if (!Coin.isListable(o.getString("symbol")) || o.optLong("count") == 0) continue;
            // Delist edilmiş pariteler: sayaç dolu ama özet günler öncesine ait (ör. 195 USDT paritesi)
            long closeTime = o.optLong("closeTime", 0);
            if (closeTime > 0 && nowMs - closeTime > STALE_MS) continue;
            coins.add(new Coin(o.getString("symbol"),
                    o.getDouble("lastPrice"), o.getDouble("openPrice"),
                    o.getDouble("highPrice"), o.getDouble("lowPrice"),
                    o.getDouble("quoteVolume")));
        }
        coins.sort((a, b) -> Double.compare(b.quoteVolume, a.quoteVolume));
        return coins;
    }

    /** /api/v3/ticker?windowSize= yanıtı. Açılış fiyatı 0 olan (işlem görmemiş) semboller atlanır. */
    static Map<String, WindowStat> parseWindow(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        Map<String, WindowStat> map = new HashMap<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            double open = o.optDouble("openPrice", 0);
            if (!(open > 0)) continue;
            map.put(o.getString("symbol"), new WindowStat(open, o.optDouble("lastPrice", open), o.optDouble("quoteVolume", 0),
                    o.optLong("openTime", 0), o.optLong("closeTime", 0)));
        }
        return map;
    }

    static List<Candle> parseCandles(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<Candle> candles = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONArray k = array.getJSONArray(i);
            candles.add(new Candle(k.getLong(0), k.getDouble(1), k.getDouble(2), k.getDouble(3), k.getDouble(4),
                    k.getDouble(5)));
        }
        return candles;
    }

    /** WebSocket "<symbol>@miniTicker" olayı. */
    public static Coin parseMiniTicker(JSONObject d) throws JSONException {
        return new Coin(d.getString("s"), d.getDouble("c"), d.getDouble("o"),
                d.getDouble("h"), d.getDouble("l"), d.getDouble("q"));
    }

    /** WebSocket "<symbol>@kline_<interval>" olayı. */
    public static Candle parseKlineEvent(JSONObject d) throws JSONException {
        JSONObject k = d.getJSONObject("k");
        return new Candle(k.getLong("t"), k.getDouble("o"), k.getDouble("h"), k.getDouble("l"), k.getDouble("c"),
                k.getDouble("v"));
    }
}
