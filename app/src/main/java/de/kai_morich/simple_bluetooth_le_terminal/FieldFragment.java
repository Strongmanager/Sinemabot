package de.kai_morich.simple_bluetooth_le_terminal;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Button;
import android.webkit.WebView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;


import java.io.IOException;
import java.util.ArrayDeque;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class FieldFragment extends Fragment implements ServiceConnection, SerialListener {
    private WebView webView;
    private Button recordButton, pauseButton, stopButton;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private boolean isPaused = false;

    private String deviceAddress;
    private enum Connected { False, Pending, True }

    private SerialService service;
    private boolean isLeftJoy=false, isLeftJoy_vib=false;
    private boolean isRightJoy=false, isRightJoy_vib=false;

    public byte[] Rmotions = new byte[5];
    public byte[] Lmotions = new byte[5];

    private TextView receiveText;
    private TextView sendText;
    private Vibrator vibrator;

    private TextView vLeftSpeed, vRightSpeed;

    private FieldFragment.Connected connected = FieldFragment.Connected.False;
    private boolean initialStart = true;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;
    JoystickView joystickLeft;


    public byte[] communication = new byte[5];
    //Variable data yang dikirim

    /*
     * Lifecycle
     */

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
        
    }

    @Override
    public void onDestroy() {
        if (connected != FieldFragment.Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }


    private void resetCom(){
        communication[0]=1;
        communication[1]=0;
        communication[2]=0;
        communication[3]=0;
        communication[4]=0;
    }



    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_field, container, false);
        receiveText = view.findViewById(R.id.receiveText);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());
        WebView webView = view.findViewById(R.id.webView);
        Button connectButton = view.findViewById(R.id.buttonD3);
        final EditText ipEditText = view.findViewById(R.id.ipEditText);
        recordButton = view.findViewById(R.id.record_button);
        pauseButton = view.findViewById(R.id.pause_button);
        stopButton = view.findViewById(R.id.stop_button);



        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(connected == Connected.True) {
                    disconnect();
                }else if(connected == Connected.False){
                    connect();
                }
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
                v.startAnimation(animation);
                vibrator.vibrate(170);
                String ipAddress = ipEditText.getText().toString();
                String url = "http://" + ipAddress + ":5000";
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        view.loadUrl(url);
                        return true;
                    }
                });
                webView.loadUrl(url);
            }
        });

         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjectionManager = (MediaProjectionManager) getActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startRecording();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseRecording();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });

//        code tanam untuk menampilkan hasil kamera
//        WebView webView = view.findViewById(R.id.webView);
//        String url = "http://10.106.60.120:5000";
//        webView.getSettings().setJavaScriptEnabled(true);
//        webView.setWebViewClient(new WebViewClient() {
//            @Override
//            public boolean shouldOverrideUrlLoading(WebView view, String url) {
//                view.loadUrl(url);
//                return true;
//            }
//        });
//        webView.loadUrl(url);

// Dapatkan referensi ke ProgressBar
        ProgressBar progressBar = view.findViewById(R.id.progressBar);
        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                // Periksa apakah ProgressBar telah didefinisikan sebelumnya
                if (progressBar != null) {
                    if(progress < 100 && progressBar.getVisibility() == ProgressBar.GONE){
                        progressBar.setVisibility(ProgressBar.VISIBLE);
                    }
                    progressBar.setProgress(progress);
                    if(progress == 100) {
                        progressBar.setVisibility(ProgressBar.GONE);
                    }
                }
            }
        });
        resetCom();


        /**WebView webView = view.findViewById(R.id.webView);
        String video = "<iframe width=\"100%\" height=\"100%\" src=\"\"http://10.104.72.68:5000\" title=\"YouTube video player\" frameborder=\"0\" allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share\" referrerpolicy=\"strict-origin-when-cross-origin\" allowfullscreen></iframe>";
        webView.loadData(video, "text/html", "utf-8");
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
         WebView webView = getView().findViewById(R.id.webView);
        String video = "<iframe width=\"100%\" height=\"100%\" src=\"http://10.104.72.68:5000"\" title=\"YouTube video player\" frameborder=\"0\" allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share\" referrerpolicy=\"strict-origin-when-cross-origin\" allowfullscreen></iframe>";
        webView.loadData(video, "text/html", "utf-8");
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient()); **/


//        Toast.makeText(getActivity(),deviceAddress, Toast.LENGTH_SHORT).show();
        vibrator = (Vibrator) getActivity().getSystemService(getContext().VIBRATOR_SERVICE);

        JoystickView left_joystick = (JoystickView) view.findViewById(R.id.joystickView_left);
        left_joystick.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
              receiveText.setText("left joystick");
              vibrator.vibrate(170);
          }
        });
        left_joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                if(isLeftJoy || (strength>10)){
                    int x = (int) (Math.cos(Math.toRadians((double)angle)) * strength *1000 );
                    int y = (int) (Math.sin(Math.toRadians((double)angle)) * strength *1000);
                    sendData(motionToByte(x,y,Lmotions[0]));
                    resetMotion();
                    isLeftJoy=true;
                    sendData(Lmotions);
                    if(strength==0) {
                        isLeftJoy = false;
                        isLeftJoy_vib = false;
                    }
                }
                if(!isLeftJoy_vib && strength > 10){
                    isLeftJoy_vib = true;
                    vibrator.vibrate(170);
                }
            }
        },50);
        JoystickView right_joystick = (JoystickView) view.findViewById(R.id.joystickView_right);
        right_joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                if(isRightJoy || (strength>30)){
                    int x = (int) (Math.cos(Math.toRadians((double)angle)) * strength *1000);
                    int y = (int) (Math.sin(Math.toRadians((double)angle)) * strength *1000);
                    sendData(motionToByte(x,y,Rmotions[0]));
                    resetMotion();
                    isRightJoy=true;
                    if(strength==0){
                        isRightJoy=false;
                        isRightJoy_vib=false;
                    }
                }
                if(!isRightJoy_vib&&strength>10){
                    isRightJoy_vib = true;
                    vibrator.vibrate(170);
                }
            }
        }, 50);


        // Tombol Samping
        // Kecepatan Pelempar Kiri(Left launcer speed) : Increment
//        View sendA1 = view.findViewById(R.id.buttonA1);
//        sendA1.setOnClickListener(v->{
//            communication[0] = (byte)(communication[0] | 0b00000100);
//            sendData(communication);
//            Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
//            v.startAnimation(animation);
//            vibrator.vibrate(170);
//            resetCom();
//        });
//        // Kecepatan Pelempar Kiri(Left launcer speed): Decrement
//        View sendA2 = view.findViewById(R.id.buttonA2);
//        sendA2.setOnClickListener(v -> {
//            communication[0] = (byte)(communication[0] | 0b00001000);
//            sendData(communication);
//            Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
//            v.startAnimation(animation);
//            vibrator.vibrate(170);
//            resetCom();
//        });
        //Kecepatan(+)
//        View sendD1 = view.findViewById(R.id.buttonD1);
//        sendD1.setOnClickListener(v -> {
//            communication[0] = (byte)(communication[0] | 0b00010000);
//            sendData(communication);
//            Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
//            v.startAnimation(animation);
//            vibrator.vibrate(170);
//            resetCom();
//        });
        //Kecepatan(-)
//        View sendD2 = view.findViewById(R.id.buttonD2);
//        sendD2.setOnClickListener(v -> {
//            communication[0] = (byte)(communication[0] | 0b00100000);
//            sendData(communication);
//            Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
//            v.startAnimation(animation);
//            vibrator.vibrate(170);
//            resetCom();
//        });
        //GRIP
//        View sendA3 = view.findViewById(R.id.buttonA3);
//        sendA3.setOnClickListener(v -> {
//            communication[0] = (byte)(communication[0] | 0b01000000);
//            sendData(communication);
//            Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
//            v.startAnimation(animation);
//            vibrator.vibrate(170);
//            resetCom();
//        });
        //NEXT
//        View sendD3 = view.findViewById(R.id.buttonD3);
//       sendD3.setOnClickListener(v -> {
//            communication[0] = (byte)(communication[0] | 0b10000000);
//            sendData(communication);
//            Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
//           v.startAnimation(animation);
//           vibrator.vibrate(170);
//            resetCom();
//        });
//        //Posisi C
//        View sendB3 = view.findViewById(R.id.buttonB3);
//        sendB3.setOnClickListener(v -> {
//            communication[1] = (byte)(communication[1] | 0b00000001);
//            sendData(communication);
//            Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
//            v.startAnimation(animation);
//            vibrator.vibrate(170);
//            resetCom();
//        });

        // Tombol Field






        //State A
//        View sendC1 = view.findViewById(R.id.buttonC1);
//        sendC1.setOnClickListener(v -> {
//            communication[1] = (byte)(communication[1] | 0b00000010);
//            sendData(communication);
//            Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
//            v.startAnimation(animation);
//            vibrator.vibrate(170);
//            resetCom();
////            send("C1");
//        });
//        //State B
//        View sendC2 = view.findViewById(R.id.buttonC2);
//        sendC2.setOnClickListener(v -> {
//            communication[1] = (byte)(communication[1] | 0b00000100);
//            sendData(communication);
//            Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
//            v.startAnimation(animation);
//            vibrator.vibrate(170);
//            resetCom();
//        });
//        //State C
//        View sendC3 = view.findViewById(R.id.buttonC3);
//        sendC3.setOnClickListener(v -> {
//            communication[1] = (byte)(communication[1] | 0b00001000);
//            sendData(communication);
//            Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
//            v.startAnimation(animation);
//            vibrator.vibrate(170);
//            resetCom();
//        });

        View socketConnection = view.findViewById(R.id.socketConnection);
        socketConnection.setOnClickListener(v -> {
            if(connected == Connected.True) {
                disconnect();
            }else if(connected == Connected.False){
                connect();
            }
            Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
            v.startAnimation(animation);
            vibrator.vibrate(170);
        });

        return view;

    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
//            status("connecting...");
            ((android.widget.Button)getView().findViewById(R.id.socketConnection)).setText("Connecting...");
            connected = FieldFragment.Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = FieldFragment.Connected.False;
        service.disconnect();
        ((android.widget.Button)getView().findViewById(R.id.socketConnection)).setText("C");
        (getView().findViewById(R.id.socketConnection)).setActivated(false);
    }

    public void sendData(byte[] data){
        if(connected != FieldFragment.Connected.True) {
//            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        for (byte[] data : datas) {
            String msg = new String(data);
//            spn.append(TextUtil.toHexString(data)).append('\n');
            if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    if(spn.length() >= 2) {
                        spn.delete(spn.length() - 2, spn.length());
                    } else {
                        Editable edt = receiveText.getEditableText();
                        if (edt != null && edt.length() >= 2)
                            edt.delete(edt.length() - 2, edt.length());
                    }
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }

//            spn.append(TextUtil.toCaretString(msg, newline.length() != 0));

        }
        receiveText.append(spn);
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
//        status("connected");
        connected = FieldFragment.Connected.True;
        ((android.widget.Button)getView().findViewById(R.id.socketConnection)).setText("Disconnect");
        (getView().findViewById(R.id.socketConnection)).setActivated(true);
    }


    @Override
    public void onSerialConnectError(Exception e) {
//        status("connection failed: " + e.getMessage());
        disconnect();
        ((android.widget.Button)getView().findViewById(R.id.socketConnection)).setText("Connect");
        (getView().findViewById(R.id.socketConnection)).setActivated(false);
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
        for (byte[] data : datas) {
            if ((data[0]&7) == 0) {
                //vLeftSpeed = getView().findViewById(R.id.ViewLeftSpeed);
                //vRightSpeed = getView().findViewById(R.id.ViewRightSpeed);


            }
       }
    }

    @Override
    public void onSerialIoError(Exception e) {
//        status("connection lost: " + e.getMessage());
        disconnect();
        ((android.widget.Button)getView().findViewById(R.id.socketConnection)).setText("connect");
        (getView().findViewById(R.id.socketConnection)).setActivated(false);
    }

    public void resetMotion(){
        Rmotions[0]=0b0010;
        Rmotions[1]=0b0;
        Rmotions[2]=0b0;
        Rmotions[3]=0b0;
        Rmotions[4]=0b0;
        Lmotions[0]=0b0110;
        Lmotions[1]=0b0;
        Lmotions[2]=0b0;
        Lmotions[3]=0b0;
        Lmotions[4]=0b0;
    }
    private byte[] motionToByte(int x, int y, byte axis){
        return new byte[]{
                (byte) (axis | ((x << 4) & 0xff)), // byte 0
                (byte) ((x >> 4) & 0xff), // byte 1
                (byte) (((x >> 12) & 0xff)| ((y<<6)& 0xff)), // byte 2
                (byte) ((y >> 2) & 0xff),   // byte 3
                (byte) ((y >> 10) & 0xff), // byte 4
        };
    }

    private void startRecording() throws IOException {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, null);
        }
        virtualDisplay = createVirtualDisplay();
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoSize(1280, 720);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setOutputFile(getVideoFilePath());
        mediaRecorder.prepare();
        mediaRecorder.start();
        isRecording = true;
    }

    private void pauseRecording() {
        if (isRecording) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder.pause();
            }
            isPaused = true;
        }
    }

    private void stopRecording() {
        if (isRecording) {
            mediaRecorder.stop();
            mediaRecorder.release();
            isRecording = false;
            isPaused = false;
        }
    }

    private VirtualDisplay createVirtualDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return mediaProjection.createVirtualDisplay("ScreenRecorder", 1280, 720, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, null, null, null);
        } else {
            throw new RuntimeException("API level too low to create virtual display.");
        }
    }

    private String getVideoFilePath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/recorded_video.mp4";
    }


}

