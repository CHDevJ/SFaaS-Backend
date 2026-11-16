package com.sfaasproject.stf.measurement.service;

public interface IngestionMessageGuard {
    enum Acquisition { ACQUIRED, COMPLETED, IN_PROGRESS }

    Acquisition acquire(String messageId);
    void completed(String messageId);
    void release(String messageId);
}
