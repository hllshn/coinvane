package com.abraxel.cryptocurrency.chart;

import android.content.Context;
import android.graphics.Paint;

import androidx.core.content.ContextCompat;

import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.data.Candle;
import com.abraxel.cryptocurrency.data.Format;
import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Mum grafiği. X ekseni mum sırasıdır (boşluksuz, eşit aralıklı çizim);
 * gerçek zaman damgaları {@link #times} listesinde tutulur.
 */
public final class CandleChart {
    private static final int VISIBLE_CANDLES = 60;

    private final CandleStickChart chart;
    private final CandleDataSet set = new CandleDataSet(new ArrayList<>(), "");
    private final List<Long> times = new ArrayList<>();
    private SimpleDateFormat axisFormat = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());

    public CandleChart(CandleStickChart chart) {
        this.chart = chart;
        Context context = chart.getContext();
        int text = ContextCompat.getColor(context, R.color.chart_text);
        int grid = ContextCompat.getColor(context, R.color.chart_grid);
        int up = ContextCompat.getColor(context, R.color.up);
        int down = ContextCompat.getColor(context, R.color.down);

        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataTextColor(text);
        chart.setScaleYEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setAutoScaleMinMaxEnabled(true); // kaydırırken Y ekseni görünen mumlara uyar
        chart.setMinOffset(0f);
        chart.setExtraBottomOffset(4f);
        chart.setMarker(new CandleMarkerView(context, times));

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setDrawAxisLine(false);
        x.setTextColor(text);
        x.setGranularity(1f);
        x.setLabelCount(4);
        x.setAvoidFirstLastClipping(true);
        x.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int i = Math.round(value);
                return i >= 0 && i < times.size() ? axisFormat.format(new Date(times.get(i))) : "";
            }
        });

        chart.getAxisLeft().setEnabled(false);
        YAxis y = chart.getAxisRight(); // borsalardaki gibi fiyat ekseni sağda
        y.setTextColor(text);
        y.setGridColor(grid);
        y.setDrawAxisLine(false);
        y.setLabelCount(5);
        y.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return Format.number(value);
            }
        });

        set.setDrawValues(false);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        set.setShadowColorSameAsCandle(true);
        set.setShadowWidth(1f);
        set.setIncreasingColor(up);
        set.setIncreasingPaintStyle(Paint.Style.FILL);
        set.setDecreasingColor(down);
        set.setDecreasingPaintStyle(Paint.Style.FILL);
        set.setNeutralColor(text);
        set.setBarSpace(0.2f);
        set.setHighLightColor(text);
        set.setDrawHorizontalHighlightIndicator(false);
    }

    public void showMessage(String message) {
        times.clear();
        chart.clear(); // veri boşken noDataText görünür
        chart.setNoDataText(message);
        chart.invalidate();
    }

    /** interval: 15m, 1h, 4h, 1d, 1w — eksen etiketinin biçimini belirler. */
    public void setCandles(List<Candle> candles, String interval) {
        if (candles.isEmpty()) return;
        String pattern = interval.endsWith("m") ? "HH:mm" : interval.endsWith("h") ? "dd.MM HH:mm" : "dd.MM.yy";
        axisFormat = new SimpleDateFormat(pattern, Locale.getDefault());

        times.clear();
        List<CandleEntry> entries = new ArrayList<>(candles.size());
        for (Candle c : candles) {
            entries.add(entry(times.size(), c));
            times.add(c.time);
        }
        set.setValues(entries);
        chart.highlightValue(null);
        chart.setData(new CandleData(set));
        chart.fitScreen();
        chart.setVisibleXRangeMaximum(VISIBLE_CANDLES);
        chart.moveViewToX(entries.size());
        chart.invalidate();
    }

    /** Canlı kline olayı: aynı açılış zamanlı son mumu günceller ya da yeni mum ekler. */
    public void update(Candle c) {
        if (times.isEmpty() || chart.getData() == null) return;
        int last = times.size() - 1;
        long lastTime = times.get(last);
        if (c.time == lastTime) {
            CandleEntry e = set.getEntryForIndex(last);
            e.setOpen((float) c.open);
            e.setHigh((float) c.high);
            e.setLow((float) c.low);
            e.setClose((float) c.close);
        } else if (c.time > lastTime) {
            boolean followingLatest = chart.getHighestVisibleX() >= last - 0.5f;
            set.addEntry(entry(times.size(), c));
            times.add(c.time);
            if (followingLatest) chart.moveViewToX(times.size());
        } else {
            return;
        }
        chart.getData().notifyDataChanged();
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    private static CandleEntry entry(int index, Candle c) {
        return new CandleEntry(index, (float) c.high, (float) c.low, (float) c.open, (float) c.close);
    }
}
