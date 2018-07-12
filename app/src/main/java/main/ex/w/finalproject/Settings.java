package main.ex.w.finalproject;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public class Settings extends AppCompatActivity {

    private SharedPreferences preferences;
    private RadioGroup[] radioGroups;

    private int   groupCount = 5; //EDIT
    private int[] radioVals;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        String radio = "Radio";

        radioVals = new int[groupCount];
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        radioGroups = new RadioGroup[groupCount];

        for (int i = 0; i < groupCount; i++) {
            int id = i+1; //Because I forgot to index the ID's on zero.
            radioVals[i] = preferences.getInt(radio+i,1);

            int rgId = getResources().getIdentifier("rg"+id, "id", getApplicationContext().getPackageName());
            radioGroups[i] = findViewById(rgId);

            int rbId = getResources().getIdentifier("rb"+id+""+(radioVals[i]+1), "id", getApplicationContext().getPackageName());


            radioGroups[i].check(rbId);
        }
    }

    @Override
    public void onBackPressed() {
        pushValues();
        finish();
    }


    public void pushValues() {
        for (int i = 0; i < groupCount; i++) {
            //Fetch index of value, push to preferences.
            int rbId = radioGroups[i].getCheckedRadioButtonId();
            RadioButton rb = radioGroups[i].findViewById(rbId);
            int index = radioGroups[i].indexOfChild(rb);

            preferences.edit().putInt("Radio"+i,index).apply();
        }
    }


}
