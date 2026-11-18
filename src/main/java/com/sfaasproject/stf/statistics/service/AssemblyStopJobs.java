package com.sfaasproject.stf.statistics.service;

import org.springframework.scheduling.annotation.Scheduled;

/** Reconciles external stock updates and safely resumes after application restarts. */
public class AssemblyStopJobs {
    private final AssemblyStopRecorder recorder;

    public AssemblyStopJobs(AssemblyStopRecorder recorder) {
        this.recorder = recorder;
    }

    @Scheduled(fixedDelayString = "${stf.backend-b.assembly-stop-delay:1000}")
    public void refresh() {
        recorder.refresh();
    }
}
