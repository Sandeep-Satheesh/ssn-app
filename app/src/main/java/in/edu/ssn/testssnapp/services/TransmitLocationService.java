package in.edu.ssn.testssnapp.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import in.edu.ssn.testssnapp.BusTrackingActivity;
import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.utils.SharedPref;

public class TransmitLocationService extends Service implements LocationListener {
    DatabaseReference busLocDBRef;
    LocationManager locationManager;
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    String routeNo, latLongString, userID;
    int speed;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null)
            return super.onStartCommand(intent, flags, startId);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        routeNo = intent.getStringExtra("routeNo");
        userID = SharedPref.getString(getApplicationContext(),"email");
        busLocDBRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo).getRef();

        switch (intent.getAction()) {
            case ACTION_START_FOREGROUND_SERVICE:
                try {
                    if (locationManager != null) {
                        busLocDBRef.child("currentSharerID").setValue(userID);
                        busLocDBRef.child("sharingLoc").setValue(true);
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 20, this);
                        Location l = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (l != null) {
                            latLongString = l.getLatitude() + "," +l.getLongitude();
                            busLocDBRef.child("latLong").setValue(latLongString);
                            speed =(int) l.getSpeed();
                            busLocDBRef.child("speed").setValue(speed);
                        }
                    }
                } catch (SecurityException e) {
                    e.printStackTrace();
                }

                //Build the notification...
                startForeground(1, prepareForegroundServiceNotification("Sharing your location", "Your location will be used to determine your bus' location.", this, new Intent(this, BusTrackingActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), getResources()));
//                Toast.makeText(getApplicationContext(), "Location Sharing started successfully.", Toast.LENGTH_SHORT).show();

                break;
            case ACTION_STOP_FOREGROUND_SERVICE:
                busLocDBRef.removeValue();
                locationManager.removeUpdates(this);
                stopForeground(true);
                stopSelf();
        }
        return START_STICKY;
    }

    public Notification prepareForegroundServiceNotification(String title, String message, Context context, Intent intent, Resources r) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(new NotificationChannel("1", "general", NotificationManager.IMPORTANCE_HIGH));
            Notification.Builder nbuilder = new Notification.Builder(context, "1")
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
            Notification.Builder nbuilder = new Notification.Builder(context)
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
        latLongString = location.getLatitude()+","+location.getLongitude();
        busLocDBRef.child("latLong").setValue(latLongString);
        speed =(int) location.getSpeed();
        busLocDBRef.child("speed").setValue(speed);
        busLocDBRef.child("sharingLoc").setValue(true);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        try {
            if (status == LocationProvider.AVAILABLE) {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location != null) {
                    latLongString = location.getLatitude()+","+location.getLongitude();
                    busLocDBRef.child("latLong").setValue(latLongString);
                    speed =(int) location.getSpeed();
                    busLocDBRef.child("speed").setValue(speed);
                    busLocDBRef.child("sharingLoc").setValue(true);

                }
            } else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                busLocDBRef.child("sharingLoc").setValue(false);
                busLocDBRef.child("speed").setValue(0);
                busLocDBRef.child("currentSharerID").setValue("null");
            }
            else {
                busLocDBRef.removeValue();
                stopForeground(true);
                stopSelf();
            }

        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        busLocDBRef.child("currentSharerID").setValue(userID);
        try {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                latLongString = location.getLatitude()+","+location.getLongitude();
                busLocDBRef.child("latLong").setValue(latLongString);
                speed = (int) location.getSpeed();
                busLocDBRef.child("speed").setValue(speed);
                busLocDBRef.child("sharingLoc").setValue(true);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        busLocDBRef.child("sharingLoc").setValue(false);
        busLocDBRef.child("speed").setValue(0);
        busLocDBRef.child("currentSharerID").setValue("null");
    }

}

