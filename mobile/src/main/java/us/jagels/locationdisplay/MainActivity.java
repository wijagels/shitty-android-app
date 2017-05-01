package us.jagels.locationdisplay;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

import static java.text.DateFormat.getDateTimeInstance;

public class MainActivity extends Activity {
    private int DEFAULT_INTERVAL = 5;
    private int SHAKE_REDUCE = 1;
    private int LOWER_INTERVAL_BOUND = 1;
    private int LOW_BATT_THRESHOLD = 50;
    private int LOW_BATT_OFFSET = 10;
    private int interval;


    private LocationManager locationManager;
    private LocationListener locationListener;
    private SensorManager mSensorManager;
    private float mAccel; // acceleration apart from gravity
    private float mAccelCurrent; // current acceleration including gravity
    private float mAccelLast; // last acceleration including gravity
    private Intent batteryStatus;
    private final SensorEventListener mSensorListener = new SensorEventListener() {
        private long debounce = 0;

        public void onSensorChanged(SensorEvent se) throws SecurityException {
            float x = se.values[0];
            float y = se.values[1];
            float z = se.values[2];
            mAccelLast = mAccelCurrent;
            mAccelCurrent = (float) Math.sqrt((double) (x * x + y * y + z * z));
            float delta = mAccelCurrent - mAccelLast;
            mAccel = mAccel * 0.9f + delta; // perform low-cut filter

            if (mAccel > 30) {
                if (se.timestamp - debounce > 2e9) {
                    debounce = se.timestamp;
                    decLocPolling();
                    updateLocPolling();
                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Shake it like a Polariod picture! " + Integer.toString(interval), Toast.LENGTH_SHORT);
                    toast.show();
                }
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Lol who gives a shit
        }
    };
    private IntentFilter ifilter;

    private void decLocPolling() {
        interval -= SHAKE_REDUCE;
        updateLocPolling();
    }

    private void updateLocPolling() throws SecurityException {
        if (getBatt() > LOW_BATT_THRESHOLD / 100) {
            locationManager.removeUpdates(locationListener);
            if (interval < LOWER_INTERVAL_BOUND) interval = DEFAULT_INTERVAL;
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    interval * 1000, 0, locationListener);
        } else {
            locationManager.removeUpdates(locationListener);
            if (interval < LOWER_INTERVAL_BOUND + LOW_BATT_OFFSET)
                interval = DEFAULT_INTERVAL + LOW_BATT_OFFSET;
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    interval * 1000, 0, locationListener);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        interval = DEFAULT_INTERVAL;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = this.registerReceiver(null, ifilter);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                setstr(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        // Gimme location pls
        while (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval * 1000, 0, locationListener);
    }

    protected void setstr(Location location) {
        TextView tv = (TextView) findViewById(R.id.sample_text);
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(location.getLatitude());
        sb.append(", ");
        sb.append(location.getLatitude());
        sb.append(") ");
        String dateString = getDateTimeInstance().format(new Date(location.getTime()));
        sb.append(dateString);
        tv.setText(sb.toString());
    }

    private float getBatt() {
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return level / (float) scale;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLocPolling();
        mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        mSensorManager.unregisterListener(mSensorListener);
        locationManager.removeUpdates(locationListener);
        super.onPause();
    }
}
