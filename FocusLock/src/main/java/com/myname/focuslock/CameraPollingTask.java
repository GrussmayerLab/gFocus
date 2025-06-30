package com.myname.focuslock;

import org.micromanager.Studio;
import mmcorej.CMMCore;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A repeating task that grabs a snapshot from a custom camera every 10 seconds.
 */
public class CameraPollingTask {
    private final Studio studio;
    private final CMMCore privateCore ;
    private final CMMCore mainCore;
    private final String cameraName = "gFocus Light Sensor";
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();    
    public volatile short[] pixelData;
    private Consumer<short[]> onImageUpdate;
//    private boolean isCameraAttached = false;

    public CameraPollingTask(Studio studio) {
        this.studio = studio;
        this.privateCore  = new CMMCore();
        this.mainCore  = studio.getCMMCore();

        try {
        	privateCore.loadSystemConfiguration("C:/Program Files/Micro-Manager-2.0/gFocus/gFocus.cfg");
        	this.setAverage(1);
        	this.setExposure(1.0);
            studio.logs().logMessage("Private core for light sensor initialized");
        } catch (Exception e) {
            studio.logs().showError("Failed to initialize private core for light sensor: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
//    private void attachCamera() {
//        if (!isCameraAttached) {
//            try {
//                privateCore.setCameraDevice(cameraName);
//                isCameraAttached = true;
//            } catch (Exception e) {
//            	studio.logs().logMessage("Failed to attach camera: " + e.getMessage());
//            }
//        }
//    }
//
//    private void detachCamera() {
//        if (isCameraAttached) {
//            try {
//                privateCore.setCameraDevice("");  // Detach by setting to empty string
//                isCameraAttached = false;
//            } catch (Exception e) {
//            	studio.logs().logMessage("Failed to detach camera: " + e.getMessage());
//            }
//        }
//    }
    
    public void setExposure(double exposure) {
        try {
            privateCore.setProperty(cameraName, "Time [ms]", exposure);
            studio.logs().logMessage("Set exposure to: " + exposure);
        } catch (Exception e) {
            studio.logs().showError("Failed to set exposure: " + e.getMessage());
        }
    }

    public void setAverage(int average) {
        try {
            privateCore.setProperty(cameraName, "Average #", average);
            studio.logs().logMessage("Set averaging to: " + average);
        } catch (Exception e) {
            studio.logs().showError("Failed to set averaging: " + e.getMessage());
        }
    }
    
    public void setOnImageUpdate(Consumer<short[]> callback) {
        this.onImageUpdate = callback;
    }

    public void start() {
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }

        scheduler.scheduleWithFixedDelay(() -> {
            final int maxRetries = 10;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
//                	attachCamera();
                	privateCore.setCameraDevice(cameraName);
                    privateCore.snapImage();
                    Object img = privateCore.getImage();
//                    detachCamera();
                    
                    if (img instanceof byte[]) {
                        byte[] raw = (byte[]) img;
                        int numPixels = raw.length / 2;
                        pixelData = new short[numPixels];
                        for (int i = 0; i < numPixels; i++) {
                            int low = raw[2 * i] & 0xFF;
                            int high = raw[2 * i + 1] & 0xFF;
                            pixelData[i] = (short) ((high << 8) | low);
                        }
                    } else if (img instanceof short[]) {
                        pixelData = (short[]) img;
                    } else {
                        studio.logs().showError("Unsupported image type: " + img.getClass().getSimpleName());
                        return;
                    }

                    // Success
                    StringBuilder sb = new StringBuilder();
                    sb.append("Snapshot at ").append(System.currentTimeMillis()).append("\n");
                    for (int i = 0; i < Math.min(256, pixelData.length); i++) {
                        sb.append(pixelData[i]).append(" ");
                    }

                    if (onImageUpdate != null) {
                        studio.logs().logMessage(sb.toString());
                        onImageUpdate.accept(pixelData);
                        studio.logs().logMessage("Exposure: " + privateCore.getExposure());
                    }
                    return; // done if successful

                } catch (Exception e) {
                    if (attempt == maxRetries) {
                        studio.logs().showError("Failed to acquire image after " + maxRetries + " attempts: " + e.getMessage());
                        e.printStackTrace();
                        scheduler.shutdownNow();
                    }
                    // no sleep; retry immediately
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }


    public void stop() {
        scheduler.shutdownNow();
    }
    
    public short[] snapOnce() {
        final int maxRetries = 10;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
//            	attachCamera(); // <- Attach before using
            	privateCore.setCameraDevice(cameraName);
            	privateCore.snapImage();
                Object img = privateCore.getImage();
//                detachCamera(); // <- Detach after using
                
                if (img instanceof byte[]) {
                    byte[] raw = (byte[]) img;
                    int numPixels = raw.length / 2;
                    short[] result = new short[numPixels];
                    for (int i = 0; i < numPixels; i++) {
                        int low = raw[2 * i] & 0xFF;
                        int high = raw[2 * i + 1] & 0xFF;
                        result[i] = (short) ((high << 8) | low);
                    }
                    return result;
                } else if (img instanceof short[]) {
                    return (short[]) img;
                } else {
                    studio.logs().showError("Unsupported image type: " + img.getClass().getSimpleName());
                    return null;
                }

            } catch (Exception e) {
                if (attempt == maxRetries) {
                    studio.logs().showError("snapOnce failed after " + maxRetries + " attempts: " + e.getMessage());
                    e.printStackTrace();
                }
                // immediate retry
            }
        }

        return null; // All attempts failed
    }
}
