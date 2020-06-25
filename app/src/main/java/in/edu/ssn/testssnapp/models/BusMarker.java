package in.edu.ssn.testssnapp.models;

import android.content.Context;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.utils.LatLongPair;

public class BusMarker extends Marker {
    LatLongPair<Double> location;

    public LatLongPair<Double> getLocation() {
        return location;
    }

    public BusMarker(Context c, LatLongPair<Double> location, MapView mapView) {
        super(mapView);
        this.location = location;
        setPosition(new GeoPoint(location.getLat(), location.getLng()));
        setIcon(c.getResources().getDrawable(R.drawable.bus_mapicon));
    }
    public boolean locationEquals(LatLongPair<Double> latLong) {
        return getPosition().equals(new GeoPoint(latLong.getLat(), latLong.getLng()));
    }
}
