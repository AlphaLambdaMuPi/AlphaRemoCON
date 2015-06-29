package com.MobileAnarchy.Android.Widgets.Joystick;

public interface JoystickMovedListener {
    void OnMoved(double pan, double tilt);
    void OnReleased();
}
