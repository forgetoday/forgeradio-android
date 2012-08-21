package com.forgetoday.radio;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class AboutActivity extends Activity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);
		
		final Resources r = getResources();
		
		TextView versionTV = (TextView) findViewById(R.id.versionTextView);
		try {
			PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			versionTV.setText(r.getString(R.string.version) + " " + pinfo.versionName);
		} catch (NameNotFoundException e) {
			versionTV.setText(r.getString(R.string.version) + " " + r.getString(R.string.unknown));
		}

	    Button contactBtn = (Button)findViewById(R.id.contactBtn);
	    // Register the onClick listener with the implementation above
	    contactBtn.setOnClickListener(new OnClickListener() {
	    	public void onClick(View v) {
	    		final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
				
				emailIntent.setType("plain/text");
				emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, r.getStringArray(R.array.support_email));
				emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, r.getString(R.string.app_name));
				
				startActivity(Intent.createChooser(emailIntent, r.getString(R.string.send_email)));
	    	}
	    });

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
	
}
