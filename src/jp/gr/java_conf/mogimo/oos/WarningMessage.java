package jp.gr.java_conf.mogimo.oos;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;

public class WarningMessage extends Activity 
        implements CompoundButton.OnCheckedChangeListener, View.OnClickListener {

    private Button mClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.warning_message);

        CheckBox mReadFirstCheck = (CheckBox)findViewById(R.id.checkbox);
        mReadFirstCheck.setOnCheckedChangeListener(this);
        
        mClose = (Button)findViewById(R.id.close);
        mClose.setOnClickListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
        SettingUtils setting = new SettingUtils(this);
        setting.saveBooleanSetting(SettingUtils.WARNING, !isChecked);
    }

    @Override
    public void onClick(View view) {
        finish();
    }
}
