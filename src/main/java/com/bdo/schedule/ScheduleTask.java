package com.bdo.schedule;

import com.bdo.Application;

import java.util.Timer;
import java.util.TimerTask;

public class ScheduleTask extends TimerTask {
    private Timer timer;

    public ScheduleTask(Timer timer) {
        this.timer = timer;
    }

    @Override
    public void run() {
        if (!Application.execute()) timer.cancel();
    }

}