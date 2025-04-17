package com.loookup.modelingtest;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity {

    private GLSurfaceView glSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // GLSurfaceView 생성 및 설정
        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(2); // OpenGL ES 2.0 사용
        glSurfaceView.setRenderer(new CubeRenderer());
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        setContentView(glSurfaceView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
    }

    // 3D 큐브 렌더러
    private static class CubeRenderer implements GLSurfaceView.Renderer {
        private FloatBuffer vertexBuffer;
        private FloatBuffer colorBuffer;
        private int program;

        // 큐브 정점 데이터
        private final float[] vertices = {
                // 앞면
                -0.5f, -0.5f,  0.5f,
                0.5f, -0.5f,  0.5f,
                0.5f,  0.5f,  0.5f,
                -0.5f,  0.5f,  0.5f,
                // 뒷면
                -0.5f, -0.5f, -0.5f,
                0.5f, -0.5f, -0.5f,
                0.5f,  0.5f, -0.5f,
                -0.5f,  0.5f, -0.5f,
        };

        // 큐브 색상 데이터 (각 면에 단색 적용)
        private final float[] colors = {
                1.0f, 0.0f, 0.0f, 1.0f, // 빨강
                1.0f, 0.0f, 0.0f, 1.0f,
                1.0f, 0.0f, 0.0f, 1.0f,
                1.0f, 0.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f, // 초록
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
        };

        // 큐브 인덱스 데이터
        private final byte[] indices = {
                // 앞면
                0, 1, 2, 2, 3, 0,
                // 뒷면
                4, 5, 6, 6, 7, 4,
                // 왼쪽
                0, 3, 7, 7, 4, 0,
                // 오른쪽
                1, 5, 6, 6, 2, 1,
                // 위
                3, 2, 6, 6, 7, 3,
                // 아래
                0, 4, 5, 5, 1, 0
        };

        // 셰이더 코드
        private final String vertexShaderCode =
                "uniform mat4 uMVPMatrix;" +
                        "attribute vec4 vPosition;" +
                        "attribute vec4 vColor;" +
                        "varying vec4 fColor;" +
                        "void main() {" +
                        "  gl_Position = uMVPMatrix * vPosition;" +
                        "  fColor = vColor;" +
                        "}";

        private final String fragmentShaderCode =
                "precision mediump float;" +
                        "varying vec4 fColor;" +
                        "void main() {" +
                        "  gl_FragColor = fColor;" +
                        "}";

        private final float[] mvpMatrix = new float[16];
        private final float[] projectionMatrix = new float[16];
        private final float[] viewMatrix = new float[16];
        private final float[] modelMatrix = new float[16];
        private float angle = 0.0f;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // 배경색 설정
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);

            // 정점 버퍼 초기화
            ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(vertices);
            vertexBuffer.position(0);

            // 색상 버퍼 초기화
            ByteBuffer cb = ByteBuffer.allocateDirect(colors.length * 4);
            cb.order(ByteOrder.nativeOrder());
            colorBuffer = cb.asFloatBuffer();
            colorBuffer.put(colors);
            colorBuffer.position(0);

            // 셰이더 컴파일
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);

            // 투영 행렬 설정
            float ratio = (float) width / height;
            Matrix.perspectiveM(projectionMatrix, 0, 45.0f, ratio, 0.1f, 10.0f);

            // 뷰 행렬 설정
            Matrix.setLookAtM(viewMatrix, 0,
                    0.0f, 0.0f, 2.0f, // 카메라 위치
                    0.0f, 0.0f, 0.0f, // 시선 방향
                    0.0f, 1.0f, 0.0f  // 위쪽 방향
            );
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            // 모델 행렬 설정 (회전 애니메이션)
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.rotateM(modelMatrix, 0, angle, 0.0f, 1.0f, 0.0f); // Y축 회전
            angle += 1.0f;

            // MVP 행렬 계산
            Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0);
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0);

            // 셰이더 프로그램 사용
            GLES20.glUseProgram(program);

            // 정점 데이터 전달
            int positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);

            // 색상 데이터 전달
            int colorHandle = GLES20.glGetAttribLocation(program, "vColor");
            GLES20.glEnableVertexAttribArray(colorHandle);
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 16, colorBuffer);

            // MVP 행렬 전달
            int mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

            // 큐브 그리기
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_BYTE,
                    ByteBuffer.allocateDirect(indices.length).order(ByteOrder.nativeOrder()).put(indices).rewind());

            // 정점 배열 비활성화
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(colorHandle);
        }

        private int loadShader(int type, String shaderCode) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderCode);
            GLES20.glCompileShader(shader);
            return shader;
        }
    }
}