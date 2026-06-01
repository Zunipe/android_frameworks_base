package com.android.systemui.gamemode;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.res.R;

import java.util.ArrayList;
import java.util.List;

public class PerfAdapter extends RecyclerView.Adapter<PerfAdapter.PerfViewHolder> {

    private final List<PerfItem> mDataList = new ArrayList<>();

    // 更新整个列表数据
    public void setData(List<PerfItem> newData) {
        mDataList.clear();
        if (newData != null) {
            mDataList.addAll(newData);
        }
        notifyDataSetChanged();
    }

    // 单个条目更新（方便后续按索引刷新）
    public void updateItem(int index, String newText) {
        if (index >= 0 && index < mDataList.size()) {
            mDataList.get(index).text = newText;
            notifyItemChanged(index);
        }
    }

    @Override
    public PerfViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_perf_info, parent, false);
        return new PerfViewHolder(view);
    }

    @Override
    public void onBindViewHolder(PerfViewHolder holder, int position) {
        PerfItem item = mDataList.get(position);
        holder.textView.setText(item.type + ":" + item.text);
        holder.textView.setTextColor(item.color); // 动态设置颜色（如CPU绿、GPU青）
    }

    @Override
    public int getItemCount() {
        return mDataList.size();
    }

    static class PerfViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        PerfViewHolder(View root) {
            super(root);
            textView = (TextView) root.findViewById(R.id.tv_perf_text);
        }
    }
}
