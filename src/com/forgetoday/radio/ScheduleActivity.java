package com.forgetoday.radio;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.ExpandableListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class ScheduleActivity extends ExpandableListActivity {
	
	private static final String TAG = "ForgeRadio";
	private static ArrayList<ArrayList<HashMap<String, String>>> schedule;
	ProgressDialog progressDialog;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		progressDialog = ProgressDialog.show(this, "", getResources().getString(R.string.loading));
		
		if (! buildSchedule()) {
			Toast.makeText(getApplicationContext(), getResources().getString(R.string.schedule_error), Toast.LENGTH_LONG)
				.show();
			finish();
		}
		
		progressDialog.dismiss();
		
		ScheduleAdapter adapter = new ScheduleAdapter(this, null, R.layout.schedule_grouplayout, null, null, schedule, R.layout.schedule_childlayout, null, null);
		setListAdapter(adapter);
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
	
	private boolean buildSchedule() {
		if (ListenActivity.schedule == null) {
			return false;
		}
		
		long time = System.currentTimeMillis();
		int localOffset = TimeZone.getDefault().getOffset(time) / 1000;
		int radioOffset = TimeZone.getTimeZone("Europe/London").getOffset(time) / 1000;

		JSONObject show = null;
		Calendar c = Calendar.getInstance();
		
		// Populate the week
		schedule = new ArrayList<ArrayList<HashMap<String, String>>>(7);
		int i;
		for (i=0;i<7;i++) {
			schedule.add(new ArrayList<HashMap<String, String>>());
		}
		
		int count = ListenActivity.schedule.length();
		for (i=0;i<count;i++) {
			try {
				show = ListenActivity.schedule.getJSONObject(i);
				HashMap<String, String> showmap = new HashMap<String, String>(3);
				
				int start = show.getInt("start") - radioOffset + localOffset;
				int end = show.getInt("end") - radioOffset + localOffset;
				
				if (start >= 7*24*60*60) {
					start -= 7*24*60*60;
				}
				if (end >= 7*24*60*60) {
					end -= 7*24*60*60;
				}
				
				int day = (int) Math.floor(start / (24*60*60));
				
				// Keep Sunday at the end of the week
				if (day == 0) {
					day = 6;
				} else {
					day -= 1;
				}
				
				showmap.put("offset",new Integer(start).toString());
				
				c.setTimeInMillis(start*1000);
				showmap.put("start",RadioApp.hourMinuteWithOffset(start,0));
				
				c.setTimeInMillis(end*1000);
//				showmap.put("end",RadioApp.hourMinuteWithOffset(end,0));
				
//				showmap.put("imageUrl",show.getString("image"));
//				showmap.put("linkUrl",show.getString("link"));
				showmap.put("name",show.getString("name"));
				
				schedule.get(day).add(showmap);
			} catch (JSONException e) {
				Log.e(TAG,"Failed to add show");
			}
		}
		
		HashMap<String, String> deadmap = null;
		
		for (i=0;i<7;i++) {
			final ArrayList<HashMap<String, String>> day = schedule.get(i);
			
			if (day.size() == 0) {
				if (deadmap == null) {
					deadmap = new HashMap<String, String>(3);
					deadmap.put("start","");
//					deadmap.put("end","");
//					deadmap.put("imageUrl","");
//					deadmap.put("linkUrl","");
					deadmap.put("name",(String) getResources().getText(R.string.off_air_simple));
				}
				
				day.add(deadmap);
			} else {
				Collections.sort(day, new Comparator<HashMap<String, String>>() {
					@Override
					public int compare(HashMap<String, String> map1,
							HashMap<String, String> map2) {
						return new Integer(map1.get("offset"))
							.compareTo(new Integer(map2.get("offset")));
					}
				});
			}
		}
		
		return true;
	}
	
	static class ChildViewCache {
		private View baseView;
		private TextView nameView;
		private TextView timeView;
		private ImageView imageView;
		
		public ChildViewCache(View baseView) {
			this.baseView = baseView;
		}
	 
		public TextView getNameView() {
			if (nameView == null) {
				nameView = (TextView) baseView.findViewById(R.id.scheduleNameView);
			}
			return nameView;
		}
		
		public TextView getTimeView() {
			if (timeView == null) {
				timeView = (TextView) baseView.findViewById(R.id.scheduleTimeView);
			}
			return timeView;
		}
	 
		public ImageView getImageView() {
			if (imageView == null) {
				imageView = (ImageView) baseView.findViewById(R.id.catchupShowImage);
			}
			return imageView;
		}
	}
	
	static class GroupViewCache {
		private View baseView;
		private TextView dayView;
		
		public GroupViewCache(View baseView) {
			this.baseView = baseView;
		}
	 
		public TextView getDayView() {
			if (dayView == null) {
				dayView = (TextView) baseView.findViewById(R.id.scheduleDayView);
			}
			return dayView;
		}
	}
	
	private static class ScheduleAdapter extends SimpleExpandableListAdapter {

		private List<? extends List<? extends Map<String, ?>>> mChildData;
		final private static String[] weekdays = new DateFormatSymbols().getWeekdays();
		
		public ScheduleAdapter(Context context,
				List<? extends Map<String, ?>> groupData, int groupLayout,
				String[] groupFrom, int[] groupTo,
				List<? extends List<? extends Map<String, ?>>> childData,
				int childLayout, String[] childFrom, int[] childTo) {
			super(context, groupData, groupLayout, groupFrom, groupTo, childData,
					childLayout, childFrom, childTo);
			
			mChildData = childData;
		}
		
		@Override
		public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
			View rowView = convertView;
			ChildViewCache viewCache;
			
			if (rowView == null) {
				rowView = newChildView(isLastChild, parent);
				viewCache = new ChildViewCache(rowView);
				rowView.setTag(viewCache);
			} else {
				viewCache = (ChildViewCache) rowView.getTag();
			}
			
			@SuppressWarnings("unchecked")
			HashMap<String, String> childData = (HashMap<String, String>) mChildData.get(groupPosition).get(childPosition);
			
			String start = childData.get("start");
//			String end = childData.get("end");
//			String imageUrl = childData.get("imageUrl");
//			String linkUrl = childData.get("linkUrl");
			String name = childData.get("name");
			
			TextView nameView = viewCache.getNameView();
			TextView timeView = viewCache.getTimeView();
			
			nameView.setText(name);
			timeView.setText(start);
			
			return rowView;
		}
		
		@Override
		public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
			View rowView = convertView;
			GroupViewCache viewCache;
			if (rowView == null) {
				rowView = newGroupView(isExpanded, parent);
				viewCache = new GroupViewCache(rowView);
				rowView.setTag(viewCache);
			} else {
				viewCache = (GroupViewCache) rowView.getTag();
			}
			
			// Reverse the dohickeys
			int day = groupPosition;
			if (day == 6) {
				day = 1;
			} else {
				day += 2;
			}
			
			TextView dayView = viewCache.getDayView();
			dayView.setText(weekdays[day]);
			
			return rowView;
		}
		
		@Override
		public int getGroupCount() {
			return 7;
		}
		
		@Override
		public boolean isChildSelectable(int groupPosition, int childPosition) {
			return false;
		}
		
	}
	
}