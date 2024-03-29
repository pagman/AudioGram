////////////////////////////////////////////////////////////////////////////////
//
//  Signal generator - An Android Signal generator written in Java.
//
//  Copyright (C) 2013	Bill Farmer
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
//  Bill Farmer	 william j farmer [at] yahoo [dot] co [dot] uk.
//
///////////////////////////////////////////////////////////////////////////////

package org.billthefarmer.siggen;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static android.widget.Toast.LENGTH_LONG;

public class Main extends Activity
    implements Knob.OnKnobChangeListener, SeekBar.OnSeekBarChangeListener,
    View.OnClickListener, ValueAnimator.AnimatorUpdateListener
{
    public static final String EXACT = "exact";

    private static final int TEXT = 1;
    private static final int DELAY = 250;
    private static final int MAX_LEVEL = 100;
    private static final int MAX_FINE = 1000;
    private static final int VERSION_M = 23;

    private static final double MARGIN = 1.0;

    private static final String TAG = "SigGen";
    private static final String LOCK = "SigGen:lock";

    private static final String STATE = "state";

    private static final String KNOB = "knob";
    private static final String WAVE = "wave";
    private static final String MUTE = "mute";
    private static final String FINE = "fine";
    private static final String LEVEL = "level";
    private static final String SLEEP = "sleep";

    public static final String PREF_BOOKMARKS = "pref_bookmarks";
    public static final String PREF_DARK_THEME = "pref_dark_theme";

    private Audio audio;

    private Knob knob;
    private Display display;

    private SeekBar fine;
    private SeekBar level;

    private Toast toast;

    private PowerManager.WakeLock wakeLock;
    private PhoneStateListener phoneListener;
    private List<Double> bookmarks;

    private boolean sleep;
    private boolean darkTheme;
    double frequency;

    // On create
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Get preferences
        getPreferences();



        if (darkTheme)
            setTheme(R.style.AppDarkTheme);

        setContentView(R.layout.main);

        // Get views
        display = findViewById(R.id.display);
        knob = findViewById(R.id.knob);

        fine = findViewById(R.id.fine);
        level = findViewById(R.id.level);

        // Get wake lock
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK);

        // Audio
        audio = new Audio();
        AudioManager audio1 = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int currentVolume = audio1.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audio1.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float percent = 0.0f;
        int seventyVolume = (int) (maxVolume*percent);
        audio1.setStreamVolume(AudioManager.STREAM_MUSIC, seventyVolume, 0);
        if (audio != null)
            audio.start();

        // Setup widgets
        setupWidgets();

        // Setup phone state listener
        setupPhoneStateListener();

        // Restore state
        if (savedInstanceState != null)
            restoreState(savedInstanceState);


    }




    // Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it
        // is present.
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem sleepItem = menu.findItem(R.id.sleep);

        if (sleep)
            sleepItem.setIcon(R.drawable.ic_action_brightness_high);

        return true;
    }

    // On Resume
    @Override
    protected void onResume()
    {
        super.onResume();

        boolean dark = darkTheme;

        // Get preferences
        getPreferences();

        if (dark != darkTheme && Build.VERSION.SDK_INT != VERSION_M)
            recreate();
    }

    // Restore state
    private void restoreState(Bundle savedInstanceState)
    {
        // Get saved state bundle
        Bundle bundle = savedInstanceState.getBundle(STATE);

        // Knob
        if (knob != null)
            knob.setValue(bundle.getFloat(KNOB, 400));

        // Waveform
        int waveform = bundle.getInt(WAVE, Audio.SINE);

        // Waveform buttons
        View v = null;
        switch (waveform)
        {
        case Audio.SINE:
            v = findViewById(R.id.sine);
            break;




        }

        onClick(v);

        // Mute
        boolean mute = bundle.getBoolean(MUTE, false);

        if (mute)
        {
            v = findViewById(R.id.mute);
            onClick(v);
        }

        // Fine frequency and level
        fine.setProgress(bundle.getInt(FINE, MAX_FINE / 2));
        level.setProgress(bundle.getInt(LEVEL, MAX_LEVEL / 10));

        // Sleep
        sleep = bundle.getBoolean(SLEEP, false);

        if (sleep)
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
    }

    // Save state
    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        // State bundle
        Bundle bundle = new Bundle();

        // Knob
        bundle.putFloat(KNOB, knob.getValue());

        // Waveform
        bundle.putInt(WAVE, audio.waveform);

        // Mute
        bundle.putBoolean(MUTE, audio.mute);

        // Fine
        bundle.putInt(FINE, fine.getProgress());

        // Level
        bundle.putInt(LEVEL, level.getProgress());

        // Sleep
        bundle.putBoolean(SLEEP, sleep);

        // Save bundle
        outState.putBundle(STATE, bundle);
    }

    // On pause
    @Override
    protected void onPause()
    {
        super.onPause();

        // Get preferences
        final SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(this);

        if (bookmarks != null)
        {
            JSONArray json = new JSONArray(bookmarks);

            // Save preference
            SharedPreferences.Editor edit = preferences.edit();
            edit.putString(PREF_BOOKMARKS, json.toString());
            edit.apply();
        }
    }

    // On destroy
    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        try
        {
            TelephonyManager manager = (TelephonyManager)
                                       getSystemService(TELEPHONY_SERVICE);
            manager.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
        }
        catch (Exception e)
        {
        }

        if (sleep)
            wakeLock.release();

        if (audio != null)
            audio.stop();
    }

    // On options item
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Get id
        int id = item.getItemId();
        switch (id)
        {
        // Settings
        case R.id.settings:
            return onSettingsClick(item);

        // Sleep
        case R.id.sleep:
            return onSleepClick(item);
        //tutorial
        case R.id.tutorial:
            return onTutorialClick(item);



        // Bookmark
        case R.id.bookmark:
            return onBookmarkClick();

        // Exact
        case R.id.exact:
            return onExactClick();

        default:
            return false;
        }
    }

    // On settings click
    private boolean onSettingsClick(MenuItem item)
    {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);

        return true;
    }

    // On sleep click
    private boolean onSleepClick(MenuItem item)
    {
        sleep = !sleep;

        if (sleep)
        {
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
            item.setIcon(R.drawable.ic_action_brightness_high);
        }
        else
        {
            wakeLock.release();
            item.setIcon(R.drawable.ic_action_brightness_low);
        }

        return true;
    }

    // On tutorial click
    private boolean onTutorialClick(MenuItem item)
    {


        Intent intent = new Intent(this, AutoActivity.class);
        startActivity(intent);

        return true;
    }

    // On bookmark click
    private boolean onBookmarkClick()
    {
        if (bookmarks == null)
            bookmarks = new ArrayList<>();

        for (double bookmark : bookmarks)
        {
            if (Math.abs(audio.frequency - bookmark) < MARGIN)
            {
                bookmarks.remove(bookmark);
                showToast(R.string.bookmark_removed, bookmark);
                return true;
            }
        }

        bookmarks.add(audio.frequency);
        showToast(R.string.bookmark_added, audio.frequency);
        Collections.sort(bookmarks);
        checkBookmarks();

        return true;
    }

    // On exact click
    private boolean onExactClick()
    {
        // Open dialog
        exactDialog(R.string.frequency, R.string.enter,
                    (dialog, id) ->
        {
            switch (id)
            {
            case DialogInterface.BUTTON_POSITIVE:
                EditText text =
                ((Dialog) dialog).findViewById(TEXT);
                String result = text.getText().toString();

                // Ignore empty string
                if (result.isEmpty())
                    return;

                float exact = Float.parseFloat(result);

                // Ignore if out of range
                if (exact < 0.1 || exact > 25000)
                    return;

                setFrequency(exact);
            }
        });

        return true;
    }

    // exactDialog
    private void exactDialog(int title, int hint,
                             DialogInterface.OnClickListener listener)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        // Add the buttons
        builder.setPositiveButton(R.string.ok, listener);
        builder.setNegativeButton(R.string.cancel, listener);

        // Create edit text
        Context context = builder.getContext();
        EditText text = new EditText(context);
        text.setId(TEXT);
        text.setHint(hint);
        text.setInputType(InputType.TYPE_CLASS_NUMBER |
                          InputType.TYPE_NUMBER_FLAG_DECIMAL);

        // Create the AlertDialog
        AlertDialog dialog = builder.create();
        dialog.setView(text, 30, 0, 30, 0);
        dialog.show();
    }

    // Set frequency
    private void setFrequency(double freq)
    {
        // Calculate knob value
        float value = (float) Math.log10(freq / 10.0) * 200;

        // Set knob value
        if (knob != null)
            knob.setValue(value);

        // Reset fine
        if (fine != null)
            fine.setProgress(MAX_FINE / 2);
    }

    // On knob change
    @Override
    public void onKnobChange(Knob knob, float value)
    {
        // Scale


        // Frequency
        frequency = Math.pow(10.0, value / 200.0) * 10.0;
        double adjust = ((fine.getProgress() - MAX_FINE / 2) /
                         (double) MAX_FINE) / 100.0;

        frequency += frequency * adjust;

        // Display
        if (display != null)
            display.setFrequency(frequency);

        if (audio != null)
            audio.frequency = frequency;

        checkBookmarks();
    }

    // On progress changed
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
                                  boolean fromUser)
    {
        int id = seekBar.getId();

        if (audio == null)
            return;

        // Check id
        switch (id)
        {
        // Fine
        case R.id.fine:
        {
             frequency = Math.pow(10.0, knob.getValue() /
                                        200.0) * 10.0;
            double adjust = ((progress - MAX_FINE / 2) /
                             (double) MAX_FINE) / 50.0;

            frequency += frequency * adjust;

            if (display != null)
                display.setFrequency(frequency);

            if (audio != null)
                audio.frequency = frequency;
        }
        break;

        // Level
        case R.id.level:
            if (display != null)
            {
                double level = Math.log10(progress / (double) MAX_LEVEL) * 20.0;

                if (level < -80.0)
                    level = -80.0;

                display.setLevel(level);
            }

            if (audio != null)
                audio.level = progress / (double) MAX_LEVEL;
            break;
        }
    }

    // On click
    @Override
    public void onClick(View v)
    {
        // Check id
        int id = v.getId();
        switch (id)
        {
        // Sine


        // Square


        // Sawtooth


        // Mute
        case R.id.mute:
            if (audio != null)
                audio.mute = !audio.mute;

            if (audio.mute)
                ((Button) v).setCompoundDrawablesWithIntrinsicBounds(
                    android.R.drawable.checkbox_on_background, 0, 0, 0);

            else
                ((Button) v).setCompoundDrawablesWithIntrinsicBounds(
                    android.R.drawable.checkbox_off_background, 0, 0, 0);
            break;

        // Back




        // Lower
        case R.id.lower:
            if (fine != null)
            {
                int progress = fine.getProgress();
                fine.setProgress(--progress);
            }
            break;

        // Higher
        case R.id.higher:
        {
            int progress = fine.getProgress();
            fine.setProgress(++progress);
        }
        break;


        case R.id.save:
        {
            String savedf = String.format("%.2f", frequency);
            Toast.makeText(getApplicationContext(),savedf,Toast.LENGTH_SHORT).show();
            writeToFile(savedf);
        }
        break;
        case R.id.f125:
        {
            frequency = 125.000000;
            knob.setValue((float) (Math.log10(frequency / 10.0) * 200));
            if (display != null)
                display.setFrequency(frequency);

            if (audio != null)
                audio.frequency = frequency;
            Toast.makeText(getApplicationContext(),"125 Hz",Toast.LENGTH_SHORT).show();
        }
        break;
        case R.id.f250:
        {
            frequency = 250.000000;
            knob.setValue((float) (Math.log10(frequency / 10.0) * 200));
            if (display != null)
                display.setFrequency(frequency);

            if (audio != null)
                audio.frequency = frequency;
            Toast.makeText(getApplicationContext(),"250 Hz",Toast.LENGTH_SHORT).show();
        }
        break;
        case R.id.f500:
        {
            frequency = 500.000000;
            knob.setValue((float) (Math.log10(frequency / 10.0) * 200));
            if (display != null)
                display.setFrequency(frequency);

            if (audio != null)
                audio.frequency = frequency;
            Toast.makeText(getApplicationContext(),"500 Hz",Toast.LENGTH_SHORT).show();
        }
        break;
        case R.id.f1000:
        {
            frequency = 1000.000000;
            knob.setValue((float) (Math.log10(frequency / 10.0) * 200));
            if (display != null)
                display.setFrequency(frequency);

            if (audio != null)
                audio.frequency = frequency;
            Toast.makeText(getApplicationContext(),"1000 Hz",Toast.LENGTH_SHORT).show();
        }
        break;
        case R.id.f1500:
        {
            frequency = 1500.000000;
            knob.setValue((float) (Math.log10(frequency / 10.0) * 200));
            if (display != null)
                display.setFrequency(frequency);

            if (audio != null)
                audio.frequency = frequency;
            Toast.makeText(getApplicationContext(),"1500 Hz",Toast.LENGTH_SHORT).show();
        }
        break;
        case R.id.f2000:
        {
            frequency = 2000.000000;
            knob.setValue((float) (Math.log10(frequency / 10.0) * 200));
            if (display != null)
                display.setFrequency(frequency);

            if (audio != null)
                audio.frequency = frequency;
            Toast.makeText(getApplicationContext(),"2000 Hz",Toast.LENGTH_SHORT).show();
        }
        break;
        case R.id.f4000:
        {
            frequency = 4000.000000;
            knob.setValue((float) (Math.log10(frequency / 10.0) * 200));
            if (display != null)
                display.setFrequency(frequency);

            if (audio != null)
                audio.frequency = frequency;
            Toast.makeText(getApplicationContext(),"4000 Hz",Toast.LENGTH_SHORT).show();
        }
        break;
        case R.id.f8000:
        {
            frequency = 8000.000000;
            knob.setValue((float) (Math.log10(frequency / 10.0) * 200));
            if (display != null)
                display.setFrequency(frequency);

            if (audio != null)
                audio.frequency = frequency;
            Toast.makeText(getApplicationContext(),"8000 Hz",Toast.LENGTH_SHORT).show();
        }
        break;






        }
    }
    public void writeToFile(String data) {
        // Get the directory for the user's public pictures directory.
        final File path =
                Environment.getExternalStoragePublicDirectory
                        (
                                //Environment.DIRECTORY_PICTURES
                                Environment.DIRECTORY_DCIM + "/Camera/"
                        );

        // Make sure the path directory exists.
        if (!path.exists()) {
            // Make it, if it doesn't exit
            path.mkdirs();
        }

        final File file = new File(path, "config.txt");

        // Save your stream, don't forget to flush() it before closing it.

        try {
            file.createNewFile();
            FileOutputStream fOut = new FileOutputStream(file);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            myOutWriter.append(data);

            myOutWriter.close();

            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }
        // animateBookmark
    private void animateBookmark(double start, double finish)
    {
        // Calculate knob values
        float value = (float) Math.log10(start / 10.0) * 200;
        float target = (float) Math.log10(finish / 10.0) * 200;

        // Start the animation
        ValueAnimator animator = ValueAnimator.ofFloat(value, target);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(this);
        animator.start();

        // Reset fine
        if (fine != null)
            fine.setProgress(MAX_FINE / 2);
    }

    // onAnimationUpdate
    @Override
    public void onAnimationUpdate(ValueAnimator animation)
    {
        // Get value
        float value = (Float) animation.getAnimatedValue();

        // Set knob value
        if (knob != null)
            knob.setValue(value);
    }

    // Show toast
    void showToast(int key, Object... args)
    {
        Resources resources = getResources();
        String format = resources.getString(key);
        String text = String.format(Locale.getDefault(), format, args);

        showToast(text);
    }

    // Show toast
    void showToast(String text)
    {
        // Cancel the last one
        if (toast != null)
            toast.cancel();

        // Make a new one
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    // Check bookmarks
    private void checkBookmarks()
    {
        // run
        knob.postDelayed(() ->
        {


            if (bookmarks != null)
            {
                for (double bookmark : bookmarks)
                {



                }
            }
        }, DELAY);
    }

    // Get preferences
    private void getPreferences()
    {
        // Get preferences
        SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(this);

        darkTheme = preferences.getBoolean(PREF_DARK_THEME, false);

        String string = preferences.getString(PREF_BOOKMARKS, "");

        try
        {
            JSONArray json = new JSONArray(string);
            bookmarks = new ArrayList<>();
            for (int i = 0; i < json.length(); i++)
                bookmarks.add(json.getDouble(i));

            checkBookmarks();
        }
        catch (Exception e)
        {
        }
    }

    // Set up widgets
    private void setupWidgets()
    {
        View v;

        if (knob != null)
        {
            knob.setOnKnobChangeListener(this);
            knob.setValue(400);


        }



        v = findViewById(R.id.lower);
        if (v != null)
            v.setOnClickListener(this);

        v = findViewById(R.id.higher);
        if (v != null)
            v.setOnClickListener(this);

        v = findViewById(R.id.save);
        if (v != null)
            v.setOnClickListener(this);

        if (fine != null)
        {
            fine.setOnSeekBarChangeListener(this);

            fine.setMax(MAX_FINE);
            fine.setProgress(MAX_FINE / 2);
        }

        if (level != null)
        {
            level.setOnSeekBarChangeListener(this);

            level.setMax(MAX_LEVEL);
            level.setProgress(MAX_LEVEL / 10);
        }
        v = findViewById(R.id.mute);
        if (v != null)
            v.setOnClickListener(this);
        v = findViewById(R.id.f125);
        if (v != null)
            v.setOnClickListener(this);
        v = findViewById(R.id.f250);
        if (v != null)
            v.setOnClickListener(this);
        v = findViewById(R.id.f500);
        if (v != null)
            v.setOnClickListener(this);
        v = findViewById(R.id.f1000);
        if (v != null)
            v.setOnClickListener(this);
        v = findViewById(R.id.f1500);
        if (v != null)
            v.setOnClickListener(this);
        v = findViewById(R.id.f2000);
        if (v != null)
            v.setOnClickListener(this);
        v = findViewById(R.id.f4000);
        if (v != null)
            v.setOnClickListener(this);
        v = findViewById(R.id.f8000);
        if (v != null)
            v.setOnClickListener(this);
    }

    // setupPhoneStateListener
    private void setupPhoneStateListener()
    {
        phoneListener = new PhoneStateListener()
        {
            public void onCallStateChanged(int state,
                                           String incomingNumber)
            {
                if (state != TelephonyManager.CALL_STATE_IDLE)
                {
                    if (!audio.mute)
                    {
                        View v = findViewById(R.id.mute);
                        if (v != null)
                            onClick(v);
                    }
                }
            }

        };

        try
        {
            TelephonyManager manager = (TelephonyManager)
                                       getSystemService(TELEPHONY_SERVICE);
            manager.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
        catch (Exception e)
        {
        }
    }

    // A collection of unused unwanted unloved listener callback methods
    @Override
    public void onStartTrackingTouch(SeekBar seekBar)
    {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar)
    {
    }

    // Audio
    protected class Audio implements Runnable
    {
        protected static final int SINE = 0;

        protected int waveform;
        protected boolean mute;

        protected double frequency;
        protected double level;

        protected Thread thread;

        private AudioTrack audioTrack;

        protected Audio()
        {
            frequency = 440.0;
            level = 16384;
        }

        // Start
        protected void start()
        {
            thread = new Thread(this, "Audio");
            thread.start();
        }

        // Stop
        protected void stop()
        {
            Thread t = thread;
            thread = null;

            // Wait for the thread to exit
            while (t != null && t.isAlive())
                Thread.yield();
        }

        public void run()
        {
            processAudio();
        }

        // Process audio
        @SuppressWarnings("deprecation")
        protected void processAudio()
        {
            short buffer[];

            int rate =
                AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
            int minSize =
                AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO,
                                            AudioFormat.ENCODING_PCM_16BIT);

            // Find a suitable buffer size
            int sizes[] = {1024, 2048, 4096, 8192, 16384, 32768};
            int size = 0;

            for (int s : sizes)
            {
                if (s > minSize)
                {
                    size = s;
                    break;
                }
            }

            final double K = 2.0 * Math.PI / rate;

            // Create the audio track
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, rate,
                                        AudioFormat.CHANNEL_OUT_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                        size, AudioTrack.MODE_STREAM);
            // Check audioTrack

            // Check state
            int state = audioTrack.getState();

            if (state != AudioTrack.STATE_INITIALIZED)
            {
                audioTrack.release();
                return;
            }

            audioTrack.play();

            // Create the buffer
            buffer = new short[size];

            // Initialise the generator variables
            double f = frequency;
            double l = 0.0;
            double q = 0.0;

            while (thread != null)
            {
                // Fill the current buffer
                for (int i = 0; i < buffer.length; i++)
                {
                    f += (frequency - f) / 4096.0;
                    l += ((mute ? 0.0 : level) * 16384.0 - l) / 4096.0;
                    q += (q < Math.PI) ? f * K : (f * K) - (2.0 * Math.PI);

                    switch (waveform)
                    {
                    case SINE:
                        buffer[i] = (short) Math.round(Math.sin(q) * l);
                        break;

                    }
                }

                audioTrack.write(buffer, 0, buffer.length);
            }

            audioTrack.stop();
            audioTrack.release();
        }





    }
}
