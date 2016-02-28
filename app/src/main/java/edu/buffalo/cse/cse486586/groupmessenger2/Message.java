package edu.buffalo.cse.cse486586.groupmessenger2;

public class Message implements Comparable<Message>{

    private  String messageText;
    private int  type;
    private int receiverMessageId;
    private int senderMessageId;        // original sender id
    private int globalSeqNum;           // proposed or agreed Sequence Number
    private int proposerPid;
    private int senderPid;
    private boolean isDeliverable;

    public int getReceiverMessageId() {
        return receiverMessageId;
    }

    public void setReceiverMessageId(int receiverMessageId) {
        this.receiverMessageId = receiverMessageId;
    }

    public int getSenderPid() {
        return senderPid;
    }

    public void setSenderPid(int senderPid) {
        this.senderPid = senderPid;
    }

    public int getSenderMessageId() {
        return senderMessageId;
    }

    public void setSenderMessageId(int senderMessageId) {
        this.senderMessageId = senderMessageId;
    }

    public boolean isDeliverable() {
        return isDeliverable;
    }

    public void setIsDeliverable(boolean isDeliverable) {
        this.isDeliverable = isDeliverable;
    }

    public int getProposerPid() {
        return proposerPid;
    }

    public void setProposerPid(int proposerPid) {
        this.proposerPid = proposerPid;
    }

    public int getGlobalSeqNum() {
        return globalSeqNum;
    }

    public void setGlobalSeqNum(int globalSeqNum) {
        this.globalSeqNum = globalSeqNum;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    /**
     * Compares this object to the specified object to determine their relative
     * order.
     *
     * @param another the object to compare to this instance.
     * @return a negative integer if this instance is less than {@code another};
     * a positive integer if this instance is greater than
     * {@code another}; 0 if this instance has the same order as
     * {@code another}.
     * @throws ClassCastException if {@code another} cannot be converted into something
     *                            comparable to {@code this} instance.
     */
    @Override
    public int compareTo(Message another) {
        if(this.globalSeqNum < another.globalSeqNum)
            return -1;
        else if(this.globalSeqNum > another.globalSeqNum)
            return 1;
        else if(this.proposerPid < another.proposerPid)
            return -1;
        else if(this.proposerPid > another.proposerPid)
            return 1;
        else
            return 0;
    }

    /**
     * Compares this instance with the specified object and indicates if they
     * are equal. In order to be equal, {@code o} must represent the same object
     * as this instance using a class-specific comparison. The general contract
     * is that this comparison should be reflexive, symmetric, and transitive.
     * Also, no object reference other than null is equal to null.
     * <p/>
     * <p>The default implementation returns {@code true} only if {@code this ==
     * o}. See <a href="{@docRoot}reference/java/lang/Object.html#writing_equals">Writing a correct
     * {@code equals} method</a>
     * if you intend implementing your own {@code equals} method.
     * <p/>
     * <p>The general contract for the {@code equals} and {@link
     * #hashCode()} methods is that if {@code equals} returns {@code true} for
     * any two objects, then {@code hashCode()} must return the same value for
     * these objects. This means that subclasses of {@code Object} usually
     * override either both methods or neither of them.
     *
     * @param o the object to compare this instance with.
     * @return {@code true} if the specified object is equal to this {@code
     * Object}; {@code false} otherwise.
     * @see #hashCode
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof Message && this.receiverMessageId == ((Message) o).receiverMessageId;
    }

    /**
     * Returns an integer hash code for this object. By contract, any two
     * objects for which {@link #equals} returns {@code true} must return
     * the same hash code value. This means that subclasses of {@code Object}
     * usually override both methods or neither method.
     * <p/>
     * <p>Note that hash values must not change over time unless information used in equals
     * comparisons also changes.
     * <p/>
     * <p>See <a href="{@docRoot}reference/java/lang/Object.html#writing_hashCode">Writing a correct
     * {@code hashCode} method</a>
     * if you intend implementing your own {@code hashCode} method.
     *
     * @return this object's hash code.
     * @see #equals
     */
    @Override
    public int hashCode() {
        return this.receiverMessageId;
    }

    /**
     * Returns a string containing a concise, human-readable description of this
     * object. Subclasses are encouraged to override this method and provide an
     * implementation that takes into account the object's type and data. The
     * default implementation is equivalent to the following expression:
     * <pre>
     *   getClass().getName() + '@' + Integer.toHexString(hashCode())</pre>
     * <p>See <a href="{@docRoot}reference/java/lang/Object.html#writing_toString">Writing a useful
     * {@code toString} method</a>
     * if you intend implementing your own {@code toString} method.
     *
     * @return a printable representation of this object.
     */
    @Override
    public String toString() {

        return ("Type: " + this.type + "\n") + "Message Text: " + this.messageText + "\n" +
                "Global Sequence Number: " + this.globalSeqNum + "\n" +
                "Message Id at at source: " + this.senderMessageId + "\n" +
                "Message Id at receiver: " + this.receiverMessageId + "\n" +
                "Sender Id: " + this.senderPid + "\n" +
                "Proposer Id: " + this.proposerPid + "\n" +
                "Is Deliverable? " + this.isDeliverable;
    }

}
