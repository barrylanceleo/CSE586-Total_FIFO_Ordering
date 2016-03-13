package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

public class Sender {

    static final String TAG = SenderTask.class.getSimpleName();
    private static final int SOCKET_TIMEOUT = 5000;
    Activity parentActivity;
    FailureListener failureListener;

    Sender(Activity parentActivity, FailureListener failureListener)
    {
        this.parentActivity = parentActivity;
        this.failureListener = failureListener;
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
                socket.setSoTimeout(SOCKET_TIMEOUT);
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        destinationPort), SOCKET_TIMEOUT);
                if(!socket.isConnected())
                {
                    Log.v(TAG, "Couldn't connect. Failure");
                }
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeUTF(msgToSend);
//                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
//                bw.write(msgToSend);
//                bw.newLine();
                //Log.v(TAG, "Message Sent to " + destinationPort + " : " + msgToSend);
                socket.close();
            } catch(Exception e)
            {
                Log.e(TAG, "SenderTask Exception");
                //failureListener.onFailure(destinationPort);
            }
//            catch (UnknownHostException e) {
//                Log.e(TAG, "SenderTask UnknownHostException");
//            } catch (IOException e) {
//                Log.e(TAG, "SenderTask socket IOException");
//            }
            return null;
        }
    }

}
