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

    //Map Route 에서 내 위치 사용하려고
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
    private String UserID; //맥주소로 ID 생성
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
        //Map Route 에서 내 위치 사용하려고
        context_main = this;

        super.onCreate(savedInstanceState);
        UserID = getMacAddress();
        setContentView(R.layout.activity_maps);
        setTitle("GPS 현재위치 확인하기");
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
                Log.i("MyLocTest", "지도 준비됨");
                map = googleMap;
                //map.setMyLocationEnabled(true);
                map.getUiSettings().setZoomControlsEnabled(true);
                map.getUiSettings().setZoomGesturesEnabled(true);
            }
        });
        // 최초 지도 숨김
        mapFragment.getView().setVisibility(View.GONE);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 지도 보임
                mapFragment.getView().setVisibility(View.VISIBLE);
                startLocationService();
            }
        });


        // 장치에서 Bluetooth LE 를 지원하는지 확인
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //블루투스를 지원하는지 확인
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }


        // BluetoothManager를 통해 BluetoothAdapter를 얻고 BluetoothAdapter 초기화
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // 디바이스에서 블루투스를 지원하는지 확인
        // null이면 지원하지 않는 것
        if (mBluetoothAdapter == null) {
            Log.v(TAG, "디바이스에서 블루투스를 지원하지 않음");
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
            long minTime = 0;        // 0초마다 갱신 - 바로바로갱신
            float minDistance = 0;
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location != null) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    String message = "최근 위치 Network -> Latitude : " + latitude + "\n Longitude : " + longitude;
                    UserInfo user = new UserInfo(latitude, longitude);
                    WriteLocation(user);
                    textView1.setText(message);
                    if (myMarker!=null){
                        myMarker.remove();
                    }
                    myMarker = map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("my Location").icon(BitmapDescriptorFactory.fromResource((R.drawable.mylocation))));
                    showCurrentLocation(latitude, longitude);
                    Log.i("MyLocTest", "최근 위치 Network 호출");
                }
                //위치 요청하기
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, minDistance, gpsListener);
                //manager.removeUpdates(gpsListener);
                Toast.makeText(getApplicationContext(), "내 위치 network 확인 요청함", Toast.LENGTH_SHORT).show();
                Log.i("MyLocTest", "requestLocationUpdates() 내 위치 network에서 호출시작 ~~ ");
            } else if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location != null) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    String message = "최근 위치 GPS -> Latitude : " + latitude + "\n Longitude : " + longitude;
                    //데이터베이스에 위치 저장
                    UserInfo user = new UserInfo(latitude, longitude);
                    WriteLocation(user);
                    textView1.setText(message);
                    if (myMarker!=null){
                        myMarker.remove();
                    }
                    myMarker = map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("my Location").icon(BitmapDescriptorFactory.fromResource((R.drawable.mylocation))));
                    showCurrentLocation(latitude, longitude);
                    Log.i("MyLocTest", "최근 위치 GPS 호출");
                }
                //위치 요청하기
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDistance, gpsListener);
                //manager.removeUpdates(gpsListener);
                Toast.makeText(getApplicationContext(), "내 위치 GPS 확인 요청", Toast.LENGTH_SHORT).show();
                Log.i("MyLocTest", "requestLocationUpdates() 내 위치 GPS에서 호출시작");
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void WriteLocation(UserInfo user) {
        //Write data on DB
        mDatabase.child(UserID).setValue(user); //child: 아래에 노드 추가하면서 저장됨
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void ReadLocation() {
        //Read data from DB
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ////1. 블루투스 신호 잡히는 기기의 데이터만 마커////
//                for(int i=0; i<mLeDeviceListAdapter.getCount();i++){
//                    String id = (String) mLeDeviceListAdapter.getItem(i);
//                    //Latitude 가져오기
//                    double readLatitude = (double) snapshot.child("users").child(id).child("Latitude").getValue();
//                    //Longitude 가져오기
//                    double readLongitude = (double) snapshot.child("users").child(id).child("Latitude").getValue();
//                    otherMarker = map.addMarker(new MarkerOptions().position(new LatLng(readLatitude, readLongitude)).title("other Location").icon(BitmapDescriptorFactory.fromResource((R.drawable.otherlocation))));
//                }
                /////////////////////////////////////////////

                ////2. 앱 사용중인 기기의 데이터를 모두 마커////
                //초기 마커 설정, 아이디와 각각의 마커를 해시맵으로 생성하여 관리
                for(DataSnapshot messageData:snapshot.getChildren()) {
                    String userID = messageData.getKey().toString();
                    if(!userID.equals(UserID)){
                        double readLatitude = (double) snapshot.child(userID).child("Latitude").getValue();
                        double readLongitude = (double) snapshot.child(userID).child("Longitude").getValue();
                        UserInfo user = new UserInfo(readLatitude, readLongitude);
                        Log.i("DatabaseTest", userID+" "+readLatitude+" "+readLongitude);
                        double myLatitude = (double) snapshot.child(UserID).child("Latitude").getValue();
                        double myLongitude = (double) snapshot.child(UserID).child("Longitude").getValue();

                        //GPS로 거리 측정
                       double distance = GPSDistance(myLatitude, myLongitude, readLatitude, readLongitude);

                        //Bluetooth로 거리 측정: 2m 이내 여부만 판별
                        scanLeDevice(true);
                        boolean Check2M;
                        String CheckMessage;
                        if(RssiHash.get(userID)==null){
                            Check2M=false;
                            CheckMessage="블루투스 측정 불가";
                        }
                        else{
                            Check2M = RssiDistanceLess2m(RssiHash.get(userID));
                            if (Check2M==true){
                                CheckMessage="2m 이내에 있습니다.";
                                Toast t = Toast.makeText(getApplicationContext(), "2m 거리를 유지하세요", Toast.LENGTH_SHORT);
                                t.show();
                            }
                            else{
                                CheckMessage="2m 이내에 없습니다.";
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
                                //2m 여부를 toast로 알려주기
                                Toast t = Toast.makeText(getApplicationContext(), "2m 거리를 유지하세요", Toast.LENGTH_SHORT);
                                t.show();
                            }
                        }
                        textView2.setText(rssimessage);
                        Marker marker = map.addMarker(new MarkerOptions().position(new LatLng(readLatitude,readLongitude)).title("other Location").snippet("2m 여부:"+CheckMessage+"\n거리(m):"+distance).icon(BitmapDescriptorFactory.fromResource((R.drawable.otherlocation))));

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


                        //Marker marker = map.addMarker(new MarkerOptions().position(new LatLng(readLatitude,readLongitude)).title("other Location").snippet("거리(m):"+distance).icon(BitmapDescriptorFactory.fromResource((R.drawable.otherlocation))));
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

        //해시맵에서 아이디를 하나씩 가져와서 업데이트
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
        // 위치 확인되었을때 자동으로 호출됨 (일정시간 and 일정거리)
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onLocationChanged(Location location) {
            if (myMarker != null) {
                myMarker.remove();
            }
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            String message = "내 위치는 Latitude : " + latitude + "\nLongtitude : " + longitude;
            textView1.setText(message);
            //데이터베이스에 위치 저장
            UserInfo user = new UserInfo(latitude, longitude);
            WriteLocation(user);
            myMarker = map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("my Location").icon(BitmapDescriptorFactory.fromResource((R.drawable.mylocation))));
            showCurrentLocation(latitude, longitude);
            ReadLocation();
            Log.i("MyLocTest", "onLocationChanged() 호출되었습니다.");
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
        // GPS provider를 이용전에 퍼미션 체크
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    Activity#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for Activity#requestPermissions for more details.
            Toast.makeText(getApplicationContext(), "접근 권한이 없습니다.", Toast.LENGTH_SHORT).show();
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
                map.setMyLocationEnabled(true); // 액티비티가 화면에 보일 때 내 위치 표시 활성화
            }
            Log.i("MyLocTest", "onResume에서 requestLocationUpdates() 되었습니다.");
        }


//        if (!mBluetoothAdapter.isEnabled()) {
//            // mBluetoothAdapter.isEnabled 이 '!' 라면, ( 비활성화 상태 )
//            // 블루투스 활성화 요청 -> ACTION_REQUEST_ENABLE 작업인텐트 사용 -> startActivityForResult() 호출
//            // => App 중지 없이 시스템 설정을 통한 블루투스 활성화 요청
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//        }
//
//        //블루투스 실행시킬때 활성화시키기!!
//        scanLeDevice(true);

    }

    @Override
    protected void onPause() {
        super.onPause();
        manager.removeUpdates(gpsListener);
        if (map != null) {
            map.setMyLocationEnabled(false); // 액티비티가 중지될 때 내 위치 표시 비활성화
        }
        Log.i("MyLocTest", "onPause에서 removeUpdates() 되었습니다.");
    }

    private void showCurrentLocation(double latitude, double longitude) {
        LatLng curPoint = new LatLng(latitude, longitude);
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(curPoint, 19));
        CreateCircle(curPoint);
    }

    private void CreateCircle(LatLng curPoint) {
        // 반경추가
        if (circle2M == null) {
            circle2M = new CircleOptions().center(curPoint) // 원점
                    .radius(2)       // 반지름 단위 : m
                    .strokeWidth(1.0f);    // 선너비 0f : 선없음
            //.fillColor(Color.parseColor("#1AFFFFFF")); // 배경색
            circle = map.addCircle(circle2M);
        } else {
            circle.remove(); // 반경삭제
            circle2M.center(curPoint);
            circle = map.addCircle(circle2M);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            Log.v(TAG, "스캔 시작");

            // @jun
            // 위에서 SCAN_PERIOD로 정의된 10000 millisec 의 검색시간이 지나면 stopLeScan -> 탐색 중지
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
            Log.v(TAG, "스캔 멈춤");
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
        double radius = 6371; // 지구 반지름(km)
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

//    public static double RssiDistance(int rssi){ //정확한 거리 구하는 함수를 만들어보려 하였으나 쉽지않음
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