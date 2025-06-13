package com.myname.focuslock;

import java.awt.EventQueue;
import java.util.HashMap;
import java.util.Random;
import java.util.TreeMap;

import javax.swing.JFrame;

import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.ui.ConfigurableMainFrame;
import de.embl.rieslab.emu.utils.settings.Setting;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import java.awt.GridLayout;

public class MyFrame extends ConfigurableMainFrame {

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MyFrame frame = new MyFrame();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	private org.micromanager.Studio studio;

	/**
	 * Create the frame.
	 */
	public MyFrame() {
		super("",null,null); // calls superconstructor
		setBounds(100, 100, 650, 500);
	}

	public MyFrame(String arg0, SystemController arg1, TreeMap<String, String> arg2) {
		super(arg0, arg1, arg2);
	}

	@Override
	public HashMap<String, Setting> getDefaultPluginSettings() {
		HashMap<String, Setting>  settgs = new HashMap<String, Setting>();
		return settgs;
	}

	@Override
	protected String getPluginInfo() {
		return "Description of the plugin and mention of the author.";
	}

	@Override
	protected void initComponents() {
		System.out.println("Studio in MyFrame.initComponents(): " + studio);

		setBounds(100, 100, 733, 618);
		getContentPane().setLayout(null);
		
		JPanel panel = new JPanel();
		panel.setBounds(10, 10, 616, 421);
		getContentPane().add(panel);
		panel.setLayout(new GridLayout(1, 0, 0, 0));
		
		LockPanel lockPanel = new LockPanel("settings", this.getController());
		panel.add(lockPanel);
		
        int length = 128;
        double amplitude = 1000;
        double mean = 64;    // center of the array
        double sigma = 15;
		
//        int[] gaussianData = generateGaussianData(length, amplitude, mean, sigma);
        int[] gaussianData = new int[128];

		
		GraphPanel graphPanel = new GraphPanel(gaussianData, 0, 0);
		graphPanel.setBounds(220, 10, 396, 411);
		lockPanel.add(graphPanel);
		
		// Connect callback
		lockPanel.setPixelDataListener(data -> {
		    // Optionally convert short[] to int[]
		    int[] intData = new int[data.length];
		    for (int i = 0; i < data.length; i++) {
		        intData[i] = data[i];
		    }

		    // Update UI on EDT
		    javax.swing.SwingUtilities.invokeLater(() -> {
		    	graphPanel.updateGraph(intData);
		    	graphPanel.repaint();           // force redraw if needed
		    });
		});
		
		lockPanel.setReferenceDataListener(data -> {
			graphPanel.updateReferenceGraph(data[0], data[1]);
			graphPanel.repaint();
		});
	}
	
	private static int[] generateGaussianData(int length, double amplitude, double mean, double sigma) {
	    int[] data = new int[length];
	    Random rand = new Random();

	    double noiseStdDev = amplitude * 0.05; // Adjust this factor to control noise level

	    for (int i = 0; i < length; i++) {
	        double x = i;
	        double value = amplitude * Math.exp(-Math.pow(x - mean, 2) / (2 * sigma * sigma));
	        double noise = rand.nextGaussian() * noiseStdDev; // Gaussian noise
	        data[i] = (int) Math.max(0, Math.round(value + noise)); // Clamp to 0 to avoid negatives
	    }
	    return data;
	}
}
