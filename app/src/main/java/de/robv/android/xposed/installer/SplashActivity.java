package de.robv.android.xposed.installer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Clone intent to pass on filter information
        // such as intent action for launcher shortcuts
        Intent intent = getIntent().cloneFilter();
        intent.setClass(this, WelcomeActivity.class);
        startActivity(intent);
        finish();
    }
}
