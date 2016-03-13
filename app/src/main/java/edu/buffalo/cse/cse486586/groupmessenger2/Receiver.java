package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Receiver {

    MessageReceivedEventListener messageReceivedListener;

    static final String TAG = Receiver.class.getSimpleName();
    ServerSocket listenerSocket;
    Activity parentActivity;
    int listenerPort;

    Receiver(Activity parentActivity, int listenerPort, MessageReceivedEventListener messageReceivedListener)
    {
        this.listenerPort = listenerPort;
        this.parentActivity = parentActivity;

        // make the coordinator listen to message received events
        setOnMessageReceivedListener(messageReceivedListener);

        // start listening on a background thread
        try {
            listenerSocket = new ServerSocket(listenerPort);
            new ReceiverTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, listenerSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }
    }

    void setOnMessageReceivedListener(MessageReceivedEventListener listener)
    {
        messageReceivedListener = listener;
    }

    private class ReceiverTask extends AsyncTask<ServerSocket, String, Void>  {

        final String TAG = ReceiverTask.class.getSimpleName();

        @Override
        protected Void doInBackground(ServerSocket... params) {
            ServerSocket serverSocket = params[0];
            BufferedReader br;
            //noinspection InfiniteLoopStatement
            while(true) {
                try {
                    Socket server = serverSocket.accept();
                    server.setSoTimeout(parentActivity.getResources().getInteger(R.integer.timeout));
                    Log.v(TAG, "Connection Accepted!");
//                    br = new BufferedReader(new InputStreamReader(server.getInputStream()));
//                    String mesRecvd = br.readLine();
                    DataInputStream in = new DataInputStream(server.getInputStream());
                    String mesRecvd = in.readUTF();
                    Log.v(TAG, "Message Received: " + mesRecvd);
                    server.close();
                    Log.v(TAG, "Socket  closed");
                    if(mesRecvd == null)
                    {
                        // connection establishment
                        continue;
                    }

                    messageReceivedListener.onMessageReceived(mesRecvd);
                } catch (SocketTimeoutException s) {
                    Log.e(TAG, "Receiver timed out!");
                } catch (IOException e) {
                    Log.e(TAG, Log.getStackTraceString(e));
                }
            }
        }
    }
}
