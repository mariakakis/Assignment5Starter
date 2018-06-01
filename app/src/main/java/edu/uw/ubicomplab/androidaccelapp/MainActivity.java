package edu.uw.ubicomplab.androidaccelapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity implements BluetoothLeUart.Callback {

    // GLOBALS
    // UI elements
    private TextView resultText;
    private TextView gesture1CountText, gesture2CountText, gesture3CountText;
    private List<Button> buttons;

    // Machine learning
    private Model model;
    private static final int GESTURE_DURATION_SECS = 1;

    // Bluetooth
    private BluetoothLeUart uart;
    private TextView messages;
    private boolean isRecentTrain;
    private String recentLabel;

    private static final String DEVICE_NAME = "Adafruit Bluefruit LE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the UI elements
        resultText = findViewById(R.id.resultText);
        gesture1CountText = findViewById(R.id.gesture1TextView);
        gesture2CountText = findViewById(R.id.gesture2TextView);
        gesture3CountText = findViewById(R.id.gesture3TextView);
        buttons = new ArrayList<>();
        buttons.add((Button) findViewById(R.id.gesture1Button));
        buttons.add((Button) findViewById(R.id.gesture2Button));
        buttons.add((Button) findViewById(R.id.gesture3Button));
        buttons.add((Button) findViewById(R.id.testButton));

        // Initialize the model
        model = new Model(this);

        // Get Bluetooth
        messages = findViewById(R.id.bluetoothText);
        messages.setMovementMethod(new ScrollingMovementMethod());
        uart = new BluetoothLeUart(getApplicationContext(), DEVICE_NAME);

        // Check permissions
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtons(false);
        uart.registerCallback(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        uart.unregisterCallback(this);
        uart.disconnect();
    }

    public void connect(View v) {
        startScan();
    }

    private void startScan(){
        writeLine("Scanning for devices ...");
        uart.connectFirstAvailable();
    }


    /**
     * Records a gesture that is GESTURE_DURATION_SECS long
     */
    public void recordGesture(View v) {
        final View v2 = v;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                v2.setEnabled(false);
            }
        });
        uart.send("start");
        Log.i("BLE-UART", "done sending start");

        // Figure out which button got pressed to determine label
        switch (v.getId()) {
            case R.id.gesture1Button:
                recentLabel = model.outputClasses[0];
                isRecentTrain = true;
                break;
            case R.id.gesture2Button:
                recentLabel = model.outputClasses[1];
                isRecentTrain = true;
                break;
            case R.id.gesture3Button:
                recentLabel = model.outputClasses[2];
                isRecentTrain = true;
                break;
            default:
                recentLabel = "?";
                isRecentTrain = false;
                break;
        }


        // Create the timer to stop data collection
        Timer endTimer = new Timer();
        TimerTask endTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        uart.send("stop");
                        Log.i("BLE-UART", "Done writing stop");
                        v2.setEnabled(true);
                    }
                });
            }
        };

        // Start the timers
//        startTimer.schedule(startTask, 0);
        endTimer.schedule(endTask, GESTURE_DURATION_SECS*1000);
    }

    /**
     * Trains the model as long as there is at least one sample per class
     */
    public void trainModel(View v) {
        // Make sure there is training data for each gesture
        for (int i=0; i<model.outputClasses.length; i++) {
            int gestureCount = model.getNumTrainSamples(i);
            if (gestureCount == 0) {
                Toast.makeText(getApplicationContext(), "Need examples for gesture" + (i+1),
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Train
        model.train();
    }

    /**
     * Resets the training data of the model
     */
    public void clearModel(View v) {
        model.resetTrainingData();
        updateTrainDataCount();
        resultText.setText("Result: ");
    }

    /**
     * Updates the text boxes that show how many samples have been recorded
     */
    public void updateTrainDataCount() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gesture1CountText.setText("Num samples: " + model.getNumTrainSamples(0));
                gesture2CountText.setText("Num samples: " + model.getNumTrainSamples(1));
                gesture3CountText.setText("Num samples: " + model.getNumTrainSamples(2));
            }
        });
    }

    /**
     * Writes a line to the messages textbox
     * @param text: the text that you want to write
     */
    private void writeLine(final CharSequence text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messages.append(text);
                messages.append("\n");
            }
        });
    }

    /**
     * Enables or disables the buttons that send messages to the BLE device
     * @param enabled: whether the buttons should be enabled or disabled
     */
    private void updateButtons(boolean enabled){
        for (Button b: buttons) {
            b.setClickable(enabled);
            b.setEnabled(enabled);
        }
    }

    /**
     * Called when a UART device is discovered (after calling startScan)
     * @param device: the BLE device
     */
    @Override
    public void onDeviceFound(BluetoothDevice device) {
        writeLine("Found device : " + device.getAddress());
        writeLine("Waiting for a connection ...");
    }

    /**
     * Prints the devices information
     */
    @Override
    public void onDeviceInfoAvailable() {
        writeLine(uart.getDeviceInfo());
    }

    /**
     * Called when UART device is connected and ready to send/receive data
     * @param uart: the BLE UART object
     */
    @Override
    public void onConnected(BluetoothLeUart uart) {
        writeLine("Connected!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateButtons(true);
            }
        });
    }

    /**
     * Called when some error occurred which prevented UART connection from completing
     * @param uart: the BLE UART object
     */
    @Override
    public void onConnectFailed(BluetoothLeUart uart) {
        writeLine("Error connecting to device!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateButtons(false);
            }
        });
    }

    /**
     * Called when the UART device disconnected
     * @param uart: the BLE UART object
     */
    @Override
    public void onDisconnected(BluetoothLeUart uart) {
        writeLine("Disconnected!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateButtons(false);
            }
        });
    }

    /**
     * Called when data is received by the UART
     * @param uart: the BLE UART object
     * @param rx: the received characteristic
     */
    @Override
    public void onReceive(BluetoothLeUart uart, BluetoothGattCharacteristic rx) {
        writeLine("Received: " + rx.getStringValue(0));

        // Predict if the recent sample is for testing
        double[] features = new double[3]; // TODO: convert your received string into a double[]
        model.addFeatures(features, recentLabel, isRecentTrain);
        if (!isRecentTrain) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String result = model.test();
                    resultText.setText("Result: "+result);
                }
            });
        }

        // Update number of samples shown
        updateTrainDataCount();
    }
}
