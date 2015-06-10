package com.ooxx.meteor.androidcopter;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.SeekBar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;


public class MainActivity extends ActionBarActivity {

    private NumberPicker numberPicker;
    private SeekBar seekBar;
    private int thrust = 0;

    private OutputStreamWriter writer = null;
    private InputStreamReader reader = null;
    private HandlerThread socketThread;
    private Handler UIHandler = new Handler();
    private Handler socketHandler;
    private Socket socket = null;
    private boolean tookoff = false;
    private boolean connected = false;
    private boolean started = false;

    private String datahost = "140.112.18.210";
    private int port = 12345;

    private void set_thrust(int value) {
        if(thrust == value) return;
        thrust = value;
        if(started) {
            socketHandler.post( new DataSender(dataToString()) );
        }
    }

    private String dataToString() {
        JSONObject json = new JSONObject();
        try {
            json.put("action", "M");
            JSONArray jarr = new JSONArray();
            if (thrust > 0) {
                jarr.put(thrust * 10);
                jarr.put(thrust * 10);
                jarr.put(thrust * 10);
                jarr.put(thrust * 10);
            } else {
                jarr.put(-500);
                jarr.put(-500);
                jarr.put(-500);
                jarr.put(-500);
            }
            json.put("args", jarr);
        } catch(JSONException e) {
            Log.d("dataToString", "Json encode failed.");
        }
        return json.toString();
    }

    public void takeOffButtonHandler(View view) {
        if(!started) return;
        JSONObject json = new JSONObject();
        try {
            if(!tookoff) json.put("action", "T");
            else json.put("action", "S");
            json.put("args", "");
        } catch(JSONException e) {
            Log.d("dataToString", "Json encode failed.");
        }
        socketHandler.post( new DataSender(json.toString()) );
        if(!tookoff) {
            Button button = (Button) view;
            button.setText("Land");
            tookoff = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        socketThread = new HandlerThread("socket");
        socketThread.start();
        socketHandler = new Handler(socketThread.getLooper());

        numberPicker = (NumberPicker) findViewById(
                R.id.numberPicker
        );
        numberPicker.setMaxValue(50);
        numberPicker.setMinValue(0);
        numberPicker.setFormatter(new NumberPicker.Formatter() {
            @Override
            public String format(int i) {
                if (i == 0)
                    return "halt";
                else
                    return String.valueOf(1200 + i * 10);
            }
        });
        numberPicker.setWrapSelectorWheel(false);
        numberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                MainActivity.this.seekBar.setProgress(newVal);
                MainActivity.this.set_thrust(newVal);
            }
        });

        seekBar = (SeekBar) findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                MainActivity.this.numberPicker.setValue(progress);
                MainActivity.this.set_thrust(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        socketHandler.post(starter);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private Runnable starter = new Runnable() {
        @Override
        public void run() {
            if (datahost.equals("") || port == 0)
                return;
            try {
                socket = new Socket(datahost, port);
                writer = new OutputStreamWriter(socket.getOutputStream());
                reader = new InputStreamReader(socket.getInputStream());
                connected = true;
                started = true;
                Log.d("DEBUG", "connected");
            }
            catch (Exception e) {
                connected = false;
                started = false;
                Log.d("DEBUG", "connection failed");
                Log.d("DEBUG", e.getMessage());
                return;
            }
            JSONObject data = new JSONObject();
            try {
                data.put("name", android.os.Build.MODEL);
                data.put("role", "CONTROL");
                String s = data.toString() + "\n";
                writer.write(s);

                data = new JSONObject();
                data.put("target", "RPI_DRONE");
                s = data.toString() + "\n";
                writer.write(s);
                writer.flush();
            }
            catch (JSONException e) {
                Log.d("DEBUG", "JSON object error");
            }
            catch (IOException e) {
                Log.d("DEBUG", "socket write error");
                if (socket.isInputShutdown())
                    socketHandler.post(stopper);
            }
        }
    };

    private Runnable stopper = new Runnable() {
        @Override
        public void run() {
            started = false;
            JSONObject data = new JSONObject();
            try {
                data.put("stop", true);
                String s = data.toString() + "\n";
                writer.write(s);
                writer.flush();
                socket.close();
            }
            catch (JSONException e) {
                Log.d("DEBUG", "JSON object error");
            }
            catch (IOException e) {
                Log.d("DEBUG", "socket was already closed");
            }
            socket = null;
        }
    };

    private class DataSender implements Runnable {
        private String data;
        public DataSender(String data) {
            this.data = data;
        }

        @Override
        public void run() {
            if (socket == null || socket.isClosed() || !started)
                return;

            try {
                writer.write(data + "\n");
                writer.flush();
            } catch (IOException e) {
                Log.d("DEBUG", "socket write error");
                if (socket.isInputShutdown())
                    socketHandler.post(stopper);
            }
        }

    }
}
