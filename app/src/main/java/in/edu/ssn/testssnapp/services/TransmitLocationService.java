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
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;

import in.edu.ssn.testssnapp.BusRoutesActivity;
import in.edu.ssn.testssnapp.MapActivity;
import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import in.edu.ssn.testssnapp.utils.Constants;
import in.edu.ssn.testssnapp.utils.SharedPref;
import in.edu.ssn.testssnapp.utils.SystemTimeChangedReceiver;

public class TransmitLocationService extends Service implements LocationListener {
    DatabaseReference busLocDBRef;
    LocationManager locationManager;
    volatile boolean suspendFlag = false, allowLocationFromMockProvider = false;
    //    double SSNCEPolygon[]; //TODO: The polygon within which we have to check to stop service automatically; check note at end.
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    public static final String ACTION_CHANGE_NOTIFICATION_MESSAGE = "ACTION_CHANGE_NOTIFICATION_MESSAGE";
    String routeNo, latLongString, userID;
    int speed;
    SystemTimeChangedReceiver systemTimeChangedReceiver;
    IntentFilter intentFilter;

    public static Notification prepareForegroundServiceNotification(boolean goToActivity, String title, String message, Context context, Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, goToActivity ? intent : new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder nbuilder;
        NotificationManager notificationManager;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(new NotificationChannel(Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID, "general", NotificationManager.IMPORTANCE_HIGH));
            NotificationChannel channel = notificationManager.getNotificationChannel(Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID);
            channel.enableLights(true);
            channel.setLightColor(Color.BLUE);
            channel.setDescription("Bus Tracking Volunteer Status");
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            nbuilder = new NotificationCompat.Builder(context, Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ssn_logo)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setChannelId(Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID)
                    .setPriority(NotificationManager.IMPORTANCE_MAX)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setColorized(false)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setContentIntent(pendingIntent);

            return nbuilder.build();
        } else {
            nbuilder = new NotificationCompat.Builder(context, Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ssn_logo)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setAutoCancel(false)
                    .setPriority(NotificationManagerCompat.IMPORTANCE_MAX)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setOngoing(true)
                    .setColor(Color.LTGRAY)
                    .setSound(alarmSound)
                    .setContentIntent(pendingIntent);

            return nbuilder.build();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return super.onStartCommand(intent, flags, startId);
        }
        allowLocationFromMockProvider = SharedPref.getBoolean(getApplicationContext(), "allow_mockloc_provider");
        switch (intent.getAction()) {
            case ACTION_START_FOREGROUND_SERVICE:
                locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
                routeNo = intent.getStringExtra("routeNo");
                suspendFlag = intent.getBooleanExtra("suspendFlag", true);
                SharedPref.putBoolean(getApplicationContext(), "service_suspended", suspendFlag);
                userID = SharedPref.getString(getApplicationContext(), "email");

                validateCurrentTime();

                busLocDBRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);
                try {
                    unregisterReceiver(systemTimeChangedReceiver);
                } catch (Exception e) {
                    e.printStackTrace();
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
                try {
                    if (locationManager != null) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, this);
                        Location l = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (l != null) {
                            checkLocationValidity(l);
                            latLongString = l.getLatitude() + "," + l.getLongitude();
                            busLocDBRef.child("latLong").setValue(latLongString);
                            speed = (int) l.getSpeed();
                            busLocDBRef.child("speed").setValue(speed);
                            busLocDBRef.child("currentSharerID").setValue(userID);
                            busLocDBRef.child("sharingLoc").setValue(true);
                        }
                    }
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
                busLocDBRef.child("currentSharerID").onDisconnect().setValue("null");
                busLocDBRef.child("sharingLoc").onDisconnect().setValue(false);
                //Build the notification...
                startForeground(1, prepareForegroundServiceNotification(true, "Sharing your location", "Your location will be used to determine your bus' location.", this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)));
//                Toast.makeText(getApplicationContext(), "Location Sharing started successfully.", Toast.LENGTH_SHORT).show();

                break;
            case ACTION_CHANGE_NOTIFICATION_MESSAGE:
                locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
                routeNo = intent.getStringExtra("routeNo");
                suspendFlag = intent.getBooleanExtra("suspendFlag", true);
                SharedPref.putBoolean(getApplicationContext(), "service_suspended", suspendFlag);

                if (suspendFlag) {
                    int disruptionCount = SharedPref.getInt(getApplicationContext(), "disruption_count");
                    SharedPref.putInt(getApplicationContext(), "disruption_count", disruptionCount + 1);
                }
                userID = SharedPref.getString(getApplicationContext(), "email");
                busLocDBRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);
                validateCurrentTime();

                String messageTitle = intent.getStringExtra("messageTitle"), messageContent = intent.getStringExtra("messageContent");
                suspendFlag = intent.getBooleanExtra("suspendFlag", false);
                if (messageTitle != null) {
                    startForeground(1, prepareForegroundServiceNotification(!messageTitle.contains("Reconnecting"), messageTitle, messageContent, this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)));
                }
                break;
            case ACTION_STOP_FOREGROUND_SERVICE:
                locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
                routeNo = intent.getStringExtra("routeNo");
                suspendFlag = intent.getBooleanExtra("suspendFlag", true);
                SharedPref.putBoolean(getApplicationContext(), "service_suspended", false);
                userID = SharedPref.getString(getApplicationContext(), "email");
                if (routeNo != null && !routeNo.isEmpty())
                    busLocDBRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);

                locationManager.removeUpdates(this);
                try {
                    unregisterReceiver(systemTimeChangedReceiver);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (busLocDBRef != null) {
                    busLocDBRef.child("currentSharerID").onDisconnect().cancel();
                    busLocDBRef.child("sharingLoc").onDisconnect().cancel();
                    if (intent.getBooleanExtra("deleteDBValue", false)) {
                        busLocDBRef.removeValue();
                    }
                }
                stopForeground(true);
                stopSelf();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onLocationChanged(Location location) {
        //changeProviderIfRequired(locationManager.getBestProvider(criteria, true));
        if (CommonUtils.alerter(this) || suspendFlag) return;
        validateCurrentTime();
        checkLocationValidity(location);
        busLocDBRef.child("currentSharerID").setValue(userID);
        busLocDBRef.child("sharingLoc").setValue(true);
        latLongString = location.getLatitude() + "," + location.getLongitude();
        busLocDBRef.child("latLong").setValue(latLongString);
        speed = (int) location.getSpeed();
        busLocDBRef.child("speed").setValue(speed);
        busLocDBRef.child("sharingLoc").setValue(true);
    }

    private void checkLocationValidity(Location location) {
        if (location.isFromMockProvider() && !allowLocationFromMockProvider) {
            try {
                locationManager.removeUpdates(TransmitLocationService.this);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                unregisterReceiver(systemTimeChangedReceiver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (busLocDBRef == null)
                busLocDBRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo);
            busLocDBRef.removeValue((error, ref) -> {
                busLocDBRef.removeValue();
                MapActivity.showNotification(4, Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID, "Location sharing force-stopped.", "You are not allowed to emulate your GPS location!", getApplicationContext(), new Intent());
                Intent i = new Intent(getApplicationContext(), TransmitLocationService.class);
                i.setAction(TransmitLocationService.ACTION_STOP_FOREGROUND_SERVICE);
                i.putExtra("deleteDBValue", false);
                Toast.makeText(getApplicationContext(), "You are not allowed to emulate your GPS location! The service is stopping...", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, BusRoutesActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                startService(i);
            });
        }
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
                    checkLocationValidity(location);
                    latLongString = location.getLatitude() + "," + location.getLongitude();
                    busLocDBRef.child("latLong").setValue(latLongString);
                    speed = (int) location.getSpeed();
                    busLocDBRef.child("speed").setValue(speed);
                    busLocDBRef.child("sharingLoc").setValue(true);
                }
            } else {
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
        if (!suspendFlag && !provider.equals(LocationManager.GPS_PROVIDER)) {
            suspendFlag = true;
            return;
        }
        suspendFlag = false;
        startForeground(1, prepareForegroundServiceNotification(true, "Sharing your location", "Your location will be used to determine your bus' location.", this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null || routeNo.isEmpty() ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)));
        if (suspendFlag || CommonUtils.alerter(this)) return;
        if (CommonUtils.alerter(this) || suspendFlag) return;
        try {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                checkLocationValidity(location);
                latLongString = location.getLatitude() + "," + location.getLongitude();
                busLocDBRef.child("latLong").setValue(latLongString);
                speed = (int) location.getSpeed();
                busLocDBRef.child("speed").setValue(speed);
                busLocDBRef.child("currentSharerID").setValue(userID);
                busLocDBRef.child("sharingLoc").setValue(true);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (!suspendFlag && !provider.equals(LocationManager.GPS_PROVIDER)) {
            busLocDBRef.child("currentSharerID").setValue("null");
            busLocDBRef.child("sharingLoc").setValue(false);
            //busLocDBRef.child("speed").setValue(0);
            suspendFlag = true;
            return;
        }
        startForeground(1, prepareForegroundServiceNotification(true, "Unable to use GPS", "Please check if your GPS is enabled. Your location will be used to determine your bus' location.", this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null || routeNo.isEmpty() ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)));
        if (suspendFlag || CommonUtils.alerter(this)) return;
        busLocDBRef.child("currentSharerID").setValue("null");
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
            e.printStackTrace();
        }
        try {
            unregisterReceiver(systemTimeChangedReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void validateCurrentTime() {
        String currentTime = new SimpleDateFormat("EEE, MMM dd yyyy, HH:mm").format(System.currentTimeMillis() + SharedPref.getLong(getApplicationContext(), "time_offset")).substring(18),
                startTime = SharedPref.getString(getApplicationContext(), "bustracking_starttime"),
                endTime = SharedPref.getString(getApplicationContext(), "bustracking_endtime");

        if (!(currentTime.compareTo(endTime) < 0 && currentTime.compareTo(startTime) > 0)) {
            MapActivity.showNotification(4, Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID, "Location sharing force-stopped.", "You have exceeded the time limit allowed to use this feature! Thank you for your services.", getApplicationContext(), new Intent());
            try {
                busLocDBRef.child("timeLimitViolation").setValue(true);
            } catch (Exception e) {
            }
            try {
                Toast.makeText(getApplicationContext(), "The current time is: " + currentTime + ". You have exceeded the allowed time limits to use this feature!", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
            }

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