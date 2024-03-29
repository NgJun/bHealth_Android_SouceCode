package com.mcuhq.simplebluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    // GUI Components
    private TextView item;
    private TextView txtStatus;
    private TextView txtBPM;
    private TextView txtBuffer;

    private ImageView imgHeart;
    private ImageView imgBell;
    int heart = 0;
    int bell = 0;
    private Button btnFile;
    private Button btnOn;
    private Button btnOff;
    private Button btn_pair;
    private Button btnSeek;
    private BluetoothAdapter mBTAdapter;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ListView mDevicesListView;
    private CheckBox checkBox_noti;
    //Data process
    String sdata = ""; // Container data from hc06
    String ibitotal = "";
    Double Fs = 12.0;
    Double Ts = 1 / Fs;
    int check_first = 1;
    float[] ibi = new float[16];
    float[] pulse_check = new float[3];
    float IBI = 7;
    double IBI_total = 10;
    int time = 0;
    int lastTimeBeat = 0;
    int N = 0;

    private final String TAG = MainActivity.class.getSimpleName();
    private Handler mHandler; // Our main handler that will receive callback notifications
    private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier


    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names.
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgHeart = (ImageView) findViewById(R.id.iv_heart);
        imgBell  = (ImageView) findViewById(R.id.iv_bell);
        btnFile = (Button) findViewById(R.id.buttonFile);
        txtStatus = (TextView)findViewById(R.id.checkBox_notifi);
        txtStatus.setTextSize(10);
        txtBuffer = (TextView) findViewById(R.id.texthi);
        btnOn = (Button)findViewById(R.id.button_on);
        btnOff = (Button)findViewById(R.id.button_off);
        btnSeek = (Button)findViewById(R.id.button_seek);
        btn_pair = (Button)findViewById(R.id.button_pair);
        checkBox_noti = (CheckBox)findViewById(R.id.checkBox_notifi);
        txtBPM = (TextView)findViewById(R.id.textBPM);

        mBTArrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1){
            @Override
            public View getView(int position, View convertView, ViewGroup parent){
                // Get the Item from ListView
                View view = super.getView(position, convertView, parent);

                // Initialize a TextView for ListView each Item
                TextView tv = (TextView) view.findViewById(android.R.id.text1);

                // Set the text color of TextView (ListView Item)
                tv.setTextColor(Color.rgb(255, 255, 255));

                // Generate ListView Item using TextView
                return view;
            }
        };
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        mDevicesListView = (ListView)findViewById(R.id.listview_device);
        mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // Ask for location permission if not already allowed
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);


        mHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                if(msg.what == MESSAGE_READ)
                {
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    //txtBuffer.setText(readMessage);
                    //int a = Integer.valueOf(readMessage);
                    //for(int i=1; i<1000; i++)

                    // Fs = 4 samples / s
                    time++;
                    if(time > 20) {
                        txtBPM.setTextSize(40);
                        txtBPM.setText("\n  -");
                        check_first = 1;
                        IBI_total = 75.0;
                        time = 0; // reset avoid huge value
                    }

                    if(check_first == 1) {
                        pulse_check[0]=0;
                        pulse_check[1]=0;
                        pulse_check[2]=0;
                        for (int i = 0; i < ibi.length; i++) {
                            ibi[i] = IBI;
                            check_first = 0;
                        }
                    }

                    pulse_check[0] = pulse_check[1];
                    pulse_check[1] = pulse_check[2];
                    pulse_check[2] = Float.parseFloat(stripNonDigits(readMessage));
                    if( check_first == 0 && time>IBI*2/3 )
                    {
                        if( (pulse_check[1] > 65) &&  ( ((pulse_check[1]>pulse_check[0]) && (pulse_check[1] > pulse_check[2]))  || (pulse_check[1] == pulse_check[0]))) // peak detection and threshHold check
                        {

                            ibi[0] = ibi[1];
                            ibi[1] = ibi[2];
                            ibi[2] = ibi[3];
                            ibi[3] = ibi[4];
                            ibi[4] = ibi[5];
                            ibi[5] = ibi[6];
                            ibi[6] = ibi[7];
                            ibi[7] = ibi[8];
                            ibi[8] = ibi[9];

                            ibi[9] = ibi[10];
                            ibi[10] = ibi[11];
                            ibi[11] = ibi[12];
                            ibi[12] = ibi[13];
                            ibi[13] = ibi[14];
                            ibi[14] = ibi[15];
                            ibi[15] = time;
                            time=0;

                            IBI_total = (ibi[0] + ibi[1] + ibi[2] + ibi[3]+ibi[4]+ibi[5] + ibi[6] + ibi[7] + ibi[8]+ibi[9] +ibi[10] + ibi[11] + ibi[12] + ibi[13]+ibi[14]+ibi[15])*0.0625;

                            txtBuffer.setText(Double.toString(IBI_total));
                            txtBPM.setText( Integer.toString((int) Math.round(IBI_total*8.7777)) );


                        }
                    }
                        //ibitotal+= Double.toString(IBI_total) + "\n";
                        //sdata += stripNonDigits(readMessage)+"\n";


                }

                if(msg.what == CONNECTING_STATUS){
                    if(msg.arg1 == 1)
                        txtStatus.setText("Connected to Device: " + (String)(msg.obj));
                    else
                        txtStatus.setText("Connection Failed");
                }
            }
        };

        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            txtStatus.setText("Status: Bluetooth not found");
            Toast.makeText(getApplicationContext(),"Bluetooth device not found!",Toast.LENGTH_SHORT).show();
        }
        else {

            imgHeart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(heart == 0) {    // First tap on heart to turn LED on
                        mConnectedThread.write("1");
                        heart=1;
                    }
                    else if(heart == 1)
                    {                   // Second tap to turn off
                        mConnectedThread.write("0");
                        heart = 0;

                    }

                }
            });

            imgBell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(bell == 0) {    // First tap on heart to turn LED on
                        mConnectedThread.write("4");
                        bell=1;
                    }
                    else if(bell == 1)
                    {                   // Second tap to turn off
                        mConnectedThread.write("5");
                        bell = 0;

                    }
                }
            });


            checkBox_noti.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    if(mConnectedThread != null)        //First check to make sure thread created
                    mConnectedThread.write("2");    // Check box to turn LED mode on

                    if(!checkBox_noti.isChecked())
                    mConnectedThread.write("3");       // Uncheck to Turn off LED_mode
                        heart =0;

                }
            });


            btnOn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    btnOn.setTextSize(15);
                    btnOff.setTextSize(12);
                    btnFile.setTextSize(12);
                    btnSeek.setTextSize(12);
                    btn_pair.setTextSize(12);
                    bluetoothOn(v);
                }
            });

            btnOff.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    btnOn.setTextSize(12);
                    btnOff.setTextSize(15);
                    btnFile.setTextSize(12);
                    btnSeek.setTextSize(12);
                    btn_pair.setTextSize(12);
                    bluetoothOff(v);
                }
            });

            btn_pair.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v){
                    btnOn.setTextSize(12);
                    btnOff.setTextSize(12);
                    btnFile.setTextSize(12);
                    btnSeek.setTextSize(12);
                    btn_pair.setTextSize(15);
                    listPairedDevices(v);
                }
            });

            btnFile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    btnOn.setTextSize(12);
                    btnOff.setTextSize(12);
                    btnFile.setTextSize(20);
                    btnSeek.setTextSize(12);
                    btn_pair.setTextSize(12);
                    writeTofile("data", sdata);
                    writeTofile("ibitotal", ibitotal);
                }
            });


            btnSeek.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    btnOn.setTextSize(12);
                    btnOff.setTextSize(12);
                    btnFile.setTextSize(12);
                    btnSeek.setTextSize(15);
                    btn_pair.setTextSize(12);
                    discover(v);
                }
            });
        }
    }

    public  static double median(final int[] array)
    {
        int sum = 0;
        for( int i=0; i< array.length; i++) sum += array[i];
        double kq;
        kq = sum/array.length;
        return kq;


    }


    public static String stripNonDigits( final CharSequence input ) {
        int dem = 0;
        final StringBuilder sb = new StringBuilder( input.length());
        for (int i = 0; i < input.length(); i++)
        {
            final char c = input.charAt(i);
            if(dem < 5)
                if ((c > 47 && c < 58) || c == '.')
                {
                    dem++;
                    sb.append(c);
                }

        }
        return sb.toString();
    }


        private void bluetoothOn(View view){
        if (!mBTAdapter.isEnabled()) {  // if BT not open yet
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); // generate request Enable BT
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT); // send request
            txtStatus.setText("Bluetooth enabled");
            Toast.makeText(getApplicationContext(),"Bluetooth turned on",Toast.LENGTH_SHORT).show();

        }
        else{
            Toast.makeText(getApplicationContext(),"Bluetooth is already on", Toast.LENGTH_SHORT).show();
        }
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data){
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                txtStatus.setText("Enabled");
            }
            else
                txtStatus.setText("Disabled");
        }
    }

    private void bluetoothOff(View view){
        mBTAdapter.disable(); // turn off
        txtStatus.setText("Bluetooth disabled");
        Toast.makeText(getApplicationContext(),"Bluetooth turned Off", Toast.LENGTH_SHORT).show();
    }

    private void discover(View view){
        // Check if the device is already discovering
        if(mBTAdapter.isDiscovering()){
            mBTAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(),"Discovery stopped",Toast.LENGTH_SHORT).show();
        }
        else{
            if(mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear(); // clear items
                mBTAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();
                registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            }
            else{
                Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
            }
        }
    }

    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private void listPairedDevices(View view){
        mBTArrayAdapter.clear();
        mPairedDevices = mBTAdapter.getBondedDevices();
        if(mBTAdapter.isEnabled()) {
            // put it's one to the adapter
            for (BluetoothDevice device : mPairedDevices) {
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }

            Toast.makeText(getApplicationContext(), "Showed Paired Devices", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            if(!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                return;
            }

            txtStatus.setText("Connecting...");
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0,info.length() - 17);

            // Spawn a new thread to avoid blocking the GUI one
            new Thread()
            {
                public void run() {
                    boolean fail = false;

                    BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

                    try {
                        mBTSocket = createBluetoothSocket(device);
                    } catch (IOException e) {
                        fail = true;
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }
                    // Establish the Bluetooth socket connection.
                    try {
                        mBTSocket.connect();
                    } catch (IOException e) {
                        try {
                            fail = true;
                            mBTSocket.close();
                            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                    .sendToTarget();
                        } catch (IOException e2) {
                            //insert code to deal with this
                            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    if(fail == false) {
                        mConnectedThread = new ConnectedThread(mBTSocket);
                        mConnectedThread.start();

                        mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                                .sendToTarget();
                    }
                }
            }.start();
        }
    };

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BTMODULEUUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
        }
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
//        public  char receiveData(BluetoothSocket socket) throws IOException{
//            byte[] buffer = new byte[1];
//            ByteArrayInputStream input = new ByteArrayInputStream(buffer);
//            InputStream inputStream = socket.getInputStream();
//            inputStream.read(buffer);
//            return (char) input.read();
//
//
//        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if(bytes != 0) {
                        buffer = new byte[1024];
                        SystemClock.sleep(100); //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available(); // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget(); // Send the obtained bytes to the UI activity
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    // SenData_hc06_.txt
    public void writeTofile(String fileName, String data)
    {

        final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + "/bhealth_/" );

        //Toast.makeText(getApplicationContext(),"File 1:" +path.toString(),Toast.LENGTH_LONG).show();
        if(!path.exists())
        {
            path.mkdirs();
        }
        final File file = new File(path + fileName + ".txt");
        // Ex:\Dowload\SenData_hc06.txt
        //Toast.makeText(getApplicationContext(),"File 2:" +file.toString(),Toast.LENGTH_LONG).show();
        try
        {
            file.createNewFile();
            FileOutputStream fOut = new FileOutputStream(file);

            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            myOutWriter.append(data);
            Toast.makeText(getApplicationContext(),"ok :" + file.toString(),Toast.LENGTH_LONG).show();
            myOutWriter.close();
            fOut.flush();
            fOut.close();
        }
        catch (IOException e)
        {
            //Toast.makeText(getApplicationContext(),"Notice: " + e.toString(),Toast.LENGTH_LONG).show();
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

}
