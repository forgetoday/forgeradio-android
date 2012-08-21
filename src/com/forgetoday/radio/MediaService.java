package com.forgetoday.radio;

import java.io.IOException;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

public class MediaService extends Service implements MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener, AudioManager.OnAudioFocusChangeListener {
	private static final String TAG = "ForgeRadio";
	final static String ACTION_PAUSE = "com.forgetoday.radio.PAUSE";
	final static String ACTION_PLAY = "com.forgetoday.radio.PLAY";
	final static String ACTION_PLAY_PAUSE = "com.forgetoday.radio.PLAY_PAUSE";
	final static String ACTION_UPDATE = "com.forgetoday.radio.UPDATE";
	final private static int NOTIFICATION_ID = 123;
	private static AudioManager audioManager;
	private static MediaPlayer mMediaPlayer = null;
	private static WifiLock wifiLock = null;
	private static String showName;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent.getAction().equals(ACTION_PLAY) ) {
			mediaPlay();
		} else if (intent.getAction().equals(ACTION_PLAY_PAUSE)) {
			if (mMediaPlayer == null) {
				mediaPlay();
			} else {
				mediaStop(false);
			}
		} else if (intent.getAction().equals(ACTION_PAUSE)) {
			mediaStop(false);
		} else if (intent.getAction().equals(ACTION_UPDATE)) {
			showName = intent.getStringExtra("showName");
			if (mediaPlaying()) {
				showNotification(showName);
			}
		}
		
		return START_NOT_STICKY;
	}
	
	@Override
	public void onPrepared(MediaPlayer player) {
		player.start();
		showNotification(showName);
		Log.i(TAG,"Starting audio playback");
	}
	
	@Override
	public void onAudioFocusChange(int focusChange) {
		switch (focusChange) {
			case AudioManager.AUDIOFOCUS_GAIN:
				mediaPlay();
				if (mMediaPlayer != null) {
					mMediaPlayer.setVolume(1.0f, 1.0f);
				}
				Log.i(TAG,"Regained audio focus");
				break;
			
			case AudioManager.AUDIOFOCUS_LOSS:
				mediaStop(true);
				Log.i(TAG,"Lost audio focus");
				break;
			
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
				mediaStop(true);
				Log.i(TAG,"Temporarily lost audio focus");
				break;
			
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
				if (mediaPlaying()) mMediaPlayer.setVolume(0.1f, 0.1f);
				Log.i(TAG,"Temporarily lost audio focus to a flock of ducks overhead");
				break;
		}
	}
	
	@Override
	public boolean onError(MediaPlayer player, int what, int extra) {
		if (what == 1 && extra == -1004) { // HTTPDataSource had a boo-boo trying to read the stream
			Toast.makeText(getApplicationContext(), getResources().getString(R.string.media_access_error), Toast.LENGTH_LONG)
				.show();
			Log.e(TAG,"Failed to get stream connection");
		}

		mediaStop(false);
		return true;
	}
	
	@Override
	public void onCompletion(MediaPlayer player) {
		mediaStop(false);
		Toast.makeText(getApplicationContext(), getResources().getString(R.string.media_end), Toast.LENGTH_LONG)
			.show();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onDestroy() {
		mediaStop(false);
		
		super.onDestroy();
	}
	
	public static boolean mediaPlaying() {
		if ( mMediaPlayer == null ) {
			return false;
		} else {
			try {
				return mMediaPlayer.isPlaying();
			} catch (IllegalStateException e) {
				Log.e(TAG,e.getClass().getSimpleName()+": "+e.getMessage());
				return false;
			}
		}
	}

	private void mediaPlay() {
		if (mMediaPlayer != null)
			return;
		
		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		
		mMediaPlayer.setOnErrorListener(this);
		mMediaPlayer.setOnCompletionListener(this);
		
		try {
			mMediaPlayer.setDataSource((String) getResources().getText(R.string.stream_url));
		} catch (IllegalArgumentException e) { // Oh shi- what is this?
			Toast.makeText(getApplicationContext(), getResources().getString(R.string.media_illegal_error), Toast.LENGTH_LONG)
			.show();
			Log.e(TAG,e.getClass().getSimpleName()+": "+e.getMessage());
			return;
		} catch (IllegalStateException e) { // I wasn't ready yet
			Toast.makeText(getApplicationContext(), getResources().getString(R.string.media_illegal_error), Toast.LENGTH_LONG)
			.show();
			Log.e(TAG,e.getClass().getSimpleName()+": "+e.getMessage());
			return;
		} catch (NotFoundException e) { // HERE? NO WAI
			Toast.makeText(getApplicationContext(), getResources().getString(R.string.media_access_error), Toast.LENGTH_LONG)
			.show();
			Log.e(TAG,e.getClass().getSimpleName()+": "+e.getMessage());
			return;
		} catch (IOException e) { // INTERNETZ FAIL
			Toast.makeText(getApplicationContext(), getResources().getString(R.string.media_access_error), Toast.LENGTH_LONG)
			.show();
			Log.e(TAG,e.getClass().getSimpleName()+": "+e.getMessage());
			return;
		}
		
		// Locks
		mMediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
		wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
			.createWifiLock(WifiManager.WIFI_MODE_FULL, "ForgeMediaService");
		wifiLock.acquire();
		
		// Audio focus
		if (audioManager == null) {
			audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		}
		int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
		
		if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
			// TODO Find solution for lack of audio focus
			Log.w(TAG,"Failed to gain audio focus");
			return;
		}
		
		mMediaPlayer.setOnPreparedListener(this);
		mMediaPlayer.prepareAsync();
		Log.i(TAG,"Preparing audio playback");
	}
	
	private void mediaStop(boolean keepAudioFocus) {
		if (mediaPlaying()) {
			mMediaPlayer.stop();
		}
		if (mMediaPlayer != null) {
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
		
		// Release wifi lock
		if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
		
		// Give up audio focus
		if (! keepAudioFocus && audioManager != null) audioManager.abandonAudioFocus(this);
		
		// End foregrounded service status
		stopForeground(true);
		
		Log.i(TAG,"Stopping audio playback");
	}
	
	private void showNotification(String showName) {
		PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
						new Intent(getApplicationContext(), ListenActivity.class),
						PendingIntent.FLAG_UPDATE_CURRENT);
		Notification notification = new Notification();
		notification.tickerText = showName;
		notification.icon = R.drawable.notification;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.setLatestEventInfo(getApplicationContext(), getResources().getText(R.string.app_name),
						showName, pi);
		startForeground(NOTIFICATION_ID, notification);
	}
}