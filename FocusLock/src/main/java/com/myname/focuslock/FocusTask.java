package com.myname.focuslock;
import com.myname.focuslock.CameraPollingTask;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.lang.Math;

import org.apache.commons.math3.analysis.function.Abs;
import org.micromanager.Studio;
import mmcorej.CMMCore;

public class FocusTask {
	private Studio studio;
	private CMMCore core;
	
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    private String stage;
    private double calSlope = 0;
    private double refMean = 0;
    private double focusThreshold = 2;
    private double mean = 0;
    private boolean start = false;
    
    public FocusTask(Studio studio) {
    	this.studio = studio;
    	this.core = studio.core();
    }
    
    public double[] startFocus(double slopeCal) {
    	calSlope = slopeCal;
    	double[] result = new double[3];
    	try {
    		short[] data = new CameraPollingTask(studio).snapOnce();
            result = new GaussianFitter(data).fit();
            refMean = result[1];
    	} catch(Exception e) {
    		studio.logs().showError("Image acquisition failed: " + e.toString());
    	}
    	start = true;
    	scheduler.execute(this::focussing);
    	return result;
    }
    
    private void focussing() {
    	double startZ;
    	double deltaZ;
    	
    	if (!start) {
    		return;
    	}
    	
    	try {
    		short[] data = new CameraPollingTask(studio).snapOnce();
            double[] result = new GaussianFitter(data).fit();
            mean = result[1];
            
    	} catch(Exception e) {
    		studio.logs().showError("Image acquisition failed: " + e.toString());
    		return;
    	}
    	
    	double deltaMean = mean - refMean;
    	
    	if (Math.abs(deltaMean) > focusThreshold) {
    		deltaZ = deltaMean * calSlope;
    		
        	try {
        		startZ = core.getPosition(stage);
        	} catch (Exception e) {
        		studio.logs().showError("Failed to get stage position: " + e.getMessage());
        		return;
        	}
        	
        	double newZ = startZ + deltaZ;
        	
        	try {
        		core.setPosition(stage, newZ);
        		Thread.sleep(1000); // Give hardware a moment to settle
        	} catch (Exception e) {
        		studio.logs().showError("Stage movement failed: " + e.getMessage());
        		return;
        	}
    	}
        scheduler.schedule(this::focussing, 2000, java.util.concurrent.TimeUnit.MILLISECONDS); // 2.0s between steps
    }
    
    public void stopFocus() {
    	start = false;
    }

}
