package org.spreadme.retry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;

public abstract class HashWheelRetryTask<T> implements TimerTask{
 
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final long TICKDURATION = 100;
    private static final TimeUnit TICKDURATION_TIME_UNIT = TimeUnit.MILLISECONDS;
    private static final HashedWheelTimer HASHED_WHEEL_TIMER = new HashedWheelTimer(
        new NamedThreadFactory("retry-task"),
        TICKDURATION, 
        TICKDURATION_TIME_UNIT);

    private AtomicReference<T> result = new AtomicReference<>();
    private int maxTimes = 3;
    private long delay = 150;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    private AtomicInteger currentTime = new AtomicInteger(0);
    private AtomicBoolean finished = new AtomicBoolean(false);
    private Object lock = new Object();

    private final String name;

    protected HashWheelRetryTask(String name) {
        this.name = name;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        try {
            if(currentTime.get() < maxTimes) {
                currentTime.getAndAdd(1);
                logger.info("the task[{}] retry {} times", this.name, currentTime.get());
                result.compareAndSet(null, retry());
                finished.compareAndSet(false, true);
                synchronized(lock) {
                    lock.notifyAll();
                }
            } else {
                finished.compareAndSet(false, true);
                synchronized(lock) {
                    lock.notifyAll();
                }
            }
        } catch (Exception e) {
            logger.warn("the task[{}] retry error: ", this.name, e);
            this.doExecute();
        }
    }

    protected abstract T retry() throws Exception;

    public T execute() throws InterruptedException{
        if(timeUnit.toMillis(delay) <= TICKDURATION_TIME_UNIT.toMillis(TICKDURATION)) {
            throw new IllegalArgumentException("the delay time must greater than the default tick duration");
        }
        if(maxTimes < 1) {
            throw new IllegalArgumentException("the max retry times must greater than 1");
        }
        // 首先进行一次retry
        try {
            return retry();
        } catch (Exception e) {
            logger.warn("the task[{}] happens exception, will retry", this.name, e);
            this.doExecute();
        }

        synchronized (lock) {
            while (!finished.get()) {
                lock.wait();
            }
        }
        return result.get();
    }

    private void doExecute() {
        //long delay = timeUnit.toMillis(this.delay) - TICKDURATION_TIME_UNIT.toMillis(TICKDURATION);
        HASHED_WHEEL_TIMER.newTimeout(this, delay, timeUnit);
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public void setMaxTimes(int maxTimes) {
        this.maxTimes = maxTimes;
    }
}