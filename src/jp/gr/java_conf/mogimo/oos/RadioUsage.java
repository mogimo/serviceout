package jp.gr.java_conf.mogimo.oos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class RadioUsage extends Activity {
    private TextView mText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.logview);
        mText = (TextView)findViewById(R.id.text);
        
        Intent intent = getIntent();
        String str = intent.getStringExtra("EXTRA_LOG");
        if (str.equals("")) {
            mText.setText(getString(R.string.nothing));
        } else {
            mText.setText(str);
        }
    }
}
