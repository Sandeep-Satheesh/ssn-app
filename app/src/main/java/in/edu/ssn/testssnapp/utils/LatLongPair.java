package in.edu.ssn.testssnapp.utils;

public class LatLongPair<X> {

    private final X lat;
    private final X lng;

    public LatLongPair(X lat, X lng) {
        assert lat != null;
        assert lng != null;

        this.lat = lat;
        this.lng = lng;
    }

    public X getLat() { return lat; }
    public X getLng() { return lng; }

    @Override
    public int hashCode() { return lat.hashCode() ^ lng.hashCode(); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LatLongPair)) return false;
        LatLongPair pairo = (LatLongPair) o;
        return this.lat.equals(pairo.getLat()) &&
                this.lng.equals(pairo.getLng());
    }

}
