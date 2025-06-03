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
    private final CMMCore core;
    
    private final String cameraName = "gFocus Light Sensor";
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();    
    public volatile short[] pixelData;
    private Consumer<short[]> onImageUpdate;

    public CameraPollingTask(Studio studio) {
        this.studio = studio;
        this.core = studio.core();
    }
    
    public void setOnImageUpdate(Consumer<short[]> callback) {
        this.onImageUpdate = callback;
    }

    public void start() {
        try {
            core.setCameraDevice(cameraName);
            core.snapImage();
            core.getImage(); 
        } catch (Exception e) {
            studio.logs().logMessage("First snap failed (warm-up): " + e.getMessage());
        }

        scheduler.scheduleWithFixedDelay(() -> {
            final int maxRetries = 5;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    core.setCameraDevice(cameraName);
                    core.snapImage();
                    Object img = core.getImage();

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
                    System.out.println("Snapshot at " + System.currentTimeMillis());
                    for (int i = 0; i < Math.min(256, pixelData.length); i++) {
                        System.out.print(pixelData[i] + " ");
                    }
                    System.out.println();

                    if (onImageUpdate != null) {
                        onImageUpdate.accept(pixelData);
                    }
                    return; // done if successful

                } catch (Exception e) {
                    if (attempt == maxRetries) {
                        studio.logs().showError("Failed to acquire image after " + maxRetries + " attempts: " + e.getMessage());
                        e.printStackTrace();
                    }
                    // no sleep; retry immediately
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }


    public void stop() {
        scheduler.shutdownNow();
    }
}
