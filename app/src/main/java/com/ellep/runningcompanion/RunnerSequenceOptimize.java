package com.ellep.runningcompanion;

public interface RunnerSequenceOptimize {
    boolean isReportValid(RunnerLocationReport report);
    boolean isReportBetter(RunnerLocationReport best, RunnerLocationReport current);
}
