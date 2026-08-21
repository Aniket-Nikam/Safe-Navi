package com.safenavi.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatbotFragment extends Fragment {

    private RecyclerView chatRecycler;
    private EditText msgInput;
    private MaterialButton sendBtn;
    private TextView statusTextView;

    private ChatAdapter chatAdapter;
    private List<Message> messageList;

    private ExecutorService executorService;
    private OkHttpClient client;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chatbot, container, false);

        initializeViews(view);
        setupRecyclerView();
        setupClickListeners();

        // Add welcome message
        addMessage("Hello! I'm your AI assistant. How can I help you today?", Message.TYPE_BOT);

        return view;
    }

    private void initializeViews(View view) {
        chatRecycler = view.findViewById(R.id.chatRecycler);
        msgInput = view.findViewById(R.id.msgInput);
        sendBtn = view.findViewById(R.id.sendBtn);
        statusTextView = view.findViewById(R.id.statusTextView);

        // Initialize executor and HTTP client
        executorService = Executors.newSingleThreadExecutor();
        client = new OkHttpClient();
    }

    private void setupRecyclerView() {
        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true); // Stack messages from bottom

        chatRecycler.setLayoutManager(layoutManager);
        chatRecycler.setAdapter(chatAdapter);
    }

    private void setupClickListeners() {
        sendBtn.setOnClickListener(v -> {
            String userText = msgInput.getText().toString().trim();

            if (userText.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a message", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add user message
            addMessage(userText, Message.TYPE_USER);
            msgInput.setText("");

            // Show typing indicator
            updateStatus("Typing...");
            addMessage("Robo is thinking...", Message.TYPE_BOT);

            // Send message to bot
            sendMessageToBot(userText);
        });
    }

    private void sendMessageToBot(String userText) {
        executorService.execute(() -> {
            try {
                // Construct Gemini API Request JSON
                JSONObject payload = new JSONObject();
                
                // System instructions to restrict the bot
                JSONObject systemInstruction = new JSONObject();
                JSONObject systemParts = new JSONObject();
                systemParts.put("text", "You are the Safe-Navi safety assistant. Only explain features of Safe-Navi, reporting and verification workflows, communities, emergency guidance, and safety information that is explicitly supplied by the Safe-Navi dataset. Never invent hazards or claim information is live when it is synthetic or unavailable. Clearly separate general advice from government-verified alerts.");
                systemInstruction.put("parts", systemParts);
                payload.put("system_instruction", systemInstruction);

                // User message
                JSONObject contents = new JSONObject();
                JSONObject parts = new JSONObject();
                parts.put("text", userText);
                contents.put("parts", new org.json.JSONArray().put(parts));
                payload.put("contents", new org.json.JSONArray().put(contents));

                RequestBody body = RequestBody.create(
                        payload.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + BuildConfig.GEMINI_API_KEY;

                Request request = new Request.Builder()
                        .url(geminiUrl)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseData = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseData);
                        
                        // Parse Gemini response
                        String botReply = jsonResponse.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                removeTypingIndicator();
                                addMessage(botReply, Message.TYPE_BOT);
                                updateStatus("Online");
                            });
                        }

                    } else {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                removeTypingIndicator();
                                addMessage("Server Error: " + response.code(), Message.TYPE_BOT);
                                updateStatus("Online");
                            });
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        removeTypingIndicator();
                        addMessage("Connection Failed âŒ\nPlease check your internet connection.", Message.TYPE_BOT);
                        updateStatus("Offline");
                    });
                }
            }
        });
    }

    // Add message to RecyclerView
    private void addMessage(String text, int type) {
        messageList.add(new Message(text, type));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        chatRecycler.scrollToPosition(messageList.size() - 1);
    }

    // Remove "Robo is thinking..." indicator
    private void removeTypingIndicator() {
        if (!messageList.isEmpty()) {
            Message last = messageList.get(messageList.size() - 1);
            if (last.getType() == Message.TYPE_BOT &&
                    last.getText().equals("Robo is thinking...")) {
                int pos = messageList.size() - 1;
                messageList.remove(pos);
                chatAdapter.notifyItemRemoved(pos);
            }
        }
    }

    // Update status text
    private void updateStatus(String status) {
        if (statusTextView != null) {
            statusTextView.setText(status);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Shutdown executor to prevent memory leaks
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
