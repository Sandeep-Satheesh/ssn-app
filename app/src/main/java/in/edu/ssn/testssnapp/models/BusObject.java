package in.edu.ssn.testssnapp.models;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

public class BusObject {
    String routeNo, currentVolunteerId;
    Marker busMarker;
    OnLocationUpdatedListener locationUpdatedListener;
    boolean isSharerOnline;
    int speed;


    public void setSharerOnline(boolean sharerOnline) {
        isSharerOnline = sharerOnline;
        locationUpdatedListener.onOnlineStatusChanged(routeNo, sharerOnline);
    }

    public void setSpeed(int speed) {
        this.speed = speed;
        busMarker.setTitle("Est. Speed: " + speed + " km/h");
        locationUpdatedListener.onSpeedChanged(routeNo, speed);
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

    public BusObject(String routeNo, String currentVolunteerId, Marker busMarker, int speed, boolean isSharerOnline, OnLocationUpdatedListener locationUpdatedListener) {
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
        return busMarker.getPosition();
    }

    public void setLocation(LatLng location) {
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
    public interface OnLocationUpdatedListener {
        void onSharerIdChanged(String r, String newSharerId);
        void onLocationChanged(String r, LatLng location);
        void onOnlineStatusChanged(String r, boolean isOnline);
        void onSpeedChanged(String r, int newSpeed);
    }
}
