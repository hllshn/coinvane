package com.abraxel.cryptocurrency.portfolio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Sade yatay dağılım çubuğu: dilimler yüzdeleriyle orantılı, uçları yuvarlak. RTL'de sağdan başlar. */
public class AllocationBar extends View {
    /** Kategorik renkler (açık/koyu zeminde okunur). Son renk "Diğer". */
    public static final int[] COLORS = {
            0xFF6E63DB, 0xFF2FB5A8, 0xFFF0A23B, 0xFF4C8DF6, 0xFFE0607E, 0xFF8BC34A, 0xFFB07CE8, 0xFF8C93A6};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private List<Double> percents = new ArrayList<>();
    private List<Integer> colors = new ArrayList<>();

    public AllocationBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); // açıklamalı liste ayrıca var
    }

    public void setPercents(List<Double> percents) {
        this.percents = percents;
        invalidate();
    }

    public static int color(int index, boolean other) {
        return other ? COLORS[COLORS.length - 1] : COLORS[index % (COLORS.length - 1)];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (percents.isEmpty()) return;
        float h = getHeight();
        float w = getWidth();
        clip.reset();
        rect.set(0, 0, w, h);
        clip.addRoundRect(rect, h / 2, h / 2, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float x = 0;
        float gap = getResources().getDisplayMetrics().density;
        for (int i = 0; i < percents.size(); i++) {
            float width = (float) (w * percents.get(i) / 100.0);
            paint.setColor(colorAt(i));
            float left = rtl ? w - x - width : x;
            canvas.drawRect(left, 0, left + Math.max(0, width - gap), h, paint);
            x += width;
        }
        canvas.restore();
    }

    private int colorAt(int i) {
        return i < colors.size() ? colors.get(i) : color(i, false);
    }

    /** Dilim renkleri (açıklamalı listeyle aynı sırada). */
    public void setColors(List<Integer> colors) {
        this.colors = colors;
        invalidate();
    }
}
