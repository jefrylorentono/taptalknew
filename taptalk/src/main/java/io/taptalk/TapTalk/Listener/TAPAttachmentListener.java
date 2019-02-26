package io.taptalk.TapTalk.Listener;

import io.taptalk.TapTalk.Interface.TapTalkAttachmentInterface;
import io.taptalk.TapTalk.Model.TAPMessageModel;

public abstract class TAPAttachmentListener implements TapTalkAttachmentInterface {
    @Override public void onDocumentSelected() {}
    @Override public void onCameraSelected() {}
    @Override public void onGallerySelected() {}
    @Override public void onAudioSelected() {}
    @Override public void onLocationSelected() {}
    @Override public void onContactSelected() {}
    @Override public void onCopySelected(String text) {}
    @Override public void onReplySelected(TAPMessageModel message) {}
    @Override public void onForwardSelected(TAPMessageModel message) {}
    @Override public void onOpenLinkSelected() {}
    @Override public void onComposeSelected() {}
    @Override public void onPhoneCallSelected() {}
    @Override public void onPhoneSmsSelected() {}
}
