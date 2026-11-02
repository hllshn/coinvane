package com.abraxel.cryptocurrency.chart;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.abraxel.cryptocurrency.R;
import com.abraxel.cryptocurrency.data.Candle;
import com.abraxel.cryptocurrency.data.Money;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fiyat grafiği: mum veya çizgi + altta hacim çubukları. X ekseni mum sırasıdır (boşluksuz, eşit aralıklı);
 * gerçek zaman damgaları {@link #times} listesinde tutulur. Değerler kullanıcının para birimine çevrilerek çizilir.
 */
public final class CandleChart {
    private static final int VISIBLE_CANDLES = 60;

    private final CombinedChart chart;
    private final CandleDataSet candles = new CandleDataSet(new ArrayList<>(), "price");
    private final LineDataSet line = new LineDataSet(new ArrayList<>(), "close");
    private final BarDataSet volume = new BarDataSet(new ArrayList<>(), "volume");
    private final List<Long> times = new ArrayList<>();
    private final List<Integer> volumeColors = new ArrayList<>();
    private final int upVolume;
    private final int downVolume;
    private final CandleMarkerView marker;
    private Money money;
    private boolean lineMode;
    private DateFormat axisFormat = DateFormat.getTimeInstance(DateFormat.SHORT);

    public CandleChart(CombinedChart chart, Money money) {
        this.chart = chart;
        this.money = money;
        Context context = chart.getContext();
        int text = ContextCompat.getColor(context, R.color.chart_text);
        int grid = ContextCompat.getColor(context, R.color.chart_grid);
        int up = ContextCompat.getColor(context, R.color.up);
        int down = ContextCompat.getColor(context, R.color.down);
        int brand = ContextCompat.getColor(context, R.color.brand);
        upVolume = ColorUtils.setAlphaComponent(up, 0x55);
        downVolume = ColorUtils.setAlphaComponent(down, 0x55);

        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataTextColor(text);
        chart.setScaleYEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setAutoScaleMinMaxEnabled(true); // kaydırırken fiyat ekseni görünen mumlara uyar
        chart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.CANDLE, CombinedChart.DrawOrder.LINE});
        chart.setMinOffset(0f);
        chart.setExtraBottomOffset(4f);
        marker = new CandleMarkerView(context, times, money);
        chart.setMarker(marker);

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

        // Sol eksen: hacim (gizli). Üst sınır 4 katı → çubuklar alt %25'te kalır.
        YAxis volumeAxis = chart.getAxisLeft();
        volumeAxis.setEnabled(false);
        volumeAxis.setAxisMinimum(0f);

        YAxis price = chart.getAxisRight(); // borsalardaki gibi fiyat ekseni sağda
        price.setTextColor(text);
        price.setGridColor(grid);
        price.setDrawAxisLine(false);
        price.setLabelCount(5);
        price.setSpaceBottom(35f); // mumlar hacim çubuklarının üstünde kalsın
        price.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return CandleChart.this.money.number(value);
            }
        });

        candles.setDrawValues(false);
        candles.setAxisDependency(YAxis.AxisDependency.RIGHT);
        candles.setShadowColorSameAsCandle(true);
        candles.setShadowWidth(1f);
        candles.setIncreasingColor(up);
        candles.setIncreasingPaintStyle(Paint.Style.FILL);
        candles.setDecreasingColor(down);
        candles.setDecreasingPaintStyle(Paint.Style.FILL);
        candles.setNeutralColor(text);
        candles.setBarSpace(0.2f);
        candles.setHighLightColor(text);
        candles.setDrawHorizontalHighlightIndicator(false);

        line.setAxisDependency(YAxis.AxisDependency.RIGHT);
        line.setColor(brand);
        line.setLineWidth(2f);
        line.setDrawCircles(false);
        line.setDrawValues(false);
        line.setMode(LineDataSet.Mode.LINEAR);
        line.setDrawFilled(true);
        line.setFillDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ColorUtils.setAlphaComponent(brand, 0x66), ColorUtils.setAlphaComponent(brand, 0)}));
        line.setHighLightColor(text);
        line.setDrawHorizontalHighlightIndicator(false);

        volume.setAxisDependency(YAxis.AxisDependency.LEFT);
        volume.setDrawValues(false);
        volume.setHighlightEnabled(false);
        volume.setColors(volumeColors);
        setLineMode(false);
    }

    /** Para birimi değişince eksen ve işaretçi biçimi güncellenir; veri yeniden yüklenmelidir. */
    public void setMoney(Money money) {
        this.money = money;
        marker.setMoney(money);
    }

    public void setLineMode(boolean lineMode) {
        this.lineMode = lineMode;
        candles.setVisible(!lineMode);
        candles.setHighlightEnabled(!lineMode);
        line.setVisible(lineMode);
        line.setHighlightEnabled(lineMode);
        chart.highlightValue(null);
        chart.invalidate();
    }

    public boolean isLineMode() {
        return lineMode;
    }

    public void showMessage(String message) {
        times.clear();
        chart.clear(); // veri boşken noDataText görünür
        chart.setNoDataText(message);
        chart.invalidate();
    }

    /** interval: 15m, 1h, 4h, 1d, 1w — eksen etiketinin biçimini belirler. */
    public void setCandles(List<Candle> list, String interval) {
        if (list.isEmpty()) return;
        Locale locale = chart.getResources().getConfiguration().getLocales().get(0);
        axisFormat = interval.endsWith("m") ? DateFormat.getTimeInstance(DateFormat.SHORT, locale)
                : interval.endsWith("h") ? new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMHHmm"), locale)
                : new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMyy"), locale);

        times.clear();
        volumeColors.clear();
        List<CandleEntry> candleEntries = new ArrayList<>(list.size());
        List<Entry> lineEntries = new ArrayList<>(list.size());
        List<BarEntry> volumeEntries = new ArrayList<>(list.size());
        double maxVolume = 0;
        for (Candle c : list) {
            int i = times.size();
            candleEntries.add(candleEntry(i, c));
            lineEntries.add(new Entry(i, (float) money.convert(c.close)));
            volumeEntries.add(new BarEntry(i, (float) c.volume));
            volumeColors.add(c.close >= c.open ? upVolume : downVolume);
            times.add(c.time);
            maxVolume = Math.max(maxVolume, c.volume);
        }
        candles.setValues(candleEntries);
        line.setValues(lineEntries);
        volume.setValues(volumeEntries);
        volume.setColors(volumeColors);
        chart.getAxisLeft().setAxisMaximum((float) maxVolume * 4f);

        CombinedData data = new CombinedData();
        data.setData(new CandleData(candles));
        data.setData(new LineData(line));
        BarData bars = new BarData(volume);
        bars.setBarWidth(0.7f);
        data.setData(bars);

        chart.highlightValue(null);
        chart.setData(data);
        chart.fitScreen();
        chart.setVisibleXRangeMaximum(VISIBLE_CANDLES);
        chart.moveViewToX(list.size());
        chart.invalidate();
    }

    /** Canlı kline olayı: aynı açılış zamanlı son mumu günceller ya da yeni mum ekler. */
    public void update(Candle c) {
        if (times.isEmpty() || chart.getData() == null) return;
        int last = times.size() - 1;
        long lastTime = times.get(last);
        if (c.time == lastTime) {
            CandleEntry e = candles.getEntryForIndex(last);
            e.setOpen((float) money.convert(c.open));
            e.setHigh((float) money.convert(c.high));
            e.setLow((float) money.convert(c.low));
            e.setClose((float) money.convert(c.close));
            line.getEntryForIndex(last).setY((float) money.convert(c.close));
            volume.getEntryForIndex(last).setY((float) c.volume);
            volumeColors.set(last, c.close >= c.open ? upVolume : downVolume);
        } else if (c.time > lastTime) {
            boolean followingLatest = chart.getHighestVisibleX() >= last - 0.5f;
            int i = times.size();
            candles.addEntry(candleEntry(i, c));
            line.addEntry(new Entry(i, (float) money.convert(c.close)));
            volume.addEntry(new BarEntry(i, (float) c.volume));
            volumeColors.add(c.close >= c.open ? upVolume : downVolume);
            times.add(c.time);
            if (followingLatest) chart.moveViewToX(times.size());
        } else {
            return;
        }
        volume.setColors(volumeColors);
        chart.getData().notifyDataChanged();
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    private CandleEntry candleEntry(int index, Candle c) {
        return new CandleEntry(index, (float) money.convert(c.high), (float) money.convert(c.low),
                (float) money.convert(c.open), (float) money.convert(c.close));
    }
}
