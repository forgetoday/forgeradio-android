package com.forgetoday.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;

public class MediaIntentReceiver extends BroadcastReceiver {
	private static final String TAG = "ForgeRadio";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		String intentAction = intent.getAction();
		
		if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intentAction)) {
			Log.i(TAG,"Pausing due to audio state change");
			
			Intent mediaServiceIntent = new Intent(context,MediaService.class);
			mediaServiceIntent.setAction(MediaService.ACTION_PAUSE);
			context.startService(mediaServiceIntent);
		} else if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
			int keyEvent = intent.getIntExtra(Intent.EXTRA_KEY_EVENT,0);
			// TODO Check media buttons work
			// TODO Restrict media buttons based on activity thread?
			if (KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE == keyEvent) {
				Intent mediaServiceIntent = new Intent(context,MediaService.class);
				mediaServiceIntent.setAction(MediaService.ACTION_PLAY_PAUSE);
				context.startService(mediaServiceIntent);
			} else if ( KeyEvent.KEYCODE_MEDIA_STOP == keyEvent ) {
				Intent mediaServiceIntent = new Intent(context,MediaService.class);
				mediaServiceIntent.setAction(MediaService.ACTION_PAUSE);
				context.startService(mediaServiceIntent);
			}
		}
	}
	
}