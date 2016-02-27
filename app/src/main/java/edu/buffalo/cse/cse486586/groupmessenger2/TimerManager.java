package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by barry on 2/27/16.
 */
public class TimerManager {


    static final String TAG = TimerManager.class.getSimpleName();
    Activity parentActivity;
    TimerExpiredEventListener timerExpiredEventListener;
    List<Timer> timerList;

    TimerManager(Activity parentActivity, TimerExpiredEventListener listener)
    {
        this.parentActivity = parentActivity;
        this.timerExpiredEventListener = listener;
        timerList = new LinkedList<Timer>();

    }

    void startTimer(long time, int senderMessageId)
    {
        new Timer().schedule(new WaitTask(senderMessageId), time);
    }


    class WaitTask extends TimerTask {

        private int senderMessageId;

        WaitTask(int senderMessageId)
        {
            this.senderMessageId = senderMessageId;
        }

        public void run() {
            timerExpiredEventListener.onTimerExpired(senderMessageId);
            //timer.cancel(); //Terminate the timer thread
        }
    }


}
