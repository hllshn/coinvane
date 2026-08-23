package com.abraxel.cryptocurrency.formatter;

import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Index bazlı grafik için: değeri mum indeksi olarak alır,
 * ilgili zaman damgasına bakarak etiket üretir.
 */
public class LineChartXAxisFormatter extends IndexAxisValueFormatter {
    private final SimpleDateFormat timeFormat;
    private final List<Long> candleTimes;

    public LineChartXAxisFormatter(List<Long> candleTimes) {
        this.candleTimes = candleTimes;
        timeFormat = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
    }

    @Override
    public String getFormattedValue(float value) {
        int index = Math.round(value);
        if (candleTimes != null && index >= 0 && index < candleTimes.size()) {
            return timeFormat.format(new Date(candleTimes.get(index)));
        }
        return "";
    }
}
