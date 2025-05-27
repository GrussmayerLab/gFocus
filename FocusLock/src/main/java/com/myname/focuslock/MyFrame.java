package com.myname.focuslock;

import java.awt.EventQueue;
import java.util.HashMap;
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
		
		GraphPanel graphPanel = new GraphPanel(new int[128]);
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
	}
}
