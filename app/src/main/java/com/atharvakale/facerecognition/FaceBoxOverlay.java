package com.atharvakale.facerecognition;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View; // ✅ Fixed import

import java.util.ArrayList;
import java.util.List;

public class FaceBoxOverlay extends View { // ✅ Corrected View class
    private final Paint boxPaint;
    private final Paint textPaint;
    private final List<RectF> faceBoundingBoxes = new ArrayList<>();
    private int boxColor = Color.RED; // Default: Red for unrecognized faces
    private String recognitionText = "Unknown";  // Default name

    public FaceBoxOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setStrokeWidth(5f);
        boxPaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(50f);
        textPaint.setStyle(Paint.Style.FILL);
    }

    public void setFaceBoxes(List<RectF> boxes, boolean isRecognized) {
        faceBoundingBoxes.clear();
        faceBoundingBoxes.addAll(boxes);
        boxColor = isRecognized ? Color.GREEN : Color.RED;
        invalidate(); // ✅ Works now
    }

    public void setRecognitionText(String text) {
        recognitionText = text;
        invalidate(); // ✅ Works now
    }

    @Override
    protected void onDraw(Canvas canvas) { // ✅ Works now
        super.onDraw(canvas);
        boxPaint.setColor(boxColor);

        for (RectF box : faceBoundingBoxes) {
            canvas.drawRect(box, boxPaint);
            float textX = box.left;
            float textY = box.top - 10;
            canvas.drawText(recognitionText, textX, textY, textPaint);
        }
    }
}
