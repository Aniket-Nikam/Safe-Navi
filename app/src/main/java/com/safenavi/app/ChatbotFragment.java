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
    private String pendingQuestion="",sessionIdentity,serverOrigin;
    private final android.os.Handler draftHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable persist=this::saveConversation;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                  @Nullable ViewGroup container,
                                                  @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chatbot, container, false);
        chatRecycler = view.findViewById(R.id.chatRecycler);
        msgInput = view.findViewById(R.id.msgInput);
        msgInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
        sendBtn = view.findViewById(R.id.sendBtn);
        statusTextView = view.findViewById(R.id.statusTextView);
        api = new ProductApiClient(requireContext());
        sessionIdentity=com.safenavi.app.product.ProductSession.token(requireContext());serverOrigin=com.safenavi.app.product.ServerConnection.baseUrl();
        JSONObject retained=com.safenavi.app.product.ScopedDraftStore.load(requireContext(),"navi");
        messageList.clear();JSONArray turns=retained.optJSONArray("turns");if(turns!=null)for(int i=0;i<turns.length();i++){JSONObject turn=turns.optJSONObject(i);if(turn!=null)messageList.add(new Message(turn.optString("content"),turn.optInt("type")));}
        if(retained.optBoolean("pending")&&!messageList.isEmpty()&&messageList.get(messageList.size()-1).getType()==Message.TYPE_USER)messageList.remove(messageList.size()-1);
        msgInput.setText(retained.optString("input"));sending=false;
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
        if(messageList.isEmpty())addMessage("Hi, I’m Navi. I can explain route risk, help you prepare a useful report, and offer general safety guidance. I never invent live incidents or replace emergency services.", Message.TYPE_BOT);
        msgInput.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(android.text.Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){draftHandler.removeCallbacks(persist);draftHandler.postDelayed(persist,500);}});
        return view;
    }

    private void send() { submitPrompt(msgInput.getText().toString().trim()); }

    private void submitPrompt(String text) {
        if (sending) return;
        if (text.isEmpty()) { Toast.makeText(requireContext(), "Write a question first", Toast.LENGTH_SHORT).show(); return; }
        JSONArray history = buildHistory();
        final View screen=getView();pendingQuestion=text;
        addMessage(text, Message.TYPE_USER);
        msgInput.setText("");
        sending = true; sendBtn.setEnabled(false); updateStatus("Navi is thinking…");
        addMessage("Thinking with Safe-Navi context…", Message.TYPE_BOT);
        saveConversation();
        api.assistant(text, history, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) {
                onScreen(screen,() -> {
                    removeTyping();
                    addMessage(value.optString("answer", "I could not produce an answer."), Message.TYPE_BOT);
                    sending = false; sendBtn.setEnabled(true); updateStatus("Groq · safety-aware");
                    pendingQuestion="";saveConversation();
                });
            }
            @Override public void onError(String message) {
                onScreen(screen,() -> {
                    removeTyping();
                    if(!messageList.isEmpty()&&messageList.get(messageList.size()-1).getType()==Message.TYPE_USER&&text.equals(messageList.get(messageList.size()-1).getText())){int last=messageList.size()-1;messageList.remove(last);chatAdapter.notifyItemRemoved(last);}
                    if(msgInput.getText().toString().isEmpty())msgInput.setText(text);
                    sending = false; sendBtn.setEnabled(true); updateStatus("Assistant unavailable");
                    pendingQuestion="";saveConversation();Toast.makeText(requireContext(),message+" Your question is retained; tap Send to retry.",Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void onScreen(View screen,Runnable action){if(screen!=null)screen.post(()->{if(isAdded()&&getView()==screen&&sessionIdentity.equals(com.safenavi.app.product.ProductSession.token(requireContext()))&&serverOrigin.equals(com.safenavi.app.product.ServerConnection.baseUrl()))action.run();});}
    private void saveConversation(){if(!isAdded()||msgInput==null||!sessionIdentity.equals(com.safenavi.app.product.ProductSession.token(requireContext()))||!serverOrigin.equals(com.safenavi.app.product.ServerConnection.baseUrl()))return;try{JSONArray turns=new JSONArray();int end=messageList.size()-(sending?1:0);for(int i=Math.max(0,end-20);i<end;i++){Message message=messageList.get(i);turns.put(new JSONObject().put("type",message.getType()).put("content",message.getText()));}com.safenavi.app.product.ScopedDraftStore.save(requireContext(),"navi",new JSONObject().put("input",sending?pendingQuestion:msgInput.getText().toString()).put("pending",sending).put("turns",turns));}catch(Exception ignored){}}
    @Override public void onPause(){saveConversation();draftHandler.removeCallbacks(persist);super.onPause();}
    @Override public void onDestroyView(){saveConversation();draftHandler.removeCallbacks(persist);super.onDestroyView();}

    private JSONArray buildHistory() {
        JSONArray history = new JSONArray();
        int start = Math.max(0, messageList.size() - 10);
        for (int i = start; i < messageList.size(); i++) {
            Message message = messageList.get(i);
            try { history.put(new JSONObject().put("role", message.getType() == Message.TYPE_USER ? "user" : "assistant").put("content", message.getText().length()>2000?message.getText().substring(0,2000):message.getText())); }
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
