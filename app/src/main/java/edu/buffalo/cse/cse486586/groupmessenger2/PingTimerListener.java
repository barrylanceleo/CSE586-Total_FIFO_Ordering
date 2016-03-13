package edu.buffalo.cse.cse486586.groupmessenger2;

/**
 * Created by barry on 2/27/16.
 */
public interface PingTimerListener {

    void onTimeToPing(int processIdToPing);
    void onTimeToCheckAck(int process);
}
