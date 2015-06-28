package com.ooxx.meteor.androidcopter;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.SeekBar;

import com.MobileAnarchy.Android.Widgets.Joystick.JoystickMovedListener;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickView;
import com.dd.CircularProgressButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {

    private NumberPicker numberPicker;
    private SeekBar seekBar;
    private int thrust = 0;
    private int angle_x, angle_y;

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

    private JoystickMovedListener jsListener;

    private void set_thrust(int value) {
        if(thrust == value) return;
        if(value > thrust + 10) return;
        thrust = value;
        if(started) {
            socketHandler.post( new DataSender(thrustToString()) );
        }
    }

    private void set_angle(int ax, int ay) {
        if(!(ax == 0 && ay == 0)) {
            if (Math.abs(ax - angle_x) + Math.abs(ay - angle_y) <= 3)
                return;
        }

        angle_x = ax; angle_y = ay;
        Log.d("set_angle", "" + angle_x + "," + angle_y);
        if(started) {
            socketHandler.post( new DataSender(angleToString()) );
        }
    }

    private void set_tweak(String type, double per) {
        JSONObject json = new JSONObject();
        try {
            json.put("action", "tweak");
            JSONArray jarr = new JSONArray();
            jarr.put(type);
            jarr.put(per);
            json.put("args", jarr);
        } catch(JSONException e) {
            Log.d("dataToString", "Json encode failed.");
        }
        Log.d("set_tweak", json.toString());
        if(started) {
            socketHandler.post( new DataSender(json.toString()) );
        }
    }

    private String thrustToString() {
        JSONObject json = new JSONObject();
        try {
            json.put("action", "thrust");
            JSONArray jarr = new JSONArray();
            if (thrust > 0) {
                jarr.put(thrust * 10);
            } else {
                jarr.put(-500);
            }
            json.put("args", jarr);
        } catch(JSONException e) {
            Log.d("dataToString", "Json encode failed.");
        }
        return json.toString();
    }

    private String angleToString() {
        JSONObject json = new JSONObject();
        try {
            json.put("action", "angle");
            JSONArray jarr = new JSONArray();
            double tx, ty;
            tx = 1.0 * angle_x * Math.PI / 180.0;
            ty = 1.0 * angle_y * Math.PI / 180.0;
            jarr.put(tx);
            jarr.put(ty);
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
            if(!tookoff) json.put("action", "arm");
            else json.put("action", "stop");
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
                MainActivity.this.set_thrust(newVal);
                MainActivity.this.seekBar.setProgress(
                        MainActivity.this.thrust);
            }
        });

        jsListener = new JoystickMovedListener() {
            @Override
            public void OnMoved(int pan, int tilt) {
                MainActivity.this.set_angle(tilt, pan);
            }

            @Override
            public void OnReleased() {
                set_angle(0, 0);
            }
        };
        JoystickView jsView = (JoystickView) findViewById(R.id.joystick);
        jsView.setOnJostickMovedListener(jsListener);

        seekBar = (SeekBar) findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                MainActivity.this.set_thrust(progress);
                MainActivity.this.numberPicker.setValue(
                        MainActivity.this.thrust
                );
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        SeekBar tweakP = (SeekBar) findViewById(R.id.tweakP);
        tweakP.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                MainActivity.this.set_tweak("P", progress/10.0);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {  }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {  }
        });

        SeekBar tweakI = (SeekBar) findViewById(R.id.tweakI);
        tweakI.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                MainActivity.this.set_tweak("I", progress/10.0);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {  }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {  }
        });

        SeekBar tweakD = (SeekBar) findViewById(R.id.tweakD);
        tweakD.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                MainActivity.this.set_tweak("D", progress/10.0);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {  }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {  }
        });

        final HoldButton armButton = (HoldButton) findViewById(R.id.armButton);
        armButton.setOnTouchListener(new View.OnTouchListener() {
             @Override
             public boolean onTouch(View view, MotionEvent motionEvent) {
                 HoldButton hv = (HoldButton) view;
                 if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                     int res = hv.getStateOnTouch();
                     if(res == 2) {
                         Log.d("Send", "test");
                     }
                 } else if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                     int res = hv.releaseTouch();
                     if(res == 1) {
                         Log.d("Send", "hao123");
                     }
                 }
                 return true;
             }
         });
        //armButton.set

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
