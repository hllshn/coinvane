package com.abraxel.cryptocurrency.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class BinanceParserTest {

    private static String ticker(String symbol, String last, String open, long count, String quoteVolume) {
        return "{\"symbol\":\"" + symbol + "\",\"openPrice\":\"" + open + "\",\"highPrice\":\"" + last
                + "\",\"lowPrice\":\"" + open + "\",\"lastPrice\":\"" + last + "\",\"volume\":\"1\",\"quoteVolume\":\""
                + quoteVolume + "\",\"count\":" + count + "}";
    }

    @Test
    public void tryCoins_keepsOnlyTradedTryPairs_sortedByVolume() throws Exception {
        String json = "[" + String.join(",",
                ticker("ETHTRY", "150000", "140000", 10, "500"),
                ticker("BTCUSDT", "100000", "99000", 99, "999999"),   // TRY değil
                ticker("BTCTRY", "3842059", "3894545", 25190, "337347794.8"),
                ticker("FISTRY", "1", "1", 0, "0")                     // 24s işlem yok
        ) + "]";

        List<Coin> coins = Binance.parseTryCoins(json);

        assertEquals(2, coins.size());
        assertEquals("BTCTRY", coins.get(0).symbol);
        assertEquals("BTC", coins.get(0).base);
        assertEquals(3842059, coins.get(0).last, 0);
        assertEquals("ETHTRY", coins.get(1).symbol);
    }

    @Test
    public void candles_parseOhlcInOrder() throws Exception {
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
    }

    @Test
    public void miniTickerEvent_mapsToCoin() throws Exception {
        JSONObject event = new JSONObject("{\"e\":\"24hrMiniTicker\",\"E\":1,\"s\":\"SOLTRY\",\"c\":\"6200.5\","
                + "\"o\":\"6000\",\"h\":\"6300\",\"l\":\"5900\",\"v\":\"10\",\"q\":\"62005\"}");

        Coin coin = Binance.parseMiniTicker(event);

        assertEquals("SOLTRY", coin.symbol);
        assertEquals("SOL", coin.base);
        assertEquals(6200.5, coin.last, 0);
        assertEquals(62005, coin.quoteVolume, 0);
        assertEquals(3.3417, coin.changePercent(), 0.0001);
    }

    @Test
    public void klineEvent_mapsToCandle() throws Exception {
        JSONObject event = new JSONObject("{\"e\":\"kline\",\"s\":\"BTCTRY\",\"k\":{\"t\":1000,\"T\":1999,"
                + "\"i\":\"1h\",\"o\":\"1\",\"h\":\"4\",\"l\":\"0.5\",\"c\":\"3\",\"x\":false}}");

        Candle candle = Binance.parseKlineEvent(event);

        assertEquals(1000, candle.time);
        assertEquals(4, candle.high, 0);
        assertEquals(3, candle.close, 0);
    }

    @Test
    public void prices_parseSymbolMap() throws Exception {
        Map<String, Double> prices = Binance.parsePrices(
                "[{\"symbol\":\"BTCTRY\",\"price\":\"3840000.00\"},{\"symbol\":\"ETHTRY\",\"price\":\"150000\"}]");

        assertEquals(2, prices.size());
        assertEquals(3840000.0, prices.get("BTCTRY"), 0);
        assertTrue(prices.containsKey("ETHTRY"));
    }

    @Test
    public void changePercent_isZeroWhenOpenIsZero() {
        assertEquals(0, new Coin("NEWTRY", 5, 0, 5, 5, 0).changePercent(), 0);
    }

    @Test
    public void name_fallsBackToSymbol() {
        assertEquals("Bitcoin", Coin.name("BTCTRY"));
        assertEquals("XYZ", Coin.name("XYZTRY"));
        assertEquals("BTC/TRY", Coin.pairLabel("BTCTRY"));
    }
}
