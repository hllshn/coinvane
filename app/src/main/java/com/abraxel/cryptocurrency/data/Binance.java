package com.abraxel.cryptocurrency.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
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

    /** Tüm TRY paritelerinin 24 saatlik özeti, hacme göre sıralı. */
    public static Call fetchTryCoins(Result<List<Coin>> result) {
        return get(REST + "ticker/24hr?type=MINI", Binance::parseTryCoins, result);
    }

    /** Tek paritenin 24 saatlik özeti (detay ekranının ilk açılışı için). */
    public static Call fetchCoin(String symbol, Result<Coin> result) {
        return get(REST + "ticker/24hr?type=MINI&symbol=" + symbol, body -> parseTicker(new JSONObject(body)), result);
    }

    /** interval: Binance kline aralığı (15m, 1h, 4h, 1d, 1w). */
    public static Call fetchCandles(String symbol, String interval, Result<List<Candle>> result) {
        return get(REST + "klines?symbol=" + symbol + "&interval=" + interval + "&limit=200",
                Binance::parseCandles, result);
    }

    /** Arka plan işçisi için senkron fiyat sorgusu. */
    public static Map<String, Double> fetchPricesBlocking(Collection<String> symbols) throws IOException {
        JSONArray list = new JSONArray(symbols);
        String url = REST + "ticker/price?symbols=" + URLEncoder.encode(list.toString(), "UTF-8");
        try (Response response = CLIENT.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            return parsePrices(response.body().string());
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private static <T> Call get(String url, Parser<T> parser, Result<T> result) {
        Call call = CLIENT.newCall(new Request.Builder().url(url).build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!call.isCanceled()) MAIN.post(() -> result.onError(e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
                    T value = parser.parse(body.string());
                    MAIN.post(() -> result.onSuccess(value));
                } catch (IOException | JSONException e) {
                    MAIN.post(() -> result.onError(e));
                }
            }
        });
        return call;
    }

    // --- Ayrıştırıcılar (birim testli) ---

    static List<Coin> parseTryCoins(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<Coin> coins = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            String symbol = o.getString("symbol");
            // Son 24 saatte hiç işlem görmeyenler (askıya alınmış/delist) gösterilmez
            if (!symbol.endsWith(Coin.QUOTE) || symbol.equals(Coin.QUOTE) || o.optLong("count") == 0) {
                continue;
            }
            coins.add(parseTicker(o));
        }
        coins.sort((a, b) -> Double.compare(b.quoteVolume, a.quoteVolume));
        return coins;
    }

    static Coin parseTicker(JSONObject o) throws JSONException {
        return new Coin(o.getString("symbol"),
                o.getDouble("lastPrice"), o.getDouble("openPrice"),
                o.getDouble("highPrice"), o.getDouble("lowPrice"),
                o.getDouble("quoteVolume"));
    }

    static List<Candle> parseCandles(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<Candle> candles = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONArray k = array.getJSONArray(i);
            candles.add(new Candle(k.getLong(0), k.getDouble(1), k.getDouble(2), k.getDouble(3), k.getDouble(4)));
        }
        return candles;
    }

    static Map<String, Double> parsePrices(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        Map<String, Double> prices = new HashMap<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            prices.put(o.getString("symbol"), o.getDouble("price"));
        }
        return prices;
    }

    /** WebSocket "<symbol>@miniTicker" olayı. */
    public static Coin parseMiniTicker(JSONObject d) throws JSONException {
        return new Coin(d.getString("s"), d.getDouble("c"), d.getDouble("o"),
                d.getDouble("h"), d.getDouble("l"), d.getDouble("q"));
    }

    /** WebSocket "<symbol>@kline_<interval>" olayı. */
    public static Candle parseKlineEvent(JSONObject d) throws JSONException {
        JSONObject k = d.getJSONObject("k");
        return new Candle(k.getLong("t"), k.getDouble("o"), k.getDouble("h"), k.getDouble("l"), k.getDouble("c"));
    }
}
