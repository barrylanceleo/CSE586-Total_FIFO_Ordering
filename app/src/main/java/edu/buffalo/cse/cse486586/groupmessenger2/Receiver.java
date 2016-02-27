package edu.buffalo.cse.cse486586.groupmessenger2;

import android.os.AsyncTask;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Receiver {

    MessageReceivedEventListener messageReceivedListener;

    static final String TAG = Receiver.class.getSimpleName();
    ServerSocket listenerSocket;
    int listenerPort;

    Receiver(int listenerPort, MessageReceivedEventListener messageReceivedListener)
    {
        this.listenerPort = listenerPort;

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
            try
            {
                //noinspection InfiniteLoopStatement
                while(true)
                {
                    Socket server = serverSocket.accept();
                    Log.v(TAG, "Connection Accepted!");
                    DataInputStream in = new DataInputStream(server.getInputStream());
                    String mesRecvd = in.readUTF().trim();
                    //Log.v(TAG, "Message Received: " +mesRecvd);
                    publishProgress(mesRecvd);
                    server.close();
                    Log.v(TAG, "Socket  closed");
                }
            }
            catch(SocketTimeoutException s)
            {
                Log.e(TAG, "Socket timed out!", s);
            }catch(IOException e)
            {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            return null;
        }

        protected void onProgressUpdate(String...strings) {

            // inform listener about the message
            messageReceivedListener.onMessageReceived(strings[0]);

        }
    }



}
