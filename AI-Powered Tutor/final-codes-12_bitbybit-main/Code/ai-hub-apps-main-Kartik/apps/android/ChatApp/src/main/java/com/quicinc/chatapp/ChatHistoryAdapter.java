package com.quicinc.chatapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying chat history in navigation drawer
 */
public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.ChatHistoryViewHolder> {
    
    private List<ChatSession> chatSessions;
    private OnChatSessionClickListener listener;
    
    public interface OnChatSessionClickListener {
        void onChatSessionClick(ChatSession session);
        void onChatSessionLongClick(ChatSession session);
    }
    
    public ChatHistoryAdapter(OnChatSessionClickListener listener) {
        this.chatSessions = new ArrayList<>();
        this.listener = listener;
    }
    
    public void setChatSessions(List<ChatSession> sessions) {
        this.chatSessions = sessions;
        notifyDataSetChanged();
    }
    
    public void addChatSession(ChatSession session) {
        chatSessions.add(0, session);  // Add at top
        notifyItemInserted(0);
    }
    
    public void updateChatSession(ChatSession session) {
        for (int i = 0; i < chatSessions.size(); i++) {
            if (chatSessions.get(i).getSessionId().equals(session.getSessionId())) {
                chatSessions.set(i, session);
                notifyItemChanged(i);
                break;
            }
        }
    }
    
    public void removeChatSession(String sessionId) {
        for (int i = 0; i < chatSessions.size(); i++) {
            if (chatSessions.get(i).getSessionId().equals(sessionId)) {
                chatSessions.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }
    
    @NonNull
    @Override
    public ChatHistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_history, parent, false);
        return new ChatHistoryViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ChatHistoryViewHolder holder, int position) {
        ChatSession session = chatSessions.get(position);
        holder.bind(session);
    }
    
    @Override
    public int getItemCount() {
        return chatSessions.size();
    }
    
    class ChatHistoryViewHolder extends RecyclerView.ViewHolder {
        private TextView chatTitle;
        private TextView chatTimestamp;
        
        public ChatHistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            chatTitle = itemView.findViewById(R.id.chat_title);
            chatTimestamp = itemView.findViewById(R.id.chat_timestamp);
        }
        
        public void bind(ChatSession session) {
            chatTitle.setText(session.getTitle());
            chatTimestamp.setText(session.getRelativeTime());
            
            // Click to load session
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onChatSessionClick(session);
                }
            });
            
            // Long click to delete session
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onChatSessionLongClick(session);
                }
                return true;
            });
        }
    }
}
