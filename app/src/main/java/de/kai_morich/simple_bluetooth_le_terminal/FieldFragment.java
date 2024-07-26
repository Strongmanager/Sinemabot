package de.kai_morich.simple_bluetooth_le_terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.util.Log;
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
/** import uji latensi**/
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
/**---------------------------------**/
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class FieldFragment extends Fragment implements ServiceConnection, SerialListener {
    private static final int VIBRATION_DURATION = 170;
    private static final int STRENGTH_THRESHOLD = 5;
    private static final int VIBRATION_STRENGTH_THRESHOLD = 10;
    private static final int DELAY = 50;
    private static final int MULTIPLIER = 1000;

    private Button savedMovementsButton;
    private Button stopSavedMovementsButton;
    private Button playSavedMovementsButton;
    private Button resetMovementsButton;
    private boolean isRecording = false;

    private View recordingDot;
    private TextView timerTextView;
    private Animation blinkAnimation;
    private Timer timer;
    private int seconds = 0;

    private boolean isPaused = false;

    //private List<JoystickMovement> joystickMovements = new ArrayList<>();

    private Vibrator vibrator;

    private Handler handler = new Handler(Looper.getMainLooper()); // kode untuk pengujian latensi

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

        WebView webView = view.findViewById(R.id.webView);
        Button connectButton = view.findViewById(R.id.buttonD3);
        final EditText ipEditText = view.findViewById(R.id.ipEditText);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
                v.startAnimation(animation);
                vibrator.vibrate(170);
                String ipAddress = ipEditText.getText().toString();
                String url = "http://" + ipAddress + ":5000";
               handler.postDelayed(runnable, 1000); //uji delay
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        view.loadUrl(url);
                        return true;
                    }
                });
                webView.loadUrl(url);
            }
            /** UJI LATENSI**/
            private Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    webView.post(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            webView.evaluateJavascript("(function() { var canvas = document.createElement('canvas'); var img = document.querySelector('img'); canvas.width = img.width; canvas.height = img.height; canvas.getContext('2d').drawImage(img, 0, 0); return canvas.toDataURL('image/png').substring(22); })();", value -> {
                                Bitmap bitmap = convertBase64ToBitmap(value);
                                if (bitmap != null) {
                                    recognizeTextFromBitmap(bitmap);
                                }
                            });
                        }
                    });
                    handler.postDelayed(this, 1000);
                }
            };

            private Bitmap convertBase64ToBitmap(String base64Str) {
                try {
                    byte[] decodedString = Base64.decode(base64Str, Base64.DEFAULT);
                    return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                } catch (Exception e) {
                    Log.e("FieldFragment", "Error converting base64 to bitmap", e);
                    return null;
                }
            }

            private void recognizeTextFromBitmap(Bitmap bitmap) {
                InputImage image = InputImage.fromBitmap(bitmap, 0);
                TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                recognizer.process(image)
                        .addOnSuccessListener(result -> {
                            for (Text.TextBlock block : result.getTextBlocks()) {
                                for (Text.Line line : block.getLines()) {
                                    String timestamp = line.getText();
                                    Log.d("FieldFragment", "Recognized timestamp: " + timestamp);  // Log timestamp
                                    long currentTime = System.currentTimeMillis();
                                    calculateLatency(timestamp, currentTime);
                                }
                            }
                        })
                        .addOnFailureListener(e -> Log.e("FieldFragment", "Error recognizing text", e));
            }

            private void calculateLatency(String timestamp, long currentTime) {
                try {
                    Log.d("FieldFragment", "Full recognized text: " + timestamp);
                    if (timestamp.length() == 23) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
                        Date videoTime = sdf.parse(timestamp);
                        if (videoTime != null) {
                            long latency = currentTime - videoTime.getTime();
                            Log.d("FieldFragment", "Timestamp: " + timestamp + ", Latency: " + latency + " ms");
                        }
                    } else {
                        Log.e("FieldFragment", "Invalid timestamp length: " + timestamp.length());
                    }
                } catch (Exception e) {
                    Log.e("FieldFragment", "Error calculating latency", e);
                }
            }
        });

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

        AppCompatImageButton exitButton = view.findViewById(R.id.imageButton2);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Activity activity = requireActivity();
                new AlertDialog.Builder(activity)
                        .setMessage("Are you sure want to exit?")
                        .setCancelable(false)
                        .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                activity.finish();
                            }
                        })
                        .setNegativeButton("No",null)
                        .show();
            }
        });


//        Toast.makeText(getActivity(),deviceAddress, Toast.LENGTH_SHORT).show();
        vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        AppCompatImageButton savedMovementsButton = view.findViewById(R.id.saved_movements_button);
        AppCompatImageButton stopSavedMovementsButton = view.findViewById(R.id.stop_saved_movements_button);
        AppCompatImageButton playSavedMovementsButton = view.findViewById(R.id.play_saved_movements_button);
        AppCompatImageButton resetButton = view.findViewById(R.id.reset_button);
        recordingDot = view.findViewById(R.id.recording_dot);
        blinkAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.blink_animation);

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
                    savedMovementsButton.setVisibility(View.GONE);
                    stopSavedMovementsButton.setVisibility(View.VISIBLE);
                    recordingDot.setVisibility(View.VISIBLE);
                    recordingDot.startAnimation(blinkAnimation);
                    communication[0] = (byte)(communication[0] | 0b00001000);
                    sendData(communication);
                    Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
                    view.startAnimation(animation);
                    vibrator.vibrate(170);
                    resetCom();
                } else {
                    isRecording = false;
 //                   playSavedMovementsButton.setVisibility(View.VISIBLE);
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
                savedMovementsButton.setVisibility(View.VISIBLE);
                recordingDot.clearAnimation();
                recordingDot.setVisibility(View.GONE);
                communication[0] = (byte)(communication[0] | 0b00100000);
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
                communication[0] = (byte)(communication[0] | 0b0010000);
                sendData(communication);
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
                view.startAnimation(animation);
                vibrator.vibrate(170);
                resetCom();
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                communication[0] = (byte)(communication[0] | 0b1000000);
                sendData(communication);
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.anim_scale);
                view.startAnimation(animation);
                vibrator.vibrate(170);
                resetCom();
            }
        });


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
    // record joystick

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
            sendData(Rmotions);
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
        ((android.widget.Button)getView().findViewById(R.id.socketConnection)).setText("Connect");
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
                (byte) (axis | ((x << 4) & 0xff)), // byte 0 axis 2 & 6 dikirim dalam 4 bit data
                (byte) ((x >> 4) & 0xff), // byte 1
                (byte) (((x >> 12) & 0xff)| ((y<<6)& 0xff)), // byte 2
                (byte) ((y >> 2) & 0xff),   // byte 3
                (byte) ((y >> 10) & 0xff), // byte 4
        };
    }

}

