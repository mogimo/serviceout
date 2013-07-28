package jp.gr.java_conf.mogimo.oos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends ListActivity 
        implements OnItemClickListener, View.OnClickListener, 
        BlockingControler, OnSetResultMap {

    private PackageManager mPM = null;
    private View mProgressBar;
    private ImageButton mReloadButton, mSettingButton;
    private EventLogTask mTask = null;
    private ListView mListView = null;
    private ArrayList<Map.Entry<String, Integer>> mPackages;
    private SettingUtils mSetting;
    private ArrayList<String> mPackageNames = new ArrayList<String>();
    private ArrayList<String> mLaunchableApps = new ArrayList<String>();

    private final int DIALOG_TYPE_SETTING = 1;

    private final int MODE_NORMAL = 0;
    private final int MODE_ADVANCED = 1;
    private int mMode = MODE_NORMAL;
    private int mModeTmp = MODE_NORMAL;

    private boolean mWarning;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mProgressBar = findViewById(R.id.progressbar);
        mReloadButton = (ImageButton)findViewById(R.id.reload_button);
        mReloadButton.setOnClickListener(this);
        mReloadButton.setVisibility(View.INVISIBLE);
        mSettingButton = (ImageButton)findViewById(R.id.setting_button);
        mSettingButton.setOnClickListener(this);
        mSettingButton.setVisibility(View.INVISIBLE);
             
        mPM = getPackageManager();
        if (mListView == null) {
            mListView = getListView();
        }
   
        mSetting = new SettingUtils(this);
        mMode = mSetting.restoreIntSetting(SettingUtils.MODE, MODE_NORMAL);
        mWarning = mSetting.restoreBooleanSetting(SettingUtils.WARNING, true);
   
        if (mWarning) showWarning();
        executeTask();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        for (int i = 0; i < mPackageNames.size() ; i++) {
            mListView.getAdapter().isEnabled(i);
        }
    }

    private boolean isExist(String app) {
        return getApplicationInfo(app) != null;
    }

    private void showWarning() {
        Intent intent = new Intent(this, WarningMessage.class);
        startActivity(intent);
    }
    
    @Override
    public void startBlocking() {
        mProgressBar.startAnimation(AnimationUtils.loadAnimation(
                this, android.R.anim.fade_in));
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void stopBlocking() {
        mProgressBar.startAnimation(AnimationUtils.loadAnimation(
                this, android.R.anim.fade_out));
        mProgressBar.setVisibility(View.GONE);
        mReloadButton.setVisibility(View.VISIBLE);
        mSettingButton.setVisibility(View.VISIBLE);
    }
    
    private void executeTask() {
        if (mTask != null) {
            mTask = null;
        }
        if (mPackages != null) {
            mPackages.clear();
        }
        if (mPackageNames != null) {
            mPackageNames.clear();
        }
        mTask = new EventLogTask(this);
        mTask.execute();
    }
    
    private PackageInfo getPackageInfo(String packageName) {
        PackageInfo info = null;
        try {
            info = mPM.getPackageInfo(packageName,
                    PackageManager.GET_DISABLED_COMPONENTS |
                    PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (NameNotFoundException e) {
            if (Debug.DEBUG) e.printStackTrace();
        }
        return info;
    }
    
    private ApplicationInfo getApplicationInfo(String packageName) {
        PackageInfo info = getPackageInfo(packageName);
        return info != null ? info.applicationInfo : null;
    }
    
    private String getApplicationName(String packageName) {
        ApplicationInfo info = getApplicationInfo(packageName);
        String str;
        if (info != null) {
            str = (String)mPM.getApplicationLabel(info);
        } else {
            str = getString(R.string.unknown);
        }
        return str;
    }

    private Drawable getApplicationIcon(String packageName) {
        ApplicationInfo info = getApplicationInfo(packageName);
        Drawable icon;
        if (info != null) {
            icon = mPM.getApplicationIcon(info);
        } else {
            icon = getResources().getDrawable(R.drawable.ic_launcher);
        }
        return icon;
    }
    
    private boolean isPersistentApplication(String packageName) {
        ApplicationInfo info = getApplicationInfo(packageName);
        if (info == null) {
            return false;
        }
        return ((info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0);
    }

    private boolean isStopped(String packageName) {
        ApplicationInfo info = getApplicationInfo(packageName);
        if (info == null) {
            return false;
        }
        return ((info.flags & ApplicationInfo.FLAG_STOPPED) != 0);
    }

    private void getLaunchableApps() {
        String pkgName;
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = mPM.queryIntentActivities(intent, 0);
        for (ResolveInfo info : apps) {
            pkgName = info.activityInfo.packageName;
            mLaunchableApps.add(pkgName);
            Debug.log("launchable app=" + pkgName);
        }
    }
    
    private boolean isVerySmall(int score) {
        // less than 1 min.
        return (score < 60000);
    }

    private void useOnlyLaunchableApps(ArrayList<Map.Entry<String, Integer>> result) {
        if (mLaunchableApps.size() == 0) {
            // it's first time!
            getLaunchableApps();
            mPackages = new ArrayList<Map.Entry<String, Integer>>();
        }
        String pkgName;
        for (Map.Entry<String, Integer>entry : result) {
            pkgName = entry.getKey();
            if (mLaunchableApps.contains(pkgName) &&
                    !isPersistentApplication(pkgName) && 
                    isExist(pkgName) && !isStopped(pkgName) &&
                    !isVerySmall(entry.getValue().intValue())) {
                mPackages.add(entry);
                mPackageNames.add(pkgName);
            }
        }
    }
    
    private void useAllApps(ArrayList<Map.Entry<String, Integer>> result) {
        mPackages = result;
        for (Map.Entry<String, Integer>entry : result) {
            mPackageNames.add(entry.getKey());
        }        
    }
    
    @Override
    public void onSetResultMap(ArrayList<Map.Entry<String, Integer>> result) {
        if (mMode == MODE_ADVANCED) {
            useAllApps(result);
        } else {
            useOnlyLaunchableApps(result);
        }
        
        final ArrayAdapter<Map.Entry<String, Integer>> adapter =
            new ArrayAdapter<Map.Entry<String, Integer>>(this,
                    R.layout.two_line_list_with_icon, mPackages) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final LayoutInflater inflater = LayoutInflater.from(getContext());
                final View resultView = convertView == null ?
                        inflater.inflate(R.layout.two_line_list_with_icon, 
                                parent, false) : convertView;
                final TextView package_name = (TextView)resultView.findViewById(R.id.package_name);
                final TextView score = (TextView)resultView.findViewById(R.id.score);
                final ImageView icon = (ImageView)resultView.findViewById(R.id.app_icon);

                final Map.Entry<String, Integer> application = getItem(position);
                String packageName = application.getKey();
                Debug.log("position="+position+" package="+packageName);

                package_name.setText(getApplicationName(packageName));
                // score = total activated seconds
                Integer totalTime = application.getValue()/1000;
                if (mMode == MODE_ADVANCED) {
                    score.setText(totalTime.toString() + ": " + packageName);
                } else {
                    score.setText(totalTime.toString());
                }
                icon.setImageDrawable(getApplicationIcon(application.getKey()));
                return resultView;
            }
            
            @Override
            public boolean isEnabled(int position) {
                return isExist(mPackageNames.get(position));
            }
        };
        
        setListAdapter(adapter);
        mListView.setItemsCanFocus(false);
        mListView.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?>parent, View view, int position, long id) {
        String packageName = mPackageNames.get(position);
        Debug.log("position="+position+" package="+packageName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            Uri data = Uri.parse("package:" + packageName);
            intent.setData(data);
            startActivity(intent);
        } else {
            String extra;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                extra = "pkg";
            } else {
                extra = "com.android.settings.ApplicationPkgName";
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setComponent(
                    new ComponentName("com.android.settings",
                            "com.android.settings.InstalledAppDetails"));
            intent.putExtra(extra, packageName);
            startActivity(intent);
        }
    }

    private DialogInterface.OnClickListener selectItemListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            /* User clicked on a radio button do some stuff */
            Debug.log("select = " + whichButton);
            mModeTmp = whichButton;
        }
    };
    
    private DialogInterface.OnClickListener okClickLister = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            /* User clicked Yes so do some stuff */
            Debug.log("ok = " + whichButton);
            mMode = mModeTmp;
            mSetting.saveIntSetting(SettingUtils.MODE, mMode);
            executeTask();
        }
    };

    private DialogInterface.OnClickListener cancelClickLister = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            /* User clicked No so do some stuff */
            Debug.log("cancel = " + whichButton);
        }
    };

    @Override
    protected Dialog onCreateDialog(int id) {
        Debug.log("mode = " + mMode);
        return new AlertDialog.Builder(this)
        //.setIcon(R.drawable.alert_dialog_icon)
        .setTitle(R.string.choice)
        .setSingleChoiceItems(R.array.select_dialog_items, mMode, selectItemListener)
        .setPositiveButton(R.string.btn_ok, okClickLister)
        .setNegativeButton(R.string.btn_cancel, cancelClickLister)
        .create();
    }
    
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.reload_button) {
            executeTask();
        } else if (view.getId() == R.id.setting_button) {
            removeDialog(DIALOG_TYPE_SETTING);
            showDialog(DIALOG_TYPE_SETTING, null);
        }
        return;
    }
}