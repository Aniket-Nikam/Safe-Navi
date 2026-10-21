package com.safenavi.app;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.widget.TextView;
import android.view.View;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Rect;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.safenavi.app.product.ProductSession;
import com.safenavi.app.safety.model.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Isolated UI checks. No fictional hazard, journey or place is written to the cloud. */
@RunWith(AndroidJUnit4.class)
public class RouteActionsInstrumentedTest {
    private ActivityScenario<MapsActivity> openRoute() {
        ProductSession.clear(InstrumentationRegistry.getInstrumentation().getTargetContext());
        ActivityScenario<MapsActivity> scenario=ActivityScenario.launch(new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),MapsActivity.class));
        scenario.onActivity(activity -> {
            try {
                ProductSession.clear(activity);
                RouteCandidate road=new RouteCandidate("ui-test",20.4,5100,Collections.emptyList());
                set(activity,"displayedRoute",new RouteSafetyResult(road,RouteProfile.BALANCED,45,0));
                set(activity,"lastDestination",new PlaceSearchResult("SIES GST, Nerul",19.042644,73.023428));
                set(activity,"selectedSourcePlace",new PlaceSearchResult("Vashi Station",19.0637159,73.0002509));
                set(activity,"lastRouteSummary","Safe-Navi balanced route\nVashi to SIES GST\n5.1 km · 20 min (estimated)");
                ((TextView)activity.findViewById(R.id.routeResultRisk)).setText("TECHNICAL_DIAGNOSTICS synthetic area-cell +14.0");
                call(activity,"updateJourneySummary"); call(activity,"showRouteDetailsSheet");
            } catch(Exception error) { throw new AssertionError(error); }
        }); return scenario;
    }

    @Test public void simpleDetailsShowDistanceAndKeepDiagnosticsOptIn() throws Exception {
        try(ActivityScenario<MapsActivity> scenario=openRoute()) {
            visible("5.1 km · 20 min"); visible("Open directions"); tap("About route safety");
            visible("The current model uses simulated area data. It cannot tell you the chance of a crime happening.");
            technicalVisibility(scenario,View.GONE);
            tap("Show technical details"); technicalVisibility(scenario,View.VISIBLE);
        }
    }

    @Test public void saveDestinationExplainsSignInInsteadOfFailingSilently() throws Exception {
        try(ActivityScenario<MapsActivity> scenario=openRoute()) {
            tap("Save destination"); visible("Sign in to save places"); tap("Not now");
        }
    }

    @Test public void sharedJourneyExplainsSignInInsteadOfFailingSilently() throws Exception {
        try(ActivityScenario<MapsActivity> scenario=openRoute()) {
            tap("Share journey"); visible("Sign in to share a journey");
        }
    }

    @Test public void shareRouteDispatchesTheConciseTextToAndroid() throws Exception {
        AtomicReference<Intent> sent=new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor=intercept(Intent.ACTION_CHOOSER,sent);
        try(ActivityScenario<MapsActivity> scenario=openRoute()) {
            tap("Share route");
            assertNotNull("Share must invoke Android, not just close the sheet",sent.get());
            Intent payload=sent.get().getParcelableExtra(Intent.EXTRA_INTENT);
            assertEquals(Intent.ACTION_SEND,payload.getAction());
            assertTrue(payload.getStringExtra(Intent.EXTRA_TEXT).contains("5.1 km"));
            assertFalse(payload.getStringExtra(Intent.EXTRA_TEXT).contains("synthetic area-cell"));
        } finally { InstrumentationRegistry.getInstrumentation().removeMonitor(monitor); }
    }

    @Test public void directionsDispatchBothEndpointsAfterConsent() throws Exception {
        AtomicReference<Intent> sent=new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor=intercept(Intent.ACTION_VIEW,sent);
        try(ActivityScenario<MapsActivity> scenario=openRoute()) {
            tap("Open directions"); visible("Open Google Maps directions?");
            assertNull(sent.get());
            tap("Open directions");
            assertNotNull(sent.get());
            assertEquals("19.063716,73.000251",sent.get().getData().getQueryParameter("origin"));
            assertEquals("19.042644,73.023428",sent.get().getData().getQueryParameter("destination"));
        } finally { InstrumentationRegistry.getInstrumentation().removeMonitor(monitor); }
    }

    @Test public void backToMapClosesSheetAndPreservesDistance() throws Exception {
        try(ActivityScenario<MapsActivity> scenario=openRoute()) {
            tap("Back to map");
            scenario.onActivity(activity -> {
                try { assertNull(get(activity,"activeMapSheet")); }
                catch(Exception error) { throw new AssertionError(error); }
                assertTrue(activity.findViewById(R.id.routeDistanceTime).isShown());
                assertEquals("5.1 km · 20 min",((TextView)activity.findViewById(R.id.routeDistanceTime)).getText().toString());
            });
        }
    }

    private static Instrumentation.ActivityMonitor intercept(String action,AtomicReference<Intent> sent) {
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if(!action.equals(intent.getAction())) return null;
                sent.set(intent); return new Instrumentation.ActivityResult(Activity.RESULT_OK,null);
            }
        };
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor); return monitor;
    }
    // Espresso 3.5 uses a removed InputManager API on this API 36 emulator. Use the
    // public UiAutomation input API instead, retaining real screen-coordinate taps.
    private static AccessibilityNodeInfo visible(String text) {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        for(int attempt=0;attempt<50;attempt++) {
            instrumentation.waitForIdleSync();
            AccessibilityNodeInfo root=instrumentation.getUiAutomation().getRootInActiveWindow();
            if(root!=null) for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)) {
                if(text.equalsIgnoreCase(node.getText()==null?"":node.getText().toString()) && node.isVisibleToUser()) return node;
            }
            android.os.SystemClock.sleep(100);
        }
        throw new AssertionError("Expected visible text: "+text);
    }
    private static void tap(String text) {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        AccessibilityNodeInfo node=visible(text); Rect bounds=new Rect(); node.getBoundsInScreen(bounds);
        assertFalse("Button must have a real hit area: "+text,bounds.isEmpty());
        long time=android.os.SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0);
        MotionEvent up=MotionEvent.obtain(time,time+80,MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0);
        try {
            assertTrue(instrumentation.getUiAutomation().injectInputEvent(down,true));
            assertTrue(instrumentation.getUiAutomation().injectInputEvent(up,true));
        } finally { down.recycle(); up.recycle(); }
        instrumentation.waitForIdleSync();
    }
    private static void technicalVisibility(ActivityScenario<MapsActivity> scenario,int expected) {
        scenario.onActivity(activity -> {
            try {
                android.app.Dialog sheet=(android.app.Dialog)get(activity,"activeMapSheet");
                View technical=sheet.getWindow().getDecorView().findViewWithTag("routeTechnicalDetails");
                assertNotNull(technical); assertEquals(expected,technical.getVisibility());
                if(expected==View.VISIBLE) assertTrue(technical.isShown());
            } catch(Exception error) { throw new AssertionError(error); }
        });
    }
    private static void set(Object object,String name,Object value) throws Exception { Field field=MapsActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(object,value); }
    private static Object get(Object object,String name) throws Exception { Field field=MapsActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(object); }
    private static void call(Object object,String name) throws Exception { Method method=MapsActivity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(object); }
}
