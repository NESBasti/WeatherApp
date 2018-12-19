package de.bastian.androidproject;

import android.location.Location;
import android.os.AsyncTask;
import android.widget.Toast;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoadForecastJSON extends AsyncTask<Location,Boolean, Integer> {
    private MainActivity mainActivity;
    private WeatherForecast weatherForecast;
    private static String appid = "cfe31ebef1a89f6504ab9bac85dab8c4";


    public LoadForecastJSON(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Integer doInBackground(Location... locations) {


        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Api.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        Api api = retrofit.create(Api.class);

        if(locations != null) {
            Call<WeatherForecast> call1 = api.getWeatherForecastFromCoordinates(appid, locations[0].getLatitude(), locations[0].getLongitude(), "metric", "de");

            call1.enqueue(new Callback<WeatherForecast>() {
                @Override
                public void onResponse(Call<WeatherForecast> call, Response<WeatherForecast> response) {
                    weatherForecast = response.body();
                    publishProgress(true);

                }

                @Override
                public void onFailure(Call<WeatherForecast> call, Throwable t) {
                    publishProgress(false);
                }
            });

        }

        return 0;
    }

    @Override
    protected void onProgressUpdate(Boolean... values) {
        super.onProgressUpdate(values);

        if(values[0]) {
            mainActivity.updateWeatherForecast(weatherForecast);
        }
        else Toast.makeText(mainActivity, "No Connection", Toast.LENGTH_SHORT).show();

    }

    @Override
    protected void onPostExecute(Integer integer) {
        super.onPostExecute(integer);
    }

}
