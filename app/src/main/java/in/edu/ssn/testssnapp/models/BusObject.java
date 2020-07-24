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
        if (position != null && position.latitude == location.latitude && position.longitude == location.longitude)
            return;
        position = location;
        if (busMarker != null) {
            busMarker.remove();
            busMarker.setPosition(location);
        }
        infoUpdatedListener.onLocationChanged(location);
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

    public void moveMarker(GoogleMap googleMap, Handler handler, LatLng location) {
        if (busMarker == null || googleMap == null) return;
        if (position == null) {
            handler.post(() -> googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 18f)));
        }
        if (position != null && position.latitude == location.latitude && position.longitude == location.longitude)
            return;
        position = location;
        boolean contains = googleMap.getProjection()
                .getVisibleRegion()
                .latLngBounds
                .contains(location);
        if (!contains) {
            // MOVE CAMERA
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(location));
        }
        busMarker.setPosition(location);
        if (infoUpdatedListener != null) infoUpdatedListener.onLocationChanged(location);
    }

    public interface OnInfoUpdatedListener {
        void onSharerIdChanged(String newSharerId);

        void onLocationChanged(LatLng location);

        void onOnlineStatusChanged(boolean isOnline);

        void onSpeedChanged(int newSpeed);
    }

}
