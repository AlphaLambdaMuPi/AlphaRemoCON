package com.ooxx.meteor.androidcopter;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import com.dd.CircularProgressButton;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by meteor on 2015/6/28.
 */
public class HoldButton extends CircularProgressButton {
    private boolean state = false;
    private boolean lock = false;
    private int progress = 0;
    private Timer timer;

    public HoldButton(Context context) {
        super(context);
    }
    public HoldButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public HoldButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private void _setProgress(int a) {
        progress = a;
        setProgress(a);
    }

    public void reset() {
        _setProgress(0);
        state = lock = false;
        timer.cancel();
        timer.purge();
    }

    public int getStateOnTouch() {
        if(state && !lock) {
            state = false;
            lock = true;
            _setProgress(0);
            return 2;
        }
        if(!lock) {
                lock = true;
                timer = new Timer();
                _setProgress(0);
                timer.schedule(new TimerTask() {
                       @Override
                       public void run() {
                           progress += 1;
                           post(new Runnable() {
                               @Override
                               public void run() {
                                   setProgress(progress);
                               }
                           });
                           if(HoldButton.this.progress >= 99) {
                               state = true;
                               timer.cancel();
                               timer.purge();
                           }
                       }
                   }, 0, 20);
                return 0;
        }
        return 0;
    }

    public int releaseTouch() {
        lock = false;
        if(state) {
            _setProgress(100);
            return 1;
        }
        _setProgress(0);
        timer.cancel();
        timer.purge();
        return 0;
    }
}
