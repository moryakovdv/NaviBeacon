package com.navigine.navigine;
import com.navigine.navigine.*;
import com.navigine.naviginesdk.*;

import android.app.*;
import android.content.*;
import android.content.DialogInterface.OnDismissListener;
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

public class LoginActivity extends Activity
{
  // Constants
  private static final String TAG = "NAVIGINE.LoginActivity";
  private static final int AUTH_TIMEOUT = 15000;
  private static final int UPDATE_TIMEOUT = 100;
  
  // This context
  private final Context mContext        = this;
  
  private EditText      mLoginEdit      = null;
  private EditText      mPasswordEdit   = null;
  private Button        mLoginButton    = null;
  private Button        mCancelButton   = null;
  private TextView      mStatusLabel    = null;
  private Button        mMenuButton     = null;
  private ProgressBar   mProgressBar    = null;
  
  private boolean       mMenuVisible    = false;
  private long          mAuthTime       = 0;
  
  private TimerTask     mTimerTask      = null;
  private Handler       mHandler        = new Handler();
  private Timer         mTimer          = new Timer();
  
  /** Called when the activity is first created */
  @Override public void onCreate(Bundle savedInstanceState)
  {
    Log.d(TAG, "LoginActivity created");
    super.onCreate(savedInstanceState);
    
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                         WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.login);
    
    if (NavigineApp.Navigation == null)
    {
      finish();
      return;
    }
    
    //NavigineApp.startSentry(mContext);
    
    mLoginEdit    = (EditText)findViewById(R.id.login__login_edit);
    mPasswordEdit = (EditText)findViewById(R.id.login__password_edit);
    mLoginButton  = (Button)findViewById(R.id.login__done_button);
    mCancelButton = (Button)findViewById(R.id.login__cancel_button);
    mStatusLabel  = (TextView)findViewById(R.id.login__status_label);
    mProgressBar  = (ProgressBar)findViewById(R.id.login__progress_bar);
    mMenuButton   = (Button)findViewById(R.id.login__menu_button);
    
    mProgressBar.setVisibility(View.INVISIBLE);
    
    mLoginButton.setOnClickListener(new OnClickListener()
      {
        public void onClick(View view)
        {
          if (mMenuVisible)
          {
            toggleMenuLayout(null);
            return;
          }
          startLogin();
        }
      });
    
    mCancelButton.setOnClickListener(new OnClickListener()
      {
        public void onClick(View view)
        {
          if (mMenuVisible)
          {
            toggleMenuLayout(null);
            return;
          }
          stopLogin();
        }
      });
    
    NavigineApp.logout();
    NavigineApp.Navigation.stopAuthentication();
    mLoginEdit.setText(NavigineApp.Settings.getString("login", ""));
    mPasswordEdit.setText(NavigineApp.Settings.getString("password", ""));
  }
  
  @Override public void onStart()
  {
    Log.d(TAG, "LoginActivity started");
    super.onStart();
    
    // Stop interface updates
    if (mTimerTask != null)
    {
      mTimerTask.cancel();
      mTimerTask = null;
    }
    
    // Starting interface updates
    mTimerTask = 
      new TimerTask()
      {
        @Override public void run() 
        {
          update();
        }
      };
    mTimer.schedule(mTimerTask, UPDATE_TIMEOUT, UPDATE_TIMEOUT);
  }
  
  @Override public void onStop()
  {
    Log.d(TAG, "LoginActivity stopped");
    super.onStop();
    
    // Stop interface updates
    if (mTimerTask != null)
    {
      mTimerTask.cancel();
      mTimerTask = null;
    }
  }
  
  @Override public void onBackPressed()
  {
    toggleMenuLayout(null);
  }
  
  private void cleanup()
  {
    // Stop interface updates
    if (mTimerTask != null)
    {
      mTimerTask.cancel();
      mTimerTask = null;
    }
  }
  
  public void onLocationManagementMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
  }
  
  public void onMeasuringMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
    
    cleanup();
    
    Intent intent = new Intent(mContext, MeasuringActivity.class);
    startActivity(intent);
  }
  
  public void onNavigationMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
    
    cleanup();
    
    Intent intent = new Intent(mContext, NavigationActivity.class);
    startActivity(intent);
  }
  
  public void onDebugMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
    
    cleanup();
    
    Intent intent = new Intent(mContext, DebugActivity.class);
    startActivity(intent);
  }
  
  public void onSettingsMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
    
    cleanup();
    
    Intent intent = new Intent(mContext, SettingsActivity.class);
    startActivity(intent);
  }
  
  public void toggleMenuLayout(View v)
  {
    LinearLayout topLayout  = (LinearLayout)findViewById(R.id.login__top_layout);
    LinearLayout menuLayout = (LinearLayout)findViewById(R.id.login__menu_layout);
    LinearLayout mainLayout = (LinearLayout)findViewById(R.id.login__main_layout);
    ViewGroup.MarginLayoutParams layoutParams = null;
    
    boolean hasMapFile   = (NavigineApp.Settings != null && NavigineApp.Settings.getString("map_file", "").length() > 0);
    boolean hasDebugMode = (NavigineApp.Settings != null && NavigineApp.Settings.getBoolean("debug_mode_enabled", false));
    findViewById(R.id.login__menu_measuring_mode).setVisibility(hasMapFile ? View.VISIBLE : View.GONE);
    findViewById(R.id.login__menu_navigation_mode).setVisibility(hasMapFile ? View.VISIBLE : View.GONE);
    findViewById(R.id.login__menu_debug_mode).setVisibility(hasDebugMode ? View.VISIBLE : View.GONE);
    
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
  
  private void startLogin()
  {
    if (NavigineApp.Navigation == null)
      return;
    
    String login = mLoginEdit.getText().toString();
    String password = mPasswordEdit.getText().toString();
    
    // Saving login and password
    SharedPreferences.Editor editor = NavigineApp.Settings.edit();
    editor.putString("login", login);
    editor.putString("password", password);
    editor.commit();
    
    mAuthTime = DateTimeUtils.currentTimeMillis();
    NavigineApp.Navigation.startAuthentication(login, password);
    mProgressBar.setVisibility(View.VISIBLE);
    mLoginButton.setEnabled(false);
    mStatusLabel.setText("");
  }
  
  private void stopLogin()
  {
    if (NavigineApp.Navigation == null)
      return;
    
    NavigineApp.Navigation.stopAuthentication();
    mProgressBar.setVisibility(View.INVISIBLE);
    mLoginButton.setEnabled(true);
  }
  
  private void updateLogin()
  {
    if (NavigineApp.Navigation == null)
      return;
    
    String status = NavigineApp.Navigation.getAuthenticationStatus();
    //Log.d(TAG, "Authentication status: " + status);
    
    if (status.equals("AUTH_NOT_STARTED"))
    {
      mProgressBar.setVisibility(View.INVISIBLE);
      return;
    }
    else if (status.equals("AUTH_NOT_READY"))
    {
      long timeNow = DateTimeUtils.currentTimeMillis();
      if (Math.abs(timeNow - mAuthTime) > AUTH_TIMEOUT)
      {
        mProgressBar.setVisibility(View.INVISIBLE);
        mStatusLabel.setText("Проверьте соединение с интернет");
        stopLogin();
      }
      else
      {
        mProgressBar.setVisibility(View.VISIBLE);
        mStatusLabel.setText("");
      }
      return;
    }
    else if (status.equals("AUTH_SERVER_ERROR"))
    {
      mProgressBar.setVisibility(View.INVISIBLE);
      mStatusLabel.setText("Внутренняя ошибка");
      stopLogin();
      return;
    }
    else if (status.equals("AUTH_FAILED"))
    {
      mProgressBar.setVisibility(View.INVISIBLE);
      mStatusLabel.setText("Неверный логин/пароль");
      stopLogin();
      return;
    }
    else if (status.equals("AUTH_SUCCESS"))
    {
      mProgressBar.setVisibility(View.INVISIBLE);
      NavigineApp.login(NavigineApp.Navigation.getAuthenticatedUser());
      
      cleanup();
      Intent intent = new Intent(mContext, LoaderActivity.class);
      startActivity(intent);
    }
  }
  
  private void update()
  {
    mHandler.post(mRunnable);
  }
  
  final Runnable mRunnable =
    new Runnable()
    {
      public void run()
      {
        updateLogin();
      }
    };
}
