package in.edu.ssn.testssnapp;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Objects;

import in.edu.ssn.testssnapp.models.BusObject;
import in.edu.ssn.testssnapp.utils.Constants;
import in.edu.ssn.testssnapp.utils.SharedPref;
import spencerstudios.com.bungeelib.Bungee;

public class BusTrackingActivityDuplicate extends BaseActivity implements GoogleMap.OnMarkerClickListener, OnMapReadyCallback {
    ImageView backIV, isBusOnlineIV;
    String email, volunteer, routeNo;
    MapView busTrackingMap;
    LatLng SSNCEPoint = new LatLng(12.7525, 80.196111);
    GoogleMap googleMap;
    ProgressDialog pd;
    boolean isMapLoaded = false;
    DatabaseReference busLocRef;
    ValueEventListener routeExistslistener;
    BusObject currentBusObject;
    TextView tvVolunteerDetails, tvTrackbus, tvNoVolunteer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (darkModeEnabled) {
            setContentView(R.layout.activity_bustracking_dark);
            clearLightStatusBar(this);
        } else {
            setContentView(R.layout.activity_bustracking);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            getWindow().setStatusBarColor(getResources().getColor(R.color.colorAccent));

        //set up pd...
        pd = new ProgressDialog(this);
        pd.setCancelable(false);

        initUI(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (googleMap != null) busTrackingMap.onStart();
    }

    @Override
    public void onBackPressed() {
        Bungee.slideRight(this);
        finish();
    }

    private void initUI(Bundle bundle) {
        backIV = findViewById(R.id.backIV);
        tvTrackbus = findViewById(R.id.tv_trackbus);
        isBusOnlineIV = findViewById(R.id.iv_busOnlineStatus);
        backIV.setOnClickListener(v -> {
            onBackPressed();
        });
        email = SharedPref.getString(getApplicationContext(), "email");

        routeNo = "30A";
        //TODO: Replace with this after implementing busrouteactivity part: routeNo = getIntent().getStringExtra("routeno");
        volunteer = SharedPref.getString(getApplicationContext(), "student_volunteer", email);
        tvVolunteerDetails = findViewById(R.id.tv_volunteerid);
        tvNoVolunteer = findViewById(R.id.tv_novolunteer);
        if (volunteer.equals("TRUE"))
            tvNoVolunteer.setText(R.string.would_you_like_to_volunteer);

        else tvNoVolunteer.setText(R.string.no_volunteer_available);
        busTrackingMap = findViewById(R.id.mapView_bustracking);
        tvTrackbus.setText(String.format("Track Bus No. %s", routeNo));
        busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);
        routeExistslistener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.getChildrenCount() == 4) {
                    pd.setMessage("Please wait, the map is loading...");
                    pd.show();
                    initMapView(bundle);
                    tvNoVolunteer.setVisibility(View.GONE);
                } else hideMapView();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                hideMapView();
            }
        };
        busLocRef.addValueEventListener(routeExistslistener);

    }

    private void hideMapView() {
        if (busTrackingMap != null) busTrackingMap.setVisibility(View.GONE);
        tvVolunteerDetails.setVisibility(View.GONE);
        tvNoVolunteer.setVisibility(View.VISIBLE);
    }

    private void initMapView(Bundle b) {
        Bundle mapViewBundle = null;
        if (b != null) {
            mapViewBundle = b.getBundle(Constants.GMAPS_TEST_API_KEY);
        }
        busTrackingMap.onCreate(mapViewBundle);
        busTrackingMap.getMapAsync(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (googleMap != null) busTrackingMap.onResume();
        if (currentBusObject != null)
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentBusObject.getLocation(), googleMap.getCameraPosition().zoom));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle mapViewBundle = outState.getBundle(Constants.GMAPS_TEST_API_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(Constants.GMAPS_TEST_API_KEY, mapViewBundle);
        }
        busTrackingMap.onSaveInstanceState(mapViewBundle);
    }

    public void onMapReady(final GoogleMap googleMap) {
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(SSNCEPoint, 18f));
        googleMap.setTrafficEnabled(true);
        googleMap.setBuildingsEnabled(true);
        googleMap.setMinZoomPreference(10f);
        googleMap.setMaxZoomPreference(19f);

        if (darkModeEnabled)
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.nightmode_mapstyle));

        googleMap.addMarker(new MarkerOptions().title("College").position(SSNCEPoint));
        googleMap.setOnMarkerClickListener(this);
        this.googleMap = googleMap;
        pd.dismiss();
        busTrackingMap.onStart();
        busLocRef.removeEventListener(routeExistslistener);
        listenForInfoChanges();
    }

    private void listenForInfoChanges() {
        busLocRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() == 4) {
                    String latLongString = dataSnapshot.child("latLong").getValue(String.class), sharerId = dataSnapshot.child("currentSharerID").getValue(String.class);

                    int speed = dataSnapshot.child("speed").getValue() != null ? (int) (dataSnapshot.child("speed").getValue(int.class) * 3.6 < 1 ? 0 : dataSnapshot.child("speed").getValue(int.class) * 3.6) : 0;
                    boolean isSharingLoc = dataSnapshot.child("sharingLoc").getValue(boolean.class) == null ? false : dataSnapshot.child("sharingLoc").getValue(boolean.class);

                    if (latLongString == null || latLongString.isEmpty() || sharerId == null || sharerId.isEmpty())
                        return;

                    int sep = latLongString.indexOf(',');
                    LatLng currentlatLongs = new LatLng(sep == 1 ? 0 : Double.parseDouble(latLongString.substring(0, sep - 1)), sep == 1 ? 0 : Double.parseDouble(latLongString.substring(sep + 1)));

                    if (currentBusObject == null) {
                        currentBusObject = createBusObject(sharerId, currentlatLongs, speed, isSharingLoc);
                        currentBusObject.setSharerOnline(isSharingLoc);
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentBusObject.getLocation(), 18f));
                    } else if (!sharerId.equals("null")) {
                        if (currentlatLongs.latitude != currentBusObject.getLocation().latitude || currentlatLongs.longitude != currentBusObject.getLocation().longitude)
                            currentBusObject.setLocation(currentlatLongs);

                        if (speed != currentBusObject.getSpeed())
                            currentBusObject.setSpeed(speed);

                        if (!Objects.equals(sharerId, currentBusObject.getCurrentVolunteerId()))
                            currentBusObject.setCurrentVolunteerId(sharerId);

                        if (currentBusObject.isSharerOnline() != isSharingLoc)
                            currentBusObject.setSharerOnline(isSharingLoc);
                    } else currentBusObject.setSharerOnline(false);
                } else if (currentBusObject != null) {
                    currentBusObject.setSharerOnline(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private BusObject createBusObject(String sharerId, LatLng currentlatLongs, int speed, boolean isSharingLoc) {
        Marker busMarker = googleMap.addMarker(new MarkerOptions()
                .position(currentlatLongs)
                .icon(darkModeEnabled ? getBitmapDescriptor(R.drawable.ic_bus_yellow) : getBitmapDescriptor(R.drawable.ic_bus_blue))
                .title("Est. Speed: " + speed + " km/h"));

        return new BusObject(routeNo, sharerId, busMarker, speed, isSharingLoc, new BusObject.OnLocationUpdatedListener() {
            @Override
            public void onSharerIdChanged(String r, String newSharerId) {

            }

            @Override
            public void onLocationChanged(String r, LatLng location) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, googleMap.getCameraPosition().zoom));
            }

            @Override
            public void onOnlineStatusChanged(String r, boolean isOnline) {
                if (isOnline) {
                    isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_online);
                    tvVolunteerDetails.setText(String.format("Current Volunteer%s", currentBusObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID: " + sharerId + "     "));
                } else {
                    isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_offline);
                    tvVolunteerDetails.setText(String.format("Location last volunteered by: %s", currentBusObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID: " + currentBusObject.getCurrentVolunteerId() + "     "));
                }
            }

            @Override
            public void onSpeedChanged(String r, int newSpeed) {
                if (busMarker.isInfoWindowShown()) {
                    busMarker.hideInfoWindow();
                    busMarker.showInfoWindow();
                }
            }
        });
    }

    private BitmapDescriptor getBitmapDescriptor(int id) {
        Drawable vectorDrawable = getResources().getDrawable(id);
        int h = vectorDrawable.getIntrinsicHeight();
        int w = vectorDrawable.getIntrinsicWidth();
        vectorDrawable.setBounds(0, 0, w * 2, h * 2);
        Bitmap bm = Bitmap.createBitmap(2 * w, 2 * h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bm);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        marker.showInfoWindow();
        return false;
    }
}