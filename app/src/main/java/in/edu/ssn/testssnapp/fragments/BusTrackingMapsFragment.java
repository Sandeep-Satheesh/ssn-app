package in.edu.ssn.testssnapp.fragments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.fragment.app.Fragment;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

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

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.ArrayList;
import java.util.HashMap;

import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.adapters.BusTrackingAdapter;
import in.edu.ssn.testssnapp.models.BusObject;
import in.edu.ssn.testssnapp.utils.Constants;
import in.edu.ssn.testssnapp.utils.SharedPref;

import static java.lang.Character.isDigit;

public class BusTrackingMapsFragment extends Fragment implements GoogleMap.OnMarkerClickListener, OnMapReadyCallback {
    boolean darkModeEnabled;
    MapView busTrackingMap;
    LatLng SSNCEPoint = new LatLng(12.7525, 80.196111);
    AppCompatSpinner spnSelectBus;
    GoogleMap googleMap;
    String currentlySelectedBus;
    DatabaseReference userRef;
    BusTrackingAdapter busTrackingAdapter;
    BidiMap<String, BusObject> busVolunteersBidiMap = new DualHashBidiMap<>();
    TextView tvVolunteerDetails;
    View view;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        darkModeEnabled = SharedPref.getBoolean(getContext(), "dark_mode");
        if (darkModeEnabled) {
            view = inflater.inflate(R.layout.fragment_bustracking_map_dark, container,false);
        } else {
            view = inflater.inflate(R.layout.fragment_bustracking_map, container,false);
        }
        return view;
    }
    private void initMap(Bundle b) {
        busTrackingMap = view.findViewById(R.id.mapView_bustracking);
        Bundle mapViewBundle = null;
        if (b != null) {
            mapViewBundle = b.getBundle(Constants.GMAPS_TEST_API_KEY);
        }
        busTrackingMap.onCreate(mapViewBundle);
        busTrackingMap.getMapAsync(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        busTrackingMap.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        busTrackingMap.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        busTrackingMap.onResume();
        busTrackingAdapter.notifyDataSetChanged();
        if (busVolunteersBidiMap.get(currentlySelectedBus) != null) googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(busVolunteersBidiMap.get(currentlySelectedBus).getLocation(), googleMap.getCameraPosition().zoom));
    }

    @Override
    public void onPause() {
        super.onPause();
        busTrackingMap.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        busTrackingMap.onLowMemory();
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initUI(view);
        initMap(savedInstanceState);
    }
    private void initUI(View view) {
        tvVolunteerDetails = view.findViewById(R.id.tv_volunteerid);
        spnSelectBus = view.findViewById(R.id.spn_selectbus);
        busTrackingAdapter = new BusTrackingAdapter(getContext(), busVolunteersBidiMap.keySet().toArray(new String[0]));
        spnSelectBus.setAdapter(busTrackingAdapter);
        busTrackingMap = view.findViewById(R.id.mapView_bustracking);
        spnSelectBus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String busNo = ((TextView) view.findViewById(R.id.tv_busnumber)).getText().toString();
                currentlySelectedBus = busNo.substring(8);
                BusObject busObj = busVolunteersBidiMap.get(currentlySelectedBus);

                if (busObj != null) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(busVolunteersBidiMap.get(currentlySelectedBus).getLocation(), googleMap.getCameraPosition().zoom));
                    if (busObj.getCurrentVolunteerId() != null && !busObj.getCurrentVolunteerId().equals("null"))
                        tvVolunteerDetails.setText("Current Volunteer" + (busObj.getCurrentVolunteerId().equals(SharedPref.getString(getContext(), "email")) ? ": You" : " ID: " + busObj.getCurrentVolunteerId()));
                    else tvVolunteerDetails.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(SSNCEPoint, 18f));
            }
        });

    }

    public void onMapReady(final GoogleMap googleMap) {
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(SSNCEPoint, 18f));
        googleMap.setTrafficEnabled(true);
        googleMap.setBuildingsEnabled(true);
        googleMap.setMinZoomPreference(10f);
        googleMap.setMaxZoomPreference(19f);

        if (darkModeEnabled)
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(getContext(), R.raw.nightmode_mapstyle));

        googleMap.addMarker(new MarkerOptions().title("College").position(SSNCEPoint));
        googleMap.setOnMarkerClickListener(this);
        this.googleMap = googleMap;
        startReceivingLatLongs();
    }

    private void startReceivingLatLongs() {
        userRef = FirebaseDatabase.getInstance().getReference("Bus Locations");
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot ds : dataSnapshot.getChildren()) {
                    String latLongString = ds.child("latLong").getValue(String.class), routeNo = ds.getKey(), sharerId = ds.child("currentSharerID").getValue(String.class);

                    int speed = ds.child("speed").getValue() != null ? (int) (ds.child("speed").getValue(int.class) * 3.6 < 1 ? 0 : ds.child("speed").getValue(int.class) * 3.6) : 0;
                    boolean isSharingLoc = ds.child("sharingLoc").getValue(boolean.class) == null ? false : ds.child("sharingLoc").getValue(boolean.class);

                    if (latLongString == null || routeNo == null)
                        continue;

                    if (currentlySelectedBus == null) currentlySelectedBus = routeNo;

                    int sep = latLongString.indexOf(',');
                    LatLng currentlatLongs = new LatLng(Double.parseDouble(latLongString.substring(0, sep - 1)), Double.parseDouble(latLongString.substring(sep + 1)));

                    if (!busVolunteersBidiMap.containsKey(routeNo))
                        addNewBusMarker(routeNo, sharerId, currentlatLongs, speed, isSharingLoc);

                    else if (!sharerId.equals("null")) {
                        BusObject object = busVolunteersBidiMap.get(routeNo);
                        object.setLocation(currentlatLongs);
                        object.setSpeed(speed);
                        object.setSharerOnline(isSharingLoc);

                        if (!sharerId.equals(object.getCurrentVolunteerId()))
                            busTrackingAdapter.setOnlineStatus(routeNo, isSharingLoc);

                        busVolunteersBidiMap.put(routeNo, object);
                        if (object.getRouteNo().equals(currentlySelectedBus))
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentlatLongs, googleMap.getCameraPosition().zoom));
                    }

                }
                if (dataSnapshot.getChildrenCount() < busVolunteersBidiMap.size() && !busVolunteersBidiMap.isEmpty()) {
                    for (String s : busVolunteersBidiMap.keySet()) {
                        if (!dataSnapshot.child(s).exists()) {
                            busVolunteersBidiMap.get(s).getBusMarker().remove();
                            busVolunteersBidiMap.remove(s);
                            busTrackingAdapter.removeRouteNo(s);
                            tvVolunteerDetails.setText("");
                        }
                    }
                }
                busTrackingAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void addNewBusMarker(String routeNo, String sharerId, LatLng currentlatLongs, int speed, boolean isSharingLoc) {
        Marker busMarker = googleMap.addMarker(new MarkerOptions()
                .position(currentlatLongs)
                .icon(darkModeEnabled ? getBitmapDescriptor(R.drawable.ic_bus_yellow) : getBitmapDescriptor(R.drawable.ic_bus_blue))
                .title("Est. Speed: " + speed + " km/h"));

        busTrackingAdapter.addRouteNo(routeNo, isSharingLoc);

        busVolunteersBidiMap.put(routeNo, new BusObject(routeNo, sharerId, busMarker, speed, new BusObject.OnLocationUpdatedListener() {
            @Override
            public void onSharerIdChanged(String r, String newSharerId) {
                if (currentlySelectedBus == null) currentlySelectedBus = r;
                else if (newSharerId == null) return;
                if (r.equals(currentlySelectedBus) && !newSharerId.equals("null"))
                    tvVolunteerDetails.setText("Current Volunteer" + (newSharerId.equals(SharedPref.getString(getActivity().getApplicationContext(), "email")) ? ": You" : " ID: " + newSharerId));
            }

            @Override
            public void onLocationChanged(String r, LatLng location) {
            }

            @Override
            public void onOnlineStatusChanged(String r, boolean isOnline) {
                busTrackingAdapter.setOnlineStatus(r, isOnline);
                busTrackingAdapter.notifyDataSetChanged();
            }

            @Override
            public void onSpeedChanged(String r, int newSpeed) {
            }
        }));
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
        BusObject object = getBusObjectFromMarker(marker);
        if (busVolunteersBidiMap.size() > 0 && object != null)
            spnSelectBus.setSelection(busTrackingAdapter.getIndexOfRoute(busVolunteersBidiMap.getKey(object)));
        return false;
    }
    public BusObject getBusObjectFromMarker(Marker marker) {
        for (BusObject object : busVolunteersBidiMap.values()) {
            if (object.getBusMarker().equals(marker))
                return object;
        }
        return null;
    }
}