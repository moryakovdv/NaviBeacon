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
import android.view.animation.*;
import android.view.View.*;
import android.widget.*;
import android.widget.AbsListView.*;
import android.widget.ImageView.*;
import android.util.*;
import java.io.*;
import java.lang.*;
import java.nio.*;
import java.net.*;
import java.util.*;

public class LoaderActivity extends Activity
{
  // Constants
  public static final String TAG = "NAVIGINE.LoaderActivity";
  public static final int LOADER_TIMEOUT = 30000;
  public static final int UPDATE_TIMEOUT = 100;
  
  // This context
  private Context mContext = this;
  
  private com.navigine.navigine.ListView mListView = null;
  private TextView    mStatusLabel  = null;
  private Button      mMenuButton   = null;
  private Button      mLogoutButton = null;
  private TextView    mNameLabel    = null;
  private TimerTask   mTimerTask    = null;
  private Handler     mHandler      = new Handler();
  private Timer       mTimer        = new Timer();
  
  private boolean     mMenuVisible  = false;
  
  private int mLoader = -1;
  private long mLoaderTime = -1;
  private List<LocationInfo> mInfoList = new ArrayList<LocationInfo>();
  
  private int mSwipedLocation = 0;
  
  public static final int DOWNLOAD = 1;
  public static final int UPLOAD   = 2;
  private class LoaderState
  {
    public String location = "";
    public int type = 0;
    public int id = -1;
    public int state = 0;
    public long timeLabel = 0;
  }
  private Map<String, LoaderState> mLoaderMap = new TreeMap<String, LoaderState>();
  
  private class LoaderAdapter extends BaseAdapter
  { 
    @Override public int getCount()
    {
      return mInfoList.size(); 
    }
    
    @Override public Object getItem(int pos)
    {
      return mInfoList.get(pos);
    }
    
    @Override public long getItemId(int pos)
    {
      return pos;
    }
    
    public void updateList()
    {
      notifyDataSetChanged();
    }
    
    @Override public View getView(final int position, View convertView, ViewGroup parent)
    {
      LocationInfo info = mInfoList.get(position);
      
      View view = convertView;
      if (view == null)
      {
        LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        view = inflater.inflate(R.layout.content_list_item, null);
      }
      
      TextView titleTextView = (TextView)view.findViewById(R.id.list_item__title_text_view);
      TextView statusTextView = (TextView)view.findViewById(R.id.list_item__status_text_view);
      Button downloadButton = (Button)view.findViewById(R.id.list_item__download_button);
      Button uploadButton = (Button)view.findViewById(R.id.list_item__upload_button);
      Button infoButton = (Button)view.findViewById(R.id.list_item__info_button);
      Button deleteButton = (Button)view.findViewById(R.id.list_item__delete_button);
      ProgressBar progressBar = (ProgressBar)view.findViewById(R.id.list_item__progress_bar);
      Button downloadStopButton = (Button)view.findViewById(R.id.list_item__download_stop_button);
      ImageView selectedMapIcon = (ImageView)view.findViewById(R.id.list_item__selected_map_icon);
      HorizontalScrollView scrollView = (HorizontalScrollView)view.findViewById(R.id.list_item__horizontal_scroll_view);
      View updateView = (View)view.findViewById(R.id.list_item__update_view);
      ProgressBar updateProgressBar = (ProgressBar)view.findViewById(R.id.list_item__update_progress_bar);
      
      progressBar.getIndeterminateDrawable().setColorFilter(Color.rgb(0x14, 0x27, 0x3D), android.graphics.PorterDuff.Mode.SRC_IN);
      
      do {
        FrameLayout frameLayout = (FrameLayout)view.findViewById(R.id.list_item__main_frame_layout);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)frameLayout.getLayoutParams();
        layoutParams.width = Math.round(NavigineApp.DisplayWidthPx);
        frameLayout.setLayoutParams(layoutParams);
      } while (false);
      
      if (info.id == 0)
      {
        scrollView.setVisibility(View.GONE);
        updateView.setVisibility(View.VISIBLE);
        return view;
      }
      
      scrollView.setVisibility(View.VISIBLE);
      updateView.setVisibility(View.GONE);
      
      String titleText = info.title;
      if (titleText.length() > 30)
        titleText = titleText.substring(0, 28) + "...";
      titleTextView.setText(titleText);
      
      String statusText = null;
      
      if (mLoaderMap.containsKey(info.title))
      {
        LoaderState loader = mLoaderMap.get(info.title);
        downloadButton.setVisibility(View.GONE);
        uploadButton.setVisibility(View.GONE);
        infoButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        downloadStopButton.setVisibility(View.VISIBLE);
        progressBar.setProgress(loader.state);
        statusTextView.setVisibility(View.VISIBLE);
        if (loader.type == DOWNLOAD)
          statusTextView.setText(String.format(Locale.ENGLISH, "Загрузка... %d%%", loader.state));
        else if (loader.type == UPLOAD)
          statusTextView.setText(String.format(Locale.ENGLISH, "Закачка... %d%%", loader.state));
      }
      else if (info.localModified)
      {
        downloadButton.setVisibility(View.GONE);
        uploadButton.setVisibility(View.VISIBLE);
        infoButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        downloadStopButton.setVisibility(View.GONE);
        statusTextView.setVisibility(View.VISIBLE);
        statusTextView.setText("Версия изменилась. Загрузить?");
      }
      else if (info.serverVersion > info.localVersion)
      {
        downloadButton.setVisibility(View.VISIBLE);
        uploadButton.setVisibility(View.GONE);
        infoButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        downloadStopButton.setVisibility(View.GONE);
        statusTextView.setVisibility(View.VISIBLE);
        statusTextView.setText(String.format(Locale.ENGLISH, "Доступная версия: %d", info.serverVersion));
      }
      else
      {
        downloadButton.setVisibility(View.GONE);
        uploadButton.setVisibility(View.GONE);
        infoButton.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        downloadStopButton.setVisibility(View.GONE);
        statusTextView.setVisibility(View.GONE);
      }
      
      String mapFile = NavigineApp.Settings.getString("map_file", "");
      boolean selected = mapFile.equals(info.archiveFile);
      selectedMapIcon.setVisibility(selected ? View.VISIBLE : View.GONE);
      
      boolean mapExists = (new File(info.archiveFile)).exists();
      deleteButton.setVisibility(mapExists ? View.VISIBLE : View.GONE);
      
      if (info.id == mSwipedLocation)
        scrollView.scrollTo(Math.round(75 * NavigineApp.DisplayDensity), 0);
      else
        scrollView.scrollTo(0, 0);
      
      downloadButton.setOnClickListener(new View.OnClickListener()
        {
          @Override public void onClick(View v)
          {
            if (mSwipedLocation > 0)
            {
              mSwipedLocation = 0;
              mAdapter.updateList();
            }
            startDownload(position);
          }
        });
      
      uploadButton.setOnClickListener(new View.OnClickListener()
        {
          @Override public void onClick(View v)
          {
            if (mSwipedLocation > 0)
            {
              mSwipedLocation = 0;
              mAdapter.updateList();
            }
            startUpload(position);
          }
        });
      
      downloadStopButton.setOnClickListener(new View.OnClickListener()
        {
          @Override public void onClick(View v)
          {
            if (mSwipedLocation > 0)
            {
              mSwipedLocation = 0;
              mAdapter.updateList();
            }
            stopDownload(position);
          }
        });
      
      deleteButton.setOnClickListener(new View.OnClickListener()
        {
          @Override public void onClick(View v)
          {
            if (mSwipedLocation > 0)
            {
              mSwipedLocation = 0;
              mAdapter.updateList();
            }
            deleteLocation(mInfoList.get(position));
          }
        });
      
      infoButton.setOnClickListener(new View.OnClickListener()
        {
          @Override public void onClick(View v)
          {
            if (mSwipedLocation > 0)
            {
              mSwipedLocation = 0;
              mAdapter.updateList();
            }
            showLocation(mInfoList.get(position));
          }
        });
      
      scrollView.setOnTouchListener(
        new OnTouchListener()
        {
          private static final int TOUCH_SHORT_TIMEOUT = 200;
          private static final int TOUCH_LONG_TIMEOUT  = 600;
          private static final int TOUCH_SENSITIVITY   = 20;
          
          private long    mTouchTime    = 0;
          private PointF  mTouchPoint0  = null;
          private PointF  mTouchPoint1  = null;
          private float   mTouchLength  = 0.0f;
          
          private LocationInfo info     = mInfoList.get(position);
          boolean mapExists             = (new File(info.archiveFile)).exists();
          
          @Override public boolean onTouch(View v, MotionEvent event)
          {
            long timeNow = DateTimeUtils.currentTimeMillis();
            int actionMask = event.getActionMasked();
            int pointerIndex = event.getActionIndex();
            int pointerCount = event.getPointerCount();
            
            // Ignoring incorrect touch events
            if (pointerCount != 1)
              return true;
            
            if (mMenuVisible)
              toggleMenuLayout(null);
            
            PointF P = new PointF(event.getX(0), event.getY(0));
            
            if (mSwipedLocation > 0 && mSwipedLocation != info.id)
            {
              mSwipedLocation = 0;
              mAdapter.updateList();
            }
            
            switch (actionMask)
            {
              case MotionEvent.ACTION_DOWN:
              {
                //Log.d(TAG, String.format(Locale.ENGLISH, "Action down (%.2f, %.2f)", P.x, P.y));
                mTouchTime    = timeNow;
                mTouchPoint0  = P;
                mTouchPoint1  = P;
                mTouchLength  = 0.0f;
                break;
              }
              
              case MotionEvent.ACTION_MOVE:
              {
                if (mTouchPoint0 == null)
                  mTouchPoint0 = P;
                if (mTouchPoint1 == null)
                  mTouchPoint1 = P;
                
                float delta0 = P.x - mTouchPoint0.x;
                float delta1 = P.x - mTouchPoint1.x;
                
                mTouchPoint1 = P;
                mTouchLength += Math.abs(delta1);
                
                //Log.d(TAG, String.format(Locale.ENGLISH, "Action move: (delta0=%.2f, delta1=%.2f)", delta0, delta1));
                
                if (mapExists)
                {
                  if (delta0 < -TOUCH_SENSITIVITY * NavigineApp.DisplayDensity)
                  {
                    if (mSwipedLocation == 0)
                    {
                      mSwipedLocation = info.id;
                      //Log.d(TAG, String.format(Locale.ENGLISH, "Swiping location %d", info.id));
                      ((HorizontalScrollView)v).scrollTo(Math.round(75 * NavigineApp.DisplayDensity), 0);
                    }
                  }
                  else if (delta0 > TOUCH_SENSITIVITY * NavigineApp.DisplayDensity)
                  {
                    if (mSwipedLocation == info.id)
                    {
                      mSwipedLocation = 0;
                      //Log.d(TAG, String.format(Locale.ENGLISH, "Swiping back location %d", info.id));
                      ((HorizontalScrollView)v).scrollTo(0, 0);
                    }
                  }
                }
                break;
              }
              
              case MotionEvent.ACTION_UP:
              {
                //Log.d(TAG, String.format(Locale.ENGLISH, "Action up (%.2f, %.2f)", P.x, P.y));
                if (mTouchTime > 0 &&
                    mTouchTime + TOUCH_SHORT_TIMEOUT > timeNow &&
                    mTouchLength < TOUCH_SENSITIVITY * NavigineApp.DisplayDensity)
                {
                  LocationInfo info = mInfoList.get(position);
                  //Log.d(TAG, String.format(Locale.ENGLISH, "Selecting location %s", info.title));
                  selectLocation(info);
                }
                mTouchTime   = 0;
                mTouchLength = 0.0f;
                mTouchPoint0 = null;
                mTouchPoint1 = null;
                break;
              }
            }
            return true;
          }
        });
      
      return view; 
    }
  }
  private LoaderAdapter mAdapter = null;
  
  // Scroll parameters
  private int mScrollState      = 0;
  private int mScrollPosition   = 0;
  private int mOverScroll       = 0;
  
  /** Called when the activity is first created */
  @Override public void onCreate(Bundle savedInstanceState)
  {
    Log.d(TAG, "LoaderActivity created");
    super.onCreate(savedInstanceState);
    
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                         WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.content);
    
    if (NavigineApp.Navigation == null)
    {
      finish();
      return;
    }
    
    // Instantiate custom adapter
    mAdapter = new LoaderAdapter();
    
    // Handle listview and assign adapter
    mListView = (com.navigine.navigine.ListView)findViewById(R.id.content__list_view);
    mListView.setAdapter(mAdapter);
    mListView.setVisibility(View.VISIBLE);
    
    mListView.setOnScrollListener(new OnScrollListener()
      {
        @Override public void onScrollStateChanged(AbsListView view, int scrollState)
        {
          // TODO Auto-generated method stub
          //Log.d(TAG, "onScrollState: state=" + scrollState);
          mScrollState = scrollState;
          
          if (mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
          {
            //Log.d(TAG, "onScrollState: dropped overScroll=" + mOverScroll + "; threshold: " + threshold);
            mOverScroll = 0;
            mScrollPosition = -100;
          }
          else
            mScrollPosition = -1;
        }
        
        @Override public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
        {
          if (mScrollPosition == -1)
            mScrollPosition = firstVisibleItem;
        }
      });
    
    mListView.setOnOverScrollListener(new com.navigine.navigine.ListView.OnOverScrollListener()
      {
        public void onOverScroll(int scrollY)
        {
          Log.d(TAG, String.format(Locale.ENGLISH, "onOverScroll: state=%d, position=%d, total=%d", mScrollState, mScrollPosition, mOverScroll));
          if (mScrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL && (mScrollPosition == -1 || mScrollPosition == 0))
          {
            mOverScroll += scrollY;
            int threshold = Math.round(75 * 3 * NavigineApp.DisplayDensity);
            if (mOverScroll < -threshold)
            {
              mOverScroll = 0;
              mScrollPosition = -100;
              refreshMapList();
            }
          }
        }
      });
    
    mStatusLabel = (TextView)findViewById(R.id.content__status_label);
    mStatusLabel.setVisibility(View.GONE);
    
    mMenuButton = (Button)findViewById(R.id.content__menu_button);
    
    mLogoutButton = (Button)findViewById(R.id.content__logout_button);
    
    if (NavigineApp.UserInfo == null)
    {
      logout(null);
      return;
    }
    
    mNameLabel = (TextView)findViewById(R.id.content__name_text_view);
    mNameLabel.setText(NavigineApp.UserInfo.name.toUpperCase());
    
    if (!readMapList())
      refreshMapList();
  }
  
  @Override public void onStart()
  {
    Log.d(TAG, "LoaderActivity started");
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
    Log.d(TAG, "LoaderActivity stopped");
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
    stopLoaders();
    
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
    mSwipedLocation = 0;
    mAdapter.updateList();
    
    LinearLayout topLayout  = (LinearLayout)findViewById(R.id.content__top_layout);
    LinearLayout menuLayout = (LinearLayout)findViewById(R.id.content__menu_layout);
    RelativeLayout mainLayout = (RelativeLayout)findViewById(R.id.content__main_layout);
    ViewGroup.MarginLayoutParams layoutParams = null;
    
    boolean hasMapFile   = (NavigineApp.Settings != null && NavigineApp.Settings.getString("map_file", "").length() > 0);
    boolean hasDebugMode = (NavigineApp.Settings != null && NavigineApp.Settings.getBoolean("debug_mode_enabled", false));
    findViewById(R.id.content__menu_measuring_mode).setVisibility(hasMapFile ? View.VISIBLE : View.GONE);
    findViewById(R.id.content__menu_navigation_mode).setVisibility(hasMapFile ? View.VISIBLE : View.GONE);
    findViewById(R.id.content__menu_debug_mode).setVisibility(hasDebugMode ? View.VISIBLE : View.GONE);
    
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
  
  public void logout(View v)
  {
    if (mMenuVisible)
    {
      toggleMenuLayout(null);
      return;
    }
    
    cleanup();
    NavigineApp.logout();
    
    Intent intent = new Intent(mContext, LoginActivity.class);
    startActivity(intent);
  }
  
  public void onMainLayoutClicked(View v)
  {
    if (mMenuVisible)
      toggleMenuLayout(null);
  }
  
  private void selectLocation(LocationInfo info)
  {
    if (NavigineApp.Navigation == null)
      return;
    
    if (info == null)
      return;
    
    File f = new File(info.archiveFile);
    if (!f.exists())
    {
      Log.d(TAG, String.format(Locale.ENGLISH, "Location '%s' is not downloaded: missing file '%s'",
            info.title, info.archiveFile));
      return;
    }
    
    Log.d(TAG, "Selecting location " + info.id + " " + info.archiveFile);
    
    SharedPreferences.Editor editor = NavigineApp.Settings.edit();
    editor.putInt("map_id", info.id);
    editor.putString("map_file", info.archiveFile);
    editor.commit();
    mAdapter.updateList();
  }
  
  private void deleteLocation(LocationInfo info)
  {
    if (NavigineApp.Navigation == null)
      return;
    
    if (info == null)
      return;
    
    try
    {
      (new File(info.archiveFile)).delete();
      info.localVersion = -1;
      info.localModified = false;
      
      String locationDir = LocationLoader.getLocationDir(mContext, info.title);
      File dir = new File(locationDir);
      File[] files = dir.listFiles();
      for(int i = 0; i < files.length; ++i)
        files[i].delete();
      dir.delete();
      
      String mapFile = NavigineApp.Settings.getString("map_file", "");
      if (mapFile.equals(info.archiveFile))
      {
        NavigineApp.Navigation.loadArchive(null);
        SharedPreferences.Editor editor = NavigineApp.Settings.edit();
        editor.putInt("map_id", 0);
        editor.putString("map_file", "");
        editor.commit();
      }
      
      mAdapter.updateList();
    }
    catch (Throwable e)
    {
      Log.e(TAG, Log.getStackTraceString(e));
    }
  }
  
  private void showLocation(LocationInfo info)
  {
    if (NavigineApp.Navigation == null)
      return;
    
    if (info == null)
      return;
    
    if (mMenuVisible)
      toggleMenuLayout(null);
    
    Bundle b = new Bundle();
    b.putInt("location_id", info.id);
    b.putInt("location_version", info.localVersion);
    b.putString("location_title", info.title);
    b.putString("location_description", info.description);
    b.putString("location_archive_file", info.archiveFile);
    b.putBoolean("location_modified", info.localModified);
    
    Intent intent = new Intent(mContext, LocationInfoActivity.class);
    intent.putExtras(b);
    startActivity(intent);
  }
  
  private void refreshMapList()
  {
    if (mLoader >= 0)
      return;
    
    if (NavigineApp.Navigation == null)
      return;
    
    if (NavigineApp.UserInfo == null)
      return;
    
    // Starting new loader
    Log.d(TAG, "Refresh map list: started");
    String fileName = LocationLoader.getHomeDir(NavigineApp.AppContext) + "/maps.xml";
    mLoader = LocationLoader.startLocationLoader(null, fileName, true);
    mLoaderTime = DateTimeUtils.currentTimeMillis();
    Log.d(TAG, String.format(Locale.ENGLISH, "Location loader started: %d", mLoader));
    
    LocationInfo info0 = new LocationInfo();
    mInfoList.add(0, info0);
    mAdapter.updateList();
  }
  
  private boolean readMapList()
  {
    try
    {
      String fileName = LocationLoader.getLocationDir(NavigineApp.AppContext, null) + "/maps.xml";
      List<LocationInfo> infoList = Parser.parseMapsXml(NavigineApp.AppContext, fileName);
      if (infoList != null)
      {
        mInfoList = infoList;
        mAdapter.updateList();
        return true;
      }
      return false;
    }
    catch (Throwable e)
    {
      Log.e(TAG, Log.getStackTraceString(e));
    }
    return false;
  }
  
  private void updateLoader()
  {
    if (NavigineApp.Navigation == null)
      return;
    
    //Log.d(TAG, String.format(Locale.ENGLISH, "Update loader: %d", mLoader));
    
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
        Log.d(TAG, String.format(Locale.ENGLISH, "Refresh map list: stopped on timeout!"));
        LocationLoader.stopLocationLoader(mLoader);
        mLoader = -1;
      }
    }
    else
    {
      Log.d(TAG, String.format(Locale.ENGLISH, "Refresh map list: finished with result: %d", status));
      LocationLoader.stopLocationLoader(mLoader);
      mLoader = -1;
      
      if (status == 100)
      {
        if (readMapList() && mInfoList.isEmpty())
        {
          mStatusLabel.setVisibility(View.VISIBLE);
          mStatusLabel.setText("No locations available");
        }
        else
        {
          mStatusLabel.setVisibility(View.GONE);
        }
      }
      else
      {
        // TODO: show notification (check your id)
      }
    }
  }
  
  long mUpdateLocationLoadersTime = 0;
  private void updateLocationLoaders()
  {
    if (NavigineApp.Navigation == null)
      return;
    
    long timeNow = DateTimeUtils.currentTimeMillis();
    mUpdateLocationLoadersTime = timeNow;
    
    Iterator<Map.Entry<String,LoaderState> > iter = mLoaderMap.entrySet().iterator();
    while (iter.hasNext())
    {
      Map.Entry<String,LoaderState> entry = iter.next();
      
      LoaderState loader = entry.getValue();
      if (loader.state >= 0 && loader.state < 100)
      {
        loader.timeLabel = timeNow;
        if (loader.type == DOWNLOAD)
          loader.state = LocationLoader.checkLocationLoader(loader.id);
        if (loader.type == UPLOAD)
          loader.state = LocationLoader.checkLocationUploader(loader.id);
      }
      else if (loader.state == 100)
      {
        String locationFile = LocationLoader.getLocationFile(NavigineApp.AppContext, loader.location);
        for(int i = 0; i < mInfoList.size(); ++i)
        {
          LocationInfo info = mInfoList.get(i);
          if (info.archiveFile.equals(locationFile))
          {
            selectLocation(info);
            break;
          }
        }
        if (loader.type == DOWNLOAD)
          LocationLoader.stopLocationLoader(loader.id);
        if (loader.type == UPLOAD)
          LocationLoader.stopLocationUploader(loader.id);
        iter.remove();
      }
      else
      {
        // Load failed
        if (Math.abs(timeNow - loader.timeLabel) > 5000)
        {
          if (loader.type == DOWNLOAD)
            LocationLoader.stopLocationLoader(loader.id);
          if (loader.type == UPLOAD)
            LocationLoader.stopLocationUploader(loader.id);
          iter.remove();
        }
      }
    }
    
    // Updating local versions
    for(int i = 0; i < mInfoList.size(); ++i)
    {
      LocationInfo info = mInfoList.get(i);
      if (info.id == 0)
        continue;
      
      String versionStr = LocationLoader.getLocalVersion(NavigineApp.AppContext, info.title);
      if (versionStr != null)
      {
        //Log.d(TAG, info.title + ": " + versionStr);
        info.localModified = versionStr.endsWith("+");
        if (info.localModified)
          versionStr = versionStr.substring(0, versionStr.length() - 1);
        try { info.localVersion = Integer.parseInt(versionStr); } catch (Throwable e) { }
      }
      else
      {
        info.localVersion = -1;
        
        String mapFile = NavigineApp.Settings.getString("map_file", "");
        if (mapFile.equals(info.archiveFile))
        {
          NavigineApp.Navigation.loadArchive(null);
          SharedPreferences.Editor editor = NavigineApp.Settings.edit();
          editor.putInt("map_id", 0);
          editor.putString("map_file", "");
          editor.commit();
        }
      }
    }
    
    mAdapter.updateList();
  }
  
  private void startDownload(int index)
  {
    if (NavigineApp.Navigation == null)
      return;
    
    LocationInfo info = mInfoList.get(index);
    String location = new String(info.title);
    Log.d(TAG, String.format(Locale.ENGLISH, "Start download: %s", location));
    
    if (!mLoaderMap.containsKey(location))
    {
      LoaderState loader = new LoaderState();
      loader.location = location;
      loader.type = DOWNLOAD;
      loader.id = LocationLoader.startLocationLoader(location, info.archiveFile, true);
      mLoaderMap.put(location, loader);
    }
    mAdapter.updateList();
  }
  
  private void startUpload(int index)
  {
    if (NavigineApp.Navigation == null)
      return;
    
    LocationInfo info = mInfoList.get(index);
    String location = new String(info.title);
    Log.d(TAG, String.format(Locale.ENGLISH, "Start upload: %s", location));
    
    if (!mLoaderMap.containsKey(location))
    {
      LoaderState loader = new LoaderState();
      loader.location = location;
      loader.type = UPLOAD;
      loader.id = LocationLoader.startLocationUploader(location, info.archiveFile, true);
      mLoaderMap.put(location, loader);
    }
    mAdapter.updateList();
  }
  
  private void stopDownload(int index)
  {
    if (NavigineApp.Navigation == null)
      return;
    
    LocationInfo info = mInfoList.get(index);
    String location = new String(info.title);
    Log.d(TAG, String.format(Locale.ENGLISH, "Stop loader: %s", location));
    
    if (mLoaderMap.containsKey(location))
    {
      LoaderState loader = mLoaderMap.get(location);
      LocationLoader.stopLocationLoader(loader.id);
      mLoaderMap.remove(location);
    }
    mAdapter.updateList();
  }
  
  private void stopLoaders()
  {
    Log.d(TAG, "LoaderActivity: stop loaders");
    if (mLoader >= 0)
    {
      LocationLoader.stopLocationLoader(mLoader);
      mLoader = -1;
    }
    
    for(Map.Entry<String, LoaderState> entry : mLoaderMap.entrySet())
    {
      LoaderState loader = entry.getValue();
      LocationLoader.stopLocationLoader(loader.id);
    }
    mLoaderMap.clear();
  }
  
  final Runnable mRunnable =
    new Runnable()
    {
      public void run()
      {
        if (NavigineApp.Navigation == null)
          return;
        
        if (NavigineApp.UserInfo == null)
        {
          logout(null);
          return;
        }
        
        mNameLabel.setText(NavigineApp.UserInfo.name.toUpperCase());
        
        long timeNow = DateTimeUtils.currentTimeMillis();
        
        if (mLoader >= 0)
          updateLoader();
        
        if (Math.abs(timeNow - mUpdateLocationLoadersTime) > 1000)
          updateLocationLoaders();
      }
    };
}
