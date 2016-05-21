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
import android.widget.LinearLayout.*;
import android.util.*;
import java.io.*;
import java.lang.*;
import java.nio.*;
import java.net.*;
import java.util.*;

public class BeaconActivity extends Activity
{
  // Constants
  private static final String TAG             = "NAVIGINE.BeaconActivity";
  private static final int    UPDATE_TIMEOUT  = 200;
  private static final int    LOADER_TIMEOUT  = 30000;
  private static final int    RELOAD_IMAGE_TIMEOUT = 300000; // Reload file every 5 minutes
  
  // This context
  private final Context mContext = this;
  
  // GUI parameters
  private TextView    mTitleLabel   = null;
  private TextView    mTextLabel    = null;
  private ImageView   mImageView    = null;
  private ProgressBar mProgressBar  = null;
  private TimerTask   mTimerTask    = null;
  private Handler     mHandler      = new Handler();
  private Timer       mTimer        = new Timer();
  
  private String      mTitle        = "";
  private String      mContent      = "";
  private String      mImageUrl     = "";
  private String      mImagePath    = "";
  private int         mLoader       = -1;
  private long        mLoaderTime   = 0;
  private boolean     mImageLoaded  = false;
  
  /** Called when the activity is first created */
  @Override public void onCreate(Bundle savedInstanceState)
  {
    Log.d(TAG, "BeaconActivity created");
    super.onCreate(savedInstanceState);
    
   // NavigineApp.startSentry(mContext);
    
    if (NavigineApp.Navigation == null)
      NavigineApp.initialize(getApplicationContext());
    
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.notification);
    
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                         WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    
    mTitle       = getIntent().getStringExtra("beacon_action_title");
    mContent     = getIntent().getStringExtra("beacon_action_content");
    mImageUrl    = getIntent().getStringExtra("beacon_action_image_url");
    mImagePath   = getIntent().getStringExtra("beacon_action_image_path");
    
    //mImageUrl    = "http://36.media.tumblr.com/tumblr_lvswa6enIm1r7jy1ao1_500.jpg";
    
    mTitleLabel  = (TextView)findViewById(R.id.notification__title_label);
    mTextLabel   = (TextView)findViewById(R.id.notification__text_label);
    mImageView   = (ImageView)findViewById(R.id.notification__image);
    mProgressBar = (ProgressBar)findViewById(R.id.notification__progress_bar);
    
    Log.d(TAG, "ImageUrl: '" + mImageUrl + "'");
    Log.d(TAG, "ImagePath: '" + mImagePath + "'");
    
    mTitleLabel.setText(mTitle.toUpperCase());
    mTextLabel.setText(mContent);
    mTextLabel.setMovementMethod(new ScrollingMovementMethod());
    
    if (mImageUrl.length() == 0)
    {
      mImageLoaded = true;
      mImageView.setBackgroundResource(R.drawable.elm_push_no_picture);
    }
    
    // Check image file 
    if (mImagePath.length() > 0)
    {
      //File file = new File(mImagePath);
      //if (file.exists())
      //{
      //  long lastModTime = file.lastModified();
      //  long timeNow = System.currentTimeMillis();
      //  if (timeNow - lastModTime > RELOAD_IMAGE_TIMEOUT)
      //  {
      //    Log.d(TAG, "Image file is too old: reloading image!");
      //    file.delete();
      //  }
      //  else
      //    Log.d(TAG, String.format(Locale.ENGLISH, "Image file will be realoded in %d secs",
      //          (int)((timeNow - lastModTime) / 1000L)));
      //}
    }
    
    // Starting interface updates
    mTimerTask = 
      new TimerTask()
      {
        @Override public void run() 
        {
          mHandler.post(mRunnable);
        }
      };
    mTimer.schedule(mTimerTask, UPDATE_TIMEOUT, UPDATE_TIMEOUT);
  }
  
  public void onClose(View v)
  {
    finish();
  }
  
  private boolean setImageFromFile(String filename)
  {
    if (!(new File(filename)).exists())
      return false;
    
    mImageLoaded = true;
    Log.d(TAG, "Setting image from file: " + filename);
    Bitmap bitmap = BitmapFactory.decodeFile(filename);
    
    if (bitmap == null)
    {
      Log.d(TAG, "Invalid image!");
      return false;
    }
    
    try
    {
      int scaledWidth  = 0;
      int scaledHeight = 0;
      if (bitmap.getWidth() * mImageView.getHeight() >= bitmap.getHeight() * mImageView.getWidth())
      {
        scaledWidth  = mImageView.getWidth();
        scaledHeight = mImageView.getWidth() * bitmap.getHeight() / Math.max(bitmap.getWidth(), 1);
      }
      else
      {
        scaledWidth  = mImageView.getHeight() * bitmap.getWidth() / Math.max(bitmap.getHeight(), 1);
        scaledHeight = mImageView.getHeight();
      }
      mImageView.getLayoutParams().width  = scaledWidth;
      mImageView.getLayoutParams().height = scaledHeight;
      mImageView.setImageBitmap(Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true));
      return true;
    }
    catch (Throwable e)
    {
      Log.e(TAG, Log.getStackTraceString(e));
      return false;
    }
  }
  
  private void updateLoader()
  {
    if (NavigineApp.Navigation == null)
      return;
    
    long timeNow = DateTimeUtils.currentTimeMillis();
    if (mLoader < 0)
      return;
    
    int status = LocationLoader.checkLocationLoader(mLoader);
    
    if (status >= 0 && status < 100)
    {
      if ((Math.abs(timeNow - mLoaderTime) > LOADER_TIMEOUT / 3 && status == 0) ||
          (Math.abs(timeNow - mLoaderTime) > LOADER_TIMEOUT))
      {
        // TODO: show notification
        Log.d(TAG, String.format(Locale.ENGLISH, "Download image: stopped on timeout!"));
        LocationLoader.stopLocationLoader(mLoader);
        mLoader = -1;
      }
    }
    else
    {
      Log.d(TAG, String.format(Locale.ENGLISH, "Download image: finished with result: %d", status));
      LocationLoader.stopLocationLoader(mLoader);
      mLoader = -1;
      if (status == 100)
      {
        setImageFromFile(mImagePath);
      }
    }
  }
  
  final Runnable mRunnable =
    new Runnable()
    {
      public void run()
      {
        if (mImageLoaded)
        {
          mImageView.setVisibility(View.VISIBLE);
          mProgressBar.setVisibility(View.GONE);
        }
        else
        {
          mProgressBar.setVisibility(View.VISIBLE);
          if (!setImageFromFile(mImagePath))
          {
            if (NavigineApp.Navigation != null)
            {
              mLoader = LocationLoader.startUrlLoader(mImageUrl, mImagePath);
              mLoaderTime = DateTimeUtils.currentTimeMillis();
            }
          }
          
          if (mLoader >= 0)
            updateLoader();
        }
      }
    };
}
