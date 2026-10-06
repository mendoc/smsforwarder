package com.dimitriongoua.smsforwarder.activity;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

import com.dimitriongoua.smsforwarder.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.IOException;

/** Icône de l'application (logo « Bulle qui part ») : icône adaptative et icône à thème, côte à côte. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "xhdpi")
public class IconScreenshotTest {
    private static final int SIZE = 432;

    @Test
    public void icone() throws IOException {
        Drawable icon = Screens.app().getDrawable(R.mipmap.ic_launcher);
        assertTrue(icon instanceof AdaptiveIconDrawable);
        AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) icon;
        assertTrue(adaptive.getMonochrome() != null);

        Bitmap bitmap = Bitmap.createBitmap(SIZE * 2 + 48, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.parseColor("#F2F3EE"));
        adaptive.setBounds(0, 0, SIZE, SIZE);
        adaptive.draw(canvas);

        // Icône à thème : fond clair, monochrome teinté comme le fait le lanceur.
        canvas.save();
        canvas.translate(SIZE + 48, 0);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.parseColor("#D8E8DF"));
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f * 66 / 72f, paint);
        Drawable mono = adaptive.getMonochrome().mutate();
        mono.setTint(Color.parseColor("#0D5C4A"));
        int inset = -SIZE / 4; // le calque de 108 dp déborde de l'icône visible (72 dp)
        mono.setBounds(inset, inset, SIZE - inset, SIZE - inset);
        mono.draw(canvas);
        canvas.restore();
        Screens.save(bitmap, "icone");
    }
}
