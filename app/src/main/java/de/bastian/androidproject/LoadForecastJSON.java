package de.bastian.androidproject;

import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import de.bastian.androidproject.WeatherData.WeatherForecast;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoadForecastJSON extends AsyncTask<Location,Boolean, Integer> {
    private WeakReference<MainActivity> weakMainActivity;
    private WeatherForecast weatherForecast;
    private SharedPreferences mPrefs;
    private static String appid = "cfe31ebef1a89f6504ab9bac85dab8c4";


    LoadForecastJSON(MainActivity mainActivity) {
        weakMainActivity = new WeakReference<>(mainActivity);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mainActivity);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Integer doInBackground(Location... locations) {

        String unit = mPrefs.getString("UNIT", "metric");
        String language = mPrefs.getString("Language", "en");

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Api.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        Api api = retrofit.create(Api.class);

        if(locations != null) {
            Call<WeatherForecast> call1 = api.getWeatherForecastFromCoordinates(appid, locations[0].getLatitude(), locations[0].getLongitude(), unit, language);

            call1.enqueue(new Callback<WeatherForecast>() {
                @Override
                public void onResponse(@NonNull Call<WeatherForecast> call, @NonNull Response<WeatherForecast> response) {
                    weatherForecast = response.body();
                    publishProgress(true);
                }

                @Override
                public void onFailure(@NonNull Call<WeatherForecast> call, @NonNull Throwable t) {
                    publishProgress(false);
                }
            });

        }

        return 0;
    }

    @Override
    protected void onProgressUpdate(Boolean... values) {
        super.onProgressUpdate(values);
        MainActivity mainActivity = weakMainActivity.get();

        if(mainActivity != null){
            if(values[0]) {
                mainActivity.updateWeatherForecast(weatherForecast);
            }
            else Toast.makeText(mainActivity, "Error retrieving weather data", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPostExecute(Integer integer) {
        super.onPostExecute(integer);
    }

}
