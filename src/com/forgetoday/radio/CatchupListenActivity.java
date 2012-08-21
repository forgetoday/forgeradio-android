package com.forgetoday.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.Toast;

public class CatchupListenActivity extends Activity {
	private static final String TAG = "ForgeRadio";
	private static WebView webview;
	private static PowerManager.WakeLock wakeLock;
	private static WifiLock wifiLock = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.catchup_listen);
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		
		if (!settings.getBoolean("catchupLockWarned", false)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(getResources().getString(R.string.catchup_lock_warning))
				   .setPositiveButton(getResources().getString(R.string.catchup_lock_accept), new DialogInterface.OnClickListener() {
					   @Override
					   public void onClick(DialogInterface dialog, int id) {
						   dialog.dismiss();
					   }
				   })
				   .create()
				   .show();
			
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean("catchupLockWarned", true);
			editor.commit();
		}
		
		// Pause the radio stream
		Intent mediaServiceIntent = new Intent(getApplicationContext(),MediaService.class);
		mediaServiceIntent.setAction(MediaService.ACTION_PAUSE);
		startService(mediaServiceIntent);
		
		String className = getLocalClassName();
		
		// URL loading and HTML parsing
		String url = getIntent().getStringExtra("url");
		
		if (url == null) {
			Toast.makeText(getApplicationContext(), getResources().getString(R.string.catchup_error), Toast.LENGTH_LONG)
					.show();
			Log.e(TAG,"Invalid URL passed to "+className);
			finish();
		}
		
		String html = RadioApp.getTextFile(url);
		
		if (html == null) {
			Toast.makeText(getApplicationContext(), getResources().getString(R.string.catchup_error), Toast.LENGTH_LONG)
					.show();
			Log.e(TAG,"Failed to get HTML for URL "+url+" in "+className);
			finish();
		}
		
		int tagStart = html.indexOf("<object");
		int tagEnd = html.indexOf("</object>");
		if (tagStart != -1 && tagEnd != -1)
			html = html.substring(tagStart, tagEnd+9);
		
		// WebView setup
		webview = (WebView) findViewById(R.id.webview);
		webview.getSettings().setPluginsEnabled(true);
		webview.getSettings().setSupportZoom(false);
		webview.setBackgroundColor(0x00000000); // Transparent
		webview.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
		webview.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
		
		// Locks
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, className);
		wakeLock.acquire();
		wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
				.createWifiLock(WifiManager.WIFI_MODE_FULL, className);
		wifiLock.acquire();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.generic_menu, menu);
		return true;
	}
	
	@Override
	protected void onDestroy() {
		// Some men just want to watch the world burn
		if (webview != null) webview.destroy();
		if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
		if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
		
		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.exit:
			setResult(1,new Intent());
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}