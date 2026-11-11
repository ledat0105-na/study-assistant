package com.example.studyassistant.service;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class IncidentMonitor {
    private final AtomicLong browserErrors = new AtomicLong();
    private final AtomicLong serverErrors = new AtomicLong();

    public void browserError() { browserErrors.incrementAndGet(); }
    public void serverError() { serverErrors.incrementAndGet(); }
    public long browserErrors() { return browserErrors.get(); }
    public long serverErrors() { return serverErrors.get(); }
}
