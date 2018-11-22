package com.utils;

/**
 * 任务
 */
public class TimerTask {

    /**
     * 延迟时间
     */
    private long delayMs;

    /**
     * 任务
     */
    private Runnable task;

    protected TimerTaskList timerTaskList;

    protected TimerTask next;

    protected TimerTask pre;

    public String desc;

    public TimerTask(long delayMs, Runnable task) {
        this.delayMs = delayMs;
        this.task = task;
        this.timerTaskList = null;
        this.next = null;
        this.pre = null;
    }

    public Runnable getTask() {
        return task;
    }

    public long getDelayMs() {
        return delayMs;
    }

    @Override
    public String toString() {
        return desc;
    }
}