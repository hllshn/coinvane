package com.abraxel.cryptocurrency.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Eksensiz, ince mini fiyat grafiği. Yalnızca gerçek veriyle çizilir; veri yoksa boş kalır. */
public class SparklineView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float[] points;

    public SparklineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getResources().getDisplayMetrics().density * 1.5f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setData(@Nullable float[] points, int color) {
        this.points = points;
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (points == null || points.length < 2) return;
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float p : points) {
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        float pad = paint.getStrokeWidth();
        float w = getWidth() - getPaddingLeft() - getPaddingRight() - 2 * pad;
        float h = getHeight() - getPaddingTop() - getPaddingBottom() - 2 * pad;
        // Çok küçük oynamalar (ör. stablecoin ±%0,01) dev dalgalar gibi görünmesin: en az %1'lik dikey ölçek
        float mid = (max + min) / 2;
        float minRange = Math.abs(mid) * 0.01f;
        if (max - min < minRange) {
            max = mid + minRange / 2;
            min = mid - minRange / 2;
        }
        float range = max - min;
        path.reset();
        for (int i = 0; i < points.length; i++) {
            float x = getPaddingLeft() + pad + w * i / (points.length - 1);
            float y = getPaddingTop() + pad + (range == 0 ? h / 2 : h * (max - points[i]) / range);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
    }
}
