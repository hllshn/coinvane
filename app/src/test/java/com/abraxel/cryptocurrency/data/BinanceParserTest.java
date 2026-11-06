package com.abraxel.cryptocurrency.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class BinanceParserTest {

    private static String ticker(String symbol, String last, String open, long count, String quoteVolume) {
        return "{\"symbol\":\"" + symbol + "\",\"openPrice\":\"" + open + "\",\"highPrice\":\"" + last
                + "\",\"lowPrice\":\"" + open + "\",\"lastPrice\":\"" + last + "\",\"volume\":\"1\",\"quoteVolume\":\""
                + quoteVolume + "\",\"count\":" + count + "}";
    }

    @Test
    public void coins_keepOnlyTradedCryptoUsdtPairs_sortedByVolume() throws Exception {
        String json = "[" + String.join(",",
                ticker("ETHUSDT", "3000", "2900", 10, "500"),
                ticker("BTCTRY", "3842059", "3894545", 99, "999999"), // USDT değil
                ticker("BTCUSDT", "77000", "78000", 25190, "1200000000"),
                ticker("EURUSDT", "1.11", "1.10", 500, "9999999999"),  // fiat
                ticker("FISUSDT", "1", "1", 0, "0")                     // 24s işlem yok
        ) + "]";

        List<Coin> coins = Binance.parseCoins(json);

        assertEquals(2, coins.size());
        assertEquals("BTCUSDT", coins.get(0).symbol);
        assertEquals("BTC", coins.get(0).base);
        assertEquals(77000, coins.get(0).last, 0);
        assertEquals("ETHUSDT", coins.get(1).symbol);
    }

    @Test
    public void coins_skipDelistedPairsWithFrozenTicker() throws Exception {
        long now = 1_794_000_000_000L;
        String fresh = "{\"symbol\":\"BTCUSDT\",\"openPrice\":\"1\",\"highPrice\":\"1\",\"lowPrice\":\"1\",\"lastPrice\":\"1\","
                + "\"quoteVolume\":\"10\",\"count\":5,\"closeTime\":" + (now - 60_000) + "}";
        String delisted = "{\"symbol\":\"KDAUSDT\",\"openPrice\":\"0.0051\",\"highPrice\":\"1\",\"lowPrice\":\"1\","
                + "\"lastPrice\":\"0.006\",\"quoteVolume\":\"10\",\"count\":4001,\"closeTime\":" + (now - 3L * 86_400_000) + "}";
        List<Coin> coins = Binance.parseCoins("[" + fresh + "," + delisted + "]", now);
        assertEquals(1, coins.size());
        assertEquals("BTCUSDT", coins.get(0).symbol);
    }

    @Test
    public void listable_excludesFiatButKeepsLookalikeCryptoCodes() {
        assertTrue(Coin.isListable("BTCUSDT"));
        assertTrue(Coin.isListable("MTLUSDT")); // ISO kodu gibi görünen kripto
        assertFalse(Coin.isListable("GBPUSDT"));
        assertFalse(Coin.isListable("USDT"));
        assertFalse(Coin.isListable("BTCTRY"));
    }

    @Test
    public void candles_parseOhlcvInOrder() throws Exception {
        String json = "[[1793635200000,\"10.0\",\"12.5\",\"9.5\",\"11.0\",\"3.3\",1793638799999,\"1\",1,\"1\",\"1\",\"0\"],"
                + "[1793638800000,\"11.0\",\"11.2\",\"10.1\",\"10.2\",\"1.8\",1793642399999,\"1\",1,\"1\",\"1\",\"0\"]]";

        List<Candle> candles = Binance.parseCandles(json);

        assertEquals(2, candles.size());
        Candle first = candles.get(0);
        assertEquals(1793635200000L, first.time);
        assertEquals(10.0, first.open, 0);
        assertEquals(12.5, first.high, 0);
        assertEquals(9.5, first.low, 0);
        assertEquals(11.0, first.close, 0);
        assertEquals(3.3, first.volume, 0);
    }

    @Test
    public void miniTickerEvent_mapsToCoin() throws Exception {
        JSONObject event = new JSONObject("{\"e\":\"24hrMiniTicker\",\"E\":1,\"s\":\"SOLUSDT\",\"c\":\"206.67\","
                + "\"o\":\"200\",\"h\":\"210\",\"l\":\"196.6\",\"v\":\"10\",\"q\":\"2066.7\"}");

        Coin coin = Binance.parseMiniTicker(event);

        assertEquals("SOL", coin.base);
        assertEquals(206.67, coin.last, 0);
        assertEquals(3.335, coin.changePercent(), 0.0001);
    }

    @Test
    public void klineEvent_mapsToCandle() throws Exception {
        JSONObject event = new JSONObject("{\"e\":\"kline\",\"s\":\"BTCUSDT\",\"k\":{\"t\":1000,\"T\":1999,"
                + "\"i\":\"1h\",\"o\":\"1\",\"h\":\"4\",\"l\":\"0.5\",\"c\":\"3\",\"v\":\"12.5\",\"x\":false}}");

        Candle candle = Binance.parseKlineEvent(event);

        assertEquals(1000, candle.time);
        assertEquals(4, candle.high, 0);
        assertEquals(3, candle.close, 0);
        assertEquals(12.5, candle.volume, 0);
    }

    @Test
    public void changePercent_isZeroWhenOpenIsZero() {
        assertEquals(0, new Coin("NEWUSDT", 5, 0, 5, 5, 0).changePercent(), 0);
    }

    @Test
    public void name_fallsBackToSymbol() {
        assertEquals("Bitcoin", Coin.name("BTCUSDT"));
        assertEquals("XYZ", Coin.name("XYZUSDT"));
    }
}
