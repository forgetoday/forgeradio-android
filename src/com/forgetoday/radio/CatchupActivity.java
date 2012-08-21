package com.forgetoday.radio;

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.forgetoday.radio.AsyncImageLoader.ImageCallback;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class CatchupActivity extends Activity implements OnClickListener, OnItemClickListener {
	
	private static final String TAG = "ForgeRadio";
	protected ArrayList<CatchupShow> shows;
	private static String catchupPrev;
	private static String catchupNext;
	private static ListView listView;
	private static Button prevButton;
	private static Button nextButton;
	private static TextView dateTV1;
	private static TextView dateTV2;
	private static ProgressDialog progressDialog;
	private static CatchupAdapter adapter;
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 1 && resultCode == 1) {
			setResult(1,new Intent());
			finish();
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// UI setup
		setContentView(R.layout.catchup);
		
		listView = (ListView)findViewById(R.id.listView1);
		prevButton = (Button)findViewById(R.id.schedulePrevBtn);
		nextButton = (Button)findViewById(R.id.scheduleNextBtn);
		dateTV1 = (TextView)findViewById(R.id.scheduleDate1);
		dateTV2 = (TextView)findViewById(R.id.scheduleDate2);
		
		listView.setOnItemClickListener(this);
		prevButton.setOnClickListener(this);
		nextButton.setOnClickListener(this);
		
		// Bundle restore
		if (savedInstanceState == null) {
			progressDialog = ProgressDialog.show(this, "", getResources().getString(R.string.loading));
			new getShowsTask().execute((String)getResources().getText(R.string.catchup_url), "setup");
		} else {
			shows = savedInstanceState.getParcelableArrayList("shows");
			catchupPrev = savedInstanceState.getString("catchupPrev");
			catchupNext = savedInstanceState.getString("catchupNext");
			
			prevButton.setEnabled(catchupPrev != null);
			nextButton.setEnabled(catchupNext != null);
			
			setupAdapter();
			
			// TODO-LATER Add list position restoration?
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.generic_menu, menu);
		return true;
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
	
	@Override
	protected void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		
		if (shows != null) {
			savedInstanceState.putParcelableArrayList("shows", shows);
		}
		if (catchupPrev != null) {
			savedInstanceState.putString("catchupPrev", catchupPrev);
		}
		if (catchupNext != null) {
			savedInstanceState.putString("catchupNext", catchupNext);
		}
	}
	
	@Override
	public void onClick(View v) {
		if (prevButton == v) {
			progressDialog = ProgressDialog.show(this, "", getResources().getString(R.string.loading));
			new getShowsTask().execute(catchupPrev, "update");
		} else if (nextButton == v) {
			if (catchupNext != null) {
				progressDialog = ProgressDialog.show(this, "", getResources().getString(R.string.loading));
				new getShowsTask().execute(catchupNext, "update");
			}
		}
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		CatchupShow show = (CatchupShow) parent.getItemAtPosition(position);
		
		Intent intent = new Intent(getApplicationContext(), CatchupListenActivity.class);
		intent.putExtra("url", "http://api.mixcloud.com"+show.key+"embed-html/");
		startActivityForResult(intent, 1);
	}
	
	private class getShowsTask extends AsyncTask<String, String, String> {
		@Override
		protected String doInBackground(String... params) {
			JSONArray data;
			JSONObject jsonobj;
			String jsonstring = RadioApp.getTextFile(params[0]);
			
			Log.i(TAG,"Loading feed from: "+params[0]);
			
			if (jsonstring == null)
				return null;
			
			Log.v(TAG,"JSON String: "+jsonstring);
			
			try {
				jsonobj = new JSONObject(jsonstring);
			} catch (JSONException e) {
				Log.e(TAG,"JSONException: "+e.getMessage());
				return null;
			}
			
			try {
				JSONObject paging = jsonobj.getJSONObject("paging");
				
				try {
					catchupPrev = paging.getString("previous");
				} catch (JSONException pe) {
					catchupPrev = null;
				}
				
				try {
					catchupNext = paging.getString("next");
				} catch (JSONException pe) {
					catchupNext = null;
				}
			} catch (JSONException e) {
				Log.e(TAG,"JSONException: "+e.getMessage());
				return null;
			}
			
			try {
				data = jsonobj.getJSONArray("data");
			} catch (JSONException e) { // I've fallen and cannot get up
				Log.e(TAG,"JSONException: "+e.getMessage());
				return null;
			}
			
			shows = new ArrayList<CatchupShow>(20);
			
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd\'T\'HH:mm:ss\'Z\'Z"); // Part 1 of dirty hack for ISO 8601
			int i;
			for (i=0;i<data.length();i++) {
				try {
					JSONObject show = data.getJSONObject(i);
					CatchupShow showobj = new CatchupShow(null);
					
					try {
						showobj.timestamp = show.getString("created_time");
						showobj.date = sdf.parse(showobj.timestamp+"+0000"); // Part 2
					} catch (JSONException je) {
					} catch (ParseException pe) {
						Log.e(TAG,"Exception parsing timestamp: "+pe.getMessage());
					}
					
					try {
						showobj.name = show.getString("name");
					} catch (JSONException je) {}
					
					try {
						showobj.imageUrl = show.getJSONObject("pictures").getString("medium_mobile");
					} catch (JSONException je) {}
					
					try {
						showobj.key = show.getString("key");
					} catch (JSONException je)  {}
					
					shows.add(showobj);
				} catch (JSONException e) {
					Log.e(TAG,"JSONException: "+e.getMessage());
					return null;
				}
			}
			
			return params[1];
		}
		
		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			
			if (progressDialog != null) {
				progressDialog.dismiss();
			}
			
			// Load new resultset
			if (result == null) {
				Toast.makeText(getApplicationContext(), getResources().getString(R.string.catchup_error), Toast.LENGTH_LONG);
				Log.d(TAG,"No results found for page");
			} else if (result == "setup") {
				setupAdapter();
			} else if (result == "update") {
				adapter.clear();
				int i;
				for (i=0;i<shows.size();i++) {
					adapter.add(shows.get(i));
				}
				adapter.notifyDataSetChanged();
			}
			
			// Set button states
			prevButton.setEnabled(catchupPrev != null);
			nextButton.setEnabled(catchupNext != null);
			
			// Display date range
			Date start = shows.get(0).date;
			Date end = shows.get(shows.size()-1).date;
			
			if (start == null || end == null) { // Failed date parse
				dateTV1.setText(shows.get(0).timestamp);
				dateTV2.setText(shows.get(shows.size()-1).timestamp);
			} else {
				final String[] months = new DateFormatSymbols().getMonths();
				Calendar c = Calendar.getInstance();
				
				c.setTime(start);
				int day = c.get(Calendar.DAY_OF_MONTH);
				int month = c.get(Calendar.MONTH);
				int year = c.get(Calendar.YEAR);
				dateTV1.setText(day + getOrdinalFor(day) + " " + months[month] + " " + year);
				
				c.setTime(end);
				day = c.get(Calendar.DAY_OF_MONTH);
				month = c.get(Calendar.MONTH);
				year = c.get(Calendar.YEAR);
				dateTV2.setText("to " + day + getOrdinalFor(day) + " " + months[month] + " " + year);
			}
		}
	}
	
	private static String getOrdinalFor(int value) {
		int hundredRemainder = value % 100;
		if (hundredRemainder >= 10 && hundredRemainder <= 20) {
			return "th";
		}
		int tenRemainder = value % 10;
		switch (tenRemainder) {
			case 1:
				return "st";
			case 2:
				return "nd";
			case 3:
				return "rd";
			default:
				return "th";
		}
	}

	protected void setupAdapter() {
		adapter = new CatchupAdapter(this, shows);
		listView.setAdapter(adapter);
	}
	
	private static class ViewCache {
		private View baseView;
		private TextView nameView;
		private TextView dateView;
		private ImageView imageView;
		
		public ViewCache(View baseView) {
			this.baseView = baseView;
		}
	 
		public TextView getNameView() {
			if (nameView == null) {
				nameView = (TextView) baseView.findViewById(R.id.catchupShowName);
			}
			return nameView;
		}
		
		public TextView getDateView() {
			if (dateView == null) {
				dateView = (TextView) baseView.findViewById(R.id.catchupShowDate);
			}
			return dateView;
		}
	 
		public ImageView getImageView() {
			if (imageView == null) {
				imageView = (ImageView) baseView.findViewById(R.id.catchupShowImage);
			}
			return imageView;
		}
	}
	
	protected static class CatchupShow implements Parcelable {
		public Date date;
		public String timestamp;
		public String name;
		public String imageUrl;
		public String key;
		
		@Override
		public void writeToParcel(Parcel out, int flags) {
			long datelong = date.getTime();
			
			out.writeLong(datelong);
			out.writeString(timestamp);
			out.writeString(name);
			out.writeString(imageUrl);
			out.writeString(key);
		}
		
		@Override
		public int describeContents() {
			return 0;
		}
		
		public static final Parcelable.Creator<CatchupShow> CREATOR
				 = new Parcelable.Creator<CatchupShow>() {
			 @Override
			 public CatchupShow createFromParcel(Parcel in) {
				 return new CatchupShow(in);
			 }
			 
			 @Override
			 public CatchupShow[] newArray(int size) {
				 return new CatchupShow[size];
			 }
		 };
		 
		 CatchupShow(Parcel in) {
			 if (in == null) {
				 return;
			 }
			 
			 long datelong = in.readLong();
			 timestamp = in.readString();
			 name = in.readString();
			 imageUrl = in.readString();
			 key = in.readString();
			 
			 date = new Date(datelong);
		 }

	}
	
	private static class CatchupAdapter extends ArrayAdapter<CatchupShow> {
		private static AsyncImageLoader asyncImageLoader;
		
		public CatchupAdapter(Context context, ArrayList<CatchupShow> shows) {
			super(context, 0, shows);

			asyncImageLoader = new AsyncImageLoader();
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			// Inflate the views from XML
			View rowView = convertView;
			ViewCache viewCache;
			CatchupShow show = getItem(position);
			
			if (rowView == null) {
				LayoutInflater inflater = ((Activity)getContext()).getLayoutInflater();
				rowView = inflater.inflate(R.layout.catchup_rowlayout, null);
				viewCache = new ViewCache(rowView);
				rowView.setTag(viewCache);
			} else {
				viewCache = (ViewCache) rowView.getTag();
			}
			
			// Load the image and set it on the ImageView
			ImageView imageView = viewCache.getImageView();
			imageView.setTag(show.imageUrl + position);
			Drawable cachedImage = asyncImageLoader.loadDrawable(show.imageUrl, new ImageCallback() {
				@Override
				public void imageLoaded(Drawable imageDrawable, String imageUrl, int i) {
					ImageView imageViewByTag = (ImageView) listView.findViewWithTag(imageUrl + i);
					if (imageViewByTag != null) {
						if (imageDrawable == null) {
							imageViewByTag.setImageDrawable(RadioApp.getContext().getResources().getDrawable(R.drawable.icon));
							Log.e(TAG,"Failed to load catchup show image "+imageUrl);
						} else {
							imageViewByTag.setImageDrawable(imageDrawable);
						}
					}
				}
			}, position);
			imageView.setImageDrawable(cachedImage);
	 
			// Set the text on the TextViews
			TextView nameView = viewCache.getNameView();
			TextView dateView = viewCache.getDateView();
			nameView.setText(show.name);
			
			if (show.date == null) { // Failed date parse
				dateView.setText(show.timestamp);
			} else {
				Calendar c = Calendar.getInstance();
				c.setTime(show.date);
				dateView.setText(c.get(Calendar.DAY_OF_MONTH) + "/" + c.get(Calendar.MONTH) + "/" + c.get(Calendar.YEAR));
			}
			
			return rowView;
		}
	}
	
}