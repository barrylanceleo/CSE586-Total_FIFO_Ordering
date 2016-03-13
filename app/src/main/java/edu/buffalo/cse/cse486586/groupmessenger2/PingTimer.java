package edu.buffalo.cse.cse486586.groupmessenger2;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by barry on 2/27/16.
 */
public class PingTimer {


    static final String TAG = PingTimer.class.getSimpleName();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    PingTimerListener pingTimerListener;

    PingTimer(PingTimerListener listener)
    {
        this.pingTimerListener = listener;
    }

    void startPinger(long time, int processIdToPing)
    {
        final int processId = processIdToPing;
        final Runnable pinger = new Runnable() {
            public void run() {
                pingTimerListener.onTimeToPing(processId);
            }
        };
        scheduler.scheduleAtFixedRate(pinger, time, time, TimeUnit.MILLISECONDS);
    }

    void stopPinger() {
        scheduler.shutdown();
    }

}
