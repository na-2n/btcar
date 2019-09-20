/*
 *     Copyright (C) 2019  Yui
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pet.yui.btcar;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.UUID;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class ControllerActivity extends AppCompatActivity {
    private static final String TAG = "ControllerActivity";
    private static final int INTERVAL = 500;

    private CommandSender sender;
    private BluetoothAdapter bt;
    private InputMethodManager imm;
    private Spinner btDeviceSpinner;
    private BluetoothSocket btSocket;
    private BluetoothDevice btDevice;
    private BluetoothDevice[] btDevices;
    private int deadzone;
    private boolean centerLock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        sender = new CommandSender();
        bt = BluetoothAdapter.getDefaultAdapter();
        imm = (InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
        btDeviceSpinner = findViewById(R.id.device_select);

        final JoystickView leftStick = findViewById(R.id.joystick_left);
        final JoystickView rightStick = findViewById(R.id.joystick_right);

        final Button refreshBtn = findViewById(R.id.refresh_list);

        final CheckBox centerLockBox = findViewById(R.id.center_lock);

        final EditText deadzoneField = findViewById(R.id.deadzone);

        centerLock = true;
        deadzone = 50;

        sender.setDeadzone(deadzone);

        deadzoneField.setText(String.valueOf(deadzone));
        centerLockBox.setChecked(true);

        genDeviceListNonBlocking();

        deadzoneField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                boolean handled = false;

                if (i == EditorInfo.IME_ACTION_DONE) {
                    String text = textView.getText().toString();

                    if (text.isEmpty()) {
                        text = "50";

                        deadzoneField.setText(text);
                    }

                    deadzone = Integer.parseInt(text);

                    sender.setDeadzone(deadzone);

                    imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);

                    handled = true;
                }

                return handled;
            }
        });

        centerLockBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                centerLock = !centerLock;

                leftStick.setAutoReCenterButton(centerLock);
                rightStick.setAutoReCenterButton(centerLock);
            }
        });

        btDeviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == 0)
                    return;

                btDevice = btDevices[i - 1];

                Log.d("BLUETOOTH", "connecting to bt");

                connectRfcommNonBlocking();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                genDeviceListNonBlocking();
            }
        });

        leftStick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                if (checkDevice())
                    sender.sendInput(CommandSender.Motor.LEFT, strength, angle);
                    //sendJoyStick(angle, strength, CommandParts.MOTOR_LEFT);
            }
        }, INTERVAL);

        rightStick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                if (checkDevice())
                    sender.sendInput(CommandSender.Motor.RIGHT, strength, angle);
                    //sendJoyStick(angle, strength, CommandParts.MOTOR_RIGHT);
            }
        }, INTERVAL);
    }

    private void connectRfcommNonBlocking() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                connectRfcomm();
            }
        }).start();
    }

    private void genDeviceListNonBlocking() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayAdapter adp = genDeviceList();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btDeviceSpinner.setAdapter(adp);
                    }
                });
            }
        }).start();
    }

    private ArrayAdapter genDeviceList() {
        btDevices = bt.getBondedDevices().toArray(new BluetoothDevice[0]);

        final String[] names = new String[btDevices.length + 1];

        names[0] = "None";

        for (int i = 0; i < btDevices.length; i++) {
            names[i + 1] = btDevices[i].getName();
        }

        ArrayAdapter nameAdapter = new ArrayAdapter<>(this,
                R.layout.support_simple_spinner_dropdown_item, names);

        nameAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        nameAdapter.notifyDataSetChanged();

        return nameAdapter;
    }

    private void connectRfcomm() {
        try {
            if (btSocket != null)
                btSocket.close();

            btSocket = btDevice.createRfcommSocketToServiceRecord(
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

            if (!btSocket.isConnected()) {
                bt.cancelDiscovery();
                btSocket.connect();
            }

            OutputStream btOutput = btSocket.getOutputStream();

            btOutput.write(0x02);

            sender.setOuput(btOutput);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ControllerActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.d("BLUETOOTH", "Could not connect to device", e);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ControllerActivity.this, "Failed to connect", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private boolean checkDevice() {
        if (btDevice == null) {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show();

            return false;
        }

        if (btSocket == null) {
            Toast.makeText(this, "Socket is null", Toast.LENGTH_SHORT).show();

            return false;
        }

        return true;
    }
}
