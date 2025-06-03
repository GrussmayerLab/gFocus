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
            (xData[n - 1] - xData[0]) / 4.0  // Sigma
        };

        MultivariateJacobianFunction model = (point) -> {
            double A = point.getEntry(0);
            double mu = point.getEntry(1);
            double sigma = point.getEntry(2);

            double[] values = new double[n];
            double[][] jacobian = new double[n][3];

            for (int i = 0; i < n; i++) {
                double x = xData[i];
                double dx = x - mu;
                double sigma2 = sigma * sigma;
                double expTerm = Math.exp(-dx * dx / (2 * sigma2));
                values[i] = A * expTerm;

                // Derivatives
                jacobian[i][0] = expTerm;                         // ∂f/∂A
                jacobian[i][1] = A * expTerm * dx / sigma2;       // ∂f/∂mu
                jacobian[i][2] = A * expTerm * dx * dx / (sigma2 * sigma); // ∂f/∂sigma
            }

            return new Pair<>(
                new org.apache.commons.math3.linear.ArrayRealVector(values),
                new org.apache.commons.math3.linear.Array2DRowRealMatrix(jacobian)
            );
        };

        // Convert yData (short[]) to double[] for target
        double[] target = new double[n];
        for (int i = 0; i < n; i++) {
            target[i] = yData[i];
        }

        // Weight: uniform (can skip or use identity)
        double[] weights = new double[n];
        for (int i = 0; i < n; i++) {
            weights[i] = 1.0;
        }

        LeastSquaresProblem problem = new LeastSquaresBuilder()
                .start(initialGuess)
                .model(model)
                .target(target)
                .weight(new DiagonalMatrix(weights))
                .lazyEvaluation(false)
                .maxEvaluations(1000)
                .maxIterations(1000)
                .build();

        LeastSquaresOptimizer optimizer = new LevenbergMarquardtOptimizer();
        LeastSquaresOptimizer.Optimum optimum = optimizer.optimize(problem);

        return optimum.getPoint().toArray(); // [Amplitude, Mean, Sigma]
    }


    private double getMax(double[] arr) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : arr) {
            if (v > max) max = v;
        }
        return max;
    }
}
