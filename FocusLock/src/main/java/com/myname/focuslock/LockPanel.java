package com.myname.focuslock;

import javax.swing.JPanel;

import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.swinglisteners.SwingUIListeners;
import de.embl.rieslab.emu.ui.uiproperties.RescaledUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.TwoStateUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.ui.uiproperties.flag.NoFlag;
import de.embl.rieslab.emu.utils.EmuUtils;
import de.embl.rieslab.emu.utils.exceptions.IncorrectUIPropertyTypeException;

import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JButton;
import java.awt.TextField;
import java.awt.TextArea;
import com.jgoodies.forms.factories.DefaultComponentFactory;
import java.awt.Font;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.util.function.Consumer; // Add this at the top
import org.micromanager.Studio;

public class LockPanel extends ConfigurablePanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JSpinner spinner;
	private JSpinner spinner_1;
	private JToggleButton btnEnable;
	private JToggleButton btnLock;
	private JButton btnCalibration_1;
	private JLabel lblStatus;
	private CameraPollingTask cameraPollingTask;
	private Consumer<short[]> pixelDataListener;
	private Consumer<double[]> referanceDataListener;

	// properties
	public final String FOCUS_AVERAGE = "average";
	public final String FOCUS_EXPOSURE = "exposure";
	
	// settings
	private float exposure;
	private int average;
	private double slopeCal;
	private SystemController systemController_;
	private Studio studio;
	public LockPanel(String label, SystemController systemController) {
		super(label);
		setLayout(null);
		
		systemController_ = systemController;
		studio  = systemController_.getStudio();
        cameraPollingTask = new CameraPollingTask(systemController_.getStudio()); // studio must be set externally

		JLabel lblNewLabel = new JLabel("Average");
		lblNewLabel.setFont(new Font("Tahoma", Font.PLAIN, 12));
		lblNewLabel.setBounds(22, 50, 45, 13);
		add(lblNewLabel);
		
		JLabel lblExposure = new JLabel("Exposure [ms]");
		lblExposure.setFont(new Font("Tahoma", Font.PLAIN, 12));
		lblExposure.setBounds(22, 12, 80, 13);
		add(lblExposure);
		
		btnEnable = new JToggleButton("Enable");
		btnEnable.setFont(new Font("Tahoma", Font.PLAIN, 12));
		btnEnable.setBounds(17, 80, 85, 44);
		add(btnEnable);
		
		btnLock = new JToggleButton("Lock");
		btnLock.setFont(new Font("Tahoma", Font.PLAIN, 12));
		btnLock.setBounds(125, 80, 85, 44);
		add(btnLock);
		
		JTextPane txtpnNm = new JTextPane();
		txtpnNm.setFont(new Font("Tahoma", Font.PLAIN, 12));
		txtpnNm.setText("0.0 nm");
		txtpnNm.setEnabled(false);
		txtpnNm.setEditable(false);
		txtpnNm.setBounds(125, 174, 62, 21);
		add(txtpnNm);
		
		lblStatus = new JLabel("Status");
		lblStatus.setFont(new Font("Tahoma", Font.PLAIN, 12));
		lblStatus.setBounds(22, 204, 165, 13);
		add(lblStatus);
		
		spinner = new JSpinner();
		spinner.setModel(new SpinnerNumberModel(Double.valueOf((Double)0.1), Double.valueOf((Double)0.1), Double.valueOf(10000), Double.valueOf((Double) 0.1)));
		spinner.setFont(new Font("Tahoma", Font.PLAIN, 12));
		spinner.setBounds(125, 10, 85, 24);
		add(spinner);
		
		spinner_1 = new JSpinner();
		spinner_1.setModel(new SpinnerNumberModel(1, 1, 1000, 1));
		spinner_1.setFont(new Font("Tahoma", Font.PLAIN, 12));
		spinner_1.setBounds(125, 46, 85, 24);
		add(spinner_1);
		
		btnCalibration_1 = new JButton("Calibrate");
		btnCalibration_1.setFont(new Font("Tahoma", Font.PLAIN, 12));
		btnCalibration_1.setBounds(17, 134, 193, 30);
		add(btnCalibration_1);
		
		JLabel lblDistance = new JLabel("Distance ");
		lblDistance.setFont(new Font("Tahoma", Font.PLAIN, 12));
		lblDistance.setBounds(22, 182, 80, 13);
		add(lblDistance);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void addComponentListeners() {
	    String propertyAverage = getPanelLabel() + " " + FOCUS_AVERAGE;
	    String propertyExposure = getPanelLabel() + " " + FOCUS_EXPOSURE;

	    SwingUIListeners.addChangeListenerOnNumericalValue(this, propertyExposure, spinner);
	    SwingUIListeners.addChangeListenerOnNumericalValue(this, propertyAverage, spinner_1);

	    // Disable Lock Focus button by default
	    btnLock.setEnabled(false);

	    // Button calibrate
	    btnCalibration_1.addActionListener(e -> {
	        // If Focus Lock is on, turn it off before calibrating
	        if (btnLock.isSelected()) {
	            btnLock.setSelected(false);          // uncheck the toggle
	            focusLocking(false);                 // disable focus lock logic
	            lblStatus.setText("Focuslock disabled for calibration.");
	        }

	        CalibrateTask calibrateTask = new CalibrateTask(systemController_.getStudio());

	        calibrateTask.setOnCalibrationFinished((slope, intercept) -> {
	            lblStatus.setText(String.format("Calibrated: %.4f µm/pixel", slope));
	            slopeCal = slope;

	            // Enable Focus Lock button after calibration
	            btnLock.setEnabled(true);
	        });

	        calibrateTask.startCalibration();
	    });

	    // Monitor position
	    SwingUIListeners.addActionListenerToBooleanAction(b -> monitorPosition(b), btnEnable);

	    // Lock focus
	    SwingUIListeners.addActionListenerToBooleanAction(b -> focusLocking(b), btnLock);
	}


	@Override
	public String getDescription() {
		return "Panel controlling Focus Lock.";
	}

	@Override
	protected void initializeInternalProperties() {

	}

	@Override
	protected void initializeParameters() {
		// TODO Auto-generated method stub
		
	}


	@Override
	protected void initializeProperties() {
		String text1 = "Property changing the average of the light sensor.";
		String text2 = "Property changing the exposure of the light sensor.";
		String propertyAverage = getPanelLabel() + " " + FOCUS_AVERAGE;
		String propertyExposure = getPanelLabel() + " " + FOCUS_EXPOSURE;

		addUIProperty(new RescaledUIProperty(this, propertyAverage, text1, new NoFlag()));
		addUIProperty(new RescaledUIProperty(this, propertyExposure, text2, new NoFlag()));
	}

	@Override
	public void internalpropertyhasChanged(String arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void parameterhasChanged(String arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void propertyhasChanged(String propertyName, String newvalue) {
	    String propertyName1 = getPanelLabel() + " " + FOCUS_AVERAGE;
	    String propertyName2 = getPanelLabel() + " " + FOCUS_EXPOSURE;

	    if (propertyName.equals(propertyName1)) {
	        if (EmuUtils.isNumeric(newvalue)) {
	            average = (int) Double.parseDouble(newvalue);
	            studio.logs().logMessage("Updated average to: " + average);
            	cameraPollingTask.setAverage(average);
	        } else {
	        	studio.logs().logMessage("Invalid numeric value for average: " + newvalue);
	        }
	    } else if (propertyName.equals(propertyName2)) {
	        try {
	            exposure = (float) Double.parseDouble(newvalue);
	            studio.logs().logMessage("Updated exposure to: " + exposure);
	            cameraPollingTask.setExposure(exposure);
	        } catch (NumberFormatException e) {
	        	studio.logs().logMessage("Invalid numeric value for exposure: " + newvalue);
	        }
	    }
	}


	@Override
	public void shutDown() {
		// TODO Auto-generated method stub
		
	}
	
	protected void monitorPosition(boolean enabled) {
	    if (enabled) {
            if (pixelDataListener != null) {
                cameraPollingTask.setOnImageUpdate(pixelDataListener);
            }
	        cameraPollingTask.start();
	        lblStatus.setText("Camera Polling...");
	    } else {
            cameraPollingTask.stop();
	        lblStatus.setText("Camera Stopped");
	    }
	}
	
	protected void focusLocking(boolean enabled) {
		FocusTask focusTask = new FocusTask(systemController_.getStudio());

		if (enabled) {
			double[] result = focusTask.startFocus(slopeCal);
	        lblStatus.setText("Start Focuslock");
	        if (referanceDataListener != null) {
	        	referanceDataListener.accept(result);
	        }
		} else {
			focusTask.stopFocus();
	        lblStatus.setText("Stop Focuslock");
		}
	}
	
	public void setPixelDataListener(Consumer<short[]> listener) {
	    this.pixelDataListener = listener;
	}
	
	public void setReferenceDataListener(Consumer<double[]> listener) {
		this.referanceDataListener = listener;
	}
}