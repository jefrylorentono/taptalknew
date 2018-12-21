package io.taptalk.TapTalk.API.Api;

import android.util.Log;

import java.util.List;

import io.taptalk.TapTalk.API.Service.TAPTalkApiService;
import io.taptalk.TapTalk.API.Service.TAPTalkRefreshTokenService;
import io.taptalk.TapTalk.API.Service.TAPTalkSocketService;
import io.taptalk.TapTalk.Exception.TAPApiRefreshTokenRunningException;
import io.taptalk.TapTalk.Exception.TAPApiSessionExpiredException;
import io.taptalk.TapTalk.Exception.TAPAuthException;
import io.taptalk.TapTalk.Helper.TapTalk;
import io.taptalk.TapTalk.Manager.TAPDataManager;
import io.taptalk.TapTalk.Model.RequestModel.TAPAuthTicketRequest;
import io.taptalk.TapTalk.Model.RequestModel.TAPCommonRequest;
import io.taptalk.TapTalk.Model.RequestModel.TAPGetMessageListbyRoomAfterRequest;
import io.taptalk.TapTalk.Model.RequestModel.TAPGetMessageListbyRoomBeforeRequest;
import io.taptalk.TapTalk.Model.RequestModel.TAPGetUserByIdRequest;
import io.taptalk.TapTalk.Model.RequestModel.TAPGetUserByUsernameRequest;
import io.taptalk.TapTalk.Model.RequestModel.TAPGetUserByXcUserIdRequest;
import io.taptalk.TapTalk.Model.RequestModel.TAPPushNotificationRequest;
import io.taptalk.TapTalk.Model.RequestModel.TAPSendCustomMessageRequest;
import io.taptalk.TapTalk.Model.RequestModel.TAPUpdateMessageStatusRequest;
import io.taptalk.TapTalk.Model.RequestModel.TAPUserIdRequest;
import io.taptalk.TapTalk.Model.ResponseModel.TAPAuthTicketResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPBaseResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPCommonResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPContactResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPGetAccessTokenResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPGetMessageListbyRoomResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPGetRoomListResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPGetUserResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPSendCustomMessageResponse;
import io.taptalk.TapTalk.Model.ResponseModel.TAPUpdateMessageStatusResponse;
import io.taptalk.TapTalk.Model.TAPErrorModel;
import io.taptalk.Taptalk.BuildConfig;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static io.taptalk.TapTalk.Const.TAPDefaultConstant.HttpResponseStatusCode.RESPONSE_SUCCESS;
import static io.taptalk.TapTalk.Const.TAPDefaultConstant.HttpResponseStatusCode.UNAUTHORIZED;

public class TAPApiManager {
    private static final String TAG = TAPApiManager.class.getSimpleName();
    private TAPTalkApiService homingPigeon;
    private TAPTalkSocketService hpSocket;
    private TAPTalkRefreshTokenService hpRefresh;
    private static TAPApiManager instance;
    private int isShouldRefreshToken = 0;
    //ini flagging jadi kalau logout (refresh token expired) dy ga akan ngulang2 manggil api krna 401
    private boolean isLogout = false;

    public static TAPApiManager getInstance() {
        return instance == null ? instance = new TAPApiManager() : instance;
    }

    private TAPApiManager() {
        TAPApiConnection connection = TAPApiConnection.getInstance();
        this.homingPigeon = connection.getHomingPigeon();
        this.hpSocket = connection.getHpValidate();
        this.hpRefresh = connection.getHpRefresh();
    }

    public boolean isLogout() {
        return isLogout;
    }

    public void setLogout(boolean logout) {
        isLogout = logout;
    }

    private Observable.Transformer ioToMainThreadSchedulerTransformer
            = createIOMainThreadScheduler();

    private <T> Observable.Transformer<T, T> createIOMainThreadScheduler() {
        return tObservable -> tObservable.subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressWarnings("unchecked")
    private <T> Observable.Transformer<T, T> applyIOMainThreadSchedulers() {
        return ioToMainThreadSchedulerTransformer;
    }

    @SuppressWarnings("unchecked")
    private <T> void execute(Observable<? extends T> o, Subscriber<T> s) {
        o.compose((Observable.Transformer<T, T>) applyIOMainThreadSchedulers())
                .flatMap((Func1<T, Observable<T>>) this::validateResponse)
                .retryWhen(o1 -> o1.flatMap((Func1<Throwable, Observable<?>>) this::validateException))
                .subscribe(s);
    }

    private <T> Observable validateResponse(T t) {
        TAPBaseResponse br = (TAPBaseResponse) t;

        int code = br.getStatus();
        if (BuildConfig.DEBUG)
            Log.e(TAG, "validateResponse: XX HAS ERROR XX: __error_code:" + code);

        if (code == RESPONSE_SUCCESS && BuildConfig.DEBUG)
            Log.e(TAG, "validateResponse: √√ NO ERROR √√");
        else if (code == UNAUTHORIZED && 0 < isShouldRefreshToken) {
            return raiseApiRefreshTokenRunningException();
        } else if (code == UNAUTHORIZED) {
            isShouldRefreshToken++;
            return raiseApiSessionExpiredException(br);
        }
        isShouldRefreshToken = 0;
        return Observable.just(t);
    }

    private Observable validateException(Throwable t) {
        Log.e(TAG, "call: retryWhen(), cause: " + t.getMessage());
        return (t instanceof TAPApiSessionExpiredException && 1 == isShouldRefreshToken && !isLogout) ? refreshToken() :
                ((t instanceof TAPApiRefreshTokenRunningException || (t instanceof TAPApiSessionExpiredException && 1 < isShouldRefreshToken)) && !isLogout) ?
                       Observable.just(Boolean.TRUE) : Observable.error(t);
    }

    private Observable<Throwable> raiseApiSessionExpiredException(TAPBaseResponse br) {
        return Observable.error(new TAPApiSessionExpiredException(br.getError().getMessage()));
    }

    private Observable<Throwable> raiseApiRefreshTokenRunningException() {
        return Observable.error(new TAPApiRefreshTokenRunningException());
    }

    private void updateSession(TAPBaseResponse<TAPGetAccessTokenResponse> r) {
        TAPDataManager.getInstance().saveAccessToken(r.getData().getAccessToken());
        TAPDataManager.getInstance().saveAccessTokenExpiry(r.getData().getAccessTokenExpiry());
        TAPDataManager.getInstance().saveRefreshToken(r.getData().getRefreshToken());
        TAPDataManager.getInstance().saveRefreshTokenExpiry(r.getData().getRefreshTokenExpiry());

        TAPDataManager.getInstance().saveActiveUser(r.getData().getUser());
    }

    public void getAuthTicket(String ipAddress, String userAgent, String userPlatform, String userDeviceID, String xcUserID
            , String fullname, String email, String phone, String username, Subscriber<TAPBaseResponse<TAPAuthTicketResponse>> subscriber) {
        TAPAuthTicketRequest request = TAPAuthTicketRequest.toBuilder(ipAddress, userAgent, userPlatform, userDeviceID, xcUserID,
                fullname, email, phone, username);
        execute(homingPigeon.getAuthTicket(request), subscriber);
    }

    public void sendCustomMessage(Integer messageType, String body, String filterID, String senderUserID, String recipientUserID, Subscriber<TAPBaseResponse<TAPSendCustomMessageResponse>> subscriber) {
        TAPSendCustomMessageRequest request = new TAPSendCustomMessageRequest(messageType, body, filterID, senderUserID, recipientUserID);
        execute(homingPigeon.sendCustomMessage(request), subscriber);
    }

    public void getAccessToken(Subscriber<TAPBaseResponse<TAPGetAccessTokenResponse>> subscriber) {
        execute(homingPigeon.getAccessToken(), subscriber);
    }

    public Observable<TAPBaseResponse<TAPGetAccessTokenResponse>> refreshToken() {
        return hpRefresh.refreshAccessToken()
                .compose(this.applyIOMainThreadSchedulers())
                .doOnNext(response -> {
                    Log.e(TAG, "call: retryWhen(), cause: 2 "+response.getStatus() );
                    if (RESPONSE_SUCCESS == response.getStatus()) {
                        updateSession(response);
                        Observable.error(new TAPAuthException(response.getError().getMessage()));
                    } else if (UNAUTHORIZED == response.getStatus()){
                        TapTalk.refreshTokenExpired();
                    } else Observable.error(new TAPAuthException(response.getError().getMessage()));
                }).doOnError(throwable -> {

                });
    }

    public void refreshAccessToken(Subscriber<TAPBaseResponse<TAPGetAccessTokenResponse>> subscriber) {
        execute(hpRefresh.refreshAccessToken(), subscriber);
    }

    public void validateAccessToken(Subscriber<TAPBaseResponse<TAPErrorModel>> subscriber) {
        execute(hpSocket.validateAccessToken(), subscriber);
    }

    public void registerFcmTokenToServer(String fcmToken, Subscriber<TAPBaseResponse<TAPCommonResponse>> subscriber) {
        TAPPushNotificationRequest request = TAPPushNotificationRequest.Builder(fcmToken);
        execute(homingPigeon.registerFcmTokenToServer(request), subscriber);
    }

    public void getRoomList(String userID, Subscriber<TAPBaseResponse<TAPGetRoomListResponse>> subscriber) {
        TAPCommonRequest request = TAPCommonRequest.builderWithUserID(userID);
        execute(homingPigeon.getRoomList(request), subscriber);
    }

    public void getPendingAndUpdatedMessage(Subscriber<TAPBaseResponse<TAPGetRoomListResponse>> subscriber) {
        execute(homingPigeon.getPendingAndUpdatedMessage(), subscriber);
    }

    public void getMessageListByRoomAfter(String roomID, Long minCreated, Long lastUpdated, Subscriber<TAPBaseResponse<TAPGetMessageListbyRoomResponse>> subscriber) {
        TAPGetMessageListbyRoomAfterRequest request = new TAPGetMessageListbyRoomAfterRequest(roomID, minCreated, lastUpdated);
        execute(homingPigeon.getMessageListByRoomAfter(request), subscriber);
    }

    public void getMessageListByRoomBefore(String roomID, Long maxCreated, Subscriber<TAPBaseResponse<TAPGetMessageListbyRoomResponse>> subscriber) {
        TAPGetMessageListbyRoomBeforeRequest request = new TAPGetMessageListbyRoomBeforeRequest(roomID, maxCreated);
        execute(homingPigeon.getMessageListByRoomBefore(request), subscriber);
    }

    public void updateMessageStatusAsDelivered(List<String> messageIDs, Subscriber<TAPBaseResponse<TAPUpdateMessageStatusResponse>> subscriber) {
        TAPUpdateMessageStatusRequest request = new TAPUpdateMessageStatusRequest(messageIDs);
        execute(homingPigeon.updateMessageStatusAsDelivered(request), subscriber);
    }

    public void updateMessageStatusAsRead(List<String> messageIDs, Subscriber<TAPBaseResponse<TAPUpdateMessageStatusResponse>> subscriber) {
        TAPUpdateMessageStatusRequest request = new TAPUpdateMessageStatusRequest(messageIDs);
        execute(homingPigeon.updateMessageStatusAsRead(request), subscriber);
    }

    public void getMyContactListFromAPI(Subscriber<TAPBaseResponse<TAPContactResponse>> subscriber) {
        execute(homingPigeon.getMyContactListFromAPI(), subscriber);
    }

    public void addContact(String userID, Subscriber<TAPBaseResponse<TAPCommonResponse>> subscriber) {
        TAPUserIdRequest request = new TAPUserIdRequest(userID);
        execute(homingPigeon.addContact(request), subscriber);
    }

    public void removeContact(String userID, Subscriber<TAPBaseResponse<TAPCommonResponse>> subscriber) {
        TAPUserIdRequest request = new TAPUserIdRequest(userID);
        execute(homingPigeon.removeContact(request), subscriber);
    }

    public void getUserByID(String id, Subscriber<TAPBaseResponse<TAPGetUserResponse>> subscriber) {
        TAPGetUserByIdRequest request = new TAPGetUserByIdRequest(id);
        execute(homingPigeon.getUserByID(request), subscriber);
    }

    public void getUserByXcUserID(String xcUserID, Subscriber<TAPBaseResponse<TAPGetUserResponse>> subscriber) {
        TAPGetUserByXcUserIdRequest request = new TAPGetUserByXcUserIdRequest(xcUserID);
        execute(homingPigeon.getUserByXcUserID(request), subscriber);
    }

    public void getUserByUsername(String username, Subscriber<TAPBaseResponse<TAPGetUserResponse>> subscriber) {
        TAPGetUserByUsernameRequest request = new TAPGetUserByUsernameRequest(username);
        execute(homingPigeon.getUserByUsername(request), subscriber);
    }
}