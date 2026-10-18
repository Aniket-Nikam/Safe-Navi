package com.safenavi.app.product;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import com.safenavi.app.*;

/** A rejected session gets one foreground sign-in flow; drafts retain their account identity. */
public final class SessionRecovery implements Application.ActivityLifecycleCallbacks {
    private static SessionRecovery instance;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WeakReference<Activity> foreground=new WeakReference<>(null);
    public static void install(Application app){instance=new SessionRecovery();app.registerActivityLifecycleCallbacks(instance);}
    public static void rejected(){if(instance!=null)instance.main.post(instance::recover);}
    private void recover(){Activity activity=foreground.get();if(activity==null||activity.isFinishing()||activity.isDestroyed()||!ProductSession.needsSignIn(activity))return;
        if(activity instanceof LoginActivity||activity instanceof LoginTypeActivity||activity instanceof SignUpActivity||activity instanceof SplashActivity)return;
        // Keep the previous token until the screen has saved its draft in onPause.
        activity.startActivity(new Intent(activity,LoginActivity.class).putExtra("SESSION_RECOVERY",true)
                .putExtra("LOGIN_TYPE",ProductSession.role(activity)).putExtra("RECOVERY_EMAIL",ProductSession.email(activity))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        foreground.clear();
    }
    public void onActivityResumed(Activity activity){foreground=new WeakReference<>(activity);recover();}
    public void onActivityPaused(Activity activity){if(foreground.get()==activity)foreground.clear();}
    public void onActivityCreated(Activity activity,Bundle state){}
    public void onActivityStarted(Activity activity){}
    public void onActivityStopped(Activity activity){}
    public void onActivitySaveInstanceState(Activity activity,Bundle state){}
    public void onActivityDestroyed(Activity activity){if(foreground.get()==activity)foreground.clear();}
}
