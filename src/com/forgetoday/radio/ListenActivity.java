package com.forgetoday.radio;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.forgetoday.radio.AsyncImageLoader.ImageCallback;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ListenActivity extends Activity implements OnClickListener {
	private static final String TAG = "ForgeRadio";
	private static AsyncImageLoader asyncImageLoader;
	private static Button playBtn;
	static JSONArray schedule = null;
	private static Handler scheduleHandler = new Handler();
	private static ImageView showImageIV;
	private static TextView showNameTV;
	private static TextView showTimeTV;
	private static String showLink;
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 1 && resultCode == 1) {
			// Stop media service
			Intent intent = new Intent(this, MediaService.class);
			stopService(intent);
			
			// Finish activity
			finish();
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// UI setup
		setContentView(R.layout.main);
		
		playBtn = (Button)findViewById(R.id.playBtn);
		showImageIV = (ImageView) findViewById(R.id.showImage);
		showNameTV = (TextView) findViewById(R.id.showName);
		showTimeTV = (TextView) findViewById(R.id.showTime);
		
		playBtn.setOnClickListener(this);
		showImageIV.setOnClickListener(this);
		
		asyncImageLoader = new AsyncImageLoader();
		
		// Start media service
		Intent intent = new Intent(this, MediaService.class);
		intent.setAction(MediaService.ACTION_PLAY);
		startService(intent);
		
		// Check schedule
		scheduleHandler.post(checkScheduleTask);
		
		// Check latest version
		if (getPreferences(MODE_PRIVATE).getLong("lastCheckedVersion", 0) < System.currentTimeMillis()) {
			new checkVersionTask().execute();
		}
	}
	
	@Override
	public void onClick(View v) {
		if (playBtn == v) {
			Intent intent = new Intent(this, MediaService.class);
			intent.setAction(MediaService.ACTION_PLAY_PAUSE);
			startService(intent);
		} else if (showImageIV == v) {
			if (showLink == null || showLink.length() == 0) { // Dear null exceptions, did I mention I hate you?
				Toast.makeText(getApplicationContext(), getResources().getString(R.string.no_show_url),
						Toast.LENGTH_SHORT).show();
			} else {
				Uri uri = Uri.parse(showLink);
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
				try {
					startActivity(intent);
				} catch (ActivityNotFoundException e) {}
			}
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.listen_menu, menu);
		
		if (schedule == null) {
			menu.removeItem(R.id.schedule);
		}
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent;
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.exit:
			// Stop media service
			intent = new Intent(this, MediaService.class);
			stopService(intent);
			
			// Finish activity
			finish();
			return true;
		case R.id.catchup:
			// Flash check
			if (isFlashInstalled()) {
				intent = new Intent(getApplicationContext(), CatchupActivity.class);
				startActivityForResult(intent, 1);
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(getResources().getString(R.string.catchup_flash_warning))
					   .setPositiveButton(getResources().getString(R.string.market), new DialogInterface.OnClickListener() {
						   @Override
						   public void onClick(DialogInterface dialog, int id) {
							   Intent intent = new Intent(Intent.ACTION_VIEW);
							   intent.setData(Uri.parse(getResources().getString(R.string.flashplayer_uri)));
							   intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
							   try {
									startActivity(intent);
							   } catch(ActivityNotFoundException e) { // No Market
									Toast.makeText(getApplicationContext(), getResources().getString(R.string.no_market), Toast.LENGTH_LONG)
										.show();
							   }
						   }
					   })
					   .setNegativeButton(getResources().getString(R.string.back), null)
					   .create()
					   .show();
			}
			return true;
		case R.id.schedule:
			intent = new Intent(getApplicationContext(), ScheduleActivity.class);
			startActivityForResult(intent, 1);
			return true;
		case R.id.about:
			intent = new Intent(getApplicationContext(), AboutActivity.class);
			startActivityForResult(intent, 1);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		// If stream isn't playing, disable schedule checking for now
		if (!MediaService.mediaPlaying()) {
			scheduleHandler.removeCallbacks(checkScheduleTask);
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		// Force check
		scheduleHandler.post(checkScheduleTask);
	}
	
	private Runnable checkScheduleTask = new Runnable() {
		@Override
		public void run() {
			scheduleHandler.removeCallbacks(this);

			Resources r = getResources();
			final Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.US);
			// Hello, my name is "Dirty hack to compensate for DST while keeping times in GMT"
			int radioOffset = TimeZone.getTimeZone("Europe/London").getOffset(System.currentTimeMillis()) / 1000;
			JSONObject show = null;
			int start = 0;
			int end = 0;
			String showImage = "";
			String showName = "";
			long scheduleTime;
			boolean onAir = false;
			
			if (schedule == null) {
				String jsonstring = RadioApp.getTextFile((String) r.getText(R.string.schedule_url));
				
				if (jsonstring == null) {
					noSchedule();
					return;
				}
				
				Log.v(TAG, "JSON String: "+jsonstring);
				
				try {
					schedule = new JSONArray(jsonstring);
				} catch (JSONException e) {
					Log.e(TAG,"JSONException: "+e.getMessage());
				}
				
				if (schedule == null || schedule.length() == 0) {
					noSchedule();
					return;
				}
				
				Log.i(TAG, "JSON parsed. There are " + schedule.length() + " entries.");
			}
			
			int offset = (((c.get(Calendar.DAY_OF_WEEK)-1) * 24 + c.get(Calendar.HOUR_OF_DAY)) * 60
					+ c.get(Calendar.MINUTE)) * 60 + c.get(Calendar.SECOND);
			Log.i(TAG, "The time is " + offset + " seconds since Sunday");
			
			int i;
			int count = schedule.length();
			for (i=0;i<count;i++) {
				try {
					show = schedule.getJSONObject(i);
					start = show.getInt("start") - radioOffset;
					end = show.getInt("end") - radioOffset;
				} catch (JSONException e) {
					Log.e(TAG,"JSONException: "+e.getMessage());
					noSchedule();
					return;
				}
				
				if (start <= offset && end > offset) { // Current show
					try {
						showImage = show.getString("image");
						showLink = show.getString("link");
						showName = show.getString("name");
					} catch (JSONException e) {
						Log.e(TAG,e.getClass().getSimpleName()+": "+e.getMessage());
						noSchedule();
						return;
					}
					
					String startTime = RadioApp.hourMinute(start);
					String endTime = RadioApp.hourMinute(end);
					onAir = true;
					
					Log.i(TAG,"Current show: "+showName+" ("+startTime+" to "+endTime+")");
					Log.i(TAG,"Link URL: "+showLink);
					Log.i(TAG,"Image URL: "+showImage);
					
					showNameTV.setText(showName);
					showTimeTV.setText(startTime + " to " + endTime);
					
					// If no show image is available, load placeholder
					if (showImage.length() == 0) {
						showImageIV.setImageDrawable(getResources().getDrawable(R.drawable.logo));
					} else {
						// Load show image
						Drawable cachedImage = asyncImageLoader.loadDrawable(showImage, new ImageCallback() {
							public void imageLoaded(Drawable imageDrawable, String imageUrl, int i) {
								if (imageDrawable == null) {
									showImageIV.setImageDrawable(getResources().getDrawable(R.drawable.logo));
									Log.e(TAG,"Failed to load current show image "+imageUrl);
								} else {
									showImageIV.setImageDrawable(imageDrawable);
								}
							}
						}, 1);
						
						if (cachedImage != null) {
							showImageIV.setImageDrawable(cachedImage);
						}
					}
					
					scheduleTime = end * 1000 - ((((c.get(Calendar.DAY_OF_WEEK)-1) * 24 + c.get(Calendar.HOUR_OF_DAY))
							* 60 + c.get(Calendar.MINUTE)) * 60 + c.get(Calendar.SECOND)) * 1000 + c.get(Calendar.MILLISECOND);
					
					if (scheduleTime > 0) {
						scheduleHandler.postDelayed(checkScheduleTask, scheduleTime);
						Log.i(TAG, "Handler posting at end of show in "+scheduleTime+" milliseconds");
					} else {
						Log.w(TAG, "Handler attempted to schedule "+(-scheduleTime)+ " milliseconds ago");
					}
						
					break;
				} else if (start > offset) { // Upcoming show
					break;
				}
			}
			
			if (!onAir) { // No current show
				showName = (String) r.getText(R.string.off_air);
				String startTime = RadioApp.hourMinute(start);
				
				showNameTV.setText(Html.fromHtml("<b>" + showName + "</b>"));
				showTimeTV.setText("Back at "+startTime);
				showImageIV.setImageDrawable(r.getDrawable(R.drawable.logo));
				
				// End of the week's scheduling
				if (i == schedule.length()) {
					try {
					   	show = schedule.getJSONObject(0);
						start = Integer.parseInt(show.getString("start")) + 7*24*60*60;
					} catch (JSONException e) {
						Log.e(TAG,"JSONException: "+e.getMessage());
						noSchedule();
						return;
					}
					Log.i(TAG,"End of the week's scheduling");
				} else {
					Log.i(TAG,"Off air until " + startTime);
				}
				
				scheduleTime = start * 1000 - ((((c.get(Calendar.DAY_OF_WEEK)-1) * 24 + c.get(Calendar.HOUR_OF_DAY))
						* 60 + c.get(Calendar.MINUTE)) * 60 + c.get(Calendar.SECOND)) * 1000 + c.get(Calendar.MILLISECOND);
				
				if (scheduleTime > 0) {
					scheduleHandler.postDelayed(checkScheduleTask, scheduleTime);
					Log.i(TAG, "Handler posting at start of scheduling in "+scheduleTime+" milliseconds");
				} else {
					Log.w(TAG, "Handler attempted to schedule "+(-scheduleTime)+ " milliseconds ago");
				}
			}
			
			Intent intent = new Intent(getApplicationContext(), MediaService.class);
			intent.setAction(MediaService.ACTION_UPDATE).putExtra("showName",showName);
			startService(intent);
		}
	};
	
	private class checkVersionTask extends AsyncTask<Void, Void, String> {
		
		@Override
		protected String doInBackground(Void... args) {
			String liveVersionData[] = RadioApp.getTextFile((String) getResources().getText(R.string.version_url)).split("\\r?\\n");
			
			if (liveVersionData == null) {
				Log.e(TAG, "Unable to load latest app version data");
				return null;
			}
			
			try {
				int liveVersion = Integer.parseInt(liveVersionData[0]);
				PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				
				if (liveVersion > pinfo.versionCode) {
					return liveVersionData[1];
				}
			} catch (ArrayIndexOutOfBoundsException e) {
				Log.e(TAG, "Unable to parse latest app version data");
			} catch (NumberFormatException e) {
				Log.e(TAG, "Unable to parse latest app version data");
			} catch (NameNotFoundException e) {}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(String result) {
			if (result != null) {
				offerUpdate(result);
			}
		}
		
	};
	
	private boolean isFlashInstalled() {
		boolean flashInstalled = false;
		PackageManager pm = getPackageManager();
		
		try {
			ApplicationInfo ai = pm.getApplicationInfo("com.adobe.flashplayer", 0);
			if (ai != null) flashInstalled = true;
		} catch (NameNotFoundException e1) {
			try {
				ApplicationInfo ai = pm.getApplicationInfo("com.htc.flash", 0);
				if (ai != null) flashInstalled = true;
			} catch (NameNotFoundException e2) {}
		}
		
		return flashInstalled;
	}
	
	private void noSchedule() {
		// Null the schedule to avoid saving an empty JSONArray
		schedule = null;
		
		Resources r = getResources();
		
		Toast.makeText(getApplicationContext(), r.getString(R.string.schedule_error), Toast.LENGTH_LONG)
			.show();
		
		showImageIV.setImageDrawable(r.getDrawable(R.drawable.logo));
		
		Intent intent = new Intent(this, MediaService.class);
		intent.setAction(MediaService.ACTION_UPDATE).putExtra("showName",r.getString(R.string.station_name));
		startService(intent);
	}
	
	private void offerUpdate(final String url) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(getResources().getString(R.string.download_offer))
			   .setPositiveButton(getResources().getString(R.string.download_yes), new DialogInterface.OnClickListener() {
				   @Override
				   public void onClick(DialogInterface dialog, int id) {
						SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
						editor.putLong("lastCheckedVersion", System.currentTimeMillis());
						editor.commit();
						
						Uri uri = Uri.parse(url);
						Intent intent = new Intent(Intent.ACTION_VIEW, uri);
						intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
						try {
							startActivity(intent);
						} catch (ActivityNotFoundException e) {
							Toast.makeText(getApplicationContext(), getResources().getString(R.string.no_browser), Toast.LENGTH_LONG)
								.show();
						}
				   }
			   })
			   .setNegativeButton(getResources().getString(R.string.download_no), new DialogInterface.OnClickListener() {
				   @Override
				   public void onClick(DialogInterface dialog, int id) {
						SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
						editor.putLong("lastCheckedVersion", System.currentTimeMillis()+7*24*60*60*1000);
						editor.commit();
				   }
			   })
			   .create()
			   .show();
	}
	
}