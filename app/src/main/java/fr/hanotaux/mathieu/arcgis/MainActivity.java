package fr.hanotaux.mathieu.arcgis;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.MapView;

import static android.provider.AlarmClock.EXTRA_MESSAGE;

public class MainActivity extends AppCompatActivity {
    private MapView mMapView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button_user = (Button) findViewById(R.id.button_user);
        button_user.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                LogInAsUser(v);
            }
        });

        Button button_driver = (Button) findViewById(R.id.button_driver);
        button_driver.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                LogInAsDriver(v);
            }
        });
    }

    public void LogInAsDriver(View view) {
        Intent intent = new Intent(this, ShowMap.class);
        //EditText editText = (EditText) findViewById(R.id.editText);
        //String message = editText.getText().toString();
        //intent.putExtra(EXTRA_MESSAGE, message);
        startActivity(intent);
    }

    public void LogInAsUser(View view) {
        Intent intent = new Intent(this, MapsActivity.class);
        //EditText editText = (EditText) findViewById(R.id.editText);
        //String message = editText.getText().toString();
        //intent.putExtra(EXTRA_MESSAGE, message);
        startActivity(intent);
    }



}
