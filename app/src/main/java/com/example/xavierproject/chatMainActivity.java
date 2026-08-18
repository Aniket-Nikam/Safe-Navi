package com.example.xavierproject;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

public class chatMainActivity extends AppCompatActivity {

    private RecyclerView chatRecycler;
    private EditText msgInput;
    private Button sendBtn;

    private ChatAdapter chatAdapter;
    private List<Message> messageList;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private OkHttpClient client = new OkHttpClient();

    private static final String CHAT_URL = "http://192.168.0.108:5000/chat";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        chatRecycler = findViewById(R.id.chatRecycler);
        msgInput = findViewById(R.id.msg);
        sendBtn = findViewById(R.id.send_btn);

        // Setup RecyclerView
        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // ChatGPT-style bottom stacking

        chatRecycler.setLayoutManager(layoutManager);
        chatRecycler.setAdapter(chatAdapter);

        // Send button click
        sendBtn.setOnClickListener(v -> {
            String userText = msgInput.getText().toString().trim();

            if (userText.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add user message
            addMessage(userText, Message.TYPE_USER);
            msgInput.setText("");

            // Optional typing indicator
            addMessage("Robo is thinking...", Message.TYPE_BOT);

            sendMessageToBot(userText);
        });
    }

    private void sendMessageToBot(String userText) {
        executorService.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("message", userText);

                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url(CHAT_URL)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {

                    if (response.isSuccessful() && response.body() != null) {
                        String responseData = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseData);
                        String botReply = jsonResponse.getString("reply");

                        runOnUiThread(() -> {
                            removeTypingIndicator();
                            addMessage(botReply, Message.TYPE_BOT);
                        });

                    } else {
                        runOnUiThread(() -> {
                            removeTypingIndicator();
                            addMessage("Server Error: " + response.code(), Message.TYPE_BOT);
                        });
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    removeTypingIndicator();
                    addMessage("Connection Failed âŒ", Message.TYPE_BOT);
                });
            }
        });
    }

    // Add message to RecyclerView
    private void addMessage(String text, int type) {
        messageList.add(new Message(text, type));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        chatRecycler.scrollToPosition(messageList.size() - 1);
    }

    // Remove "Robo is thinking..."
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
}
