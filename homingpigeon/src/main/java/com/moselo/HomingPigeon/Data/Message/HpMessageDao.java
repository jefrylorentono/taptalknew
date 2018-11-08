package com.moselo.HomingPigeon.Data.Message;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.List;

import retrofit2.http.DELETE;

import static com.moselo.HomingPigeon.Helper.HpDefaultConstant.NUM_OF_ITEM;

@Dao
public interface HpMessageDao {

    int numOfItem = NUM_OF_ITEM;

    @DELETE
    void delete(List<HpMessageEntity> messageEntities);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(HpMessageEntity messageEntity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(List<HpMessageEntity> messageEntities);

    @Query("delete from Message_Table where localID = :localId")
    void delete(String localId);

    @Query("delete from message_table")
    void deleteAllMessage();

    @Query("select * from Message_Table order by created desc")
    LiveData<List<HpMessageEntity>> getAllMessage();

    @Query("select messageID, localID, roomID, roomName, roomColor, roomType," +
            " roomImage, type, body, created, " +
            "userID, xcUserID, userFullName, userImage, username, userEmail, " +
            "userPhone, userRole, lastLogin, requireChangePassword, userCreated, userUpdated, " +
            "recipientID, isRead, isDelivered, isHidden, isDeleted, isSending, isFailedSend" +
            " from Message_Table where RoomID like :roomID order by created desc limit " + numOfItem)
    List<HpMessageEntity> getAllMessageListDesc(String roomID);

    @Query("select messageID, localID, roomID, roomName, roomColor, roomType," +
            " roomImage, type, body, created, " +
            "userID, xcUserID, userFullName, userImage, username, userEmail, " +
            "userPhone, userRole, lastLogin, requireChangePassword, userCreated, userUpdated, " +
            "recipientID, isRead, isDelivered, isHidden, isDeleted, isSending, isFailedSend" +
            " from Message_Table where RoomID like :roomID order by created desc limit " + numOfItem)
    List<HpMessageEntity> getAllMessageListAsc(String roomID);

    @Query("select messageID, localID, roomID, roomName, roomColor, roomType, " +
            "roomImage, type, body, created, " +
            "userID, xcUserID, userFullName, userImage, username, userEmail, " +
            "userPhone, userRole, lastLogin, requireChangePassword, userCreated, userUpdated, " +
            "recipientID, isRead, isDelivered, isHidden, isDeleted, isSending, isFailedSend " +
            "from Message_Table where " +
            "created in (select distinct created from Message_Table where created < :lastTimestamp and RoomID like :roomID order by created desc limit " + numOfItem + " ) " +
            "and RoomID like :roomID order by created desc")
    List<HpMessageEntity> getAllMessageTimeStamp(Long lastTimestamp, String roomID);

    @Query("select messageID, localID, roomID, roomName, roomColor, roomType," +
            " roomImage, type, body, created, " +
            "userID, xcUserID, userFullName, userImage, username, userEmail, " +
            "userPhone, userRole, lastLogin, requireChangePassword, userCreated, userUpdated, " +
            "recipientID, isRead, isDelivered, isHidden, isDeleted, isSending, isFailedSend" +
            " from Message_Table where body like :keyword order by created desc")
    List<HpMessageEntity> searchAllMessages(String keyword);

    @Query("select localID, userID, xcUserID, userFullName, userImage, username, userEmail, " +
            "userPhone, userRole, lastLogin, requireChangePassword, userCreated, userUpdated, " +
            "roomName, secondQuery.roomID, roomType, body, created, isDelivered, isFailedSend, " +
            "isSending, recipientID, type from (select roomID, max(created) as max_created from Message_Table group by roomID) secondQuery join Message_Table firstQuery on firstQuery.roomID = secondQuery.roomID and firstQuery.created = secondQuery.max_created order by firstQuery.created desc")
    List<HpMessageEntity> getAllRoomList();

    @Query("select localID, userID, xcUserID, userFullName, userImage, username, userEmail, " +
            "userPhone, userRole, lastLogin, requireChangePassword, userCreated, userUpdated, " +
            "roomName, secondQuery.roomID, roomType, body, created, isDelivered, isFailedSend, " +
            "isSending, recipientID, type from (select roomID, max(created) as max_created from Message_Table group by roomID) " +
            "secondQuery join Message_Table firstQuery on firstQuery.roomID = secondQuery.roomID and firstQuery.created = secondQuery.max_created where roomName like :keyword order by firstQuery.created desc")
    List<HpMessageEntity> searchAllChatRooms(String keyword);

    @Query("select count(isRead) from message_table where isRead = 0 and RoomID like :roomID and userID not like :userID")
//    @Query("select count(isSending) from message_table where isSending = 0 and RoomID like :roomID and userID not like :userID")
    Integer getUnreadCount(String userID, String roomID);

    @Query("update Message_Table set isFailedSend = 1, isSending = 0 where isSending = 1")
    void updatePendingStatus();

    @Query("update Message_Table set isFailedSend = 1, isSending = 0 where localID = :localID")
    void updatePendingStatus(String localID);
}
