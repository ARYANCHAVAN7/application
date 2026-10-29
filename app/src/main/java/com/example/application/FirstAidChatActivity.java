package com.example.application;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.application.utils.ChatMessage;
import com.example.application.utils.ChatMessageAdapter;
import com.example.application.utils.GroqApiClient;

import java.util.ArrayList;
import java.util.List;

public class FirstAidChatActivity extends AppCompatActivity {

    private RecyclerView recyclerChat;
    private EditText editChatInput;
    private View btnSend;
    private View btnBack;
    private View btnCall108;
    private View btnApiKey;

    private ChatMessageAdapter adapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private boolean isAwaitingResponse = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_aid_chat);

        initViews();
        setupRecycler();
        setupListeners();
        setupSuggestionChips();

        // Initial Welcome Message
        if (messageList.isEmpty()) {
            messageList.add(new ChatMessage(
                    "Hello! I am Suraksha AI, your instant emergency first-aid assistant powered by Groq LPU ⚡.\n\n" +
                    "Tap any quick emergency topic above or ask anything (e.g. CPR steps, severe bleeding, burns).",
                    false
            ));
            adapter.notifyItemInserted(messageList.size() - 1);
        }

        // Check if API key is present; if not, prompt user
        if (TextUtils.isEmpty(GroqApiClient.getApiKey(this))) {
            showApiKeyDialog(true);
        }
    }

    private void initViews() {
        recyclerChat = findViewById(R.id.recyclerChat);
        editChatInput = findViewById(R.id.editChatInput);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        btnCall108 = findViewById(R.id.btnCall108);
        btnApiKey = findViewById(R.id.btnApiKey);
    }

    private void setupRecycler() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerChat.setLayoutManager(layoutManager);
        adapter = new ChatMessageAdapter(messageList);
        recyclerChat.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnCall108.setOnClickListener(v -> {
            Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:108"));
            startActivity(callIntent);
        });

        btnApiKey.setOnClickListener(v -> showApiKeyDialog(false));

        btnSend.setOnClickListener(v -> {
            String text = editChatInput.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                sendUserPrompt(text);
                editChatInput.setText("");
            }
        });
    }

    private void setupSuggestionChips() {
        setChipAction(R.id.chipBleeding, "Severe bleeding from an open wound. How do I stop it immediately step by step?");
        setChipAction(R.id.chipCPR, "Adult CPR steps: person is unconscious and not breathing. Give immediate steps.");
        setChipAction(R.id.chipChoking, "Adult is choking and cannot breathe or speak. How to perform Heimlich maneuver?");
        setChipAction(R.id.chipBurns, "First aid for a severe thermal burn. What to do and what NOT to do?");
        setChipAction(R.id.chipSnake, "Someone was bitten by a snake. Immediate first aid steps before reaching hospital.");
        setChipAction(R.id.chipShock, "Person suffered an electric shock. How to safely help them right now?");
        setChipAction(R.id.chipFracture, "Suspected broken bone or fracture in arm or leg. How to stabilize it?");
    }

    private void setChipAction(int viewId, String prompt) {
        View chip = findViewById(viewId);
        if (chip != null) {
            chip.setOnClickListener(v -> sendUserPrompt(prompt));
        }
    }

    private void sendUserPrompt(String prompt) {
        if (isAwaitingResponse) {
            Toast.makeText(this, "Suraksha AI is answering...", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add user message
        messageList.add(new ChatMessage(prompt, true));
        adapter.notifyItemInserted(messageList.size() - 1);
        recyclerChat.smoothScrollToPosition(messageList.size() - 1);

        // Add typing indicator
        isAwaitingResponse = true;
        messageList.add(ChatMessage.typingIndicator());
        final int typingIndex = messageList.size() - 1;
        adapter.notifyItemInserted(typingIndex);
        recyclerChat.smoothScrollToPosition(typingIndex);

        // Call Groq API
        GroqApiClient.sendChat(this, messageList, new GroqApiClient.ApiCallback() {
            @Override
            public void onSuccess(String responseText) {
                isAwaitingResponse = false;
                if (typingIndex < messageList.size()) {
                    messageList.set(typingIndex, new ChatMessage(responseText, false));
                    adapter.notifyItemChanged(typingIndex);
                    recyclerChat.smoothScrollToPosition(typingIndex);
                }
            }

            @Override
            public void onError(String errorMessage) {
                isAwaitingResponse = false;
                if (typingIndex < messageList.size()) {
                    messageList.set(typingIndex, new ChatMessage("⚠️ " + errorMessage, false));
                    adapter.notifyItemChanged(typingIndex);
                    recyclerChat.smoothScrollToPosition(typingIndex);
                }
            }
        });
    }

    private void showApiKeyDialog(boolean isInitialPrompt) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isInitialPrompt ? "Groq API Key Required" : "Configure Groq API Key");
        builder.setMessage("Enter your free Groq API key (starts with 'gsk_') to enable instant AI emergency assistance:");

        final EditText input = new EditText(this);
        input.setHint("gsk_...");
        input.setSingleLine(true);
        String currentKey = GroqApiClient.getApiKey(this);
        if (!TextUtils.isEmpty(currentKey)) {
            input.setText(currentKey);
        }
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String enteredKey = input.getText().toString().trim();
            if (!TextUtils.isEmpty(enteredKey)) {
                GroqApiClient.saveApiKey(FirstAidChatActivity.this, enteredKey);
                Toast.makeText(FirstAidChatActivity.this, "Groq API Key saved successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(FirstAidChatActivity.this, "Key cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}
