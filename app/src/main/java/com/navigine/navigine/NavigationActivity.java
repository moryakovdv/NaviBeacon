package com.navigine.navigine;
import com.navigine.navigine.*;
import com.navigine.naviginesdk.*;
import com.navigine.imu.*;

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

import android.bluetooth.*;

public class NavigationActivity extends Activity
{
  // Constants
  private static final String TAG = "NavigationActivity";
  private static final int UPDATE_TIMEOUT  = 100;
  private static final int ADJUST_TIMEOUT  = 3000;
  private static final int ERROR_MESSAGE_TIMEOUT = 5000;
  
  // This context
  private Context mContext = this;
  
  // GUI parameters
  private ImageView  mMapImageView        = null;
  private ImageView  mPicImageView        = null;
  private Button     mMenuButton          = null;
  private Button     mPrevFloorButton     = null;
  private Button     mNextFloorButton     = null;
  private View       mPrevFloorView       = null;
  private View       mNextFloorView       = null;
  private View       mZoomInView          = null;
  private View       mZoomOutView         = null;
  private View       mAdjustModeView      = null;
  private TextView   mCurrentFloorLabel   = null;
  private TextView   mNavigationInfoLabel = null;
  private TextView   mErrorMessageLabel   = null;
  private Button     mMakeRouteButton     = null;
  private Button     mCancelRouteButton   = null;
  private TimerTask  mTimerTask           = null;
  private Timer      mTimer               = new Timer();
  private Handler    mHandler             = new Handler();
  
  private boolean    mMapLoaded           = false;
  private boolean    mMenuVisible         = false;
  private boolean    mAdjustMode          = false;
  private boolean    mDebugModeEnabled    = false;
  private boolean    mOrientationEnabled  = false;
  
  private long       mErrorMessageTime    = 0;
  private int        mErrorMessageAction  = 0;
  
  // Image parameters
  int mMapWidth    = 0;
  int mMapHeight   = 0;
  int mViewWidth   = 0;
  int mViewHeight  = 0;
  RectF mMapRect   = null;
  Drawable mMapDrawable = null;
  PictureDrawable mPicDrawable = null;
  
  // Multi-touch parameters
  private static final int TOUCH_MODE_SCROLL = 1;
  private static final int TOUCH_MODE_ZOOM   = 2;
  private static final int TOUCH_MODE_ROTATE = 3;
  private static final int TOUCH_SENSITIVITY = 20;
  private static final int TOUCH_SHORT_TIMEOUT = 200;
  private static final int TOUCH_LONG_TIMEOUT  = 600;
  private long mTouchTime   = 0;
  private int  mTouchMode   = 0;
  private int  mTouchLength = 0;
  private PointF[] mTouchPoints = new PointF[] { new PointF(0.0f, 0.0f),
                                                 new PointF(0.0f, 0.0f),
                                                 new PointF(0.0f, 0.0f)};
  
  
  // Geometry parameters
  private Matrix  mMatrix        = null;
  private float   mRatio         = 1.0f;
  private float   mAdjustAngle   = 0.0f;
  private long    mAdjustTime    = 0;
  
  // Config parameters
  private float   mMaxX = 0.0f;
  private float   mMaxY = 0.0f;
  private float   mMinRatio = 0.1f;
  private float   mMaxRatio = 10.0f;
  
  // Device parameters
  private DeviceInfo mDeviceInfo = null;      // Current device
  private LocationPoint mPinPoint = null;     // Potential device target
  private LocationPoint mPinPoint2 = null;    // Delayed device target
  private LocationPoint mTargetPoint = null;  // Current device target
  
  // Location parameters
  private Location mLocation = null;
  private int mCurrentSubLocationIndex = -1;
  
  // IMU parameters
  private boolean mImuMode      = false;
  private int     mImuState     = 0;
  private long    mImuStateTime = 0;
  
  /** Called when the activity is first created */
  @Override public void onCreate(Bundle savedInstanceState)
  {
    Log.d(TAG, "NavigationActivity created");
    Log.d(TAG, String.format(Locale.ENGLISH, "Android API LEVEL: %d",
          android.os.Build.VERSION.SDK_INT));
    
    super.onCreate(savedInstanceState);    
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.navigation);
    
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                         WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    
    if (NavigineApp.Navigation == null)
    {
      finish();
      return;
    }
    
    // Setting up GUI parameters
    mMapImageView = (ImageView)findViewById(R.id.navigation__map_image);
    mPicImageView = (ImageView)findViewById(R.id.navigation__ext_image);
    mMenuButton = (Button)findViewById(R.id.navigation__menu_button);
    mPrevFloorButton = (Button)findViewById(R.id.navigation__prev_floor_button);
    mNextFloorButton = (Button)findViewById(R.id.navigation__next_floor_button);
    mPrevFloorView = (View)findViewById(R.id.navigation__prev_floor_view);
    mNextFloorView = (View)findViewById(R.id.navigation__next_floor_view);
    mCurrentFloorLabel = (TextView)findViewById(R.id.navigation__current_floor_label);
    mZoomInView  = (View)findViewById(R.id.navigation__zoom_in_view);
    mZoomOutView = (View)findViewById(R.id.navigation__zoom_out_view);
    mAdjustModeView = (View)findViewById(R.id.navigation__adjust_mode_view);
    mNavigationInfoLabel = (TextView)findViewById(R.id.navigation__info_label);
    mMakeRouteButton = (Button)findViewById(R.id.navigation__make_route_button);
    mCancelRouteButton = (Button)findViewById(R.id.navigation__cancel_route_button);
    mErrorMessageLabel = (TextView)findViewById(R.id.navigation__error_message_label);
    
    mMapImageView.setBackgroundColor(Color.argb(255, 235, 235, 235));
    mPicImageView.setBackgroundColor(Color.argb(0, 0, 0, 0));
    mPicImageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    
    mMakeRouteButton.setVisibility(View.GONE);
    mCancelRouteButton.setVisibility(View.GONE);
    mErrorMessageLabel.setVisibility(View.GONE);
    
    mPrevFloorView.setVisibility(View.INVISIBLE);
    mNextFloorView.setVisibility(View.INVISIBLE);
    mCurrentFloorLabel.setVisibility(View.INVISIBLE);
    mZoomInView.setVisibility(View.INVISIBLE);
    mZoomOutView.setVisibility(View.INVISIBLE);
    mAdjustModeView.setVisibility(View.INVISIBLE);
    
    // Setting up touch listener
    mMapImageView.setOnTouchListener(
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
    Log.d(TAG, "NavigationActivity started");
    super.onStart();
    
    // Reading settings
    if (NavigineApp.Settings != null)
    {
      mOrientationEnabled = NavigineApp.Settings.getBoolean("orientation_enabled", false);
      mDebugModeEnabled   = NavigineApp.Settings.getBoolean("debug_mode_enabled", false);
    }
    
    // Stop interface updates
    if (mTimerTask != null)
    {
      mTimerTask.cancel();
      mTimerTask = null;
    }
    
    // Start interface updates
    mTimerTask = 
      new TimerTask()
      {
        @Override public void run() 
        {
          mHandler.post(mRunnable);
        }
      };
    mTimer.schedule(mTimerTask, UPDATE_TIMEOUT, UPDATE_TIMEOUT);
    
    // Switch to foreground mode
    NavigineApp.setBackgroundMode(false);
  }
  
  @Override public void onStop()
  {
    Log.d(TAG, "NavigationActivity stopped");
    super.onStop();
    
    // Stop interface updates
    if (mTimerTask != null)
    {
      mTimerTask.cancel();
      mTimerTask = null;
    }
    
    // Switch to background mode
    NavigineApp.setBackgroundMode(true);
  }
  
  @Override public void onBackPressed()
  {
    toggleMenuLayout(null);
  }
  
  private void cleanup()
  {
    // Stop navigation & scanning
    NavigineApp.stopNavigation();
    NavigineApp.stopScanning();
    
    // Stop interfaceupdates
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
    
    cleanup();
    
    Intent intent = new Intent(mContext, LoaderActivity.class);
    startActivity(intent);
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
    LinearLayout topLayout  = (LinearLayout)findViewById(R.id.navigation__top_layout);
    LinearLayout menuLayout = (LinearLayout)findViewById(R.id.navigation__menu_layout);
    FrameLayout  mainLayout = (FrameLayout)findViewById(R.id.navigation__main_layout);
    ViewGroup.MarginLayoutParams layoutParams = null;
    
    boolean hasMapFile   = (NavigineApp.Settings != null && NavigineApp.Settings.getString("map_file", "").length() > 0);
    boolean hasDebugMode = (NavigineApp.Settings != null && NavigineApp.Settings.getBoolean("debug_mode_enabled", false));
    findViewById(R.id.navigation__menu_measuring_mode).setVisibility(hasMapFile ? View.VISIBLE : View.GONE);
    findViewById(R.id.navigation__menu_navigation_mode).setVisibility(hasMapFile ? View.VISIBLE : View.GONE);
    findViewById(R.id.navigation__menu_debug_mode).setVisibility(hasDebugMode ? View.VISIBLE : View.GONE);
    
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
  
  public void onNextFloor(View v)
  {
    if (mMenuVisible)
    {
      toggleMenuLayout(null);
      return;
    }
    
    if (loadNextSubLocation())
      mAdjustTime = DateTimeUtils.currentTimeMillis() + ADJUST_TIMEOUT;
  }
  
  public void onPrevFloor(View v)
  {
    if (mMenuVisible)
    {
      toggleMenuLayout(null);
      return;
    }
    
    if (loadPrevSubLocation())
      mAdjustTime = DateTimeUtils.currentTimeMillis() + ADJUST_TIMEOUT;
  }
  
  public void onZoomIn(View v)
  {
    if (mMenuVisible)
    {
      toggleMenuLayout(null);
      return;
    }
    
    doZoom(1.25f);
  }
  
  public void onZoomOut(View v)
  {
    if (mMenuVisible)
    {
      toggleMenuLayout(null);
      return;
    }
    
    doZoom(0.8f);
  }
  
  public void onMakeRoute(View v)
  {
    if (mMenuVisible)
    {
      toggleMenuLayout(null);
      return;
    }
    
    if (NavigineApp.Navigation == null)
      return;
    
    if (mPinPoint == null)
      return;
    
    mTargetPoint = mPinPoint;
    mPinPoint = null;
    
    NavigineApp.Navigation.setTarget(mTargetPoint);
    mMakeRouteButton.setVisibility(View.GONE);
    mHandler.post(mRunnable);
  }
  
  public void onCancelRoute(View v)
  {
    if (mMenuVisible)
    {
      toggleMenuLayout(null);
      return;
    }
    
    if (NavigineApp.Navigation == null)
      return;
    
    mTargetPoint = null;
    mPinPoint = null;
    
    NavigineApp.Navigation.cancelTargets();
    mMakeRouteButton.setVisibility(View.GONE);
    mCancelRouteButton.setVisibility(View.GONE);
    mHandler.post(mRunnable);
  }
  
  public void onCloseMessage(View v)
  {
    if (mMenuVisible)
    {
      toggleMenuLayout(null);
      return;
    }
    
    switch (mErrorMessageAction)
    {
      case 1:
      {
        onCancelRoute(null);
        if (mPinPoint2 != null)
        {
          mPinPoint = mPinPoint2;
          mPinPoint2 = null;
          mHandler.post(mRunnable);
        }
        break;
      }
    }
    
    mErrorMessageLabel.setVisibility(View.GONE);
    mErrorMessageTime = 0;
    mErrorMessageAction = 0;
  }
  
  public void onImuMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
    
    Log.d(TAG, "TODO: switching to the imu mode");
    
    //mImuMode = true;
  }
  
  public void toggleAdjustMode(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
    
    mAdjustMode = !mAdjustMode;
    mAdjustTime = 0;
    Button adjustModeButton = (Button)findViewById(R.id.navigation__adjust_mode_button);
    adjustModeButton.setBackgroundResource(mAdjustMode ?
                                           R.drawable.btn_adjust_mode_on :
                                           R.drawable.btn_adjust_mode_off);
    mHandler.post(mRunnable);
  }
  
  public void setErrorMessage(String message, int action)
  {
    mErrorMessageLabel.setText(message);
    mErrorMessageLabel.setVisibility(View.VISIBLE);
    mErrorMessageTime = DateTimeUtils.currentTimeMillis();
    mErrorMessageAction = action;
  }
  
  private boolean tryLoadMap()
  {
    if (mMapLoaded)
      return false;    
    mMapLoaded = true;
    
    if (NavigineApp.Navigation == null)
    {
      Toast.makeText(mContext, "Ошибка загрузки карты. SDK недоступно", Toast.LENGTH_LONG).show();
      return false;
    }
    
    String filename = NavigineApp.Settings.getString("map_file", "");
    if (filename.length() == 0)
      return false;
    
    if (!NavigineApp.Navigation.loadArchive(filename))
    {
      String error = NavigineApp.Navigation.getLastError();
      if (error != null)
        Toast.makeText(mContext, error, Toast.LENGTH_LONG).show();
      return false;
    }
    
    mLocation = NavigineApp.Navigation.getLocation();
    mCurrentSubLocationIndex = -1;
    mMatrix = null;
    
    if (mLocation == null)
    {
      String text = "Ошибка загрузки карты. Нет области";
      Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
      Log.e(TAG, text);
      return false;
    }
    
    if (mLocation.subLocations.size() == 0)
    {
      String text = "Ошибка загрузки карты. Нет подуровней";
      Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
      Log.e(TAG, text);
      mLocation = null;
      return false;
    }
    
    if (!loadSubLocation(0))
    {
      String text = "Ошибка загрузки карты. Нет подуровня по-умолчанию";
      Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
      Log.e(TAG, text);
      mLocation = null;
      return false;
    }
    
    if (mLocation.subLocations.size() >= 2)
    {
      mPrevFloorView.setVisibility(View.VISIBLE);
      mNextFloorView.setVisibility(View.VISIBLE);
      mCurrentFloorLabel.setVisibility(View.VISIBLE);
    }
    mZoomInView.setVisibility(View.VISIBLE);
    mZoomOutView.setVisibility(View.VISIBLE);
    mAdjustModeView.setVisibility(View.VISIBLE);
    
    mHandler.post(mRunnable);
    NavigineApp.startNavigation();
    return true;
  }
  
  private boolean loadSubLocation(int index)
  {
    if (mLocation == null || index < 0 || index >= mLocation.subLocations.size())
      return false;
    
    SubLocation subLoc = mLocation.subLocations.get(index);
    Log.d(TAG, String.format(Locale.ENGLISH, "Loading sublocation %s", subLoc.name));
    
    double[] gpsCoords = subLoc.getGpsCoordinates(0, 0);
    Log.d(TAG, String.format(Locale.ENGLISH, "GPS: (%.8f, %.8f)",
          gpsCoords[0], gpsCoords[1]));
    
    subLoc.getPicture();
    subLoc.getBitmap();
    
    if (subLoc.picture == null && subLoc.bitmap == null)
    {
      String text = "Не могу загрузить подуровень. Изображение не читается";
      Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
      Log.e(TAG, text);
      return false;
    }
    
    if (subLoc.width == 0.0f || subLoc.height == 0.0f)
    {
      String text = String.format(Locale.ENGLISH, "Не могу загрузить подуровень: неверный размер: %.2f x %.2f",
                                  subLoc.width, subLoc.height);
      Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
      Log.e(TAG, text);
      return false;
    }
    
    Log.d(TAG, String.format(Locale.ENGLISH, "Loading sublocation: %.2f x %.2f\n",
                             subLoc.width, subLoc.height));
    
    mViewWidth  = mMapImageView.getWidth();
    mViewHeight = mMapImageView.getHeight();
    Log.d(TAG, String.format(Locale.ENGLISH, "View size: %dx%d", mViewWidth, mViewHeight));
    
    // Updating image view size parameters
    float pixLength = 0.0f;
    if (mMatrix != null && mMapWidth > 0 && mRatio > 0)
      pixLength = mMaxX / mMapWidth / mRatio; // Pixel length in meters
    
    // Determine absolute coordinates of the screen center
    PointF P = null;
    if (mMatrix != null)
      P = getAbsCoordinates(mViewWidth / 2, mViewHeight / 2);
    
    mMapWidth   = subLoc.picture == null ? subLoc.bitmap.getWidth()  : subLoc.picture.getWidth();
    mMapHeight  = subLoc.picture == null ? subLoc.bitmap.getHeight() : subLoc.picture.getHeight();
    mMapRect    = new RectF(0, 0, mMapWidth, mMapHeight);
    
    Log.d(TAG, String.format(Locale.ENGLISH, "Map size: %dx%d", mMapWidth, mMapHeight));
    
    Picture pic = new Picture();
    pic.beginRecording(mViewWidth, mViewHeight);
    pic.endRecording();
    
    mMapDrawable = subLoc.picture == null ? new BitmapDrawable(getResources(), subLoc.bitmap) : new PictureDrawable(subLoc.picture);
    mPicDrawable = new PictureDrawable(pic);
    
    mMapImageView.setImageDrawable(mMapDrawable);
    mMapImageView.setScaleType(ScaleType.MATRIX);
    mPicImageView.setImageDrawable(mPicDrawable);
    
    // Reinitializing map/matrix parameters
    mMatrix      = new Matrix();
    mMaxX        = subLoc.width;
    mMaxY        = subLoc.height;
    mRatio       = 1.0f;
    mMinRatio    = Math.min((float)mViewWidth / mMapWidth, (float)mViewHeight / mMapHeight);
    mMaxRatio    = Math.min((float)mViewWidth / mMapWidth * subLoc.width / 2, (float)mViewHeight / mMapHeight * subLoc.height / 2);
    mMaxRatio    = Math.max(mMaxRatio, mMinRatio);
    mAdjustAngle = 0.0f;
    mAdjustTime  = 0;
    
    // Calculating new pixel length in meters
    if (mMapWidth > 0 && pixLength > 0.0f)
      doZoom(subLoc.width / mMapWidth / pixLength);
    
    if (P != null)
    {
      PointF Q = getScreenCoordinates(P.x, P.y);
      doScroll(mViewWidth / 2 - Q.x, mViewHeight / 2 - Q.y);
    }
    else
    {
      doScroll(mViewWidth / 2 - mMapWidth / 2, mViewHeight / 2 - mMapHeight / 2);
      doZoom(mMinRatio);
    }
    
    mCurrentSubLocationIndex = index;
    mCurrentFloorLabel.setText(String.format(Locale.ENGLISH, "%d", mCurrentSubLocationIndex));
    
    if (mCurrentSubLocationIndex > 0)
    {
      mPrevFloorButton.setEnabled(true);
      mPrevFloorView.setBackgroundColor(Color.parseColor("#90aaaaaa"));
    }
    else
    {
      mPrevFloorButton.setEnabled(false);
      mPrevFloorView.setBackgroundColor(Color.parseColor("#90dddddd"));
    }
    
    if (mCurrentSubLocationIndex + 1 < mLocation.subLocations.size())
    {
      mNextFloorButton.setEnabled(true);
      mNextFloorView.setBackgroundColor(Color.parseColor("#90aaaaaa"));
    }
    else
    {
      mNextFloorButton.setEnabled(false);
      mNextFloorView.setBackgroundColor(Color.parseColor("#90dddddd"));
    }
    
    mHandler.post(mRunnable);
    return true;
  }
  
  private boolean loadNextSubLocation()
  {
    if (mLocation == null || mCurrentSubLocationIndex < 0)
      return false;
    return loadSubLocation(mCurrentSubLocationIndex + 1);
  }
  
  private boolean loadPrevSubLocation()
  {
    if (mLocation == null || mCurrentSubLocationIndex < 0)
      return false;
    return loadSubLocation(mCurrentSubLocationIndex - 1);
  }
  
  private void doScroll(float deltaX, float deltaY)
  {
    if (mMatrix == null)
      return;
    //Log.d(TAG, String.format(Locale.ENGLISH, "Scroll by vector: (%.2f, %.2f)", deltaX, deltaY));
    float maxDeltaX = mViewWidth  / 2 - mMapRect.left;
    float minDeltaX = mViewWidth  / 2 - mMapRect.right;
    float maxDeltaY = mViewHeight / 2 - mMapRect.top;
    float minDeltaY = mViewHeight / 2 - mMapRect.bottom;
    //Log.d(TAG, String.format(Locale.ENGLISH, "Scroll bounds: dx: %.2f..%.2f, dy: %.2f..%.2f",
    //      minDeltaX, maxDeltaX, minDeltaY, maxDeltaY));
    deltaX = Math.max(Math.min(deltaX, maxDeltaX), minDeltaX);
    deltaY = Math.max(Math.min(deltaY, maxDeltaY), minDeltaY);
    
    mMatrix.postTranslate(deltaX, deltaY);
    mMatrix.mapRect(mMapRect, new RectF(0, 0, mMapWidth, mMapHeight));
    //Log.d(TAG, String.format(Locale.ENGLISH, "Map rect: (%.2f, %.2f) - (%.2f, %.2f)",
    //      mMapRect.left, mMapRect.top, mMapRect.right, mMapRect.bottom));
  }
  
  private void doZoom(float ratio)
  {
    if (mMatrix == null)
      return;
    //Log.d(TAG, String.format(Locale.ENGLISH, "Zoom by ratio: %.2f", ratio));
    float r = Math.max(Math.min(ratio, mMaxRatio / mRatio), mMinRatio / mRatio);
    mMatrix.postScale(r, r, mViewWidth / 2, mViewHeight / 2);
    mMatrix.mapRect(mMapRect, new RectF(0, 0, mMapWidth, mMapHeight));
    mRatio *= r;
    //Log.d(TAG, String.format(Locale.ENGLISH, "Map rect: (%.2f, %.2f) - (%.2f, %.2f)",
    //      mMapRect.left, mMapRect.top, mMapRect.right, mMapRect.bottom));
  }
  
  private void doRotate(float angle, float x, float y)
  {
    if (mMatrix == null)
      return;
    //Log.d(TAG, String.format(Locale.ENGLISH, "Rotate: angle=%.2f, center=(%.2f, %.2f)", angle, x, y));
    float angleInDegrees = angle * 180.0f / (float)Math.PI;
    mMatrix.postRotate(angleInDegrees, x, y);
    mMatrix.mapRect(mMapRect, new RectF(0, 0, mMapWidth, mMapHeight));
    //Log.d(TAG, String.format(Locale.ENGLISH, "Map rect: (%.2f, %.2f) - (%.2f, %.2f)",
    //      mMapRect.left, mMapRect.top, mMapRect.right, mMapRect.bottom));
  }
  
  // Convert absolute coordinates (x,y) to SVG coordinates
  private PointF getSvgCoordinates(float x, float y)
  {
    return new PointF(x / mMaxX * mMapWidth, (mMaxY - y) / mMaxY * mMapHeight);
  }
  
  private float getSvgLength(float d)
  {
    return Math.max(d * mMapWidth / mMaxX, d * mMapHeight / mMaxY);
  }
  
  // Convert absolute coordinates (x,y) to screen coordinates
  private PointF getScreenCoordinates(float x, float y)
  {
    float[] pts = {x / mMaxX * mMapWidth, (mMaxY - y) / mMaxY * mMapHeight};
    mMatrix.mapPoints(pts);
    return new PointF(pts[0], pts[1]);
  }
  
  private float getScreenLength(float d)
  {
    return getSvgLength(d) * mRatio;
  }
  
  // Convert screen coordinates (x,y) to absolute coordinates
  private PointF getAbsCoordinates(float x, float y)
  {
    Matrix invMatrix = new Matrix();
    mMatrix.invert(invMatrix);
    
    float[] pts = {x, y};
    invMatrix.mapPoints(pts);
    return new PointF( pts[0] / mMapWidth  * mMaxX,
                      -pts[1] / mMapHeight * mMaxY + mMaxY);
  }
  
  private void doTouch(MotionEvent event)
  {
    long timeNow = DateTimeUtils.currentTimeMillis();
    int actionMask = event.getActionMasked();
    int pointerIndex = event.getActionIndex();
    int pointerCount = event.getPointerCount();
    
    PointF[] points = new PointF[pointerCount];
    for(int i = 0; i < pointerCount; ++i)
      points[i] = new PointF(event.getX(i), event.getY(i));
    
    //Log.d(TAG, String.format(Locale.ENGLISH, "MOTION EVENT: %d", actionMask));
    
    switch (actionMask)
    {
      case MotionEvent.ACTION_DOWN:
      {
        // Gesture started
        mTouchPoints[0].set(points[0]);
        mTouchTime   = timeNow;
        mTouchMode   = 0;
        mTouchLength = 0;
        return;
      }
      
      case MotionEvent.ACTION_MOVE:
      {
        if (pointerCount == 1)
        {
          if (mTouchMode == TOUCH_MODE_SCROLL)
          {
            float deltaX = points[0].x - mTouchPoints[0].x;
            float deltaY = points[0].y - mTouchPoints[0].y;
            mTouchLength += Math.abs(deltaX);
            mTouchLength += Math.abs(deltaY);
            if (mTouchLength > TOUCH_SENSITIVITY * NavigineApp.DisplayDensity)
              mTouchTime = 0;
            doScroll(deltaX, deltaY);
            mAdjustTime = timeNow + ADJUST_TIMEOUT;
            mHandler.post(mRunnable);
          }
          mTouchMode = TOUCH_MODE_SCROLL;
          mTouchPoints[0].set(points[0]);
        }
        else if (pointerCount == 2)
        {
          if (mTouchMode == TOUCH_MODE_ZOOM)
          {
            float oldDist = PointF.length(mTouchPoints[0].x - mTouchPoints[1].x, mTouchPoints[0].y - mTouchPoints[1].y);
            float newDist = PointF.length(points[0].x - points[1].x, points[0].y - points[1].y);
            oldDist = Math.max(oldDist, 1.0f);
            newDist = Math.max(newDist, 1.0f);
            float ratio = newDist / oldDist;
            doZoom(ratio);
            mHandler.post(mRunnable);
          }
          mTouchMode = TOUCH_MODE_ZOOM;
          mTouchPoints[0].set(points[0]);
          mTouchPoints[1].set(points[1]);
        }
        return;
      }
      
      case MotionEvent.ACTION_UP:
      {
        // Gesture stopped. Check if it was a single tap
        //Log.d(TAG, String.format(Locale.ENGLISH, "ACTION UP: %d %d\n", (int)(timeNow - mTouchTime), mTouchLength));
        if (mTouchTime > 0 &&
            mTouchTime + TOUCH_SHORT_TIMEOUT > timeNow &&
            mTouchLength < TOUCH_SENSITIVITY * NavigineApp.DisplayDensity)
        {
          doShortTouch(mTouchPoints[0].x, mTouchPoints[0].y);
        }
        mTouchTime    = 0;
        mTouchMode    = 0;
        mTouchLength  = 0;
        return;
      }
      
      default:
      {
        mTouchTime    = 0;
        mTouchMode    = 0;
        mTouchLength  = 0;
        return;
      }
    }
  }
  
  private void doShortTouch(float x, float y)
  {
    Log.d(TAG, String.format(Locale.ENGLISH, "Short click at (%.2f, %.2f)", x, y));
    
    if (mMenuVisible)
    {
      toggleMenuLayout(null);
      return;
    }
    
    if (mPinPoint != null)
    {
      cancelPin();
      return;
    }
    
    if (mTargetPoint == null)
      return;
    
    if (mCancelRouteButton.getVisibility() == View.VISIBLE)
    {
      mCancelRouteButton.setVisibility(View.GONE);
      return;
    }
    
    // Check if we touched pin point => highlight cancel button
    PointF P = getScreenCoordinates(mTargetPoint.x, mTargetPoint.y);
    float dist = Math.abs(x - P.x) + Math.abs(y - P.y);
    if (dist < 30.0f * NavigineApp.DisplayDensity)
      mCancelRouteButton.setVisibility(View.VISIBLE);
  }
  
  private void doLongTouch(float x, float y)
  {
    if (mMenuVisible)
    {
      toggleMenuLayout(null);
      return;
    }
    
    Log.d(TAG, String.format(Locale.ENGLISH, "Long click at (%.2f, %.2f)", x, y));
    makePin(getAbsCoordinates(x, y));
  }
  
  private void makePin(PointF P)
  {
    if (mLocation == null || mCurrentSubLocationIndex < 0)
      return;
    
    SubLocation subLoc = mLocation.subLocations.get(mCurrentSubLocationIndex);
    if (subLoc == null)
      return;
    
    if (P.x < 0.0f || P.x > mMaxX ||
        P.y < 0.0f || P.y > mMaxY)
    {
      // Missing the map
      return;
    }
    
    if (mTargetPoint != null)
    {
      setErrorMessage("Сначала отмените предыдущий маршрут, тапнув тут", 1);
      mPinPoint2 = new LocationPoint(subLoc.id, P.x, P.y);
      return;
    }
    
    if (mDeviceInfo == null)
    {
      setErrorMessage("Не могу построить маршрут, навигация недоступна!", 0);
      return;
    }
    
    mPinPoint = new LocationPoint(subLoc.id, P.x, P.y);
    mHandler.post(mRunnable);
  }
  
  private void cancelPin()
  {
    if (mLocation == null || mCurrentSubLocationIndex < 0)
      return;
    
    SubLocation subLoc = mLocation.subLocations.get(mCurrentSubLocationIndex);
    if (subLoc == null)
      return;
    
    if (mTargetPoint != null || mPinPoint == null)
      return;
    
    mPinPoint = null;
    mMakeRouteButton.setVisibility(View.GONE);
    mHandler.post(mRunnable);
  }
  
  private void drawDevice(DeviceInfo info, Canvas canvas)
  {
    if (info == null)
      return;
    
    // Check if location is loaded
    if (mLocation == null || mCurrentSubLocationIndex < 0)
      return;
    
    // Check if device belongs to the location loaded
    if (info.location != mLocation.id)
      return;
    
    // Get current sublocation displayed
    SubLocation subLoc = mLocation.subLocations.get(mCurrentSubLocationIndex);
    
    if (subLoc == null)
      return;
    
    final int solidColor  = Color.argb(255, 64,  163, 205); // Light-blue color
    final int circleColor = Color.argb(127, 64,  163, 205); // Semi-transparent light-blue color
    final int arrowColor  = Color.argb(255, 255, 255, 255); // White color
    final float dp = NavigineApp.DisplayDensity;
    
    // Preparing paints
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setStyle(Paint.Style.FILL_AND_STROKE);
    
    /// Drawing device path (if it exists)
    if (info.paths != null && info.paths.size() > 0)
    {
      DevicePath p = info.paths.get(0);
      if (p.path.length >= 2)
      {
        paint.setColor(solidColor);
        
        for(int j = 1; j < p.path.length; ++j)
        {
          LocationPoint P = p.path[j-1];
          LocationPoint Q = p.path[j];
          if (P.subLocation == subLoc.id && Q.subLocation == subLoc.id)
          {
            paint.setStrokeWidth(3 * dp);
            PointF P1 = getScreenCoordinates(P.x, P.y);
            PointF Q1 = getScreenCoordinates(Q.x, Q.y);
            canvas.drawLine(P1.x, P1.y, Q1.x, Q1.y, paint);
          }
        }
      }
    }
    
    // Drawing pin point (if it exists and belongs to the current sublocation)
    if (mPinPoint != null && mPinPoint.subLocation == subLoc.id)
    {
      final PointF T = getScreenCoordinates(mPinPoint.x, mPinPoint.y);
      final float tRadius = 10 * dp;
      
      paint.setARGB(255, 0, 0, 0);
      paint.setStrokeWidth(4 * dp);
      canvas.drawLine(T.x, T.y, T.x, T.y - 3 * tRadius, paint);
      
      paint.setColor(solidColor);
      canvas.drawCircle(T.x, T.y - 3 * tRadius, tRadius, paint);
      
      ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams)mMakeRouteButton.getLayoutParams();
      layoutParams.leftMargin   = (int)(T.x - (float)mMakeRouteButton.getWidth() / 2.0f);
      layoutParams.topMargin    = (int)(T.y - (float)mMakeRouteButton.getHeight() - tRadius * 5);
      layoutParams.rightMargin  = (int)(layoutParams.leftMargin + (float)mMakeRouteButton.getWidth());
      layoutParams.bottomMargin = (int)(layoutParams.topMargin  + (float)mMakeRouteButton.getHeight());
      mMakeRouteButton.setLayoutParams(layoutParams);
      mMakeRouteButton.setVisibility(View.VISIBLE);
    }
    else
      mMakeRouteButton.setVisibility(View.GONE);
    
    // Drawing target point (if it exists and belongs to the current sublocation)
    if (mTargetPoint != null && mTargetPoint.subLocation == subLoc.id)
    {
      final PointF T = getScreenCoordinates(mTargetPoint.x, mTargetPoint.y);
      final float tRadius = 10 * dp;
      
      paint.setARGB(255, 0, 0, 0);
      paint.setStrokeWidth(4 * dp);
      canvas.drawLine(T.x, T.y, T.x, T.y - 3 * tRadius, paint);
      
      paint.setColor(solidColor);
      canvas.drawCircle(T.x, T.y - 3 * tRadius, tRadius, paint);
      
      ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams)mCancelRouteButton.getLayoutParams();
      layoutParams.leftMargin   = (int)(T.x - (float)mCancelRouteButton.getWidth() / 2.0f);
      layoutParams.topMargin    = (int)(T.y - (float)mCancelRouteButton.getHeight() - 100.0f);
      layoutParams.rightMargin  = (int)(layoutParams.leftMargin + (float)mCancelRouteButton.getWidth());
      layoutParams.bottomMargin = (int)(layoutParams.topMargin  + (float)mCancelRouteButton.getHeight());
      mCancelRouteButton.setLayoutParams(layoutParams);
    }
    else
      mCancelRouteButton.setVisibility(View.GONE);
    
    // Check if device belongs to the current sublocation
    if (info.subLocation != subLoc.id)
      return;
    
    final float x  = info.x;
    final float y  = info.y;
    final float r  = info.r;
    final float angle = info.azimuth;
    final float sinA = (float)Math.sin(angle);
    final float cosA = (float)Math.cos(angle);
    final float radius  = getScreenLength(r);   // External radius: navigation-determined, transparent
    final float radius1 = 25 * dp;              // Internal radius: fixed, solid
    
    PointF O = getScreenCoordinates(x, y);
    PointF P = new PointF(O.x - radius1 * sinA * 0.22f, O.y + radius1 * cosA * 0.22f);
    PointF Q = new PointF(O.x + radius1 * sinA * 0.55f, O.y - radius1 * cosA * 0.55f);
    PointF R = new PointF(O.x + radius1 * cosA * 0.44f - radius1 * sinA * 0.55f, O.y + radius1 * sinA * 0.44f + radius1 * cosA * 0.55f);
    PointF S = new PointF(O.x - radius1 * cosA * 0.44f - radius1 * sinA * 0.55f, O.y - radius1 * sinA * 0.44f + radius1 * cosA * 0.55f);
    
    // Drawing transparent circle
    paint.setStrokeWidth(0);
    paint.setColor(circleColor);
    canvas.drawCircle(O.x, O.y, radius, paint);
    
    // Drawing solid circle
    paint.setColor(solidColor);
    canvas.drawCircle(O.x, O.y, radius1, paint);
    
    if (mOrientationEnabled)
    {
      // Drawing arrow
      paint.setColor(arrowColor);
      Path path = new Path();
      path.moveTo(Q.x, Q.y);
      path.lineTo(R.x, R.y);
      path.lineTo(P.x, P.y);
      path.lineTo(S.x, S.y);
      path.lineTo(Q.x, Q.y);
      canvas.drawPath(path, paint);
    }
  }
  
  private void adjustDevice(DeviceInfo info)
  {
    // Check if location is loaded
    if (mLocation == null || mCurrentSubLocationIndex < 0)
      return;
    
    // Check if device belongs to the location loaded
    if (info.location != mLocation.id)
      return;
    
    long timeNow = DateTimeUtils.currentTimeMillis();
    
    // Adjust map, if necessary
    if (timeNow >= mAdjustTime)
    {
      // Firstly, set the correct sublocation
      SubLocation subLoc = mLocation.subLocations.get(mCurrentSubLocationIndex);
      if (info.subLocation != subLoc.id)
      {
        for(int i = 0; i < mLocation.subLocations.size(); ++i)
          if (mLocation.subLocations.get(i).id == info.subLocation)
            loadSubLocation(i);
      }
      
      // Secondly, adjust device to the center of the screen
      PointF center = getScreenCoordinates(info.x, info.y);
      float deltaX  = mViewWidth  / 2 - center.x;
      float deltaY  = mViewHeight / 2 - center.y;
      doScroll(deltaX, deltaY);
      
      // Thirdly, adjust device direction to the top of screen
      //float angle = info.azimuth;
      //float deltaA = mAdjustAngle - angle;
      //doRotate(deltaA, center.x, center.y);
      //mAdjustAngle -= deltaA;
      
      //Log.d(TAG, String.format(Locale.ENGLISH, "Adjusted by: (%.2f, %.2f), %.2f (%.2f)",
      //      deltaX, deltaY, deltaA, angle));
      mAdjustTime = timeNow;
    }
  }
  
  private void drawArrow(PointF A, PointF B, Paint paint, Canvas canvas)
  {
    float ux = B.x - A.x;
    float uy = B.y - A.y;
    float n = (float)Math.sqrt(ux * ux + uy * uy);
    float m = Math.min(15.0f, n / 3);
    float k = m / n;
    
    PointF C = new PointF(k * A.x + (1 - k) * B.x, k * A.y + (1 - k) * B.y);
    
    float wx = -uy * m / n;
    float wy =  ux * m / n;
    
    PointF E = new PointF(C.x + wx / 3, C.y + wy / 3);
    PointF F = new PointF(C.x - wx / 3, C.y - wy / 3);
    
    Path path = new Path();
    path.moveTo(B.x, B.y);
    path.lineTo(E.x, E.y);
    path.lineTo(F.x, F.y);
    path.lineTo(B.x, B.y);
    
    canvas.drawLine(A.x, A.y, B.x, B.y, paint);
    //canvas.drawPath(path, paint);
  }
  
  private void connectToIMU(int subLocId, float x0, float y0)
  {
    if (mLocation == null)
      return;
    
    SubLocation subLoc = mLocation.getSubLocation(subLocId);
    if (subLoc == null)
      return;
    
    Log.d(TAG, "Connecting to IMU!");
    
    NavigineApp.stopNavigation();
    
    if (NavigineApp.Navigation != null)
    {
      String logFile = null;
      String mapFile = NavigineApp.Settings.getString("map_file", "");
      if (mapFile.length() > 0)
      {
        for(int i = 1; i < 10; ++i)
        {
          String suffix = String.format(Locale.ENGLISH, ".IMU.%d.log", i);
          String filename = mapFile.replaceAll("\\.zip$", suffix);
          if (!(new File(filename)).exists())
          {
            logFile = filename;
            break;
          }
        }
      }
      NavigineApp.IMU.setLogFile(logFile);
    }
    
    NavigineApp.IMU_Location = mLocation.id;
    NavigineApp.IMU_SubLocation = subLocId;
    NavigineApp.IMU.setStartPoint(x0, y0, 0.0f);
    NavigineApp.IMU.connect();
    
    mImuMode      = true;
    mImuState     = 0;
    mImuStateTime = 0;
  }
  
  private void disconnectFromIMU()
  {
    Log.d(TAG, "Disconnecting from IMU!");
    NavigineApp.IMU.disconnect();
    mImuMode      = false;
    mImuState     = 0;
    mImuStateTime = 0;
    
    NavigineApp.startNavigation();
  }
  
  final Runnable mRunnable =
    new Runnable()
    {
      public void run()
      {
        if (NavigineApp.Navigation == null)
          return;
        
        if (mMatrix == null)
        {
          tryLoadMap();
          return;
        }
        
        long timeNow = DateTimeUtils.currentTimeMillis();
        
        // Handling long touch gesture
        if (mTouchTime > 0 &&
            mTouchTime + TOUCH_LONG_TIMEOUT < timeNow &&
            mTouchLength < TOUCH_SENSITIVITY * NavigineApp.DisplayDensity)
        {
          doLongTouch(mTouchPoints[0].x, mTouchPoints[0].y);
          mTouchTime = 0;
          mTouchLength = 0;
        }
        
        if (mErrorMessageTime > 0 && timeNow > mErrorMessageTime + ERROR_MESSAGE_TIMEOUT)
        {
          mErrorMessageTime = 0;
          mErrorMessageAction = 0;
          mErrorMessageLabel.setVisibility(View.GONE);
        }
        
        // Check if location is loaded
        if (mLocation == null || mCurrentSubLocationIndex < 0)
          return;
        
        // Get current sublocation displayed
        SubLocation subLoc = mLocation.subLocations.get(mCurrentSubLocationIndex);
        
        mDeviceInfo = null;
        String infoText = null;
        int errorCode = 0;
        
        if (mImuMode)
        {
          String error = NavigineApp.IMU.getConnectionError();
          int state = NavigineApp.IMU.getConnectionState();
          if (state != mImuState)
          {
            mImuState = state;
            mImuStateTime = timeNow;
          }
          
          switch (mImuState)
          {
            case IMU_Thread.STATE_IDLE:
              infoText = error;
              if (Math.abs(timeNow - mImuStateTime) > 2500)
                disconnectFromIMU();
              break;
            
            case IMU_Thread.STATE_CONNECT:
              infoText = new String("IMU: connecting...");
              break;
            
            case IMU_Thread.STATE_DISCONNECT:
              infoText = new String("IMU: disconnecting...");
              break;
            
            case IMU_Thread.STATE_NORMAL:
            {
              IMU_Device imuDevice = NavigineApp.IMU.getDevice();
              if (imuDevice != null)
              {
                mDeviceInfo = NavigineApp.getDeviceInfoByIMU(imuDevice);
                infoText = String.format(Locale.ENGLISH, "IMU: packet #%d", imuDevice.packetNumber);
              }
              break;
            }
          }
        }
        else
        {
          // Start navigation if necessary
          if (NavigineApp.Navigation.getMode() == NavigationThread.MODE_IDLE)
            NavigineApp.startNavigation();
          
          // Get device info from NavigationThread
          mDeviceInfo = NavigineApp.Navigation.getDeviceInfo();
          errorCode   = NavigineApp.Navigation.getErrorCode();
          
          infoText = (mDeviceInfo == null) ?
                      String.format(Locale.ENGLISH, " Error code: %d ",  errorCode) :
                      String.format(Locale.ENGLISH, " Step %d [%.2fm] ", mDeviceInfo.stepCount, mDeviceInfo.stepLength);
        }
        
        mNavigationInfoLabel.setText(infoText != null ? " " + infoText + " " : "");
        mNavigationInfoLabel.setVisibility(mDebugModeEnabled ? View.VISIBLE : View.GONE);
        
        if (mDeviceInfo != null)
        {
          if (mAdjustMode)
            adjustDevice(mDeviceInfo);
          
          if (mErrorMessageAction == 2)
          {
            mErrorMessageTime = 0;
            mErrorMessageAction = 0;
            mErrorMessageLabel.setVisibility(View.GONE);
          }
        }
        else
        {
          if (errorCode == 4)
          {
            setErrorMessage("Вы покинули зону приема? Проверьте Bluetooth!", 2);
          }
          else if (errorCode != 0)
          {
            setErrorMessage(String.format(Locale.ENGLISH, "Что-то пошло не так с '%s'! Обратитесь к разработчикам",
                            mLocation.name), 2);
          }
          mMakeRouteButton.setVisibility(View.GONE);
          mCancelRouteButton.setVisibility(View.GONE);
        }
        
        // Drawing the device
        Picture pic = mPicDrawable.getPicture();
        Canvas canvas = pic.beginRecording(mViewWidth, mViewHeight);
        if (mDeviceInfo != null)
          drawDevice(mDeviceInfo, canvas);
        pic.endRecording();
        
        mPicImageView.invalidate();
        mMapImageView.setImageMatrix(mMatrix);
        mMapImageView.invalidate();
      }
    };
}
