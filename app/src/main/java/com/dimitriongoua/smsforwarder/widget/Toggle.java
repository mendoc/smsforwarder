package com.dimitriongoua.smsforwarder.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.core.content.ContextCompat;

import com.dimitriongoua.smsforwarder.R;

/**
 * Interrupteur de la refonte : piste 52 × 32 dp (verte allumée, grise éteinte), pastille
 * blanche de 26 dp à 3 dp du bord, comme la maquette. Bouton à deux états accessible
 * (annoncé comme un interrupteur) ; la zone tactile de 44 dp vient de son parent.
 */
public class Toggle extends CompoundButton {
    private static final float TRACK_WIDTH_DP = 52;
    private static final float TRACK_HEIGHT_DP = 32;
    private static final float THUMB_INSET_DP = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final float density;
    private final int onColor;
    private final int offColor;
    private final int thumbOnColor;
    private final int thumbOffColor;

    public Toggle(Context context) {
        this(context, null);
    }

    public Toggle(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        onColor = ContextCompat.getColor(context, R.color.primary);
        offColor = ContextCompat.getColor(context, R.color.toggle_off);
        thumbOnColor = ContextCompat.getColor(context, R.color.toggle_thumb_on);
        thumbOffColor = ContextCompat.getColor(context, R.color.toggle_thumb_off);
        setBackground(null);
        setButtonDrawable(null);
        setClickable(true);
        setFocusable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                resolveSize(Math.round(TRACK_WIDTH_DP * density), widthMeasureSpec),
                resolveSize(Math.round(TRACK_HEIGHT_DP * density), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = TRACK_WIDTH_DP * density;
        float height = TRACK_HEIGHT_DP * density;
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        track.set(left, top, left + width, top + height);
        paint.setColor(isChecked() ? onColor : offColor);
        paint.setAlpha(isEnabled() ? 255 : 110);
        canvas.drawRoundRect(track, height / 2f, height / 2f, paint);

        float inset = THUMB_INSET_DP * density;
        float radius = height / 2f - inset;
        float cx = isChecked() ? track.right - inset - radius : track.left + inset + radius;
        paint.setColor(isChecked() ? thumbOnColor : thumbOffColor);
        canvas.drawCircle(cx, track.centerY(), radius, paint);
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        invalidate();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Switch.class.getName();
    }
}
