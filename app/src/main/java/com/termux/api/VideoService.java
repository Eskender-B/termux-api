package com.termux.api;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;
import com.termux.api.util.ConnectionEstablisher;
import com.termux.api.util.ImageUtils;
import com.termux.api.util.TermuxApiLogger;
import java.io.BufferedOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Created by eskender on 1/15/18.
 */

public class VideoService extends IntentService {


    static CameraManager mCameraManager;
    static CameraDevice.StateCallback mCameraDeviceStateCallback;
    static CameraDevice mCamera;
    static ImageReader mImageReader;
    static CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback;
    static CameraCaptureSession.CaptureCallback mCameraCaptureSessionCaptureCallback;
    static Handler mHandler;
    static Surface mImageReaderSurface;
    static List<Surface> mOutputSurfaces;
    private int NOTIFICATION_ID = 102938;
    private String NOTIFICATION_CHANNEL_ID = "termux_video_streamer";
    private Context context;
    private RenderScript mRS;
    private ScriptIntrinsicYuvToRGB mScript;
    private Allocation mAIn, mAOut;

    private Size mSize = new Size(320, 240);


    public VideoService(){
        super("Video Service");
        context = this;
    }


    @Override
    protected void onHandleIntent(final Intent intent){

        TermuxApiLogger.info("VideoStreamer service started");


        setupNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        final String cameraId = Objects.toString(intent.getStringExtra("camera"), "0");

        ConnectionEstablisher conn = new ConnectionEstablisher();


        conn.connectionEstablish(null, intent, conn.new Transmitter(){

            @Override
            public void transmit(BufferedOutputStream bOutputStream){

                //Looper is already prepared by the calling function.
                final Looper looper = Looper.myLooper();

                //Handler used for running all callbacks in this thread.
                mHandler = new Handler(Looper.myLooper());

                setupCamera(bOutputStream, cameraId, looper);
           }

        });

        stopForeground(true);

        TermuxApiLogger.info("VideoStreamer service stopped");
        this.stopSelf();
    }
    public void setupCamera(final BufferedOutputStream bOutputStream, String cameraId, final Looper looper){

        try {
            mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);

            mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice camera) {
                    try {
                        mCamera = camera;
                        proceedWithOpenedCamera(mCameraManager, camera, looper, bOutputStream);
                    } catch (Exception e) {
                        TermuxApiLogger.error("Exception in onOpened()", e);
                        closeCamera(camera, looper);
                        //throw new Exception();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    TermuxApiLogger.info("onDisconnected() from camera");
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    TermuxApiLogger.error("Failed opening camera: " + error);
                    closeCamera(camera, looper);
                    //throw new Exception();
                }
            };
            //noinspection MissingPermission
            mCameraManager.openCamera(cameraId, mCameraDeviceStateCallback, mHandler);

        } catch (Exception e) {
            TermuxApiLogger.error("Error getting camera", e);
            looper.quit();
        }

    }

    private void proceedWithOpenedCamera(final CameraManager manager, final CameraDevice camera, final Looper looper, final BufferedOutputStream bOutputStream)throws CameraAccessException, IllegalArgumentException {

        mOutputSurfaces = new ArrayList<>();

        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera.getId());

        int autoExposureMode = CameraMetadata.CONTROL_AE_MODE_OFF;
        for (int supportedMode : characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)) {
            if (supportedMode == CameraMetadata.CONTROL_AE_MODE_ON) {
                autoExposureMode = supportedMode;
            }
        }
        final int autoExposureModeFinal = autoExposureMode;

        // Use largest available size:
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        final Comparator<Size> bySize = new Comparator<Size>() {
            @Override
            public int compare(Size lhs, Size rhs) {
                // Cast to ensure multiplications won't overflow:
                return Long.signum(((long) lhs.getWidth() * lhs.getHeight()) - ((long) rhs.getWidth() * rhs.getHeight()));
            }
        };

        List<Size> sizes = Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888));

        TermuxApiLogger.info(sizes.toString());

        if(!sizes.contains(mSize)){
            TermuxApiLogger.error("Size " + mSize.toString() + "not supported");
            looper.quit();
            return;
        }

        //Size largest = Collections.min(sizes, bySize);
        //TermuxApiLogger.info("Frame size: " + largest.toString());

        mRS = RenderScript.create(context);
        mScript = ScriptIntrinsicYuvToRGB.create(mRS, Element.RGBA_8888(mRS));

        // Create the input allocation  memory for Renderscript to work with
        Type.Builder yuvType = new Type.Builder(mRS, Element.U8(mRS))
                .setX(mSize.getWidth())
                .setY(mSize.getHeight())
                .setYuvFormat(ImageFormat.YUV_420_888);

       mAIn = Allocation.createTyped(mRS, yuvType.create(), Allocation.USAGE_SCRIPT);

        // Create the output allocation
        Type.Builder rgbType = new Type.Builder(mRS, Element.RGBA_8888(mRS))
                .setX(mSize.getWidth())
                .setY(mSize.getHeight());

       mAOut = Allocation.createTyped(mRS, rgbType.create(), Allocation.USAGE_SCRIPT);


        mImageReader = ImageReader.newInstance(mSize.getWidth(), mSize.getHeight(), ImageFormat.YUV_420_888, 5);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            long t = System.currentTimeMillis();
            @Override
            public void onImageAvailable(final ImageReader reader){

                TermuxApiLogger.info("0: " + (System.currentTimeMillis()-t));
                t = System.currentTimeMillis();

                try {
                    final Image image = reader.acquireNextImage();

                    long prev = System.currentTimeMillis();
                    byte[] data = ImageUtils.imageToArray(image);
                    long curr = System.currentTimeMillis();
                    TermuxApiLogger.info("1: " + (curr-prev));

                    prev = System.currentTimeMillis();
                    mAIn.copyFrom(data);
                    curr = System.currentTimeMillis();
                    TermuxApiLogger.info("2: " + (curr-prev));


                    prev = System.currentTimeMillis();
                    mScript.setInput(mAIn);
                    curr = System.currentTimeMillis();
                    TermuxApiLogger.info("3: " + (curr-prev));

                    prev = System.currentTimeMillis();
                    mScript.forEach(mAOut);
                    curr = System.currentTimeMillis();
                    TermuxApiLogger.info("4: " + (curr-prev));


                    prev = System.currentTimeMillis();
                    //Bitmap outBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.);
                    byte bytes[] = new byte[mSize.getWidth() * mSize.getHeight()*4];
                    TermuxApiLogger.info("Bytes length: "+ bytes.length);
                    curr = System.currentTimeMillis();
                    TermuxApiLogger.info("5: " + (curr-prev));


                    prev = System.currentTimeMillis();
                    //mAOut.copyTo(outBitmap);
                    mAOut.copyTo(bytes);
                    curr = System.currentTimeMillis();
                    TermuxApiLogger.info("6: " + (curr-prev));

                    //prev = System.currentTimeMillis();
                    //ByteBuffer buffer = ByteBuffer.allocate(outBitmap.getByteCount());
                    //curr = System.currentTimeMillis();
                    //TermuxApiLogger.info("7: " + (curr-prev));


                    //prev = System.currentTimeMillis();
                    //outBitmap.copyPixelsToBuffer(buffer);
                    //curr = System.currentTimeMillis();
                    //TermuxApiLogger.info("8: " + (curr-prev));


                    //byte[] bytes = new byte[buffer.remaining()];
                    //buffer.get(bytes);
                    TermuxApiLogger.info("Byte per picture: " + bytes.length);

                    prev = System.currentTimeMillis();
                    bOutputStream.write(bytes, 0, bytes.length);
                    curr = System.currentTimeMillis();
                    TermuxApiLogger.info("9: " + (curr-prev));


                    /*
                    prev = System.currentTimeMillis();
                    bOutputStream.flush();
                    curr = System.currentTimeMillis();
                    TermuxApiLogger.info("10: " + (curr-prev));
                    */
                    /*
                    Image.Plane[] planes = mImage.getPlanes();

                    for (int i = 0; i < planes.length; i++) {
                        ByteBuffer buffer = planes[i].getBuffer();
                        planes[i].getRowStride()
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        TermuxApiLogger.info(i + ": " + bytes.length);
                        bOutputStream.write(bytes, 0, bytes.length);
                    }
                    */

                    image.close();
                } catch (Exception e) {
                    TermuxApiLogger.error("Error writing image", e);
                    closeCamera(camera, looper);
                }
            }
        }, mHandler);


        mImageReaderSurface = mImageReader.getSurface();
        mOutputSurfaces.add(mImageReaderSurface);

        mCameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(final CameraCaptureSession session) {
                try {
                    final CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                    // Render to our image reader:
                    request.addTarget(mImageReaderSurface);
                    // Configure auto-focus (AF) and auto-exposure (AE) modes:
                    request.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    request.set(CaptureRequest.CONTROL_AE_MODE, autoExposureModeFinal);
                    request.set(CaptureRequest.JPEG_ORIENTATION, correctOrientation(context, characteristics));


                    mCameraCaptureSessionCaptureCallback =  new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession completedSession, CaptureRequest request, TotalCaptureResult result) {
                            TermuxApiLogger.info("onCaptureCompleted()");
                            //closeCamera(camera, null);
                        }
                    };

                    session.setRepeatingRequest(request.build(), mCameraCaptureSessionCaptureCallback , mHandler);

                } catch (Exception e) {
                    TermuxApiLogger.error("onConfigured() error in preview", e);
                    closeCamera(camera, looper);
                    //throw new Exception();
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                TermuxApiLogger.error("onConfigureFailed() error in preview");
                closeCamera(camera, looper);
            }
        };

        camera.createCaptureSession(mOutputSurfaces, mCameraCaptureSessionStateCallback , mHandler);

    }

    /**
     * Determine the correct JPEG orientation, taking into account device and sensor orientations.
     * See https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
     */
    private int correctOrientation(final Context context, final CameraCharacteristics characteristics) {
        final Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        final boolean isFrontFacing = lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_FRONT;
        TermuxApiLogger.info((isFrontFacing ? "Using" : "Not using") + " a front facing camera.");

        Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (sensorOrientation != null) {
            TermuxApiLogger.info(String.format("Sensor orientation: %s degrees", sensorOrientation));
        } else {
            TermuxApiLogger.info("CameraCharacteristics didn't contain SENSOR_ORIENTATION. Assuming 0 degrees.");
            sensorOrientation = 0;
        }

        int deviceOrientation;
        final int deviceRotation =
                ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        switch (deviceRotation) {
            case Surface.ROTATION_0:
                deviceOrientation = 0;
                break;
            case Surface.ROTATION_90:
                deviceOrientation = 90;
                break;
            case Surface.ROTATION_180:
                deviceOrientation = 180;
                break;
            case Surface.ROTATION_270:
                deviceOrientation = 270;
                break;
            default:
                TermuxApiLogger.info(
                        String.format("Default display has unknown rotation %d. Assuming 0 degrees.", deviceRotation));
                deviceOrientation = 0;
        }
        TermuxApiLogger.info(String.format("Device orientation: %d degrees", deviceOrientation));

        int jpegOrientation;
        if (isFrontFacing) {
            jpegOrientation = sensorOrientation + deviceOrientation;
        } else {
            jpegOrientation = sensorOrientation - deviceOrientation;
        }
        // Add an extra 360 because (-90 % 360) == -90 and Android won't accept a negative rotation.
        jpegOrientation = (jpegOrientation + 360) % 360;
        TermuxApiLogger.info(String.format("Returning JPEG orientation of %d degrees", jpegOrientation));
        return jpegOrientation;
    }


     void closeCamera(CameraDevice camera, Looper looper) {

        if (looper != null) {
            mHandler.removeCallbacksAndMessages(null);

            TermuxApiLogger.info("Looper quited.");
            looper.quit();
        }

        try {
            camera.close();
        } catch (RuntimeException e) {
            TermuxApiLogger.info("Exception closing camera: " + e.getMessage());
        }
   }


    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        String channelName = "Termux video streamer";
        String channelDescription = "Notifications from Termux video streamer";
        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName,importance);
        channel.setDescription(channelDescription);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {

        String contentText = "video streamer";

        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle(getText(R.string.app_name));
        builder.setContentText(contentText);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setOngoing(true);

        // No need to show a timestamp:
        builder.setShowWhen(false);

        // Background color for small notification icon:
        builder.setColor(0xFF000000);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(NOTIFICATION_CHANNEL_ID);
        }

        return builder.build();
    }

}
