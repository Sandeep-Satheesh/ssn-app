package in.edu.ssn.testssnapp.BGTasks;

import android.os.AsyncTask;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.util.ArrayList;

import in.edu.ssn.testssnapp.models.BusRoute;

public class GetBusRouteDetailsAsyncTask extends AsyncTask<Void, Void, Void> {
    //FileInputStream outFile;
    BufferedReader reader;
    ArrayList<BusRoute> routes;
    private OnBusRoutesCompletelyFetchedListener listener;

    public GetBusRouteDetailsAsyncTask(BufferedReader reader) {
        this.reader = reader;
        listener = null;
    }

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected Void doInBackground(Void... args) {
        // do background work here
        routes = new ArrayList<>(1);

        //Finding the lat-long's of all the stops from json (through geocoding earlier)...
        //Check if successfully generated file already exists. If yes, no need to proceed further.

        try {
            //need to find lat-longs.
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }

            JSONArray arr = new JSONArray(sb.toString());

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = (JSONObject) arr.get(i);
                BusRoute bus = new Gson().fromJson(obj.toString(), BusRoute.class);
                routes.add(bus);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null; //no need to find lat-longs.
        }
        // This is the code to return lat-long list using geocoder API, which was used to generate data in data_bus.json
        /*Geocoder geocoder = new Geocoder(main);
        double clgLat = 0, clgLong = 0;
        try {
            Address a = geocoder.getFromLocationName("SSN College of Engineering", 1).get(0);
            clgLat = a.getLatitude();
            clgLong = a.getLongitude();
        } catch (IOException e) {
            e.printStackTrace();
        }
        int i = 0;
        for (ArrayList<String> stopsPerRoute : main.stops) {
            final ArrayList<Double> latsInRoute = new ArrayList<>(1);
            ArrayList<Double> longsInRoute = new ArrayList<>(1);
            for (final String s : stopsPerRoute) {
                if (s.equals("College")) {
                    latsInRoute.add(clgLat);
                    longsInRoute.add(clgLong);
                    continue;
                }
                try {
                    ArrayList<Address> a = ((ArrayList<Address>) geocoder.getFromLocationName(s, 1));
                    i++;
                    final int finalI = i;
                    main.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog.setProgress((int) (((float)(finalI /main.stops.size()))*100));
                        }
                    });
                    if (a.size() == 0) {
                        main.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(main.getApplicationContext(), "Unable to find location '" + s + "'!", Toast.LENGTH_SHORT).show();
                            }
                        });
                        latsInRoute.add(-1010101d);
                        longsInRoute.add(-1010101d);
                        continue;
                    }
                    latsInRoute.add(a.get(0).getLatitude());
                    longsInRoute.add(a.get(0).getLongitude());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            i = 0;
            main.lats.add(latsInRoute);
            main.longs.add(longsInRoute);
        } */
        if (listener != null)
            listener.onBusRoutesCompletelyRead(routes);
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
    }
    public interface OnBusRoutesCompletelyFetchedListener {
        void onBusRoutesCompletelyRead(ArrayList<BusRoute> busRoutes);
    }
}