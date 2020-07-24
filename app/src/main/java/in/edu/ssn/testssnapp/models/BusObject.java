package in.edu.ssn.testssnapp.models;

import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

public class BusObject {
    volatile String currentVolunteerId;

    volatile LatLng position;
    volatile Marker busMarker;
    volatile OnInfoUpdatedListener infoUpdatedListener;
    volatile boolean isSharerOnline;
    public volatile boolean isMarkerVisible;
    volatile int speed;


    public BusObject() {
        isSharerOnline = false;
        speed = 0;
        currentVolunteerId = "";
        isMarkerVisible = false;
        infoUpdatedListener = null;
    }

    public BusObject(String routeNo, String currentVolunteerId, Marker busMarker, int speed, boolean isSharerOnline, OnInfoUpdatedListener infoUpdatedListener) {
        this.currentVolunteerId = currentVolunteerId;
        this.busMarker = busMarker;
        this.speed = speed;
        this.isSharerOnline = isSharerOnline;
        this.infoUpdatedListener = infoUpdatedListener;
    }


    public boolean isSharerOnline() {
        return isSharerOnline;
    }

    public int getSpeed() {
        return speed;
    }

    public void setUserOnline(boolean sharerOnline) {
//        if (sharerOnline == isSharerOnline) return;
        isSharerOnline = sharerOnline;
        infoUpdatedListener.onOnlineStatusChanged(sharerOnline);
    }

    public void setSpeed(int speed) {
        this.speed = speed;
        if (infoUpdatedListener != null)
            infoUpdatedListener.onSpeedChanged(speed);
    }

    public void setInfoUpdatedListener(OnInfoUpdatedListener locationUpdatedListener) {
        this.infoUpdatedListener = locationUpdatedListener;
    }

    private float bearingBetweenLocations(LatLng latLng1, LatLng latLng2) {

        double PI = 3.14159;
        double lat1 = latLng1.latitude * PI / 180;
        double long1 = latLng1.longitude * PI / 180;
        double lat2 = latLng2.latitude * PI / 180;
        double long2 = latLng2.longitude * PI / 180;

        double dLon = (long2 - long1);

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                * Math.cos(lat2) * Math.cos(dLon);

        float brng = (float) Math.atan2(y, x);
        brng = (float) Math.toDegrees(brng);
        brng = (brng + 360) % 360;

        return brng;
    }

    public void moveVehicle(final LatLng finalPosition, GoogleMap googleMap, Handler handler) {
        if (busMarker == null || googleMap == null) return;
        if (position == null) {
            handler.post(() -> googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(finalPosition, 18f)));
        }

        final LatLng startPosition = busMarker.getPosition();

        final long start = SystemClock.uptimeMillis();
        final Interpolator interpolator = new AccelerateDecelerateInterpolator();
        final float durationInMs = 500;
        handler.post(() -> {
            if (position != null)
                busMarker.setRotation(bearingBetweenLocations(position, finalPosition));
        });
        handler.post(new Runnable() {
            long elapsed;
            float t;
            float v;

            @Override
            public void run() {
                // Calculate progress using interpolator
                elapsed = SystemClock.uptimeMillis() - start;
                t = elapsed / durationInMs;
                v = interpolator.getInterpolation(t);

                LatLng currentPosition = new LatLng(
                        startPosition.latitude * (1 - t) + (finalPosition.latitude) * t,
                        startPosition.longitude * (1 - t) + (finalPosition.longitude) * t);
                busMarker.setPosition(currentPosition);
                position = currentPosition;
                boolean contains = googleMap.getProjection()
                        .getVisibleRegion()
                        .latLngBounds
                        .contains(currentPosition);
                if (!contains) {
                    // MOVE CAMERA
                    googleMap.animateCamera(CameraUpdateFactory.newLatLng(currentPosition));
                }

                // Repeat till progress is complete
                if (t < 1) {
                    handler.postDelayed(this, 16);
                } else {
                    busMarker.setVisible(true);
                }
            }
        });
    }

    public Marker getBusMarker() {
        return busMarker;
    }

    public LatLng getLocation() {
        return position;
    }

    public void hideBusMarker() {
        if (!isMarkerVisible || busMarker == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
            animator.setInterpolator(new DecelerateInterpolator(5f));
            animator.setDuration(2000);
            animator.addUpdateListener(animation -> {
                busMarker.setAlpha(1 - animation.getAnimatedFraction());
            });
            animator.start();
        } else busMarker.setVisible(false);
        isMarkerVisible = false;
    }

    public void showBusMarker() {
        if (isMarkerVisible || busMarker == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            busMarker.setVisible(true);
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
            animator.setInterpolator(new AccelerateInterpolator(1f));
            animator.setDuration(2000);
            animator.addUpdateListener(animation -> {
                busMarker.setAlpha(animation.getAnimatedFraction());
            });
            animator.start();
        } else busMarker.setVisible(true);
        isMarkerVisible = true;
    }

    public void setBusMarker(Marker busLocation) {
        this.busMarker = busLocation;
    }

    public String getCurrentVolunteerId() {
        return currentVolunteerId;
    }

    public void setCurrentVolunteerId(String currentVolunteerId) {
        if (currentVolunteerId.equals(this.currentVolunteerId)) return;
        this.currentVolunteerId = currentVolunteerId;
        infoUpdatedListener.onSharerIdChanged(currentVolunteerId);
    }

    public interface OnInfoUpdatedListener {
        void onSharerIdChanged(String newSharerId);

        void onLocationChanged(LatLng location);

        void onOnlineStatusChanged(boolean isOnline);

        void onSpeedChanged(int newSpeed);
    }

}
