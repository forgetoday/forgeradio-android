package com.forgetoday.radio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.TimeZone;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class RadioApp extends Application {
	private static final String TAG = "ForgeRadio";
	private static Context mContext;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mContext = this;
	}

	protected static Context getContext() {
		return mContext;
	}
	
	static String getTextFile(String url) {
		BufferedReader br = null;
		String outputString = "";
				
		HttpGet httpRequest = null;
		HttpResponse response = null;
		InputStream instream = null;
		
		httpRequest = new HttpGet(url);
		
		HttpParams httpParameters = new BasicHttpParams();
		// Set the timeout in milliseconds until a connection is established.
		int timeoutConnection = 3000;
		HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
		// Set the default socket timeout (SO_TIMEOUT) 
		// in milliseconds which is the timeout for waiting for data.
		int timeoutSocket = 10000;
		HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
		
		HttpClient httpclient = new DefaultHttpClient(httpParameters);
		try {
			response = (HttpResponse) httpclient.execute(httpRequest);
		} catch (ClientProtocolException e) {
			Log.e(TAG,"ClientProtocolException: "+e.getMessage());
			return null;
		} catch (IOException e) {
			Log.e(TAG,"IOException: "+e.getMessage());
			return null;
		}
		
		HttpEntity entity = response.getEntity();
		BufferedHttpEntity bufHttpEntity;
		try {
			bufHttpEntity = new BufferedHttpEntity(entity);
			instream = bufHttpEntity.getContent();
		} catch (IOException e) {
			Log.e(TAG,"IOException: "+e.getMessage());
			return null;
		}
		br = new BufferedReader(new InputStreamReader(instream));
		
		StringBuffer buffer = new StringBuffer();
		String line;
		
		try {
			while ((line = br.readLine()) != null) {
				buffer.append(line + "\n");
			}
			outputString = buffer.toString();
		} catch (IOException e) {
			Log.e(TAG,"IOException: "+e.getMessage());
			return null;
		}
		
		return outputString;
	}
	
	static String hourMinute(int time) {
		return hourMinuteWithOffset(time,TimeZone.getDefault().getOffset(System.currentTimeMillis()));
	}
	
	static String hourMinuteWithOffset(int time, int offset) {
		String amPm;
		
		time = time + offset/1000;
		if ( time > 7*24*60*60 )
			time = time - 7*24*60*60;
		
		int rem = time % (24*60*60);
//		int day = (time - rem) / (24*60*60) + 1;
		int minute = (int)Math.floor((rem % (60*60)) / 60);
		int hour = (rem - minute) / (60*60);
		
		if ( hour >= 12 ) {
			if ( hour != 12 ) {
				hour = hour - 12;
			}
			amPm = "pm";
		}
		else {
			if ( hour == 0 ) {
				hour = 12;
			}
			amPm = "am";
		}
		
		return hour + ":" + String.format("%02d",minute) + amPm;
	}
	
}
