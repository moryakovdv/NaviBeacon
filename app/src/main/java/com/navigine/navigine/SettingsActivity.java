package com.navigine.navigine;
import com.navigine.navigine.*;
import com.navigine.naviginesdk.*;

import android.app.*;
import android.content.*;
import android.database.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.hardware.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.*;
import android.text.method.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import android.widget.ImageView.*;
import android.util.*;
import java.io.*;
import java.lang.*;
import java.nio.*;
import java.net.*;
import java.util.*;

public class SettingsActivity extends Activity
{
  // Constants
  private static final String TAG = "NAVIGINE.SettingsActivity";
  private static final int REQUEST_PICK_FILE = 1;
  
  // This context
  private final Context mContext = this;
  
  private Button  mMenuButton  = null;
  private boolean mMenuVisible = false;
  
  private boolean mBackgroundNavigationEnabled = true;
  private int mBackgroundMode = NavigationThread.MODE_NORMAL;
  
  private boolean mNavigationFileEnabled = false;
  private String mNavigationFile = "";
  
  private int mDebugCounter = 0;
  
  /** Called when the activity is first created */
  @Override public void onCreate(Bundle savedInstanceState)
  {
    Log.d(TAG, "SettingsActivity created");
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.settings);
    
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                         WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    
    if (NavigineApp.Navigation == null)
    {
      finish();
      return;
    }
    
    mMenuButton = (Button)findViewById(R.id.settings__menu_button);
    
    if (NavigineApp.Settings.getBoolean("navigation_file_enabled", false) &&
        NavigineApp.Settings.getString("navigation_file", "").length() > 0)
    {
      mNavigationFileEnabled = true;
      mNavigationFile = NavigineApp.Settings.getString("navigation_file", "");
      setCheckBox (R.id.settings__navigation_file_enabled_checkbox, true);
      TextView tv = (TextView)findViewById(R.id.settings__navigation_file_enabled_label);
      String name = new File(mNavigationFile).getName();
      String text = String.format(Locale.ENGLISH, "Navigation file enabled:\n'%s'", name);
      tv.setText(text);
    }
    else
    {
      mNavigationFileEnabled = false;
      setCheckBox (R.id.settings__navigation_file_enabled_checkbox, false);
      TextView tv = (TextView)findViewById(R.id.settings__navigation_file_enabled_label);
      tv.setText("Navigation file enabled");
    }
    
    findViewById(R.id.settings__orientation_enabled_label).setVisibility(View.GONE);
    findViewById(R.id.settings__orientation_enabled_checkbox).setVisibility(View.GONE);
    
    setTextValue(R.id.settings__location_server_address_edit,     NavigineApp.Settings.getString ("location_server_address", NavigineApp.DEFAULT_SERVER));
    setCheckBox (R.id.settings__beacon_service_checkbox,          NavigineApp.Settings.getBoolean("beacon_service_enabled", true));
    setCheckBox (R.id.settings__save_navigation_log_checkbox,     NavigineApp.Settings.getBoolean("navigation_log_enabled", false));
    setCheckBox (R.id.settings__save_navigation_track_checkbox,   NavigineApp.Settings.getBoolean("navigation_track_enabled", false));
    setCheckBox (R.id.settings__post_messages_enabled_checkbox,   NavigineApp.Settings.getBoolean("post_messages_enabled", true));
    setCheckBox (R.id.settings__crash_messages_enabled_checkbox,  NavigineApp.Settings.getBoolean("crash_messages_enabled", true));
    setCheckBox (R.id.settings__debug_mode_enabled_checkbox,      NavigineApp.Settings.getBoolean("debug_mode_enabled", false));
    setCheckBox (R.id.settings__orientation_enabled_checkbox,     NavigineApp.Settings.getBoolean("orientation_enabled", false));
    
    mBackgroundMode = NavigineApp.Settings.getInt("background_navigation_mode", NavigationThread.MODE_NORMAL);
    switch (mBackgroundMode)
    {
      case NavigationThread.MODE_NORMAL:
        setCheckBox(R.id.settings__background_navigation_checkbox, true);
        ((RadioButton)findViewById(R.id.settings__radio_normal_mode)).setChecked(true);
        break;
      
      case NavigationThread.MODE_ECONOMIC1:
        setCheckBox(R.id.settings__background_navigation_checkbox, true);
        ((RadioButton)findViewById(R.id.settings__radio_economic_mode)).setChecked(true);
        break;
      
      case NavigationThread.MODE_ECONOMIC2:
        setCheckBox(R.id.settings__background_navigation_checkbox, true);
        ((RadioButton)findViewById(R.id.settings__radio_economic2_mode)).setChecked(true);
        break;
      
      case NavigationThread.MODE_IDLE:
        setCheckBox(R.id.settings__background_navigation_checkbox, false);
    }
  }
  
  @Override public void onBackPressed()
  {
    toggleMenuLayout(null);
  }
  
  public void onButtonClicked(View view)
  {
    // Check which button was clicked
    switch (view.getId())
    {
      case R.id.settings__background_navigation_checkbox:
        mBackgroundNavigationEnabled = ((CheckBox)view).isChecked();
        findViewById(R.id.settings__radio_group).setVisibility(mBackgroundNavigationEnabled ? View.VISIBLE : View.GONE);
        break;
      
      case R.id.settings__radio_normal_mode:
        if (((RadioButton)view).isChecked())
          mBackgroundMode = NavigationThread.MODE_NORMAL;
        break;
      
      case R.id.settings__radio_economic_mode:
        if (((RadioButton)view).isChecked())
          mBackgroundMode = NavigationThread.MODE_ECONOMIC1;
        break;
      
      case R.id.settings__radio_economic2_mode:
        if (((RadioButton)view).isChecked())
          mBackgroundMode = NavigationThread.MODE_ECONOMIC2;
        break;
      
      case R.id.settings__debug_mode_enabled_checkbox:
        if (++mDebugCounter >= 6)
        {
          findViewById(R.id.settings__orientation_enabled_label).setVisibility(View.VISIBLE);
          findViewById(R.id.settings__orientation_enabled_checkbox).setVisibility(View.VISIBLE);
        }
        break;
      
      case R.id.settings__navigation_file_enabled_checkbox:
        mNavigationFileEnabled = ((CheckBox)view).isChecked();
        if (mNavigationFileEnabled)
        {
          Intent intent = new Intent(NavigineApp.AppContext, FilePickerActivity.class);
          startActivityForResult(intent, REQUEST_PICK_FILE);
        }
        break;
    }
    saveSettings();
  }
  
  public void onLocationManagementMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
    
    saveSettings();
    
    Intent intent = new Intent(mContext, LoaderActivity.class);
    startActivity(intent);
  }
  
  public void onMeasuringMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
    
    saveSettings();
    
    Intent intent = new Intent(mContext, MeasuringActivity.class);
    startActivity(intent);
  }
  
  public void onNavigationMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
    
    saveSettings();
    
    Intent intent = new Intent(mContext, NavigationActivity.class);
    startActivity(intent);
  }
  
  public void onDebugMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
    
    saveSettings();
    
    Intent intent = new Intent(mContext, DebugActivity.class);
    startActivity(intent);
  }
  
  public void onSettingsMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
  }
  
  public void toggleMenuLayout(View v)
  {
    LinearLayout topLayout  = (LinearLayout)findViewById(R.id.settings__top_layout);
    LinearLayout menuLayout = (LinearLayout)findViewById(R.id.settings__menu_layout);
    LinearLayout mainLayout = (LinearLayout)findViewById(R.id.settings__main_layout);
    ViewGroup.MarginLayoutParams layoutParams = null;
    
    boolean hasMapFile   = (NavigineApp.Settings != null && NavigineApp.Settings.getString("map_file", "").length() > 0);
    boolean hasDebugMode = (NavigineApp.Settings != null && NavigineApp.Settings.getBoolean("debug_mode_enabled", false));
    findViewById(R.id.settings__menu_measuring_mode).setVisibility(hasMapFile ? View.VISIBLE : View.GONE);
    findViewById(R.id.settings__menu_navigation_mode).setVisibility(hasMapFile ? View.VISIBLE : View.GONE);
    findViewById(R.id.settings__menu_debug_mode).setVisibility(hasDebugMode ? View.VISIBLE : View.GONE);
    
    if (!mMenuVisible)
    {
      mMenuVisible = true;
      layoutParams = (ViewGroup.MarginLayoutParams)menuLayout.getLayoutParams();
      layoutParams.leftMargin  += 250.0f * NavigineApp.DisplayDensity;
      layoutParams.rightMargin -= 250.0f * NavigineApp.DisplayDensity;
      menuLayout.setVisibility(View.VISIBLE);
      menuLayout.setLayoutParams(layoutParams);
      
      layoutParams = (ViewGroup.MarginLayoutParams)topLayout.getLayoutParams();
      layoutParams.leftMargin  += 250.0f * NavigineApp.DisplayDensity;
      layoutParams.rightMargin -= 250.0f * NavigineApp.DisplayDensity;
      topLayout.setLayoutParams(layoutParams);
      
      layoutParams = (ViewGroup.MarginLayoutParams)mainLayout.getLayoutParams();
      layoutParams.leftMargin  += 250.0f * NavigineApp.DisplayDensity;
      layoutParams.rightMargin -= 250.0f * NavigineApp.DisplayDensity;
      mainLayout.setLayoutParams(layoutParams);
    }
    else
    {
      mMenuVisible = false;
      layoutParams = (ViewGroup.MarginLayoutParams)menuLayout.getLayoutParams();
      layoutParams.leftMargin  -= 250.0f * NavigineApp.DisplayDensity;
      layoutParams.rightMargin += 250.0f * NavigineApp.DisplayDensity;
      menuLayout.setVisibility(View.GONE);
      menuLayout.setLayoutParams(layoutParams);
      
      layoutParams = (ViewGroup.MarginLayoutParams)topLayout.getLayoutParams();
      layoutParams.leftMargin  -= 250.0f * NavigineApp.DisplayDensity;
      layoutParams.rightMargin += 250.0f * NavigineApp.DisplayDensity;
      topLayout.setLayoutParams(layoutParams);
      
      layoutParams = (ViewGroup.MarginLayoutParams)mainLayout.getLayoutParams();
      layoutParams.leftMargin  -= 250.0f * NavigineApp.DisplayDensity;
      layoutParams.rightMargin += 250.0f * NavigineApp.DisplayDensity;
      mainLayout.setLayoutParams(layoutParams);
    }
  }
  
  public void onMainLayoutClicked(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
  }
  
  private void setTextValue(int id, String text)
  {
    EditText edit = (EditText)findViewById(id);
    if (edit != null)
      edit.setText(text);
  }
  
  private String getTextValue(int id)
  {
    EditText edit = (EditText)findViewById(id);
    return edit.getText().toString();
  }
  
  private int getIntValue(int id, int defaultValue, int minValue, int maxValue)
  {
    EditText edit = (EditText)findViewById(id);
    String text = edit.getText().toString();
    int value = defaultValue;
    try { value = Integer.parseInt(text); } catch (Throwable e) { }
    return Math.max(Math.min(value, maxValue), minValue);
  }
  
  private void setCheckBox(int id, boolean enabled)
  {
    CheckBox checkBox = (CheckBox)findViewById(id);
    if (checkBox != null)
      checkBox.setChecked(enabled);
  }
  
  private boolean getCheckBox(int id)
  {
    CheckBox checkBox = (CheckBox)findViewById(id);
    return checkBox.isChecked();
  }
  
  private void saveSettings()
  {
    Log.d(TAG, "SettingsActivity: saving settings");
    SharedPreferences.Editor editor = NavigineApp.Settings.edit();
    editor.putString ("location_server_address",      getTextValue(R.id.settings__location_server_address_edit));
    editor.putInt("background_navigation_mode",       mBackgroundNavigationEnabled ? mBackgroundMode : NavigationThread.MODE_IDLE);
    editor.putBoolean("beacon_service_enabled",       getCheckBox (R.id.settings__beacon_service_checkbox));
    editor.putBoolean("navigation_log_enabled",       getCheckBox (R.id.settings__save_navigation_log_checkbox));
    editor.putBoolean("navigation_track_enabled",     getCheckBox (R.id.settings__save_navigation_track_checkbox));
    editor.putBoolean("post_messages_enabled",        getCheckBox (R.id.settings__post_messages_enabled_checkbox));
    editor.putBoolean("crash_messages_enabled",       getCheckBox (R.id.settings__crash_messages_enabled_checkbox));
    editor.putBoolean("debug_mode_enabled",           getCheckBox (R.id.settings__debug_mode_enabled_checkbox));
    editor.putBoolean("orientation_enabled",          getCheckBox (R.id.settings__orientation_enabled_checkbox));
    editor.putBoolean("navigation_file_enabled",      mNavigationFileEnabled);
    editor.putString ("navigation_file",              mNavigationFileEnabled ? mNavigationFile : "");
    editor.commit();
    
    applySettings();
  }
  
  private void applySettings()
  {
    Log.d(TAG, "SettingsActivity: applying settings");
    NavigineApp.applySettings();
  }
  
  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    if (requestCode != REQUEST_PICK_FILE)
      return;
    
    if (resultCode == RESULT_OK)
    {
      if (data.hasExtra(FilePickerActivity.EXTRA_FILE_PATH))
      {
        // Get the file path
        File f = new File(data.getStringExtra(FilePickerActivity.EXTRA_FILE_PATH));
        
        mNavigationFileEnabled = true;
        mNavigationFile = f.getAbsolutePath();
        
        ((CheckBox)findViewById(R.id.settings__navigation_file_enabled_checkbox)).setChecked(true);
        TextView tv = (TextView)findViewById(R.id.settings__navigation_file_enabled_label);
        String text = String.format(Locale.ENGLISH, "Navigation file enabled:\n'%s'", f.getName());
        tv.setText(text);
      }
    }
    else
    {
      mNavigationFileEnabled = false;
      mNavigationFile = "";
      ((CheckBox)findViewById(R.id.settings__navigation_file_enabled_checkbox)).setChecked(false);
      TextView tv = (TextView)findViewById(R.id.settings__navigation_file_enabled_label);
      tv.setText("Navigation file enabled");
    }
    saveSettings();
  }
}
