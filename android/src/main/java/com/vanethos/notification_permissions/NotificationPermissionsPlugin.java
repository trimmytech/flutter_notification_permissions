package com.vanethos.notification_permissions; // Ensure this matches your package structure

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener; // For handling permission results

public class NotificationPermissionsPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, RequestPermissionsResultListener {
  private MethodChannel channel;
  private Context applicationContext;
  private Activity activity;
  private Result pendingResult; // To store the result for async permission request

  // FlutterPlugin methods
  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    applicationContext = flutterPluginBinding.getApplicationContext();
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "notification_permissions");
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    applicationContext = null;
  }

  // ActivityAware methods
  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    activity = binding.getActivity();
    binding.addRequestPermissionsResultListener(this); // Listen for permission results
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivity() {
    activity = null;
    // If you had a RequestPermissionsResultListener added in onAttachedToActivity,
    // you might need to remove it if the binding object provides a way,
    // or ensure it handles a null activity.
    // For this plugin, ActivityPluginBinding handles listener removal.
  }


  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (activity == null) {
      result.error("NO_ACTIVITY", "Plugin is not attached to an activity.", null);
      return;
    }

    pendingResult = result; // Store for async callback

    switch (call.method) {
      case "getNotificationPermissionStatus":
        // This is a simplification. Android 13 (API 33) introduced a specific
        // POST_NOTIFICATIONS permission. Older versions didn't have a distinct
        // runtime permission for general notifications (they were controlled by channels).
        // You'll need to check Android version.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
          if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            result.success("granted");
          } else if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
            result.success("denied"); // Or "shouldShowRationale"
          } else {
            result.success("unknown"); // Or "permanentlyDenied" / "notDetermined"
          }
        } else {
          // For older Android versions, notification enablement is typically checked via NotificationManagerCompat.areNotificationsEnabled()
          // This requires more context (NotificationManagerCompat).
          // For simplicity here, let's assume granted if pre-Tiramisu,
          // though in a real plugin you'd check NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
          androidx.core.app.NotificationManagerCompat notificationManagerCompat = androidx.core.app.NotificationManagerCompat.from(applicationContext);
          if (notificationManagerCompat.areNotificationsEnabled()) {
            result.success("granted");
          } else {
            result.success("denied"); // Or based on channel status
          }
        }
        break;
      case "requestNotificationPermissions":
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
          ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1024 // Request code
          );
          // The result will be handled in onRequestPermissionsResult
        } else {
          // No specific runtime permission to request for general notifications pre-Tiramisu
          // You might open app settings for notifications here.
          result.success("granted"); // Or indicate not applicable
        }
        break;
      default:
        result.notImplemented();
        break;
    }
  }

  // RequestPermissionsResultListener method
  @Override
  public boolean onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == 1024 && pendingResult != null) { // Match your request code
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        pendingResult.success("granted");
      } else if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
              !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
        pendingResult.success("permanentlyDenied"); // Or simply "denied"
      }
      else {
        pendingResult.success("denied");
      }
      pendingResult = null; // Clear the pending result
      return true; // Indicate we've handled this result
    }
    return false; // Indicate we haven't handled this result
  }
}