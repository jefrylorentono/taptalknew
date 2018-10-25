package com.moselo.HomingPigeon.Data.Contact;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import com.moselo.HomingPigeon.Model.HpUserModel;

import java.util.List;

@Dao
public interface HpMyContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(HpUserModel... userModels);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(List<HpUserModel> userModels);

    @Delete
    void delete(HpUserModel... userModels);

    @Delete
    void delete(List<HpUserModel> userModels);

    @Query("delete from mycontact")
    void deleteAllContact();

    @Update
    void update(HpUserModel userModel);

    @Query("select userID, xcUserID, name, thumbnail, fullsize, username, email, phoneNumber, " +
            "userRoleID, roleName, roleIconURL, lastLogin, lastActivity, requireChangePassword, " +
            "created, updated from mycontact order by name asc")
    List<HpUserModel> getAllMyContact();

    @Query("select userID, xcUserID, name, thumbnail, fullsize, username, email, phoneNumber, " +
            "userRoleID, roleName, roleIconURL, lastLogin, lastActivity, requireChangePassword, " +
            "created, updated from mycontact order by name asc")
    LiveData<List<HpUserModel>> getAllMyContactLive();

    @Query("select userID, xcUserID, name, thumbnail, fullsize, username, email, phoneNumber, " +
            "userRoleID, roleName, roleIconURL, lastLogin, lastActivity, requireChangePassword, " +
            "created, updated from mycontact where name like :keyword order by name asc")
    List<HpUserModel> searchAllMyContacts(String keyword);

    @Query("select count(userID) from mycontact where userID like :userID")
    Integer checkUserInMyContacts(String userID);
}
