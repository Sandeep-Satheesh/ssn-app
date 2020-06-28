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

import java.util.ArrayList;

import in.edu.ssn.testssnapp.BusTrackingActivityDuplicate;
import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.models.BusRoute;
import in.edu.ssn.testssnapp.utils.SharedPref;

public class BusRouteAdapter extends RecyclerView.Adapter<BusRouteAdapter.BusRouteViewHolder> {

    boolean darkMode = false;
    private ArrayList<BusRoute> busRoutes;
    private Context context;

    public BusRouteAdapter(Context context, ArrayList<BusRoute> busRoutes) {
        this.context = context;
        this.busRoutes = busRoutes;
        darkMode = SharedPref.getBoolean(context, "dark_mode");
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
                Toast.makeText(context, Route,Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(context, BusTrackingActivityDuplicate.class);
                intent.putExtra("routeNo", Route);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);

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
