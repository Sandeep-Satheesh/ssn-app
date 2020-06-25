package in.edu.ssn.testssnapp.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;

import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.utils.SharedPref;

public class BusTrackingAdapter extends BaseAdapter {
    Context context;
    ArrayList<MutablePair<String, Boolean>> routesList;
    boolean darkMode;
    LayoutInflater inflater;
    ImageView busOnlineIV;

    public BusTrackingAdapter(Context c, String[] routeNos) {
        this.context = c;
        this.routesList = new ArrayList<>();
        for (String s : routeNos)
            this.routesList.add(MutablePair.of(s, false));
        inflater = (LayoutInflater.from(c));
        darkMode = SharedPref.getBoolean(c, "dark_mode");
    }

    public int getIndexOfRoute(String routeNo) {
        for (int i = 0; i < routesList.size(); i++)
            if (routesList.get(i).getLeft().equals(routeNo))
                return i;

        return routesList.size();
    }
    public void removeRouteNo(String routeNo) {
        routesList.remove(getIndexOfRoute(routeNo));
    }

    public void addRouteNo(String routeNo, boolean isSharingLoc) {
        for (Pair<String, Boolean> s : routesList)
            if (s.getLeft().equals(routeNo)) return;
        routesList.add(MutablePair.of(routeNo, isSharingLoc));
        Collections.sort(routesList, (o1, o2) -> routeIndex(o1.left) - routeIndex(o2.left));
    }

    private int routeIndex(String s) {
        if (s.equals("9A"))
            return 10;
        else if (s.equals("30A"))
            return 32;
        else {
            int i = Integer.parseInt(s);
            if (i <= 9) return i;
            if (i <= 30) return i+1;
            else return i+2;
        }
    }

    @Override
    public int getCount() {
        return routesList.size();
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    public void setOnlineStatus(String routeNo, boolean isOnline) {
        for (int i = 0; i < routesList.size(); i++)
            if (routesList.get(i).getLeft().equals(routeNo))
                routesList.get(i).setValue(isOnline);
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        view = darkMode ? inflater.inflate(R.layout.bustracking_busitem_dark, null) : inflater.inflate(R.layout.bustracking_busitem, null);
        TextView names = view.findViewById(R.id.tv_busnumber);
        busOnlineIV = view.findViewById(R.id.iv_busOnline);
        busOnlineIV.setImageDrawable(context.getResources().getDrawable(routesList.get(i).getRight() ? R.drawable.ic_bus_online : R.drawable.ic_bus_offline));
        names.setText("Bus No: " + routesList.get(i).getLeft());
        return view;
    }
}