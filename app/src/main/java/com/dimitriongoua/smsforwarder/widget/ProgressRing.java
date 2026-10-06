package com.dimitriongoua.smsforwarder.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.dimitriongoua.smsforwarder.R;

/** Anneau de progression de l'écran Autorisations : 64 dp, trait de 7 dp, vert sur gris. */
public class ProgressRing extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float stroke;
    private float progress;

    public ProgressRing(Context context, AttributeSet attrs) {
        super(context, attrs);
        stroke = 7 * getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
    }

    /** Part accomplie, de 0 à 1. */
    public void setProgress(float value) {
        progress = Math.max(0, Math.min(1, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float half = stroke / 2;
        oval.set(half, half, getWidth() - half, getHeight() - half);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.line));
        canvas.drawArc(oval, 0, 360, false, paint);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.pine));
        canvas.drawArc(oval, -90, 360 * progress, false, paint);
    }
}
