package com.abraxel.cryptocurrency.data;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Bir TRY paritesinin 24 saatlik özeti (Binance mini ticker). Değişmez. */
public final class Coin {
    public static final String QUOTE = "TRY";

    public final String symbol;      // BTCTRY
    public final String base;        // BTC
    public final double last;
    public final double open;        // 24 saat önceki fiyat
    public final double high;
    public final double low;
    public final double quoteVolume; // 24 saatlik TRY hacmi

    public Coin(String symbol, double last, double open, double high, double low, double quoteVolume) {
        this.symbol = symbol;
        this.base = symbol.endsWith(QUOTE) ? symbol.substring(0, symbol.length() - QUOTE.length()) : symbol;
        this.last = last;
        this.open = open;
        this.high = high;
        this.low = low;
        this.quoteVolume = quoteVolume;
    }

    public double changePercent() {
        return open == 0 ? 0 : (last - open) / open * 100;
    }

    /** Bilinen coinler için tam ad, diğerleri için sembol. */
    public String name() {
        String name = NAMES.get(base);
        return name != null ? name : base;
    }

    public String pairLabel() {
        return base + "/" + QUOTE;
    }

    public static String pairLabel(String symbol) {
        return new Coin(symbol, 0, 0, 0, 0, 0).pairLabel();
    }

    public static String name(String symbol) {
        return new Coin(symbol, 0, 0, 0, 0, 0).name();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Coin)) return false;
        Coin c = (Coin) o;
        return symbol.equals(c.symbol) && last == c.last && open == c.open
                && high == c.high && low == c.low && quoteVolume == c.quoteVolume;
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, last, open, high, low, quoteVolume);
    }

    @NonNull
    @Override
    public String toString() {
        return symbol + "@" + last;
    }

    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        String[][] names = {
                {"BTC", "Bitcoin"}, {"ETH", "Ethereum"}, {"BNB", "BNB"}, {"XRP", "XRP"},
                {"USDT", "Tether"}, {"USDC", "USD Coin"}, {"SOL", "Solana"}, {"ADA", "Cardano"},
                {"DOGE", "Dogecoin"}, {"TRX", "TRON"}, {"AVAX", "Avalanche"}, {"DOT", "Polkadot"},
                {"LINK", "Chainlink"}, {"LTC", "Litecoin"}, {"BCH", "Bitcoin Cash"}, {"XLM", "Stellar"},
                {"ATOM", "Cosmos"}, {"NEAR", "NEAR Protocol"}, {"SHIB", "Shiba Inu"}, {"PEPE", "Pepe"},
                {"SUI", "Sui"}, {"APT", "Aptos"}, {"ARB", "Arbitrum"}, {"OP", "Optimism"},
                {"FIL", "Filecoin"}, {"ETC", "Ethereum Classic"}, {"ALGO", "Algorand"}, {"ICP", "Internet Computer"},
                {"INJ", "Injective"}, {"FET", "Artificial Superintelligence"}, {"GRT", "The Graph"},
                {"SAND", "The Sandbox"}, {"MANA", "Decentraland"}, {"AXS", "Axie Infinity"}, {"APE", "ApeCoin"},
                {"CHZ", "Chiliz"}, {"GALA", "Gala"}, {"VET", "VeChain"}, {"XTZ", "Tezos"}, {"NEO", "NEO"},
                {"FLOKI", "Floki"}, {"PAXG", "PAX Gold"}, {"STX", "Stacks"}, {"TON", "Toncoin"},
                {"UNI", "Uniswap"}, {"AAVE", "Aave"}, {"RENDER", "Render"}, {"WLD", "Worldcoin"},
                {"SEI", "Sei"}, {"TIA", "Celestia"}, {"BONK", "Bonk"}, {"WIF", "dogwifhat"},
        };
        for (String[] n : names) NAMES.put(n[0], n[1]);
    }
}
