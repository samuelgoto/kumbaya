package com.kumbaya.android.client;

import java.sql.Date;
import java.util.List;
import java.util.concurrent.Executor;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.inject.Provider;
import com.kumbaya.android.R;
import com.kumbaya.android.client.MainActivity.Phone;
import com.kumbaya.android.client.PagerAdapter;
import com.kumbaya.android.client.sdk.BackgroundService;
import com.kumbaya.android.client.sdk.BackgroundService.LocalBinder;
import com.kumbaya.android.client.sdk.E164;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
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
	protected void onResume() {
	    super.onResume();
	    Log.d(TAG, "onResume");
		Intent i = new Intent(context, BackgroundService.class);
		bindService(i, connection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onPause() {
	    super.onPause();
	    Log.d(TAG, "onPause");
	    unbindService(connection);
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

	interface AsyncDbLoader<K> {
		CursorLoader load();
		List<K> read(Cursor cursor);
	}
	
	static class CallsLogLoader implements AsyncDbLoader<E164> {
		private final Provider<Context> context;
		
		CallsLogLoader(Provider<Context> context) {
			this.context = context;
		}
		
		@Override
		public CursorLoader load() {
			return new CursorLoader(
					context.get(),
					CallLog.Calls.CONTENT_URI,
					null,
					null,
					null,
					null);		
		}

		@Override
		public List<E164> read(Cursor managedCursor) {
			Optional<E164> localNumber = E164.localNumber(
					context.get());
			if (!localNumber.isPresent()) {
				return ImmutableList.of();
			}
			
			ImmutableList.Builder<E164> result = ImmutableList.builder();
			StringBuffer sb = new StringBuffer();
			int number = managedCursor.getColumnIndex(CallLog.Calls.NUMBER);
			int type = managedCursor.getColumnIndex(CallLog.Calls.TYPE);
			int date = managedCursor.getColumnIndex(CallLog.Calls.DATE);
			int duration = managedCursor.getColumnIndex(CallLog.Calls.DURATION);
			sb.append("Call Details :");
			while (managedCursor.moveToNext()) {
				String phoneNumber = managedCursor.getString(number);
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

				try {
					E164 normalized = E164.normalize(
							localNumber.get().country(), 
							localNumber.get().areaCode(), 
							phoneNumber); 
					result.add(normalized);
				} catch (NumberParseException e) {
					// Ignored.
				}
			}
			return result.build();
		}
	}
	
	static class ContactsLoader implements AsyncDbLoader<Phone> {
		private final Provider<Context> context;
		
		ContactsLoader(Provider<Context> context) {
			this.context = context;
		}

		@Override
		public CursorLoader load() {
			String[] projection = new String[] {
					ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
	                ContactsContract.CommonDataKinds.Phone.NUMBER,
	                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER};
			
			return new CursorLoader(
					context.get(),
					ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
					projection,
					null,
					null,
					null);
		}

		@Override
		public List<Phone> read(Cursor cur) {
			Optional<E164> localNumber = E164.localNumber(
					context.get());
			if (!localNumber.isPresent()) {
				return ImmutableList.of();
			}

			ImmutableList.Builder<Phone> result = ImmutableList.builder();

			int nameColumn = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
			int numberColumn = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

			if (cur.getCount() > 0) {
				while (cur.moveToNext()) {
					String name   = cur.getString(nameColumn);
					String number = cur.getString(numberColumn);
					try {
						E164 normalized = E164.normalize(
								localNumber.get().country(), 
								localNumber.get().areaCode(), 
								number);
						result.add(Phone.of(normalized.toString(), name));
					} catch (NumberParseException e) {
						// Ignoring.
					} 
				}
			}

			return result.build();
		}
	}
	
	private static Provider<Context> context(final Fragment fragment) {
		return new Provider<Context>() {
			@Override
			public Context get() {
				return fragment.getActivity();
			}
		};
	}
	
	public static class ContactsFragment extends Fragment implements 
	LoaderCallbacks<Cursor> {
		private static final String TAG = "ContactsFragment";
		private static final int CALLS_LOG_LOADER = 1;
		private static final int CONTACTS_LOADER = 2;
		private final CallsLogLoader callsLogLoader = new CallsLogLoader(
				context(this));
		private final ContactsLoader contactsLoader = new ContactsLoader(
				context(this));

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
				return callsLogLoader.load();
			}
			case CONTACTS_LOADER: {
				return contactsLoader.load();
			}
			}
			throw new UnsupportedOperationException("Can't create a loader of id: " + id);
		}

		@Override
		public void onLoadFinished(Loader<Cursor> loader, final Cursor cursor) {
			switch (loader.getId()) {
			case CALLS_LOG_LOADER: {
				new AsyncTask<Void, Void, List<E164>>() {
					@Override
					protected List<E164> doInBackground(Void... params) {
						return callsLogLoader.read(cursor);
					}
					@Override
				    protected void onPostExecute(List<E164> result) {
						setText(CALLS_LOG_LOADER, "Number of calls: " + result.size());
				    }
				}.execute();
				break;
			}
			case CONTACTS_LOADER: {
				new AsyncTask<Void, Void, List<Phone>>() {
					@Override
					protected List<Phone> doInBackground(Void... params) {
						return contactsLoader.read(cursor);
					}
					@Override
				    protected void onPostExecute(List<Phone> result) {
						setText(CONTACTS_LOADER, "Number of contacts: " + result.size());
				    }
				}.execute();

				break;
			}
			}
		}

		@Override
		public void onLoaderReset(Loader<Cursor> arg0) {
		}
	}
}