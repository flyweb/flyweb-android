package ca.vijayan.flyweb;

import android.app.Activity;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.vijayan.flyweb.mdns.DNSPacket;
import ca.vijayan.flyweb.mdns.DNSServiceInfo;

/**
 * Created by kvijayan on 04/11/16.
 */

public class DiscoverListAdapter implements ListAdapter {
    Activity mContext;
    ArrayList<DNSServiceInfo.Key> mServiceList = new ArrayList<DNSServiceInfo.Key>();
    Map<DNSServiceInfo, Bitmap> mIconMap = new HashMap<>();
    Set<DataSetObserver> mObservers = new HashSet<DataSetObserver>();

    public static String logName() {
        return "DiscoverListAdapter";
    }

    DiscoverListAdapter(Activity context) {
        mContext = context;
    }

    void addServiceInfo(DNSServiceInfo info) {
        if (mServiceList.contains(info.getKey())) {
            return;
        }
        if (info.getType().equals(DNSPacket.FLYWEB_SERVICE_TYPE)) {
            // Sort _flyweb._tcp services first.
            boolean added = false;
            for (int i = 0; i < mServiceList.size(); i++) {
                DNSServiceInfo iInfo = mServiceList.get(i).getServiceInfo();
                if (iInfo.getType().equals(DNSPacket.HTTP_SERVICE_TYPE)) {
                    mServiceList.add(i, info.getKey());
                    added = true;
                    break;
                }
            }
            if (!added) {
                mServiceList.add(info.getKey());
            }
        } else {
            mServiceList.add(info.getKey());
        }
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
        ImageView iconView = (ImageView) itemView.findViewById(R.id.discover_list_item_icon);
        TextView nameView = (TextView) itemView.findViewById(R.id.discover_list_item_name);

        DNSServiceInfo serviceInfo = mServiceList.get(i).getServiceInfo();
        String serviceName = serviceInfo.displayName();
        Bitmap iconBitmap = mIconMap.get(serviceInfo);
        if (iconBitmap != null) {
            // We have found an icon for this service.
            iconView.setImageBitmap(iconBitmap);
        } else if (serviceInfo.getType().equals(DNSPacket.FLYWEB_SERVICE_TYPE)) {
            // Haven't found an icon, and it's a flyweb service.
            // If it has an 'icon' attribute, try to look it up.
            Map<String, byte[]> attrs = serviceInfo.getAttributes();
            if (attrs.containsKey("icon")) {
                try {
                    String iconPath = new String(attrs.get("icon"), "UTF-8");
                    URL iconUrl = new URL(serviceInfo.getBaseURL() + "/" + iconPath);
                    retreiveAndSetIcon(serviceInfo, iconUrl);
                } catch (Exception exc) {
                    // Ignore serviceDescr.
                    Log.e(logName(), "Exception getting icon URL.", exc);
                }
            }
        }

        if (iconBitmap == null) {
            iconView.setImageResource(R.drawable.blank_64x64);
        }

        nameView.setText(serviceName);
        paneView.setTag(serviceInfo);

        return itemView;
    }

    private void retreiveAndSetIcon(final DNSServiceInfo serviceInfo, final URL iconUrl) {
        final Handler setIconHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                Bitmap bmp = (Bitmap) message.obj;
                mIconMap.put(serviceInfo, bmp);
                notifyObservers();
                return true;
            }
        });

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bmp = BitmapFactory.decodeStream(iconUrl.openConnection().getInputStream());
                    Message m = setIconHandler.obtainMessage(0, bmp);
                    m.sendToTarget();
                } catch (Exception exc) {
                    Log.e(logName(), "Exception getting icon", exc);
                }
            }
        });
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
