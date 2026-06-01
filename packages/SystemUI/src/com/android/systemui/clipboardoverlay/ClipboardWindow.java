package com.android.systemui.clipboardoverlay;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.zunipe.IZunipeGestureCallback;
import android.zunipe.ZunipeInputManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.gamemode.DraggableWindowsHelper;
import com.android.systemui.res.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

public class ClipboardWindow {
    private final HashMap<Integer, ArrayList<String>> mClipboardMap = new HashMap<>();
    private final Context mContext;
    private final DraggableWindowsHelper mDraggableWindowsHelper;
    private final Handler mHandler;
    private final ZunipeInputManager mZunipeInputManager;
    private int mCurrentUser;
    private View mEmptyView;
    private ClipboardAdapter mAdapter;

    private final IZunipeGestureCallback mCallback = new IZunipeGestureCallback.Stub() {
        @Override
        public void onGestureTrigger(int type) {
            mHandler.post(() -> show());
        }
    };

    @Inject
    public ClipboardWindow(Context context,
                           ZunipeInputManager zunipeInputManager,
                           @Main Handler mainHandler,
                           DraggableWindowsHelper draggableWindowsHelper) {
        zunipeInputManager.registerCallback(mCallback);
        this.mContext = context;
        this.mZunipeInputManager = zunipeInputManager;
        this.mHandler = mainHandler;
        this.mDraggableWindowsHelper = draggableWindowsHelper;
    }

    public void show() {
        if (mDraggableWindowsHelper.isShowing()) return;
        float density = mContext.getResources().getDisplayMetrics().density;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                (int) (300 * density),
                (int) (300 * density),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        Context themedContext = new ContextThemeWrapper(mContext, R.style.Theme_SystemUI);
        View clipboardView = LayoutInflater.from(themedContext).inflate(R.layout.clipboard_history_layout, null);
        clipboardView.findViewById(R.id.close_button).setOnClickListener(v -> dismiss());

        RecyclerView recyclerView = clipboardView.findViewById(R.id.clipboard_recycler_view);
        mEmptyView = clipboardView.findViewById(R.id.empty_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(mContext));

        List<String> dataList = getClipDataList();
        mAdapter = new ClipboardAdapter(dataList);
        mAdapter.setOnDataChangedListener(this::updateEmptyState);
        recyclerView.setAdapter(mAdapter);

        attachSwipeToDelete(recyclerView);
        updateEmptyState();
        mDraggableWindowsHelper.show(params, clipboardView);
    }

    private void attachSwipeToDelete(RecyclerView recyclerView) {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    mAdapter.removeItem(position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX,
                                    float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    getForegroundCard(viewHolder).setTranslationX(dX);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                getForegroundCard(viewHolder).setTranslationX(0f);
                super.clearView(recyclerView, viewHolder);
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    private View getForegroundCard(RecyclerView.ViewHolder viewHolder) {
        return viewHolder.itemView.findViewById(R.id.clipboard_item_card);
    }

    private void updateEmptyState() {
        mEmptyView.setVisibility(mAdapter.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void dismiss() {
        try {
            if (mDraggableWindowsHelper.dismiss()) {
                mAdapter = null;
                mEmptyView = null;
            }
        } catch (IllegalArgumentException ignored) {
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
        public interface OnDataChangedListener {
            void onDataChanged();
        }

        private final List<String> mData;
        private OnDataChangedListener mListener;

        public ClipboardAdapter(List<String> data) {
            this.mData = data;
        }

        public void setOnDataChangedListener(OnDataChangedListener listener) {
            mListener = listener;
        }

        public void removeItem(int position) {
            if (position < 0 || position >= mData.size()) {
                return;
            }
            mData.remove(position);
            notifyItemRemoved(position);
            if (mListener != null) {
                mListener.onDataChanged();
            }
        }

        public boolean isEmpty() {
            return mData.isEmpty();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            View card;

            ViewHolder(View view) {
                super(view);
                textView = view.findViewById(R.id.clipboard_text);
                card = view.findViewById(R.id.clipboard_item_card);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_clipboard, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.textView.setText(mData.get(position));
            holder.card.setOnClickListener(v -> {
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
