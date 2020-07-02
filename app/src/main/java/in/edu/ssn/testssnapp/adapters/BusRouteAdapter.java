package in.edu.ssn.testssnapp.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Objects;

import in.edu.ssn.testssnapp.MapActivity;
import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.models.BusRoute;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import in.edu.ssn.testssnapp.utils.SharedPref;

public class BusRouteAdapter extends RecyclerView.Adapter<BusRouteAdapter.BusRouteViewHolder> {

    boolean darkMode;
    boolean isDayScholar;
    private ArrayList<BusRoute> busRoutes;
    private Context context;

    public BusRouteAdapter(Context context, ArrayList<BusRoute> busRoutes) {
        this.context = context;
        this.busRoutes = busRoutes;
        darkMode = SharedPref.getBoolean(context, "dark_mode");
        isDayScholar = SharedPref.getBoolean(context, "isDayScholar");

    }

    @NonNull
    @Override
    public BusRouteAdapter.BusRouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (darkMode) {
            view = LayoutInflater.from(context).inflate(R.layout.bus_route_item_dark, parent, false);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.bus_route_item, parent, false);
        }
        return new BusRouteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BusRouteAdapter.BusRouteViewHolder holder, int position) {
        BusRoute busRoute = this.busRoutes.get(position);
        String Route = busRoute.getName();
        holder.routeNameTV.setText("Route " + busRoute.getName());
        holder.busStopsRV.setAdapter(new BusStopAdapter(context, busRoute));
        holder.busRouteCV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (CommonUtils.alerter(context)) {
                    Toast.makeText(context, "You're offline! Please connect to the internet to continue!", Toast.LENGTH_SHORT).show();
                } else if (isDayScholar) {
                    DatabaseReference timeRef = FirebaseDatabase.getInstance().getReference("Bus Locations");
                    Toast.makeText(context, "Attempting to fetch internet time, please wait...", Toast.LENGTH_LONG).show();
                    timeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            new CommonUtils.getInternetTime(context, (trueTime) -> {
                                /*Long startTime = snapshot.child("startTime").getValue(Long.class);
                                Long endTime = snapshot.child("endTime").getValue(Long.class);*/
                                if (trueTime == 0) {
                                    Toast.makeText(context, "There was an error fetching the internet time! Access denied!", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                String s = new SimpleDateFormat("u hh:mm:ss").format(trueTime);

                                Boolean masterEnable = snapshot.child("masterEnable").getValue(Boolean.class);
                                if (masterEnable == null || !masterEnable) {// || startTime == null || endTime == null) {
                                    Toast.makeText(context, "Cannot start the bus tracking feature! Master switch is off!", Toast.LENGTH_LONG).show();
                                    return;
                                } else if (Objects.equals(s.charAt(0), '7')) {
                                    Toast.makeText(context, "Cannot start bus tracking feature on Sundays!", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                /*else if (currentTime < startTime) {
                                    Toast.makeText(context, "Cannot use the bus tracking feature until " + SimpleDateFormat.getTimeInstance().format(startTime) + "!", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                else if (currentTime > endTime) {
                                    Toast.makeText(context, "Cannot use the bus tracking feature beyond " + SimpleDateFormat.getTimeInstance().format(endTime) + "!", Toast.LENGTH_LONG).show();
                                    return;
                                } */
                                Toast.makeText(context, "Access granted!", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(context, MapActivity.class);
                                intent.putExtra("routeNo", Route);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);
                            }).execute();

                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {

                        }
                    });
                }
                /*else
                    Toast.makeText(context,"Tracking feature is not available for Hostellers",Toast.LENGTH_LONG).show();*/
            }
        });
    }

    @Override
    public int getItemCount() {
        return busRoutes.size();
    }

    public class BusRouteViewHolder extends RecyclerView.ViewHolder {
        public TextView routeNameTV;
        public RecyclerView busStopsRV;
        public CardView busRouteCV;

        public BusRouteViewHolder(View convertView) {
            super(convertView);

            routeNameTV = convertView.findViewById(R.id.routeNameTV);
            busStopsRV = convertView.findViewById(R.id.busStopsRV);
            busRouteCV = convertView.findViewById(R.id.busRouteCV);
            LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
            busStopsRV.setHasFixedSize(true);
            busStopsRV.setLayoutManager(layoutManager);
        }
    }
}
