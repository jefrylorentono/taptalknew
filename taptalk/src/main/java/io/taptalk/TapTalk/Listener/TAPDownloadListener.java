package io.taptalk.TapTalk.Listener;

import android.graphics.Bitmap;
import android.net.Uri;

import io.taptalk.TapTalk.Interface.TapTalkDownloadInterface;

public abstract class TAPDownloadListener implements TapTalkDownloadInterface {
    @Override public void onImageDownloadProcessFinished(String localID, Bitmap bitmap) {}
    @Override public void onThumbnailDownloaded(String fileID, Bitmap bitmap) {}
    @Override public void onFileDownloadProcessFinished(String localID, Uri fileUri) {}
    @Override public void onDownloadFailed(String localID) {}
}
