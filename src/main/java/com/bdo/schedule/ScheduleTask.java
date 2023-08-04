package com.bdo.schedule;

import com.bdo.Application;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class ScheduleTask extends TimerTask {
    private final Timer timer;

    public ScheduleTask(Timer timer) {
        this.timer = timer;
    }

    @Override
    public void run() {
        try {
            if (!Application.execute("2")) timer.cancel();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}