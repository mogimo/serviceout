package jp.gr.java_conf.mogimo.oos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.text.format.DateFormat;
import android.util.EventLog;
import android.util.EventLog.Event;

public class EventLogTask extends AsyncTask<Void, String, ArrayList<Map.Entry<String, Integer>>> {
    // maybe host activity
    private BlockingControler mContext = null;
    private StringBuilder mLog = new StringBuilder();

    // EventLog entries that we want to watch
    private final static int CONNECTIVITY_STATE_INDEX = 0;
    private final static int CREATE_SERVICE_INDEX = 1;
    private final static int DESTROY_SERVICE_INDEX = 2;    
    private final static String[] sReadTags = {
        "connectivity_state_changed",
        "am_create_service",
        "am_destroy_service"};

    // statistics service/package list with working-time
    // (created service, start time-stamp (nsec))
    private HashMap<String, Long> mCreatedServices = new HashMap<String, Long>();
    // (destroyed service, working time (msec))
    private HashMap<String, Integer> mActivatedServices = new HashMap<String, Integer>();
    // (activated package, working time (msec))
    private HashMap<String, Integer> mActivatedProcesses = new HashMap<String, Integer>();
    
    EventLogTask(BlockingControler context) {
        mContext = context;
    }
    
    @Override
    protected void onPreExecute() {
        // show progress bar
        mContext.startBlocking();
    }

    public String getConnectivityLog() {
        return mLog.toString();
    }
    
    // read events only for interested tags
    private List<Event> readEvents() {
        int n = sReadTags.length;
        int ids[] = new int[n];
        for (int i=0; i<n; i++) {
            ids[i] = EventLog.getTagCode(sReadTags[i]);
        }
        
        List<Event> events = new ArrayList<Event>();
        try {
            EventLog.readEvents(ids, events);
        } catch (IOException e) {
            e.printStackTrace();
            events = null;
        }
        return events;
    }

    private void dispatchEvent(Event event) {
        int id = event.getTag();
        String tagName = EventLog.getTagName(id);
        
        if (sReadTags[CONNECTIVITY_STATE_INDEX].equals(tagName)) {
            // connectivity change
            mLog.append(DateFormat.format("[yyyy/MM/dd kk:mm:ss]\n",
                    event.getTimeNanos()/1000000L));
            mLog.append("  " + connectivityChanged(event));
            Debug.log(mLog.toString());
        } else if (sReadTags[CREATE_SERVICE_INDEX].equals(tagName)) {
            // create service
            makeCreatedServicesMap(event);
        } else if (sReadTags[DESTROY_SERVICE_INDEX].equals(tagName)) {
            // destroy service
            makeActivatedServicesMap(event);
        }
    }
    
    private final static int NETWORK_TYPE_MASK = 0x3;
    private final static int DETAILED_STATE_MASK = 0x3f;
    private final static int NETWORK_SUBTYPE_MASK = 0x0f;
    
    // just logging connectivity changed
    private String connectivityChanged(Event event) {
        Object data = event.getData();
        if (data instanceof Integer) {
            StringBuilder builder = new StringBuilder();
            int value = ((Integer)data).intValue();
            int type = value & NETWORK_TYPE_MASK;
            switch (type) {
                case ConnectivityManager.TYPE_MOBILE:
                    builder.append("Mobile:");
                    break;
                case ConnectivityManager.TYPE_MOBILE_DUN:
                    builder.append("Mobile(DUN):");
                    break;
                case ConnectivityManager.TYPE_MOBILE_HIPRI:
                    builder.append("Mobile(HIPRI):");
                    break;
                case ConnectivityManager.TYPE_MOBILE_MMS:
                    builder.append("Mobile(MMS):");
                    break;
                case ConnectivityManager.TYPE_MOBILE_SUPL:
                    builder.append("Mobile(SUPL):");
                    break;
                case ConnectivityManager.TYPE_WIFI:
                    builder.append("WIFI:");
                    break;
                case ConnectivityManager.TYPE_WIMAX:
                    builder.append("WIMAX:");
                    break;
            }

            int state = (value >> 3) & DETAILED_STATE_MASK;
            if (state == NetworkInfo.DetailedState.AUTHENTICATING.ordinal()) {
                builder.append(" state=AUTHENTICATING");
            } else if (state == NetworkInfo.DetailedState.CONNECTED.ordinal()) {
                builder.append(" state=CONNECTED");
            } else if (state == NetworkInfo.DetailedState.CONNECTING.ordinal()) {
                builder.append(" state=CONNECTING");
            } else if (state == NetworkInfo.DetailedState.DISCONNECTED.ordinal()) {
                builder.append(" state=DISCONNECTED");
            } else if (state == NetworkInfo.DetailedState.DISCONNECTING.ordinal()) {
                builder.append(" state=DISCONNECTING");
            } else if (state == NetworkInfo.DetailedState.FAILED.ordinal()) {
                builder.append(" state=FAILED");
            }
            
            int subType = (value >> 9) & NETWORK_SUBTYPE_MASK;
            switch (subType) {
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    builder.append(", sub type=1xRTT");
                    break;
                case TelephonyManager.NETWORK_TYPE_CDMA:
                    builder.append(", sub type=CDMA");
                    break;
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    builder.append(", sub type=EDGE");
                    break;
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    builder.append(", sub type=EVDO 0");
                    break;
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    builder.append(", sub type=EVDO A");
                    break;
                case TelephonyManager.NETWORK_TYPE_GPRS:
                    builder.append(", sub type=GPRS");
                    break;
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                    builder.append(", sub type=HSDPA");
                    break;
                case TelephonyManager.NETWORK_TYPE_HSPA:
                    builder.append(", sub type=HSPA");
                    break;
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    builder.append(", sub type=IDEN");
                    break;
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    builder.append(", sub type=UMTS");
                    break;
            }            
            builder.append("\n");
            return builder.toString();
        }
        return null;
    }

    private long mFirstEventTime = -1;  //nsec
    
    // generate created services list
    private void makeCreatedServicesMap(Event event) {
        Object data = event.getData();
        if (mFirstEventTime == -1) {
            mFirstEventTime = event.getTimeNanos();
        }
        if (data instanceof Object[]) {
            Object[] array = (Object[])data;
            // data = {hash code, service name, intent, pid}
            if (array.length == 4) {
                // retrieve a service name
                String name = (String)array[1];
                if (!name.equals("")) {
                    // put (name, start-time) into map if name is not empty!
                    mCreatedServices.put(name, event.getTimeNanos());
                    Debug.log("create event = " + name);
                }
            }
        }
    }
    
    // update created services list after reading all event logs
    // i.e. pick up and calculate activate time of created but not destroyed services
    private void updateCreatedServicesMap() {
        Debug.log("All event search done!");
        Iterator<String> remains = mCreatedServices.keySet().iterator();
        while (remains.hasNext()) {
            String name = remains.next();
            long now = System.currentTimeMillis();
            // round up nanosecond to millisecond
            long startTime = (long)(mCreatedServices.get(name)/1000000L);
            int update = (int)(now - startTime);
            Debug.log("created but not destroyed = " + name + " (" + update + ")");
            updateActivatedTime(name, update);
        }
    }

    // update activated services time (msec)
    private void updateActivatedTime(String name, int time) {
        if (mActivatedServices.containsKey(name)) {
            int old = mActivatedServices.get(name);
            mActivatedServices.put(name, old + time);
            Debug.log("destroy evnet (update) = " + name + " (" + (old+time) + ")");
        } else {
            mActivatedServices.put(name, time);
            Debug.log("destroy evnet = " + name + " (" + time + ")");
        }
    }
    
    // create activated service list
    private void makeActivatedServicesMap(Event event) {
        Object data = event.getData();
        if (data instanceof Object[]) {
            Object[] array = (Object[])data;
            if (array.length == 3) {
                String name = (String)array[1];
                if (!name.equals("") && mCreatedServices.containsKey(name)) {
                    Long startTime = mCreatedServices.get(name);
                    mCreatedServices.remove(name);
                    long stopTime = event.getTimeNanos();
                    int activeTime = (int)((stopTime - startTime)/1000000L); //msec
                    updateActivatedTime(name, activeTime);
                } else {
                    // there is only destroy event (no create event)
                    if (!name.equals("")) {
                        long stopTime = event.getTimeNanos();
                        int activeTime = (int)((stopTime - mFirstEventTime)/1000000L); //msec
                        updateActivatedTime(name, activeTime);
                    }
                }
            }
        }
    }

    // create activated package list
    private void makeActivatedPackagesMap() {
        Iterator<String> services = mActivatedServices.keySet().iterator();
        while (services.hasNext()) {
            String service = services.next();
            String process = getPackageName(service);
            updatePackageTime(process, mActivatedServices.get(service));
        }
    }
    
    // update activated time for each packages
    private void updatePackageTime(String name, int time) {
        if (mActivatedProcesses.containsKey(name)) {
            int old = mActivatedProcesses.get(name);
            mActivatedProcesses.put(name, old + time);
        } else {
            mActivatedProcesses.put(name, time);
        }
    }

    // a complete service name is the form of "package/.service"
    // this method retrieve a package name from the service name.
    private String getPackageName(String serviceName) {
        StringTokenizer tokenizer = new StringTokenizer(serviceName, "/");
        return tokenizer != null ? tokenizer.nextToken() : null;
    }

    // sort by value with a descending order
    private ArrayList<Map.Entry<String, Integer>> sortMapByValue(Map<String, Integer> map) {
        ArrayList<Map.Entry<String, Integer>> entries = new ArrayList(map.entrySet());
        Collections.sort(entries, new Comparator() {
            @Override
            public int compare(Object obj1, Object obj2) {
                Map.Entry<String, Integer> entry1 = (Map.Entry<String, Integer>)obj1;
                Map.Entry<String, Integer> entry2 = (Map.Entry<String, Integer>)obj2;
                // big to small
                return (entry1.getValue().compareTo(entry2.getValue()) * -1);
            }
        });
        return entries;
    }
    
    // generate string for all key/value pair
    private String generateList(Iterator<Map.Entry<String, Integer>> iterator) {
        StringBuilder builder = new StringBuilder();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = (Entry<String, Integer>) iterator.next();
            builder.append("key=" + entry.getKey() + ", ");
            builder.append("value=" + entry.getValue() + "\n");
        }
        return builder.toString();
    }
    
    @Override
    protected ArrayList<Map.Entry<String, Integer>> doInBackground(Void... params) {
        // get specified events
        List<Event> events = readEvents();
        if (events != null) {
            for (Event event : events) {
                // retrieve data for each event type
                dispatchEvent(event);
            }
            // clear up for all working services
            updateCreatedServicesMap();
            
            if (Debug.DEBUG) {
                Debug.log("all services");
                Debug.log(generateList(mActivatedServices.entrySet().iterator()));
            }
            
            // tidy up for each packages
            makeActivatedPackagesMap();
            
            if (Debug.DEBUG) {
                Debug.log("all packages");
                Debug.log(generateList(mActivatedProcesses.entrySet().iterator()));
            }
            // sort big value to small
            ArrayList<Map.Entry<String, Integer>> processes =
                sortMapByValue(mActivatedProcesses);

            return processes;
        }
        return null;
    }
    
    @Override
    protected void onProgressUpdate(String... progress) {
    }

    @Override
    protected void onPostExecute(ArrayList<Map.Entry<String, Integer>> result) {
        mContext.stopBlocking();
        
        if (mContext instanceof OnSetResultMap) {
            ((OnSetResultMap) mContext).onSetResultMap(result);
        }
        
        mCreatedServices.clear();
        mActivatedServices.clear();
        mActivatedProcesses.clear();
    }

}
