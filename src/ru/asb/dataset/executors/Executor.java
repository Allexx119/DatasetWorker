package ru.asb.dataset.executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Executor implements Runnable {
    protected int threadsNum = 1;
    protected AtomicInteger completeCommandsCount;
    protected static Logger log = LogManager.getLogger(Executor.class);

    protected Executor() {
        this.completeCommandsCount = new AtomicInteger(0);
    }

    public synchronized void setThreadsNum(int threadsNum) {
        this.threadsNum = threadsNum;
    }

    public AtomicInteger getCompleteCommandsCount() {
        return completeCommandsCount;
    }

    ExecutorService initExecutor() {
        return Executors.newFixedThreadPool(threadsNum, new ThreadFactory() {
            int threadNumber = 1;

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("ExecutorThread-%d", threadNumber++));
            }
        });
    }
}
