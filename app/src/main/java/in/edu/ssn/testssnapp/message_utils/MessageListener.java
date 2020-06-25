package in.edu.ssn.testssnapp.message_utils;

public interface MessageListener {
    void onMessageAdded(Message message);

    void onMessageRemoved(String id);

    void onMessageModified(String id, Message message);
}
