/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.hoho.android.usbserial.examples;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.ProtocolFactory;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static com.hoho.android.usbserial.util.HexDump.hexStringToByteArray;
import static com.hoho.android.usbserial.util.HexDump.toHexString;

/**
 * Monitors a single {@link UsbSerialPort} instance, showing all data
 * received.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class SerialConsoleActivity extends AppCompatActivity {

    private final String TAG = SerialConsoleActivity.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.llb.USB_PERMISSION";
    private final int PROTOCOL_DLT97  = 1;
    private final int PROTOCOL_DLT07  = 2;
    private final int PROTOCOL_MODBUS = 3;
    private final int DLTMSG_VA = 1;
    private final int DLTMSG_CA = 2;
    private final int DLTMSG_P  = 3;
    private final int DLTMSG_E  = 4;
    private final int DLTMSG_EP = 5;
    private final int DLTMSG_CUST = 255;
    private final String PARITY_S[]={"NONE","ODD","EVEN"};
    private static final int MSG_SEND_SCHEDULE = 1;

    private TextView mDumpTextView;
    private CheckBox chkHexTx;
    private CheckBox chkHexRx;
    private CheckBox chkAddSuffix;
    private CheckBox chkSendPer;
    private EditText editCMD;
    private Button btnSend;
    private ImageButton btnAddCMD;
    private ImageButton btnDelCMD;
    private RadioGroup rgProtocol;
    private ListView cmdListView;
    private Toolbar mToolbar;
    private LinearLayout mProtocolLayout;
    private LinearLayout mDLT645Layout;
    private LinearLayout mModbusLayout;
    private Button btnVA;
    private Button btnCA;
    private Button btnP;
    private Button btnE;
    private Button btnEpeak;
    private Button btnSendDLT;
    private CheckBox chkDecode;
    private EditText editAddr;
    private EditText editDataID;
    private EditText editCode;
    private EditText editReg;
    private EditText editRegNum;
    private Button   btnSendModbus;
    private boolean isSendScheduled = false;
    private boolean isDecode = false;
    private ArrayAdapter<CustomSerialCMD> cmdListAdapter;
    private SharedPreferences msp;
    private boolean needSaveCMD = false;
    private int mCurProcol = PROTOCOL_DLT07;

    private List<CustomSerialCMD> cmdList = new ArrayList<>();
    private int mCurCMDIndex = 0;

    //滑动监测
    private MyGestureDetector mDector;
    private GestureDetector gestureDetector;

    private boolean isHexTx = false;
    private boolean isHexRx = false;
    private boolean isAddSuffix = false;
    private boolean isOpen = false;
    private int mBandRate = 9600;
    private int mParity = UsbSerialPort.PARITY_NONE;
    private boolean isRxNewLine = true;

    private static UsbSerialPort sPort = null;
    private UsbManager mUsbManagerger;
    private BroadcastReceiver mUsbReceiver;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager;
    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

        @Override
        public void onRunError(Exception e) {
            Log.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data) {
                SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        SerialConsoleActivity.this.updateReceivedData(data);
                    }
                });
        }
    };
    //定时发送
    private int mScheduledTimeMs = 1000;
    private byte[] mLastMSG = null;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what) {
                case MSG_SEND_SCHEDULE:
                    if(isSendScheduled) {
                        send2Port(mLastMSG);
                        //mHandler.sendEmptyMessageDelayed(MSG_SEND_SCHEDULE, mScheduledTimeMs);
                    }
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.serial_console);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        chkHexTx =(CheckBox) findViewById(R.id.checkBoxHexTx);
        chkHexRx =(CheckBox) findViewById(R.id.checkBoxHexRx);
        chkAddSuffix = (CheckBox) findViewById(R.id.checkBoxAddSuffix);
        chkSendPer = findViewById(R.id.checkBoxSendPer);
        editCMD = (EditText)findViewById(R.id.editCMD);
        btnSend = (Button)findViewById(R.id.btnSend);
        btnAddCMD = findViewById(R.id.btnAddCmd);
        btnDelCMD = findViewById(R.id.btnDelCmd);
        cmdListView = findViewById(R.id.cmdlistv);
        rgProtocol = findViewById(R.id.rg_protocol);
        mProtocolLayout = findViewById(R.id.layout_protocol);
        mDLT645Layout = findViewById(R.id.layout_dlt);
        mModbusLayout = findViewById(R.id.layout_modbus);

        btnVA = findViewById(R.id.btnVoltage);
        btnCA = findViewById(R.id.btnCurrent);
        btnP = findViewById(R.id.btnPower);
        btnE = findViewById(R.id.btnEnergy);
        btnEpeak = findViewById(R.id.btnPeakEnergy);
        btnSendDLT = findViewById(R.id.btnSendDLT);
        chkDecode = findViewById(R.id.checkBox_Decode);
        editAddr = findViewById(R.id.editAddr);
        editDataID = findViewById(R.id.editCustomCMD);
        editCode = findViewById(R.id.editModbusCode);
        editReg = findViewById(R.id.editModbusReg);
        editRegNum = findViewById(R.id.editModbusNumVal);
        btnSendModbus = findViewById(R.id.btnSendModbus);

        chkHexTx.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isHexTx = isChecked;
            }
        });

        chkHexRx.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isHexRx = isChecked;
            }
        });
        chkAddSuffix.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isAddSuffix = isChecked;
            }
        });
        chkSendPer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                isSendScheduled = isChecked;
            }
        });
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mScheduledTimeMs = 1000;
                send2Port(editCMD.getText().toString(),isHexTx,isAddSuffix);
            }
        });

        btnAddCMD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addAnCMDOnList();
                needSaveCMD = true;
            }
        });
        btnDelCMD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mCurCMDIndex >= cmdList.size()){
                    mCurCMDIndex = 0;
                    return;
                }
                cmdList.remove(mCurCMDIndex);
                if(mCurCMDIndex > 0){
                    mCurCMDIndex--;
                }
                cmdListAdapter.notifyDataSetChanged();
                needSaveCMD = true;
            }
        });
        rgProtocol.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch (i){
                    case R.id.rb_97:
                        mCurProcol = PROTOCOL_DLT97;
                        break;
                    case R.id.rb_07:
                        mCurProcol = PROTOCOL_DLT07;
                        break;
                    case R.id.rb_modbus:
                        mCurProcol = PROTOCOL_MODBUS;
                        break;
                }
                convertProtocolView();
            }
        });
        btnVA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendDLTMSG(DLTMSG_VA);
            }
        });
        btnCA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendDLTMSG(DLTMSG_CA);
            }
        });
        btnP.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendDLTMSG(DLTMSG_P);
            }
        });
        btnE.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendDLTMSG(DLTMSG_E);
            }
        });
        btnEpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendDLTMSG(DLTMSG_EP);
            }
        });
        btnSendDLT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendDLTMSG(DLTMSG_CUST);
            }
        });
        btnSendModbus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendModbus();
            }
        });
        chkDecode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isDecode = isChecked;
            }
        });
        mToolbar = findViewById(R.id.toolbar);
        mToolbar.setTitle("SerialTools");
        mToolbar.setSubtitle("9600:NONE");
        setSupportActionBar(mToolbar);
        convertProtocolView();
        //usb广播注册
        mUsbManagerger = (UsbManager)getSystemService(Context.USB_SERVICE);
        mUsbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                    closePort();
                    Toast.makeText(context, "Device has been removed", Toast.LENGTH_SHORT).show();
                }
            }
        };
        registerReceiver(mUsbReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));

        //命令列表
        msp = getSharedPreferences("appinfo",MODE_PRIVATE);
        FillCmdList();
        if(cmdList.size()==0){
            cmdList.add(new CustomSerialCMD("AT+CSQ",null,false,true,0));
            cmdList.add(new CustomSerialCMD("AT+CGATT?",null,false,true,0));
            cmdList.add(new CustomSerialCMD("AT+CEREG?",null,false,true,0));
            cmdList.add(new CustomSerialCMD("AT+NMSTATUS?",null,false,true,0));
            cmdList.add(new CustomSerialCMD("AT+NRB",null,false,true,0));
        }
        cmdListAdapter = new CmdListArrayAdapter(this,android.R.layout.simple_list_item_1,cmdList);
        cmdListView.setAdapter(cmdListAdapter);
        cmdListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mCurCMDIndex = i;
                CustomSerialCMD scmd = cmdList.get(i);
                if(scmd.getCmd().length()>0) {
                    mScheduledTimeMs = scmd.getPeriod();
                    send2Port(scmd.getCmd(), scmd.isHex(), scmd.isAddCR());
                }
            }
        });
        cmdListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                modifyCMDInfo(i);
                cmdListAdapter.notifyDataSetChanged();
                needSaveCMD = true;
                return true;
            }
        });
        //滑动监测
        mDector = new MyGestureDetector();
        gestureDetector = new GestureDetector(this,mDector);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu,menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.band1200:
                setPortParameters(1200,mParity);
                break;
            case R.id.band2400:
                setPortParameters(2400,mParity);
                break;
            case R.id.band4800:
                setPortParameters(4800,mParity);
                break;
            case R.id.band9600:
                setPortParameters(9600,mParity);
                break;
            case R.id.band115200:
                setPortParameters(115200,mParity);
                break;
            case R.id.parityNone:
                setPortParameters(mBandRate,UsbSerialPort.PARITY_NONE);
                break;
            case R.id.parityEven:
                setPortParameters(mBandRate,UsbSerialPort.PARITY_EVEN);
                break;
            case R.id.parityOdd:
                setPortParameters(mBandRate,UsbSerialPort.PARITY_ODD);
            case R.id.btnOpenOnTool:
                if(!isOpen){
                    openPort();
                }else{
                    closePort();
                }
                break;
            case R.id.btnClearOnTool:
                mDumpTextView.setText("");
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        closePort();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
        if(needSaveCMD) {
            SaveCmdContent();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        //openPort();
    }

    private void convertProtocolView()
    {
        switch(mCurProcol){
            case PROTOCOL_DLT97:
            case PROTOCOL_DLT07:
                mModbusLayout.setVisibility(View.GONE);
                mDLT645Layout.setVisibility(View.VISIBLE);
                break;
            case PROTOCOL_MODBUS:
                mDLT645Layout.setVisibility(View.GONE);
                mModbusLayout.setVisibility(View.VISIBLE);
                break;
        }
    }
    private void sendDLTMSG(int cmd)
    {
        String macAddr = editAddr.getText().toString().replace(" ","");
        if(macAddr.length()!=12){
            Toast.makeText(SerialConsoleActivity.this, "电表地址错误", Toast.LENGTH_SHORT).show();
        }
        int idLen = 0;
        if(mCurProcol == PROTOCOL_DLT07){
            idLen = 8;
        }else if(mCurProcol == PROTOCOL_DLT97){
            idLen = 4;
        }else{
            return;
        }
        String dltID=null;
        switch (cmd){
            case DLTMSG_VA:
                if(mCurProcol == PROTOCOL_DLT07){
                    dltID = ProtocolFactory.VA07;
                }else{
                    dltID = ProtocolFactory.VA97;
                }
                break;
            case DLTMSG_CA:
                if(mCurProcol == PROTOCOL_DLT07){
                    dltID = ProtocolFactory.CA07;
                }else {
                    dltID = ProtocolFactory.CA97;
                }
                break;
            case DLTMSG_P:
                if(mCurProcol == PROTOCOL_DLT07){
                    dltID = ProtocolFactory.P07;
                }else{
                    dltID = ProtocolFactory.P97;
                }
                break;
            case DLTMSG_E:
                if(mCurProcol == PROTOCOL_DLT07){
                    dltID = ProtocolFactory.E07;
                }else {
                    dltID = ProtocolFactory.E97;
                }
                break;
            case DLTMSG_EP:
                if(mCurProcol == PROTOCOL_DLT07){
                    dltID = ProtocolFactory.EP07;
                }else {
                    dltID = ProtocolFactory.EP97;
                }
                break;
            case DLTMSG_CUST:
                dltID=editDataID.getText().toString().replace(" ","");
                break;
        }
        if(dltID.length() != idLen) {
            Log.d(TAG,"SendDLT645,dlt len:"+dltID.length()+"idLen:"+idLen);
            Toast.makeText(SerialConsoleActivity.this, "自定义命令错误", Toast.LENGTH_SHORT).show();
        }

        mScheduledTimeMs = 1000;
        byte[] msg=null;
        msg = ProtocolFactory.BuildDLT645(editAddr.getText().toString(),dltID);
        if(msg!=null){
            Log.d(TAG,"SendDLT645:"+ HexDump.toHexString(msg));
            send2Port(msg);
        }
    }
    private void sendModbus()
    {
        String addr = editAddr.getText().toString().replace(" ","");
        String code = editCode.getText().toString().replace(" ","");
        String reg = editReg.getText().toString().replace(" ","");
        String regVal = editRegNum.getText().toString().replace(" ","");
        if(TextUtils.isEmpty(addr)||!HexDump.isHexString(addr)) {
            Toast.makeText(SerialConsoleActivity.this, "地址错误", Toast.LENGTH_SHORT).show();
            return;
        }
        if(TextUtils.isEmpty(code)||!HexDump.isHexString(code)) {
            Toast.makeText(SerialConsoleActivity.this, "功能码错误", Toast.LENGTH_SHORT).show();
            return;
        }
        if(TextUtils.isEmpty(reg)||!HexDump.isHexString(reg)) {
            Toast.makeText(SerialConsoleActivity.this, "寄存器错误", Toast.LENGTH_SHORT).show();
            return;
        }
        if(TextUtils.isEmpty(regVal)||!HexDump.isHexString(regVal)) {
            Toast.makeText(SerialConsoleActivity.this, "值错误", Toast.LENGTH_SHORT).show();
            return;
        }
        mScheduledTimeMs = 1000;
        byte[] msg = ProtocolFactory.BuildModbus(addr,code,reg,regVal,false);
        if(msg!=null){
            Log.d(TAG,"SendModbus:"+ HexDump.toHexString(msg));
            send2Port(msg);
        }
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }
    private void updataTextView(String msg)
    {
        mDumpTextView.append(msg);
        int offset = mDumpTextView.getLineCount()*mDumpTextView.getLineHeight();
        int to = offset - mDumpTextView.getHeight();
        if(to > 0) {
            mDumpTextView.scrollTo(0, to);
        }
    }
    private void updateReceivedData(byte[] data) {
        String message;
        if(isHexRx) {
//            message = "Rx " + data.length + " bytes(hex): \n" + toHexString(data) + "\n";
            message = toHexString(data);
        }else{
//            message = "Rx " + data.length + " bytes(string): \n" + new String(data) + "\n";
            message = new String(data);
        }
        if(isRxNewLine){
            message+="\n";
        }
        updataTextView(message);
    }

    private void send2Port(byte[] tx)
    {
        if(!isOpen){
            Log.d(TAG,"send faild,port is not open");
            updataTextView("Port is not open\n");
            return;
        }
        if(tx != null) {
            try {
                mLastMSG = tx;
                sPort.write(tx, 1000);
            } catch (IOException x) {
            }
        }
        if(isSendScheduled&&mScheduledTimeMs!=0){
            mHandler.sendEmptyMessageDelayed(MSG_SEND_SCHEDULE,mScheduledTimeMs);
        }
    }

    private void send2Port(String data,boolean isHex,boolean addsuffix)
    {
        if(addsuffix){
            data = data+"\r\n";
        }
        byte[] tx;
        if(isHex) {
            tx = hexStringToByteArray(data);
        }
        else {
            tx = data.getBytes();
        }
        send2Port(tx);
    }

    /**
     * Starts the activity, using the supplied driver instance.
     *
     */
    private void openPort()
    {
        if(isOpen) {
            return;
        }
        if (sPort == null) {
            sPort = getSerialPort();
        }
        if (sPort == null) {
            updataTextView("NO Device Connect\n");
        } else {
            //try to get permisson
            if(!mUsbManagerger.hasPermission(sPort.getDriver().getDevice())) {
                BroadcastReceiver usbPermissonReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        unregisterReceiver(this);
                        synchronized (this) {
                            if(!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                sPort = null;
                            }else{
                                openPort();
                            }
                        }
                    }
                };
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                registerReceiver(usbPermissonReceiver, filter); //注册广播
                PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION),0);
                mUsbManagerger.requestPermission(sPort.getDriver().getDevice(),permissionIntent);
            }
            if(sPort == null) {
                Toast.makeText(this,"Permission denied",Toast.LENGTH_SHORT).show();
                return;
            }
            UsbDeviceConnection connection = mUsbManagerger.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                Log.d(TAG, "Connection null,maybe no permission");
            }else {
                try {
                    sPort.open(connection);
                    sPort.setParameters(mBandRate, 8, UsbSerialPort.STOPBITS_1, mParity);
                    isOpen = true;
                } catch (IOException e) {
                    Log.e(TAG, "ERR when open port: " + e.getMessage(), e);
                    try {
                        sPort.close();
                    } catch (IOException e2) {
                    }
                    sPort = null;
                }
            }
        }
        onDeviceStateChange();
        if(isOpen) {
            mToolbar.getMenu().findItem(R.id.btnOpenOnTool).setIcon(R.drawable.open);
        }
    }
    private void closePort()
    {
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                Log.e(TAG,"ERR when close port," + e.getMessage(), e);
            }
            sPort = null;
        }
        isOpen = false;
        mToolbar.getMenu().findItem(R.id.btnOpenOnTool).setIcon(R.drawable.close);
    }
    private void setPortParameters(int band,int parity)
    {
        this.mBandRate = band;
        this.mParity = parity;
        if(sPort!=null) {
            try {
                sPort.setParameters(band, 8, UsbSerialPort.STOPBITS_1, parity);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mToolbar.setSubtitle(String.valueOf(band)+":"+PARITY_S[parity]);
    }
    private UsbSerialPort getSerialPort()
    {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManagerger);
        if (availableDrivers.isEmpty()) {
            return null;
        }
        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        return driver.getPorts().get(0);
    }
    private void modifyCMDInfo(final int index)
    {
        View v = View.inflate(this,R.layout.cmd_dialog_view,null);
        final EditText etCMD = v.findViewById(R.id.cmdEdit_dialog);
        final EditText etAlias = v.findViewById(R.id.aliasEdit_dialog);
        final EditText etPer = v.findViewById(R.id.perEdit_dialog);
        final CheckBox cbHex = v.findViewById(R.id.hexCB_dialog);
        final CheckBox cbCR = v.findViewById(R.id.addcrCB_dialog);
        etCMD.setText(cmdList.get(index).getCmd());
        etAlias.setText(cmdList.get(index).getAlias());
        etPer.setText(String.valueOf(cmdList.get(index).getPeriod()));
        cbHex.setChecked(cmdList.get(index).isHex());
        cbCR.setChecked(cmdList.get(index).isAddCR());
        new AlertDialog.Builder(this)
                .setTitle("修改命令信息")
                .setCancelable(false)
                .setView(v)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String cmd = etCMD.getText().toString();
                        String alias = etAlias.getText().toString();
                        int per = Integer.parseInt(etPer.getText().toString());
                        boolean addCR = cbCR.isChecked();
                        boolean ishex = cbHex.isChecked();
                        cmdList.set(index,new CustomSerialCMD(cmd,alias,ishex,addCR,per));
                    }
                })
                .show();

    }
    private void addAnCMDOnList() {
        cmdList.add(mCurCMDIndex,new CustomSerialCMD("","",false,true,0));
        cmdListAdapter.notifyDataSetChanged();
    }
    private class CmdListArrayAdapter  extends ArrayAdapter<CustomSerialCMD>
    {
        private int resId;
        public CmdListArrayAdapter(Context context, int resource, List<CustomSerialCMD> cmd) {
            super(context, resource, cmd);
            this.resId = resource;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v;
            ViewHolder vh;
            CustomSerialCMD cmd = getItem(position);
            if(convertView == null){
                v = LayoutInflater.from(getContext()).inflate(resId,parent,false);
                vh = new ViewHolder();
                vh.tv = v.findViewById(android.R.id.text1);
                v.setTag(vh);
            }else{
                v = convertView;
                vh = (ViewHolder) v.getTag();
            }
            vh.tv.setText(cmd.getAlias());
            return v;
        }
        class ViewHolder {
            TextView tv;
        }
    }
    private void SaveCmdContent()
    {
        SharedPreferences.Editor editor = msp.edit();
        editor.clear();
        for(int i=0; i < cmdList.size();i++) {
            CustomSerialCMD cmd = cmdList.get(i);
            String value = String.format("%s<,>%s<,>%s<,>%s<,>%d",cmd.getCmd(),cmd.getAlias(),
                    String.valueOf(cmd.isHex()),String.valueOf(cmd.isAddCR()),cmd.getPeriod());

            String name=String.format("c%d",i);
            Log.d(TAG,"cmdList:"+name+":"+value);
            editor.putString(name,value);
        }
        editor.apply();
        Log.d(TAG,"Save list,size:"+cmdList.size());
    }
    private void FillCmdList()
    {
        cmdList.clear();
        for(int i = 0; i<200;i++) {
            String name=String.format("c%d",i);
            String value = msp.getString(name,"");
            if(TextUtils.isEmpty(value)){
                break;
            }
            int index1 = value.indexOf("<,>");
            int index2 = value.indexOf("<,>",index1+3);
            int index3 = value.indexOf("<,>",index2+3);
            int index4 = value.indexOf("<,>",index3+3);
            String cmd = value.substring(0,index1);
            String alias = value.substring(index1+3,index2);
            boolean ishex = Boolean.parseBoolean(value.substring(index2+3,index3));
            boolean addcr = Boolean.parseBoolean(value.substring(index3+3,index4));
            int per = Integer.parseInt(value.substring(index4+3));
            cmdList.add(new CustomSerialCMD(cmd,alias,ishex,addcr,per));
        }
    }

    //侧滑监听
    class MyGestureDetector extends GestureDetector.SimpleOnGestureListener{

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if(e1.getX()-e2.getX()>100){
                //Toast.makeText(SerialConsoleActivity.this,"左滑",Toast.LENGTH_SHORT).show();
                cmdListView.setVisibility(View.VISIBLE);
            }else if(e2.getX()-e1.getX()>100){
                //Toast.makeText(MainActivity.this,"右滑",Toast.LENGTH_SHORT).show();
                cmdListView.setVisibility(View.GONE);
            }
            if(e1.getY()-e2.getY()>100){
                //Toast.makeText(MainActivity.this,"上滑",Toast.LENGTH_SHORT).show();
                mProtocolLayout.setVisibility(View.VISIBLE);
            }else if(e2.getY()-e1.getY()>100){
                //Toast.makeText(MainActivity.this,"下滑",Toast.LENGTH_SHORT).show();
                mProtocolLayout.setVisibility(View.GONE);
            }
            return true;
        }
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

}
