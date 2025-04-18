package com.loookup.modelingtest;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.*;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {

    private SceneView sceneView;
    private SurfaceView cameraPreview;
    private CameraManager cameraManager;
    private CameraCaptureSession captureSession;
    private CameraDevice cameraDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // SceneView 설정
        sceneView = findViewById(R.id.scene_view);
        sceneView.setBackground(null);

        // 카메라 프리뷰 설정
        cameraPreview = findViewById(R.id.camera_preview);
        cameraPreview.getHolder().setKeepScreenOn(true); // 화면 유지
        cameraPreview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                openCamera(holder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                releaseCamera();
            }
        });

        // 카메라 권한 요청
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        }

        // GLB 모델 로드 및 배치
        createScene();
    }

    private void openCamera(SurfaceHolder holder) {
        if (!holder.getSurface().isValid()) {
            Log.e("Camera", "SurfaceHolder is not valid");
            return;
        }
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String cameraId = cameraManager.getCameraIdList()[0]; // 첫 번째 카메라
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice device) {
                        cameraDevice = device;
                        startPreview(holder);
                    }

                    @Override
                    public void onDisconnected(CameraDevice device) {
                        device.close();
                    }

                    @Override
                    public void onError(CameraDevice device, int error) {
                        device.close();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "카메라 열기 실패: " + error, Toast.LENGTH_LONG).show());
                    }
                }, null);
            }
        } catch (CameraAccessException e) {
            Toast.makeText(this, "카메라 열기 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startPreview(SurfaceHolder holder) {
        if (cameraDevice == null) {
            Log.e("Camera", "Cannot start preview: cameraDevice is null");
            return;
        }
        if (!holder.getSurface().isValid()) {
            Log.e("Camera", "Cannot start preview: Surface is not valid");
            return;
        }
        try {
            // SurfaceView의 Surface를 프리뷰 타겟으로 설정
            Surface surface = holder.getSurface();
            CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            // 프리뷰 세션 생성
            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                Log.e("Camera", "CameraDevice closed during session configuration");
                                return;
                            }
                            captureSession = session;
                            try {
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                                session.setRepeatingRequest(previewRequestBuilder.build(), null, null);
                                Log.d("Camera", "Preview session started successfully");
                            } catch (CameraAccessException e) {
                                Log.e("Camera", "Failed to start preview session: " + e.getMessage());
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "프리뷰 시작 실패: " + e.getMessage(), Toast.LENGTH_LONG).show());
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e("Camera", "Preview session configuration failed");
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "프리뷰 설정 실패", Toast.LENGTH_LONG).show());
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Log.e("Camera", "Failed to create capture session: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "프리뷰 시작 실패: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void releaseCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void createScene() {
        // GLB 모델 로드
        ModelRenderable.builder()
                .setSource(this, RenderableSource.builder()
                        .setSource(this, Uri.parse("model.glb"), RenderableSource.SourceType.GLB)
                        .build())
                .build()
                .thenAccept(renderable -> {
                    // 20개 모델을 일렬로 배치 (원근법)
                    for (int i = 0; i < 10; i++) {
                        Node modelNode = new Node();
                        modelNode.setRenderable(renderable);

                        // 상대 위치: X축 중심, Z축으로 원근감 (z: -1 ~ -5)
                        float z = -1f - (i * 0.2f); // -1, -1.2, -1.4, ..., -4.8
                        modelNode.setLocalPosition(new Vector3(0f + i, 0f, z));

                        // 자세: Y축 기준 고유 회전 (18도씩 증가)
                        float angle = i * 18f; // 0, 18, 36, ..., 342
                        modelNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1, 0), angle));

                        // SceneView에 추가
                        sceneView.getScene().addChild(modelNode);
                    }
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        Toast toast = Toast.makeText(this, "Failed to load model: " + throwable.getMessage(), Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    });
                    return null;
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            sceneView.resume();
        } catch (CameraNotAvailableException e) {
            throw new RuntimeException(e);
        }
        if (cameraDevice != null && cameraPreview.getHolder().getSurface().isValid()) {
            startPreview(cameraPreview.getHolder());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sceneView.pause();
        releaseCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sceneView.destroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera(cameraPreview.getHolder());
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_LONG).show();
        }
    }
}