package org.billthefarmer.siggen;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class AutoActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(this);
        super.onCreate(savedInstanceState);
        boolean darkTheme =
                preferences.getBoolean(Main.PREF_DARK_THEME, false);

        if (darkTheme)
            setTheme(R.style.AppDarkTheme);
        setContentView(R.layout.auto);


    }




}
