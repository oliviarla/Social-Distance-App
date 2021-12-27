package com.example.distancecheck;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.example.distancecheck.R.*;

public class MapRouteActivity extends BaseActivity implements OnMapReadyCallback{
    private Button button1;
    SupportMapFragment mapFragment;
    GoogleMap mMap;
    double ori_latitude = ((MapsActivity)MapsActivity.context_main).latitude;
    double ori_longitude = ((MapsActivity)MapsActivity.context_main).longitude;

    EditText editText;
    private Button btnOK;

    //double ori_latitude = 37.23990168243135;
   // double ori_longitude = 127.0832652116079;
    double dest_latitude = 0 ;
    double dest_longtitude = 0 ;

    ProgressDialog progressDialog;

    LatLng origin = new LatLng(ori_latitude, ori_longitude);
    LatLng dest = new LatLng(dest_latitude, dest_longtitude);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_route);
        editText = findViewById(R.id.editText);
        btnOK = (Button)findViewById(R.id.btn);

        MapFragment mapFragment = (MapFragment) getFragmentManager() .findFragmentById(id.map);
        mapFragment.getMapAsync(this);

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //editText.setText("버튼눌렀음");
                Log.d("도착지2", editText.getText().toString());

                if(editText.getText().toString().length() > 0){
                    Location location = getLocationFromAddress(getApplicationContext(), editText.getText().toString());
                    dest_latitude =  location.getLatitude();
                    dest_longtitude = location.getLongitude();
                    Log.d("도착지1", String.valueOf(location.getLatitude()));
                    Log.d("도착지2", String.valueOf(location.getLongitude()));

                    Log.d("확인2222 ", String.valueOf(dest_latitude));
                    LatLng origin = new LatLng(ori_latitude, ori_longitude);
                    LatLng dest2 = new LatLng(dest_latitude, dest_longtitude);
                    mMap.addMarker(new MarkerOptions().position(dest2).title("dest Location"));
                    drawPolylines(origin, dest2);

                    

                }
            }
        });

/*
        dest_latitude = imsi_la;
        dest_longtitude = imsi_lo;
        MapFragment mapFragment = (MapFragment) getFragmentManager() .findFragmentById(id.map);
        mapFragment.getMapAsync(this);
        Log.d("확인2222 ", String.valueOf(imsi_la));

*/
    }



    private Location getLocationFromAddress(Context context, String address) {
        Geocoder geocoder = new Geocoder(context);
        List<Address> addresses;
        Location resLocation = new Location("");
        try {
            addresses = geocoder.getFromLocationName(address, 5);
            if((addresses == null) || (addresses.size() == 0)) {
                return null;
            }
            Address addressLoc = addresses.get(0);

            resLocation.setLatitude(addressLoc.getLatitude());
            resLocation.setLongitude(addressLoc.getLongitude());

        } catch (Exception e) {
            e.printStackTrace();
        }
        return resLocation;
    }

    private void drawPolylines(LatLng origin, LatLng dest) {
        Log.d("Exception", "그림그리기");
        progressDialog = new ProgressDialog(MapRouteActivity.this);
        progressDialog.setMessage("Please Wait, Polyline between two locations is building.");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Checks, whether start and end locations are captured
        // Getting URL to the Google Directions API
        String url = getDirectionsUrl(origin, dest);
        Log.d("url", url + "");
        DownloadTask downloadTask = new DownloadTask();
        // Start downloading json data from Google Directions API
        downloadTask.execute(url);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.mymenu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.addMarker(new MarkerOptions()
                .position(origin)
                .title("Now")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(origin, 15));
    }


    private class DownloadTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... url) {
            String data = "";
            try {
                data = downloadUrl(url[0]);
                Log.d("Download Data", data);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            ParserTask parserTask = new ParserTask();
            parserTask.execute(result);

        }
    }
    /**
     * A class to parse the Google Places in JSON format
     */
    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {

            progressDialog.dismiss();
            Log.d("result", result.toString());
            ArrayList points = null;
            PolylineOptions lineOptions = null;
            Log.d("1번째 ", "-");

            for (int i = 0; i < result.size(); i++) {
                Log.d("2번째 ", "-");
                points = new ArrayList();
                lineOptions = new PolylineOptions();

                List<HashMap<String, String>> path = result.get(i);

                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);

                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    Log.d("잘나오는가 ", "lat" + lat);
                    Log.d("잘나오는가 ", "lng" + lng);
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                lineOptions.addAll(points);
                lineOptions.width(12);
                lineOptions.color(Color.RED);
                lineOptions.geodesic(true);

            }

// Drawing polyline in the Google Map for the i-th route
            if(result.size()>0) {
                mMap.addPolyline(lineOptions);
            }
        }
    }
    private String getDirectionsUrl(LatLng origin, LatLng dest) {
        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        // Sensor enabled

        String sensor = "sensor=false";
        String key = "key=AIzaSyB3KLLZS8rRxWw5GuQ3rZ4gnssgjo0yVXs";
        String mode = "mode=transit";
        // Building the parameters to the web service

        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" +mode + "&"+ key;
        // Output format

        //String parameters = str_origin + "&" + str_dest + "&" + key;
        String output = "json";
        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;
        return url;
    }

    /**
     * A method to download json data from url
     */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));
            StringBuffer sb = new StringBuffer();
            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            data = sb.toString();
            br.close();
            Log.d("data", data);

        } catch (Exception e) {
            Log.d("Exception", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }
}
