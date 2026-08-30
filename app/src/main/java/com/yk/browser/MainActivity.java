package com.yk.browser;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn1 = findViewById(R.id.btnProfile1);
        Button btn2 = findViewById(R.id.btnProfile2);
        Button btn3 = findViewById(R.id.btnProfile3);

        btn1.setOnClickListener(v -> openCloneBrowser("Profile_1"));
        btn2.setOnClickListener(v -> openCloneBrowser("Profile_2"));
        btn3.setOnClickListener(v -> openCloneBrowser("Profile_3"));
    }

    private void openCloneBrowser(String profileName) {
        Intent intent = new Intent(MainActivity.this, CloneBrowserActivity.class);
        intent.putExtra("profile_name", profileName);
        startActivity(intent);
    }
}
