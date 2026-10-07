package com.safenavi.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.safenavi.app.product.ProductApiClient;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ChatbotFragment extends Fragment {
    private RecyclerView chatRecycler;
    private EditText msgInput;
    private MaterialButton sendBtn;
    private TextView statusTextView;
    private ChatAdapter chatAdapter;
    private final List<Message> messageList = new ArrayList<>();
    private ProductApiClient api;
    private boolean sending;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                  @Nullable ViewGroup container,
                                                  @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chatbot, container, false);
        chatRecycler = view.findViewById(R.id.chatRecycler);
        msgInput = view.findViewById(R.id.msgInput);
        sendBtn = view.findViewById(R.id.sendBtn);
        statusTextView = view.findViewById(R.id.statusTextView);
        api = new ProductApiClient(requireContext());
        chatAdapter = new ChatAdapter(messageList);
        LinearLayoutManager layout = new LinearLayoutManager(requireContext());
        layout.setStackFromEnd(true);
        chatRecycler.setLayoutManager(layout);
        chatRecycler.setAdapter(chatAdapter);
        sendBtn.setOnClickListener(v -> send());
        msgInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { send(); return true; }
            return false;
        });
        view.findViewById(R.id.assistantBackButton).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        view.findViewById(R.id.promptRoute).setOnClickListener(v -> submitPrompt("How can I compare the safest route with the fastest route?"));
        view.findViewById(R.id.promptReport).setOnClickListener(v -> submitPrompt("What information should I include in a useful civic issue report?"));
        view.findViewById(R.id.promptEmergency).setOnClickListener(v -> submitPrompt("What should I do if I feel unsafe during a journey?"));
        addMessage("Hi, I’m Navi. I can explain route risk, help you prepare a useful report, and offer general safety guidance. I never invent live incidents or replace emergency services.", Message.TYPE_BOT);
        return view;
    }

    private void send() { submitPrompt(msgInput.getText().toString().trim()); }

    private void submitPrompt(String text) {
        if (sending) return;
        if (text.isEmpty()) { Toast.makeText(requireContext(), "Write a question first", Toast.LENGTH_SHORT).show(); return; }
        JSONArray history = buildHistory();
        addMessage(text, Message.TYPE_USER);
        msgInput.setText("");
        sending = true; sendBtn.setEnabled(false); updateStatus("Navi is thinking…");
        addMessage("Thinking with Safe-Navi context…", Message.TYPE_BOT);
        api.assistant(text, history, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    removeTyping();
                    addMessage(value.optString("answer", "I could not produce an answer."), Message.TYPE_BOT);
                    sending = false; sendBtn.setEnabled(true); updateStatus("Groq · safety-aware");
                });
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    removeTyping(); addMessage(message, Message.TYPE_BOT);
                    sending = false; sendBtn.setEnabled(true); updateStatus("Assistant unavailable");
                });
            }
        });
    }

    private JSONArray buildHistory() {
        JSONArray history = new JSONArray();
        int start = Math.max(0, messageList.size() - 10);
        for (int i = start; i < messageList.size(); i++) {
            Message message = messageList.get(i);
            try { history.put(new JSONObject().put("role", message.getType() == Message.TYPE_USER ? "user" : "assistant").put("content", message.getText())); }
            catch (JSONException ignored) { }
        }
        return history;
    }

    private void addMessage(String text, int type) {
        messageList.add(new Message(text, type));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        chatRecycler.scrollToPosition(messageList.size() - 1);
    }

    private void removeTyping() {
        if (!messageList.isEmpty() && messageList.get(messageList.size() - 1).getText().startsWith("Thinking with")) {
            int index = messageList.size() - 1; messageList.remove(index); chatAdapter.notifyItemRemoved(index);
        }
    }

    private void updateStatus(String status) { statusTextView.setText(status); }
}
