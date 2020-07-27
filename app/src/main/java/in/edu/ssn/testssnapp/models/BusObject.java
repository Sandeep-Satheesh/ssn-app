package in.edu.ssn.testssnapp.models;

import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

public class BusObject {
    volatile String currentVolunteerId;

    public volatile LatLng position;
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

    private static float bearingBetweenLocations(LatLng latLng1, LatLng latLng2) {

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
        brng = (int) Math.toDegrees(brng);
        brng = (brng + 360) % 360;

        return brng;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
        if (infoUpdatedListener != null)
            infoUpdatedListener.onSpeedChanged(speed);
    }

    public void setInfoUpdatedListener(OnInfoUpdatedListener locationUpdatedListener) {
        this.infoUpdatedListener = locationUpdatedListener;
    }

    public void setUserOnline(boolean sharerOnline) {
//        if (sharerOnline == isSharerOnline) return;
        position = null;
        isSharerOnline = sharerOnline;
        infoUpdatedListener.onOnlineStatusChanged(sharerOnline);
    }

    public void rotateMarker(final Marker marker, final float toRotation, Handler handler) {
        final long start = SystemClock.uptimeMillis();
        final float startRotation = marker.getRotation();
        final long duration = 500;
        float deltaRotation = Math.abs(toRotation - startRotation) % 360;
        final float rotation = (deltaRotation > 180 ? 360 - deltaRotation : deltaRotation) *
                ((toRotation - startRotation >= 0 && toRotation - startRotation <= 180) || (toRotation - startRotation <= -180 && toRotation - startRotation >= -360) ? 1 : -1);

        final LinearInterpolator interpolator = new LinearInterpolator();
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed / duration);
                marker.setRotation((startRotation + t * rotation) % 360);
                if (t < 1.0) {
                    handler.postDelayed(this, 10);
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

    public void animateBusMarker(final LatLng startPosition, final LatLng endPosition, GoogleMap googleMap, Handler handler, int duration) {
        boolean contains = googleMap.getProjection()
                .getVisibleRegion()
                .latLngBounds
                .contains(endPosition);
        if (!contains) {
            // MOVE CAMERA
            handler.post(() -> googleMap.animateCamera(CameraUpdateFactory.newLatLng(endPosition)));
        }

        ValueAnimator positionAnimator = ValueAnimator.ofFloat(0, 1), rotationAnimator = ValueAnimator.ofFloat(busMarker.getRotation(), bearingBetweenLocations(startPosition, endPosition));
        positionAnimator.setDuration(duration);
        rotationAnimator.setDuration(duration);
        final LatLngInterpolatorNew latLngInterpolator = new LatLngInterpolatorNew.LinearFixed();

        positionAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.setInterpolator(new LinearInterpolator());

        positionAnimator.addUpdateListener(valueAnimator1 -> {

            float v = valueAnimator1.getAnimatedFraction();

            LatLng newPos = latLngInterpolator.interpolate(v, startPosition, endPosition);
            busMarker.setPosition(newPos);
            busMarker.setRotation(bearingBetweenLocations(newPos, endPosition));
        });
        positionAnimator.start();
        rotationAnimator.addUpdateListener(animation -> busMarker.setRotation((Float) animation.getAnimatedValue()));
        rotationAnimator.start();
    }

    public interface LatLngInterpolatorNew {

        LatLng interpolate(float fraction, LatLng a, LatLng b);

        class LinearFixed implements LatLngInterpolatorNew {
            @Override
            public LatLng interpolate(float fraction, LatLng a, LatLng b) {
                double lat = (b.latitude - a.latitude) * fraction + a.latitude;
                double lngDelta = b.longitude - a.longitude;
                // Take the shortest path across the 180th meridian.
                if (Math.abs(lngDelta) > 180) {
                    lngDelta -= Math.signum(lngDelta) * 360;
                }
                double lng = lngDelta * fraction + a.longitude;
                return new LatLng(lat, lng);
            }
        }
    }

    public interface OnInfoUpdatedListener {
        void onSharerIdChanged(String newSharerId);

        void onLocationChanged(LatLng location);

        void onOnlineStatusChanged(boolean isOnline);

        void onSpeedChanged(int newSpeed);
    }

}
