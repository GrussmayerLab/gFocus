package com.myname.focuslock;

import org.apache.commons.math3.fitting.leastsquares.*;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.util.Pair;

public class GaussianFitter {

    private double[] xData;
    private double[] yData;

    // Constructor assumes yData only, xData is indices converted to double
    public GaussianFitter(short[] yData) {
        this.yData = new double[yData.length];
        this.xData = new double[yData.length];
        for (int i = 0; i < yData.length; i++) {
            this.yData[i] = yData[i];
            this.xData[i] = i;  // Use index as x-value
        }
    }

    // Constructor with explicit xData and yData
    public GaussianFitter(short[] xData, short[] yData) {
        if (xData.length != yData.length) {
            throw new IllegalArgumentException("x and y arrays must be the same length");
        }
        this.xData = new double[xData.length];
        this.yData = new double[yData.length];
        for (int i = 0; i < xData.length; i++) {
            this.xData[i] = xData[i];
            this.yData[i] = yData[i];
        }
    }

    public double[] fit() {
        int n = yData.length;

        // Initial guess: amplitude, mean, sigma
        double[] initialGuess = {
                getMax(yData),                   // Amplitude
                xData[n / 2],                    // Mean
                (xData[n - 1] - xData[0]) / 4.0 // Sigma
        };

        MultivariateJacobianFunction model = (point) -> {
            double A = point.getEntry(0);
            double mu = point.getEntry(1);
            double sigma = point.getEntry(2);

            double[] values = new double[n];
            for (int i = 0; i < n; i++) {
                double dx = xData[i] - mu;
                values[i] = A * Math.exp(-dx * dx / (2 * sigma * sigma));
            }
            // No Jacobian matrix provided, numerical differentiation is used
            return new Pair<>(new org.apache.commons.math3.linear.ArrayRealVector(values), null);
        };

        LeastSquaresProblem problem = new LeastSquaresBuilder()
                .start(initialGuess)
                .model(model)
                .target(yData)
                .weight(new DiagonalMatrix(new double[n])) // uniform weights
                .lazyEvaluation(false)
                .maxEvaluations(1000)
                .maxIterations(1000)
                .build();

        LeastSquaresOptimizer optimizer = new LevenbergMarquardtOptimizer();
        LeastSquaresOptimizer.Optimum optimum = optimizer.optimize(problem);

        return optimum.getPoint().toArray();  // amplitude, mean, sigma as doubles
    }

    private double getMax(double[] arr) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : arr) {
            if (v > max) max = v;
        }
        return max;
    }
}
