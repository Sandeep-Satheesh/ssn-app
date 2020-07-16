package in.edu.ssn.testssnapp.models;

import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Handler;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

public class BusObject {
    volatile String routeNo, currentVolunteerId;

    public void setRouteNo(String routeNo) {
        this.routeNo = routeNo;
    }

    volatile LatLng position;
    volatile Marker busMarker;
    volatile OnInfoUpdatedListener locationUpdatedListener;
    volatile boolean isSharerOnline;
    public volatile boolean isMarkerVisible;
    volatile int speed;


    public void setUserOnline(boolean sharerOnline) {
        isSharerOnline = sharerOnline;
        locationUpdatedListener.onOnlineStatusChanged(routeNo, sharerOnline);
    }

    public void setSpeed(int speed) {
        this.speed = speed;
        if (busMarker != null && busMarker.isInfoWindowShown()) {
            busMarker.hideInfoWindow();
            busMarker.setTitle("Est. Speed: " + speed + " km/h");
            busMarker.showInfoWindow();
        }
        if (isSharerOnline) locationUpdatedListener.onSpeedChanged(routeNo, speed);
    }

    public String getRouteNo() {
        return routeNo;
    }

    public boolean isSharerOnline() {
        return isSharerOnline;
    }

    public int getSpeed() {
        return speed;
    }

    public void setInfoUpdatedListener(OnInfoUpdatedListener locationUpdatedListener) {
        this.locationUpdatedListener = locationUpdatedListener;
    }

    public BusObject() {
        isSharerOnline = false;
        speed = 0;
        routeNo = currentVolunteerId = "";
        isMarkerVisible = false;
        locationUpdatedListener = null;
    }

    public BusObject(String routeNo, String currentVolunteerId, Marker busMarker, int speed, boolean isSharerOnline, OnInfoUpdatedListener locationUpdatedListener) {
        this.currentVolunteerId = currentVolunteerId;
        this.busMarker = busMarker;
        this.routeNo = routeNo;
        this.speed = speed;
        this.isSharerOnline = isSharerOnline;
        this.locationUpdatedListener = locationUpdatedListener;
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

    public void setLocation(LatLng location, Handler handler, GoogleMap googleMap) {
        if (position == null && googleMap != null) {
            handler.post(() -> googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 18f)));
        }
        position = location;
        if (busMarker != null)
            busMarker.setPosition(location);
        locationUpdatedListener.onLocationChanged(routeNo, location);
    }

    public void setBusMarker(Marker busLocation) {
        this.busMarker = busLocation;
    }

    public String getCurrentVolunteerId() {
        return currentVolunteerId;
    }

    public void setCurrentVolunteerId(String currentVolunteerId) {
        this.currentVolunteerId = currentVolunteerId;
        locationUpdatedListener.onSharerIdChanged(routeNo, currentVolunteerId);
    }

    public void moveMarker(GoogleMap googleMap, LatLng newLatLng) {
        if (busMarker == null) return;

        boolean contains = googleMap.getProjection()
                .getVisibleRegion()
                .latLngBounds
                .contains(newLatLng);

        if (!contains) {
            // MOVE CAMERA
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(newLatLng));
        }
        busMarker.setPosition(newLatLng);
    }

    public interface OnInfoUpdatedListener {
        void onSharerIdChanged(String r, String newSharerId);

        void onLocationChanged(String r, LatLng location);

        void onOnlineStatusChanged(String r, boolean isOnline);

        void onSpeedChanged(String r, int newSpeed);
    }

}
