package com.bdo.schedule;

import com.bdo.Application;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class ScheduleTask extends TimerTask {
    private final Timer timer;

    private final String mode;

    public ScheduleTask(Timer timer, String mode) {
        this.timer = timer;
        this.mode = mode;
    }

    @Override
    public void run() {
        try {
            if (!Application.execute(mode)) timer.cancel();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}