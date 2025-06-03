package com.myname.focuslock;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import com.myname.focuslock.GaussianFitter;
import com.myname.focuslock.CameraPollingTask;

import org.micromanager.Studio;

import mmcorej.CMMCore;

public class CalibrateTask {
    private Studio studio;
    private CMMCore core;
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private BiConsumer<Double, Double> onCalibrationFinished;

    private final int numSteps = 6;
    private final double stepSizeUm = 0.25;
    
    private final double[] positionsUm = new double[numSteps];
    private final double[] pixelMeans = new double[numSteps];
    
    private int currentStep = 0;
    private double startZ = 0;
    private String stage;
    
    public CalibrateTask(Studio studio) {
    	this.studio = studio;
    	this.core = studio.core();
    	
    	try {
    		this.stage = core.getFocusDevice();
    	} catch(Exception e) {
    		studio.logs().showError("Could not find focus stage: " + e.toString());
    	}
    }
    
    public void setOnCalibrationFinished(BiConsumer<Double, Double> callback) {
        this.onCalibrationFinished = callback;
    }
    
    public void startCalibration() {
    	try {
    		startZ = core.getPosition(stage);
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
    		core.setPosition(stage, targetZ);
    		Thread.sleep(1000); // Give hardware a moment to settle
    	} catch (Exception e) {
    		studio.logs().showError("Stage movement failed: " + e.getMessage());
    		return;
    	}
    	
    	try {
    		short[] data = new CameraPollingTask(studio).snapOnce();
            double[] result = new GaussianFitter(data).fit();
            double mean = result[1];
            
            positionsUm[currentStep] = targetZ;
            pixelMeans[currentStep] = mean;
            studio.logs().logMessage("Step " + currentStep + ": Z=" + targetZ + ", Mean=" + mean);
            
            currentStep++;
            scheduler.schedule(this::stepCalibration, 1000, java.util.concurrent.TimeUnit.MILLISECONDS); // 1.0s between steps
    	} catch (Exception e) {
            studio.logs().showError("Image acquisition failed: " + e.getMessage());
    	}
    }
    
    private void finishCalibration() {
    	try {
    		core.setPosition(stage, startZ);
    	} catch (Exception e) {
    		studio.logs().showError("Returning to original Z position failed: " + e.getMessage());
    	}
    	
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
