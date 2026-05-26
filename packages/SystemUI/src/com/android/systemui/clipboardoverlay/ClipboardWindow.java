package com.android.systemui.clipboardoverlay;

import android.content.ClipData;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.zunipe.IZunipeGestureCallback;
import android.zunipe.ZunipeInputManager;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.res.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

public class ClipboardWindow {
    private final HashMap<Integer, ArrayList<String>> mClipboardMap = new HashMap<>();
    private int mCurrentUser;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mHandler;
    private final ZunipeInputManager mZunipeInputManager;
    private View mClipboardView;
    private boolean isShowing = false;

    private final IZunipeGestureCallback mCallback = new IZunipeGestureCallback.Stub() {
        @Override
        public void onGestureTrigger(int type) {
            mHandler.post(() -> show());
        }
    };

    @Inject
    public ClipboardWindow(Context context,
                           ZunipeInputManager zunipeInputManager,
                           @Main Handler mainHandler) {
        zunipeInputManager.registerCallback(mCallback);
        this.mContext = context;
        this.mZunipeInputManager = zunipeInputManager;
        this.mHandler = mainHandler;
        this.mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        if (isShowing) return;
        float density = mContext.getResources().getDisplayMetrics().density;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                (int) (300 * density),
                (int) (300 * density),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        mClipboardView = LayoutInflater.from(mContext).inflate(R.layout.clipboard_history_layout, null);
        mClipboardView.findViewById(R.id.close_button).setOnClickListener(v -> dismiss());

        mClipboardView.findViewById(R.id.title_bar).setOnTouchListener(new View.OnTouchListener() {
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

                        mWindowManager.updateViewLayout(mClipboardView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:

                        return true;
                }
                return false;
            }
        });
        RecyclerView recyclerView = mClipboardView.findViewById(R.id.clipboard_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(mContext));

        List<String> dataList = getClipDataList();
        ClipboardAdapter adapter = new ClipboardAdapter(dataList);
        recyclerView.setAdapter(adapter);

        params.gravity = Gravity.CENTER;

        mWindowManager.addView(mClipboardView, params);
        isShowing = true;
    }

    private void dismiss() {
        if (mClipboardView != null && mWindowManager != null) {
            try {
                mWindowManager.removeView(mClipboardView);
                isShowing = false;
                mClipboardView = null;
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private List<String> getClipDataList() {
        if (!mClipboardMap.containsKey(mCurrentUser)) {
            ArrayList<String> list = new ArrayList<>();
            mClipboardMap.put(mCurrentUser, list);
            return list;
        }

        return mClipboardMap.get(mCurrentUser);
    }

    public void setUser(int user) {
        dismiss();
        mCurrentUser = user;
    }

    public void insertClipData(ClipData clipData) {
        List<String> list = getClipDataList();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clipData.getItemCount(); ++i) {
            ClipData.Item itemAt = clipData.getItemAt(i);
            sb.append(itemAt.getText());
        }
        String str = sb.toString();
        if (list.contains(str)) {
            list.remove(str);
        } else if (list.size() >= 10) {
            list.removeLast();
        }
        list.addFirst(str);
    }

    private class ClipboardAdapter extends RecyclerView.Adapter<ClipboardAdapter.ViewHolder> {
        private final List<String> mData;

        public ClipboardAdapter(List<String> data) {
            this.mData = data;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            View root;

            ViewHolder(View view) {
                super(view);
                textView = view.findViewById(R.id.clipboard_text);
                root = view;
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_clipboard, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.textView.setText(mData.get(position));
            holder.root.setOnClickListener(v -> {
                mZunipeInputManager.pasteString(mData.get(position));
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }
    }
}
