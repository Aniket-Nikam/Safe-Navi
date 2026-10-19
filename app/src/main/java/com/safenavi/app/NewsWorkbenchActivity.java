package com.safenavi.app;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.text.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.safenavi.app.product.*;
import com.safenavi.app.safety.network.*;
import com.safenavi.app.safety.model.PlaceSearchResult;
import org.json.*;
import org.maplibre.android.MapLibre;
import org.maplibre.android.maps.*;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.*;
import java.util.*;

/** Review evidence, pinpoint the affected road, then explicitly publish a time-limited official hazard. */
public class NewsWorkbenchActivity extends AppCompatActivity {
    private ProductApiClient api; private TextView status; private EditText search; private Spinner filter;
    private final List<JSONObject> signals=new ArrayList<>(); private final Rows rows=new Rows();
    private String identity,origin; private boolean loading; private String[] filters={"All","UNVERIFIED","NEEDS_FIELD_CHECK","RELEVANT","PUBLISHED","DISMISSED"};
    private android.app.Dialog publicationDialog,storyDialog;
    private boolean alive(){return !isFinishing()&&!isDestroyed()&&identity.equals(ProductSession.token(this))&&origin.equals(ServerConnection.baseUrl());}
    @Override protected void onCreate(Bundle state){super.onCreate(state);if(!Arrays.asList("government","admin").contains(ProductSession.role(this))){finish();return;}identity=ProductSession.token(this);origin=ServerConnection.baseUrl();api=new ProductApiClient(this);
        LinearLayout root=column();LinearLayout heading=new LinearLayout(this);MaterialButton back=button("Back");back.setOnClickListener(v->finish());heading.addView(back);heading.addView(text("News intelligence",20));root.addView(heading);
        root.addView(text("External reports need official location and current-incident verification before they affect public routes.",13));
        search=new EditText(this);search.setHint("Search headline, locality or publisher");search.setSingleLine(true);search.setTextSize(14);root.addView(search);
        filter=spinner(filters);root.addView(filter);status=text("Loading…",12);status.setOnClickListener(v->load());root.addView(status);
        LinearLayout controls=new LinearLayout(this);MaterialButton refresh=button("Refresh");refresh.setOnClickListener(v->load());controls.addView(refresh);
        {
            MaterialButton fetch=button("Check internet"),schedule=button("Schedule");
            fetch.setOnClickListener(v->{
                fetch.setEnabled(false);
                api.ingestNews(new ProductApiClient.ObjectCallback(){
                    public void onSuccess(JSONObject value){runOnUiThread(()->{if(alive()){fetch.setEnabled(true);load();JSONArray errors=value.optJSONArray("errors");String details=value.optInt("inserted")+" new articles · "+value.optInt("deduplicated")+" already known\nFeeds configured: "+value.optInt("feeds");String skipped=JsonDisplay.text(value,"skipped","");if(!skipped.isEmpty())details+="\nCheck skipped: "+skipped;if(errors!=null&&errors.length()>0)details+="\nSome feeds failed: "+errors.toString();new MaterialAlertDialogBuilder(NewsWorkbenchActivity.this).setTitle("Internet check result").setMessage(details).setPositiveButton("Close",null).show();}});}
                    public void onError(String message){runOnUiThread(()->{if(alive()){fetch.setEnabled(true);status.setText(message);}});}
                });
            });
            schedule.setOnClickListener(v->schedule());controls.addView(fetch);if("admin".equals(ProductSession.role(this)))controls.addView(schedule);
        }
        root.addView(controls);
        RecyclerView list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));list.setAdapter(rows);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){rows.filter();}});
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int n,long id){rows.filter();}public void onNothingSelected(AdapterView<?> p){}});load();
    }
    private void load(){if(loading||!alive())return;loading=true;status.setText("Refreshing news review queue…");api.newsSignals(new ProductApiClient.ArrayCallback(){public void onSuccess(JSONArray values){runOnUiThread(()->{if(!alive())return;loading=false;signals.clear();for(int i=0;i<values.length();i++)if(values.optJSONObject(i)!=null)signals.add(values.optJSONObject(i));rows.filter();status.setText(signals.isEmpty()?"No news signals available. An administrator can check configured internet feeds.":signals.size()+" latest signals. Tap a story to inspect its source and review state.");});}public void onError(String message){runOnUiThread(()->{if(alive()){loading=false;status.setText(message+" Tap here to retry.");}});}});}
    private String routingEffect(JSONObject signal){
        switch(signal.optString("routing_effect")){
            case "EXCLUDES_INTERSECTING_ROUTES":return "LIVE RESTRICTION: intersecting routes are excluded.";
            case "INCREASES_NEARBY_ROUTE_RISK":return "LIVE HAZARD: nearby route exposure is increased.";
            case "INACTIVE":return "NOT ACTIVE: the published hazard has expired or been resolved.";
            default:return "NOT ON MAP YET: an official must confirm the exact road and publish.";
        }
    }
    private void openHazard(JSONObject signal,String id){startActivity(new Intent(this,MapsActivity.class).putExtra("focus_hazard_id",id).putExtra("focus_latitude",signal.optDouble("latitude")).putExtra("focus_longitude",signal.optDouble("longitude")));}
    private void review(JSONObject signal){LinearLayout content=column();content.addView(text(signal.optString("headline"),20));content.addView(text(signal.optString("publisher")+"\nPublished: "+JsonDisplay.text(signal,"published_at","Unknown")+"\nLocality hint: "+JsonDisplay.text(signal,"location_text","Not located")+"\n"+signal.optString("category")+" · "+signal.optString("review_status"),13));
        content.addView(text(routingEffect(signal),15));
        content.addView(text(signal.optString("summary"),15));content.addView(text("Classification is keyword-based, not a trained news model. Match score: "+Math.round(signal.optDouble("confidence")*100)+"%. A locality hint is not an exact incident location.",12));
        MaterialButton source=button("Read source article");source.setOnClickListener(v->openSource(signal.optString("source_url")));content.addView(source);
        String linked=JsonDisplay.text(signal,"linked_hazard_id","");
        if(!linked.isEmpty()){MaterialButton map=button("View published hazard on map");content.addView(map);map.setEnabled(!"INACTIVE".equals(signal.optString("routing_effect")));map.setOnClickListener(v->openHazard(signal,linked));content.addView(text("Already published. Resolve or change severity through the official hazard lifecycle; dismissing this article cannot silently remove a road restriction.",13));}
        else {String[] labels={"Retain as relevant","Request field check","Dismiss signal"},states={"RELEVANT","NEEDS_FIELD_CHECK","DISMISSED"};for(int i=0;i<labels.length;i++){final String state=states[i];MaterialButton action=button(labels[i]);content.addView(action);action.setOnClickListener(v->{action.setEnabled(false);api.reviewNews(signal.optString("id"),state,new ProductApiClient.ObjectCallback(){public void onSuccess(JSONObject result){runOnUiThread(()->{if(alive()){Toast.makeText(NewsWorkbenchActivity.this,"Review saved",Toast.LENGTH_SHORT).show();load();action.setEnabled(true);}});}public void onError(String message){runOnUiThread(()->{if(alive()){action.setEnabled(true);Toast.makeText(NewsWorkbenchActivity.this,message,Toast.LENGTH_LONG).show();}});}});});}
            if("government".equals(ProductSession.role(this))){MaterialButton publish=button("Verify location & publish hazard");content.addView(publish);publish.setOnClickListener(v->{if(storyDialog!=null)storyDialog.dismiss();publish(signal);});}
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(content);if(storyDialog!=null)storyDialog.dismiss();storyDialog=new MaterialAlertDialogBuilder(this).setView(scroll).setNegativeButton("Close",null).show();
    }
    private void openSource(String value){Uri uri=Uri.parse(value);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null){Toast.makeText(this,"Source link must be HTTPS",Toast.LENGTH_LONG).show();return;}try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){Toast.makeText(this,"No browser available",Toast.LENGTH_SHORT).show();}}
    private void publish(JSONObject signal){if(!"government".equals(ProductSession.role(this)))return;MapLibre.getInstance(this);LinearLayout form=column();form.addView(text("Confirm the exact affected road",19));form.addView(text("Read the article, confirm this is a current incident, then pin the affected road. News is not automatically treated as a road closure.",13));
        final Double[] location={null,null}; final boolean[] disposed={false},busy={false};final MapLibreMap[] map={null};final Style[] style={null};
        TextInputEditText address=input("Affected road / location",1),note=input("How did you verify the incident is current?",3),radius=input("Closure buffer metres (optional, 10–500)",1);radius.setInputType(8194);
        MaterialButton find=button("Find affected place");form.addView(find);
        MapView preview=new MapView(this);preview.onCreate(null);form.addView(preview,new LinearLayout.LayoutParams(-1,dp(210)));preview.setOnTouchListener((v,event)->{v.getParent().requestDisallowInterceptTouchEvent(event.getAction()!=MotionEvent.ACTION_UP&&event.getAction()!=MotionEvent.ACTION_CANCEL);return false;});
        java.util.function.Consumer<PlaceSearchResult> pin=place->{if(disposed[0]||busy[0])return;location[0]=place.getLatitude();location[1]=place.getLongitude();address.setText(place.getDisplayName());if(style[0]!=null){if(style[0].getLayer("news-pin")!=null)style[0].removeLayer("news-pin");if(style[0].getSource("news-location")!=null)style[0].removeSource("news-location");style[0].addSource(new GeoJsonSource("news-location",Feature.fromGeometry(Point.fromLngLat(location[1],location[0]))));style[0].addLayer(new CircleLayer("news-pin","news-location").withProperties(org.maplibre.android.style.layers.PropertyFactory.circleColor(android.graphics.Color.parseColor("#B93D36")),org.maplibre.android.style.layers.PropertyFactory.circleRadius(9f)));}if(map[0]!=null)map[0].animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location[0],location[1]),15));};
        preview.getMapAsync(value->{if(disposed[0])return;map[0]=value;value.setStyle(new Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty"),ready->{if(disposed[0])return;style[0]=ready;value.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(19.05,73.0),10));value.addOnMapClickListener(point->{pin.accept(new PlaceSearchResult(String.format(Locale.US,"Road at %.6f, %.6f",point.getLatitude(),point.getLongitude()),point.getLatitude(),point.getLongitude()));return true;});});});
        find.setOnClickListener(v->new PlaceSearchSheet(this,new OpenMapService(),new OfflineRouter(this),"Affected location",JsonDisplay.text(signal,"location_text",""),19.05,73,new PlaceSearchSheet.Listener(){public void onPlace(PlaceSearchResult place){pin.accept(place);}public void onMapPin(){Toast.makeText(NewsWorkbenchActivity.this,"Tap the exact affected road in the preview map",Toast.LENGTH_LONG).show();}public void onDeviceLocation(){Toast.makeText(NewsWorkbenchActivity.this,"Select the incident location explicitly, not the reviewer’s current location",Toast.LENGTH_LONG).show();}}).show());
        form.addView(address);form.addView(text("Severity",12));Spinner severity=spinner(new String[]{"LOW","MODERATE","HIGH","CRITICAL"});severity.setSelection(1);form.addView(severity);form.addView(text("Road status",12));Spinner road=spinner(new String[]{"CAUTION","UNSAFE","WORK_REQUIRED","CLOSED"});form.addView(road);form.addView(radius);form.addView(text("Automatically expire after",12));Spinner expiry=spinner(new String[]{"3 hours","6 hours","12 hours","24 hours"});expiry.setSelection(2);form.addView(expiry);form.addView(note);CheckBox current=new CheckBox(this);current.setText("I verified the current incident and the exact affected road.");form.addView(current);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle("Publish verified news hazard").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Publish",null).create();
        if(publicationDialog!=null)publicationDialog.dismiss();publicationDialog=dialog;
        dialog.setOnDismissListener(d->{disposed[0]=true;preview.onPause();preview.onStop();preview.onDestroy();if(publicationDialog==dialog)publicationDialog=null;});
        dialog.setOnShowListener(d->{preview.onStart();preview.onResume();dialog.getButton(-1).setOnClickListener(v->{if(busy[0])return;if(location[0]==null){address.setError("Pin the affected road first");return;}if(address.getText().toString().trim().length()<5){address.setError("Enter a location");return;}if(note.getText().toString().trim().length()<10){note.setError("Explain your current-incident verification");return;}if(!current.isChecked()){current.setError("Confirmation is required");return;}
            try{JSONObject payload=new JSONObject().put("latitude",location[0]).put("longitude",location[1]).put("address",address.getText().toString().trim()).put("severity",severity.getSelectedItem().toString()).put("road_status",road.getSelectedItem().toString()).put("valid_hours",new int[]{3,6,12,24}[expiry.getSelectedItemPosition()]).put("verification_note",note.getText().toString().trim()).put("confirmed_current",true);String buffer=radius.getText().toString().trim();if(!buffer.isEmpty())payload.put("no_go_radius_m",Double.parseDouble(buffer));busy[0]=true;dialog.getButton(-1).setEnabled(false);dialog.getButton(-1).setText("Publishing…");
                api.publishNews(signal.optString("id"),payload,new ProductApiClient.ObjectCallback(){public void onSuccess(JSONObject hazard){runOnUiThread(()->{if(!alive())return;dialog.dismiss();load();
                    String effect="CLOSED".equals(hazard.optString("road_status"))||("CRITICAL".equals(hazard.optString("severity"))&&"UNSAFE".equals(hazard.optString("road_status")))?"Routes crossing the official exclusion buffer are now excluded.":"Nearby routes now receive increased assessed exposure.";
                    new MaterialAlertDialogBuilder(NewsWorkbenchActivity.this).setTitle("Hazard published to backend")
                            .setMessage(effect+"\nThe source link is attached to the map marker. Open the map and compare a route to inspect the impact. This hazard automatically expires at "+JsonDisplay.text(hazard,"valid_until","its configured expiry")+".")
                            .setNegativeButton("Stay here",null).setPositiveButton("View on map",(d,w)->openHazard(hazard,hazard.optString("id"))).show();
                });}public void onError(String message){runOnUiThread(()->{if(!alive()||disposed[0])return;busy[0]=false;dialog.getButton(-1).setEnabled(true);dialog.getButton(-1).setText("Retry publish");note.setError(message);});}});
            }catch(Exception error){radius.setError("Enter a valid radius between 10 and 500 metres");}
        });});dialog.show();
    }
    private void schedule(){api.newsSettings(new ProductApiClient.ObjectCallback(){public void onSuccess(JSONObject settings){runOnUiThread(()->{if(!alive())return;new MaterialAlertDialogBuilder(NewsWorkbenchActivity.this).setTitle("News check interval").setMessage("The admin controls the ingestion interval. External scheduler configuration must also be active on free cloud hosting. Last successful ingestion: "+JsonDisplay.text(settings,"last_ingested_at","Not yet recorded")).setNegativeButton("Close",null).setNeutralButton("12 hours",(d,w)->saveSchedule(12)).setPositiveButton("24 hours",(d,w)->saveSchedule(24)).show();});}public void onError(String message){runOnUiThread(()->{if(alive())status.setText(message);});}});}
    private void saveSchedule(int hours){api.updateNewsSettings(hours,new ProductApiClient.ObjectCallback(){public void onSuccess(JSONObject value){runOnUiThread(()->{if(alive())status.setText("News interval saved: "+hours+" hours");});}public void onError(String message){runOnUiThread(()->{if(alive())status.setText(message);});}});}
    @Override protected void onDestroy(){if(publicationDialog!=null)publicationDialog.dismiss();if(storyDialog!=null)storyDialog.dismiss();super.onDestroy();}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(16),dp(12),dp(16),dp(12));v.setBackgroundColor(ContextCompat.getColor(this,R.color.background));return v;}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(ContextCompat.getColor(this,R.color.text_primary));v.setPadding(0,dp(6),0,dp(6));return v;}
    private MaterialButton button(String value){MaterialButton v=new MaterialButton(this,null,com.google.android.material.R.attr.borderlessButtonStyle);v.setText(value);v.setAllCaps(false);v.setLetterSpacing(0);v.setTextSize(13);return v;}
    private TextInputEditText input(String hint,int lines){TextInputEditText v=new TextInputEditText(this);v.setHint(hint);v.setMinLines(lines);v.setMaxLines(4);v.setTextSize(14);v.setInputType(lines>1?131073:1);return v;}
    private Spinner spinner(String[] values){Spinner v=new Spinner(this);v.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));return v;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private final class Rows extends RecyclerView.Adapter<Row>{
        final List<JSONObject> visible=new ArrayList<>();
        void filter(){
            visible.clear();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            String state=filters[filter.getSelectedItemPosition()];
            for(JSONObject signal:signals)if(("All".equals(state)||state.equals(signal.optString("review_status")))&&
                (signal.optString("headline")+" "+signal.optString("publisher")+" "+JsonDisplay.text(signal,"location_text","")).toLowerCase(Locale.ROOT).contains(query))visible.add(signal);
            notifyDataSetChanged();
        }
        public Row onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout view=column();view.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            TextView headline=text("",16),meta=text("",12);headline.setMaxLines(3);headline.setEllipsize(TextUtils.TruncateAt.END);
            view.addView(headline);view.addView(meta);return new Row(view,headline,meta);
        }
        public void onBindViewHolder(Row row,int position){
            JSONObject value=visible.get(position);row.headline.setText(value.optString("headline"));
            row.meta.setText(value.optString("review_status")+" · "+value.optString("category")+"\n"+
                value.optString("publisher")+"\n"+JsonDisplay.text(value,"location_text","Location requires review")+"\n"+routingEffect(value));
            row.itemView.setOnClickListener(v->review(value));
        }
        public int getItemCount(){return visible.size();}
    }
    private static class Row extends RecyclerView.ViewHolder{final TextView headline,meta;Row(View v,TextView h,TextView m){super(v);headline=h;meta=m;}}
}
