package com.kumbaya.android.client;

import java.sql.Date;
import java.util.List;
import java.util.concurrent.Executor;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.kumbaya.android.R;
import com.kumbaya.android.client.MainActivity.PagerAdapter;
import com.kumbaya.android.client.MainActivity.Phone;
import com.kumbaya.android.client.sdk.BackgroundService;
import com.kumbaya.android.client.sdk.BackgroundService.LocalBinder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

public class DebugActivity extends FragmentActivity {
	private static final String TAG = "DebugActivity";
	private final Executor executor = Executors.mainLooperExecutor();
    private final Activity context = this;
	private Optional<BackgroundService> service = Optional.absent();
	private PagerAdapter mDemoCollectionPagerAdapter;
	private DebugFragment debugFragment;
	private ContactsFragment contactsFragment;
	private ViewPager mViewPager;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        getActionBar().hide();
        
        setContentView(R.layout.debug);
        
		debugFragment = new DebugFragment();
		contactsFragment = new ContactsFragment();

		Fragment fragments[] = {
				debugFragment, 
				contactsFragment};

		mDemoCollectionPagerAdapter =
				new PagerAdapter(getSupportFragmentManager(), fragments);
		mViewPager = (ViewPager) findViewById(R.id.debug_pager);
		mViewPager.setAdapter(mDemoCollectionPagerAdapter);
		mViewPager.setCurrentItem(0);
		mViewPager.setPageTransformer(true, new ZoomOutPageTransformer());
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				// TextView text = (TextView) context.findViewById(R.id.progress_status);
				// text.setText("");
			}
		});

		// if (service.isPresent()) {
		// }
		
		Intent i = new Intent(context, BackgroundService.class);
		bindService(i, connection, Context.BIND_AUTO_CREATE);
	}
	
	private final ServiceConnection connection = new ServiceConnection() {
        @Override
		public void onServiceConnected(ComponentName name, IBinder b) {
			LocalBinder binder = (LocalBinder) b;
			service = Optional.of(binder.getService());

			executor.execute(new Runnable() {
				@Override
				public void run() {
					String result = service.get().toString();
					debugFragment.setText(result);
				}
			});
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
		}
	};

	@Override
	protected void onDestroy() {
		Log.i(TAG, "Destroying the activity.");
		super.onDestroy();
	}
	
	public static class DebugFragment extends Fragment {
		private static final String TAG = "DebugFragment";

		public void setText(String text) {
			if (getView() == null) {
				Log.e(TAG, "View not available.");
				return;
			}

			TextView textView = ((TextView) getView().findViewById(
					R.id.debug_log));
			textView.setText(text);
		}

		public String getText() {
			if (getView() == null) {
				Log.e(TAG, "View not available.");
				return "";
			}

			TextView textView = ((TextView) getView().findViewById(
					R.id.debug_log));
			return textView.getText().toString();
		}

		@Override
		public View onCreateView(LayoutInflater inflater,
				ViewGroup container, Bundle savedInstanceState) {
			return inflater.inflate(
					R.layout.debug_fragment, container, false);
		}
	}


	public static class ContactsFragment extends Fragment implements 
	LoaderCallbacks<Cursor> {
		private static final String TAG = "ContactsFragment";
		private static final int CALLS_LOG_LOADER = 1;
		private static final int CONTACTS_LOADER = 2;

		public void setText(int id, String text) {
			if (getView() == null) {
				Log.e(TAG, "View not available.");
				return;
			}

			final int view;
			switch (id) {
			case CALLS_LOG_LOADER:
				view = R.id.calls_log_view;
				break;
			case CONTACTS_LOADER:
				view = R.id.contacts_view;
				break;
			default:
				throw new UnsupportedOperationException("Invalid id" + id);
			}
			TextView textView = ((TextView) getView().findViewById(view));
			textView.setText(text);
		}

		public String getText() {
			if (getView() == null) {
				Log.e(TAG, "View not available.");
				return "";
			}

			TextView textView = ((TextView) getView().findViewById(
					R.id.contacts_view));
			return textView.getText().toString();
		}

		@Override
		public View onCreateView(LayoutInflater inflater,
				ViewGroup container, Bundle savedInstanceState) {
			getLoaderManager().initLoader(CALLS_LOG_LOADER, null, this);
			getLoaderManager().initLoader(CONTACTS_LOADER, null, this);
			return inflater.inflate(
					R.layout.contacts_fragment, container, false);
		}

		@Override
		public Loader<Cursor> onCreateLoader(int id, Bundle args) {
			switch (id) {
			case CALLS_LOG_LOADER: {
				return new CursorLoader(
						getActivity(),
						CallLog.Calls.CONTENT_URI,
						null,
						null,
						null,
						null);		
			}
			case CONTACTS_LOADER: {
				String[] projection = new String[] {
						ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
		                ContactsContract.CommonDataKinds.Phone.NUMBER};
				
				return new CursorLoader(
						getActivity(),
						ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
						projection,
						null,
						null,
						null);
			}
			}
			throw new UnsupportedOperationException("Can't create a loader of id: " + id);
		}

		public List<Phone> readContacts(Cursor cur) {
			ImmutableList.Builder<Phone> result = ImmutableList.builder();

			int nameColumn = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
			int numberColumn = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

			if (cur.getCount() > 0) {
				while (cur.moveToNext()) {
					String name   = cur.getString(nameColumn);
					String number = cur.getString(numberColumn);
					result.add(Phone.of(number, name));
				}
			}

			return result.build();
		}

		private List<String> getCallLog(Cursor managedCursor) {
			ImmutableList.Builder<String> result = ImmutableList.builder();
			StringBuffer sb = new StringBuffer();
			int number = managedCursor.getColumnIndex(CallLog.Calls.NUMBER);
			int type = managedCursor.getColumnIndex(CallLog.Calls.TYPE);
			int date = managedCursor.getColumnIndex(CallLog.Calls.DATE);
			int duration = managedCursor.getColumnIndex(CallLog.Calls.DURATION);
			sb.append("Call Details :");
			while (managedCursor.moveToNext()) {
				String phNumber = managedCursor.getString(number);
				String callType = managedCursor.getString(type);
				String callDate = managedCursor.getString(date);
				Date callDayTime = new Date(Long.valueOf(callDate));
				String callDuration = managedCursor.getString(duration);
				String dir = null;
				int dircode = Integer.parseInt(callType);
				switch (dircode) {
				case CallLog.Calls.OUTGOING_TYPE:
					dir = "OUTGOING";
					break;

				case CallLog.Calls.INCOMING_TYPE:
					dir = "INCOMING";
					break;

				case CallLog.Calls.MISSED_TYPE:
					dir = "MISSED";
					break;
				}
				result.add(phNumber);
			}
			return result.build();
		}

		@Override
		public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
			switch (loader.getId()) {
			case CALLS_LOG_LOADER: {
				List<String> result = getCallLog(cursor);
				setText(CALLS_LOG_LOADER, "Number of calls: " + result.size());
				break;
			}
			case CONTACTS_LOADER: {
				List<Phone> result = readContacts(cursor);
				setText(CONTACTS_LOADER, "Number of contacts: " + result.size());
				break;
			}
			}
		}

		@Override
		public void onLoaderReset(Loader<Cursor> arg0) {
		}
	}
}
