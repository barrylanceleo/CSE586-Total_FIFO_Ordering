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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Coordinator implements MessageReceivedEventListener, TimerExpiredEventListener {

    static final String TAG = Coordinator.class.getSimpleName();
    static final int[] DESTINATION_PORTS = {11108, 11112, 11116, 11120, 11124};
    static final int LISTENER_PORT = 10000;
    static final long TIMEOUT = 5000;
    int MY_PORT;
    private Activity parentActivity;
    private TextView displayView;

    private static Coordinator instance = null;
    private Sender mSender;
    private Receiver mReceiver;
    private TimerManager mTimerManager;
    private Integer lastReceivedMessageId = -1;
    private Integer lastSentMessageId = -1;
    private Integer lastProposedSeqNum = -1;
    private Integer lastAgreedSeqNum = -1;
    private Integer finalContinuousSeqNum = 0;

    private ConcurrentHashMap<Integer, List<Message>> broadcastedMessageList = new ConcurrentHashMap<Integer, List<Message>>();
    private PriorityQueue<Message> messageQueue = new PriorityQueue<Message>();
    private List<Integer> currentDestinationPorts;

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
        mSender = new Sender(parentActivity);
        this.currentDestinationPorts = new LinkedList<Integer>();
        for (int port : DESTINATION_PORTS)
            this.currentDestinationPorts.add(port);

        //create a receiver to keep listening to incoming messages
        mReceiver = new Receiver(parentActivity, LISTENER_PORT, this);

        // create a timer manager
        mTimerManager = new TimerManager(parentActivity, this);
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
        StringBuilder sb = new StringBuilder();
        sb.append("Message Queue on receiving message:\n");
        for (Message m : messageQueue)
            sb.append(m.toString() + "\n");
        Log.v(TAG, sb.toString());

        // handle the message based on the message type
        switch (receivedMessage.getType()) {
            case 0:
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
                receivedMessage.setType(1);
                sendMessage(receivedMessage, receivedMessage.getSenderPid());
                Log.v(TAG, "Proposal sent: " + receivedMessage.toString());

                break;
            case 1:

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

            case 2:
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

            default:
                Log.e(TAG, "Improper Message Type. Ignoring...");
                return;
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
            }
            ++finalContinuousSeqNum;
        }

        // print queue
        StringBuilder sb = new StringBuilder();
        sb.append("Message Queue after delivering messages:\n");
        for (Message m : messageQueue)
            sb.append(m.toString() + "\n");
        Log.v(TAG, sb.toString());

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
                        agreement.setType(2);
                        agreement.setGlobalSeqNum(agreedSeqNum);
                        agreement.setProposerPid(proposerOfAgreedSeq);
                        sendMessage(agreement, destinationpid);
                        Log.v(TAG, "Agreement sent: " + agreement.toString());
                    }

                    //remove the message from the hashmap
                    broadcastedMessageList.remove(senderMessageId);
        }
    }

    synchronized Message buildMessageFromReceivedJson(String message) throws JSONException {
        JSONObject messageJSON;
        Message mMessage = new Message();
        try {
            messageJSON = new JSONObject(message);

            mMessage.setType(messageJSON.getInt("type"));
            if (mMessage.getType() < 0 || mMessage.getType() > 2)
                throw new NumberFormatException();

            // assign a receiver messageId if it a new message
            if (mMessage.getType() == 0) {
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
            jWriter.name("type").value(message.getType());

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
    synchronized public void broadcastMessage(String messageText) {
        // build message object
        Message mMessage = buildMessageObject(messageText);

        broadcastedMessageList.put(mMessage.getSenderMessageId(), new LinkedList<Message>());
        mSender.broadcastMessage(buildJSONMessage(mMessage), currentDestinationPorts);
        Log.v(TAG, "Message broad-casted: " + mMessage.toString());

        // start a timer after broadcasting a message
        mTimerManager.startTimer(TIMEOUT, mMessage.getSenderMessageId());
        Log.v(TAG, "Timer for Message Id " + mMessage.getSenderMessageId() +
                " Start Time: " + new Date());

    }

    synchronized public void broadcastMessage(Message mMessage) {
        mSender.broadcastMessage(buildJSONMessage(mMessage), currentDestinationPorts);
    }

    synchronized public void sendMessage(Message mMessage, Integer destinationPort) {
        mSender.sendMessage(buildJSONMessage(mMessage), destinationPort);
    }

    // this is would be used for new messages only
    synchronized Message buildMessageObject(String messageText) {
        Message mMessage = new Message();
        mMessage.setMessageText(messageText);
        mMessage.setType(0);
        mMessage.setSenderMessageId(++lastSentMessageId);

        // no globalSeqNum for new messages
        // no receiverMessageId for new messages
        mMessage.setReceiverMessageId(-1);
        mMessage.setGlobalSeqNum(-1);

        mMessage.setProposerPid(-1);

        mMessage.setSenderPid(MY_PORT);
        // isDeliverable will be handled by the receiver
        return mMessage;
    }

    @Override
    synchronized public void onTimerExpired(final int senderMessageId) {

        Log.v(TAG, "Timer for Message Id " + senderMessageId +
                " End Time: " + new Date());

        List<Message> proposals = broadcastedMessageList.get(senderMessageId);

        if (proposals != null && proposals.size() != currentDestinationPorts.size()) {

            Log.v(TAG, "Proposals missing even after the timer expired." +
                    "\nSenderMessageId: " + senderMessageId +
                    "\nProposal Count: " + proposals.size() +
                    "\nDestination Count: " + currentDestinationPorts.size());

            //find the missing proposer

            // create a set of processes that have proposed
            HashSet<Integer> currentProposers = new HashSet<Integer>();
            for (Message proposal : proposals) {
                currentProposers.add(proposal.getProposerPid());
            }

            // find the missing proposers and put them in a list
            List<Integer> missingProposers = new LinkedList<Integer>();
            for (Integer proposers : currentDestinationPorts) {
                if (!currentProposers.contains(proposers)) {
                    missingProposers.add(proposers);

                    // display in the UI
                    final int missingProposer = proposers;
                    parentActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            //display the message
                            displayView.append("Looks like pid: "
                                    + missingProposer + "has been terminated\n");
                        }
                    });

                }
            }

            // for each missing proposer clear their state
            for (Integer missingProposer : missingProposers) {
                Log.v(TAG, "Missing Proposer: " + missingProposer);

                // declare the missing proposer as failed and
                // remove the proposers from the list
                if (currentDestinationPorts.remove(missingProposer)) {
                    Log.v(TAG, "Removed missing proposer from the destinations: " + missingProposer
                            + "\nCurrent proposers size: " + currentDestinationPorts.size());
                } else {
                    Log.v(TAG, "Unable to remove Missing Proposer: " + missingProposer);
                }

                // clear the deleted process' messages from the priority queue
                Iterator<Message> queueItr = messageQueue.iterator();
                while (queueItr.hasNext()) {
                    Message messageInQueue = queueItr.next();
                    if (messageInQueue.getSenderPid() == missingProposer) {
                        if (messageQueue.remove(messageInQueue)) {
                            Log.v(TAG, "Cleared message from queue due to missing proposer: "
                                    + missingProposer + "\n" + messageInQueue.toString());
                        }
                    }
                }

                // clear the proposal of this proposer from my list
                Iterator<HashMap.Entry<Integer, List<Message>>> broadcastListItr =
                        broadcastedMessageList.entrySet().iterator();
                while (broadcastListItr.hasNext()) {
                    HashMap.Entry<Integer, List<Message>> pair = broadcastListItr.next();
                    List<Message> myProposals = pair.getValue();

                    for(Iterator<Message> iter = myProposals.iterator(); iter.hasNext();)
                    {
                        Message proposal = iter.next();
                        if (proposal.getProposerPid() == missingProposer) {
                            iter.remove();
                            Log.v(TAG, "Removed proposal: \n" + proposal.toString()
                                    + "\nfrom missing proposer " + missingProposer);
                        }
                    }

                    // Send agreement if necessary
                    if(!myProposals.isEmpty()){
                        Message firstProposal = myProposals.get(0);
                        sendAgreementIfAppropriate(myProposals, firstProposal.getSenderMessageId());
                    }
                }


            }

            // deliver the messages on top of the queue which are deliverable
            deliverMessagesifAppropriate();

        }
    }
}


