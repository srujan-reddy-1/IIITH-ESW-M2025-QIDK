// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

public class Message_RecyclerViewAdapter extends RecyclerView.Adapter<Message_RecyclerViewAdapter.MyViewHolder> {

    Context context;
    ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>(1000);
    private Markwon markwon;
    private String profileImageUri;

    public Message_RecyclerViewAdapter(Context context, ArrayList<ChatMessage> messages) {
        this.context = context;
        this.messages = messages;
        
        // Load profile image URI from SharedPreferences
        SharedPreferences sharedPreferences = context.getSharedPreferences("UserProfile", Context.MODE_PRIVATE);
        profileImageUri = sharedPreferences.getString("profile_image_uri", "");
        
        // Initialize Markwon with plugins for rich markdown rendering
        markwon = Markwon.builder(context)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(TaskListPlugin.create(context))
                .build();
        
        // Enable stable IDs for better performance during streaming
        setHasStableIds(true);
    }
    
    @Override
    public long getItemId(int position) {
        // Use position as stable ID (messages are append-only)
        return position;
    }

    @NonNull
    @Override
    public Message_RecyclerViewAdapter.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.chat_row, parent, false);

        return new Message_RecyclerViewAdapter.MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Message_RecyclerViewAdapter.MyViewHolder holder, int position) {
        onBindViewHolder(holder, position, null);
    }
    
    @Override
    public void onBindViewHolder(@NonNull Message_RecyclerViewAdapter.MyViewHolder holder, int position, @NonNull java.util.List<Object> payloads) {
        ChatMessage msg = messages.get(position);
        
        // If payload is present and it's a content update, only update text (faster, no formatting change)
        if (payloads != null && !payloads.isEmpty() && payloads.contains("content_update")) {
            if (!msg.isMessageFromUser()) {
                // Quick update for bot message content only - maintain consistent formatting
                String messageText = msg.getMessage();
                if (messageText != null && messageText.trim().length() > 0) {
                    // Always use markdown for consistency (even if it was "Thinking..." before)
                    if (messageText.equals("Thinking...")) {
                        holder.mBotMessage.setText(messageText);
                        holder.mBotMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
                        holder.mBotMessage.setTextColor(holder.mBotMessage.getContext().getResources()
                            .getColor(android.R.color.darker_gray, null));
                    } else {
                        markwon.setMarkdown(holder.mBotMessage, messageText);
                        holder.mBotMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                        // Set text color to dark black
                        holder.mBotMessage.setTextColor(android.graphics.Color.parseColor("#000000"));
                    }
                }
            }
            return;
        }
        
        // Full bind for new items or when payload is empty
        if (msg.isMessageFromUser()) {
            // User messages - plain text (no markdown needed for user input)
            holder.mUserMessage.setText(msg.getMessage());
            holder.mLeftChatLayout.setVisibility(View.GONE);
            holder.mRightChatLayout.setVisibility(View.VISIBLE);
            
            // Load profile image if available
            if (!TextUtils.isEmpty(profileImageUri)) {
                try {
                    Uri imageUri = Uri.parse(profileImageUri);
                    holder.mUserAvatarImage.setImageURI(imageUri);
                    holder.mUserAvatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    holder.mUserAvatarImage.setImageTintList(null);
                } catch (Exception e) {
                    // If loading fails, keep default icon
                    holder.mUserAvatarImage.setImageResource(R.drawable.ic_person);
                }
            } else {
                // No profile image set, use default icon
                holder.mUserAvatarImage.setImageResource(R.drawable.ic_person);
            }
        } else {
            // Bot messages - consistent formatting
            String messageText = msg.getMessage();
            if (messageText != null && messageText.equals("Thinking...")) {
                // Special case for thinking message - keep italic
                holder.mBotMessage.setText(messageText);
                holder.mBotMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
                holder.mBotMessage.setTextColor(holder.mBotMessage.getContext().getResources()
                    .getColor(android.R.color.darker_gray, null));
            } else {
                // Render markdown for bot responses - consistent formatting
                markwon.setMarkdown(holder.mBotMessage, messageText != null ? messageText : "");
                // Ensure normal typeface (markdown handles its own formatting)
                holder.mBotMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                // Set text color to dark black
                holder.mBotMessage.setTextColor(android.graphics.Color.parseColor("#000000"));
            }
            holder.mLeftChatLayout.setVisibility(View.VISIBLE);
            holder.mRightChatLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage msg) {
        messages.add(msg);
    }

    /**
     * updateBotMessage: updates / inserts message on behalf of Bot
     *
     * @param bot_message message to update or insert
     * @return newly added message
     */
    public String updateBotMessage(String bot_message) {
        boolean lastMessageFromBot = false;
        ChatMessage lastMessage;

        if (messages.size() > 1) {
            lastMessage = messages.get(messages.size() - 1);
            if (lastMessage.mSender == MessageSender.BOT) {
                lastMessageFromBot = true;
            }
        } else {
            addMessage(new ChatMessage(bot_message, MessageSender.BOT));
        }

        if (lastMessageFromBot) {
            messages.get(messages.size() - 1).mMessage = messages.get(messages.size() - 1).mMessage + bot_message;
        } else {
            addMessage(new ChatMessage(bot_message, MessageSender.BOT));
        }
        return messages.get(messages.size() - 1).mMessage;
    }

    /**
     * replaceBotMessage: replaces the last bot message entirely (not append)
     *
     * @param bot_message message to replace with
     * @return newly replaced message
     */
    public String replaceBotMessage(String bot_message) {
        if (messages.size() > 0) {
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            if (lastMessage.mSender == MessageSender.BOT) {
                // Replace the entire message content
                lastMessage.mMessage = bot_message;
                return lastMessage.mMessage;
            }
        }
        // If no bot message to replace, add new one
        addMessage(new ChatMessage(bot_message, MessageSender.BOT));
        return messages.get(messages.size() - 1).mMessage;
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {

        TextView mUserMessage;
        TextView mBotMessage;
        LinearLayout mLeftChatLayout;
        LinearLayout mRightChatLayout;
        ImageView mUserAvatarImage;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);

            mBotMessage = itemView.findViewById(R.id.bot_message);
            mUserMessage = itemView.findViewById(R.id.user_message);
            mLeftChatLayout = itemView.findViewById(R.id.left_chat_layout);
            mRightChatLayout = itemView.findViewById(R.id.right_chat_layout);
            mUserAvatarImage = itemView.findViewById(R.id.user_avatar_image);
        }
    }
}
