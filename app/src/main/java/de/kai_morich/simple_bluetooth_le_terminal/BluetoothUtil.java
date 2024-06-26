package de.kai_morich.simple_bluetooth_le_terminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.fragment.app.Fragment;

import java.util.Map;

public class BluetoothUtil {

    interface PermissionGrantedCallback {
        void call();
    }

    /**
     * sort by name, then address. sort named devices first
     */
    @SuppressLint("MissingPermission")
    static int compareTo(BluetoothDevice a, BluetoothDevice b) {
        boolean aValid = a.getName()!=null && !a.getName().isEmpty();
        boolean bValid = b.getName()!=null && !b.getName().isEmpty();
        if(aValid && bValid) {
            int ret = a.getName().compareTo(b.getName());
            if (ret != 0) return ret;
            return a.getAddress().compareTo(b.getAddress());
        }
        if(aValid) return -1;
        if(bValid) return +1;
        return a.getAddress().compareTo(b.getAddress());
    }

    /**
     * Android 12 permission handling
     */
    private static void showRationaleDialog(Fragment fragment, DialogInterface.OnClickListener listener) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getActivity());
        builder.setTitle(fragment.getString(R.string.bluetooth_permission_title));
        builder.setMessage(fragment.getString(R.string.bluetooth_permission_grant));
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Continue", listener);
        builder.show();
    }

    private static void showSettingsDialog(Fragment fragment) {
        String s = fragment.getResources().getString(fragment.getResources().getIdentifier("@android:string/permgrouplab_nearby_devices", null, null));
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getActivity());
        builder.setTitle(fragment.getString(R.string.bluetooth_permission_title));
        builder.setMessage(String.format(fragment.getString(R.string.bluetooth_permission_denied), s));
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Settings", (dialog, which) ->
                fragment.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + BuildConfig.APPLICATION_ID))));
        builder.show();
    }

    /**
     * CONNECT + SCAN are granted together in same permission group, so actually no need to check/request both, but one never knows
     */
    static boolean hasPermissions(Fragment fragment, ActivityResultLauncher<String[]> requestPermissionLauncher) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return true;
        boolean missingPermissions = fragment.getActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                                   | fragment.getActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED;
        boolean showRationale = fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
                              | fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN);
        String[] permissions = new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN};
        if(missingPermissions) {
            if (showRationale) {
                showRationaleDialog(fragment, (dialog, which) ->
                requestPermissionLauncher.launch(permissions));
            } else {
                requestPermissionLauncher.launch(permissions);
            }
            return false;
        } else {
            return true;
        }
    }

    static void onPermissionsResult(Fragment fragment, Map<String, Boolean> grants, PermissionGrantedCallback cb) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return;
        boolean showRationale = fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
                              | fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN);
        boolean granted = grants.values().stream().reduce(true, (a, b) -> a && b);
        if (granted) {
            cb.call();
        } else if (showRationale) {
            showRationaleDialog(fragment, (dialog, which) -> cb.call());
        } else {
            showSettingsDialog(fragment);
        }
    }

}
/**
 public class WiFiUtil {

 interface PermissionGrantedCallback {
 void call();
 }


 * sort by name, then address. sort named devices first

@SuppressLint("MissingPermission")
static int compareTo(WifiConfiguration a, WifiConfiguration b) {
    boolean aValid = a.SSID != null && !a.SSID.isEmpty();
    boolean bValid = b.SSID != null && !b.SSID.isEmpty();
    if(aValid && bValid) {
        int ret = a.SSID.compareTo(b.SSID);
        if (ret != 0) return ret;
        return a.BSSID.compareTo(b.BSSID);
    }
    if(aValid) return -1;
    if(bValid) return +1;
    return a.BSSID.compareTo(b.BSSID);
}


     * Android 12 permission handling

    private static void showRationaleDialog(Fragment fragment, DialogInterface.OnClickListener listener) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getActivity());
        builder.setTitle(fragment.getString(R.string.wifi_permission_title));
        builder.setMessage(fragment.getString(R.string.wifi_permission_grant));
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Continue", listener);
        builder.show();
    }

    private static void showSettingsDialog(Fragment fragment) {
        String s = fragment.getResources().getString(fragment.getResources().getIdentifier("@android:string/permgrouplab_wifi_settings", null, null));
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getActivity());
        builder.setTitle(fragment.getString(R.string.wifi_permission_title));
        builder.setMessage(String.format(fragment.getString(R.string.wifi_permission_denied), s));
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Settings", (dialog, which) ->
                fragment.startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)));
        builder.show();
    }


     * CONNECT + SCAN are granted together in same permission group, so actually no need to check/request both, but one never knows

    static boolean hasPermissions(Fragment fragment, ActivityResultLauncher<String[]> requestPermissionLauncher) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return true;
        boolean missingPermissions = fragment.getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                | fragment.getActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        boolean showRationale = fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                | fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
        String[] permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        if(missingPermissions) {
            if (showRationale) {
                showRationaleDialog(fragment, (dialog, which) ->
                        requestPermissionLauncher.launch(permissions));
            } else {
                requestPermissionLauncher.launch(permissions);
            }
            return false;
        } else {
            return true;
        }
    }

    static void onPermissionsResult(Fragment fragment, Map<String, Boolean> grants, PermissionGrantedCallback cb) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return;
        boolean showRationale = fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                | fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
        boolean granted = grants.values().stream().reduce(true, (a, b) -> a && b);
        if (granted) {
            cb.call();
        } else if (showRationale) {
            showRationaleDialog(fragment, (dialog, which) -> cb.call());
        } else {
            showSettingsDialog(fragment);
        }
    }

}
 */