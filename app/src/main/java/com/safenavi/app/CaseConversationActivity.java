package com.safenavi.app;

import android.os.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.text.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.*;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.safenavi.app.product.*;
import org.json.*;
import java.util.*;

/** One case, one authenticated conversation. No read receipts or delivery claims before server acknowledgement. */
public class CaseConversationActivity extends AppCompatActivity {
    private ProductApiClient api; private String reportId,scope,identity,origin,requestId,localPhoto,remotePhoto;
    private TextInputEditText composer; private TextView status,attachment;
    private MaterialButton send,photo,older; private RecyclerView list; private LinearLayoutManager layout;
    private final Messages messages=new Messages(); private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean foreground,loading,sending,restoring,sendUnconfirmed; private int generation;
    private final Runnable poll=new Runnable(){public void run(){ if(foreground) { fetch(false); handler.postDelayed(this,5000); } }};
    private final Runnable persist=this::saveDraft;
    private final ActivityResultLauncher<String[]> picker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),this::retainPhoto);
    public static void open(Context context,String reportId) { context.startActivity(new Intent(context,CaseConversationActivity.class).putExtra("report_id",reportId)); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); reportId=getIntent().getStringExtra("report_id");
        if(reportId==null || !ProductSession.isSignedIn(this)) { finish(); return; }
        identity=ProductSession.token(this); origin=ServerConnection.baseUrl(); scope="case_"+reportId; api=new ProductApiClient(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(8),dp(12),dp(8)); root.setBackgroundColor(color(R.color.background));
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton back=button("Back"); back.setOnClickListener(v -> finish()); header.addView(back);
        TextView title=text("Case conversation",19); title.setTypeface(null,android.graphics.Typeface.BOLD); header.addView(title,new LinearLayout.LayoutParams(0,-2,1)); root.addView(header);
        status=text("Connecting securely…",12); status.setPadding(dp(6),dp(6),dp(6),dp(6)); status.setOnClickListener(v -> fetch(false)); root.addView(status);
        older=button("Load earlier messages"); older.setOnClickListener(v -> fetch(true)); root.addView(older);
        list=new RecyclerView(this); layout=new LinearLayoutManager(this); layout.setStackFromEnd(true); list.setLayoutManager(layout); list.setAdapter(messages); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        attachment=text("",12); root.addView(attachment); attachment.setOnClickListener(v -> {
            if(sending) return;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Unsent photograph").setMessage("Remove this photograph from the draft?")
                    .setNegativeButton("Keep",null).setPositiveButton("Remove",(d,w)-> { if(localPhoto!=null) OfflineEvidenceStore.delete(this,localPhoto); localPhoto=null;remotePhoto=null;requestId=UUID.randomUUID().toString(); saveDraft(); updateAttachment(); }).show();
        });
        composer=new TextInputEditText(this); composer.setHint("Message about this report…"); composer.setTextSize(15); composer.setMinLines(1); composer.setMaxLines(4); composer.setInputType(131073); composer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)}); root.addView(composer,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this); photo=button("Photo"); send=button("Send"); actions.addView(photo,new LinearLayout.LayoutParams(0,dp(48),1)); actions.addView(send,new LinearLayout.LayoutParams(0,dp(48),1)); root.addView(actions);
        photo.setOnClickListener(v -> picker.launch(new String[]{"image/jpeg","image/png","image/webp"})); send.setOnClickListener(v -> send());
        setContentView(root); getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        JSONObject draft=ScopedDraftStore.load(this,scope); requestId=draft.optString("request_id",UUID.randomUUID().toString()); localPhoto=nullable(draft,"local_photo"); remotePhoto=nullable(draft,"remote_photo");
        restoring=true; composer.setText(draft.optString("content")); restoring=false; updateAttachment();
        composer.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int start,int count,int after){} public void afterTextChanged(Editable e){} public void onTextChanged(CharSequence s,int start,int before,int count){if(!restoring){requestId=UUID.randomUUID().toString(); handler.removeCallbacks(persist);handler.postDelayed(persist,500);}} });
        if("admin".equals(ProductSession.role(this))) { composer.setEnabled(false); photo.setEnabled(false);send.setEnabled(false);composer.setHint("Administrator read-only access"); }
    }
    private boolean alive(){return !isFinishing()&&!isDestroyed()&&identity.equals(ProductSession.token(this))&&origin.equals(ServerConnection.baseUrl());}
    private String nullable(JSONObject value,String key){String result=JsonDisplay.text(value,key,"");return result.isEmpty()?null:result;}
    private void updateAttachment(){attachment.setText(localPhoto==null&&remotePhoto==null?"":"Photograph retained with draft. Tap to remove.");}
    private void saveDraft(){if(!alive()||composer==null)return;try{JSONObject draft=new JSONObject().put("content",composer.getText().toString()).put("request_id",requestId).put("local_photo",localPhoto).put("remote_photo",remotePhoto);if(!ScopedDraftStore.save(this,scope,draft))status.setText("Draft could not be saved. Keep this screen open.");}catch(Exception ignored){}}
    private void retainPhoto(Uri uri){if(uri==null||!alive())return; setSending(true);status.setText("Keeping photograph securely on this device…");
        new Thread(()->{try{JSONObject value=OfflineEvidenceStore.store(getApplicationContext(),uri);runOnUiThread(()->{if(!alive())return;if(localPhoto!=null)OfflineEvidenceStore.delete(this,localPhoto);localPhoto=value.optString("id");remotePhoto=null;requestId=UUID.randomUUID().toString();saveDraft();updateAttachment();setSending(false);status.setText("Photo saved locally. Send when connected.");});}catch(Exception error){runOnUiThread(()->{if(alive()){setSending(false);status.setText("Photo could not be retained: "+error.getMessage());}});}},"case-photo-copy").start();
    }
    private void setSending(boolean value){sending=value;composer.setEnabled(!value);send.setEnabled(!value);photo.setEnabled(!value);send.setText(value?"Sending…":"Send");}
    private void send(){if(sending||!alive())return;String content=composer.getText().toString().trim();if(content.isEmpty()&&localPhoto==null&&remotePhoto==null){composer.setError("Write a message or attach a photograph");return;}setSending(true);saveDraft();
        if(localPhoto!=null&&remotePhoto==null){JSONObject item=OfflineEvidenceStore.get(this,localPhoto);if(item==null){fail("The local photograph is unavailable. Remove it or select it again.");return;}
            api.uploadStoredEvidence(item,new ProductApiClient.ObjectCallback(){public void onSuccess(JSONObject result){runOnUiThread(()->{if(!alive())return;remotePhoto=result.optString("id");saveDraft();postMessage();});}public void onError(String message){runOnUiThread(()->fail(message));}});
        }else postMessage();
    }
    private void postMessage(){String content=composer.getText().toString().trim();api.createReportMessage(reportId,content.isEmpty()?"Photograph supplied for this case":content,remotePhoto,requestId,new ProductApiClient.ObjectCallback(){
        public void onSuccess(JSONObject value){runOnUiThread(()->{if(!alive())return;sendUnconfirmed=false;if(localPhoto!=null)OfflineEvidenceStore.delete(CaseConversationActivity.this,localPhoto);localPhoto=null;remotePhoto=null;restoring=true;composer.setText("");restoring=false;requestId=UUID.randomUUID().toString();ScopedDraftStore.clear(CaseConversationActivity.this,scope);updateAttachment();setSending(false);status.setText("Accepted by server. Refreshing conversation…");fetch(false);});}
        public void onError(String message){runOnUiThread(()->fail(message));}
    });}
    private void fail(String message){if(!alive())return;sendUnconfirmed=true;setSending(false);saveDraft();status.setText("Not confirmed sent. Draft retained; tap Send to retry. "+message);}
    private void fetch(boolean previous){if(!alive()||!foreground||loading)return;loading=true;older.setEnabled(false);int request=generation;
        String before=previous&&!messages.items.isEmpty()?messages.items.get(0).optString("id"):null;
        String after=!previous&&!messages.items.isEmpty()?messages.items.get(messages.items.size()-1).optString("id"):null;
        api.reportMessages(reportId,before,after,new ProductApiClient.ArrayCallback(){
            public void onSuccess(JSONArray values){runOnUiThread(()->{if(!alive()||request!=generation)return;loading=false;older.setEnabled(true);if(previous&&values.length()<50)older.setVisibility(View.GONE);
                boolean atBottom=layout.findLastVisibleItemPosition()>=messages.items.size()-3;int inserted=messages.merge(values,previous);if(!previous&&inserted>0&&atBottom)list.scrollToPosition(messages.items.size()-1);
                if(!sending&&!sendUnconfirmed)status.setText(messages.items.isEmpty()?"No messages yet. This private thread connects the citizen and reviewing officials.":"Connected. Updates refresh while this screen is open.");});}
            public void onError(String message){runOnUiThread(()->{if(!alive()||request!=generation)return;loading=false;older.setEnabled(true);if(!sending)status.setText("Connection interrupted. Your draft is retained. Tap here to retry. "+message);});}
        });
    }
    @Override protected void onStart(){super.onStart();foreground=true;handler.post(poll);}
    @Override protected void onStop(){saveDraft();foreground=false;generation++;loading=false;handler.removeCallbacks(poll);handler.removeCallbacks(persist);super.onStop();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);} private int color(int id){return ContextCompat.getColor(this,id);}
    private TextView text(String value,int size){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color(R.color.text_primary));return view;}
    private MaterialButton button(String value){MaterialButton view=new MaterialButton(this,null,com.google.android.material.R.attr.borderlessButtonStyle);view.setText(value);view.setAllCaps(false);view.setLetterSpacing(0);return view;}
    private final class Messages extends RecyclerView.Adapter<MessageHolder>{
        final List<JSONObject> items=new ArrayList<>();
        int merge(JSONArray values,boolean previous){List<JSONObject> added=new ArrayList<>();Set<String> known=new HashSet<>();for(JSONObject item:items)known.add(item.optString("id"));for(int i=0;i<values.length();i++){JSONObject value=values.optJSONObject(i);if(value!=null&&known.add(value.optString("id")))added.add(value);}int start=previous?0:items.size();if(previous)items.addAll(0,added);else items.addAll(added);if(!added.isEmpty())notifyItemRangeInserted(start,added.size());return added.size();}
        public MessageHolder onCreateViewHolder(ViewGroup parent,int type){LinearLayout bubble=new LinearLayout(CaseConversationActivity.this);bubble.setOrientation(LinearLayout.VERTICAL);bubble.setPadding(dp(14),dp(10),dp(14),dp(10));RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,-2);p.setMargins(dp(4),dp(5),dp(4),dp(5));bubble.setLayoutParams(p);TextView author=text("",12),body=text("",15),time=text("",11);body.setTextIsSelectable(true);MaterialButton image=button("View photograph");bubble.addView(author);bubble.addView(body);bubble.addView(image);bubble.addView(time);return new MessageHolder(bubble,author,body,time,image);}
        public void onBindViewHolder(MessageHolder holder,int position){JSONObject item=items.get(position);boolean own=ProductSession.userId(CaseConversationActivity.this).equals(item.optString("author_id"));holder.author.setText((own?"You":JsonDisplay.text(item,"author_name","Participant"))+" · "+JsonDisplay.text(item,"author_role",""));holder.body.setText(item.optString("content"));holder.time.setText(item.optString("created_at").replace('T',' '));
            android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(color(own?R.color.surface_elevated:R.color.surface));background.setCornerRadius(dp(16));holder.itemView.setBackground(background);
            RecyclerView.LayoutParams p=(RecyclerView.LayoutParams)holder.itemView.getLayoutParams();p.setMargins(own?dp(28):dp(4),dp(5),own?dp(4):dp(28),dp(5));holder.itemView.setLayoutParams(p);
            String evidence=JsonDisplay.text(item,"evidence_id","");holder.image.setVisibility(evidence.isEmpty()?View.GONE:View.VISIBLE);holder.image.setOnClickListener(v->{try{JSONObject file=new JSONObject().put("id",evidence).put("original_name",JsonDisplay.text(item,"attachment_name","Case photograph")).put("media_type",JsonDisplay.text(item,"attachment_type","image/jpeg"));EvidenceViewer.show(CaseConversationActivity.this,new JSONArray().put(file));}catch(Exception ignored){}});
        }
        public int getItemCount(){return items.size();}
    }
    private static final class MessageHolder extends RecyclerView.ViewHolder{final TextView author,body,time;final MaterialButton image;MessageHolder(View view,TextView author,TextView body,TextView time,MaterialButton image){super(view);this.author=author;this.body=body;this.time=time;this.image=image;}}
}
