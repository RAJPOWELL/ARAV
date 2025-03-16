package com.atharvakale.facerecognition;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class FaceBoxOverlay extends View {
    private final Paint boxPaint;
    private final Paint textPaint;
    private final List<RectF> faceBoundingBoxes = new ArrayList<>();
    private final List<String> faceNames = new ArrayList<>(); // ✅ Store names with boxes
    private int boxColor = Color.RED;

    public FaceBoxOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setStrokeWidth(5f);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setColor(boxColor);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f); // ✅ Increased text size
        textPaint.setStyle(Paint.Style.FILL);
    }

    public void setFaceBoxes(List<RectF> boxes, List<String> names, boolean isRecognized) {
        faceBoundingBoxes.clear();
        faceBoundingBoxes.addAll(boxes);

        faceNames.clear();
        faceNames.addAll(names);

        boxColor = isRecognized ? Color.GREEN : Color.RED;
        invalidate(); // ✅ Redraw the view
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boxPaint.setColor(boxColor);

        for (int i = 0; i < faceBoundingBoxes.size(); i++) {
            RectF box = faceBoundingBoxes.get(i);
            canvas.drawRect(box, boxPaint);

            // ✅ Draw name above the box
            String name = faceNames.get(i);
            float textX = box.left + 10;
            float textY = box.top - 20;
            canvas.drawText(name, textX, textY, textPaint);
        }
    }
}
