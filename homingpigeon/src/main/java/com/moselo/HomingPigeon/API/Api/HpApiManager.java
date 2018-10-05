package com.moselo.HomingPigeon.API.Api;

import android.util.Log;

import com.moselo.HomingPigeon.API.BaseResponse;
import com.moselo.HomingPigeon.BuildConfig;
import com.moselo.HomingPigeon.Exception.ApiSessionExpiredException;
import com.moselo.HomingPigeon.Helper.HpDefaultConstant;
import com.moselo.HomingPigeon.Model.RequestModel.CommonRequest;
import com.moselo.HomingPigeon.Model.ResponseModel.AuthTicketResponse;
import com.moselo.HomingPigeon.Model.ResponseModel.GetAccessTokenResponse;
import com.moselo.HomingPigeon.Model.RequestModel.AuthTicketRequest;
import com.moselo.HomingPigeon.Model.ResponseModel.GetRoomListResponse;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class HpApiManager {
    private static final String TAG = HpApiManager.class.getSimpleName();
    private HomingPigeonApiService homingPigeon;
    private static HpApiManager instance;

    public static HpApiManager getInstance() {
        return instance == null ? instance = new HpApiManager() : instance;
    }

    private HpApiManager() {
        HpApiConnection connection = HpApiConnection.getInstance();
        this.homingPigeon = connection.getHomingPigeon();
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
                .flatMap((Func1<T, Observable<T>>) t -> validateResponse(t))
                .retryWhen(o1 -> o1.flatMap((Func1<Throwable, Observable<?>>) t -> validateException(t)))
                .subscribe(s);
    }

    private <T> Observable validateResponse(T t) {
        BaseResponse br = (BaseResponse) t;

//        if (br.getRefreshToken() != null && br.getRefreshToken() != null)
//            updateSession((BaseResponse) t);

        if (br.getError() != null) {
            String code = br.getError().getCode();
            if (BuildConfig.DEBUG)
                Log.e(TAG, "validateResponse: XX HAS ERROR XX: __error_code:" + code);

//            if (code == ERR_FORBIDDEN)
//                return raiseApiTokenException(br);
//            else if (code == ERR_TOKEN_EXPIRED /*|| code == ERR_UNAUTHORIZED*/ || code == OK_ZOMBIE)
//                return raiseApiSessionExpiredException(br);
            if (code.equals(HpDefaultConstant.HttpResponseStatusCode.RESPONSE_SUCCESS) && BuildConfig.DEBUG)
                    Log.e(TAG, "validateResponse: √√ NO ERROR √√");
            //else if ()
        }
        return Observable.just(t);
    }

    private Observable validateException(Throwable t) {
        Log.e(TAG, "call: retryWhen(), cause: " + t.getMessage());
        t.printStackTrace();
//        return (t instanceof ApiTokenException)
//                ? createApiToken() : t instanceof ApiSessionExpiredException
//                ? refreshToken() : Observable.error(t);
        return Observable.error(t);
    }

    private Observable<Throwable> raiseApiSessionExpiredException(BaseResponse br) {
        return Observable.error(new ApiSessionExpiredException(br.getError().getMessage()));
    }

    public void getAuthTicket(String ipAddress, String userAgent, String userPlatform, String userDeviceID, String xcUserID
            , String fullname, String email, String phone, String username, Subscriber<BaseResponse<AuthTicketResponse>> subscriber) {
        AuthTicketRequest request = AuthTicketRequest.toBuilder(ipAddress, userAgent, userPlatform, userDeviceID, xcUserID,
                fullname, email, phone, username);
        execute(homingPigeon.getAuthTicket(request), subscriber);
    }

    public void getAccessToken(Subscriber<BaseResponse<GetAccessTokenResponse>> subscriber) {
        execute(homingPigeon.getAccessToken(), subscriber);
    }

    public void refreshAccessToken(Subscriber<BaseResponse<GetAccessTokenResponse>> subscriber) {
        execute(homingPigeon.refreshAccessToken(), subscriber);
    }

    public void getRoomList(String userID, Subscriber<BaseResponse<GetRoomListResponse>> subscriber) {
        CommonRequest request = CommonRequest.builderWithUserID(userID);
        execute(homingPigeon.getRoomList(request), subscriber);
    }

}
