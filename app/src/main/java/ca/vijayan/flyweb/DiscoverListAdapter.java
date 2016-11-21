package ca.vijayan.flyweb;

import android.app.Activity;
import android.content.Context;
import android.database.DataSetObserver;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.vijayan.flyweb.mdns.DNSServiceInfo;

/**
 * Created by kvijayan on 04/11/16.
 */

public class DiscoverListAdapter implements ListAdapter {
    Activity mContext;
    List<DNSServiceInfo.Key> mServiceList = new ArrayList<DNSServiceInfo.Key>();
    Set<DataSetObserver> mObservers = new HashSet<DataSetObserver>();

    DiscoverListAdapter(Activity context) {
        mContext = context;
    }

    void addServiceInfo(DNSServiceInfo info) {
        if (mServiceList.contains(info.getKey())) {
            return;
        }
        mServiceList.add(info.getKey());
        notifyObservers();
    }

    void removeServiceInfo(DNSServiceInfo info) {
        if (!mServiceList.contains(info.getKey())) {
            return;
        }
        mServiceList.remove(info.getKey());
        notifyObservers();
    }

    void updateServiceInfo(DNSServiceInfo info, DNSServiceInfo newInfo) {
        int index = mServiceList.indexOf(info.getKey());
        if (index < 0) {
            return;
        }
        mServiceList.set(index, info.getKey());
        notifyObservers();
    }

    private void notifyObservers() {
        for (DataSetObserver obs : mObservers) {
            obs.onChanged();
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int i) {
        return true;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver dataSetObserver) {
        mObservers.add(dataSetObserver);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {
        mObservers.remove(dataSetObserver);
    }

    @Override
    public int getCount() {
        return mServiceList.size();
    }

    @Override
    public Object getItem(int i) {
        return mServiceList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        LayoutInflater inflater =
                (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup itemView;
        if (view == null) {
            itemView = (ViewGroup) inflater.inflate(R.layout.discover_list_item, null, true);
        } else {
            itemView = (ViewGroup) view;
        }
        LinearLayout paneView = (LinearLayout) itemView.findViewById(R.id.discover_list_item);
        TextView nameView = (TextView) itemView.findViewById(R.id.discover_list_item_name);
        TextView descrView = (TextView) itemView.findViewById(R.id.discover_list_item_description);

        DNSServiceInfo serviceInfo = mServiceList.get(i).getServiceInfo();
        Map<String, byte[]> attrs = serviceInfo.getAttributes();

        String serviceName = serviceInfo.displayName();
        String serviceDescr = null;
        if (attrs.containsKey("descr")) {
            try {
                serviceDescr = new String(attrs.get("descr"), "UTF-8");
            } catch (UnsupportedEncodingException exc) {
                // Ignore serviceDescr.
            }
        }

        if (serviceDescr == null) {
            serviceDescr = "";
        }
        nameView.setText(serviceName);
        descrView.setText(serviceDescr);

        paneView.setTag(serviceInfo);

        return itemView;
    }

    @Override
    public int getItemViewType(int i) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return mServiceList.isEmpty();
    }
}
