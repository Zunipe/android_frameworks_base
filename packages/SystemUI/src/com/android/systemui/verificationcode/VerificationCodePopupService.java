package com.android.systemui.verificationcode;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.zunipe.IVerificationCallback;
import android.zunipe.VerificationCodeManager;
import android.zunipe.ZunipeInputManager;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.qualifiers.Main;

import javax.inject.Inject;

public class VerificationCodePopupService implements CoreStartable {
    public static final String TAG = "VerificationCodePopupService";
    private final Context mContext;
    private final VerificationCodeManager mVerificationCodeManager;
    private final IVerificationCallback mCallback;
    private final Handler mHandler;

    private View mPopupView;
    private final WindowManager mWindowManager;
    private final ZunipeInputManager mZunipeInputManager;

    @Inject
    public VerificationCodePopupService(@NonNull @Main Context context,
                                        VerificationCodeManager verificationCodeManager,
                                        WindowManager windowManager,
                                        ZunipeInputManager zunipeInputManager,
                                        @Main Handler mainHandler) {
        mContext = context;
        mHandler = mainHandler;
        mVerificationCodeManager = verificationCodeManager;
        mWindowManager = windowManager;
        mZunipeInputManager = zunipeInputManager;
        mCallback = new IVerificationCallback.Stub() {
            @Override
            public void onVerificationCodeComing(String code) {
                mHandler.post(() -> {
                    if (mPopupView == null) {
                        showCodePopup(code);

                        mHandler.postDelayed(() -> {
                            dismissPopup();
                        }, 5000);
                    }
                });
            }
        };
    }

    private TextView getPopupView(String code) {
        float density = mContext.getResources().getDisplayMetrics().density;

        TextView popup = new TextView(mContext);
        popup.setText(code);
        popup.setTextColor(Color.BLACK);
        popup.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        popup.setGravity(Gravity.CENTER);
        popup.setPadding((int) (16 * density), (int) (16 * density),
                (int) (16 * density), (int) (16 * density));
        popup.setClickable(true);
        popup.setFocusable(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(40 * density);
        bg.setColor(0xFFF1F1F1);
        bg.setStroke(2, 0xFF575D57);

        popup.setBackground(bg);

        popup.setOnClickListener(v -> {
            String verificationCode = code == null ? "" : code.trim();
            mZunipeInputManager.pasteString(verificationCode);
        });
        return popup;
    }

    private void showCodePopup(String code) {
        dismissPopup();

        float density = mContext.getResources().getDisplayMetrics().density;

        TextView popup = getPopupView(code);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = (int) (50 * density);
        mPopupView = popup;

        mWindowManager.addView(popup, params);
    }

    private void dismissPopup() {
        if (mPopupView != null && mWindowManager != null) {
            try {
                mWindowManager.removeView(mPopupView);
            } catch (IllegalArgumentException ignored) {
            }
            mPopupView = null;
        }
    }

    @Override
    public void start() {
        if (mVerificationCodeManager != null) {
            Log.d(TAG, "start registerCallback");
            mVerificationCodeManager.registerCallback(mCallback);
        } else {
            Log.d(TAG, "mVerificationCodeManager is null");
        }
    }
}
