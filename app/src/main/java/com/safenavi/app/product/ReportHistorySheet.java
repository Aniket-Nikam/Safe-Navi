package com.safenavi.app.product;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import android.text.*;
import androidx.recyclerview.widget.*;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.*;
import com.google.android.material.button.MaterialButton;
import com.safenavi.app.R;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

/** Searchable case history with a clear boundary between queued device reports and server records. */
public final class ReportHistorySheet {
    private ReportHistorySheet(){}
    public static void show(Activity activity,JSONArray server,JSONArray queued,Consumer<String> open,Runnable refresh){
        BottomSheetDialog sheet=new BottomSheetDialog(activity);LinearLayout root=column(activity);root.addView(text(activity,"My reports",21));
        root.addView(text(activity,server.length()+" received by server · "+queued.length()+" queued on this device",12));
        EditText query=new EditText(activity);query.setHint("Search title or status");query.setSingleLine(true);query.setTextSize(14);root.addView(query);
        List<JSONObject> all=new ArrayList<>();
        for(int i=0;i<queued.length();i++){JSONObject entry=queued.optJSONObject(i);if(entry==null)continue;JSONObject payload=entry.optJSONObject("payload");if(payload==null)continue;try{JSONObject item=new JSONObject(payload.toString());item.put("queued",true);item.put("status","QUEUED ON DEVICE");item.put("local_files",entry.optJSONArray("evidence_local_ids")==null?0:entry.optJSONArray("evidence_local_ids").length());all.add(item);}catch(Exception ignored){}}
        for(int i=0;i<server.length();i++)if(server.optJSONObject(i)!=null)all.add(server.optJSONObject(i));
        RecyclerView list=new RecyclerView(activity);list.setLayoutManager(new LinearLayoutManager(activity));
        List<JSONObject> visible=new ArrayList<>(all);
        RecyclerView.Adapter<Holder> adapter=new RecyclerView.Adapter<Holder>(){
            public Holder onCreateViewHolder(ViewGroup parent,int type){LinearLayout row=column(activity);row.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));TextView title=text(activity,"",16),meta=text(activity,"",12);row.addView(title);row.addView(meta);return new Holder(row,title,meta);}
            public void onBindViewHolder(Holder holder,int position){JSONObject item=visible.get(position);holder.title.setText(item.optString("title"));holder.meta.setText(item.optString("status").replace('_',' ')+"\n"+JsonDisplay.text(item,"address","Location not available"));holder.itemView.setOnClickListener(v->{if(item.optBoolean("queued")){new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle("Waiting for upload").setMessage(item.optString("description")+"\n\n"+item.optInt("local_files")+" evidence file(s) retained locally. This report is not yet confirmed received by the server.").setNegativeButton("Close",null).setPositiveButton("Retry upload",(d,w)->OfflineReportUploadWorker.schedule(activity)).show();}else{sheet.dismiss();open.accept(item.optString("id"));}});}
            public int getItemCount(){return visible.size();}
        };list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence value,int a,int b,int c){String term=value.toString().toLowerCase(Locale.ROOT);visible.clear();for(JSONObject item:all)if((item.optString("title")+" "+item.optString("status")).toLowerCase(Locale.ROOT).contains(term))visible.add(item);adapter.notifyDataSetChanged();}});
        LinearLayout actions=new LinearLayout(activity);MaterialButton retry=button(activity,"Retry queued"),reload=button(activity,"Refresh"),close=button(activity,"Close");for(MaterialButton action:new MaterialButton[]{retry,reload,close})actions.addView(action,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
        retry.setEnabled(queued.length()>0);retry.setOnClickListener(v->{OfflineReportUploadWorker.schedule(activity);Toast.makeText(activity,"Upload retry scheduled; refresh to check receipt",Toast.LENGTH_LONG).show();});reload.setOnClickListener(v->{sheet.dismiss();refresh.run();});close.setOnClickListener(v->sheet.dismiss());
        root.setLayoutParams(new ViewGroup.LayoutParams(-1,Math.round(activity.getResources().getDisplayMetrics().heightPixels*.8f)));sheet.setContentView(root);sheet.show();sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }
    public static void detail(Activity activity,JSONObject report,Runnable conversation){BottomSheetDialog sheet=new BottomSheetDialog(activity);LinearLayout body=column(activity);body.addView(text(activity,report.optString("title"),21));body.addView(text(activity,report.optString("status").replace('_',' '),13));body.addView(text(activity,report.optString("description"),15));body.addView(text(activity,"Location\n"+JsonDisplay.text(report,"address","Pinned location"),13));body.addView(text(activity,"Assigned department\n"+JsonDisplay.text(report,"assigned_department","Awaiting official assignment"),13));body.addView(text(activity,"Expected resolution\n"+JsonDisplay.text(report,"due_at","Not scheduled yet"),13));String info=JsonDisplay.text(report,"requested_info","");if(!info.isEmpty())body.addView(text(activity,"Information requested\n"+info,14));MaterialButton chat=button(activity,"Open case conversation");chat.setOnClickListener(v->{sheet.dismiss();conversation.run();});body.addView(chat);JSONArray evidence=report.optJSONArray("evidence");if(evidence!=null&&evidence.length()>0){MaterialButton photos=button(activity,"View evidence ("+evidence.length()+")");photos.setOnClickListener(v->EvidenceViewer.show(activity,evidence));body.addView(photos);}body.addView(text(activity,"Status timeline",18));JSONArray timeline=report.optJSONArray("timeline");if(timeline==null||timeline.length()==0)body.addView(text(activity,"Awaiting official review",13));else for(int i=0;i<timeline.length();i++){JSONObject event=timeline.optJSONObject(i);if(event!=null)body.addView(text(activity,event.optString("action").replace('_',' ')+"\n"+event.optString("reason")+"\n"+event.optString("created_at"),13));}MaterialButton close=button(activity,"Close");close.setOnClickListener(v->sheet.dismiss());body.addView(close);ScrollView scroll=new ScrollView(activity);scroll.addView(body);sheet.setContentView(scroll);sheet.show();sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);}
    private static LinearLayout column(Activity a){LinearLayout v=new LinearLayout(a);v.setOrientation(1);int d=Math.round(16*a.getResources().getDisplayMetrics().density);v.setPadding(d,d,d,d);v.setBackgroundColor(ContextCompat.getColor(a,R.color.surface));return v;}
    private static TextView text(Activity a,String value,int size){TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(ContextCompat.getColor(a,R.color.text_primary));v.setPadding(0,8,0,8);return v;}
    private static MaterialButton button(Activity a,String value){MaterialButton b=new MaterialButton(a,null,com.google.android.material.R.attr.borderlessButtonStyle);b.setText(value);b.setAllCaps(false);b.setLetterSpacing(0);b.setTextSize(13);return b;}
    private static class Holder extends RecyclerView.ViewHolder{final TextView title,meta;Holder(View v,TextView t,TextView m){super(v);title=t;meta=m;}}
}
