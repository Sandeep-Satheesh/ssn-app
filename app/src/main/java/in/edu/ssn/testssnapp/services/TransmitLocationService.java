package in.edu.ssn.testssnapp.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import in.edu.ssn.testssnapp.BusRoutesActivity;
import in.edu.ssn.testssnapp.MapActivity;
import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import in.edu.ssn.testssnapp.utils.SharedPref;
import in.edu.ssn.testssnapp.utils.SystemTimeChangedReceiver;

public class TransmitLocationService extends Service implements LocationListener {
    DatabaseReference busLocDBRef;
    LocationManager locationManager;
    volatile boolean suspendFlag = false;
    //    double SSNCEPolygon[]; //TODO: The polygon within which we have to check to stop service automatically; check note at end.
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    public static final String ACTION_CHANGE_NOTIFICATION_MESSAGE = "ACTION_CHANGE_NOTIFICATION_MESSAGE";
    String routeNo, latLongString, userID;
    int speed;
    SystemTimeChangedReceiver systemTimeChangedReceiver;
    IntentFilter intentFilter;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null || intent.getAction() == null) {
            return super.onStartCommand(intent, flags, startId);
        }
        locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        routeNo = intent.getStringExtra("routeNo");
        suspendFlag = intent.getBooleanExtra("suspendFlag", true);
        userID = SharedPref.getString(getApplicationContext(), "email");
        busLocDBRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo).getRef();
        validateCurrentTime();

        switch (intent.getAction()) {
            case ACTION_START_FOREGROUND_SERVICE:
                try {
                    unregisterReceiver(systemTimeChangedReceiver);
                } catch (Exception e) {
                }
                intentFilter = new IntentFilter();
                intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
                intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
                systemTimeChangedReceiver = new SystemTimeChangedReceiver();
                try {
                    registerReceiver(systemTimeChangedReceiver, intentFilter);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                busLocDBRef.child("currentSharerID").setValue(userID);
                try {
                    if (locationManager != null) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 100, this);
                        Location l = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (l != null) {
                            latLongString = l.getLatitude() + "," + l.getLongitude();
                            busLocDBRef.child("latLong").setValue(latLongString);
                            speed = (int) l.getSpeed();
                            busLocDBRef.child("speed").setValue(speed);
                            busLocDBRef.child("sharingLoc").setValue(true);
                        }
                    }
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
                busLocDBRef.child("currentSharerID").onDisconnect().setValue("null");
                busLocDBRef.child("sharingLoc").onDisconnect().setValue(false);
                //Build the notification...
                startForeground(1, prepareForegroundServiceNotification(true, "Sharing your location", "Your location will be used to determine your bus' location.", this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", SharedPref.getString(getApplicationContext(), "routeNo"))));
//                Toast.makeText(getApplicationContext(), "Location Sharing started successfully.", Toast.LENGTH_SHORT).show();

                break;
            case ACTION_CHANGE_NOTIFICATION_MESSAGE:
                String messageTitle = intent.getStringExtra("messageTitle"), messageContent = intent.getStringExtra("messageContent");
                startForeground(1, prepareForegroundServiceNotification(messageTitle.contains("suspended"), messageTitle, messageContent, this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", SharedPref.getString(getApplicationContext(), "routeNo"))));
                break;
            case ACTION_STOP_FOREGROUND_SERVICE:
                locationManager.removeUpdates(this);
                try {
                    unregisterReceiver(systemTimeChangedReceiver);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (intent.getBooleanExtra("deleteDBValue", false))
                    busLocDBRef.removeValue();
                else
                    busLocDBRef.child("sharingLoc").setValue(false);

                stopForeground(true);
                stopSelf();

                return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    public static Notification prepareForegroundServiceNotification(boolean goToActivity, String title, String message, Context context, Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, goToActivity ? intent : new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder nbuilder;
        NotificationManager notificationManager;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(new NotificationChannel("1", "general", NotificationManager.IMPORTANCE_HIGH));
            NotificationChannel channel = notificationManager.getNotificationChannel("1");
            channel.enableLights(true);
            channel.setLightColor(Color.BLUE);
            channel.setDescription("Bus Tracking Volunteer Status");
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            nbuilder = new Notification.Builder(context, "1")
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ssn_logo)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setChannelId("1")
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setSound(alarmSound)
                    .setContentIntent(pendingIntent);

            return nbuilder.build();
        } else {
            nbuilder = new Notification.Builder(context)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ssn_logo)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setSound(alarmSound)
                    .setContentIntent(pendingIntent);

            return nbuilder.build();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onLocationChanged(Location location) {
        validateCurrentTime();
        //changeProviderIfRequired(locationManager.getBestProvider(criteria, true));
        if (CommonUtils.alerter(this) || suspendFlag) return;
        busLocDBRef.child("currentSharerID").setValue(userID);
        busLocDBRef.child("sharingLoc").setValue(true);
        latLongString = location.getLatitude() + "," + location.getLongitude();
        busLocDBRef.child("latLong").setValue(latLongString);
        speed = (int) location.getSpeed();
        busLocDBRef.child("speed").setValue(speed);
        busLocDBRef.child("sharingLoc").setValue(true);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        validateCurrentTime();
        if (CommonUtils.alerter(this) || suspendFlag) return;
        try {
            if (status == LocationProvider.AVAILABLE) {
                busLocDBRef.child("currentSharerID").setValue(userID);
                busLocDBRef.child("sharingLoc").setValue(true);
                Location location = locationManager.getLastKnownLocation(provider);
                if (location != null) {
                    latLongString = location.getLatitude() + "," + location.getLongitude();
                    busLocDBRef.child("latLong").setValue(latLongString);
                    speed = (int) location.getSpeed();
                    busLocDBRef.child("speed").setValue(speed);
                    busLocDBRef.child("sharingLoc").setValue(true);
                }
            } else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                busLocDBRef.child("sharingLoc").setValue(false);
                busLocDBRef.child("speed").setValue(0);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        validateCurrentTime();
        suspendFlag = false;
        if (CommonUtils.alerter(this) || suspendFlag) return;
        busLocDBRef.child("currentSharerID").setValue(userID);
        busLocDBRef.child("sharingLoc").setValue(true);
        try {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                latLongString = location.getLatitude() + "," + location.getLongitude();
                busLocDBRef.child("latLong").setValue(latLongString);
                speed = (int) location.getSpeed();
                busLocDBRef.child("speed").setValue(speed);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        startForeground(1, prepareForegroundServiceNotification(true, "GPS is disabled", "Please enable GPS to continue location sharing", this, new Intent(this, MapActivity.class)));
        if (suspendFlag || CommonUtils.alerter(this)) return;
        //busLocDBRef.child("currentSharerID").setValue("null");
        busLocDBRef.child("sharingLoc").setValue(false);
        //busLocDBRef.child("speed").setValue(0);
        suspendFlag = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            locationManager.removeUpdates(this);
        } catch (Exception e) {
        }
        try {
            unregisterReceiver(systemTimeChangedReceiver);
        } catch (Exception e) {
        }
    }

    private void validateCurrentTime() {
        //String currentTime = new SimpleDateFormat("EEE, MMM dd yyyy, hh:mm:ss").format(System.currentTimeMillis() + SharedPref.getLong(getApplicationContext(), "time_offset")).substring(18);
        String currentTime = "07:00:00";
        if (currentTime.compareTo("08:00:00") > 0 || currentTime.compareTo("06:00:00") < 0) {
            MapActivity.showNotification(4, "1", "Location sharing force-stopped.", "You have exceeded the time limit allowed to use this feature! Thank you for your services.", getApplicationContext(), new Intent(this, BusRoutesActivity.class));
            busLocDBRef.child("currentSharerID").setValue(userID);
            busLocDBRef.child("timeLimitViolation").setValue(true);

            try {
                locationManager.removeUpdates(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                unregisterReceiver(systemTimeChangedReceiver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            stopForeground(true);
            stopSelf();
        }
    }
}

/*TODO: Convert this code to java and put it in the service, as a function, to check if a point lies within a polygon
function inside(point, vs) {
    // ray-casting algorithm based on
     //http://www.ecse.rpi.edu/Homepages/wrf/Research/Short_Notes/pnpoly.html

    var x = point[0], y = point[1];

    var inside = false;
    for (var i = 0, j = vs.length - 1; i < vs.length; j = i++) {
        var xi = vs[i][0], yi = vs[i][1];
        var xj = vs[j][0], yj = vs[j][1];

        var intersect = ((yi > y) != (yj > y))
            && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
        if (intersect) inside = !inside;
    }

    return inside;
};
*/
