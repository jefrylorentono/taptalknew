package io.taptalk.TapTalk.Manager;

import android.util.Log;

import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import io.taptalk.TapTalk.Helper.TapTalk;

public class AnalyticsManager {

    private static AnalyticsManager instance;
    private MixpanelAPI mixpanel;

    public static AnalyticsManager getInstance() {
        return instance == null ? (instance = new AnalyticsManager()) : instance;
    }

    public void identifyUser() {
        if (null != TAPChatManager.getInstance().getActiveUser()) {
            Log.e("]]]]", "User Identified");
            mixpanel = MixpanelAPI.getInstance(TapTalk.appContext, "84f4d93bf3c34abe56fac7b2faaaa8b1");
            mixpanel.identify(TAPChatManager.getInstance().getActiveUser().getUserID());
            mixpanel.getPeople().identify(TAPChatManager.getInstance().getActiveUser().getUserID());
            mixpanel.getPeople().set("UserID", TAPChatManager.getInstance().getActiveUser().getUserID());
            mixpanel.getPeople().set("UserFullName", TAPChatManager.getInstance().getActiveUser().getName());
            mixpanel.getPeople().set("userPhoneNumber", TAPChatManager.getInstance().getActiveUser().getPhoneNumber());
        }
    }

    public void trackActiveUser() {
        mixpanel = MixpanelAPI.getInstance(TapTalk.appContext, "84f4d93bf3c34abe56fac7b2faaaa8b1");
        JSONObject metadata = generateDefaultData();
        mixpanel.track("Daily Active Users (DAU)", metadata);
    }

    public void trackEvent(String keyEvent) {
        mixpanel = MixpanelAPI.getInstance(TapTalk.appContext, "84f4d93bf3c34abe56fac7b2faaaa8b1");
        JSONObject metadata = generateDefaultData();
        mixpanel.track(keyEvent, metadata);
    }

    public void trackEvent(String keyEvent, @Nullable HashMap<String, String> additional) {
        mixpanel = MixpanelAPI.getInstance(TapTalk.appContext, "84f4d93bf3c34abe56fac7b2faaaa8b1");
        JSONObject metadata = generateDefaultData();
        if (null != additional) {
            for (Map.Entry<String, String> add : additional.entrySet()) {
                try {
                    metadata.put(add.getKey(), add.getValue());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        mixpanel.track(keyEvent, metadata);
    }

    public void trackErrorEvent(String keyEvent, String errorCode, String errorMessage) {
        mixpanel = MixpanelAPI.getInstance(TapTalk.appContext, "84f4d93bf3c34abe56fac7b2faaaa8b1");
        JSONObject metadata = generateDefaultData();
        try {
            metadata.put("errorCode", errorCode);
            metadata.put("errorMessage", errorMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mixpanel.track(keyEvent, metadata);
    }

    public void trackErrorEvent(String keyEvent, String errorCode, String errorMessage, @Nullable HashMap<String, String> additional) {
        mixpanel = MixpanelAPI.getInstance(TapTalk.appContext, "84f4d93bf3c34abe56fac7b2faaaa8b1");
        JSONObject metadata = generateDefaultData();
        try {
            metadata.put("errorCode", errorCode);
            metadata.put("errorMessage", errorMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (null != additional) {
            for (Map.Entry<String, String> add : additional.entrySet()) {
                try {
                    metadata.put(add.getKey(), add.getValue());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        mixpanel.track(keyEvent, metadata);
    }

    private JSONObject generateDefaultData() {
        JSONObject metadata = new JSONObject();
        try {
            metadata.put("UserID", TAPChatManager.getInstance().getActiveUser().getUserID());
            metadata.put("UserFullName", TAPChatManager.getInstance().getActiveUser().getName());
            metadata.put("userPhoneNumber", TAPChatManager.getInstance().getActiveUser().getPhoneNumber());
        } catch (Exception e) {
            return metadata;
        }
        return metadata;
    }
}
