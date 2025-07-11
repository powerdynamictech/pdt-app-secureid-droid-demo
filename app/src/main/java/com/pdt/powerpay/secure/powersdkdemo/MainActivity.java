package com.pdt.powerpay.secure.powersdkdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.luxand.FSDK;
import com.pdt.powerpay.secure.powersdk.PowerSDK;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity {

    private boolean success;
    public static boolean liveFaceDetected = false;
    private static final int CAMERA_REQUEST_CODE = 1001;

    private final String database = "facedist.dat";
    public static boolean pauseFaceProcessing = false;

    public static float sDensity = 2.0f;

    private ProcessImageAndDrawResults mDraw;

    private Button cancelButton;
    private TextView infoTextView;
    private LinearLayout mLayout;
    private Preview mPreview;

    private void initSdk() {

        // Initialize PowerSDK
        success = PowerSDK.initialize(this);
        if (!success) {
            Log.e("SDK.Demo", "Power SDK activation failed!");
            finish(); // Optional: exit app or show error
            return;
        }

        Log.d("SDK.Demo", "Power SDK activation success");

    }

    private void openCamera() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST_CODE);
        } else {

            startFaceSDK();
        }
    }

    private void resetTrackerParameters() {
        int[] errpos;
        errpos = new int[1];

        FSDK.SetTrackerMultipleParameters(mDraw.mTracker, "DetectFaces=true;RecognizeFaces=true;ContinuousVideoFeed=true;FacialFeatureJitterSuppression=0;Threshold=0.9500;MemoryLimit=4000;HandleArbitraryRotations=false;DetermineFaceRotationAngle=false;InternalResizeWidth=300;FaceDetectionThreshold=5;", errpos);

        FSDK.SetTrackerParameter(mDraw.mTracker, "KeepFaceImages", "false");

    }

    private void startFaceSDK() {

        CheckFaceDatabase();

        // Create the main layout container (LinearLayout)
        mLayout = new LinearLayout(this);
        LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params2.gravity = Gravity.BOTTOM;
        mLayout.setLayoutParams(params2);
        mLayout.setOrientation(LinearLayout.VERTICAL);

        setContentView(mLayout);

        // Create a background view (can be customized as needed)
        View background = new View(this);
        mDraw = new ProcessImageAndDrawResults(this);
        mPreview = new Preview(this, mDraw);

        mLayout.setBackgroundColor(Color.parseColor("#FFFFFF"));
        mLayout.setPadding(16, 16, 16, 16);

        // Initialize the tracker
        mDraw.mTracker = new FSDK.HTracker();

        // Layout parameters for child views (mPreview and mDraw)
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        addContentView(background, params);

        // Create and configure the FrameLayout
        FrameLayout frameLayout = new FrameLayout(this);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        frameLayout.setLayoutParams(frameParams);

        // Add mPreview to FrameLayout
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1200);
        mPreview.setLayoutParams(previewParams);
        mLayout.setPadding(20, 20, 20, 20);
        frameLayout.addView(mPreview, previewParams);

        mLayout.addView(frameLayout, previewParams);
        addContentView(mDraw, previewParams);

        // information text
        infoTextView = new TextView(this);
        infoTextView.setText("Scan your face...");
        infoTextView.setTextSize(20);
        infoTextView.setPadding(15, 60, 15, 100);
        infoTextView.setTextColor(Color.BLACK);
        infoTextView.setGravity(Gravity.CENTER);


        // Create LayoutParams for TextView
        LinearLayout.LayoutParams textViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textViewParams.setMargins(0, 20, 0, 20); // Optional: add some margin around the TextView

        infoTextView.setLayoutParams(textViewParams);
        mLayout.addView(infoTextView);


        // Create and configure the "Cancel" button
        cancelButton = new Button(this);
        cancelButton.setText("Cancel");
        cancelButton.setTextColor(Color.parseColor("#FFFFFF"));
        cancelButton.setId(View.generateViewId());

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                1000,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.gravity = Gravity.BOTTOM | Gravity.CENTER;
        buttonParams.setMargins(15, 100, 15, 100);
        cancelButton.setLayoutParams(buttonParams);
        mLayout.addView(cancelButton);


        // Initialize SDK tracker from file or create a new one if it fails
        String templatePath = this.getApplicationInfo().dataDir + "/" + database;
        if (FSDK.FSDKE_OK != FSDK.LoadTrackerMemoryFromFile(mDraw.mTracker, templatePath)) {
            int res = FSDK.CreateTracker(mDraw.mTracker);
            if (FSDK.FSDKE_OK != res) {

                Log.d("SDK.Demo", "Error Start Face Recognition");

                finish();
                return;
            }
        }

        resetTrackerParameters();

        cancelButton.setOnClickListener(v -> {

            cancelButton.setVisibility(View.GONE);
            finish();

        });

    }

    private void CheckFaceDatabase() {

        // Define the template path for loading the SDK tracker data
        String templatePath = this.getApplicationInfo().dataDir + "/" + database;
        File file = new File(templatePath);

        if (file.exists()) {

            Log.d("SDK.Demo", "Face Database File exists at: " + templatePath);

        } else {

            Toast.makeText(this, "Error dace database is corrupted!", Toast.LENGTH_SHORT).show();
            Log.d("SDK.Demo", "File does not exist at: " + templatePath);
        }

    }

    private void downloadFaceDatabase() {

        //========= for Romania data
        String url = "https://powerpaysecure.net/devices/1-facedist.dat";
        String templatePath = this.getApplicationInfo().dataDir + "/" + database;
        downloadFile(url, templatePath);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        downloadFaceDatabase(); // always download new face database file

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                initSdk();
                openCamera();

            }
        }, 2500); // Timer 2 seconds delay to start...

    }

    private void downloadFile(String fileUrl, String outputPath) {
        new Thread(() -> {
            try {
                URL url = new URL(fileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e("Download", "Server returned HTTP " + connection.getResponseCode());
                    return;
                }

                InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(outputPath);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }

                output.close();
                input.close();

                Log.d("SDK.Demo", "Download complete: " + outputPath);
            } catch (Exception e) {
                Log.e("SDK.Demo", "Error: " + e.getMessage());
            }
        }).start();
    }


    // Draw graphics on top of the video

    // Draw graphics on top of the video
    class ProcessImageAndDrawResults extends View {
        public FSDK.HTracker mTracker;

        final String[] mAttributeValue = new String[1];
        final float[] mScore = new float[1];

        final int MAX_FACES = 1;
        final FaceRectangle[] mFacePositions = new FaceRectangle[MAX_FACES];
        final long[] mIDs = new long[MAX_FACES];
        final Lock faceLock = new ReentrantLock();
        int mTouchedIndex;
        long mTouchedID;
        int mStopping;
        int mStopped;

        Context mContext;
        Paint mPaintGreen, mPaintBlue, mPaintBlueTransparent;
        byte[] mYUVData;
        byte[] mRGBData;
        int mImageWidth, mImageHeight;
        boolean first_frame_saved;
        boolean rotated;

        void GetFaceFrame(FSDK.FSDK_Features Features, FaceRectangle fr) {
            if (Features == null || fr == null)
                return;

            float u1 = Features.features[0].x;
            float v1 = Features.features[0].y;
            float u2 = Features.features[1].x;
            float v2 = Features.features[1].y;
            float xc = (u1 + u2) / 2;
            float yc = (v1 + v2) / 2;
            int w = (int) Math.pow((u2 - u1) * (u2 - u1) + (v2 - v1) * (v2 - v1), 0.5);

            fr.x1 = (int) (xc - w * 1.6 * 0.9);
            fr.y1 = (int) (yc - w * 1.1 * 0.9);
            fr.x2 = (int) (xc + w * 1.6 * 0.9);
            fr.y2 = (int) (yc + w * 2.1 * 0.9);
            if (fr.x2 - fr.x1 > fr.y2 - fr.y1) {
                fr.x2 = fr.x1 + fr.y2 - fr.y1;
            } else {
                fr.y2 = fr.y1 + fr.x2 - fr.x1;
            }
        }


        public ProcessImageAndDrawResults(Context context) {
            super(context);

            mTouchedIndex = -1;

            mStopping = 0;
            mStopped = 0;
            rotated = false;
            mContext = context;
            mPaintGreen = new Paint();
            mPaintGreen.setStyle(Paint.Style.FILL);
            mPaintGreen.setColor(Color.GREEN);
            mPaintGreen.setStrokeWidth(6);
            mPaintGreen.setTextSize(25 * MainActivity.sDensity);
            mPaintGreen.setTextAlign(Paint.Align.CENTER);
            mPaintBlue = new Paint();
            mPaintBlue.setStyle(Paint.Style.FILL);
            mPaintBlue.setColor(Color.RED);
            mPaintBlue.setStrokeWidth(6);
            mPaintBlue.setTextSize(25 * MainActivity.sDensity);
            mPaintBlue.setTextAlign(Paint.Align.CENTER);

            mPaintBlueTransparent = new Paint();
            mPaintBlueTransparent.setStyle(Paint.Style.STROKE);
            mPaintBlueTransparent.setStrokeWidth(12);
            mPaintBlueTransparent.setColor(Color.BLUE);
            mPaintBlueTransparent.setTextSize(25);

            //mBitmap = null;
            mYUVData = null;
            mRGBData = null;

            first_frame_saved = false;
        }


        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (mStopping == 1) {
                mStopped = 1;
                super.onDraw(canvas);
                return;
            }

            if (pauseFaceProcessing || mYUVData == null || mTouchedIndex != -1) {
                super.onDraw(canvas);
                return;
            }


            decodeYUV420SP(mRGBData, mYUVData, mImageWidth, mImageHeight);

            FSDK.HImage image = new FSDK.HImage();
            FSDK.FSDK_IMAGEMODE mode = new FSDK.FSDK_IMAGEMODE();
            mode.mode = FSDK.FSDK_IMAGEMODE.FSDK_IMAGE_COLOR_24BIT;

            FSDK.LoadImageFromBuffer(image, mRGBData, mImageWidth, mImageHeight, mImageWidth * 3, mode);
            FSDK.MirrorImage(image, false);

            FSDK.HImage rotatedImage = new FSDK.HImage();
            FSDK.CreateEmptyImage(rotatedImage);

            int inputImageWidth = mImageWidth;
            int inputImageHeight = mImageHeight;

            if (rotated) {
                inputImageWidth = mImageHeight;
                inputImageHeight = mImageWidth;
                FSDK.RotateImage90(image, -1, rotatedImage);
            } else {
                FSDK.CopyImage(image, rotatedImage);
            }
            FSDK.FreeImage(image);

            long[] ids = new long[MAX_FACES];
            long[] faceCount = new long[1];
            FSDK.FeedFrame(mTracker, 0, rotatedImage, faceCount, ids);
            FSDK.FreeImage(rotatedImage);

            int canvasWidth = getWidth();
            int canvasHeight = getHeight();

            float scaleX = (float) canvasWidth / inputImageWidth;
            float scaleY = (float) canvasHeight / inputImageHeight;

            faceLock.lock();

            for (int i = 0; i < MAX_FACES; ++i) {
                mFacePositions[i] = new FaceRectangle();
                mIDs[i] = ids[i];
            }

            for (int i = 0; i < faceCount[0]; ++i) {
                FSDK.FSDK_Features eyes = new FSDK.FSDK_Features();
                FSDK.GetTrackerEyes(mTracker, 0, mIDs[i], eyes);
                GetFaceFrame(eyes, mFacePositions[i]);

                mFacePositions[i].x1 *= scaleX;
                mFacePositions[i].y1 *= scaleY;
                mFacePositions[i].x2 *= scaleX;
                mFacePositions[i].y2 *= scaleY;
            }

            faceLock.unlock();

            int shift = (int) (22 * MainActivity.sDensity);

            for (int i = 0; i < faceCount[0]; ++i) {
                canvas.drawRect(
                        mFacePositions[i].x1,
                        mFacePositions[i].y1,
                        mFacePositions[i].x2,
                        mFacePositions[i].y2,
                        mPaintBlueTransparent
                );

                if (mIDs[i] != -1) {
                    mDraw.mTouchedID = mIDs[i];

                    int res = FSDK.GetTrackerFacialAttribute(mTracker, 0, mIDs[i], "Liveness", mAttributeValue, 1024);
                    FSDK.GetValueConfidence(mAttributeValue[0], "Liveness", mScore);
                    liveFaceDetected = false;

                    String[] names = new String[1];
                    FSDK.GetAllNames(mTracker, mIDs[i], names, 1024);

                    if (!TextUtils.isEmpty(names[0])) {
                        float centerX = (mFacePositions[i].x1 + mFacePositions[i].x2) / 2;
                        float bottomY = mFacePositions[i].y2 + shift;
                        canvas.drawText(names[0], centerX, bottomY, mPaintGreen);
                        infoTextView.setText(names[0]);
                    }
                }
            }

            super.onDraw(canvas);
        }


        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) { //NOTE: the method can be implemented in Preview class

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                //Log.d("NETWORK", "tapped at x=" + x + " and y=" + y);
            }
            return true;
        }

        public void decodeYUV420SP(byte[] rgb, byte[] yuv420sp, int width, int height) {
            final int frameSize = width * height;
            int yp = 0;
            for (int j = 0; j < height; j++) {
                int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
                for (int i = 0; i < width; i++) {
                    int y = (0xff & ((int) yuv420sp[yp])) - 16;
                    if (y < 0) y = 0;
                    if ((i & 1) == 0) {
                        v = (0xff & yuv420sp[uvp++]) - 128;
                        u = (0xff & yuv420sp[uvp++]) - 128;
                    }
                    int y1192 = 1192 * y;
                    int r = (y1192 + 1634 * v);
                    int g = (y1192 - 833 * v - 400 * u);
                    int b = (y1192 + 2066 * u);
                    if (r < 0) r = 0;
                    else if (r > 262143) r = 262143;
                    if (g < 0) g = 0;
                    else if (g > 262143) g = 262143;
                    if (b < 0) b = 0;
                    else if (b > 262143) b = 262143;

                    rgb[3 * yp] = (byte) ((r >> 10) & 0xff);
                    rgb[3 * yp + 1] = (byte) ((g >> 10) & 0xff);
                    rgb[3 * yp + 2] = (byte) ((b >> 10) & 0xff);
                    ++yp;
                }
            }
        }
    } // end of ProcessImageAndDrawResults class

    static class FaceRectangle {
        public int x1, y1, x2, y2;
    }

    // Show video from camera and pass frames to ProcessImageAndDraw class
    class Preview extends SurfaceView implements SurfaceHolder.Callback {
        Context mContext;
        SurfaceHolder mHolder;
        Camera mCamera;
        Camera.Size mPreviewSize;
        List<Camera.Size> mSupportedPreviewSizes;
        ProcessImageAndDrawResults mDraw;
        boolean mFinished;
        boolean mIsCameraOpen = false;

        boolean mIsPreviewStarted = false;

        Preview(Context context, ProcessImageAndDrawResults draw) {
            super(context);
            mContext = context;
            mDraw = draw;

            //Install a SurfaceHolder.Callback so we get notified when the underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        //SurfaceView callback
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            if (mIsCameraOpen) return; // surfaceCreated can be called several times
            mIsCameraOpen = true;

            mFinished = false;

            // Find the ID of the camera
            int cameraId = 0;
            boolean frontCameraFound = false;
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, cameraInfo);
                //if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    cameraId = i;
                    frontCameraFound = true;
                }
            }


            if (frontCameraFound) {
                mCamera = Camera.open(cameraId);
            } else {
                mCamera = Camera.open();
            }

            try {
                mCamera.setPreviewDisplay(holder);

                // Preview callback used whenever new viewfinder frame is available
                mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        if ((mDraw == null) || mFinished)
                            return;

                        if (mDraw.mYUVData == null) {
                            // Initialize the draw-on-top companion
                            Camera.Parameters params = camera.getParameters();
                            mDraw.mImageWidth = params.getPreviewSize().width;
                            mDraw.mImageHeight = params.getPreviewSize().height;
                            mDraw.mRGBData = new byte[3 * mDraw.mImageWidth * mDraw.mImageHeight];
                            mDraw.mYUVData = new byte[data.length];
                        }

                        // Pass YUV data to draw-on-top companion
                        System.arraycopy(data, 0, mDraw.mYUVData, 0, data.length);
                        mDraw.invalidate();
                    }
                });
            } catch (Exception exception) {

                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setMessage("No cameras found. App will close.")
                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                android.os.Process.killProcess(android.os.Process.myPid());
                            }
                        })
                        .show();
                if (mCamera != null) {
                    mCamera.release();
                    mCamera = null;
                }
            }
        }

        private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
            final double ASPECT_TOLERANCE = 0.1;
            double targetRatio = (double) w / h;
            if (sizes == null) return null;

            Camera.Size optimalSize = null;
            double minDiff = Double.MAX_VALUE;

            // Try to find an size match aspect ratio and size
            for (Camera.Size size : sizes) {
                double ratio = (double) size.width / size.height;
                if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
                }
            }

            // Cannot find the one match the aspect ratio, ignore the requirement
            if (optimalSize == null) {
                for (Camera.Size size : sizes) {
                    if (Math.abs(size.height - h) < minDiff) {
                        optimalSize = size;
                        minDiff = Math.abs(size.height - h);
                    }
                }
            }
            return optimalSize;
        }

        public void releaseCallbacks() {
            if (mCamera != null) {
                mCamera.setPreviewCallback(null);
            }
            if (mHolder != null) {
                mHolder.removeCallback(this);
            }
            mDraw = null;
            mHolder = null;
        }

        //SurfaceView callback
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            // Surface will be destroyed when we return, so stop the preview.
            // Because the CameraDevice object is not a shared resource, it's very
            // important to release it when the activity is paused.
            mFinished = true;
            if (mCamera != null) {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }

            mIsCameraOpen = false;
            mIsPreviewStarted = false;
        }

        //SurfaceView callback, configuring camera
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int w, int h) {
            if (mCamera == null) return;

            // Now that the size is known, set up the camera parameters and begin
            // the preview.
            Camera.Parameters parameters = mCamera.getParameters();

            //Keep uncommented to work correctly on phones:
            //This is an undocumented although widely known feature
            /**/
            if (this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
                parameters.set("orientation", "portrait");
                mCamera.setDisplayOrientation(90); // For Android 2.2 and above
                mDraw.rotated = true;
            } else {
                parameters.set("orientation", "landscape");
                mCamera.setDisplayOrientation(0); // For Android 2.2 and above
            }
            /**/

            // choose preview size closer to 640x480 for optimal performance
            List<Camera.Size> supportedSizes = parameters.getSupportedPreviewSizes();
            int width = 0;
            int height = 0;
            for (Camera.Size s : supportedSizes) {
                if ((width - 640) * (width - 640) + (height - 480) * (height - 480) >
                        (s.width - 640) * (s.width - 640) + (s.height - 480) * (s.height - 480)) {
                    width = s.width;
                    height = s.height;
                }
            }

            //try to set preferred parameters
            try {
                if (width * height > 0) {
                    parameters.setPreviewSize(width, height);
                } else if (mPreviewSize.width * mPreviewSize.height > 0) {
                    parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
                } else {
                    parameters.setPreviewSize(this.getWidth(), this.getHeight());
                }
                //parameters.setPreviewFrameRate(10);
                parameters.setSceneMode(Camera.Parameters.SCENE_MODE_PORTRAIT);
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                mCamera.setParameters(parameters);
            } catch (Exception ignored) {


            }

            if (!mIsPreviewStarted) {
                mCamera.startPreview();
                mIsPreviewStarted = true;
            }

            parameters = mCamera.getParameters();
            mPreviewSize = parameters.getPreviewSize();
            makeResizeForCameraAspect(1.0f / ((1.0f * mPreviewSize.width) / mPreviewSize.height));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            // We purposely disregard child measurements because act as a
            // wrapper to a SurfaceView that centers the camera preview instead
            // of stretching it.

            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);

            //MUST CALL THIS
            setMeasuredDimension(width, height);

            if (mSupportedPreviewSizes != null) {
                mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
            }
        }


        private void makeResizeForCameraAspect(float cameraAspectRatio) {

            ViewGroup.LayoutParams params = this.getLayoutParams();
            int matchParentWidth = this.getWidth();
            int newHeight = (int) (matchParentWidth / cameraAspectRatio);
            if (newHeight != params.height) {
                params.height = newHeight;
                params.width = matchParentWidth;
                this.setLayoutParams(params);
                this.invalidate();
            }
        }
    } // end of Preview class


}