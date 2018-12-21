package io.taptalk.TapTalk.Manager;

import android.content.ClipData;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.widget.Toast;

import com.fasterxml.jackson.core.type.TypeReference;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.taptalk.TapTalk.Data.Message.TAPMessageEntity;
import io.taptalk.TapTalk.Helper.TAPFileUtils;
import io.taptalk.TapTalk.Helper.TAPUtils;
import io.taptalk.TapTalk.Helper.TapTalk;
import io.taptalk.TapTalk.Interface.TapTalkSocketInterface;
import io.taptalk.TapTalk.Listener.TAPChatListener;
import io.taptalk.TapTalk.Listener.TAPSocketMessageListener;
import io.taptalk.TapTalk.Model.TAPEmitModel;
import io.taptalk.TapTalk.Model.TAPImagePreviewModel;
import io.taptalk.TapTalk.Model.TAPImageURL;
import io.taptalk.TapTalk.Model.TAPMessageModel;
import io.taptalk.TapTalk.Model.TAPOnlineStatusModel;
import io.taptalk.TapTalk.Model.TAPRoomModel;
import io.taptalk.TapTalk.Model.TAPTypingModel;
import io.taptalk.TapTalk.Model.TAPUserModel;
import io.taptalk.TapTalk.Model.TAPUserRoleModel;

import static io.taptalk.TapTalk.Const.TAPDefaultConstant.ConnectionEvent.kEventOpenRoom;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.ConnectionEvent.kSocketAuthentication;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.ConnectionEvent.kSocketCloseRoom;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.ConnectionEvent.kSocketDeleteMessage;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.ConnectionEvent.kSocketNewMessage;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.ConnectionEvent.kSocketOpenMessage;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.ConnectionEvent.kSocketStartTyping;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.ConnectionEvent.kSocketStopTyping;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.ConnectionEvent.kSocketUpdateMessage;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.ConnectionEvent.kSocketUserOnlineStatus;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.MessageType.TYPE_IMAGE;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.MessageType.TYPE_TEXT;

public class TAPChatManager {

    private final String TAG = TAPChatManager.class.getSimpleName();
    private static TAPChatManager instance;
    private Map<String, TAPMessageModel> pendingMessages, waitingResponses, incomingMessages;
    private Map<String, String> messageDrafts;
    private List<TAPChatListener> chatListeners;
    private List<TAPMessageEntity> saveMessages; //message to be saved
    private List<String> replyMessageLocalIDs;
    private TAPRoomModel activeRoom;
    private TAPUserModel activeUser;
    private ScheduledExecutorService scheduler;
    private String openRoom;
    private boolean isCheckPendingArraySequenceActive = false;
    private boolean isPendingMessageExist;
    private boolean isFileUploadExist;
    private boolean isFinishChatFlow;
    private boolean isNeedToCalledUpdateRoomStatusAPI = true;
    private int pendingRetryAttempt = 0;
    private int maxRetryAttempt = 10;
    private int pendingRetryInterval = 60 * 1000;
    private final int maxImageSize = 2000;
    private final Integer CHARACTER_LIMIT = 1000;

    private TapTalkSocketInterface socketListener = new TapTalkSocketInterface() {
        @Override
        public void onSocketConnected() {

            checkAndSendPendingMessages();
            isFinishChatFlow = false;
        }

        @Override
        public void onSocketDisconnected() {
            if (TapTalk.isForeground && TAPNetworkStateManager.getInstance().hasNetworkConnection(TapTalk.appContext)
                    && TAPConnectionManager.ConnectionStatus.DISCONNECTED == TAPConnectionManager.getInstance().getConnectionStatus())
                TAPConnectionManager.getInstance().reconnect();
        }

        @Override
        public void onSocketConnecting() {

        }

        @Override
        public void onSocketError() {
            TAPConnectionManager.getInstance().reconnect();
        }
    };

    private TAPSocketMessageListener socketMessageListener = new TAPSocketMessageListener() {
        @Override
        public void onReceiveNewEmit(String eventName, String emitData) {
            switch (eventName) {
                case kEventOpenRoom:
                    break;
                case kSocketCloseRoom:
                    break;
                case kSocketNewMessage:
                    TAPEmitModel<TAPMessageModel> newMessageEmit = TAPUtils.getInstance()
                            .fromJSON(new TypeReference<TAPEmitModel<TAPMessageModel>>() {
                            }, emitData);
                    try {
                        receiveMessageFromSocket(TAPMessageModel.BuilderDecrypt(newMessageEmit.getData()), eventName);
                    } catch (GeneralSecurityException e) {
                        e.printStackTrace();
                    }
                    break;
                case kSocketUpdateMessage:
                    TAPEmitModel<TAPMessageModel> updateMessageEmit = TAPUtils.getInstance()
                            .fromJSON(new TypeReference<TAPEmitModel<TAPMessageModel>>() {
                            }, emitData);
                    try {
                        receiveMessageFromSocket(TAPMessageModel.BuilderDecrypt(updateMessageEmit.getData()), eventName);
                    } catch (GeneralSecurityException e) {
                        e.printStackTrace();
                    }
                    break;
                case kSocketDeleteMessage:
                    TAPEmitModel<TAPMessageModel> deleteMessageEmit = TAPUtils.getInstance()
                            .fromJSON(new TypeReference<TAPEmitModel<TAPMessageModel>>() {
                            }, emitData);
                    try {
                        receiveMessageFromSocket(TAPMessageModel.BuilderDecrypt(deleteMessageEmit.getData()), eventName);
                    } catch (GeneralSecurityException e) {
                        e.printStackTrace();
                    }
                    break;
                case kSocketOpenMessage:
                    break;
                case kSocketStartTyping:
                    TAPEmitModel<TAPTypingModel> startTypingEmit = TAPUtils.getInstance()
                            .fromJSON(new TypeReference<TAPEmitModel<TAPTypingModel>>() {
                            }, emitData);
                    for (TAPChatListener listener : chatListeners) {
                        listener.onReceiveStartTyping(startTypingEmit.getData());
                    }
                    break;
                case kSocketStopTyping:
                    TAPEmitModel<TAPTypingModel> stopTypingEmit = TAPUtils.getInstance()
                            .fromJSON(new TypeReference<TAPEmitModel<TAPTypingModel>>() {
                            }, emitData);
                    for (TAPChatListener listener : chatListeners) {
                        listener.onReceiveStopTyping(stopTypingEmit.getData());
                    }
                    break;
                case kSocketAuthentication:
                    break;
                case kSocketUserOnlineStatus:
                    TAPEmitModel<TAPOnlineStatusModel> onlineStatusEmit = TAPUtils.getInstance()
                            .fromJSON(new TypeReference<TAPEmitModel<TAPOnlineStatusModel>>() {
                            }, emitData);
                    TAPOnlineStatusModel onlineStatus = onlineStatusEmit.getData();
                    for (TAPChatListener listener : chatListeners) {
                        listener.onUserOnlineStatusUpdate(onlineStatus);
                    }
                    break;
            }
        }
    };

    public static TAPChatManager getInstance() {
        return instance == null ? (instance = new TAPChatManager()) : instance;
    }

    public TAPChatManager() {
        TAPConnectionManager.getInstance().addSocketListener(socketListener);
        TAPConnectionManager.getInstance().setSocketMessageListener(socketMessageListener);
        setActiveUser(TAPDataManager.getInstance().getActiveUser());
        chatListeners = new ArrayList<>();
        saveMessages = new ArrayList<>();
        pendingMessages = new LinkedHashMap<>();
        waitingResponses = new LinkedHashMap<>();
        incomingMessages = new LinkedHashMap<>();
        messageDrafts = new HashMap<>();
    }

    public void addChatListener(TAPChatListener chatListener) {
        chatListeners.add(chatListener);
    }

    public void removeChatListener(TAPChatListener chatListener) {
        chatListeners.remove(chatListener);
    }

    public void removeChatListenerAt(int index) {
        chatListeners.remove(index);
    }

    public void clearChatListener() {
        chatListeners.clear();
    }

    public TAPRoomModel getActiveRoom() {
        return activeRoom;
    }

    public void setActiveRoom(TAPRoomModel room) {
        this.activeRoom = room;
        //ini kenapa di taro disini karena setiap ada active room otomatis open room nya juga akan diubah
        //kecuali kalau misalnya active roomnya itu diubah jadi null
        if (null != room) setOpenRoom(room.getRoomID());
    }

    //Open room itu buat nyimpen roomID apa yang dibuka
    // Apa bedanya sama activeRoom? bedanya adalah klo active room itu di pause lgsg jd null
    // kalau open room slama dy belom ke destroy activitynya ga akan jadi null
    //kegunaannya buat di pake pas reply yang ada di background
    //ngecekin karena dy trigger onNewMessageFromOtherRoom, jadi kalau roomnya lagi ga di buka jangan di push messagenya ke UI
    public String getOpenRoom() {
        return openRoom;
    }

    public void setOpenRoom(String openRoom) {
        this.openRoom = openRoom;
    }

    public TAPUserModel getActiveUser() {
        return activeUser == null ? TAPDataManager.getInstance().getActiveUser() : activeUser;
    }

    public void setActiveUser(TAPUserModel user) {
        this.activeUser = user;
    }

    public void saveActiveUser(TAPUserModel user) {
        this.activeUser = user;
        TAPDataManager.getInstance().saveActiveUser(user);
    }

    public Map<String, TAPMessageModel> getMessageQueueInActiveRoom() {
        Map<String, TAPMessageModel> roomQueue = new LinkedHashMap<>();
        for (Map.Entry<String, TAPMessageModel> entry : pendingMessages.entrySet()) {
            if (entry.getValue().getRoom().getRoomID().equals(activeRoom)) {
                roomQueue.put(entry.getKey(), entry.getValue());
            }
        }
        return roomQueue;
    }

    /**
     * generate room ID
     */
    public String arrangeRoomId(String userId, String friendId) {
        int myId = (null != userId && !"null".equals(userId)) ? Integer.parseInt(userId) : 0;
        int fId = (null != friendId && !"null".equals(friendId)) ? Integer.parseInt(friendId) : 0;
        return myId < fId ? myId + "-" + fId : fId + "-" + myId;
    }

    /**
     * get other user ID from the currently active room
     */
    private String getOtherUserIdFromActiveRoom(String roomID) {
        String[] splitRoomID = roomID.split("-");
        return !splitRoomID[0].equals(getActiveUser().getUserID()) ? splitRoomID[0] : splitRoomID[1];
    }

    /**
     * convert TAPMessageEntity to TAPMessageModel
     */
    public TAPMessageModel convertToModel(TAPMessageEntity entity) {
        return new TAPMessageModel(
                entity.getMessageID(),
                entity.getLocalID(),
                entity.getFilterID(),
                entity.getBody(),
                new TAPRoomModel(entity.getRoomID(), entity.getRoomName(), entity.getRoomType(),
                        // TODO: 18 October 2018 REMOVE CHECK
                        /* TEMPORARY CHECK FOR NULL IMAGE */null != entity.getRoomImage() ?
                        TAPUtils.getInstance().fromJSON(new TypeReference<TAPImageURL>() {
                        }, entity.getRoomImage())
                        /* TEMPORARY CHECK FOR NULL IMAGE */ : null
                        , entity.getRoomColor()),
                entity.getType(),
                entity.getCreated(),
                new TAPUserModel(entity.getUserID(), entity.getXcUserID(), entity.getUserFullName(),
                        TAPUtils.getInstance().fromJSON(new TypeReference<TAPImageURL>() {
                        }, entity.getUserImage()),
                        entity.getUsername(), entity.getUserEmail(), entity.getUserPhone(),
                        TAPUtils.getInstance().fromJSON(new TypeReference<TAPUserRoleModel>() {
                        }, entity.getUserRole()),
                        entity.getLastLogin(), entity.getLastActivity(), entity.getRequireChangePassword(), entity.getUserCreated(),
                        entity.getUserUpdated()),
                entity.getRecipientID(),
                entity.getDeleted(),
                entity.getSending(),
                entity.getFailedSend(),
                entity.getDelivered(),
                entity.getIsRead(),
                entity.getHidden(),
                entity.getUpdated(),
                entity.getUserDeleted());
    }

    /**
     * convert TAPMessageModel to TAPMessageEntity
     */
    public TAPMessageEntity convertToEntity(TAPMessageModel model) {
        return new TAPMessageEntity(
                model.getMessageID(), model.getLocalID(), model.getFilterID(), model.getBody(), model.getRecipientID(),
                model.getType(), model.getCreated(), model.getUpdated(), model.getIsRead(),
                model.getDelivered(), model.getHidden(), model.getIsDeleted(), model.getSending(),
                model.getFailedSend(), model.getRoom().getRoomID(), model.getRoom().getRoomName(),
                model.getRoom().getRoomColor(), model.getRoom().getRoomType(),
                TAPUtils.getInstance().toJsonString(model.getRoom().getRoomImage()),
                model.getUser().getUserID(), model.getUser().getXcUserID(), model.getUser().getName(),
                model.getUser().getUsername(), TAPUtils.getInstance().toJsonString(model.getUser().getAvatarURL()),
                model.getUser().getEmail(), model.getUser().getPhoneNumber(),
                TAPUtils.getInstance().toJsonString(model.getUser().getUserRole()),
                model.getUser().getLastLogin(), model.getUser().getLastActivity(),
                model.getUser().getRequireChangePassword(),
                model.getUser().getCreated(), model.getUser().getUpdated(), model.getDeleted()
        );
    }

    /**
     * Send text messages
     */
    public void sendTextMessage(String textMessage) {
        sendTextMessageWithRoomModel(textMessage, activeRoom);
    }

    public void sendTextMessageWithRoomModel(String textMessage, TAPRoomModel roomModel) {
        Integer startIndex;
        if (textMessage.length() > CHARACTER_LIMIT) {
            // Message exceeds character limit
            List<TAPMessageEntity> messageEntities = new ArrayList<>();
            Integer length = textMessage.length();
            for (startIndex = 0; startIndex < length; startIndex += CHARACTER_LIMIT) {
                String substr = TAPUtils.getInstance().mySubString(textMessage, startIndex, CHARACTER_LIMIT);
                TAPMessageModel messageModel = buildTextMessage(substr, roomModel, getActiveUser());
                // Add entity to list
                messageEntities.add(TAPChatManager.getInstance().convertToEntity(messageModel));

                // Send truncated message
                triggerListenerAndSendMessage(messageModel);
            }
        } else {
            TAPMessageModel messageModel = buildTextMessage(textMessage, roomModel, getActiveUser());
            // Send message
            triggerListenerAndSendMessage(messageModel);
        }
        // Run queue after list is updated
        //checkAndSendPendingMessages();
    }

    public void sendDirectReplyTextMessage(String textMessage, TAPRoomModel roomModel) {
        if (!TapTalk.isForeground)
            TAPConnectionManager.getInstance().connect();

        Integer startIndex;
        if (textMessage.length() > CHARACTER_LIMIT) {
            // Message exceeds character limit
            List<TAPMessageEntity> messageEntities = new ArrayList<>();
            Integer length = textMessage.length();
            for (startIndex = 0; startIndex < length; startIndex += CHARACTER_LIMIT) {
                String substr = TAPUtils.getInstance().mySubString(textMessage, startIndex, CHARACTER_LIMIT);
                TAPMessageModel messageModel = buildTextMessage(substr, roomModel, TAPDataManager.getInstance().getActiveUser());
                // Add entity to list
                messageEntities.add(TAPChatManager.getInstance().convertToEntity(messageModel));

                // save LocalID to list of Reply Local IDs
                // gunanya adalah untuk ngecek kapan semua reply message itu udah kekirim atau belom
                // ini kalau misalnya message yang di kirim > character limit
                addReplyMessageLocalID(messageModel.getLocalID());

                // Send truncated message
                triggerListenerAndSendMessage(messageModel);
            }
        } else {
            TAPMessageModel messageModel = buildTextMessage(textMessage, roomModel, TAPDataManager.getInstance().getActiveUser());

            // save LocalID to list of Reply Local IDs
            // gunanya adalah untuk ngecek kapan semua reply message itu udah kekirim atau belom
            // ini kalau misalnya message yang di kirim < character limit (1x dikirim aja)
            addReplyMessageLocalID(messageModel.getLocalID());

            // Send message
            triggerListenerAndSendMessage(messageModel);
        }
        // Run queue after list is updated
        //checkAndSendPendingMessages();
    }

    private TAPMessageModel buildTextMessage(String message, TAPRoomModel room, TAPUserModel user) {
        // Create new TAPMessageModel based on text
        return TAPMessageModel.Builder(
                message,
                room,
                TYPE_TEXT,
                System.currentTimeMillis(),
                user, getOtherUserIdFromActiveRoom(room.getRoomID()));
    }

    /**
     * Send image messages
     */
    public void sendImageMessage(Uri imageUri) {
        // Build message model
        TAPMessageModel messageModel = TAPMessageModel.Builder(
                imageUri.toString(),
                activeRoom,
                TYPE_IMAGE,
                System.currentTimeMillis(),
                activeUser,
                getOtherUserIdFromActiveRoom(activeRoom.getRoomID()));

        // TODO: 8 November 2018 CHECK IMAGE WIDTH/HEIGHT AFTER ENCODE
        // Get image width and height
        String pathName = TAPFileUtils.getInstance().getFilePath(TapTalk.appContext, imageUri);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pathName, options);
        int orientation = TAPFileUtils.getInstance().getImageOrientation(imageUri, TapTalk.appContext);
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            messageModel.setImageWidth(options.outHeight);
            messageModel.setImageHeight(options.outWidth);
        } else {
            messageModel.setImageWidth(options.outWidth);
            messageModel.setImageHeight(options.outHeight);
        }

        // Trigger listener to create temporary image in activity
        triggerSendMessageListener(messageModel);

//        new Thread(() -> {
//            // Encode image to base 64
//            // TODO: 1 November 2018 UPDATE ENCODE METHOD
//            String encodedImage = TAPFileUtils.getInstance().encodeToBase64(imageUri, maxImageSize, activity);
//            messageModel.setBody(encodedImage);
//            // TODO: 31 October 2018 SEND MESSAGE TO SERVER
//        }).start();
    }

    // Send multiple image messages
    public void sendImageMessage (ClipData clipData) {
        int itemSize = clipData.getItemCount();
        for (int i = 0; i < itemSize; i++) {
            sendImageMessage(clipData.getItemAt(i).getUri());
        }
    }

    //sendImageFromList
    public void sendImageMessage(ArrayList<TAPImagePreviewModel> images) {
        for (TAPImagePreviewModel image : images) {
            sendImageMessage(image.getImageUris());
        }
    }

    // Send image without encoding
    public void sendImageMessage(String encodedImage) {
        TAPMessageModel messageModel = TAPMessageModel.Builder(
                encodedImage,
                activeRoom,
                TYPE_IMAGE,
                System.currentTimeMillis(),
                activeUser,
                getOtherUserIdFromActiveRoom(activeRoom.getRoomID()));
        // TODO: 31 October 2018 SEND MESSAGE TO SERVER
        triggerSendMessageListener(messageModel);
    }

    private void triggerSendMessageListener(TAPMessageModel messageModel) {
        if (null != chatListeners && !chatListeners.isEmpty()) {
            for (TAPChatListener chatListener : chatListeners) {
                TAPMessageModel tempNewMessage = messageModel.copyMessageModel();
                // TODO: 8 November 2018 TESTING
                tempNewMessage.setImageWidth(messageModel.getImageWidth());
                tempNewMessage.setImageHeight(messageModel.getImageHeight());
                chatListener.onSendTextMessage(tempNewMessage);
            }
        }
    }

    // Previously sendMessage
    private void triggerListenerAndSendMessage(TAPMessageModel messageModel) {
        // Call listener
        if (null != chatListeners && !chatListeners.isEmpty()) {
            for (TAPChatListener chatListener : chatListeners) {
                TAPMessageModel tempNewMessage = messageModel.copyMessageModel();
                chatListener.onSendTextMessage(tempNewMessage);
            }
        }
        runSendMessageSequence(messageModel);
    }

    /**
     * save text to draft
     */
    public void saveMessageToDraft(String message) {
        messageDrafts.put(getActiveRoom().getRoomID(), message);
    }

    public String getMessageFromDraft() {
        return messageDrafts.get(getActiveRoom().getRoomID());
    }

    public void removeDraft() {
        messageDrafts.remove(getActiveRoom().getRoomID());
    }

    /**
     * send pending messages from queue
     */
    public void checkAndSendPendingMessages() {
        if (!pendingMessages.isEmpty()) {
            TAPMessageModel message = pendingMessages.entrySet().iterator().next().getValue();
            runSendMessageSequence(message);
            pendingMessages.remove(message.getLocalID());
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    checkAndSendPendingMessages();
                }
            }, 50);
        }
    }

    /**
     * Send message to server
     */
    private void runSendMessageSequence(TAPMessageModel messageModel) {
        if (TAPConnectionManager.getInstance().getConnectionStatus() == TAPConnectionManager.ConnectionStatus.CONNECTED) {
            waitingResponses.put(messageModel.getLocalID(), messageModel);

            // Send message if socket is connected
            try {
                sendEmit(kSocketNewMessage, TAPMessageModel.BuilderEncrypt(messageModel));
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            }
        } else {
            // Add message to queue if socket is not connected
            pendingMessages.put(messageModel.getLocalID(), messageModel);
        }
    }

    /**
     * Send Start Typing to Server
     */
    public void sendStartTypingEmit(String roomID) {
        TAPTypingModel typingModel = new TAPTypingModel(roomID);
        sendEmit(kSocketStartTyping, typingModel);
    }

    /**
     * Send Stop Typing to Server
     */
    public void sendStopTypingEmit(String roomID) {
        TAPTypingModel typingModel = new TAPTypingModel(roomID);
        sendEmit(kSocketStopTyping, typingModel);
    }

    /**
     * Send emit to server (Message)
     */
    private void sendEmit(String eventName, TAPMessageModel messageModel) {
        TAPEmitModel<TAPMessageModel> TAPEmitModel;
        TAPEmitModel = new TAPEmitModel<>(eventName, messageModel);
        TAPConnectionManager.getInstance().send(TAPUtils.getInstance().toJsonString(TAPEmitModel));
    }

    /**
     * Send emit to server (Typing)
     */
    private void sendEmit(String eventName, TAPTypingModel typingModel) {
        TAPEmitModel<TAPTypingModel> TAPEmitModel;
        TAPEmitModel = new TAPEmitModel<>(eventName, typingModel);
        TAPConnectionManager.getInstance().send(TAPUtils.getInstance().toJsonString(TAPEmitModel));
    }

    /**
     * update pending status when app enters background and close socket
     */
    public void updateMessageWhenEnterBackground() {
        //saveWaitingMessageToList();
        setPendingRetryAttempt(0);
        isCheckPendingArraySequenceActive = false;
        if (null != scheduler && !scheduler.isShutdown())
            scheduler.shutdown();
        saveUnsentMessage();
        checkPendingBackgroundTask();
    }

    private void insertToList(Map<String, TAPMessageModel> hashMap) {
        for (Map.Entry<String, TAPMessageModel> message : hashMap.entrySet()) {
            saveMessages.add(convertToEntity(message.getValue()));
        }
    }

    private void checkPendingBackgroundTask() {
        // TODO: 05/09/18 nnti cek file manager upload queue juga
        isPendingMessageExist = false;
        isFileUploadExist = false;

        if (0 < pendingMessages.size())
            isPendingMessageExist = true;

        if (isCheckPendingArraySequenceActive)
            return;

        // TODO: 13/09/18 check file progress upload

        if ((isPendingMessageExist || isFileUploadExist) && maxRetryAttempt > pendingRetryAttempt) {
            isCheckPendingArraySequenceActive = true;
            pendingRetryAttempt++;
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    checkPendingBackgroundTask();
                }
            }, pendingRetryInterval);
            isCheckPendingArraySequenceActive = false;
        } else saveIncomingMessageAndDisconnect();
    }

    public void saveIncomingMessageAndDisconnect() {

        TAPConnectionManager.getInstance().close();
        saveUnsentMessage();
        if (null != scheduler && !scheduler.isShutdown())
            scheduler.shutdown();
        isFinishChatFlow = true;
    }

    public void disconnectAfterRefreshTokenExpired() {
        TAPConnectionManager.getInstance().close();
        if (null != scheduler && !scheduler.isShutdown())
            scheduler.shutdown();
        isFinishChatFlow = true;
    }

    public void deleteActiveRoom() {
        activeRoom = null;
    }

    /**
     * this is when receive new message from socket
     *
     * @param newMessage
     */
    private void receiveMessageFromSocket(TAPMessageModel newMessage, String eventName) {
        // Remove from waiting response hashmap
        if (kSocketNewMessage.equals(eventName))
            waitingResponses.remove(newMessage.getLocalID());

        // Insert decrypted message to database
        incomingMessages.put(newMessage.getLocalID(), newMessage);

        // Receive message in active room
        if (null != chatListeners && !chatListeners.isEmpty() && null != activeRoom && newMessage.getRoom().getRoomID().equals(activeRoom.getRoomID())) {
            for (TAPChatListener chatListener : chatListeners) {
                TAPMessageModel tempNewMessage = newMessage.copyMessageModel();
                if (kSocketNewMessage.equals(eventName))
                    chatListener.onReceiveMessageInActiveRoom(tempNewMessage);
                else if (kSocketUpdateMessage.equals(eventName))
                    chatListener.onUpdateMessageInActiveRoom(tempNewMessage);
                else if (kSocketDeleteMessage.equals(eventName))
                    chatListener.onDeleteMessageInActiveRoom(tempNewMessage);
            }
        }
        // Receive message outside active room (not in room List)
        else if (null != chatListeners && !TAPNotificationManager.getInstance().isRoomListAppear() && !chatListeners.isEmpty() && (null == activeRoom || !newMessage.getRoom().getRoomID().equals(activeRoom.getRoomID()))) {
            if (kSocketNewMessage.equals(eventName) && !newMessage.getUser().getUserID().equals(activeUser.getUserID()))
                // Show notification for new messages from other users
                TAPNotificationManager.getInstance().createAndShowInAppNotification(TapTalk.appContext, newMessage);
            for (TAPChatListener chatListener : chatListeners) {
                TAPMessageModel tempNewMessage = newMessage.copyMessageModel();

                if (kSocketNewMessage.equals(eventName))
                    chatListener.onReceiveMessageInOtherRoom(tempNewMessage);
                else if (kSocketUpdateMessage.equals(eventName))
                    chatListener.onUpdateMessageInOtherRoom(tempNewMessage);
                else if (kSocketDeleteMessage.equals(eventName))
                    chatListener.onDeleteMessageInOtherRoom(tempNewMessage);
            }
        }
        // Receive message outside active room (in room List)
        else if (null != chatListeners && !chatListeners.isEmpty() && (null == activeRoom || !newMessage.getRoom().getRoomID().equals(activeRoom.getRoomID()))) {
            for (TAPChatListener chatListener : chatListeners) {
                TAPMessageModel tempNewMessage = newMessage.copyMessageModel();

                if (kSocketNewMessage.equals(eventName))
                    chatListener.onReceiveMessageInOtherRoom(tempNewMessage);
                else if (kSocketUpdateMessage.equals(eventName))
                    chatListener.onUpdateMessageInOtherRoom(tempNewMessage);
                else if (kSocketDeleteMessage.equals(eventName))
                    chatListener.onDeleteMessageInOtherRoom(tempNewMessage);
            }
        }

        // Add to list delivered message
        if (kSocketNewMessage.equals(eventName) && !newMessage.getUser().getUserID().equals(activeUser.getUserID())
                && null != newMessage.getSending() && !newMessage.getSending()
                && null != newMessage.getDelivered() && !newMessage.getDelivered()
                && null != newMessage.getIsRead() && !newMessage.getIsRead()) {
            TAPMessageStatusManager.getInstance().addDeliveredMessageQueue(newMessage);
        }

        // Check the message is from our direct reply or not (in background)
        if (!isReplyMessageLocalIDsEmpty()) {
            removeReplyMessageLocalID(newMessage.getLocalID());
            if (isReplyMessageLocalIDsEmpty()) {
                checkPendingBackgroundTask();
                Toast.makeText(TapTalk.appContext, "Reply Success", Toast.LENGTH_SHORT).show();
            }
        }

        // Save user data to contact manager
        TAPContactManager.getInstance().updateUserDataMap(newMessage.getUser());
    }

    public void saveNewMessageToList() {
        if (0 == incomingMessages.size())
            return;

        insertToList(incomingMessages);
        incomingMessages.clear();
    }

    public void savePendingMessageToList() {
        if (0 < pendingMessages.size()) {
            insertToList(pendingMessages);
        }
    }

    public void saveWaitingMessageToList() {
        if (0 == waitingResponses.size())
            return;

        insertToList(waitingResponses);
        waitingResponses.clear();
    }

    public void triggerSaveNewMessage() {
        if (null == scheduler || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        } else {
            scheduler.shutdown();
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }

        scheduler.scheduleAtFixedRate(() -> {
            saveNewMessageToList();
            saveMessageToDatabase();
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void saveUnsentMessage() {
        saveNewMessageToList();
        savePendingMessageToList();
        saveWaitingMessageToList();
        saveMessageToDatabase();
    }

    public void putUnsentMessageToList() {
        saveNewMessageToList();
        savePendingMessageToList();
        saveWaitingMessageToList();
    }

    public void saveMessageToDatabase() {
        if (0 == saveMessages.size()) return;

        TAPDataManager.getInstance().insertToDatabase(saveMessages, true);
    }

    public List<TAPMessageEntity> getSaveMessages() {
        return saveMessages;
    }

    public void clearSaveMessages() {
        saveMessages.clear();
    }

    private void setPendingRetryAttempt(int counter) {
        pendingRetryAttempt = counter;
    }

    boolean isFinishChatFlow() {
        return isFinishChatFlow;
    }

    public void setFinishChatFlow(boolean finishChatFlow) {
        isFinishChatFlow = finishChatFlow;
    }

    public boolean isNeedToCalledUpdateRoomStatusAPI() {
        return isNeedToCalledUpdateRoomStatusAPI;
    }

    public void setNeedToCalledUpdateRoomStatusAPI(boolean needToCalledUpdateRoomStatusAPI) {
        isNeedToCalledUpdateRoomStatusAPI = needToCalledUpdateRoomStatusAPI;
    }

    private List<String> getReplyMessageLocalIDs() {
        return null == replyMessageLocalIDs ? replyMessageLocalIDs = new ArrayList<>() : replyMessageLocalIDs;
    }

    private void addReplyMessageLocalID(String localID) {
        //masukin local ID ke dalem list kalau misalnya appsnya lagi ga di foreground aja,
        //karena kalau di foreground kita ga boleh matiin socketnya cman krna reply
        if (!TapTalk.isForeground)
            getReplyMessageLocalIDs().add(localID);
    }

    private void removeReplyMessageLocalID(String localID) {
        getReplyMessageLocalIDs().remove(localID);
    }

    private boolean isReplyMessageLocalIDsEmpty() {
        return getReplyMessageLocalIDs().isEmpty();
    }

    public void updateUnreadCountInRoomList(String roomID) {
        new Thread(() -> {
            for (TAPChatListener chatListener : chatListeners) {
                chatListener.onReadMessage(roomID);
            }
        }).start();
    }
}