package de.bastian.androidproject;

import android.Manifest;
import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import de.bastian.androidproject.WeatherData.WeatherCurrent;
import de.bastian.androidproject.WeatherData.WeatherForecast;

import static de.bastian.androidproject.UserInterface.iconToResource;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    // GPS
    private Location location;
    private Location[]  locationCities = new Location[6];
    private LocationCallback locationCallback;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private GoogleApiClient googleApiClient;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static final long UPDATE_INTERVAL = 120000, FASTEST_INTERVAL = 120000; // = 120 seconds
    private ArrayList<String> permissionsToRequest;
    private ArrayList<String> permissionsRejected = new ArrayList<>();
    private ArrayList<String> permissions = new ArrayList<>();
    private static final int ALL_PERMISSIONS_RESULT = 1011;
    private LocationManager locationManager;
    private int currentCity = 0;
    private String[] mLocationList = new String[6];

    // JSON
    private int updateFrequency = 120000; // = 120 seconds

    //Swipe Refresh
    private SwipeRefreshLayout swipeLayout;

    //User Interface
    private UserInterface ui;
    private SharedPreferences widgetData;
    private SharedPreferences  mPrefs;

    //Animation
    private LinearLayout linearLayoutBackground;

    //Selected City
    private ImageView cityShow1;
    private ImageView cityShow2;
    private ImageView cityShow3;
    private ImageView cityShow4;
    private ImageView cityShow5;
    private ImageView cityShow6;
    private ImageView myGPS;
    private int cityCounter;


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        mPrefs = getPreferences(MODE_PRIVATE);
        widgetData = this.getApplicationContext().getSharedPreferences("WIDGET_DATA", Context.MODE_PRIVATE);
        linearLayoutBackground = findViewById(R.id.background);


        ui = new UserInterface(this);

        mLocationList = loadArray("myCitynames");
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        cityCounter = preferences.getInt("CityCount", 0);

        String background = preferences.getString("Background", "0");
        if (background != null) {
            switch (background) {
                case "1":
                    linearLayoutBackground.setBackgroundResource(R.drawable.background_image);
                    break;
                case "2":
                    linearLayoutBackground.setBackgroundResource(R.drawable.background_image1);
                    break;
                case "3":
                    linearLayoutBackground.setBackgroundResource(R.drawable.background_image2);
                    break;
                case "4":
                    linearLayoutBackground.setBackgroundResource(R.drawable.background_image3);
                    break;
                default:
                    break;
            }
        }

        for (int i = 0; i < 6; i++) {
            locationCities[i] = new Location("");
        }
        for (int i = 1; i < mLocationList.length; i++) {
            List<Address> address;
            Geocoder geocoder = new Geocoder(this);
            try {
                address = geocoder.getFromLocationName(mLocationList[i], 5);
                if (address != null) {
                    locationCities[i].setLatitude(address.get(0).getLatitude());
                    locationCities[i].setLongitude(address.get(0).getLongitude());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // adding permission to request the location
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissionsToRequest = permissionsToRequest(permissions);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permissionsToRequest.size() > 0) {
                requestPermissions(permissionsToRequest.toArray(
                        new String[0]), ALL_PERMISSIONS_RESULT);
            }
        }

        //initialize fusedLocationProviderClient and locationCallback
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                location = locationResult.getLastLocation();
                onLocationChanged(locationResult.getLastLocation());
                locationCities[0] = location;
            }

        };


        //Swipe Refresh
        swipeLayout = findViewById(R.id.swipe_container);

        swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                onResume();
                ui.setLastUpdate(0L);
                getJSON(locationCities[currentCity]);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        swipeLayout.setRefreshing(false); //stop animation after 4 seconds
                    }
                }, 4000);
            }
        });

        // Scheme colors for animation
        swipeLayout.setColorSchemeColors(
                getResources().getColor(android.R.color.holo_blue_bright),
                getResources().getColor(android.R.color.holo_green_light),
                getResources().getColor(android.R.color.holo_orange_light),
                getResources().getColor(android.R.color.holo_red_light)
        );

        //Selected WeatherCity
        cityShow1 = findViewById(R.id.MyCityShow1);
        cityShow2 = findViewById(R.id.MyCityShow2);
        cityShow3 = findViewById(R.id.MyCityShow3);
        cityShow4 = findViewById(R.id.MyCityShow4);
        cityShow5 = findViewById(R.id.MyCityShow5);
        cityShow6 = findViewById(R.id.MyCityShow6);

        myGPS = findViewById(R.id.MyGPS);


        if(cityCounter > 0) {
            for (int i = 0; i <= cityCounter; i++) {
                switch (i) {
                    case 0:
                        cityShow1.setVisibility(View.VISIBLE);
                        break;
                    case 1:
                        cityShow2.setVisibility(View.VISIBLE);
                        break;
                    case 2:
                        cityShow3.setVisibility(View.VISIBLE);
                        break;
                    case 3:
                        cityShow4.setVisibility(View.VISIBLE);
                        break;
                    case 4:
                        cityShow5.setVisibility(View.VISIBLE);
                        break;
                    case 5:
                        cityShow6.setVisibility(View.VISIBLE);
                        break;
                    default:
                        break;
                }
            }
        }


        linearLayoutBackground.setOnTouchListener(new OnSwipeTouchListener(MainActivity.this) {
            public void onSwipeRight() {
                if(cityCounter > 0){
                    currentCity--;
                    if (currentCity < 0)
                        currentCity = cityCounter;

                    updateSelectedCity(currentCity);
                    ui.setLastUpdate(0L);
                    getJSON(locationCities[currentCity]);
                    ui.updateInterface();
                }
            }

            public void onSwipeLeft() {
                if(cityCounter > 0){
                    currentCity++;
                    if (currentCity > cityCounter)
                        currentCity = 0;
                    updateSelectedCity(currentCity);
                    ui.setLastUpdate(0L);
                    getJSON(locationCities[currentCity]);
                    ui.updateInterface();
                }

            }
        });

        updateWeatherWidget();

    }

    /**
     *  called when activity gets reordered to the front
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        reopenSavedActivity();

    }

    /**
     *      Uses Retrofit and GSON Converter to grab a JSON of current weather and the
     *      5-day-weather-forecast from openweathermap.org
     */
    private void getJSON(Location location){
        if((System.currentTimeMillis() > ui.getLastUpdate() + updateFrequency) && location != null){
            LoadCurrentJSON loadCurrentJSON = new LoadCurrentJSON(MainActivity.this);
            loadCurrentJSON.execute(location);
            LoadForecastJSON loadForecastJSON = new LoadForecastJSON(MainActivity.this);
            loadForecastJSON.execute(location);
            ui.setLastUpdate(System.currentTimeMillis());
            ui.setLocation(location);
        }
        else if(location == null & locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) & locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
            Toast.makeText(this, R.string.ToastNoNetworkConnection, Toast.LENGTH_SHORT).show();

        }

    }

    /**
     *      sets currentWeather when it got fetched,
     *      called in LoadCurrentJSON
     */
    public void updateCurrentWeather(WeatherCurrent current){
        ui.setWeatherCurrent(current);
        ui.updateCurrentInterface();
    }

    /**
     *      sets weatherForecast when it got fetched,
     *      called in LoadForecastJSON
     */
    public void updateWeatherForecast(WeatherForecast forecast){
        ui.setWeatherForecast(forecast);
        ui.updateForecastInterface();
    }

    /**
     * updates all AppWidgets on the home screen with latest data
     */
    void updateWeatherWidget(){
        SharedPreferences.Editor edit = widgetData.edit();
        if(ui.getWeatherCurrent() != null) {
            edit.putInt("TEMPERATURE", ((int) Math.round(ui.getWeatherCurrent().getMain().getTemp())));
            if(location != null){
                edit.putFloat("LATITUDE", (float)location.getLatitude());
                edit.putFloat("LONGITUDE", (float)location.getLongitude());
            }
            edit.putLong("LAST_UPDATE", ui.getLastUpdate());
            edit.putString("LOCATION", (String)ui.getCityName().getText());
            edit.putInt("CITYCOUNTER", cityCounter);
            edit.putFloat("LATITUDE2", (float)locationCities[1].getLatitude());
            edit.putFloat("LONGITUDE2", (float)locationCities[1].getLongitude());
            edit.putFloat("LATITUDE3", (float)locationCities[2].getLatitude());
            edit.putFloat("LONGITUDE3", (float)locationCities[2].getLongitude());
            edit.putFloat("LATITUDE4", (float)locationCities[3].getLatitude());
            edit.putFloat("LONGITUDE4", (float)locationCities[3].getLongitude());
            edit.putFloat("LATITUDE5", (float)locationCities[4].getLatitude());
            edit.putFloat("LONGITUDE5", (float)locationCities[4].getLongitude());
            edit.putFloat("LATITUDE6", (float)locationCities[5].getLatitude());
            edit.putFloat("LONGITUDE6", (float)locationCities[5].getLongitude());
            edit.putInt("ICON", iconToResource(ui.getWeatherCurrent().getWeather().get(0).getIcon()));
            edit.apply();
        }

        AppWidgetManager man = AppWidgetManager.getInstance(this.getApplicationContext());
        int[] ids = man.getAppWidgetIds(
                new ComponentName(this.getApplicationContext(),WeatherAppWidgetProvider.class));
        Intent updateIntent = new Intent();
        updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        updateIntent.putExtra(WeatherAppWidgetProvider.WIDGET_IDS_KEY, ids);
        this.getApplicationContext().sendBroadcast(updateIntent);
    }

    //region GPS functions
    private void buildGoogleApiClient(){
        googleApiClient = new GoogleApiClient.Builder(this).
                addApi(LocationServices.API).
                addConnectionCallbacks(this).
                addOnConnectionFailedListener(this).build();

        googleApiClient.connect();
    }

    private ArrayList<String> permissionsToRequest(ArrayList<String> wantedPermissions) {
        ArrayList<String> result = new ArrayList<>();

        for (String perm : wantedPermissions) {
            if (!hasPermission(perm)) {
                result.add(perm);
            }
        }

        return result;
    }

    private boolean hasPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }



    @Override
    protected void onResume() {
        super.onResume();

        if (googleApiClient != null && fusedLocationProviderClient != null) {
            startLocationUpdates();
        }else if(!checkPlayServices()) {
            Toast.makeText(this, R.string.ToastGooglePlayServices, Toast.LENGTH_SHORT).show();
        }else {
            buildGoogleApiClient();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (googleApiClient != null  &&  googleApiClient.isConnected()) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);

        }
    }


    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST);
            } else {
                finish();
            }

            return false;
        }

        return true;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationProviderClient.getLastLocation();
        startLocationUpdates();



    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onLocationChanged(Location location) {
        if(currentCity == 0){
            getJSON(location);
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);


        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                &&  ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.ToastGPSPermission, Toast.LENGTH_SHORT).show();
        }

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());


        if(!(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) & location == null) {
            highAccuracyDisabled();
        }

        if(currentCity == 0 && location != null){
            getJSON(location);
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode) {
            case ALL_PERMISSIONS_RESULT:
                for (String perm : permissionsToRequest) {
                    if (!hasPermission(perm)) {
                        permissionsRejected.add(perm);
                    }
                }

                if (permissionsRejected.size() > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (shouldShowRequestPermissionRationale(permissionsRejected.get(0))) {
                            new AlertDialog.Builder(MainActivity.this).
                                    setMessage("These permissions are mandatory to get your location. You need to allow them.").
                                    setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            requestPermissions(permissionsRejected.
                                                toArray(new String[0]), ALL_PERMISSIONS_RESULT);

                                        }
                                    }).setNegativeButton("Cancel", null).create().show();
                            return;
                        }
                    }
                } else {
                    if (googleApiClient != null) {
                        googleApiClient.connect();
                    }
                }
                break;
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(this, R.string.ToastConnectionFailed, Toast.LENGTH_SHORT).show();
    }

    /**
     *      gets called when high accuracy for location is disabled,
     *      opens Settings for user to enable it
     */
    private void highAccuracyDisabled(){

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.DialogGPSTitle);
        builder.setMessage(R.string.DialogGPSMessage);

        builder.setPositiveButton(R.string.YES, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                Intent viewIntent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(viewIntent);
                googleApiClient.disconnect();
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(R.string.NO, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {


                googleApiClient.disconnect();
                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();


    }
    // endregion

    //region save content
    @Override
    protected void onStop() {
        super.onStop();

        SharedPreferences.Editor prefsEditor = mPrefs.edit();
        Gson gson = new Gson();
        if(ui.getWeatherCurrent() != null){
            String jsonCurrent = gson.toJson(ui.getWeatherCurrent());
            prefsEditor.putString("weatherCurrent", jsonCurrent);
        }
        if(ui.getWeatherForecast() != null){
            String jsonForecast = gson.toJson(ui.getWeatherForecast());
            prefsEditor.putString("weatherForecast", jsonForecast);
        }
        prefsEditor.putLong("lastUpdate", ui.getLastUpdate());
        prefsEditor.putInt("currentCity", currentCity);
        prefsEditor.apply();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Gson gson = new Gson();
        String jsonCurrent = mPrefs.getString("weatherCurrent", "");
        if(gson.fromJson(jsonCurrent, WeatherCurrent.class) != null){
            ui.setWeatherCurrent(gson.fromJson(jsonCurrent, WeatherCurrent.class));
        }
        String jsonForecast = mPrefs.getString("weatherForecast", "");
        if(gson.fromJson(jsonForecast, WeatherForecast.class) != null){
            ui.setWeatherForecast(gson.fromJson(jsonForecast, WeatherForecast.class));
        }
        Long prefsUpdate = mPrefs.getLong("lastUpdate", 0);
        ui.setLastUpdate(prefsUpdate);
        currentCity = mPrefs.getInt("currentCity", 0);
        updateSelectedCity(currentCity);
        ui.updateInterface();
    }
    //endregion

    /**
     *  opens settings (reorders settings to front if it already exists)
     */
    public void MySettingsOC(View view) {
        Intent i = new Intent(MainActivity.this, Settings.class);
        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
        //finish();
    }

    /**
     *  shows more information for the selected day
     */
    public void MyDailyOpening1(View view) {
        ui.openDaily(1);
    }

    public void MyDailyOpening2(View view) {
        ui.openDaily(2);
    }

    public void MyDailyOpening3(View view) {
        ui.openDaily(3);
    }

    public void MyDailyOpening4(View view) {
        ui.openDaily(4);
    }

    public void MyDailyOpening5(View view) {
        ui.openDaily(5);
    }


    /**
     *  closes more information
     */
    public void MyDailyClosing(View view){
        ui.setInvisible();
        ui.setInvisibleClothing();
    }

    /**
     *  load cities from shared preferences into array
     */
    public String[] loadArray(String arrayName) {
        SharedPreferences prefs = getSharedPreferences("sharedLocations", MODE_PRIVATE);
        int size = prefs.getInt(arrayName + "_size", 0);
        String array[] = new String[size];
        for(int i=0;i<size;i++)
            array[i] = prefs.getString(arrayName + "_" + i, null);
        return array;
    }

    /**
     * shows more text for the clothes suggestions
     */
    public void MyClothingOpening1(View view) {
        ui.openClothing(1);
    }
    public void MyClothingOpening2(View view) {
        ui.openClothing(2);
    }
    public void MyClothingOpening3(View view) {
        ui.openClothing(3);
    }
    public void MyClothingOpening4(View view) {
        ui.openClothing(4);
    }
    public void MyClothingOpening5(View view) {
        ui.openClothing(5);
    }

    /**
     *  updates the page indicator
     */
    public void updateSelectedCity(int showedCity) {
        cityShow1.setImageResource(R.drawable.unselectedcity);
        cityShow2.setImageResource(R.drawable.unselectedcity);
        cityShow3.setImageResource(R.drawable.unselectedcity);
        cityShow4.setImageResource(R.drawable.unselectedcity);
        cityShow5.setImageResource(R.drawable.unselectedcity);
        cityShow6.setImageResource(R.drawable.unselectedcity);
        myGPS.setVisibility(View.INVISIBLE);
        switch(showedCity)
        {
            case 0:
                myGPS.setVisibility(View.VISIBLE);
                cityShow1.setImageResource(R.drawable.selectedcity);
                break;
            case 1:
                cityShow2.setImageResource(R.drawable.selectedcity);
                break;
            case 2:
                cityShow3.setImageResource(R.drawable.selectedcity);
                break;
            case 3:
                cityShow4.setImageResource(R.drawable.selectedcity);
                break;
            case 4:
                cityShow5.setImageResource(R.drawable.selectedcity);
                break;
            case 5:
                cityShow6.setImageResource(R.drawable.selectedcity);
                break;
                default:
                    break;
        }
    }

    /**
     *  called by newIntent
     *  updates mainActivity when it gets reordered to the front (e.g. after closing settings)
     */
    @SuppressLint("ClickableViewAccessibility")
    void reopenSavedActivity(){
        mLocationList = loadArray("myCitynames");
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        cityCounter = preferences.getInt("CityCount", 0);
        onResume();

        String background = preferences.getString("Background", "0");
        if (background != null) {
            switch (background) {
                case "1":
                    linearLayoutBackground.setBackgroundResource(R.drawable.background_image);
                    break;
                case "2":
                    linearLayoutBackground.setBackgroundResource(R.drawable.background_image1);
                    break;
                case "3":
                    linearLayoutBackground.setBackgroundResource(R.drawable.background_image2);
                    break;
                case "4":
                    linearLayoutBackground.setBackgroundResource(R.drawable.background_image3);
                    break;
                default:
                    break;
            }
        }

        for (int i = 0; i < 6; i++) {
            locationCities[i] = new Location("");
        }
        for (int i = 1; i < mLocationList.length; i++) {
            List<Address> address;
            Geocoder geocoder = new Geocoder(this);
            try {
                address = geocoder.getFromLocationName(mLocationList[i], 5);
                if (address != null) {
                    locationCities[i].setLatitude(address.get(0).getLatitude());
                    locationCities[i].setLongitude(address.get(0).getLongitude());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        cityShow1.setVisibility(View.INVISIBLE);
        cityShow2.setVisibility(View.GONE);
        cityShow3.setVisibility(View.GONE);
        cityShow4.setVisibility(View.GONE);
        cityShow5.setVisibility(View.GONE);
        cityShow6.setVisibility(View.GONE);

        if(cityCounter > 0) {
            for (int i = 0; i <= cityCounter; i++) {
                switch (i) {
                    case 0:
                        cityShow1.setVisibility(View.VISIBLE);
                        break;
                    case 1:
                        cityShow2.setVisibility(View.VISIBLE);
                        break;
                    case 2:
                        cityShow3.setVisibility(View.VISIBLE);
                        break;
                    case 3:
                        cityShow4.setVisibility(View.VISIBLE);
                        break;
                    case 4:
                        cityShow5.setVisibility(View.VISIBLE);
                        break;
                    case 5:
                        cityShow6.setVisibility(View.VISIBLE);
                        break;
                    default:
                        break;
                }
            }
        }

        linearLayoutBackground.setOnTouchListener(new OnSwipeTouchListener(MainActivity.this) {
            public void onSwipeRight() {
                if(cityCounter > 0){
                    currentCity--;
                    if (currentCity < 0)
                        currentCity = cityCounter;

                    updateSelectedCity(currentCity);
                    ui.setLastUpdate(0L);
                    getJSON(locationCities[currentCity]);
                    ui.updateInterface();
                }
            }

            public void onSwipeLeft() {
                if(cityCounter > 0){
                    currentCity++;
                    if (currentCity > cityCounter)
                        currentCity = 0;
                    updateSelectedCity(currentCity);
                    ui.setLastUpdate(0L);
                    getJSON(locationCities[currentCity]);
                    ui.updateInterface();
                }

            }
        });

        ui.setLastUpdate(0L);

        if(currentCity != 0) {
            getJSON(locationCities[currentCity]);
        }else getJSON(location);
    }
}