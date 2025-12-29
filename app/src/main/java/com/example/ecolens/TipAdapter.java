package com.example.ecolens;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class TipAdapter extends RecyclerView.Adapter<TipAdapter.TipViewHolder> {
    private final List<Tip> tips;
    private final Context ctx;

    public TipAdapter(Context context, List<Tip> tips) {
        this.ctx = context;
        this.tips = tips;
    }

    @NonNull
    @Override
    public TipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(ctx)
                .inflate(R.layout.item_tip, parent, false);
        return new TipViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TipViewHolder holder, int position) {
        Tip tip = tips.get(position);
        holder.tvTipText.setText(tip.getTipText());
        Glide.with(ctx).load(tip.getImageUrl()).into(holder.imgTip);

        // Make the whole card open the source URL
        holder.itemView.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(tip.getSourceUrl()));
            ctx.startActivity(i);
        });
    }

    @Override
    public int getItemCount() {
        return tips.size();
    }

    static class TipViewHolder extends RecyclerView.ViewHolder {
        ImageView imgTip;
        TextView tvTipText;

        TipViewHolder(@NonNull View itemView) {
            super(itemView);
            imgTip     = itemView.findViewById(R.id.imgTip);
            tvTipText  = itemView.findViewById(R.id.tvTipText);
        }
    }
}
