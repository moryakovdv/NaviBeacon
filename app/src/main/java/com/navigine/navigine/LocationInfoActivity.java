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

public class LocationInfoActivity extends Activity
{
  // Constants
  private static final String TAG = "LocationInfoActivity";
  private static final int UPDATE_TIMEOUT       = 500;
  private static final int TOUCH_SHORT_TIMEOUT  = 200;
  private static final int TOUCH_SENSITIVITY    = 20;
  
  // This context
  private final Context mContext = this;
  
  private TextView      mTitleLabel       = null;
  private TextView      mVersionLabel     = null;
  private TextView      mSublocationLabel = null;
  private TextView      mSizeLabel        = null;
  private ImageView     mImageView        = null;
  private TimerTask     mTimerTask        = null;
  private Timer         mTimer            = new Timer();
  private Handler       mHandler          = new Handler();
  
  private Matrix        mMatrix           = null;
  private float         mScrollX          = 0.0f;
  
  private LocationInfo  mLocationInfo     = new LocationInfo();
  private Location      mLocation         = null;
  private int           mSublocationIndex = -1;
  
  private long          mTouchTime        = 0;
  private float         mTouchLength      = 0.0f;
  private PointF        mTouchPoint0      = null;
  private PointF        mTouchPoint1      = null;
  
  /** Called when the activity is first created */
  @Override public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.location_info);
    
    //NavigineApp.startSentry(mContext);
    
    // Extracting parameters from the intent
    Bundle b = getIntent().getExtras();
    mLocationInfo.id = b.getInt("location_id");
    mLocationInfo.title = b.getString("location_title");
    mLocationInfo.description = b.getString("location_description");
    mLocationInfo.archiveFile = b.getString("location_archive_file");
    mLocationInfo.localModified = b.getBoolean("location_modified");
    mLocationInfo.localVersion = b.getInt("location_version");
    
    mTitleLabel       = (TextView)findViewById(R.id.location_info__title_text_view);
    mSizeLabel        = (TextView)findViewById(R.id.location_info__size_label);
    mVersionLabel     = (TextView)findViewById(R.id.location_info__version_label);
    mSublocationLabel = (TextView)findViewById(R.id.location_info__sublocation_label);
    mImageView        = (ImageView)findViewById(R.id.location_info__map_image);
    
    mImageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    mImageView.setBackgroundColor(Color.argb(0xFF, 0xE6, 0xE6, 0xE6));
    
    mTitleLabel.setText(mLocationInfo.title);
    mVersionLabel.setText(String.format(Locale.ENGLISH,
                                        mLocationInfo.localModified ? "%d+" : "%d",
                                        mLocationInfo.localVersion));
    
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                         WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    
    if (NavigineApp.Navigation == null)
      return;
    
    mLocation = NavigineApp.Navigation.lookupArchive(mLocationInfo.archiveFile);
    
    // Setting up touch listener
    mImageView.setOnTouchListener(
      new OnTouchListener()
      {
        @Override public boolean onTouch(View v, MotionEvent event)
        {
          doTouch(event);
          return true;
        }
      });
  }
  
  @Override public void onStart()
  {
    Log.d(TAG, "LocationInfoActivity started");
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
          mHandler.post(mRunnable);
        }
      };
    mTimer.schedule(mTimerTask, UPDATE_TIMEOUT, UPDATE_TIMEOUT);
  }
  
  @Override public void onStop()
  {
    Log.d(TAG, "LocationInfoActivity stopped");
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
    finish();
  }
  
  public void onBackButtonClicked(View v)
  {
    finish();
  }
  
  private void doTouch(MotionEvent event)
  {
    long timeNow = DateTimeUtils.currentTimeMillis();
    int actionMask = event.getActionMasked();
    int pointerIndex = event.getActionIndex();
    int pointerCount = event.getPointerCount();
    
    if (pointerCount != 1)
      return;
    
    PointF P = new PointF(event.getX(0), event.getY(0));
    
    //Log.d(TAG, String.format(Locale.ENGLISH, "MOTION EVENT: %d", actionMask));
    
    switch (actionMask)
    {
      case MotionEvent.ACTION_DOWN:
      {
        mTouchTime    = timeNow;
        mTouchPoint0  = P;
        mTouchPoint1  = P;
        mTouchLength  = 0.0f;
        Log.d(TAG, String.format(Locale.ENGLISH, "Action down (%.2f, %.2f)", P.x, P.y));
        break;
      }
      
      case MotionEvent.ACTION_MOVE:
      {
        if (mTouchPoint0 == null || mTouchPoint1 == null)
          break;
        Log.d(TAG, String.format(Locale.ENGLISH, "Action move %.2f", P.x - mTouchPoint0.x));
        mTouchLength += Math.abs(P.x - mTouchPoint1.x);
        if ((P.x - mTouchPoint1.x >= 0.0f && mScrollX < mImageView.getWidth() / 2) ||
            (P.x - mTouchPoint1.x <= 0.0f && mScrollX > -mImageView.getWidth() / 2))
        {
          mScrollX += P.x - mTouchPoint1.x;
          mMatrix.postTranslate(P.x - mTouchPoint1.x, 0);
          mHandler.post(mRunnable);
        }
        mTouchPoint1 = P;
        if (P.x - mTouchPoint0.x < -mImageView.getWidth() / 2)
          loadSublocation(mSublocationIndex + 1);
        else if (P.x - mTouchPoint0.x > mImageView.getWidth() / 2)
          loadSublocation(mSublocationIndex - 1);
        break;
      }
      
      case MotionEvent.ACTION_UP:
      {
        if (mTouchTime > 0 &&
            mTouchTime + TOUCH_SHORT_TIMEOUT > timeNow &&
            mTouchLength < TOUCH_SENSITIVITY * NavigineApp.DisplayDensity)
        {
          Log.d(TAG, "Single touch detected");
        }
        mMatrix.postTranslate(-mScrollX, 0);
        mHandler.post(mRunnable);
        mScrollX      = 0.0f;
        mTouchTime    = 0;
        mTouchLength  = 0;
        mTouchPoint0  = null;
        mTouchPoint1  = null;
        Log.d(TAG, String.format(Locale.ENGLISH, "Action up (%.2f, %.2f)", P.x, P.y));
        break;
      }
      
      default:
      {
        mTouchTime    = 0;
        mTouchLength  = 0;
        mTouchPoint0  = null;
        mTouchPoint1  = null;
        return;
      }
    }
  }
  
  private void loadSublocation(int index)
  {
    if (NavigineApp.Navigation == null)
      return;
    
    if (mLocation == null)
      return;
    
    if (index < 0 || index >= mLocation.subLocations.size())
      return;
    
    if (mImageView.getWidth() <= 0 || mImageView.getHeight() <= 0)
      return;
    
    mSublocationIndex  = index;
    SubLocation subLoc = mLocation.subLocations.get(index);
    subLoc.getPicture();
    subLoc.getBitmap();
    
    Log.d(TAG, String.format(Locale.ENGLISH, "Loading sublocation %d: '%s'",
                             index, subLoc.name));
    
    // Setting up sublocation name and size
    mSublocationLabel.setText(subLoc.name);
    mSizeLabel.setText(String.format(Locale.ENGLISH, "%.1fm x %.1fm", subLoc.width, subLoc.height));
    
    int viewWidth  = mImageView.getWidth();
    int viewHeight = mImageView.getHeight();
    int mapWidth   = subLoc.picture == null ? subLoc.bitmap.getWidth()  : subLoc.picture.getWidth();
    int mapHeight  = subLoc.picture == null ? subLoc.bitmap.getHeight() : subLoc.picture.getHeight();
    float ratio    = Math.min((float)viewWidth / mapWidth, (float)viewHeight / mapHeight);
    float dx       = (viewWidth  - ratio * mapWidth) / 2;
    float dy       = (viewHeight - ratio * mapHeight) / 2;
    
    Log.d(TAG, String.format(Locale.ENGLISH, "View size: %dx%d, map size: %dx%d, ratio: %.2f",
                             viewWidth, viewHeight,
                             mapWidth, mapHeight,
                             ratio));
    
    Drawable drawable = (subLoc.picture == null) ?
                        new BitmapDrawable(getResources(), subLoc.bitmap) :
                        new PictureDrawable(subLoc.picture);
    
    mScrollX = 0;
    mTouchPoint0 = null;
    mTouchPoint1 = null;
    mMatrix = new Matrix();
    mMatrix.postScale(ratio, ratio);
    mMatrix.postTranslate(dx, dy);
    mImageView.setImageDrawable(drawable);
    mImageView.setScaleType(ScaleType.MATRIX);
    mImageView.setImageMatrix(mMatrix);
  }
  
  final Runnable mRunnable =
    new Runnable()
    {
      public void run()
      {
        if (NavigineApp.Navigation == null)
          return;
        
        if (mLocation == null || mLocation.subLocations.size() == 0)
          return;
        
        if (mSublocationIndex < 0)
          loadSublocation(0);
        
        mImageView.invalidate();
        mImageView.setImageMatrix(mMatrix);
      }
    };

};
