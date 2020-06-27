package in.edu.ssn.testssnapp.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.firebase.ui.firestore.SnapshotParser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import in.edu.ssn.testssnapp.BusRoutesActivity;
import in.edu.ssn.testssnapp.BusTrackingActivityDuplicate;
import in.edu.ssn.testssnapp.NoNetworkActivity;
import in.edu.ssn.testssnapp.PdfViewerActivity;
import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.models.BusPost;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import in.edu.ssn.testssnapp.utils.Constants;
import in.edu.ssn.testssnapp.utils.SharedPref;
import spencerstudios.com.bungeelib.Bungee;

public class BusAlertsFragment extends Fragment {

    CardView busRoutesCV, busTrackingCV;
    RecyclerView alertRV;
    ShimmerFrameLayout shimmer_view;
    FirestoreRecyclerAdapter adapter;
    boolean darkMode = false;
    boolean dayScholar;
    public BusAlertsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        CommonUtils.addScreen(getContext(), getActivity(), "BusAlertsFragment");
        darkMode = SharedPref.getBoolean(getContext(), "dark_mode");
        View view;
        if (darkMode) {
            view = inflater.inflate(R.layout.fragment_bus_alerts_dark, container, false);
        } else {
            view = inflater.inflate(R.layout.fragment_bus_alerts, container, false);
        }
        CommonUtils.initFonts(getContext(), view);
        initUI(view);

        setupFireStore();

        busRoutesCV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getContext(), BusRoutesActivity.class));
                Bungee.slideLeft(getContext());
            }
        });
        busTrackingCV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), BusTrackingActivityDuplicate.class);
                    startActivity(intent);
                }
        });

        return view;
    }

    /*********************************************************/

    void setupFireStore() {
        Query query = FirebaseFirestore.getInstance().collection(Constants.collection_post_bus).orderBy("time", Query.Direction.DESCENDING).limit(5);

        FirestoreRecyclerOptions<BusPost> options = new FirestoreRecyclerOptions.Builder<BusPost>().setQuery(query, new SnapshotParser<BusPost>() {
            @NonNull
            @Override
            public BusPost parseSnapshot(@NonNull DocumentSnapshot snapshot) {
                final BusPost busPost = new BusPost();
                busPost.setTitle(snapshot.getString("title"));
                busPost.setTime(snapshot.getTimestamp("time").toDate());
                busPost.setUrl(snapshot.getString("url"));
                busPost.setDesc(snapshot.getString("desc"));
                return busPost;
            }
        })
                .build();

        adapter = new FirestoreRecyclerAdapter<BusPost, BusAlertHolder>(options) {
            @Override
            public void onBindViewHolder(final BusAlertHolder holder, int position, final BusPost model) {
                holder.dateTV.setText("Bus routes from " + model.getTitle());
                holder.descTV.setText(model.getDesc());
                holder.timeTV.setText(CommonUtils.getTime(model.getTime()));

                holder.rl_bus_alert_item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!CommonUtils.alerter(getContext())) {
                            Intent i = new Intent(getContext(), PdfViewerActivity.class);
                            i.putExtra(Constants.PDF_URL, model.getUrl());
                            startActivity(i);
                            Bungee.fade(getContext());
                        } else {
                            Intent intent = new Intent(getContext(), NoNetworkActivity.class);
                            intent.putExtra("key", "home");
                            startActivity(intent);
                            Bungee.fade(getContext());
                        }
                    }
                });
                shimmer_view.setVisibility(View.GONE);
            }

            @Override
            public BusAlertHolder onCreateViewHolder(ViewGroup group, int i) {
                View view;
                if (darkMode) {
                    view = LayoutInflater.from(group.getContext()).inflate(R.layout.bus_alert_dark, group, false);
                } else {
                    view = LayoutInflater.from(group.getContext()).inflate(R.layout.bus_alert, group, false);
                }

                return new BusAlertHolder(view);
            }
        };

        alertRV.setAdapter(adapter);
    }

    void initUI(View view) {
        busRoutesCV = view.findViewById(R.id.busRoutesCV);
        busTrackingCV = view.findViewById(R.id.busTrackingCV);
        alertRV = view.findViewById(R.id.alertRV);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        alertRV.setLayoutManager(layoutManager);
        dayScholar = SharedPref.getBoolean(getContext(),"isDayScholar");
        if(dayScholar){
            busTrackingCV.setVisibility(View.VISIBLE);
        }else
            busTrackingCV.setVisibility(View.GONE);


        shimmer_view = view.findViewById(R.id.shimmer_view);
        shimmer_view.setVisibility(View.VISIBLE);
    }

    /*********************************************************/

    @Override
    public void onStart() {
        super.onStart();
        adapter.startListening();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (adapter != null)
            adapter.stopListening();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    /*********************************************************/

    public class BusAlertHolder extends RecyclerView.ViewHolder {
        public TextView dateTV, timeTV, descTV;
        LinearLayout rl_bus_alert_item;

        public BusAlertHolder(View itemView) {
            super(itemView);
            dateTV = itemView.findViewById(R.id.dateTV);
            timeTV = itemView.findViewById(R.id.timeTV);
            descTV = itemView.findViewById(R.id.descTV);
            rl_bus_alert_item = itemView.findViewById(R.id.bus_alert_item);
        }
    }
}