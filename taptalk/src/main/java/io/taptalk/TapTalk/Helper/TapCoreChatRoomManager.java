package io.taptalk.TapTalk.Helper;

import android.net.Uri;

import java.util.List;

import io.taptalk.TapTalk.API.View.TAPDefaultDataView;
import io.taptalk.TapTalk.Interface.TapCommonInterface;
import io.taptalk.TapTalk.Interface.TapCreateGroupWithPictureInterface;
import io.taptalk.TapTalk.Interface.TapRoomInterface;
import io.taptalk.TapTalk.Listener.TAPDatabaseListener;
import io.taptalk.TapTalk.Manager.TAPChatManager;
import io.taptalk.TapTalk.Manager.TAPContactManager;
import io.taptalk.TapTalk.Manager.TAPDataManager;
import io.taptalk.TapTalk.Manager.TAPFileUploadManager;
import io.taptalk.TapTalk.Manager.TAPGroupManager;
import io.taptalk.TapTalk.Manager.TAPOldDataManager;
import io.taptalk.TapTalk.Model.ResponseModel.TAPCommonResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPCreateRoomResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPGetUserResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPUpdateRoomResponse;
import io.taptalk.TapTalk.Model.TAPErrorModel;
import io.taptalk.TapTalk.Model.TAPRoomModel;
import io.taptalk.TapTalk.Model.TAPUserModel;

import static io.taptalk.TapTalk.Const.TAPDefaultConstant.ApiErrorCode.OTHER_ERRORS;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.RoomType.TYPE_PERSONAL;

public class TapCoreChatRoomManager {

    public static void getPersonalChatRoom(TAPUserModel recipientUser, TapRoomInterface listener) {
        if (null == TAPChatManager.getInstance().getActiveUser()) {
            listener.onError("90001", "Active user not found");
        }
        try {
            TAPRoomModel roomModel = TAPRoomModel.Builder(
                    TAPChatManager.getInstance().arrangeRoomId(
                            TAPChatManager.getInstance().getActiveUser().getUserID(),
                            recipientUser.getUserID()),
                    recipientUser.getName(),
                    TYPE_PERSONAL,
                    recipientUser.getAvatarURL(),
                    ""); // TODO: 1 August 2019 DEFINE ROOM COLOR
            listener.onSuccess(roomModel);
        } catch (Exception e) {
            listener.onError(String.valueOf(OTHER_ERRORS), e.getMessage());
        }
    }

    public static void getPersonalChatRoom(String recipientUserID, TapRoomInterface listener) {
        if (null == TAPChatManager.getInstance().getActiveUser()) {
            listener.onError("90001", "Active user not found");
        }
        TAPUserModel recipient = TAPContactManager.getInstance().getUserData(recipientUserID);
        if (null == recipient) {
            TAPDataManager.getInstance().getUserByIdFromApi(recipientUserID, new TAPDefaultDataView<TAPGetUserResponse>() {
                @Override
                public void onSuccess(TAPGetUserResponse response) {
                    getPersonalChatRoom(response.getUser(), listener);
                }

                @Override
                public void onError(TAPErrorModel error) {
                    listener.onError(error.getCode(), error.getMessage());
                }

                @Override
                public void onError(String errorMessage) {
                    listener.onError(String.valueOf(OTHER_ERRORS), errorMessage);
                }
            });
        } else {
            getPersonalChatRoom(recipient, listener);
        }
    }

    public static void createGroupChatRoom(String groupName, List<String> participantUserIDs, TapRoomInterface listener) {
        TAPDataManager.getInstance().createGroupChatRoom(groupName, participantUserIDs, new TAPDefaultDataView<TAPCreateRoomResponse>() {
            @Override
            public void onSuccess(TAPCreateRoomResponse response) {
                listener.onSuccess(response.getRoom());
            }

            @Override
            public void onError(TAPErrorModel error) {
                listener.onError(error.getCode(), error.getMessage());
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(String.valueOf(OTHER_ERRORS), errorMessage);
            }
        });
    }

    public static void createGroupChatRoomWithPicture(String groupName, List<String> participantUserIDs, Uri groupPictureUri, TapCreateGroupWithPictureInterface listener) {
        TAPDataManager.getInstance().createGroupChatRoom(groupName, participantUserIDs, new TAPDefaultDataView<TAPCreateRoomResponse>() {
            @Override
            public void onSuccess(TAPCreateRoomResponse response) {
                if (null == response.getRoom()) {
                    listener.onSuccess(response.getRoom(), false);
                } else {
                    TAPFileUploadManager.getInstance().uploadRoomPicture(TapTalk.appContext,
                            groupPictureUri, response.getRoom().getRoomID(), new TAPDefaultDataView<TAPUpdateRoomResponse>() {
                                @Override
                                public void onSuccess(TAPUpdateRoomResponse response) {
                                    listener.onSuccess(response.getRoom(), true);
                                }

                                @Override
                                public void onError(TAPErrorModel error) {
                                    listener.onSuccess(response.getRoom(), false);
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    listener.onSuccess(response.getRoom(), false);
                                }
                            });
                }
            }

            @Override
            public void onError(TAPErrorModel error) {
                listener.onError(error.getCode(), error.getMessage());
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(String.valueOf(OTHER_ERRORS), errorMessage);
            }
        });
    }

    public static void updateGroupPicture(String groupRoomID, Uri groupPictureUri, TapRoomInterface listener) {
        TAPFileUploadManager.getInstance().uploadRoomPicture(TapTalk.appContext,
                groupPictureUri, groupRoomID, new TAPDefaultDataView<TAPUpdateRoomResponse>() {
                    @Override
                    public void onSuccess(TAPUpdateRoomResponse response) {
                        listener.onSuccess(response.getRoom());
                    }

                    @Override
                    public void onError(TAPErrorModel error) {
                        listener.onError(error.getCode(), error.getMessage());
                    }

                    @Override
                    public void onError(String errorMessage) {
                        listener.onError(String.valueOf(OTHER_ERRORS), errorMessage);
                    }
                });
    }

    public static void getGroupChatRoom(String groupRoomID, TapRoomInterface listener) {
        TAPRoomModel roomModel = TAPGroupManager.Companion.getGetInstance().getGroupData(groupRoomID);
        if (null == roomModel) {
            TAPDataManager.getInstance().getChatRoomData(groupRoomID, new TAPDefaultDataView<TAPCreateRoomResponse>() {
                @Override
                public void onSuccess(TAPCreateRoomResponse response) {
                    listener.onSuccess(response.getRoom());
                    if (null != response.getRoom()) {
                        TAPGroupManager.Companion.getGetInstance().addGroupData(response.getRoom());
                    }
                }

                @Override
                public void onError(TAPErrorModel error) {
                    listener.onError(error.getCode(), error.getMessage());
                }

                @Override
                public void onError(String errorMessage) {
                    listener.onError(String.valueOf(OTHER_ERRORS), errorMessage);
                }
            });
        } else {
            listener.onSuccess(roomModel);
        }
    }

    public static void deleteGroupChatRoom(TAPRoomModel groupChatRoomModel, TapCommonInterface listener) {
        TAPDataManager.getInstance().deleteChatRoom(groupChatRoomModel, new TAPDefaultDataView<TAPCommonResponse>() {
            @Override
            public void onSuccess(TAPCommonResponse tapCommonResponse, String localID) {
                TAPOldDataManager.getInstance().startCleanRoomPhysicalData(groupChatRoomModel.getRoomID(), new TAPDatabaseListener() {
                    @Override
                    public void onDeleteFinished() {
                        TAPDataManager.getInstance().deleteMessageByRoomId(groupChatRoomModel.getRoomID(), new TAPDatabaseListener() {
                            @Override
                            public void onDeleteFinished() {
                                listener.onSuccess("Chat room deleted successfully");
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(TAPErrorModel error) {
                listener.onError(error.getCode(), error.getMessage());
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(String.valueOf(OTHER_ERRORS), errorMessage);
            }
        });
    }

    public static void leaveGroupChatRoom(String groupRoomID, TapCommonInterface listener) {
        TAPDataManager.getInstance().leaveChatRoom(groupRoomID, new TAPDefaultDataView<TAPCommonResponse>() {
            @Override
            public void onSuccess(TAPCommonResponse response) {
                if (response.getSuccess()) {
                    TAPOldDataManager.getInstance().startCleanRoomPhysicalData(groupRoomID, new TAPDatabaseListener() {
                        @Override
                        public void onDeleteFinished() {
                            TAPDataManager.getInstance().deleteMessageByRoomId(groupRoomID, new TAPDatabaseListener() {
                                @Override
                                public void onDeleteFinished() {
                                    listener.onSuccess("Left chat room successfully ");
                                }
                            });
                        }
                    });
                } else {
                    listener.onError("90001", "Please assign another admin before leaving");
                }
            }

            @Override
            public void onError(TAPErrorModel error) {
                listener.onError(error.getCode(), error.getMessage());
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(String.valueOf(OTHER_ERRORS), errorMessage);
            }
        });
    }

    public static void addGroupChatMembers(String groupRoomID, List<String> userIDs, TapRoomInterface listener) {
        TAPDataManager.getInstance().addRoomParticipant(groupRoomID, userIDs, new TAPDefaultDataView<TAPCreateRoomResponse>() {
            @Override
            public void onSuccess(TAPCreateRoomResponse response) {
                TAPRoomModel room = response.getRoom();
                if (null != room) {
                    room.setGroupParticipants(response.getParticipants());
                    room.setAdmins(response.getAdmins());
                    TAPGroupManager.Companion.getGetInstance().addGroupData(room);
                }
                listener.onSuccess(room);
            }

            @Override
            public void onError(TAPErrorModel error) {
                listener.onError(error.getCode(), error.getMessage());
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(String.valueOf(OTHER_ERRORS), errorMessage);
            }
        });
    }

    public static void removeGroupChatMembers(String groupRoomID, List<String> userIDs, TapRoomInterface listener) {
        TAPDataManager.getInstance().removeRoomParticipant(groupRoomID, userIDs, new TAPDefaultDataView<TAPCreateRoomResponse>() {
            @Override
            public void onSuccess(TAPCreateRoomResponse response) {
                TAPRoomModel room = response.getRoom();
                if (null != room) {
                    room.setGroupParticipants(response.getParticipants());
                    room.setAdmins(response.getAdmins());
                    TAPGroupManager.Companion.getGetInstance().addGroupData(room);
                }
                listener.onSuccess(room);
            }

            @Override
            public void onError(TAPErrorModel error) {
                listener.onError(error.getCode(), error.getMessage());
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(String.valueOf(OTHER_ERRORS), errorMessage);
            }
        });
    }

    public static void promoteGroupAdmins(String groupRoomID, List<String> userIDs, TapRoomInterface listener) {
        TAPDataManager.getInstance().promoteGroupAdmins(groupRoomID, userIDs, new TAPDefaultDataView<TAPCreateRoomResponse>() {
            @Override
            public void onSuccess(TAPCreateRoomResponse response) {
                TAPRoomModel room = response.getRoom();
                if (null != room) {
                    room.setGroupParticipants(response.getParticipants());
                    room.setAdmins(response.getAdmins());
                    TAPGroupManager.Companion.getGetInstance().addGroupData(room);
                }
                listener.onSuccess(room);
            }

            @Override
            public void onError(TAPErrorModel error) {
                listener.onError(error.getCode(), error.getMessage());
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(String.valueOf(OTHER_ERRORS), errorMessage);
            }
        });
    }

    public static void demoteGroupAdmins(String groupRoomID, List<String> userIDs, TapRoomInterface listener) {
        TAPDataManager.getInstance().demoteGroupAdmins(groupRoomID, userIDs, new TAPDefaultDataView<TAPCreateRoomResponse>() {
            @Override
            public void onSuccess(TAPCreateRoomResponse response) {
                TAPRoomModel room = response.getRoom();
                if (null != room) {
                    room.setGroupParticipants(response.getParticipants());
                    room.setAdmins(response.getAdmins());
                    TAPGroupManager.Companion.getGetInstance().addGroupData(room);
                }
                listener.onSuccess(room);
            }

            @Override
            public void onError(TAPErrorModel error) {
                listener.onError(error.getCode(), error.getMessage());
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(String.valueOf(OTHER_ERRORS), errorMessage);
            }
        });
    }

    public static void sendStartTypingEmit(String roomID) {
        TAPChatManager.getInstance().sendStartTypingEmit(roomID);
    }

    public static void sendStopTypingEmit(String roomID) {
        TAPChatManager.getInstance().sendStopTypingEmit(roomID);
    }
}
