package com.example.distancecheck;

import android.Manifest;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.pedro.library.AutoPermissions;
import com.pedro.library.AutoPermissionsListener;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MapsActivity extends BaseActivity implements AutoPermissionsListener {

    //Map Route ?????? ??? ?????? ???????????????
    public static Context context_main;
    public double latitude;
    public double longitude;

    private Button button1;
    private TextView textView1;
    private TextView textView2;
    LocationManager manager;
    GPSListener gpsListener;
    SupportMapFragment mapFragment;
    GoogleMap map;
    Marker myMarker;

    Circle circle;
    CircleOptions circle2M;
    private DatabaseReference mDatabase;
    private String UserID; //???????????? ID ??????
    HashMap<String, Marker> MarkerHash = new HashMap<>();


    private final static String TAG = MapsActivity.class.getSimpleName();
    private BluetoothAdapter mBluetoothAdapter;
    HashMap<String, Integer> RssiHash= new HashMap<>();
    private boolean mScanning;
    private Handler mHandler;


    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Map Route ?????? ??? ?????? ???????????????
        context_main = this;

        super.onCreate(savedInstanceState);
        UserID = getMacAddress();
        setContentView(R.layout.activity_maps);
        setTitle("GPS ???????????? ????????????");
        button1 = findViewById(R.id.button1);
        textView1 = findViewById(R.id.textView1);
        textView2 = findViewById(R.id.textView2);
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        gpsListener = new GPSListener();
        mHandler = new Handler(); //bluetooth

        mDatabase = FirebaseDatabase.getInstance().getReference();

        try {
            MapsInitializer.initialize(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                Log.i("MyLocTest", "?????? ?????????");
                map = googleMap;
                //map.setMyLocationEnabled(true);
                map.getUiSettings().setZoomControlsEnabled(true);
                map.getUiSettings().setZoomGesturesEnabled(true);
            }
        });
        // ?????? ?????? ??????
        mapFragment.getView().setVisibility(View.GONE);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ?????? ??????
                mapFragment.getView().setVisibility(View.VISIBLE);
                startLocationService();
            }
        });


        // ???????????? Bluetooth LE ??? ??????????????? ??????
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //??????????????? ??????????????? ??????
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }


        // BluetoothManager??? ?????? BluetoothAdapter??? ?????? BluetoothAdapter ?????????
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // ?????????????????? ??????????????? ??????????????? ??????
        // null?????? ???????????? ?????? ???
        if (mBluetoothAdapter == null) {
            Log.v(TAG, "?????????????????? ??????????????? ???????????? ??????");
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        AutoPermissions.Companion.loadAllPermissions(this, 101);
    }

    public static String getMacAddress() {

        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;
                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();

                for (byte b : macBytes) {
                    res1.append(String.format("%02X:", b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }

        } catch (Exception ex) {

        }
        return "02:00:00:00:00:00";
    }

    public void startLocationService() {
        try {
            Location location = null;
            long minTime = 0;        // 0????????? ?????? - ??????????????????
            float minDistance = 0;
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location != null) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    String message = "?????? ?????? Network -> Latitude : " + latitude + "\n Longitude : " + longitude;
                    UserInfo user = new UserInfo(latitude, longitude);
                    WriteLocation(user);
                    textView1.setText(message);
                    if (myMarker!=null){
                        myMarker.remove();
                    }
                    myMarker = map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("my Location").icon(BitmapDescriptorFactory.fromResource((R.drawable.mylocation))));
                    showCurrentLocation(latitude, longitude);
                    Log.i("MyLocTest", "?????? ?????? Network ??????");
                }
                //?????? ????????????
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, minDistance, gpsListener);
                //manager.removeUpdates(gpsListener);
                Toast.makeText(getApplicationContext(), "??? ?????? network ?????? ?????????", Toast.LENGTH_SHORT).show();
                Log.i("MyLocTest", "requestLocationUpdates() ??? ?????? network?????? ???????????? ~~ ");
            } else if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location != null) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    String message = "?????? ?????? GPS -> Latitude : " + latitude + "\n Longitude : " + longitude;
                    //????????????????????? ?????? ??????
                    UserInfo user = new UserInfo(latitude, longitude);
                    WriteLocation(user);
                    textView1.setText(message);
                    if (myMarker!=null){
                        myMarker.remove();
                    }
                    myMarker = map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("my Location").icon(BitmapDescriptorFactory.fromResource((R.drawable.mylocation))));
                    showCurrentLocation(latitude, longitude);
                    Log.i("MyLocTest", "?????? ?????? GPS ??????");
                }
                //?????? ????????????
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDistance, gpsListener);
                //manager.removeUpdates(gpsListener);
                Toast.makeText(getApplicationContext(), "??? ?????? GPS ?????? ??????", Toast.LENGTH_SHORT).show();
                Log.i("MyLocTest", "requestLocationUpdates() ??? ?????? GPS?????? ????????????");
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void WriteLocation(UserInfo user) {
        //Write data on DB
        mDatabase.child(UserID).setValue(user); //child: ????????? ?????? ??????????????? ?????????
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void ReadLocation() {
        //Read data from DB
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ////1. ???????????? ?????? ????????? ????????? ???????????? ??????////
//                for(int i=0; i<mLeDeviceListAdapter.getCount();i++){
//                    String id = (String) mLeDeviceListAdapter.getItem(i);
//                    //Latitude ????????????
//                    double readLatitude = (double) snapshot.child("users").child(id).child("Latitude").getValue();
//                    //Longitude ????????????
//                    double readLongitude = (double) snapshot.child("users").child(id).child("Latitude").getValue();
//                    otherMarker = map.addMarker(new MarkerOptions().position(new LatLng(readLatitude, readLongitude)).title("other Location").icon(BitmapDescriptorFactory.fromResource((R.drawable.otherlocation))));
//                }
                /////////////////////////////////////////////

                ////2. ??? ???????????? ????????? ???????????? ?????? ??????////
                //?????? ?????? ??????, ???????????? ????????? ????????? ??????????????? ???????????? ??????
                for(DataSnapshot messageData:snapshot.getChildren()) {
                    String userID = messageData.getKey().toString();
                    if(!userID.equals(UserID)){
                        double readLatitude = (double) snapshot.child(userID).child("Latitude").getValue();
                        double readLongitude = (double) snapshot.child(userID).child("Longitude").getValue();
                        UserInfo user = new UserInfo(readLatitude, readLongitude);
                        Log.i("DatabaseTest", userID+" "+readLatitude+" "+readLongitude);
                        double myLatitude = (double) snapshot.child(UserID).child("Latitude").getValue();
                        double myLongitude = (double) snapshot.child(UserID).child("Longitude").getValue();

                        //GPS??? ?????? ??????
                       double distance = GPSDistance(myLatitude, myLongitude, readLatitude, readLongitude);

                        //Bluetooth??? ?????? ??????: 2m ?????? ????????? ??????
                        scanLeDevice(true);
                        boolean Check2M;
                        String CheckMessage;
                        if(RssiHash.get(userID)==null){
                            Check2M=false;
                            CheckMessage="???????????? ?????? ??????";
                        }
                        else{
                            Check2M = RssiDistanceLess2m(RssiHash.get(userID));
                            if (Check2M==true){
                                CheckMessage="2m ????????? ????????????.";
                                Toast t = Toast.makeText(getApplicationContext(), "2m ????????? ???????????????", Toast.LENGTH_SHORT);
                                t.show();
                            }
                            else{
                                CheckMessage="2m ????????? ????????????.";
                            }
                        }
                        String rssimessage = "";
                        for(Map.Entry<String, Integer> elem:RssiHash.entrySet()){
                            if (elem.getValue()>=-67){
                                rssimessage += "key: ";
                                rssimessage+=elem.getKey();
                                rssimessage+=" value: ";
                                rssimessage+=elem.getValue().toString();
                                rssimessage+="\n";
                                //2m ????????? toast??? ????????????
                                Toast t = Toast.makeText(getApplicationContext(), "2m ????????? ???????????????", Toast.LENGTH_SHORT);
                                t.show();
                            }
                        }
                        textView2.setText(rssimessage);
                        Marker marker = map.addMarker(new MarkerOptions().position(new LatLng(readLatitude,readLongitude)).title("other Location").snippet("2m ??????:"+CheckMessage+"\n??????(m):"+distance).icon(BitmapDescriptorFactory.fromResource((R.drawable.otherlocation))));

                        map.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

                            @Override
                            public View getInfoWindow(Marker marker) {
                                return null;
                            }

                            @Override
                            public View getInfoContents(Marker marker) {
                                LinearLayout info = new LinearLayout(context_main);
                                info.setOrientation(LinearLayout.VERTICAL);

                                TextView title = new TextView(context_main);
                                title.setTextColor(Color.BLACK);
                                title.setGravity(Gravity.CENTER);
                                title.setTypeface(null, Typeface.BOLD);
                                title.setText(marker.getTitle());

                                TextView snippet = new TextView(context_main);
                                snippet.setTextColor(Color.GRAY);
                                snippet.setGravity(Gravity.LEFT);
                                snippet.setText(marker.getSnippet());

                                info.addView(title);
                                info.addView(snippet);

                                return info;
                            }
                        });


                        //Marker marker = map.addMarker(new MarkerOptions().position(new LatLng(readLatitude,readLongitude)).title("other Location").snippet("??????(m):"+distance).icon(BitmapDescriptorFactory.fromResource((R.drawable.otherlocation))));
                        if(MarkerHash.get(userID)!=null){
                            MarkerHash.get(userID).remove();
                            MarkerHash.replace(userID, marker);
                        }
                        else{
                            MarkerHash.put(userID, marker);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

        //??????????????? ???????????? ????????? ???????????? ????????????
//        for(String SearchUser : MarkerHash.keySet()){
//            //Log.i("keyset", Hash.keySet().toString() + UserID);
//            if(!SearchUser.equals(UserID)){
//                UserInfo user = UserHash.get(SearchUser);
//                Marker tempMarker = map.addMarker(new MarkerOptions().position(new LatLng(user.Latitude,user.Longitude)).title("other Location"+user.Latitude+user.Longitude).icon(BitmapDescriptorFactory.fromResource((R.drawable.otherlocation))));
//                MarkerHash.replace(SearchUser,tempMarker);
//            }
//        }
    }

    class GPSListener implements LocationListener {
        // ?????? ?????????????????? ???????????? ????????? (???????????? and ????????????)
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onLocationChanged(Location location) {
            if (myMarker != null) {
                myMarker.remove();
            }
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            String message = "??? ????????? Latitude : " + latitude + "\nLongtitude : " + longitude;
            textView1.setText(message);
            //????????????????????? ?????? ??????
            UserInfo user = new UserInfo(latitude, longitude);
            WriteLocation(user);
            myMarker = map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("my Location").icon(BitmapDescriptorFactory.fromResource((R.drawable.mylocation))));
            showCurrentLocation(latitude, longitude);
            ReadLocation();
            Log.i("MyLocTest", "onLocationChanged() ?????????????????????.");
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onResume() {
        super.onResume();
        // GPS provider??? ???????????? ????????? ??????
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    Activity#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for Activity#requestPermissions for more details.
            Toast.makeText(getApplicationContext(), "?????? ????????? ????????????.", Toast.LENGTH_SHORT).show();
            return;
        } else {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpsListener);
                //manager.removeUpdates(gpsListener);
            } else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, gpsListener);
                //manager.removeUpdates(gpsListener);
            }
            if (map != null) {
                map.setMyLocationEnabled(true); // ??????????????? ????????? ?????? ??? ??? ?????? ?????? ?????????
            }
            Log.i("MyLocTest", "onResume?????? requestLocationUpdates() ???????????????.");
        }


//        if (!mBluetoothAdapter.isEnabled()) {
//            // mBluetoothAdapter.isEnabled ??? '!' ??????, ( ???????????? ?????? )
//            // ???????????? ????????? ?????? -> ACTION_REQUEST_ENABLE ??????????????? ?????? -> startActivityForResult() ??????
//            // => App ?????? ?????? ????????? ????????? ?????? ???????????? ????????? ??????
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//        }
//
//        //???????????? ??????????????? ??????????????????!!
//        scanLeDevice(true);

    }

    @Override
    protected void onPause() {
        super.onPause();
        manager.removeUpdates(gpsListener);
        if (map != null) {
            map.setMyLocationEnabled(false); // ??????????????? ????????? ??? ??? ?????? ?????? ????????????
        }
        Log.i("MyLocTest", "onPause?????? removeUpdates() ???????????????.");
    }

    private void showCurrentLocation(double latitude, double longitude) {
        LatLng curPoint = new LatLng(latitude, longitude);
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(curPoint, 19));
        CreateCircle(curPoint);
    }

    private void CreateCircle(LatLng curPoint) {
        // ????????????
        if (circle2M == null) {
            circle2M = new CircleOptions().center(curPoint) // ??????
                    .radius(2)       // ????????? ?????? : m
                    .strokeWidth(1.0f);    // ????????? 0f : ?????????
            //.fillColor(Color.parseColor("#1AFFFFFF")); // ?????????
            circle = map.addCircle(circle2M);
        } else {
            circle.remove(); // ????????????
            circle2M.center(curPoint);
            circle = map.addCircle(circle2M);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            Log.v(TAG, "?????? ??????");

            // @jun
            // ????????? SCAN_PERIOD??? ????????? 10000 millisec ??? ??????????????? ????????? stopLeScan -> ?????? ??????
            mHandler.postDelayed(new Runnable() {
                @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            Log.v(TAG, "?????? ??????");
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }


    private BluetoothAdapter.LeScanCallback mLeScanCallback  =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            RssiHash.put(device.getAddress(), rssi);
                            Log.i("keyset", RssiHash.keySet().toString());
                        }
                    });
                }
            };

    public static double GPSDistance(double x1, double y1, double x2, double y2) {
        double distance;
        double radius = 6371; // ?????? ?????????(km)
        double toRadian = Math.PI / 180;

        double deltaLatitude = Math.abs(x1 - x2) * toRadian;
        double deltaLongitude = Math.abs(y1 - y2) * toRadian;

        double sinDeltaLat = Math.sin(deltaLatitude / 2);
        double sinDeltaLng = Math.sin(deltaLongitude / 2);
        double squareRoot = Math.sqrt(
                sinDeltaLat * sinDeltaLat +
                        Math.cos(x1 * toRadian) * Math.cos(x2 * toRadian) * sinDeltaLng * sinDeltaLng);

        distance = 2 * radius * Math.asin(squareRoot);

        return distance*1000;
    }

//    public static double RssiDistance(int rssi){ //????????? ?????? ????????? ????????? ??????????????? ???????????? ????????????
//        if (rssi == 0) {
//            return -1.0; // if we cannot determine distance, return -1.
//        }
//
//        double ratio = rssi*1.0/txPower;
//        if (ratio < 1.0) {
//            return Math.pow(ratio,10);
//        }
//        else {
//            double accuracy =  (0.89976)*Math.pow(ratio,7.7095) + 0.111;
//            return accuracy;
//        }
//        double distance = 0;
//        return distance;
//    }

    public static boolean RssiDistanceLess2m(int rssi){
        if(rssi>=-67 &&rssi!=0){
            return true;
        }
        else{
            return false;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        AutoPermissions.Companion.parsePermissions(this, requestCode, permissions, this);
        Toast.makeText(this, "requestCode : "+requestCode+"  permissions : "+permissions+"  grantResults :"+grantResults, Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onDenied(int requestCode, String[] permissions) {
        Toast.makeText(getApplicationContext(),"permissions denied : " + permissions.length, Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onGranted(int requestCode, String[] permissions) {
        Toast.makeText(getApplicationContext(),"permissions granted : " + permissions.length, Toast.LENGTH_SHORT).show();
    }
}