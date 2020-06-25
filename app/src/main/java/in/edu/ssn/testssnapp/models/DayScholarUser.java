package in.edu.ssn.testssnapp.models;

public class DayScholarUser {
    public String id, email;
    public boolean sharingLoc;
    public double lat, lng;
    public float speed = 0;
    public DayScholarUser(String id, String email) {
        this.id = id;
        sharingLoc = false;
        this.email = email;
        lat = lng = 0;
        speed = 0;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isSharingLoc() {
        return sharingLoc;
    }

    public void setSharingLoc(boolean sharingLoc) {
        this.sharingLoc = sharingLoc;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }
}
