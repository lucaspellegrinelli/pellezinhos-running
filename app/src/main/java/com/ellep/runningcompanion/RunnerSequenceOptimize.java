package com.ellep.runningcompanion;

public interface RunnerSequenceOptimize {
    boolean isReportValid(RunnerLocationReport report);
    double getReportWeight(RunnerLocationReport report);
}
