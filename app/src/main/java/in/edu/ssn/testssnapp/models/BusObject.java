package in.edu.ssn.testssnapp.models;

import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

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
    volatile float accumulatedRotation = 0f;

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
                    handler.postDelayed(this, 5);
                }
            }
        });
    }

    public void animateMarker(final LatLng finalPosition, GoogleMap googleMap, Handler handler) {
        if (busMarker == null || googleMap == null) return;
        if (position == null) {
            handler.post(() -> googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(finalPosition, 18f)));
        }
        final LatLng startPosition = position;
        final long start = SystemClock.uptimeMillis();
        final Interpolator interpolator = new AccelerateDecelerateInterpolator();
        final float durationInMs = 1000;
        boolean contains = googleMap.getProjection()
                .getVisibleRegion()
                .latLngBounds
                .contains(finalPosition);
        if (!contains) {
            // MOVE CAMERA
            handler.post(() -> {
                googleMap.animateCamera(CameraUpdateFactory.newLatLng(finalPosition));
                position = finalPosition;
            });
        }
        if (position != null) {
            float b = bearingBetweenLocations(position, finalPosition);
            rotateMarker(busMarker, b, handler);
            /*accumulatedRotation = accumulatedRotation + b;
            if (Math.abs(accumulatedRotation) >= 5) {
                if (b < 5) rotateMarker(busMarker, accumulatedRotation, handler);
                else rotateMarker(busMarker, b, handler);
                accumulatedRotation = 0;
            }*/
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
                    position = finalPosition;
                    // Repeat till progress is complete
                    if (t < 1) {
                        handler.postDelayed(this, 5);
                    } else {
                        busMarker.setVisible(true);
                    }
                }
            });
        }
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
