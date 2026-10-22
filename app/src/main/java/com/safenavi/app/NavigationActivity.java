package com.safenavi.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.location.Location;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safenavi.app.navigation.*;
import com.safenavi.app.safety.model.*;
import com.safenavi.app.safety.model.HazardLocation.GeoPoint;
import com.safenavi.app.safety.service.RouteUserSummary;
import com.safenavi.app.safety.presentation.RouteColors;
import java.util.*;
import org.json.*;
import org.maplibre.android.MapLibre;
import org.maplibre.android.maps.*;
import org.maplibre.android.camera.*;
import org.maplibre.android.geometry.*;
import org.maplibre.android.style.layers.*;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.*;
import org.maplibre.geojson.Point;
import static org.maplibre.android.style.layers.PropertyFactory.*;

/** Foreground prototype navigation. Demo coordinates never enter GPS or cloud storage. */
public class NavigationActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION=811;
    private MapView mapView;
    private MapLibreMap map;
    private Style style;
    private NavigationProgress progress;
    private JSONObject snapshot;
    private boolean demo,paused,foreground,follow=true,voice,ttsReady,hasFix,offRoute;
    private int speed=20,lastSpoken=-2;
    private String routeFile;
    private long lastFixAt;
    private final Handler timer=new Handler(Looper.getMainLooper());
    private TextToSpeech speech;
    private FusedLocationProviderClient locations;
    private final LocationCallback locationUpdates=new LocationCallback() {
        @Override public void onLocationResult(LocationResult values) {
            if(!foreground||demo||progress==null) return;
            Location location=values.getLastLocation(); if(location==null) return;
            long age=SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos();
            if(age>20_000_000_000L || (location.hasAccuracy()&&location.getAccuracy()>50)) {
                status("Waiting for a more accurate location. Progress is paused."); return;
            }
            hasFix=true;lastFixAt=SystemClock.elapsedRealtime(); double separation=progress.match(location.getLatitude(),location.getLongitude()); offRoute=separation>60;
            updateDisplay();
            drawPosition(new GeoPoint(location.getLatitude(),location.getLongitude()),location.hasBearing()?location.getBearing():progress.bearing());
            if(offRoute) status("You are away from the selected route. Return to the planner to choose a new route.");
        }
    };
    private final Runnable gpsWatchdog=new Runnable() {
        @Override public void run() {
            if(!foreground||demo||progress.arrived()) return;
            if(lastFixAt==0 || SystemClock.elapsedRealtime()-lastFixAt>15000) {
                hasFix=false;updateDisplay();status("Waiting for a recent, accurate location. Progress is paused.");
            }
            timer.postDelayed(this,5000);
        }
    };
    private final Runnable tick=new Runnable() {
        @Override public void run() {
            if(!foreground||!demo||progress==null) return;
            if(style==null) { timer.postDelayed(this,500);return; }
            if(!paused&&!progress.arrived()) {
                double metersPerSecond=progress.length()/Math.max(60,progress.route.getTravelMinutes()*60);
                progress.advance(progress.progress()+metersPerSecond*speed);
                updateDisplay(); drawPosition(progress.position(),progress.bearing());
            }
            if(!progress.arrived()) timer.postDelayed(this,1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); MapLibre.getInstance(this); setContentView(R.layout.activity_navigation);
        mapView=findViewById(R.id.navigationMap); mapView.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        routeFile=getIntent().getStringExtra("route_file"); demo=getIntent().getBooleanExtra("demo",true);
        try {
            snapshot=NavigationRouteStore.read(this,routeFile); progress=new NavigationProgress(NavigationRouteStore.route(snapshot));
        } catch(Exception error) {
            new MaterialAlertDialogBuilder(this).setTitle("Navigation unavailable").setMessage("Please return to the map and plan this route again.")
                    .setPositiveButton("Back to map",(d,w)->finish()).setOnCancelListener(d->finish()).show(); return;
        }
        if(state!=null) { progress.advance(state.getDouble("progress",0)); paused=state.getBoolean("paused"); speed=state.getInt("speed",20); follow=state.getBoolean("follow",true); }
        locations=LocationServices.getFusedLocationProviderClient(this);
        ((TextView)findViewById(R.id.navigationMode)).setText(demo?"DEMO PREVIEW • SIMULATED LOCATION":"LIVE LOCATION • FOREGROUND PREVIEW");
        ((TextView)findViewById(R.id.navigationDestination)).setText("To "+snapshot.optString("to")+" · "+snapshot.optString("profile").toLowerCase(Locale.US));
        findViewById(R.id.navigationDemoControls).setVisibility(demo?View.VISIBLE:View.GONE);
        MaterialButton pause=findViewById(R.id.navigationPause),speedButton=findViewById(R.id.navigationSpeed);
        pause.setText(paused?"Resume":"Pause"); speedButton.setText(speed+"x speed");
        pause.setOnClickListener(v->{ paused=!paused; pause.setText(paused?"Resume":"Pause"); updateDisplay(); });
        findViewById(R.id.navigationNext).setOnClickListener(v->{
            if(progress.arrived()) { progress=new NavigationProgress(progress.route); lastSpoken=-2; }
            else progress.advance(Math.max(progress.progress()+35,progress.progress()+progress.toNextTurn()-25));
            updateDisplay(); drawPosition(progress.position(),progress.bearing());
            timer.removeCallbacks(tick); if(foreground&&!progress.arrived())timer.postDelayed(tick,1000);
        });
        speedButton.setOnClickListener(v->{speed=speed==5?20:speed==20?60:5;speedButton.setText(speed+"x speed");updateDisplay();});
        findViewById(R.id.navigationFollow).setOnClickListener(v->{
            follow=!follow; ((MaterialButton)v).setText(follow?"Overview":"Recenter");
            if(follow&&demo) drawPosition(progress.position(),progress.bearing()); else if(!follow) fitRoute();
        });
        findViewById(R.id.navigationVoice).setOnClickListener(v->{
            if(!ttsReady) { Toast.makeText(this,"Voice guidance is unavailable. On-screen directions still work.",Toast.LENGTH_LONG).show(); return; }
            voice=!voice; ((MaterialButton)v).setText(voice?"Voice on":"Voice off");
            if(!voice) speech.stop(); else { lastSpoken=-2; updateDisplay(); }
        });
        findViewById(R.id.navigationExit).setOnClickListener(v->finish());
        speech=new TextToSpeech(this,result->{
            if(result==TextToSpeech.SUCCESS&&speech!=null&&!isDestroyed()) {
                int supported=speech.setLanguage(Locale.ENGLISH); ttsReady=supported!=TextToSpeech.LANG_MISSING_DATA&&supported!=TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
        speech.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { }
            @Override public void onError(String id) {runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;voice=false;
                ((MaterialButton)findViewById(R.id.navigationVoice)).setText("Voice off");
                Toast.makeText(NavigationActivity.this,"Voice could not play. Follow the on-screen directions.",Toast.LENGTH_LONG).show();
            });}
        });
        updateDisplay();
        mapView.getMapAsync(value->{
            if(isDestroyed()) return; map=value;
            map.setStyle(new Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty"),loaded->{
                if(isDestroyed()||isFinishing()) return; style=loaded; renderRoute();
                if(demo) drawPosition(progress.position(),progress.bearing()); else fitRoute();
            });
            map.addOnMapClickListener(point->{
                if(style==null) return false;
                List<Feature> hits=map.queryRenderedFeatures(map.getProjection().toScreenLocation(point),"navigation-alerts");
                if(hits.isEmpty()) return false;
                Feature hit=hits.get(0);
                new MaterialAlertDialogBuilder(this).setTitle(hit.getStringProperty("title")).setMessage(hit.getStringProperty("reason"))
                        .setPositiveButton("Close",null).show();return true;
            });
        });
    }
    private void renderRoute() {
        List<Point> shape=new ArrayList<>(); for(GeoPoint point:progress.route.getGeometry()) shape.add(Point.fromLngLat(point.getLongitude(),point.getLatitude()));
        style.addSource(new GeoJsonSource("navigation-route",Feature.fromGeometry(LineString.fromLngLats(shape))));
        int color=RouteColors.forProfileName(snapshot.optString("profile"));
        style.addLayer(new LineLayer("navigation-route-outline","navigation-route").withProperties(lineColor(RouteColors.CASING),lineWidth(RouteColors.SELECTED_WIDTH+RouteColors.CASING_EXTRA_WIDTH)));
        style.addLayer(new LineLayer("navigation-route-line","navigation-route").withProperties(lineColor(color),lineWidth(RouteColors.SELECTED_WIDTH),lineJoin(Property.LINE_JOIN_ROUND)));
        List<Feature> endpoints=new ArrayList<>();
        for(int i=0;i<2;i++) {
            GeoPoint p=progress.route.getGeometry().get(i==0?0:progress.route.getGeometry().size()-1);
            String label=i==0?"A":"B";style.addImage("navigation-"+label,pin(label,i==0?"#08735C":"#BF3F38"));
            Feature feature=Feature.fromGeometry(Point.fromLngLat(p.getLongitude(),p.getLatitude()));feature.addStringProperty("icon","navigation-"+label);endpoints.add(feature);
        }
        style.addSource(new GeoJsonSource("navigation-endpoints",FeatureCollection.fromFeatures(endpoints)));
        style.addLayer(new SymbolLayer("navigation-endpoints","navigation-endpoints").withProperties(iconImage(org.maplibre.android.style.expressions.Expression.get("icon")),iconAllowOverlap(true),iconIgnorePlacement(true)));
        List<Feature> alerts=new ArrayList<>(); JSONArray values=snapshot.optJSONArray("alerts");
        if(values!=null) for(int i=0;i<values.length();i++) {
            JSONObject alert=values.optJSONObject(i);if(alert==null)continue;
            Feature feature=Feature.fromGeometry(Point.fromLngLat(alert.optDouble("lon"),alert.optDouble("lat")));
            feature.addStringProperty("title",alert.optString("title"));feature.addStringProperty("reason",alert.optString("reason"));alerts.add(feature);
        }
        style.addImage("navigation-alert",pin("!","#BF3F38"));
        style.addSource(new GeoJsonSource("navigation-alerts",FeatureCollection.fromFeatures(alerts)));
        style.addLayer(new SymbolLayer("navigation-alerts","navigation-alerts").withProperties(iconImage("navigation-alert"),iconAllowOverlap(true),iconIgnorePlacement(true)));
        style.addImage("navigation-arrow",arrow());
        style.addSource(new GeoJsonSource("navigation-position"));
        style.addLayer(new SymbolLayer("navigation-position","navigation-position").withProperties(iconImage("navigation-arrow"),iconAllowOverlap(true),iconIgnorePlacement(true),iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP)));
        mapView.post(()->{
            if(isDestroyed()||map==null) return;
            int gap=Math.round(12*getResources().getDisplayMetrics().density);
            int bottom=mapView.getHeight()-findViewById(R.id.navigationBottom).getTop()+gap;
            map.getUiSettings().setAttributionMargins(gap,0,0,bottom);
            map.getUiSettings().setLogoMargins(gap,0,0,bottom+Math.round(28*getResources().getDisplayMetrics().density));
            map.getUiSettings().setCompassMargins(0,findViewById(R.id.navigationTop).getBottom()+gap,gap,0);
        });
    }
    private Bitmap pin(String text,String color) {
        float density=getResources().getDisplayMetrics().density; int size=Math.round(32*density);
        Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);canvas.drawCircle(size/2f,size/2f,size/2f,paint);paint.setColor(Color.parseColor(color));canvas.drawCircle(size/2f,size/2f,size/2f-2*density,paint);
        paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(17*density);paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(text,size/2f,size/2f-(paint.ascent()+paint.descent())/2,paint);return bitmap;
    }
    private Bitmap arrow() {
        float density=getResources().getDisplayMetrics().density;int size=Math.round(44*density);
        Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path=new Path();path.moveTo(size*.5f,size*.08f);path.lineTo(size*.85f,size*.86f);path.lineTo(size*.5f,size*.68f);path.lineTo(size*.15f,size*.86f);path.close();
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5*density);canvas.drawPath(path,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.parseColor("#1664CC"));canvas.drawPath(path,paint);return bitmap;
    }
    private void drawPosition(GeoPoint point,double bearing) {
        if(style==null||!style.isFullyLoaded()||isDestroyed()) return;
        GeoJsonSource source=style.getSourceAs("navigation-position"); if(source==null)return;
        source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(point.getLongitude(),point.getLatitude())));
        SymbolLayer layer=style.getLayerAs("navigation-position");if(layer!=null)layer.setProperties(iconRotate((float)bearing));
        if(follow) map.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(new LatLng(point.getLatitude(),point.getLongitude())).zoom(16.5).bearing(bearing).tilt(30).build()),800);
    }
    private void fitRoute() {
        if(map==null) return;
        mapView.post(()->{
            if(isDestroyed()||style==null) return;
            LatLngBounds.Builder bounds=new LatLngBounds.Builder();for(GeoPoint point:progress.route.getGeometry()) bounds.include(new LatLng(point.getLatitude(),point.getLongitude()));
            int gap=Math.round(20*getResources().getDisplayMetrics().density);
            map.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder(map.getCameraPosition()).bearing(0).tilt(0).build()));
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),gap,findViewById(R.id.navigationTop).getBottom()+gap,gap,mapView.getHeight()-findViewById(R.id.navigationBottom).getTop()+gap),700);
        });
    }
    private void status(String text) { ((TextView)findViewById(R.id.navigationStatus)).setText(text); }
    private void updateDisplay() {
        boolean arrived=progress.arrived();
        ((TextView)findViewById(R.id.navigationInstruction)).setText(arrived?progress.instruction():!demo&&!hasFix?"Waiting for your location":offRoute?"Away from selected route":progress.instruction());
        double turn=progress.toNextTurn();
        ((TextView)findViewById(R.id.navigationTurnDistance)).setText(arrived?"Route complete":!demo&&(!hasFix||offRoute)?"Progress paused":turn<1000?String.format(Locale.US,"In %.0f m",Math.max(0,turn)):"In "+RouteUserSummary.distance(turn));
        ((TextView)findViewById(R.id.navigationRemaining)).setText(RouteUserSummary.distance(progress.remainingMeters())+" · "+RouteUserSummary.duration(progress.remainingMinutes())+" left");
        status(demo?(arrived?"Demo complete. No actual travel was recorded.":paused?"Demo paused. No GPS or live traffic.":"Simulating at "+speed+"x speed. Not GPS or live traffic.")
                :arrived?"End of selected route. Check your surroundings.":hasFix?"Foreground prototype. No live traffic or automatic rerouting.":"Location permission and a recent, accurate fix are needed. Demo works indoors.");
        ((MaterialButton)findViewById(R.id.navigationNext)).setText(arrived?"Replay":"Next turn");
        if(voice&&ttsReady&&foreground&&!offRoute&&(demo||hasFix)) {
            int step=arrived?Integer.MAX_VALUE:progress.nextStepIndex();
            if(lastSpoken!=step) {lastSpoken=step;speech.speak((demo?"Demo. ":"")+progress.instruction(),TextToSpeech.QUEUE_FLUSH,null,"safenavi-turn");}
        }
        if(arrived&&!demo&&locations!=null) locations.removeLocationUpdates(locationUpdates);
    }
    private void startGps() {
        if(demo||!foreground||progress==null) return;
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION);return;
        }
        try {
            locations.requestLocationUpdates(new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,2000).setMinUpdateIntervalMillis(1000).build(),locationUpdates,Looper.getMainLooper())
                    .addOnFailureListener(error->status("Location updates could not start. Check location settings, or use Demo preview."));
        } catch(SecurityException error) {status("Location permission is required. Use Demo preview without GPS.");}
    }
    @Override public void onRequestPermissionsResult(int request,@NonNull String[] permissions,@NonNull int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==LOCATION_PERMISSION) {if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startGps();else status("Location permission declined. Exit and choose Demo preview to demonstrate without GPS.");}
    }
    @Override protected void onStart(){super.onStart();mapView.onStart();}
    @Override protected void onResume(){super.onResume();mapView.onResume();foreground=true;if(progress!=null){if(demo)timer.postDelayed(tick,1000);else {hasFix=false;lastFixAt=0;updateDisplay();if(!progress.arrived()){startGps();timer.postDelayed(gpsWatchdog,5000);}}}}
    @Override protected void onPause(){foreground=false;timer.removeCallbacksAndMessages(null);if(locations!=null)locations.removeLocationUpdates(locationUpdates);if(speech!=null)speech.stop();mapView.onPause();super.onPause();}
    @Override protected void onStop(){mapView.onStop();super.onStop();}
    @Override public void onLowMemory(){super.onLowMemory();mapView.onLowMemory();}
    @Override protected void onSaveInstanceState(@NonNull Bundle out){super.onSaveInstanceState(out);mapView.onSaveInstanceState(out);if(progress!=null)out.putDouble("progress",progress.progress());out.putBoolean("paused",paused);out.putInt("speed",speed);out.putBoolean("follow",follow);}
    @Override protected void onDestroy(){timer.removeCallbacksAndMessages(null);if(speech!=null)speech.shutdown();style=null;mapView.onDestroy();if(isFinishing())NavigationRouteStore.discard(this,routeFile);super.onDestroy();}
}
