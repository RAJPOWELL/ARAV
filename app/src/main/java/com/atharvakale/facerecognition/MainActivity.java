package com.atharvakale.facerecognition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private FaceDetector detector;
    private Interpreter tfLite;
    private PreviewView previewView;
    private FaceBoxOverlay faceBoxOverlay;
    private ProcessCameraProvider cameraProvider;
    private final HashMap<String, SimilarityClassifier.Recognition> registered = new HashMap<>();
    private static final int MY_CAMERA_REQUEST_CODE = 100;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        faceBoxOverlay = findViewById(R.id.faceBoxOverlay);
        Button scanButton = findViewById(R.id.scanButton);
        FrameLayout container = findViewById(R.id.container);

        initializeFaceDetection();
        initializeModel();
        preloadFaces(); // Load faces at startup

        scanButton.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                container.setVisibility(View.VISIBLE);
                scanButton.setVisibility(View.GONE);
                startCamera();
            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
            }
        });
    }

    private void initializeFaceDetection() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build();
        detector = FaceDetection.getClient(options);
    }

    private void initializeModel() {
        try {
            tfLite = new Interpreter(Utils.loadModelFile(this, "mobile_face_net.tflite"));
        } catch (IOException e) {
            Log.e("ModelInit", "Error initializing model", e);
        }
    }

    private void preloadFaces() {
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                String[] faceFiles = getAssets().list("faces");
                if (faceFiles == null || faceFiles.length == 0) {
                    Log.e("FaceRecognition", "No face images found in assets!");
                    return;
                }

                Log.d("FaceRecognition", "Total faces found: " + faceFiles.length);
                for (String fileName : faceFiles) {
                    if (fileName.toLowerCase().endsWith(".jpg")) {
                        Bitmap bitmap = BitmapFactory.decodeStream(getAssets().open("faces/" + fileName));
                        if (bitmap == null) {
                            Log.e("FaceRecognition", "Failed to load face image: " + fileName);
                            continue;
                        }

                        processAndRegisterFace(bitmap, fileName.replace(".jpg", ""));
                        Log.d("FaceRecognition", "Loaded face: " + fileName);
                    }
                }
            } catch (IOException e) {
                Log.e("FaceRecognition", "Error loading faces", e);
            }
        });
    }

    private void processAndRegisterFace(Bitmap faceBitmap, String name) {
        if (faceBitmap == null) {
            Log.e("MainActivity", "Face bitmap is null, skipping embedding extraction!");
            return;
        }

        float[][] embeddings = new float[1][192]; // 192-d embedding vector
        Utils.extractEmbeddings(tfLite, faceBitmap, embeddings);

        // Create Recognition object with correct constructor parameters
        SimilarityClassifier.Recognition recognition = new SimilarityClassifier.Recognition(
                name, name, 1.0f
        );
        recognition.setExtra(embeddings);
        registered.put(name, recognition);

        Log.d("MainActivity", "Face registered: " + name);
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::analyzeImage);
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            } catch (Exception e) {
                Log.e("CameraInit", "Camera binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeImage(ImageProxy imageProxy) {
        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    Log.d("FaceDetection", "Faces detected: " + faces.size());

                    if (faces.isEmpty()) {
                        Log.e("FaceDetection", "No faces detected!");
                    }

                    for (Face face : faces) {
                        Log.d("FaceDetection", "Face bounding box: " + face.getBoundingBox().toString());

                        Bitmap croppedFace = Utils.cropFace(previewView, imageProxy, face.getBoundingBox());
                        if (croppedFace == null) {
                            Log.e("FaceDetection", "Cropped face is null!");
                            continue;
                        }

                        boolean recognized = Utils.recognizeFace(registered, tfLite, croppedFace, faceBoxOverlay);
                        Log.d("FaceRecognition", "Face recognized: " + recognized);
                    }
                })
                .addOnFailureListener(e -> Log.e("FaceDetection", "Detection failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }
}
