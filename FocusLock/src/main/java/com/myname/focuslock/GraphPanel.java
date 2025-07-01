package com.myname.focuslock;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import javax.swing.JFrame;
import java.awt.Color;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import com.myname.focuslock.GaussianFitter;

import de.embl.rieslab.emu.ui.ConfigurablePanel;

public class GraphPanel extends ConfigurablePanel {

    private XYSeries rawSeries;
    private XYSeries fittedSeries;
    private XYSeries referenceSeries;

    public GraphPanel(int[] intensityValues, double[] referenceValues) {
        super("GraphPanel");

        setLayout(new BorderLayout());
        setBackground(Color.DARK_GRAY);  // Panel background

        rawSeries = new XYSeries("Raw Data");
        fittedSeries = new XYSeries("Fitted Gaussian");
        referenceSeries = new XYSeries("Reference Gaussian");

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(rawSeries);
        dataset.addSeries(fittedSeries);
        dataset.addSeries(referenceSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
            "",
            "Pixel",
            "Intensity",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );

        XYPlot plot = chart.getXYPlot();

        // Dark mode styling
        chart.setBackgroundPaint(Color.DARK_GRAY);                       // Outside chart area
        plot.setBackgroundPaint(new Color(30, 30, 30));                  // Inside plot area
        plot.setDomainGridlinePaint(Color.GRAY);
        plot.setRangeGridlinePaint(Color.GRAY);

        // Axis styling
        NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setRange(0.0, 4095.0);
        xAxis.setLabelPaint(Color.WHITE);
        yAxis.setLabelPaint(Color.WHITE);
        xAxis.setTickLabelPaint(Color.WHITE);
        yAxis.setTickLabelPaint(Color.WHITE);

        // Renderer styling
//        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
//        renderer.setSeriesPaint(0, Color.CYAN);      // Raw Data
//        renderer.setSeriesPaint(1, Color.GREEN);     // Fitted Gaussian
//        renderer.setSeriesPaint(2, Color.ORANGE);    // Reference Gaussian
//        renderer.setSeriesStroke(0, new BasicStroke(0.1f));
//        renderer.setSeriesStroke(1, new BasicStroke(0.1f));
//        renderer.setSeriesStroke(2, new BasicStroke(0.1f));
//        plot.setRenderer(renderer);

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setBackground(Color.DARK_GRAY);
        add(chartPanel, BorderLayout.CENTER);

        // Initial population
        updateGraph(intensityValues);
        updateReferenceGraph(referenceValues);
    }

    /**
     * Update the chart with new intensity data and Gaussian fit.
     */
    public void updateGraph(int[] intensityValues) {
        rawSeries.clear();
        fittedSeries.clear();

        for (int i = 0; i < intensityValues.length; i++) {
            rawSeries.add(i + 1, intensityValues[i]);
        }
        
        short[] shortValues = new short[intensityValues.length];
        for (int i = 0; i < intensityValues.length; i++) {
            shortValues[i] = (short) intensityValues[i];  // explicit cast, beware of overflow!
        }

        double a = 0;
        double mu = 0;
        double sigma = 0;

        try {
            double[] params = new GaussianFitter(shortValues).fit();
            a = params[0];
            mu = params[1];
            sigma = params[2];
        } catch (Exception e) {
        	System.out.println("Gaussian fitting failed: " + e.getMessage());
            // You could log the stack trace if needed
            e.printStackTrace();
        }

        for (int x = 1; x <= 128; x++) {
            double y = a * Math.exp(-Math.pow(x - mu, 2) / (2 * sigma * sigma));
            fittedSeries.add(x, y);
        }
    }
    
    public void updateReferenceGraph(double [] params) {
        referenceSeries.clear();

        double a = params[0];
        double mu = params[1];
        double sigma = params[2];

        for (int x = 1; x <= 128; x++) {
            double y = a * Math.exp(-Math.pow(x - mu, 2) / (2 * sigma * sigma));
            referenceSeries.add(x, y);
        }
    }


    // EMU-required overrides
    @Override protected void addComponentListeners() {}
    @Override public String getDescription() {
        return "Displays a graph of 12-bit intensity values and a fitted Gaussian.";
    }
    @Override protected void initializeInternalProperties() {}
    @Override protected void initializeParameters() {}
    @Override protected void initializeProperties() {}
    @Override public void internalpropertyhasChanged(String property) {}
    @Override protected void parameterhasChanged(String parameter) {}
    @Override protected void propertyhasChanged(String category, String property) {}
    @Override public void shutDown() {}

    // For testing
    public static void main(String[] args) {
        int[] values = new int[128];
        for (int i = 0; i < 128; i++) {
            values[i] = (int)(Math.random() * 4096);
        }
        double[] reference = new double[3];
        JFrame frame = new JFrame("Graph Panel Test");
        GraphPanel panel = new GraphPanel(values, reference);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        frame.setVisible(true);

        // Simulate an update after 3 seconds
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                int[] newValues = new int[128];
                for (int i = 0; i < 128; i++) {
                    newValues[i] = (int)(2048 + 1024 * Math.sin(i / 10.0));  // Simulated data
                }
                panel.updateGraph(newValues);
            } catch (InterruptedException ignored) {}
        }).start();
    }
}
