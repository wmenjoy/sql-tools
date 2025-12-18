package com.footstone.audit.service.consumer;

import org.springframework.core.task.SimpleAsyncTaskExecutor;

public class VirtualThreadTaskExecutor extends SimpleAsyncTaskExecutor {
    public VirtualThreadTaskExecutor(String threadNamePrefix) {
        super(threadNamePrefix);
        setVirtualThreads(true);
    }
}
