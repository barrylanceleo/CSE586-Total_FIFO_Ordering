package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;

public class Sender {

    static final String TAG = SenderTask.class.getSimpleName();
    Activity parentActivity;

    Sender(Activity parentActivity)
    {
        this.parentActivity = parentActivity;
    }


    public void broadcastMessage(String message, List<Integer> destinationPorts)
    {
        for(Integer port : destinationPorts) {

            // create a sender task to send the message
            new SenderTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, port);
        }
    }

    public void sendMessage(String message, Integer destinationPort)
    {
        // create a sender task to send the message
        new SenderTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, destinationPort);
    }

    private class SenderTask extends AsyncTask<Object, Void, Void> {

        final String TAG = SenderTask.class.getSimpleName();

        @Override
        protected Void doInBackground(Object... params) {

            String msgToSend = (String)params[0];
            Integer destinationPort = (Integer)params[1];
            try
            {
                //Log.v(TAG, "Sending message to " + destinationPort + " : " + msgToSend);
                Socket socket = new Socket();
                socket.setSoTimeout(parentActivity.getResources().getInteger(R.integer.timeout));
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        destinationPort), parentActivity.getResources().getInteger(R.integer.timeout));

                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeUTF(msgToSend);
                //Log.v(TAG, "Message Sent to " + destinationPort + " : " + msgToSend);
                socket.close();
            } catch(SocketTimeoutException e)
            {
                Log.e(TAG, "SenderTask timed out");
            }
            catch (UnknownHostException e) {
                Log.e(TAG, "SenderTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "SenderTask socket IOException");
            }
            return null;
        }
    }

}
