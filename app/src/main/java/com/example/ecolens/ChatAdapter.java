package com.example.ecolens;

import android.content.res.Resources;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private final ArrayList<ChatMessage> chatMessages;
    private final OnMessageLongClickListener longClickListener;
    private final String currentUserId;

    public interface OnMessageLongClickListener {
        void onMessageLongClicked(ChatMessage message, int position);
    }

    public ChatAdapter(ArrayList<ChatMessage> chatMessages,
                       OnMessageLongClickListener listener,
                       String currentUserId) {
        this.chatMessages = chatMessages;
        this.longClickListener = listener;
        this.currentUserId = currentUserId != null ? currentUserId : "";
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return chatMessages.get(position).getId().hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        ChatMessage message = chatMessages.get(position);

        holder.usernameText.setText(message.getUsername());
        holder.messageText.setText(message.getMessage());

        DateFormat df = DateFormat.getDateTimeInstance();
        holder.timestampText.setText(df.format(new Date(message.getTimestamp())));

        boolean isMine = message.getUid() != null &&
                message.getUid().equals(currentUserId);

        // ---------- WIDTH ----------
        int maxWidth = (int) (Resources.getSystem().getDisplayMetrics().widthPixels * 0.70f);
        holder.messageText.setMaxWidth(maxWidth);
        holder.replyText.setMaxWidth((int) (maxWidth * 0.9f));

        // ---------- PROFILE IMAGE ----------
        if (message.getPhotoUrl() != null && !message.getPhotoUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(message.getPhotoUrl())
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .into(holder.profileImage);
        } else {
            holder.profileImage.setImageResource(R.drawable.ic_profile);
        }

        // ---------- LAYOUT PARAMS ----------
        LinearLayout.LayoutParams usernameParams =
                (LinearLayout.LayoutParams) holder.usernameRow.getLayoutParams();
        LinearLayout.LayoutParams cardParams =
                (LinearLayout.LayoutParams) holder.messageCard.getLayoutParams();
        LinearLayout.LayoutParams timeParams =
                (LinearLayout.LayoutParams) holder.timestampText.getLayoutParams();
        LinearLayout.LayoutParams replyParams =
                (LinearLayout.LayoutParams) holder.replyContainer.getLayoutParams();

        if (isMine) {
            // RIGHT ALIGN
            usernameParams.gravity = Gravity.END;
            cardParams.gravity = Gravity.END;
            timeParams.gravity = Gravity.END;
            replyParams.gravity = Gravity.END;

            holder.messageCard.setCardBackgroundColor(Color.parseColor("#2ECC71"));
            holder.replyContainer.setBackgroundColor(Color.parseColor("#0B2A1A"));
        } else {
            // LEFT ALIGN
            usernameParams.gravity = Gravity.START;
            cardParams.gravity = Gravity.START;
            timeParams.gravity = Gravity.START;
            replyParams.gravity = Gravity.START;

            holder.messageCard.setCardBackgroundColor(Color.parseColor("#184033"));
            holder.replyContainer.setBackgroundColor(Color.parseColor("#0B2A1A"));
        }

        holder.usernameRow.setLayoutParams(usernameParams);
        holder.messageCard.setLayoutParams(cardParams);
        holder.timestampText.setLayoutParams(timeParams);
        holder.replyContainer.setLayoutParams(replyParams);

        // ---------- REPLY PREVIEW ----------
        if (message.getReplyToId() != null && !message.getReplyToId().isEmpty()) {
            holder.replyContainer.setVisibility(View.VISIBLE);
            holder.replyUsername.setText(message.getReplyToUsername());
            holder.replyText.setText(message.getReplyToText());
        } else {
            holder.replyContainer.setVisibility(View.GONE);
        }

        // ---------- LONG CLICK ----------
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && longClickListener != null) {
                longClickListener.onMessageLongClicked(chatMessages.get(pos), pos);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    // ---------- HELPERS ----------
    public ChatMessage getMessage(int position) {
        return (position >= 0 && position < chatMessages.size())
                ? chatMessages.get(position) : null;
    }

    public void addMessage(ChatMessage message) {
        chatMessages.add(message);
        notifyItemInserted(chatMessages.size() - 1);
    }

    public void removeMessageById(String id) {
        for (int i = 0; i < chatMessages.size(); i++) {
            if (id.equals(chatMessages.get(i).getId())) {
                chatMessages.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    // ---------- VIEW HOLDER ----------
    public static class ViewHolder extends RecyclerView.ViewHolder {

        LinearLayout usernameRow;
        ImageView profileImage;
        TextView usernameText;
        TextView messageText;
        TextView timestampText;

        View replyContainer;
        TextView replyUsername;
        TextView replyText;

        CardView messageCard;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            usernameRow = itemView.findViewById(R.id.username_row);
            profileImage = itemView.findViewById(R.id.profile_image_small);
            usernameText = itemView.findViewById(R.id.username);
            messageText = itemView.findViewById(R.id.message);
            timestampText = itemView.findViewById(R.id.timestamp);

            replyContainer = itemView.findViewById(R.id.reply_container);
            replyUsername = itemView.findViewById(R.id.reply_username);
            replyText = itemView.findViewById(R.id.reply_text);

            messageCard = itemView.findViewById(R.id.message_card);
        }
    }
}
