package org.sabbir.edutrace.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.sabbir.edutrace.data.models.TimelineDayData;
import org.sabbir.edutrace.data.models.TimelineSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom interactive view rendering a 24-hour day timeline (1440 minutes)
 * partitioned into Sleep (Midnight Navy), Study (Vibrant Blue), and Idle/Lost Time (White).
 */
public class DayTimelineView extends View {

    private final Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF barRect = new RectF();
    private final Path clipPath = new Path();

    private List<TimelineSegment> segments = new ArrayList<>();
    private float cornerRadius = 16f;

    public DayTimelineView(Context context) {
        super(context);
        init();
    }

    public DayTimelineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DayTimelineView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        cornerRadius = getResources().getDisplayMetrics().density * 8f;

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(getResources().getDisplayMetrics().density * 1.5f);
        borderPaint.setColor(Color.parseColor("#475569"));

        tickPaint.setColor(Color.parseColor("#64748B"));
        tickPaint.setStrokeWidth(getResources().getDisplayMetrics().density * 1f);

        labelPaint.setColor(Color.parseColor("#94A3B8"));
        labelPaint.setTextSize(getResources().getDisplayMetrics().density * 11f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setAntiAlias(true);
    }

    public void setTimelineData(TimelineDayData data) {
        if (data != null && data.segments != null) {
            this.segments = new ArrayList<>(data.segments);
        } else {
            this.segments = new ArrayList<>();
        }
        invalidate();
    }

    public void setSegments(List<TimelineSegment> segments) {
        this.segments = segments != null ? new ArrayList<>(segments) : new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) (getResources().getDisplayMetrics().density * 72f);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float barTop = density * 4f;
        float barHeight = density * 36f;
        float barBottom = barTop + barHeight;

        barRect.set(density * 4f, barTop, w - density * 4f, barBottom);
        float barWidth = barRect.width();

        // Clip rounded rectangle for the bar
        clipPath.reset();
        clipPath.addRoundRect(barRect, cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);

        // Fill background with idle/lost time default (White / Slate)
        segmentPaint.setColor(Color.parseColor("#FFFFFF"));
        segmentPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(barRect, segmentPaint);

        if (!segments.isEmpty()) {
            for (TimelineSegment seg : segments) {
                int startM = parseTimeToMinutes(seg.start);
                int endM = parseTimeToMinutes(seg.end);
                if (endM <= startM) endM = 1440;

                float left = barRect.left + ((startM / 1440.0f) * barWidth);
                float right = barRect.left + ((endM / 1440.0f) * barWidth);

                segmentPaint.setColor(parseHexColor(seg.colorHex, seg.type));
                canvas.drawRect(left, barRect.top, right, barRect.bottom, segmentPaint);

                // If segment is white/idle, draw a subtle inner separator
                if ("#FFFFFF".equalsIgnoreCase(seg.colorHex) || "idle".equalsIgnoreCase(seg.type)) {
                    canvas.drawLine(left, barRect.top, left, barRect.bottom, borderPaint);
                    canvas.drawLine(right, barRect.top, right, barRect.bottom, borderPaint);
                }
            }
        } else {
            // Placeholder: night sleep 00:00-07:00, study 09:00-13:00, rest idle
            segmentPaint.setColor(Color.parseColor("#1E1B4B"));
            canvas.drawRect(barRect.left, barRect.top, barRect.left + (7f / 24f * barWidth), barRect.bottom, segmentPaint);

            segmentPaint.setColor(Color.parseColor("#2563EB"));
            canvas.drawRect(barRect.left + (9f / 24f * barWidth), barRect.top, barRect.left + (13f / 24f * barWidth), barRect.bottom, segmentPaint);
        }

        canvas.restore();

        // Draw outer border
        canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, borderPaint);

        // Draw Hour Marks along the bottom
        float labelY = barBottom + (density * 18f);
        String[] hours = {"00:00", "06:00", "12:00", "18:00", "24:00"};
        float[] ratios = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f};

        for (int i = 0; i < hours.length; i++) {
            float x = barRect.left + (ratios[i] * barWidth);
            // Tick line
            canvas.drawLine(x, barBottom, x, barBottom + (density * 4f), tickPaint);
            // Label
            canvas.drawText(hours[i], x, labelY, labelPaint);
        }
    }

    private int parseTimeToMinutes(String timeStr) {
        if (timeStr == null || !timeStr.contains(":")) return 0;
        try {
            String[] parts = timeStr.trim().split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return (h * 60) + m;
        } catch (Exception e) {
            return 0;
        }
    }

    private int parseHexColor(String hex, String type) {
        if (hex != null && !hex.isEmpty()) {
            try {
                return Color.parseColor(hex.trim());
            } catch (Exception ignored) {}
        }
        if ("sleep".equalsIgnoreCase(type)) return Color.parseColor("#1E1B4B");
        if ("study".equalsIgnoreCase(type)) return Color.parseColor("#2563EB");
        return Color.parseColor("#FFFFFF");
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && !segments.isEmpty()) {
            float x = event.getX();
            float barWidth = barRect.width();
            if (barWidth > 0 && x >= barRect.left && x <= barRect.right) {
                float ratio = (x - barRect.left) / barWidth;
                int touchMinutes = (int) (ratio * 1440);

                for (TimelineSegment seg : segments) {
                    int s = parseTimeToMinutes(seg.start);
                    int e = parseTimeToMinutes(seg.end);
                    if (touchMinutes >= s && touchMinutes <= e) {
                        String details = seg.label + "\n" + seg.start + " - " + seg.end + " (" + seg.durationMinutes + " min)";
                        Toast.makeText(getContext(), details, Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }
        }
        return true;
    }
}
