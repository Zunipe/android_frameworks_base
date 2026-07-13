package com.zunipe.server;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.zunipe.IVerificationCallback;
import android.zunipe.IVerificationCodeManager;

import com.android.server.SystemService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerificationCodeManagerService extends IVerificationCodeManager.Stub {
    private static final String TAG = "VerificationCodeManagerService";
    private final Context mContext;
    private final RemoteCallbackList<IVerificationCallback> mCallbacks;
    private static final List<String> VERIFICATION_CODE_KEYWORD = new ArrayList<>();
    public static final String CODE_REGEX = "\\d{5,10}";

    public VerificationCodeManagerService(Context context) {
        mContext = context;
        mCallbacks = new RemoteCallbackList<IVerificationCallback>();

        VERIFICATION_CODE_KEYWORD.addAll(Arrays.asList(context.getResources().getStringArray(com.android.internal.R.array.verification_code_keyword)));
    }

    public static class Lifecycle extends SystemService {
        private VerificationCodeManagerService mService;

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            mService = new VerificationCodeManagerService(getContext());
            publishBinderService(Context.VERIFICATION_CODE_SERVICE, mService);
        }
    }

    @Override
    public void onPostNotification(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        String text = (String) extras.getCharSequence(Notification.EXTRA_TEXT);
        Pattern pattern = Pattern.compile(CODE_REGEX);
        Matcher matcher = pattern.matcher(text);
        if (text == null || !matcher.find()) {
            return;
        }

        boolean hasKeyword = false;

        for (String keyword : VERIFICATION_CODE_KEYWORD) {
            Log.d(TAG, "keyword = " + keyword);
            if (text.contains(keyword)) {
                hasKeyword = true;
                break;
            }
        }

        if (hasKeyword) {
            final int n = mCallbacks.beginBroadcast();
            try {
                for (int i = 0; i < n; i++) {
                    try {
                        IVerificationCallback cb = mCallbacks.getBroadcastItem(i);
                        cb.onVerificationCodeComing(matcher.group(0));
                    } catch (RemoteException ignored) {

                    }
                }
            } finally {
                mCallbacks.finishBroadcast();
            }
        }
    }

    @Override
    public void registerCallback(IVerificationCallback callback) {
        mCallbacks.register(callback);
    }

    @Override
    public void unregisterCallback(IVerificationCallback callback) {
        mCallbacks.unregister(callback);
    }
}
