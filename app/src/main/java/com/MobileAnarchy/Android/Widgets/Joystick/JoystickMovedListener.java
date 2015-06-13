package com.MobileAnarchy.Android.Widgets.Joystick;

public interface JoystickMovedListener {
    void OnMoved(int pan, int tilt);
    void OnReleased();
}
