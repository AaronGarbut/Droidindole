package com.aarongarbut.droidindole;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    String action;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Switch switcher = findViewById(R.id.switcher);
        switcher.setOnCheckedChangeListener((CompoundButton button, boolean open) -> {
            synchronized (this) {
                action = open ? "T" : "F";
                Intent intent = VpnService.prepare(this);
                if (intent != null) {
                    startActivityForResult(intent, 0);
                } else {
                    onActivityResult(0, RESULT_OK, null);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK) {
            startService(new Intent(this, IndoleVpnService.class).setAction(action));
        }
    }

    @SuppressLint("SetTextI18n")
    public void command(View view) {
        EditText editText = findViewById(R.id.command);
        String[] words = editText.getText().toString().split(" ");
        TextView textView = findViewById(R.id.result);
        SharedPreferences sharedPreferences = getSharedPreferences("", MODE_PRIVATE);
        try {
            switch (words[0]) {
                case "port":
                    switch (words[1]) {
                        case "get":
                            textView.setText(Integer.toString(sharedPreferences.getInt("Port", 3023)));
                            break;
                        case "set":
                            if (sharedPreferences.edit().putInt("Port", Integer.parseInt(words[2])).commit()) {
                                textView.setText("OK");
                            } else {
                                textView.setText("NOT OK");
                            }
                            break;
                        default:
                            textView.setText("ERROR SUB COMMAND");
                            break;
                    }
                    break;
                case "application":
                    switch (words[1]) {
                        case "get":
                            textView.setText(String.join("\n", sharedPreferences.getStringSet("Application", new HashSet<>())));
                            break;
                        case "add":
                            Set<String> application_add_set = sharedPreferences.getStringSet("Application", new HashSet<>());
                            application_add_set.add(words[2]);
                            if (sharedPreferences.edit().putStringSet("Application", application_add_set).commit()) {
                                textView.setText("OK");
                            } else {
                                textView.setText("NOT OK");
                            }
                            break;
                        case "del":
                            Set<String> application_del_set = sharedPreferences.getStringSet("Application", new HashSet<>());
                            application_del_set.remove(words[2]);
                            if (sharedPreferences.edit().putStringSet("Application", application_del_set).commit()) {
                                textView.setText("OK");
                            } else {
                                textView.setText("NOT OK");
                            }
                            break;
                        case "all":
                            textView.setText(
                                    getPackageManager()
                                            .getInstalledApplications(
                                                    PackageManager.GET_META_DATA)
                                            .stream()
                                            .map(x -> x.packageName + "\n")
                                            .collect(Collectors.joining()));
                            break;
                        default:
                            textView.setText("ERROR SUB COMMAND");
                            break;
                    }
                    break;
                case "help":
                    textView.setText("HELP:\n"
                            + "port get\n"
                            + "port set <PORT>\n"
                            + "application get\n"
                            + "application add <PACKAGE NAME>\n"
                            + "application del <PACKAGE NAME>\n"
                            + "application all\n");
                    break;
                default:
                    textView.setText("ERROR COMMAND");
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            textView.setText("ERROR");
        }

    }
}
