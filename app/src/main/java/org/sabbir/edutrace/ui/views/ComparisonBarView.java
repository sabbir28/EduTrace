package org.sabbir.edutrace.ui.views;
import org.sabbir.edutrace.R;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;

public class ComparisonBarView extends View {
    private Paint barPaint;
    private Paint bgBarPaint;
    private Paint labelPaint;
    private Paint valuePaint;
    
    private float value1 = 0;
    private float value2 = 0;
    private float animValue1 = 0;
    private float animValue2 = 0;
    private String label1 = "Subject";
    private String label2 = "Others";
    
    private int color1 = Color.parseColor("#FACC15");
    private int color2 = Color.parseColor("#3B82F6");

    public ComparisonBarView(Context context) {
        super(context);
        init();
    }

    public ComparisonBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        bgBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgBarPaint.setColor(Color.parseColor("#1E293B"));
        bgBarPaint.setAlpha(100);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setTextSize(32f);
        labelPaint.setColor(Color.parseColor("#94A3B8"));
        labelPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));

        valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setTextSize(40f);
        valuePaint.setColor(Color.WHITE);
        valuePaint.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
    }

    public void setValues(float v1, float v2, String l1, String l2) {
        this.label1 = l1;
        this.label2 = l2;
        animateToValues(v1, v2);
    }

    private void animateToValues(float target1, float target2) {
        ValueAnimator vAnimator = ValueAnimator.ofFloat(0, 1);
        float start1 = animValue1;
        float start2 = animValue2;
        vAnimator.addUpdateListener(animation -> {
            float f = (float) animation.getAnimatedValue();
            animValue1 = start1 + (target1 - start1) * f;
            animValue2 = start2 + (target2 - start2) * f;
            invalidate();
        });
        vAnimator.setDuration(1000);
        vAnimator.setInterpolator(new DecelerateInterpolator());
        vAnimator.start();
        this.value1 = target1;
        this.value2 = target2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float w = getWidth();
        float h = getHeight();
        float barH = 50f;
        float spacing = 120f;
        float startX = 20;
        float barW = w - 40;

        float maxVal = Math.max(value1, value2);
        if (maxVal < 1) maxVal = 1;

        // Draw First Data Set (Hero)
        drawDataPoint(canvas, label1, value1, animValue1, maxVal, startX, 60, barW, barH, color1);

        // Draw Second Data Set (Comparison)
        drawDataPoint(canvas, label2, value2, animValue2, maxVal, startX, 60 + spacing, barW, barH, color2);
    }

    private void drawDataPoint(Canvas canvas, String label, float realVal, float animVal, float maxVal, 
                               float x, float y, float width, float height, int color) {
        
        // Label
        canvas.drawText(label.toUpperCase(), x, y, labelPaint);
        
        // Value (Right Aligned)
        String valStr = String.format("%.1fh", realVal);
        float vW = valuePaint.measureText(valStr);
        canvas.drawText(valStr, x + width - vW, y, valuePaint);

        // Background Bar
        RectF bgRect = new RectF(x, y + 20, x + width, y + 20 + height);
        canvas.drawRoundRect(bgRect, height / 2, height / 2, bgBarPaint);

        // Foreground Bar
        float progressW = (animVal / maxVal) * width;
        if (progressW < height) progressW = height;
        
        RectF fgRect = new RectF(x, y + 20, x + progressW, y + 20 + height);
        barPaint.setShader(new LinearGradient(x, y + 20, x + progressW, y + 20, 
                color, adjustAlpha(color, 0.6f), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(fgRect, height / 2, height / 2, barPaint);

        // Subtle Glow
        Paint glowPaint = new Paint(barPaint);
        glowPaint.setAlpha(40);
        canvas.drawRoundRect(new RectF(x - 2, y + 18, x + progressW + 2, y + 22 + height), 
                height / 2 + 2, height / 2 + 2, glowPaint);
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        if (alpha > 255) alpha = 255;
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
