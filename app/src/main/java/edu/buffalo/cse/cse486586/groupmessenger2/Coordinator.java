package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.JsonWriter;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Coordinator implements MessageReceivedEventListener, FailureListener, PingTimerListener {

    static final String TAG = Coordinator.class.getSimpleName();
    static final int[] DESTINATION_PORTS = {11108, 11112, 11116, 11120, 11124};
    static final int LISTENER_PORT = 10000;
    static final long TIMEOUT = 2500;
    static final  int PINGERSTOPPER_COUNT = 10;
    int MY_PORT;
    private Activity parentActivity;
    private TextView displayView;

    private static Coordinator instance = null;
    private Sender mSender;
    private Receiver mReceiver;
    private Integer lastReceivedMessageId = -1;
    private Integer lastSentMessageId = -1;
    private Integer lastProposedSeqNum = -1;
    private Integer lastAgreedSeqNum = -1;
    private Integer finalContinuousSeqNum = 0;

    private ConcurrentHashMap<Integer, List<Message>> broadcastedMessageList;
    private PriorityQueue<Message> messageQueue;
    private List<Integer> currentDestinationPorts;
    private HashMap<Integer, PingTracker> pingerMap;


    private class PingTracker
    {
        PingTimer pinger;
        boolean receivedACK;
        int numAcksWithoutDataTransfer;

        PingTracker(PingTimer pinger, boolean receivedACK, int numAcksWithoutDataTransfer)
        {
            this.pinger = pinger;
            this.receivedACK = receivedACK;
            this.numAcksWithoutDataTransfer = numAcksWithoutDataTransfer;
        }

    }

    private Coordinator(Activity parentActivity) {
        this.parentActivity = parentActivity;

        // initialize my sending port
        TelephonyManager tel = (TelephonyManager) parentActivity.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        MY_PORT = (Integer.parseInt(portStr) * 2);

        // set up the display view
        displayView = (TextView) parentActivity.findViewById(R.id.textView1);
        displayView.setMovementMethod(new ScrollingMovementMethod());

        // initialize a sender that can be used to send messages later
        mSender = new Sender(parentActivity, this);
        this.currentDestinationPorts = new LinkedList<Integer>();
        for (int port : DESTINATION_PORTS)
            this.currentDestinationPorts.add(port);

        //create a receiver to keep listening to incoming messages
        mReceiver = new Receiver(LISTENER_PORT, this);

        // create the message queue
        messageQueue = new PriorityQueue<Message>();

        // create a list to keep track of the broadcasted messages and their proposal
        broadcastedMessageList = new ConcurrentHashMap<Integer, List<Message>>();

        // create a pingerList
        pingerMap = new HashMap<Integer, PingTracker>();

        // create pingers for the destinations
        for(Integer processId: currentDestinationPorts)
        {
            if (processId != MY_PORT) {
                PingTimer pinger = new PingTimer(this);
                pinger.startPinger(TIMEOUT, processId);
                PingTracker pingTracker = new PingTracker(pinger, true, 0);
                pingerMap.put(processId, pingTracker);

                Log.v(TAG, "Pinger started for Sender Id " + processId +
                        " Start Time: " + new Date());
            }
        }
    }

    synchronized static Coordinator getInstance(Activity parentActivity) {
        if (instance == null) {
            instance = new Coordinator(parentActivity);
        }
        return instance;
    }

    @Override
    synchronized public void onMessageReceived(String message) {


        Message receivedMessage;
        try {
            receivedMessage = buildMessageFromReceivedJson(message);

        } catch (JSONException e) {
            Log.e(TAG, "Improper Message received. Ignoring...");
            return;
        }

        // print queue
        if(!(receivedMessage.getType() == Message.TYPE.PING || receivedMessage.getType() == Message.TYPE.ACK))
        {
//            StringBuilder sb = new StringBuilder();
//            sb.append("Message Queue on receiving message:\n");
//            for (Message m : messageQueue)
//                sb.append(m.toString() + "\n");
//            Log.v(TAG, sb.toString());

            if(receivedMessage.getSenderPid() != MY_PORT)
            {
                PingTracker pingTracker = pingerMap.get(receivedMessage.getSenderPid());
                if(pingTracker != null){
                    pingTracker.numAcksWithoutDataTransfer = 0;
                    pingTracker.receivedACK = true;
                }
            }
        }

        // handle the message based on the message type
        switch (receivedMessage.getType()) {
            case DATA:
                // Log the message
                Log.v(TAG, "New Message Received: " + receivedMessage.toString());

                // propose a global sequence number
                int newProposedSeqNum = Math.max(lastProposedSeqNum, lastAgreedSeqNum) + 1;
                receivedMessage.setGlobalSeqNum(newProposedSeqNum);

                if (newProposedSeqNum <= lastProposedSeqNum)
                    Log.e(TAG, "ProposedSeqNum did not increase!!! lastProposedSeqNum: "
                            + lastProposedSeqNum + "newProposedSeqNum: " + newProposedSeqNum);

                lastProposedSeqNum = newProposedSeqNum;

                // add process id, here the port number would server as the process id
                // as the port number is unique
                receivedMessage.setProposerPid(MY_PORT);

                // add Message to the priority queue
                messageQueue.add(receivedMessage);

                // send proposal message with proposed sequence number
                receivedMessage.setType(Message.TYPE.PROPOSAL);
                sendMessage(receivedMessage, receivedMessage.getSenderPid());
                Log.v(TAG, "Proposal sent: " + receivedMessage.toString());

                break;
            case PROPOSAL:

                // new proposal received, add to proposal list for that message
                List<Message> messageProposals = broadcastedMessageList.get(receivedMessage.getSenderMessageId());
                if (messageProposals == null) {
                    messageProposals = new LinkedList<Message>();
                }
                messageProposals.add(receivedMessage);
                broadcastedMessageList.put(receivedMessage.getSenderMessageId(), messageProposals);

                // Log the message
                Log.v(TAG, "Proposal Received: " + receivedMessage.toString() +
                        "\nNumber of proposals for this message: " + messageProposals.size());

                // check if we have received proposal from all nodes
                // if yes, send the agreed sequence number
                sendAgreementIfAppropriate(messageProposals, receivedMessage.getSenderMessageId());
                break;

            case AGREEMENT:
                // Log the message
                Log.v(TAG, "Agreement Received: " + receivedMessage.toString());

                // new agreement received
                receivedMessage.setIsDeliverable(true);
                if (!messageQueue.remove(receivedMessage))
                    Log.e(TAG, "Unable to find message in priority queue, this message would be duplicated." + receivedMessage.toString());

                messageQueue.add(receivedMessage);

                // deliver the messages on top of the queue which are deliverable
                deliverMessagesifAppropriate();

                break;

            case PING:
                Log.v(TAG + "Pinger", "PING Received from process: " + receivedMessage.getSenderPid()
                        + " in " + MY_PORT);
                // send ack
                Message pingMessage = buildMessageObject(Message.TYPE.ACK, "");
                sendMessage(pingMessage, receivedMessage.getSenderPid());
                Log.v(TAG + "Pinger", "ACK sent to process Id " + receivedMessage.getSenderPid() + " from " + MY_PORT);
                break;

            case ACK:
                Log.v(TAG + "Pinger", "ACK Received from process: " + receivedMessage.getSenderPid()
                        + " in " + MY_PORT);
                //check if previous ACK was received
                PingTracker pingTracker = pingerMap.get(receivedMessage.getSenderPid());
                if(pingTracker != null){
                    pingTracker.receivedACK = true;
                }
                else{
                    Log.e(TAG + "Pinger", "Unable to find the pinger on receiving ACK." + receivedMessage.getSenderPid());
                }
                break;

            default:
                Log.e(TAG, "Improper Message Type. Ignoring." + receivedMessage.getSenderPid());
        }

    }

    synchronized void deliverMessagesifAppropriate() {

        // deliver the messages that are on the top of the queue
        while ((messageQueue.peek() != null) && messageQueue.peek().isDeliverable()) {
            //remove the message from the priority queue
            Message topMessage = messageQueue.poll();

            final String messageText = topMessage.getMessageText();
            final int finalSequenceNum = finalContinuousSeqNum;
            parentActivity.runOnUiThread(new Runnable() {
                public void run() {
                    //display the message
                    displayView.append(finalSequenceNum + ". "
                            + messageText + "\n");
                }
            });

            //store the message in the content provider
            ContentResolver mContentResolver = parentActivity.getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put("key", Integer.toString(finalContinuousSeqNum));
            cv.put("value", topMessage.getMessageText());
            mContentResolver.insert(GroupMessengerProvider.CPUri, cv);
            Log.v(TAG, "Delivered and stored message in Content Provider: " + topMessage.toString());
            Cursor testCursor = mContentResolver.query(GroupMessengerProvider.CPUri, null, String.valueOf(finalContinuousSeqNum), null, null);
            if (testCursor != null) {
                testCursor.moveToFirst();
                String returnKey = testCursor.getString(testCursor.getColumnIndex("key"));
                String returnValue = testCursor.getString(testCursor.getColumnIndex("value"));
                Log.v(TAG, "From Database:\nKEY: " + returnKey + "\nVALUE: " + returnValue);
                testCursor.close();
            }
            ++finalContinuousSeqNum;
        }

//        // print queue
//        StringBuilder sb = new StringBuilder();
//        sb.append("Message Queue after delivering messages:\n");
//        for (Message m : messageQueue){
//            sb.append(m.toString());
//            sb.append( "\n");
//        }
//        Log.v(TAG, sb.toString());

    }


    synchronized void sendAgreementIfAppropriate(List<Message> messageProposals, int senderMessageId) {
                if (messageProposals.size() == currentDestinationPorts.size()) {
                    int agreedSeqNum = -1;
                    int proposerOfAgreedSeq = -1;

                    Iterator<Message> proposalItr = messageProposals.iterator();
                    while (proposalItr.hasNext()) {
                        //Choose the proposal with the highest seqNum then with the highest proposerPid
                        Message proposal = proposalItr.next();
                        if (proposal.getGlobalSeqNum() > agreedSeqNum) {
                            agreedSeqNum = proposal.getGlobalSeqNum();
                            proposerOfAgreedSeq = proposal.getProposerPid();
                        }
                        else if(proposal.getGlobalSeqNum() == agreedSeqNum)
                        {
                            if(proposal.getProposerPid() > proposerOfAgreedSeq)
                                proposerOfAgreedSeq = proposal.getProposerPid();
                        }
                    }

                    // send agreements to the proposers with the agreed sequence number
                    proposalItr = messageProposals.iterator();
                    while (proposalItr.hasNext()) {
                        Message agreement = proposalItr.next();
                        Integer destinationpid = agreement.getProposerPid();
                        agreement.setType(Message.TYPE.AGREEMENT);
                        agreement.setGlobalSeqNum(agreedSeqNum);
                        agreement.setProposerPid(proposerOfAgreedSeq);
                        sendMessage(agreement, destinationpid);
                        Log.v(TAG, "Agreement sent: " + agreement.toString());
                    }

                    //remove the message from the broadcastedMessageList
                    broadcastedMessageList.remove(senderMessageId);
        }
    }

    synchronized Message buildMessageFromReceivedJson(String message) throws JSONException {
        JSONObject messageJSON;
        Message mMessage = new Message();
        try {
            messageJSON = new JSONObject(message);

            mMessage.setTypeId(messageJSON.getInt("type"));

            // assign a receiver messageId if it a new message
            if (mMessage.getType() == Message.TYPE.DATA) {
                mMessage.setReceiverMessageId(++lastReceivedMessageId);
            } else {
                mMessage.setReceiverMessageId(messageJSON.getInt("receiverMessageId"));
            }

            mMessage.setSenderMessageId(messageJSON.getInt("senderMessageId"));
            mMessage.setGlobalSeqNum(messageJSON.getInt("globalSeqNum"));
            mMessage.setSenderPid(messageJSON.getInt("senderPid"));
            mMessage.setProposerPid(messageJSON.getInt("proposerPid"));
            mMessage.setMessageText(messageJSON.getString("messageText"));

        } catch (JSONException e) {
            Log.e(TAG, "Improper Message Format.");
            throw e;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Improper Message Type.");
            throw new JSONException("Improper message type");
        }

        return mMessage;

    }


    synchronized String buildJSONMessage(Message message) {
        StringWriter sWriter = new StringWriter();
        JsonWriter jWriter = new JsonWriter(sWriter);
        String jsonMessage = "";
        try {
            jWriter.beginObject();

            // message type
            jWriter.name("type").value(message.getTypeId());

            // if message is new there would be no receiverMessageId and globalSequence number
            jWriter.name("receiverMessageId").value(message.getReceiverMessageId());
            jWriter.name("senderMessageId").value(message.getSenderMessageId());
            jWriter.name("globalSeqNum").value(message.getGlobalSeqNum());
            jWriter.name("senderPid").value(message.getSenderPid());
            jWriter.name("proposerPid").value(message.getProposerPid());

            // add the messageText
            jWriter.name("messageText").value(message.getMessageText());
            jWriter.endObject();
            jsonMessage = sWriter.toString();

        } catch (IOException e) {
            Log.e(TAG, "IO Exception in JsonWriter.");
            Log.e(TAG, e.toString());
        }

        return jsonMessage;
    }


    // this would be invoke when the send button it tapped
    synchronized public void broadcastMessage(Message.TYPE messageType, String messageText) {
        // build message object
        Message mMessage = buildMessageObject(messageType, messageText);

        broadcastedMessageList.put(mMessage.getSenderMessageId(), new LinkedList<Message>());
        mSender.broadcastMessage(buildJSONMessage(mMessage), currentDestinationPorts);
        Log.v(TAG, "Message broad-casted: " + mMessage.toString());

    }

    synchronized public void sendMessage(Message mMessage, Integer destinationID) {
        mSender.sendMessage(buildJSONMessage(mMessage), destinationID);
    }

    // this is would be used for new messages only
    synchronized Message buildMessageObject(Message.TYPE messageType, String messageText) {
        Message mMessage = new Message();
        mMessage.setMessageText(messageText);
        mMessage.setType(messageType);
        // no globalSeqNum for new messages
        // no receiverMessageId for new messages
        mMessage.setReceiverMessageId(-1);
        mMessage.setGlobalSeqNum(-1);
        mMessage.setProposerPid(-1);
        mMessage.setSenderPid(MY_PORT);
        // isDeliverable will be handled by the receiver

        if(!(messageType == Message.TYPE.PING || messageType == Message.TYPE.ACK))
        {
            mMessage.setSenderMessageId(++lastSentMessageId);
        }

        return mMessage;
    }

    @Override
    synchronized public void onTimeToPing(int processIdToPing) {

//        Log.v(TAG, "Time to send ping to process Id " + processIdToPing +
//                "\nTime: " + new Date());

        // ping the process
        Message pingMessage = buildMessageObject(Message.TYPE.PING, "");
        sendMessage(pingMessage, processIdToPing);
        // Log.v(TAG + "Pinger", "Ping sent to process Id " + processIdToPing + " from " + MY_PORT);
    }


    @Override
    synchronized public void onTimeToCheckAck(int process)
    {
        //check if previous ACK was received
        PingTracker pingTracker = pingerMap.get(process);
        if(pingTracker != null){
            if(!pingTracker.receivedACK)
            {
                // this process has likely failed
                onFailure(process);
            }
            pingTracker.receivedACK = false;
            if(pingTracker.numAcksWithoutDataTransfer++ == PINGERSTOPPER_COUNT)
            {
                Log.e(TAG + "Pinger", "No Active data connection with " + process + " in " + MY_PORT
                        + ". Stopping Pinger.");
                pingTracker.pinger.stopPinger();
            }
        } else {
            Log.e(TAG + "Pinger", "Unable to find the pinger of process." + process);
        }
        // Log.v(TAG + "Pinger", "Checked ack for " + process + " in " + MY_PORT);
    }

    @Override
    synchronized public void onFailure(final int failedProcessId) {

        Log.e(TAG + "ONFAILURE", "Failed process: " + failedProcessId +
                "\nTime: " + new Date());


        parentActivity.runOnUiThread(new Runnable() {
            public void run() {
                //display the message
                displayView.append("Process " + failedProcessId + " has failed.\n");
            }
        });

        // stop the pinger for the failed process
        PingTracker pingTracker = pingerMap.remove(failedProcessId);
        if(pingTracker != null){
            pingTracker.pinger.stopPinger();
            Log.e(TAG + "ONFAILURE", "Stopping pinger for " + failedProcessId);

        }
        else{
            Log.e(TAG  + "ONFAILURE", "Unable to find the pinger of the failed process." + failedProcessId);
        }

        // remove the failed process from the destination list
        Log.v(TAG + "ONFAILURE", "Before removing from destination ports"
                + "\ncurrentDestinationPorts: " +currentDestinationPorts.toString());
        if (currentDestinationPorts.remove(new Integer(failedProcessId))) {
            Log.v(TAG  + "ONFAILURE", "Removed failed process from the destinations: " + failedProcessId
                    + "\nCurrent destination list size: " + currentDestinationPorts.size());
        } else {
            Log.e(TAG  + "ONFAILURE", "Unable to remove failed process: " + failedProcessId);
        }

        // print queue
        StringBuilder sb = new StringBuilder();
        sb.append("Message Queue before removing on Failure:\n");
        for (Message m : messageQueue) {
            sb.append(m.toString());
            sb.append("\n");
        }
        Log.v(TAG + "ONFAILURE", sb.toString());
        // clear the deleted process' messages from the priority queue
        Iterator<Message> queueItr = messageQueue.iterator();
        while (queueItr.hasNext()) {
            Message messageInQueue = queueItr.next();
            if (messageInQueue.getSenderPid() == failedProcessId) {
                queueItr.remove();
                Log.v(TAG  + "ONFAILURE", "Cleared message from queue due to failed process: "
                        + failedProcessId + "\n" + messageInQueue.toString());
            }
        }

        Log.v(TAG + "ONFAILURE", "Broadcasted Messages and their proposals");
        // clear the proposals of this failed process from my list
        Iterator<HashMap.Entry<Integer, List<Message>>> broadcastListItr =
                broadcastedMessageList.entrySet().iterator();
        int i = 1;
        while (broadcastListItr.hasNext()) {
            HashMap.Entry<Integer, List<Message>> pair = broadcastListItr.next();
            List<Message> myProposals = pair.getValue();

            Log.v(TAG + "ONFAILURE", "Broadcasted Message " + i++);
            for(Iterator<Message> iter = myProposals.iterator(); iter.hasNext();)
            {
                Message proposal = iter.next();
                Log.v(TAG + "ONFAILURE", proposal.toString());
                if (proposal.getProposerPid() == failedProcessId) {
                    iter.remove();
                    Log.v(TAG  + "ONFAILURE", "Removed proposal: \n" + proposal.toString()
                            + "\nfrom failed process " + failedProcessId);
                }
            }

            // Send agreement if necessary
            if(!myProposals.isEmpty()){
                Message firstProposal = myProposals.get(0);
                sendAgreementIfAppropriate(myProposals, firstProposal.getSenderMessageId());
            }
        }

        // deliver the messages on top of the queue which are deliverable
        deliverMessagesifAppropriate();

        Log.v(TAG + "ONFAILURE", "End of ON FAILURE");

    }
}


