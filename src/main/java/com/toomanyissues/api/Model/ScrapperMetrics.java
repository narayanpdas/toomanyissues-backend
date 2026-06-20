package com.toomanyissues.api.Model;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Getter @Setter
public class ScrapperMetrics {
    private final String id;
    private final String name;
    private final String description;

    private final AtomicReference<String> status = new AtomicReference<>("PAUSED"); // RUNNING or IDLE or PAUSED
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicInteger pointsCostInCurrentCycle = new AtomicInteger(0);
    private final AtomicInteger totalPointsCost = new AtomicInteger(0);

    public ScrapperMetrics(String id, String name, String description,String status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status.getAndSet(status);
    }
}
