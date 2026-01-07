// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class Message_RecyclerViewAdapter extends RecyclerView.Adapter<Message_RecyclerViewAdapter.MyViewHolder> {

    Context context;
    ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>(1000);

    public Message_RecyclerViewAdapter(Context context, ArrayList<ChatMessage> messages) {
        this.context = context;
        this.messages = messages;
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
        ChatMessage msg = messages.get(position);
        if (msg.isMessageFromUser()) {
            holder.mUserMessage.setText(msg.getMessage());
            holder.mLeftChatLayout.setVisibility(View.GONE);
            holder.mRightChatLayout.setVisibility(View.VISIBLE);
        } else {
            // Format bot message with better structure
            String formattedMessage = formatBotMessage(msg.getMessage());
            holder.mBotMessage.setText(formattedMessage);
            holder.mLeftChatLayout.setVisibility(View.VISIBLE);
            holder.mRightChatLayout.setVisibility(View.GONE);
        }
    }
    
    /**
     * Format bot message for better readability
     * - Handles formulas and mathematical expressions
     * - Adds proper spacing after punctuation
     * - Ensures consistent line breaks
     * - Improves overall structure for lists and equations
     */
    private String formatBotMessage(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        
        // Normalize line endings
        message = message.replaceAll("\\r\\n", "\n");
        message = message.replaceAll("\\r", "\n");
        
        // Remove excessive whitespace while preserving intentional line breaks
        message = message.replaceAll("[ \\t]+", " ");
        
        // Normalize multiple consecutive line breaks (3+ becomes 2)
        message = message.replaceAll("\\n{3,}", "\n\n");
        
        // Ensure proper spacing after sentence-ending punctuation
        message = message.replaceAll("([.!?])([A-Z])", "$1 $2");
        
        // Format numbered lists with proper line breaks (only if not already on new line)
        message = message.replaceAll("([^\\n])\\s*([0-9]+\\.)\\s+", "$1\n$2 ");
        
        // Format bullet points with proper line breaks (only if not already on new line)
        message = message.replaceAll("([^\\n])\\s*([•\\-\\*])\\s+([A-Z])", "$1\n$2 $3");
        
        // Add spacing before section headers (capitalized text ending with colon)
        // But only if it's a reasonable header length (5-50 chars)
        message = message.replaceAll("([^\\n])\n([A-Z][^:\\n]{4,49}:)", "$1\n\n$2");
        
        // Add line break before "The formula is:" or similar patterns
        message = message.replaceAll("([^\\n])(The formula [^\\n]{0,20}:)", "$1\n\n$2");
        
        // Keep formulas on single lines - remove line breaks within mathematical expressions
        // This fixes the broken formula display
        message = message.replaceAll("(\\bformula[^:]{0,20}:)\\s*\\n+\\s*([^\\n]+)", "$1 $2");
        
        // Clean up leading/trailing whitespace on each line
        message = message.replaceAll("(?m)^[ \\t]+", "");
        message = message.replaceAll("(?m)[ \\t]+$", "");
        
        // Clean up any leading/trailing whitespace
        message = message.trim();
        
        return message;
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
     * Get the last bot message for conversation history
     */
    public String getLastBotMessage() {
        if (messages.size() > 0) {
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            if (lastMessage.mSender == MessageSender.BOT) {
                return lastMessage.mMessage;
            }
        }
        return "";
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {

        TextView mUserMessage;
        TextView mBotMessage;
        LinearLayout mLeftChatLayout;
        LinearLayout mRightChatLayout;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);

            mBotMessage = itemView.findViewById(R.id.bot_message);
            mUserMessage = itemView.findViewById(R.id.user_message);
            mLeftChatLayout = itemView.findViewById(R.id.left_chat_layout);
            mRightChatLayout = itemView.findViewById(R.id.right_chat_layout);
        }
    }
}
