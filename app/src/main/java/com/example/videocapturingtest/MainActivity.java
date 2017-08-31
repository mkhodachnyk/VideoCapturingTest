package com.example.videocapturingtest;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.camera.SCamera;
import com.samsung.android.sdk.camera.SCameraCaptureSession;
import com.samsung.android.sdk.camera.SCameraCharacteristics;
import com.samsung.android.sdk.camera.SCameraDevice;
import com.samsung.android.sdk.camera.SCameraManager;
import com.samsung.android.sdk.camera.SCaptureRequest;
import com.samsung.android.sdk.camera.STotalCaptureResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements CountDownAnimation.CountDownListener {
    private ImageView ivStop;
    private ImageView ivGetReady;
    private TextView tvCountDown;
    private CountDownAnimation countDownAnimation;
    private TextView tvDuration;
    private Spinner spinner;
    private View progressBar;

    private static int counter;

    private static double duration;
    private String PATH;
    private String android_id;

    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Sample_ConstrainedHSV";
    private static final String SLOW_MO_UPLOAD = "UPLOAD_TAG";

    private SCamera mSCamera;
    private SCameraManager mSCameraManager;

    /**
     * A reference to the opened {@link com.samsung.android.sdk.camera.SCameraDevice}.
     */
    private SCameraDevice mSCameraDevice;
    private SCameraCaptureSession mSCameraSession;
    private SCameraCharacteristics mCharacteristics;

    /**
     * Maximum preview width app will use.
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;
    /**
     * Maximum preview height app will use.
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * {@link com.samsung.android.sdk.camera.SCaptureRequest.Builder} for the camera preview and recording
     */
    private SCaptureRequest.Builder mPreviewBuilder;

    /**
     * Current Preview Size.
     */
    private Size mPreviewSize;

    /**
     * ID of the current {@link com.samsung.android.sdk.camera.SCameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView = null;

    /**
     * A camera related listener/callback will be posted in this handler.
     */
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;

    /**
     * An orientation listener for jpeg orientation
     */
    private OrientationEventListener mOrientationListener;
    private int mLastOrientation = 0;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Current app state.
     */
    private CAMERA_STATE mState = CAMERA_STATE.IDLE;

    /**
     * The {@link MediaRecorder} for recording audio and video.
     */
    private MediaRecorder mMediaRecorder;

    private boolean videoRecording = false;

    /**
     * Button to record video
     */
    private ImageButton mRecordButton;

    private long mRecordingStartTime;
    /**
     * A {@link com.samsung.android.sdk.camera.SCameraCaptureSession.CaptureCallback} for {@link com.samsung.android.sdk.camera.SCameraCaptureSession#setRepeatingRequest(com.samsung.android.sdk.camera.SCaptureRequest, com.samsung.android.sdk.camera.SCameraCaptureSession.CaptureCallback, android.os.Handler)}
     */
    private SCameraCaptureSession.CaptureCallback mSessionCaptureCallback = new SCameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(SCameraCaptureSession session, SCaptureRequest request, STotalCaptureResult result) {
            // Depends on the current state and capture result, app will take next action.
            switch (getState()) {

                case IDLE:
                case PREVIEW:
                case CLOSING:
                    // do nothing
                    break;
            }
        }
    };


    @Override
    public void onPause() {

        if (getState() == CAMERA_STATE.RECORD_VIDEO) stopRecordVideo(true);

        setState(CAMERA_STATE.CLOSING);

        setOrientationListener(false);

        stopBackgroundThread();

        closeCamera();

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        setState(CAMERA_STATE.IDLE);

        startBackgroundThread();

        // initialize SCamera
        mSCamera = new SCamera();
        try {
            mSCamera.initialize(this);
        } catch (SsdkUnsupportedException e) {
            showAlertDialog("Fail to initialize SCamera.", true);
            return;
        }

        setOrientationListener(true);

        if (!checkRequiredFeatures()) return;

        createUI();
        openCamera();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);

        android_id = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ANDROID_ID);

        PATH = getExternalFilesDir("DCIM").getAbsolutePath();
        Log.d("PATH", PATH);
        if (savedInstanceState == null) {
            //Check permissions
            ensurePermissions(Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        Intent prevIntent = getIntent();
//        if (prevIntent.hasExtra("audio")) {
            duration = prevIntent.getDoubleExtra("duration", 4);
//        }

        progressBar = findViewById(R.id.progress_bar);

        ivStop = findViewById(R.id.iv_stop);
        ivGetReady = findViewById(R.id.iv_get_ready);
        tvCountDown = findViewById(R.id.txt_countdown);
        tvDuration = findViewById(R.id.txt_duration);
        tvDuration.setText(duration + "s");

        spinner = findViewById(R.id.spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.animations_array,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        initCountDownAnimation();

        if (counter > 0 && counter % 150 == 0) {
            counter = 0;
            showAlert();
        } else {
            counter++;
        }
    }

    private void showAlert() {

        final AlertDialog.Builder dialog = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AlertDialogCustom));
        dialog.setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Warning!\n")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        sendBroadcast(new Intent("xyz"));
                        finishAffinity();
                    }
                })
                .setMessage("Your system is low on memory and the application will need to close. Please relaunch the application after it closes")
                .setCancelable(false)
                .create();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dialog.show();
            }
        });
    }

    private void ensurePermissions(String... permissions) {
        ArrayList<String> deniedPermissionList = new ArrayList<>();

        for (String permission : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, permission))
                deniedPermissionList.add(permission);
        }

        if (!deniedPermissionList.isEmpty())
            ActivityCompat.requestPermissions(this, deniedPermissionList.toArray(new String[0]), 0);
    }

    private boolean checkRequiredFeatures() {
        try {
            mCameraId = null;

            for (String id : mSCamera.getSCameraManager().getCameraIdList()) {
                SCameraCharacteristics cameraCharacteristics = mSCamera.getSCameraManager().getCameraCharacteristics(id);
                if (cameraCharacteristics.get(SCameraCharacteristics.LENS_FACING) == SCameraCharacteristics.LENS_FACING_FRONT) {
                    mCameraId = id;
                    break;
                }
            }

            if (mCameraId == null) {
                showAlertDialog("No back-facing camera exist.", true);
                return false;
            }

            mCharacteristics = mSCamera.getSCameraManager().getCameraCharacteristics(mCameraId);

//            if (!contains(mCharacteristics.get(SCameraCharacteristics.CONTROL_AF_AVAILABLE_MODES), SCameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
//                showAlertDialog("Required AF mode is not supported.", true);
//                return false;
//            }

            StreamConfigurationMap streamConfigurationMap = mCharacteristics.get(SCameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            mPreviewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];

            for (Size option : streamConfigurationMap.getOutputSizes(SurfaceTexture.class)) {
                // Find maximum preview size that is not larger than MAX_PREVIEW_WIDTH/MAX_PREVIEW_HEIGHT
                int areaCurrent = Math.abs((mPreviewSize.getWidth() * mPreviewSize.getHeight()) - (MAX_PREVIEW_WIDTH * MAX_PREVIEW_HEIGHT));
                int areaNext = Math.abs((option.getWidth() * option.getHeight()) - (MAX_PREVIEW_WIDTH * MAX_PREVIEW_HEIGHT));

                if (areaCurrent > areaNext) mPreviewSize = option;
            }

        } catch (CameraAccessException e) {
            showAlertDialog("Cannot access the camera.", true);
            Log.e(TAG, "Cannot access the camera.", e);
            return false;
        }

        return true;
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();

            stopPreview();

            if (mSCameraSession != null) {
                mSCameraSession.close();
                mSCameraSession = null;
            }

            if (mSCameraDevice != null) {
                mSCameraDevice.close();
                mSCameraDevice = null;
            }

            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }

            mSCameraManager = null;
            mSCamera = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Configures requires transform {@link android.graphics.Matrix} to TextureView.
     */
    private void configureTransform(int viewWidth, int viewHeight) {

        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else {
            matrix.postRotate(90 * rotation, centerX, centerY);
        }

        mTextureView.setTransform(matrix);
        mTextureView.getSurfaceTexture().setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
    }

    private boolean contains(final int[] array, final int key) {
        for (final int i : array) {
            if (i == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates file name based on current time.
     */
    private String createFileName() {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeZone(TimeZone.getDefault());
        long dateTaken = calendar.getTimeInMillis();

        return DateFormat.format("yyyyMMdd_kkmmss", dateTaken).toString();
    }

    /**
     * Create a {@link com.samsung.android.sdk.camera.SCameraCaptureSession} for preview.
     */
    private void createPreviewSession() {

        if (null == mSCamera || null == mSCameraDevice || null == mSCameraManager || !mTextureView.isAvailable())
            return;

        try {
//            mPreviewSize = mVideoParameter.mVideoSize;

            setState(CAMERA_STATE.START_PREVIEW);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                }
            });

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(
                        mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(
                        mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }

            prepareMediaRecorder();

            SurfaceTexture texture = mTextureView.getSurfaceTexture();

            // Set default buffer size to camera preview size.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            Surface previewSurface = new Surface(texture);
            Surface recorderSurface = mMediaRecorder.getSurface();

            // Creates SCaptureRequest.Builder for preview and recording with output target.
            mPreviewBuilder = mSCameraDevice.createCaptureRequest(SCameraDevice.TEMPLATE_RECORD);

            // {@link com.samsung.android.sdk.camera.processor.SCameraEffectProcessor} supports only 24fps.
//            mPreviewBuilder.set(SCaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(30, 30));
//            mPreviewBuilder.set(SCaptureRequest.CONTROL_AF_MODE, SCaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewBuilder.addTarget(previewSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            // Creates a CameraCaptureSession here.
            List<Surface> outputSurface = Arrays.asList(previewSurface, recorderSurface);
            mSCameraDevice.createCaptureSession(outputSurface, new SCameraCaptureSession.StateCallback() {
                @Override
                public void onConfigureFailed(SCameraCaptureSession sCameraCaptureSession) {
                    if (getState() == CAMERA_STATE.CLOSING)
                        return;
                    showAlertDialog("Fail to create camera capture session.", true);
                }

                @Override
                public void onConfigured(SCameraCaptureSession sCameraCaptureSession) {
                    if (getState() == CAMERA_STATE.CLOSING)
                        return;
                    mSCameraSession = (SCameraCaptureSession) sCameraCaptureSession;
                    startPreview();
                }
            }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Prepares an UI, like button, etc.
     */
    private void createUI() {
        mRecordButton = (ImageButton) findViewById(R.id.video_record);
        mRecordButton.setEnabled(true);
        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getState() == CAMERA_STATE.PREVIEW) {
                    startCountDownAnimation();
                } else if (getState() == CAMERA_STATE.RECORD_VIDEO) {
                    ivStop.setVisibility(View.GONE);
//                    tvDuration.setText(duration + "s");
                    countDownAnimation.cancel();
                    stopRecordVideo(false);
                }
            }
        });

        mTextureView = (AutoFitTextureView) findViewById(R.id.texture);

        // Set SurfaceTextureListener that handle life cycle of TextureView
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // "onSurfaceTextureAvailable" is called, which means that SCameraCaptureSession is not created.
                // We need to configure transform for TextureView and crate SCameraCaptureSession.
                configureTransform(width, height);
                createPreviewSession();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                // SurfaceTexture size changed, we need to configure transform for TextureView, again.
                configureTransform(width, height);
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });

        VideoParameter videoParameter = new VideoParameter(new Size(1920, 1080), new Range<>(30, 30));

//        if (!videoParameter.equals(mVideoParameter)) {
//            mVideoParameter = videoParameter;
//
//            if (getState() == CAMERA_STATE.PREVIEW) {
//                mMediaRecorder.reset();
//                createPreviewSession();
//            }
//        }
    }

    /**
     * Returns required orientation that the jpeg picture needs to be rotated to be displayed upright.
     */
    private int getJpegOrientation() {
        int degrees = mLastOrientation;

        if (mCharacteristics.get(SCameraCharacteristics.LENS_FACING) == SCameraCharacteristics.LENS_FACING_FRONT) {
            degrees = -degrees;
        }
        return (mCharacteristics.get(SCameraCharacteristics.SENSOR_ORIENTATION) + degrees + 360) % 360;
    }

    private synchronized CAMERA_STATE getState() {
        return mState;
    }

    private synchronized void setState(CAMERA_STATE state) {
        mState = state;
    }

    /**
     * Opens a {@link com.samsung.android.sdk.camera.SCameraDevice}.
     */
    private void openCamera() {
        try {
            if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
                ensurePermissions(Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
            if (!mCameraOpenCloseLock.tryAcquire(3000, TimeUnit.MILLISECONDS)) {
                showAlertDialog("Time out waiting to lock camera opening.", true);
            }

            mSCameraManager = mSCamera.getSCameraManager();
            mMediaRecorder = new MediaRecorder();

            // Opening the camera device here
            mSCameraManager.openCamera(mCameraId, new SCameraDevice.StateCallback() {
                @Override
                public void onDisconnected(SCameraDevice sCameraDevice) {
                    mCameraOpenCloseLock.release();
                    if (getState() == CAMERA_STATE.CLOSING)
                        return;
                    showAlertDialog("Camera disconnected.", true);
                }

                @Override
                public void onError(SCameraDevice sCameraDevice, int i) {
                    mCameraOpenCloseLock.release();
                    if (getState() == CAMERA_STATE.CLOSING)
                        return;
                    showAlertDialog("Error while camera open.", true);
                }

                public void onOpened(SCameraDevice sCameraDevice) {
                    Log.v(TAG, "onOpened");
                    mCameraOpenCloseLock.release();
                    if (getState() == CAMERA_STATE.CLOSING)
                        return;
                    mSCameraDevice = sCameraDevice;
                    createPreviewSession();
                }
            }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            showAlertDialog("Cannot open the camera.", true);
            Log.e(TAG, "Cannot open the camera.", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Prepares the media recorder to begin recording.
     */
    private void prepareMediaRecorder() throws IOException {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        CamcorderProfile cpHigh = CamcorderProfile
                .get(Integer.parseInt(mCameraId), CamcorderProfile.QUALITY_HIGH);
        mMediaRecorder.setProfile(cpHigh);
        mMediaRecorder.setOutputFile(new File(getExternalFilesDir(null), "temp.mp4").getAbsolutePath());
        mMediaRecorder.setMaxDuration(30000);
//        mMediaRecorder.setMaxFileSize(5000000); // Approximately 5 megabytes
        mMediaRecorder.setOrientationHint(getJpegOrientation());
        mMediaRecorder.prepare();
    }

    private synchronized void recordVideo() {
        setState(CAMERA_STATE.RECORD_VIDEO);

        // Start recording
        mMediaRecorder.start();
        mRecordingStartTime = System.currentTimeMillis();
    }

    /**
     * Enable/Disable an orientation listener.
     */
    private void setOrientationListener(boolean isEnable) {
        if (mOrientationListener == null) {

            mOrientationListener = new OrientationEventListener(this) {
                @Override
                public void onOrientationChanged(int orientation) {
                    if (orientation == ORIENTATION_UNKNOWN) return;
                    mLastOrientation = (orientation + 45) / 90 * 90;
                }
            };
        }

        if (isEnable) {
            mOrientationListener.enable();
        } else {
            mOrientationListener.disable();
        }
    }

    /**
     * Shows alert dialog.
     */
    private void showAlertDialog(String message, final boolean finishActivity) {

        final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Alert")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (finishActivity) finish();
                    }
                }).setCancelable(false);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dialog.show();
            }
        });
    }

    /**
     * Starts background thread that callback from camera will posted.
     */
    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("Background Thread");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    /**
     * Starts a preview.
     */
    private void startPreview() {
        try {
            // Starts displaying the preview.
            List<SCaptureRequest> sCaptureRequestList = new ArrayList<>();
            sCaptureRequestList.add(mPreviewBuilder.build());
            mSCameraSession.setRepeatingBurst(sCaptureRequestList, mSessionCaptureCallback, mBackgroundHandler);
            setState(CAMERA_STATE.PREVIEW);
        } catch (CameraAccessException e) {
            showAlertDialog("Fail to start preview.", true);
        }
    }

    /**
     * Stops background thread.
     */
    private void stopBackgroundThread() {
        if (mBackgroundHandlerThread != null) {
            mBackgroundHandlerThread.quitSafely();
            try {
                mBackgroundHandlerThread.join();
                mBackgroundHandlerThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Stop a preview.
     */
    private void stopPreview() {
        try {
            if (mSCameraSession != null) {
                try {
                    mSCameraSession.stopRepeating();
                } catch (IllegalStateException e) {
                    Log.d("MyLog", "Exception!");
                    e.printStackTrace();
                }
            }
        } catch (CameraAccessException e) {
            showAlertDialog("Fail to stop preview.", true);
        }
    }

    private synchronized void stopRecordVideo(boolean isPausing) {

        // prevents terminated during that the operation to start.
        if (!isPausing && (System.currentTimeMillis() - mRecordingStartTime) < 1000) {
            return;
        }

        if (videoRecording) {

            mMediaRecorder.stop();
            mMediaRecorder.reset();

            videoRecording = false;

            File dir = new File(getExternalFilesDir("DCIM").getAbsolutePath());
            if (!dir.exists()) dir.mkdirs();

            final File file = new File(dir, createFileName() + "_" + android_id + "_Samsung_SlowMo.mp4");
            new File(getExternalFilesDir(null), "temp.mp4").renameTo(file);

            Video video = new Video(file.getName(), false);
            video.save();

            Log.d("UPLOAD_TAG", "stopRecording: new video added to queue");

            if (!isPausing) {
                createPreviewSession();
            }
        }
    }

    @Override
    public void onCountDownEnd(CountDownAnimation animation) {
        videoRecording = true;
        recordVideo();
        MyCountDownTimer countDownTimer = new MyCountDownTimer((long) (duration * 1000), 500);
        countDownTimer.start();
        ivStop.setVisibility(View.VISIBLE);
    }

    private enum CAMERA_STATE {
        IDLE, START_PREVIEW, PREVIEW, RECORD_VIDEO, CLOSING
    }

    private static class VideoParameter {
        final Size mVideoSize;
        final Range<Integer> mFpsRange;

        VideoParameter(Size videoSize, Range<Integer> fpsRange) {
            mVideoSize = new Size(videoSize.getWidth(), videoSize.getHeight());
            mFpsRange = new Range<>(fpsRange.getLower(), fpsRange.getUpper());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof VideoParameter &&
                    mVideoSize.equals(((VideoParameter) o).mVideoSize) &&
                    mFpsRange.equals(((VideoParameter) o).mFpsRange);
        }

        @Override
        public String toString() {
            return mVideoSize.toString() + " @ " + mFpsRange.getUpper() + "FPS";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                showAlertDialog("Requested permission is not granted.", true);
            }
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void initCountDownAnimation() {
        Animation scaleAnimation = new ScaleAnimation(1.0f, 0.0f, 1.0f, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        countDownAnimation = new CountDownAnimation(tvCountDown, getStartCount());
        countDownAnimation.setAnimation(scaleAnimation);
        countDownAnimation.setCountDownListener(this);
    }

    private void cancelCountDownAnimation() {
        countDownAnimation.cancel();
    }

    private int getStartCount() {
        return 5;
    }

    private void startCountDownAnimation() {
        mRecordButton.setClickable(false);
        // Customizable animation
        if (spinner.getSelectedItemPosition() == 1) { // Scale
            // Use scale animation
            Animation scaleAnimation = new ScaleAnimation(1.0f, 0.0f, 1.0f,
                    0.0f, Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            countDownAnimation.setAnimation(scaleAnimation);
        } else if (spinner.getSelectedItemPosition() == 2) { // Set (Scale +
            // Alpha)
            // Use a set of animations
            Animation scaleAnimation = new ScaleAnimation(1.0f, 0.0f, 1.0f,
                    0.0f, Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            Animation alphaAnimation = new AlphaAnimation(1.0f, 0.0f);
            AnimationSet animationSet = new AnimationSet(false);
            animationSet.addAnimation(scaleAnimation);
            animationSet.addAnimation(alphaAnimation);
            countDownAnimation.setAnimation(animationSet);
        }

        // Customizable start count
        countDownAnimation.setStartCount(getStartCount());
//        mRecordButton.setClickable(false);
        ivGetReady.setVisibility(View.VISIBLE);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                ivGetReady.setVisibility(View.INVISIBLE);
                progressBarToRight(5000);
                countDownAnimation.start();
            }
        }, 1000);
    }

    private void progressBarToRight(long durationMillis) {
        ScaleAnimation anim = new ScaleAnimation(0.0f, 1.0f, 1.0f, 1.0f);
        anim.setFillAfter(true);
        anim.setFillBefore(false);
        anim.setFillEnabled(true);
        anim.setDuration(durationMillis);
        progressBar.startAnimation(anim);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mRecordButton.setClickable(true);
                progressBarToLeft((long) (duration * 1000));
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    private void progressBarToLeft(long durationMillis) {
        ScaleAnimation anim = new ScaleAnimation(1.0f, 0.0f, 1.0f, 1.0f);
        anim.setFillAfter(true);
        anim.setFillBefore(false);
        anim.setFillEnabled(true);
        anim.setDuration(durationMillis);
        progressBar.startAnimation(anim);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                ivStop.setVisibility(View.GONE);
                tvDuration.setText(duration + "s");
                stopRecordVideo(false);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    private class MyCountDownTimer extends CountDownTimer {
        public MyCountDownTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            tvDuration.setText(millisUntilFinished / 1000 + ".0s");
        }

        @Override
        public void onFinish() {
        }
    }

}
