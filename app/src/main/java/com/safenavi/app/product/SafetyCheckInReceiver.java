package com.safenavi.app.product;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SafetyCheckInReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        SafetyCheckInWorker.acknowledgeNow(context);
    }
}
