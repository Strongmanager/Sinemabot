package de.kai_morich.simple_bluetooth_le_terminal;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.webkit.WebView;
import android.Manifest;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class FieldFragment extends Fragment implements ServiceConnection, SerialListener {
    private static final int VIBRATION_DURATION = 170;
    private static final int STRENGTH_THRESHOLD = 30;
    private static final int VIBRATION_STRENGTH_THRESHOLD = 10;
    private static final int DELAY = 50;
    private static final int MULTIPLIER = 1000;

    private Button savedMovementsButton;
    private Button stopSavedMovementsButton;
    private Button playSavedMovementsButton;
    private boolean isRecording = false;

    private boolean isPaused = false;

    //private List<JoystickMovement> joystickMovements = new ArrayList<>();

    private Vibrator vibrator;
    private static final String READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE;
    private WebView webView;
    private SurfaceView surfaceView;
    private Button recordButton, pauseButton, stopButton;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder mediaRecorder;

    private String deviceAddress;
    private enum Connected { False, Pending, True }

    private SerialService service;
    private boolean isLeftJoy=false, isLeftJoy_vib=false;
    private boolean isRightJoy=false, isRightJoy_vib=false;

    public byte[] Rmotions = new byte[5];
    public byte[] Lmotions = new byte[5];

    private TextView receiveText;
    private TextView sendText;

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
        /**ActivityCompat.requestPermissions(getActivity(),
         new String[]{CAMERA,
         READ_EXTERNAL_STORAGE,
         RECORD_AUDIO},
         PackageManager.PERMISSION_GRANTED);
         SurfaceView surfaceView = view.findViewById(R.id.surfaceView);
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
         mediaRecorder = new MediaRecorder();
         }**/
        WebView webView = view.findViewById(R.id.webView);
        Button connectButton = view.findViewById(R.id.buttonD3);
        final EditText ipEditText = view.findViewById(R.id.ipEditText);
        //String ipAddress = ipEditText.getText().toString();
        //String url = "http://" + ipAddress + ":5000";
        /**Button startButton = view.findViewById(R.id.button);
         Button stopButton = view.findViewById(R.id.button2);
         startButton.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
        buttonStartVideoRecording(v);
        Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
        v.startAnimation(animation);
        vibrator.vibrate(170);
        }
        });
         stopButton.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
        buttonStopVideoRecording(v);
        Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
        v.startAnimation(animation);
        vibrator.vibrate(170);
        }
        });**/

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (connected == Connected.True) {
                    disconnect();
                } else if (connected == Connected.False) {
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
        /**FrameLayout frameLayout = new FrameLayout(getActivity());
         frameLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
         frameLayout.addView(webView);

         ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
         surfaceView.setLayoutParams(layoutParams);
         surfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
         surfaceView.setZOrderOnTop(true);
         surfaceView.setVisibility(View.GONE);

         webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
         webView.setBackgroundColor(0);
         webView.setVisibility(View.VISIBLE);

         webView.loadUrl(url);

         // Initialize mediaRecorder inside onCreateView()
         mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
         mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
         mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
         mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
         mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
         mediaRecorder.setOutputFile("/sdcard/test.mp4");
         mediaRecorder.setMaxDuration(5000); // 5 seconds of max recording
         mediaRecorder.setMaxFileSize(5000000); // Max 5 MB **/


        /**if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
         mediaProjectionManager = (MediaProjectionManager) getActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
         }

         recordButton.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
        try {
        startRecording();
        } catch (IOException e) {
        throw new RuntimeException(e);
        }
        }
        });

         pauseButton.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
        pauseRecording();
        }
        });

         stopButton.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
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
         //        webView.loadUrl(url);**/

// Dapatkan referensi ke ProgressBar
        ProgressBar progressBar = view.findViewById(R.id.progressBar);
        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                // Periksa apakah ProgressBar telah didefinisikan sebelumnya
                if (progressBar != null) {
                    if (progress < 100 && progressBar.getVisibility() == ProgressBar.GONE) {
                        progressBar.setVisibility(ProgressBar.VISIBLE);
                    }
                    progressBar.setProgress(progress);
                    if (progress == 100) {
                        progressBar.setVisibility(ProgressBar.GONE);
                    }
                }
            }
        });
        resetCom();


//        Toast.makeText(getActivity(),deviceAddress, Toast.LENGTH_SHORT).show();
        vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        savedMovementsButton = view.findViewById(R.id.saved_movements_button);
        stopSavedMovementsButton = view.findViewById(R.id.stop_saved_movements_button);
        playSavedMovementsButton = view.findViewById(R.id.play_saved_movements_button);

        JoystickView leftJoystick = (JoystickView) view.findViewById(R.id.joystickView_left);
        leftJoystick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                receiveText.setText("left joystick");
                vibrate();
            }
        });

        leftJoystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                handleLeftJoystickMove(angle, strength);
            }
        }, DELAY);


        JoystickView rightJoystick = (JoystickView) view.findViewById(R.id.joystickView_right);
        rightJoystick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                receiveText.setText("left joystick");
                vibrate();
            }
        });

        rightJoystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                handleRightJoystickMove(angle, strength);
            }
        }, DELAY);


        savedMovementsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isRecording) {
                    isRecording = true;
                    savedMovementsButton.setText("Stop");
                    stopSavedMovementsButton.setVisibility(View.VISIBLE);
                    communication[0] = (byte)(communication[0] | 0b00001000);
                    sendData(communication);
                    Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
                    view.startAnimation(animation);
                    vibrator.vibrate(170);
                    resetCom();
                } else {
                    isRecording = false;
                    savedMovementsButton.setText("Record");
                    stopSavedMovementsButton.setVisibility(View.GONE);
                    communication[0] = (byte)(communication[0] | 0b00010000);
                    sendData(communication);
                    Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
                    view.startAnimation(animation);
                    vibrator.vibrate(170);
                    resetCom();
                }
            }
        });

        stopSavedMovementsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isRecording = false;
                stopSavedMovementsButton.setVisibility(View.GONE);
                playSavedMovementsButton.setVisibility(View.VISIBLE);
                communication[0] = (byte)(communication[0] | 0b00010000);
                sendData(communication);
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
                view.startAnimation(animation);
                vibrator.vibrate(170);
                resetCom();
            }
        });

        playSavedMovementsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //playBackJoystickMovements();
                //joystickMovements.clear();
                playSavedMovementsButton.setVisibility(View.GONE);
                savedMovementsButton.setVisibility(View.VISIBLE);
                savedMovementsButton.setText("Record");
                communication[0] = (byte)(communication[0] | 0b00100000);
                sendData(communication);
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
                view.startAnimation(animation);
                vibrator.vibrate(170);
                resetCom();
            }
        });







        /**vibrator = (Vibrator) getActivity().getSystemService(getContext().VIBRATOR_SERVICE);

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
                if(isLeftJoy || (strength>30)){
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
        }, 50); **/


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

    /**private void playBackJoystickMovements() {
        if (joystickMovements.isEmpty()) {
            return;
        }

        for (JoystickMovement movement : joystickMovements) {
            sendData(motionToByte(movement.x, movement.y, (byte) movement.type));
            // Wait for the specified time interval between joystick movements
            long sleepTime = movement.time - System.currentTimeMillis();
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    // Handle the InterruptedException
                }
            }
        }

        joystickMovements.clear();
    }**/

    private void handleLeftJoystickMove(int angle, int strength) {
        if (strength <= STRENGTH_THRESHOLD && !isLeftJoy) {
            resetMotion();
            sendData(Lmotions);
            return;
        }

        int x = calculateCoordinatex(angle, strength, MULTIPLIER);
        int y = calculateCoordinatey(angle, strength, MULTIPLIER);
        sendData(motionToByte(x, y, Lmotions[0]));
        resetMotion();
        isLeftJoy = true;

        if (strength == 0) {
            isLeftJoy = false;
            isLeftJoy_vib = false;
        }

        if (!isLeftJoy_vib && strength > VIBRATION_STRENGTH_THRESHOLD) {
            isLeftJoy_vib = true;
            vibrate();
        }
        if (isRecording && strength > STRENGTH_THRESHOLD) {
         //   joystickMovements.add(new JoystickMovement(x, y, Lmotions[0], System.currentTimeMillis()));
        }
        //joystickMovements.add(new JoystickMovement(x, y, Lmotions[0]));
    }

    private void handleRightJoystickMove(int angle, int strength) {
        if (strength <= STRENGTH_THRESHOLD && !isRightJoy) {
            resetMotion();
            sendData(Lmotions);
            return;
        }

        int x = calculateCoordinatex(angle, strength, MULTIPLIER);
        int y = calculateCoordinatey(angle, strength, MULTIPLIER);
        sendData(motionToByte(x, y, Rmotions[0]));
        resetMotion();
        isRightJoy = true;

        if (strength == 0) {
            isRightJoy = false;
            isRightJoy_vib = false;
        }

        if (!isRightJoy_vib && strength > VIBRATION_STRENGTH_THRESHOLD) {
            isRightJoy_vib = true;
            vibrate();
        }
        if (isRecording && strength > STRENGTH_THRESHOLD) {
         //   joystickMovements.add(new JoystickMovement(x, y, Rmotions[0], System.currentTimeMillis()));
        }
        //joystickMovements.add(new JoystickMovement(x, y, Rmotions[0]));
    }
    /**public List<JoystickMovement> getJoystickMovements() {
        return joystickMovements;
    }**/

    private int calculateCoordinatex(int angle, int strength, int multiplier) {
        return (int) (Math.cos(Math.toRadians((double) angle)) * strength * multiplier);
    }
    private int calculateCoordinatey(int angle, int strength, int multiplier) {
        return (int) (Math.sin(Math.toRadians((double) angle)) * strength * multiplier);
    }

    private void vibrate() {
        vibrator.vibrate(VIBRATION_DURATION);
    }
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

    /**public class JoystickMovement {
        public int x;
        public int y;
        public int type;
        public long time;


        public JoystickMovement(int x, int y, int type, long time) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.time = time;
        }

        public long getTime() {
            return time ;
        }

        private byte[] getByteArray(byte b) {
            byte[] result = new byte[1];
            result[0] = b;
            return result;
        }
    }**/

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
    /**public void buttonStartVideoRecording(View view) {
        // Check if surfaceView is currently invisible
        if (surfaceView.getVisibility() == View.GONE) {
            surfaceView.setVisibility(View.VISIBLE);
            try {
                // Initialize mediaRecorder inside buttonStartVideoRecording()
                mediaRecorder = new MediaRecorder();
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setOutputFile("/sdcard/test.mp4");
                mediaRecorder.setMaxDuration(5000); // 5 seconds of max recording
                mediaRecorder.setMaxFileSize(5000000); // Max 5 MB
                mediaRecorder.prepare();
            } catch (IOException e) {
                Toast.makeText(getActivity(), e.toString(), Toast.LENGTH_LONG).show();
            }
            mediaRecorder.start();
        }
    }

    public void buttonStopVideoRecording(View view) {
        mediaRecorder.stop();
        mediaRecorder.release();
        surfaceView.setVisibility(View.GONE);
    }**/

    /**private void startRecording() throws IOException {
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
    }**/


}

