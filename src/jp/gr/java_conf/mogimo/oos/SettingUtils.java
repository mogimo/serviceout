package jp.gr.java_conf.mogimo.oos;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingUtils {
    public static final String MODE = "mode";
    public static final String WARNING = "warning";

    private static final String FILENAME = "settings";
    private Context mContext;
    private SharedPreferences mSp;
    
    SettingUtils(Context context) {
        this.mContext = context;
        mSp = mContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE);
    }
    
    void saveIntSetting(String id, int value) {
        SharedPreferences.Editor editor = mSp.edit();
        editor.putInt(id, value);
        editor.commit();
    }
    
    int restoreIntSetting(String id, int defValue) {
        return mSp.getInt(id, defValue);
    }
    
    void saveBooleanSetting(String id, boolean value) {
        SharedPreferences.Editor editor = mSp.edit();
        editor.putBoolean(id, value);
        editor.commit();
    }
    
    boolean restoreBooleanSetting(String id, boolean defValue) {
        return mSp.getBoolean(id, defValue);
    }

}
