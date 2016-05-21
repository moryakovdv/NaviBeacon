package com.navigine.navigine;
import com.navigine.navigine.*;
import com.navigine.naviginesdk.*;
import com.navigine.imu.*;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.hardware.*;
import android.os.*;
import android.util.*;
import java.io.*;
import java.lang.*;
import java.nio.*;
import java.util.*;

public class NavigineApp extends Application
{
  public static final String      TAG               = "NAVIGINE";
  public static final String      DEFAULT_SERVER    = "https://api.navigine.com";
  public static final String      DEFAULT_USER_HASH = "0000-0000-0000-0000";
  public static final String      RESTART_ON_TASK_REMOVED = "true";
  
  public static Context           AppContext        = null;
  public static SharedPreferences Settings          = null;
  public static NavigationThread  Navigation        = null;
  public static IMU_Thread        IMU               = null;
  //public static SentryThread      Sentry            = null;
  public static int               IMU_Location      = 0;
  public static int               IMU_SubLocation   = 0;


  public static UserInfo          UserInfo          = null;
  
  public static float             DisplayWidthPx    = 0.0f;
  public static float             DisplayHeightPx   = 0.0f;
  public static float             DisplayWidthDp    = 0.0f;
  public static float             DisplayHeightDp   = 0.0f;
  public static float             DisplayDensity    = 0.0f;
  
  public static boolean           BackgroundMode    = false;
  
  //public static NavigineExceptionHandler ExceptionHandler = null;
  
  @Override public void onCreate()
  {
    super.onCreate();
    
    Log.d(TAG, "NAVIGINE APP STARTED");
    
    registerReceiver(
      new BroadcastReceiver()
      {
        @Override public void onReceive(Context context, Intent intent)
        {
          int id           = intent.getIntExtra("beacon_action_id", 0);
          String title     = intent.getStringExtra("beacon_action_title");
          String content   = intent.getStringExtra("beacon_action_content");
          String imageUrl  = intent.getStringExtra("beacon_action_image_url");
          postNotification(id, title, content, imageUrl);
        }
      },
      new IntentFilter("com.navigine.navigine.beacon_action")
    );
  }
  
  public static boolean initialize(Context appContext)
  {
    // Setting static parameters
    BeaconService.DEBUG_LEVEL     = 2;
    NativeUtils.DEBUG_LEVEL       = 2;
    LocationLoader.DEBUG_LEVEL    = 2;
    NavigationThread.DEBUG_LEVEL  = 2;
    MeasureThread.DEBUG_LEVEL     = 2;
    SensorThread.DEBUG_LEVEL      = 2;
    Parser.DEBUG_LEVEL            = 2;
    
    NavigationThread.STRICT_MODE  = true;
    
    try
    {
      AppContext = appContext;
      Settings   = AppContext.getSharedPreferences("NavigineSettings", 0);
      Navigation = new NavigationThread(null, AppContext);
      IMU        = new IMU_Thread(AppContext);
      DisplayMetrics displayMetrics = AppContext.getResources().getDisplayMetrics();
      DisplayWidthPx  = displayMetrics.widthPixels;
      DisplayHeightPx = displayMetrics.heightPixels;
      DisplayDensity  = displayMetrics.density;
      DisplayWidthDp  = DisplayWidthPx / DisplayDensity;
      DisplayHeightDp = DisplayHeightPx / DisplayDensity;
      Log.d(TAG, String.format(Locale.ENGLISH, "Display size: %.1fpx x %.1fpx (%.1fdp x %.1fdp, density=%.2f)",
                               DisplayWidthPx, DisplayHeightPx,
                               DisplayWidthDp, DisplayHeightDp,
                               DisplayDensity));
    }
    catch (Throwable e)
    {
      Navigation = null;
      Log.e(TAG, Log.getStackTraceString(e));
      //if (ExceptionHandler != null)
        //ExceptionHandler.addException(e);
      return false;
    }
    
    Log.d(TAG, String.format(Locale.ENGLISH, "Root directory: %s",
          LocationLoader.getLocationDir(AppContext, "")));
    
    if (AppContext == null || Navigation == null || Settings == null)
      return false;
    
    applySettings();
    return true;
  }
  
  public static void login(UserInfo userInfo)
  {
    if (AppContext == null || Navigation == null || Settings == null)
      return;
    
    Log.d(TAG, "Login as " + userInfo.name);
    UserInfo = new UserInfo(userInfo);
    
    SharedPreferences.Editor editor = Settings.edit();
    editor.putInt("user_id", UserInfo.id);
    editor.putInt("user_active", UserInfo.active);
    editor.putString("user_name", UserInfo.name);
    editor.putString("user_company", UserInfo.company);
    editor.putString("user_email", UserInfo.email);
    editor.putString("user_hash", UserInfo.hash);
    editor.commit();
  }
  
  public static void logout()
  {
    if (AppContext == null || Navigation == null || Settings == null)
      return;
    
    Log.d(TAG, "Logout");
    UserInfo = null;
    
    SharedPreferences.Editor editor = Settings.edit();
    editor.remove("user_id");
    editor.remove("user_active");
    editor.remove("user_name");
    editor.remove("user_company");
    editor.remove("user_email");
    editor.remove("user_hash");
    editor.commit();
    
    // Removing 'maps.xml' file
    if (AppContext != null)
    {
      Log.d(TAG, "Removing file 'maps.xml'");
      String fileName = LocationLoader.getLocationDir(AppContext, null) + "/maps.xml";
      (new File(fileName)).delete();
    }
  }

  /*
  public static void startSentry(Context appContext)
  {
    if (ExceptionHandler == null)
    {
      String homeDir = LocationLoader.getHomeDir(appContext);
      ExceptionHandler = new NavigineExceptionHandler(homeDir);
      Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler);
      
      SharedPreferences settings = appContext.getSharedPreferences("NavigineSettings", 0);
      if (settings.getBoolean("crash_messages_enabled", true))
        Sentry = new SentryThread(homeDir + "/crashes");
    }
  }
  
  public static void stopSentry()
  {
    if (Sentry != null)
    {
      Sentry.terminate();
      try
      {
        Log.d(TAG, "Joining with Sentry thread");
        Sentry.join();
      }
      catch (Throwable e)
      {
        Log.e(TAG, "Joining error!");
        Log.e(TAG, Log.getStackTraceString(e));
      }
      Sentry = null;
    }
  }
  */

  public static void applySettings()
  {
    if (AppContext == null || Navigation == null || Settings == null)
      return;
    
    // Setting up server parameters
    String address = Settings.getString("location_server_address", NavigineApp.DEFAULT_SERVER);
    Navigation.setServer(address);
    
    if (Settings.contains("user_name") &&
        Settings.contains("user_email") &&
        Settings.contains("user_hash"))
    {
      UserInfo          = new UserInfo();
      UserInfo.id       = Settings.getInt("user_id", 0);
      UserInfo.active   = Settings.getInt("user_active", 0);
      UserInfo.name     = Settings.getString("user_name", "");
      UserInfo.company  = Settings.getString("user_company", "");
      UserInfo.email    = Settings.getString("user_email", "");
      UserInfo.hash     = Settings.getString("user_hash", "");
      Navigation.setUserHash(UserInfo.hash);
    }
    
    if (!Settings.getBoolean("beacon_service_enabled", true))
    {
      Log.d(TAG, "Stopping BeaconService");
      Intent beaconIntent = new Intent("com.navigine.navigine.beacon_service");
      beaconIntent.putExtra("beacon_service_key", "stop_request");
      beaconIntent.putExtra("beacon_service_value", "true");
      AppContext.sendBroadcast(beaconIntent);
      
      // In order to prevent BeaconService from starting on BOOT_COMPLETED event
      SharedPreferences settings = AppContext.getSharedPreferences("BeaconService", 0);
      SharedPreferences.Editor editor = settings.edit();
      editor.putBoolean("restart_on_task_removed", false);
      editor.commit();
    }
    else
    {
      Log.d(TAG, "Starting BeaconService");
      NavigineApp.AppContext.startService(new Intent(AppContext, BeaconService.class));
      
      // Sending intents 1000 ms after starting BeaconService
      final Handler handler = new Handler();
      handler.postDelayed(new Runnable()
        {
          @Override public void run()
          {
            // Setting 'user_hash'
            String userHash = Settings.getString("user_hash", "");
            Intent beaconIntent1 = new Intent("com.navigine.navigine.beacon_service");
            beaconIntent1.putExtra("beacon_service_key", "user_hash");
            beaconIntent1.putExtra("beacon_service_value", userHash);
            AppContext.sendBroadcast(beaconIntent1);
            
            // Setting 'location_id'
            int mapId = Settings.getInt("map_id", 0);
            Intent beaconIntent2 = new Intent("com.navigine.navigine.beacon_service");
            beaconIntent2.putExtra("beacon_service_key", "location_id");
            beaconIntent2.putExtra("beacon_service_value", String.format(Locale.ENGLISH, "%d", mapId));
            AppContext.sendBroadcast(beaconIntent2);
            
            // Setting 'restart_on_task_removed'
            Intent beaconIntent3 = new Intent("com.navigine.navigine.beacon_service");
            beaconIntent3.putExtra("beacon_service_key", "restart_on_task_removed");
            beaconIntent3.putExtra("beacon_service_value", RESTART_ON_TASK_REMOVED);
            AppContext.sendBroadcast(beaconIntent3);
          }
        }, 1000);
    }
  }
  
  public static String getLogFile(String extension)
  {
    if (AppContext == null || Navigation == null || Settings == null)
      return null;
    
    String mapFile = Settings.getString("map_file", "");
    if (mapFile.length() > 0)
    {
      for(int i = 1; true; ++i)
      {
        String suffix = String.format(Locale.ENGLISH, ".%d.%s", i, extension);
        String logFile = mapFile.replaceAll("\\.zip$", suffix);
        if ((new File(logFile)).exists())
          continue;
        return logFile;
      }
    }
    return null;
  }
  
  public static void startNavigation()
  {
    if (AppContext == null || Navigation == null || Settings == null)
      return;
    
    String mapFile = Settings.getString("map_file", "");
    if (!Navigation.loadArchive(mapFile))
    {
      Log.e(TAG, "Unable to start navigation: invalid map " + mapFile);
      return;
    }
    
    if (Settings.getBoolean("navigation_log_enabled", false))
      Navigation.setLogFile(getLogFile("log"));
    else
      Navigation.setLogFile(null);
    
    if (Settings.getBoolean("navigation_track_enabled", false))
      Navigation.setTrackFile(getLogFile("txt"));
    else
      Navigation.setTrackFile(null);
    
    int mode = BackgroundMode ?
                  Settings.getInt("background_navigation_mode", NavigationThread.MODE_NORMAL) :
                  NavigationThread.MODE_NORMAL;
    
    if (Settings.getBoolean("navigation_file_enabled", false))
    {
      mode = NavigationThread.MODE_FILE;
      Navigation.setNavigationFile(Settings.getString("navigation_file", ""));
    }
    
    Navigation.setPostEnabled(Settings.getBoolean("post_messages_enabled", true));
    Navigation.setMode(mode);
  }
  
  public static void setBackgroundMode(boolean enabled)
  {
    if (AppContext == null || Navigation == null || Settings == null)
      return;
    
    BackgroundMode = enabled;
    
    switch (Navigation.getMode())
    {
      case NavigationThread.MODE_NORMAL:
      case NavigationThread.MODE_ECONOMIC1:
      case NavigationThread.MODE_ECONOMIC2:
        Navigation.setMode(BackgroundMode ?
            Settings.getInt("background_navigation_mode", NavigationThread.MODE_NORMAL) :
            NavigationThread.MODE_NORMAL);
        break;
    }
  }
  
  public static void stopNavigation()
  {
    if (AppContext == null || Navigation == null || Settings == null)
      return;
    
    Navigation.setLogFile(null);
    Navigation.setTrackFile(null);
    Navigation.setMode(NavigationThread.MODE_IDLE);
    
    if (IMU.getConnectionState() == IMU_Thread.STATE_NORMAL)
    {
      Log.d(TAG, "Disconnecting from IMU");
      IMU.disconnect();
    }
  }
  
  public static void startScanning()
  {
    if (AppContext == null || Navigation == null || Settings == null)
      return;
    
    Navigation.setLogFile(null);
    Navigation.setTrackFile(null);
    Navigation.setMode(NavigationThread.MODE_SCAN);
  }
  
  public static void stopScanning()
  {
    if (AppContext == null || Navigation == null || Settings == null)
      return;
    
    Navigation.setLogFile(null);
    Navigation.setTrackFile(null);
    Navigation.setMode(NavigationThread.MODE_IDLE);
  }
  
  public static void destroyNavigation()
  {
    if (AppContext == null || Navigation == null || Settings == null)
      return;
    
    Log.d(TAG, "Terminating IMU, Navigation threads!");
    IMU.terminate();
    Navigation.terminate();
    try
    {
      Log.d(TAG, "Joining with IMU thread");
      IMU.join();
      Log.d(TAG, "Joining with Navigation thread");
      Navigation.join();
    }
    catch (Throwable e)
    {
      Log.e(TAG, "Joining error!");
      Log.e(TAG, Log.getStackTraceString(e));
    }
    IMU         = null;
    Navigation  = null;
    AppContext  = null;    
    
    //stopSentry();
  }
  
  public static DeviceInfo getDeviceInfoByIMU(IMU_Device imuDevice)
  {
    if (AppContext == null || Navigation == null || Settings == null)
      return null;
    
    long timeNow = DateTimeUtils.currentTimeMillis();
    
    DeviceInfo device = new DeviceInfo();
    device.id = Navigation.getDeviceId();
    device.type = "android";
    device.time = DateTimeUtils.currentDate(timeNow);
    device.location = IMU_Location;
    device.subLocation = IMU_SubLocation;
    device.x = imuDevice.x;
    device.y = imuDevice.y;
    device.z = imuDevice.z;
    device.r = 2;
    device.azimuth = imuDevice.angle;
    device.timeLabel = timeNow;
    return device;
  }
  
  public static void postNotification(int id, String title, String content, String imageUrl)
  {
    try
    {
      Context context = BeaconService.getContext();
      Log.d(TAG, String.format(Locale.ENGLISH, "Post notification: id=%d, title='%s', imageUrl='%s'",
                               id, title, imageUrl));
      
      String imagePath = String.format(Locale.ENGLISH, "%s/image-beacon-action-%d.png",
                                       context.getCacheDir().getPath(), id);
      
      Intent intent = new Intent(context, BeaconActivity.class);
      intent.putExtra("beacon_action_title", title);
      intent.putExtra("beacon_action_content", content);
      intent.putExtra("beacon_action_image_url", imageUrl);
      intent.putExtra("beacon_action_image_path", imagePath);
      
      PendingIntent pendingIntent = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
      
      Notification.Builder notificationBuilder = new Notification.Builder(context);
      notificationBuilder.setSmallIcon(R.drawable.notification);
      notificationBuilder.setContentTitle(title);
      notificationBuilder.setContentText(content);
      notificationBuilder.setDefaults(Notification.DEFAULT_SOUND);
      notificationBuilder.setAutoCancel(true);
      notificationBuilder.setContentIntent(pendingIntent);
      
      File imageFile = new File(imagePath);
      if (imageFile.exists())
      {
        Bitmap imageBitmap = BitmapFactory.decodeFile(imagePath);
        notificationBuilder.setLargeIcon(imageBitmap);
      }
      
      // Get an instance of the NotificationManager service
      NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
      
      // Build the notification and issues it with notification manager.
      notificationManager.notify(id, notificationBuilder.build());
    }
    catch (Throwable e)
    {
      Log.e(TAG, Log.getStackTraceString(e));
    }
  }
}
