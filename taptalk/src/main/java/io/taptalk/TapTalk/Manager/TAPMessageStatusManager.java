package io.taptalk.TapTalk.Manager;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.taptalk.TapTalk.Model.TAPMessageModel;
import io.taptalk.TapTalk.View.Activity.TAPChatActivity;

public class TAPMessageStatusManager {
    private static final String TAG = TAPMessageStatusManager.class.getSimpleName();
    private static TAPMessageStatusManager instance;
    private ScheduledExecutorService messageStatusScheduler;
    private List<TAPMessageModel> readMessageQueue;

    public static TAPMessageStatusManager getInstance() {
        return null == instance ? instance = new TAPMessageStatusManager() : instance;
    }

    public List<TAPMessageModel> getReadMessageQueue() {
        return null == readMessageQueue ? readMessageQueue = new ArrayList<>() : readMessageQueue;
    }

    public void addReadMessageQueue(TAPMessageModel readMessage) {
        getReadMessageQueue().add(readMessage);
    }

    public void removeReadMessageQueue(List<TAPMessageModel> readMessages) {
        getReadMessageQueue().removeAll(readMessages);
    }

    public void removeReadMessageQueue(TAPMessageModel readMessage) {
        getReadMessageQueue().remove(readMessage);
    }

    public void clearReadMessageQueue() {
        getReadMessageQueue().clear();
    }

    public void triggerCallReadMessageApiScheduler(TAPChatActivity.MessageStatusInterface msgStatusInterface) {
        if (null == messageStatusScheduler || messageStatusScheduler.isShutdown()) {
            messageStatusScheduler = Executors.newSingleThreadScheduledExecutor();
        } else {
            messageStatusScheduler.shutdown();
            messageStatusScheduler = Executors.newSingleThreadScheduledExecutor();
        }

        messageStatusScheduler.scheduleAtFixedRate(() -> {
            // TODO: 15/11/18 call API read Message ID
            updateMessageStatusInView(msgStatusInterface);
//            Log.e(TAG, "triggerCallReadMessageApiScheduler: " );
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void shutdownReadMessageApiScheduler() {
        if (null != messageStatusScheduler && !messageStatusScheduler.isShutdown())
            messageStatusScheduler.shutdown();
    }

    public void updateMessageStatusWhenCloseRoom(TAPChatActivity.MessageStatusInterface msgStatusInterface) {
        updateMessageStatusInView(msgStatusInterface);
        shutdownReadMessageApiScheduler();
    }

    private void updateMessageStatusInView(TAPChatActivity.MessageStatusInterface msgStatusInterface) {
//        Log.e(TAG, "updateMessageStatusInView: " );
        msgStatusInterface.onReadStatus(new ArrayList<>(getReadMessageQueue()));
        clearReadMessageQueue();
    }
}
