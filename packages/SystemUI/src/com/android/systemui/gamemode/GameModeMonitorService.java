package com.android.systemui.gamemode;

import android.annotation.NonNull;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.TextView;
import android.zunipe.GameModeManager;
import android.zunipe.IPerfMonitorCallback;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.util.settings.SecureSettings;

import javax.inject.Inject;

public class GameModeMonitorService implements CoreStartable {

    public static final String TAG = "GameModeMonitorServices";
    private static final String SETTING_KEY = "show_performance_hud";
    private View mPopupView;
    private final TextView[] mCpuTextViews = new TextView[8];
    private TextView mGpuTextView;
    private boolean mEnable;
    private final int[] mValue = new int[9];

    private final Context mContext;
    private final Handler mHandler;
    private final GameModeManager mGameModeManager;
    private final WindowManager mWindowManager;
    private final InputManager mInputManager;
    private final SecureSettings mSecureSettings;

    private static final String KEY_PERF_POPUP_X = "game_mode_perf_popup_x";
    private static final String KEY_PERF_POPUP_Y = "game_mode_perf_popup_y";

    private final IPerfMonitorCallback mCallback = new IPerfMonitorCallback.Stub() {
        @Override
        public void currentFreqChanging(int[] freqArray) {
            mHandler.post(() -> {
                System.arraycopy(freqArray, 0, mValue, 0, freqArray.length);
                updateFreq();
            });
        }
    };

    @Inject
    public GameModeMonitorService(
            @NonNull @Main Context context,
            GameModeManager GameModeManager,
            WindowManager windowManager,
            InputManager inputManager,
            SecureSettings secureSettings) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        mGameModeManager = GameModeManager;
        mWindowManager = windowManager;
        mInputManager = inputManager;
        mSecureSettings = secureSettings;
    }

    private void updateState() {
        if (mEnable) {
            showPerfPopup();
            mGameModeManager.registerCallback(mCallback);
        } else {
            dismissPopup();
            mGameModeManager.unregisterCallback(mCallback);
        }
    }

    @Override
    public void onBootCompleted() {
        mEnable = mSecureSettings.getBool(SETTING_KEY, false);
        updateState();

        ContentObserver contentObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                mEnable = mSecureSettings.getBool(SETTING_KEY, false);
                updateState();
            }
        };

        mSecureSettings.registerContentObserverForUserAsync(
                mSecureSettings.getUriFor(SETTING_KEY),
                false,
                contentObserver,
                UserHandle.USER_ALL
        );
    }

    @Override
    public void start() {

    }

    private void updateFreq() {
        for (int i = 0; i < 8; i++) {
            if (mCpuTextViews[i] != null) {
                mCpuTextViews[i].setText("CPU" + i + ": " + mValue[i] / 1000 + " MHz");
            }
        }
        if (mGpuTextView != null) {
            mGpuTextView.setText("GPU:" + mValue[8] / 1000000 + " MHz");
        }
    }

    private View createPerfGridView() {
        float density = mContext.getResources().getDisplayMetrics().density;

        GridLayout gridLayout = new GridLayout(mContext);
        gridLayout.setColumnCount(2);
        gridLayout.setPadding((int) (16 * density), (int) (16 * density),
                (int) (16 * density), (int) (16 * density));

        for (int i = 0; i < 8; i++) {
            mCpuTextViews[i] = new TextView(mContext);
            mCpuTextViews[i].setTextColor(Color.GREEN);
            mCpuTextViews[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = (int) (120 * density);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.rightMargin = (int) (16 * density);
            lp.bottomMargin = (int) (8 * density);
            mCpuTextViews[i].setLayoutParams(lp);

            gridLayout.addView(mCpuTextViews[i]);
        }

        mGpuTextView = new TextView(mContext);
        mGpuTextView.setTextColor(Color.CYAN);
        mGpuTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        GridLayout.LayoutParams gpuLp = new GridLayout.LayoutParams();
        gpuLp.rowSpec = GridLayout.spec(4);
        gpuLp.columnSpec = GridLayout.spec(0, 2);
        gpuLp.width = WindowManager.LayoutParams.MATCH_PARENT;
        gpuLp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        gpuLp.topMargin = (int) (8 * density);
        mGpuTextView.setLayoutParams(gpuLp);

        gridLayout.addView(mGpuTextView);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(12 * density);

        bg.setColor(0x66000000);
        bg.setStroke(2, 0xAA575D57);
        gridLayout.setBackground(bg);

        return gridLayout;
    }

    private void showPerfPopup() {
        dismissPopup();

        float density = mContext.getResources().getDisplayMetrics().density;

        final View popupView = createPerfGridView();

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;

        int defaultX = (int) (20 * density);
        int defaultY = (int) (120 * density);

        params.x = Settings.Secure.getInt(mContext.getContentResolver(), KEY_PERF_POPUP_X, defaultX);
        params.y = Settings.Secure.getInt(mContext.getContentResolver(), KEY_PERF_POPUP_Y, defaultY);

        popupView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);

                        params.x = initialX + deltaX;
                        params.y = initialY + deltaY;

                        mWindowManager.updateViewLayout(popupView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        Settings.Secure.putInt(mContext.getContentResolver(), KEY_PERF_POPUP_X, params.x);
                        Settings.Secure.putInt(mContext.getContentResolver(), KEY_PERF_POPUP_Y, params.y);
                        return true;
                }
                return false;
            }
        });

        mPopupView = popupView;
        mWindowManager.addView(popupView, params);
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
}
