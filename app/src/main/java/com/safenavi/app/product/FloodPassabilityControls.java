package com.safenavi.app.product;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.safenavi.app.R;

/** Official-only forms call this; public/citizen screens never grant this authority. */
public final class FloodPassabilityControls {
    private FloodPassabilityControls() { }
    public static Spinner add(Context context, LinearLayout form, String existing) {
        TextView label=new TextView(context);
        label.setText("Flood road passability\nOnly mark passable after checking the affected road. Unconfirmed or impassable roads are excluded from every route preference.");
        label.setTextSize(13);label.setTextColor(ContextCompat.getColor(context,R.color.text_primary));form.addView(label);
        Spinner choice=new Spinner(context);choice.setContentDescription("Flood road passability");
        choice.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,new String[]{
                "Not confirmed (exclude road)","Inspected: passable with caution","Impassable (exclude road)"}));
        choice.setSelection("PASSABLE".equals(existing)?1:"IMPASSABLE".equals(existing)?2:0);form.addView(choice);
        return choice;
    }
    public static String value(Spinner choice) {
        return choice.getSelectedItemPosition()==1?"PASSABLE":choice.getSelectedItemPosition()==2?"IMPASSABLE":"UNKNOWN";
    }
}
