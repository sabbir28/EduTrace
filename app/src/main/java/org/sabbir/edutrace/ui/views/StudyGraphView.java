package org.sabbir.edutrace.ui.views;
import org.sabbir.edutrace.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class StudyGraphView extends View {
    private Paint barPaint;
    private Paint textPaint;
    private Paint linePaint;
    private Paint selectionPaint;
    private List<Float> data = new ArrayList<>();
    private String[] labels = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private int selectedIndex = -1;
    private OnDaySelectedListener listener;

    public interface OnDaySelectedListener {
        void onDaySelected(int index);
    }

    public StudyGraphView(Context context) {
        super(context);
        init();
    }

    public StudyGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#94A3B8"));
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#1E293B"));
        linePaint.setStrokeWidth(2f);

        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setColor(Color.parseColor("#334155"));
        selectionPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<Float> hours, int defaultSelected) {
        this.data = hours;
        this.selectedIndex = defaultSelected;
        invalidate();
    }

    public void setOnDaySelectedListener(OnDaySelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float leftPadding = 100f;
            float width = getWidth() - (leftPadding + 40f);
            float barSpace = width / 7;
            
            int index = (int) ((x - leftPadding) / barSpace);
            if (index >= 0 && index < 7) {
                selectedIndex = index;
                if (listener != null) listener.onDaySelected(index);
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null || data.isEmpty()) return;

        float width = getWidth();
        float height = getHeight();
        float leftPadding = 100f;
        float rightPadding = 40f;
        float bottomPadding = 80f;
        float topPadding = 40f;
        
        float chartWidth = width - (leftPadding + rightPadding);
        float chartHeight = height - (topPadding + bottomPadding);
        
        float maxVal = 0;
        for (float v : data) if (v > maxVal) maxVal = v;

        boolean useMinutes = maxVal < 1.0f; // If less than 1 hour, use minutes
        float scaleMax = maxVal;
        
        if (useMinutes) {
            float maxMins = maxVal * 60;
            // Round to next 5, 10, or 15
            if (maxMins <= 5) scaleMax = 5/60f;
            else if (maxMins <= 15) scaleMax = 15/60f;
            else if (maxMins <= 30) scaleMax = 30/60f;
            else scaleMax = 60/60f;
        } else {
            // Hours rounding
            if (scaleMax <= 2) scaleMax = 2;
            else if (scaleMax <= 4) scaleMax = 4;
            else if (scaleMax <= 8) scaleMax = 8;
            else scaleMax = (float) (Math.ceil(scaleMax / 4.0) * 4.0);
        }

        // Draw Y-Axis labels and grid lines
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 4; i++) {
            float val = scaleMax * i / 4;
            float y = topPadding + chartHeight - (chartHeight * i / 4);
            
            String label;
            if (useMinutes) {
                label = String.format("%.0fm", val * 60);
            } else {
                label = String.format("%.0fh", val);
            }
            
            canvas.drawText(label, leftPadding - 20f, y + 10f, textPaint);
            canvas.drawLine(leftPadding, y, width - rightPadding, y, linePaint);
        }

        float barWidth = (chartWidth / 7) * 0.5f;
        float spacing = (chartWidth / 7) * 0.5f;
        float startX = leftPadding + (spacing / 2);

        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 7; i++) {
            float val = (i < data.size()) ? data.get(i) : 0;
            float barHeight = Math.min((val / scaleMax) * chartHeight, chartHeight);
            
            float left = startX + i * (barWidth + spacing);
            float right = left + barWidth;
            float bottom = topPadding + chartHeight;
            float top = bottom - barHeight;

            // Draw selection background
            if (i == selectedIndex) {
                canvas.drawRoundRect(new RectF(left - spacing/4, topPadding, right + spacing/4, bottom + 10f), 16f, 16f, selectionPaint);
            }

            RectF rect = new RectF(left, top, right, bottom);
            
            // Gradient
            int startColor = (i == selectedIndex) ? Color.parseColor("#FACC15") : Color.parseColor("#475569");
            int endColor = (i == selectedIndex) ? Color.parseColor("#EAB308") : Color.parseColor("#334155");
            
            Shader shader = new LinearGradient(0, top, 0, bottom, startColor, endColor, Shader.TileMode.CLAMP);
            barPaint.setShader(shader);
            
            canvas.drawRoundRect(rect, 12f, 12f, barPaint);
            
            // Labels
            if (i < labels.length) {
                canvas.drawText(labels[i], left + (barWidth / 2), height - 20f, textPaint);
            }
        }
    }
}
