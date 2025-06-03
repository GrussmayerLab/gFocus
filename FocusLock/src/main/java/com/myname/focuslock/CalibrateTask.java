package com.myname.focuslock;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import com.myname.focuslock.GaussianFitter;

import org.micromanager.Studio;

import mmcorej.CMMCore;

public class CalibrateTask {
    private Studio studio;
    private CMMCore core;
    
    private final String cameraName = "gFocus Light Sensor";
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private BiConsumer<Double, Double> onCalibrationFinished;

    private final int numSteps = 6;
    private final double stepSizeUm = 25.0;
    
    private final double[] positionsUm = new double[numSteps];
    private final double[] pixelMeans = new double[numSteps];
    
    private int currentStep = 0;
    private double startZ = 0;
    
    public CalibrateTask(Studio studio) {
    	this.studio = studio;
    	this.core = studio.core();    	
    }
    
    public void setOnCalibrationFinished(BiConsumer<Double, Double> callback) {
        this.onCalibrationFinished = callback;
    }
    
    public void startCalibration() {
    	try {
    		startZ = core.getPosition("Z");
    	} catch (Exception e) {
    		studio.logs().showError("Failed to get initial stage position: " + e.getMessage());
    		return;
    	}
    	
    	scheduler.execute(this::stepCalibration);
    }
    
    private void stepCalibration() {
    	if (currentStep >= numSteps) {
    		finishCalibration();
    	}
    	
    	double targetZ = startZ + currentStep * stepSizeUm;
    	
    	try {
    		core.setPosition("Z", targetZ);
    		Thread.sleep(1000); // Give hardware a moment to settle
    	} catch (Exception e) {
    		studio.logs().showError("Stage movement failed: " + e.getMessage());
    		return;
    	}
    	
    	try {
    		core.setCameraDevice(cameraName);
    		core.snapImage();
    		Object img = core.getImage();
    		short[] data;
    		
            if (img instanceof byte[]) {
                byte[] raw = (byte[]) img;
                data = new short[raw.length / 2];
                for (int i = 0; i < data.length; i++) {
                    int low = raw[2 * i] & 0xFF;
                    int high = raw[2 * i + 1] & 0xFF;
                    data[i] = (short) ((high << 8) | low);
                }
            } else if (img instanceof short[]) {
                data = (short[]) img;
            } else {
                studio.logs().showError("Unsupported image format: " + img.getClass().getSimpleName());
                return;
            }
            double[] result = new GaussianFitter(data).fit();
            double mean = result[1];
            
            positionsUm[currentStep] = targetZ;
            pixelMeans[currentStep] = mean;
            studio.logs().logMessage("Step " + currentStep + ": Z=" + targetZ + ", Mean=" + mean);
            
            currentStep++;
            scheduler.schedule(this::stepCalibration, 500, java.util.concurrent.TimeUnit.MILLISECONDS); // 0.5s between steps
    	} catch (Exception e) {
            studio.logs().showError("Image acquisition failed: " + e.getMessage());
    	}
    }
    
    private void finishCalibration() {
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < numSteps; i++) {
            double x = pixelMeans[i];
            double y = positionsUm[i];

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        double slope = (numSteps * sumXY - sumX * sumY) / (numSteps * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / numSteps;

        studio.logs().logMessage(String.format("Calibration complete. µm per mean pixel value = %.6f, intercept = %.3f", slope, intercept));
        
        if (onCalibrationFinished != null) {
            onCalibrationFinished.accept(slope, intercept);
        }
    }
}
