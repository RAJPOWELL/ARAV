package com.atharvakale.facerecognition;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;

public class Utils {
    private static final int INPUT_SIZE = 112;
    private static final int OUTPUT_SIZE = 192;

    public static MappedByteBuffer loadModelFile(Activity activity, String modelFile) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelFile);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }


    private static Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        if (bitmap == null) {
            Log.e("Utils", "rotateBitmap: Bitmap is null!");
            return null;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }


    public static void extractEmbeddings(Interpreter tfLite, Bitmap faceBitmap, float[][] output) {
        if (faceBitmap == null) {
            Log.e("Utils", "extractEmbeddings: faceBitmap is null!");
            return;
        }

        int width = faceBitmap.getWidth();
        int height = faceBitmap.getHeight();

        if (width <= 0 || height <= 0) {
            Log.e("Utils", "extractEmbeddings: Invalid faceBitmap dimensions! Width: " + width + ", Height: " + height);
            return;
        }

        // Ensure the bitmap is resized to the correct input size
        faceBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);

        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
        imgData.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];  // ✅ Ensuring correct array size

        faceBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        imgData.rewind();
        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                int pixelValue = intValues[i * INPUT_SIZE + j];
                imgData.putFloat((((pixelValue >> 16) & 0xFF) - 128.0f) / 128.0f);
                imgData.putFloat((((pixelValue >> 8) & 0xFF) - 128.0f) / 128.0f);
                imgData.putFloat(((pixelValue & 0xFF) - 128.0f) / 128.0f);
            }
        }

        tfLite.run(imgData, output);
    }


    @androidx.camera.core.ExperimentalGetImage
    public static Bitmap cropFace(androidx.camera.view.PreviewView previewView, ImageProxy imageProxy, Rect boundingBox) {
        if (imageProxy.getImage() == null) {
            Log.e("Utils", "ImageProxy.getImage() is null!");
            return null;
        }

        Bitmap frameBitmap = imageProxyToBitmap(imageProxy);
        if (frameBitmap == null) {
            Log.e("Utils", "Frame bitmap is null!");
            return null;
        }

        Bitmap rotatedBitmap = rotateBitmap(frameBitmap, imageProxy.getImageInfo().getRotationDegrees());
        RectF bbox = new RectF(boundingBox);

        Bitmap croppedFace = Bitmap.createBitmap((int) bbox.width(), (int) bbox.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(croppedFace);
        canvas.drawBitmap(rotatedBitmap, -bbox.left, -bbox.top, new Paint(Paint.FILTER_BITMAP_FLAG));

        return Bitmap.createScaledBitmap(croppedFace, 112, 112, true); // Resize for recognition
    }




    @androidx.camera.core.ExperimentalGetImage
    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) return null;

        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        YuvImage yuvImage = new YuvImage(bytes, ImageFormat.NV21, imageProxy.getWidth(), imageProxy.getHeight(), null);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, outputStream);
        byte[] jpegBytes = outputStream.toByteArray();

        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    public static boolean recognizeFace(Map<String, SimilarityClassifier.Recognition> registered, Interpreter tfLite, Bitmap faceBitmap, FaceBoxOverlay faceBoxOverlay) {
        float[][] embeddings = new float[1][OUTPUT_SIZE];
        extractEmbeddings(tfLite, faceBitmap, embeddings);

        float minDistance = Float.MAX_VALUE;
        String recognizedName = "Unknown";

        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {
            float[] storedEmbedding = ((float[][]) entry.getValue().getExtra())[0];
            float distance = calculateDistance(embeddings[0], storedEmbedding);

            if (distance < minDistance && distance < 1.0f) {
                minDistance = distance;
                recognizedName = entry.getKey();
            }
        }

        faceBoxOverlay.setRecognitionText(recognizedName);
        return !recognizedName.equals("Unknown");
    }

    private static float calculateDistance(float[] emb1, float[] emb2) {
        float sum = 0;
        for (int i = 0; i < emb1.length; i++) {
            float diff = emb1[i] - emb2[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }
}
