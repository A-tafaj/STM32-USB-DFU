/*
 * Copyright 2015 Umbrela Smart, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.umbrela.tools.stm32dfuprogrammer;

import static android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity implements
        Handler.Callback, Usb.OnUsbChangeListener, Dfu.DfuListener {
    static final int STORAGE_ACCESS_REQUEST_CODE = 10;

    private Usb usb;
    private Dfu dfu;

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dfu = new Dfu(Usb.USB_VENDOR_ID, Usb.USB_PRODUCT_ID);
        dfu.setListener(this);

        status = findViewById(R.id.status);

        Button massErase = findViewById(R.id.btnMassErase);
        massErase.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                dfu.massErase();
            }
        });

        Button program = findViewById(R.id.btnProgram);
        program.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                dfu.program();
            }
        });

        Button forceErase = findViewById(R.id.btnForceErase);
        forceErase.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                dfu.fastOperations();
            }
        });

        Button verify = findViewById(R.id.btnVerify);
        verify.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                dfu.verify();
            }
        });

        Button enterDfu = findViewById(R.id.btnEnterDFU);
        enterDfu.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                Outputs.enterDfuMode();
            }
        });

        Button leaveDfu = findViewById(R.id.btnLeaveDFU);
        leaveDfu.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                //Outputs.leaveDfuMode();
                dfu.leaveDfuMode();
            }
        });
        Button releaseReset = findViewById(R.id.btnReleaseReset);
        releaseReset.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                Outputs.enterNormalMode();
            }
        });

        handlePermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();

        /* Setup USB */
        usb = new Usb(this);
        usb.setUsbManager((UsbManager) getSystemService(Context.USB_SERVICE));
        usb.setOnUsbChangeListener(this);

        // Handle two types of intents. Device attachment and permission
        registerReceiver(usb.getmUsbReceiver(), new IntentFilter(Usb.ACTION_USB_PERMISSION));
        registerReceiver(usb.getmUsbReceiver(), new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
        registerReceiver(usb.getmUsbReceiver(), new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));


        // Handle case where USB device is connected before app launches;
        // hence ACTION_USB_DEVICE_ATTACHED will not occur so we explicitly call for permission
        usb.requestPermission(this, Usb.USB_VENDOR_ID, Usb.USB_PRODUCT_ID);
    }

    @Override
    protected void onStop() {
        super.onStop();

        /* USB */
        dfu.setUsb(null);
        usb.release();
        try {
            unregisterReceiver(usb.getmUsbReceiver());
        } catch (IllegalArgumentException e) { /* Already unregistered */ }
    }

    @Override
    public void onStatusMsg(String msg) {
        // TODO since we are appending we should make the TextView scrollable like a log
        status.append(msg);
    }

    @Override
    public boolean handleMessage(Message message) {
        return false;
    }

    @Override
    public void onUsbConnected() {
        final String deviceInfo = usb.getDeviceInfo(usb.getUsbDevice());
        status.setText(deviceInfo);
        dfu.setUsb(usb);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Toast toast;
        if (requestCode == STORAGE_ACCESS_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toast = Toast.makeText(getApplicationContext(), "Permission granted", Toast.LENGTH_LONG);
        } else  {
            toast = Toast.makeText(getApplicationContext(), "Permission not granted", Toast.LENGTH_LONG);
        }
        toast.show();
    }

    private void handlePermissions() {
        // For versions before Android 6.0, permission is granted at installation time
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Intent intent = new Intent( ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + BuildConfig.APPLICATION_ID));
                    startActivityForResult(intent, STORAGE_ACCESS_REQUEST_CODE);
                }
            } else {
                // For versions between Android 6.0 and Android 10
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_ACCESS_REQUEST_CODE);
                }
            }
        }
    }
}
