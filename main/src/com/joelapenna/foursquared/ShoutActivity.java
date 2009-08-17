/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.types.CheckinResult;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.widget.VenueView;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 */
public class ShoutActivity extends Activity {
    public static final String TAG = "ShoutActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String EXTRA_VENUE_NAME = "com.joelapenna.foursquared.ShoutActivity.VENUE_NAME";
    public static final String EXTRA_VENUE_ADDRESS = "com.joelapenna.foursquared.ShoutActivity.VENUE_ADDRESS";
    public static final String EXTRA_VENUE_CROSSSTREET = "com.joelapenna.foursquared.ShoutActivity.VENUE_CROSSSTREET";
    public static final String EXTRA_VENUE_CITY = "com.joelapenna.foursquared.ShoutActivity.VENUE_CITY";
    public static final String EXTRA_VENUE_ZIP = "com.joelapenna.foursquared.ShoutActivity.VENUE_ZIP";
    public static final String EXTRA_VENUE_STATE = "com.joelapenna.foursquared.ShoutActivity.VENUE_STATE";
    public static final String EXTRA_IMMEDIATE_CHECKIN = "com.joelapenna.foursquared.ShoutActivity.IMMEDIATE_CHECKIN";
    public static final String EXTRA_SHOUT = "com.joelapenna.foursquared.ShoutActivity.SHOUT";

    private static final int DIALOG_CHECKIN_PROGRESS = 1;

    private StateHolder mStateHolder = new StateHolder();

    private boolean mIsShouting = true;
    private boolean mImmediateCheckin = true;
    private boolean mTellFriends = true;
    private boolean mTellTwitter = false;
    private String mShout = null;

    private Button mCheckinButton;
    private CheckBox mTwitterCheckBox;
    private CheckBox mFriendsCheckBox;
    private EditText mShoutEditText;
    private VenueView mVenueView;

    private BroadcastReceiver mLoggedInReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "onReceive: " + intent);
            finish();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");
        registerReceiver(mLoggedInReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        // Implies there is no UI.
        if (getIntent().hasExtra(EXTRA_IMMEDIATE_CHECKIN)) {
            mImmediateCheckin = getIntent().getBooleanExtra(EXTRA_IMMEDIATE_CHECKIN, true);
            if (DEBUG) Log.d(TAG, "Immediate Checkin (from extra): " + mImmediateCheckin);
        } else {
            mImmediateCheckin = PreferenceManager.getDefaultSharedPreferences(ShoutActivity.this)
                    .getBoolean(Preferences.PREFERENCE_IMMEDIATE_CHECKIN, true);
            if (DEBUG) Log.d(TAG, "Immediate Checkin (from preference): " + mImmediateCheckin);
        }

        mIsShouting = getIntent().getBooleanExtra(ShoutActivity.EXTRA_SHOUT, false);
        if (mIsShouting) {
            if (DEBUG) Log.d(TAG, "Immediate checkin disabled, this is a shout.");
            mImmediateCheckin = false;
        }

        if (DEBUG) Log.d(TAG, "Is Shouting: " + mIsShouting);
        if (DEBUG) Log.d(TAG, "Immediate Checkin: " + mImmediateCheckin);

        if (getLastNonConfigurationInstance() != null) {
            if (DEBUG) Log.d(TAG, "Using last non configuration instance");
            mStateHolder = (StateHolder)getLastNonConfigurationInstance();
        } else if (!mIsShouting) {
            // Translate the extras received in this intent into a venue, then attach it to the
            // venue view.
            mStateHolder.venue = new Venue();
            intentExtrasIntoVenue(getIntent(), mStateHolder.venue);
        }

        SharedPreferences settings = PreferenceManager
                .getDefaultSharedPreferences(ShoutActivity.this);

        mTellFriends = settings.getBoolean(Preferences.PREFERENCE_SHARE_CHECKIN, mTellFriends);
        mTellTwitter = settings.getBoolean(Preferences.PREFERENCE_TWITTER_CHECKIN, mTellTwitter);

        // Depending on how we were initialized, we finish up by either displaying a UI, checking
        // in, or displaying a checkin result.

        if (mStateHolder.checkinResult != null) {
            createCheckinResultDialog(mStateHolder.checkinResult).show();

        } else if (mImmediateCheckin) {
            setVisible(false);
            if (mStateHolder.checkinTask == null) {
                if (DEBUG) Log.d(TAG, "Immediate checkin is set.");
                mStateHolder.checkinTask = new CheckinTask().execute();
            }

        } else {
            initializeUi();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mLoggedInReceiver);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return mStateHolder;
    }

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_CHECKIN_PROGRESS:
                String title = (mIsShouting) ? "Shouting!" : "Checking in!";
                String messageAction = (mIsShouting) ? "shout!" : "check-in!";
                ProgressDialog dialog = new ProgressDialog(this);
                dialog.setCancelable(true);
                dialog.setIndeterminate(true);
                dialog.setTitle(title);
                dialog.setIcon(android.R.drawable.ic_dialog_info);
                dialog.setMessage("Please wait while we " + messageAction);
                dialog.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mStateHolder.checkinTask.cancel(true);
                    }
                });
                return dialog;
        }
        return null;
    }

    /**
     * Because we cannot parcel venues properly yet (issue #5) we have to mutate a series of intent
     * extras into a venue so that we can code to this future possibility.
     */
    public static void intentExtrasIntoVenue(Intent intent, Venue venue) {
        Bundle extras = intent.getExtras();
        venue.setId(extras.getString(Foursquared.EXTRA_VENUE_ID));
        venue.setName(extras.getString(EXTRA_VENUE_NAME));
        venue.setAddress(extras.getString(EXTRA_VENUE_ADDRESS));
        venue.setCrossstreet(extras.getString(EXTRA_VENUE_CROSSSTREET));
        venue.setCity(extras.getString(EXTRA_VENUE_CITY));
        venue.setZip(extras.getString(EXTRA_VENUE_ZIP));
        venue.setState(extras.getString(EXTRA_VENUE_STATE));
    }

    public static void venueIntoIntentExtras(Venue venue, Intent intent) {
        intent.putExtra(Foursquared.EXTRA_VENUE_ID, venue.getId());
        intent.putExtra(ShoutActivity.EXTRA_VENUE_NAME, venue.getName());
        intent.putExtra(ShoutActivity.EXTRA_VENUE_ADDRESS, venue.getAddress());
        intent.putExtra(ShoutActivity.EXTRA_VENUE_CITY, venue.getCity());
        intent.putExtra(ShoutActivity.EXTRA_VENUE_CROSSSTREET, venue.getCrossstreet());
        intent.putExtra(ShoutActivity.EXTRA_VENUE_STATE, venue.getState());
        intent.putExtra(ShoutActivity.EXTRA_VENUE_ZIP, venue.getZip());
    }

    private AlertDialog createCheckinResultDialog(CheckinResult checkinResult) {
        Builder dialogBuilder = new AlertDialog.Builder(this) //
                .setIcon(android.R.drawable.ic_dialog_info) // icon
                .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                }); //

        // Set up the custom view for it.
        LayoutInflater inflater = (LayoutInflater)getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.checkin_result_dialog,
                (ViewGroup)findViewById(R.id.layout_root));
        dialogBuilder.setView(layout);

        // Set the text message of the result.
        TextView messageView = (TextView)layout.findViewById(R.id.messageTextView);
        messageView.setText(checkinResult.getMessage());

        // Set the title and web view which vary based on if the user is shouting.

        if (mIsShouting) {
            dialogBuilder.setTitle("Shouted!");

        } else {
            dialogBuilder.setTitle("Checked in @ " + checkinResult.getVenue().getName());
            WebView webView = (WebView)layout.findViewById(R.id.webView);

            String checkinId = checkinResult.getId();
            String userId = PreferenceManager.getDefaultSharedPreferences(this).getString(
                    Preferences.PREFERENCE_ID, "");
            webView.loadUrl(Foursquared.getFoursquare().checkinResultUrl(userId, checkinId));

        }
        return dialogBuilder.create();
    }

    private void initializeUi() {
        setTheme(android.R.style.Theme_Dialog);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.shout_activity);

        mCheckinButton = (Button)findViewById(R.id.checkinButton);
        mFriendsCheckBox = (CheckBox)findViewById(R.id.tellFriendsCheckBox);
        mTwitterCheckBox = (CheckBox)findViewById(R.id.tellTwitterCheckBox);
        mShoutEditText = (EditText)findViewById(R.id.shoutEditText);
        mVenueView = (VenueView)findViewById(R.id.venue);

        mCheckinButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mCheckinButton.setEnabled(false);
                String shout = mShoutEditText.getText().toString();
                if (!TextUtils.isEmpty(shout)) {
                    mShout = shout;
                }
                mStateHolder.checkinTask = new CheckinTask().execute();
            }
        });

        mTwitterCheckBox.setChecked(mTellTwitter);

        mTwitterCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mTellTwitter = isChecked;
                mTwitterCheckBox.setEnabled(isChecked);
            }
        });
        mFriendsCheckBox.setChecked(mTellFriends);
        mFriendsCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mTellFriends = isChecked;
                mTwitterCheckBox.setEnabled(isChecked);

                if (!isChecked) {
                    mTellTwitter = false;
                    mTwitterCheckBox.setChecked(false);
                }
            }
        });

        if (mIsShouting) {
            mVenueView.setVisibility(ViewGroup.GONE);
            mFriendsCheckBox.setChecked(true);
            mFriendsCheckBox.setEnabled(false);
            mCheckinButton.setText("Shout!");
        } else {
            mVenueView.setVenue(mStateHolder.venue);
        }
    }

    class CheckinTask extends AsyncTask<Void, Void, CheckinResult> {

        private Exception mReason;

        @Override
        public void onPreExecute() {
            showDialog(DIALOG_CHECKIN_PROGRESS);
        }

        @Override
        public CheckinResult doInBackground(Void... params) {
            String venueId;
            if (mStateHolder.venue == null) {
                venueId = null;
            } else {
                venueId = mStateHolder.venue.getId();
            }

            boolean isPrivate = !mTellFriends;

            try {
                return Foursquared.getFoursquare().checkin(venueId, null, mShout, isPrivate,
                        mTellTwitter);
            } catch (Exception e) {
                Log.d(TAG, "Storing reason: ", e);
                mReason = e;
            }
            return null;
        }

        @Override
        public void onPostExecute(CheckinResult checkinResult) {
            if (DEBUG) Log.d(TAG, "CheckinTask: onPostExecute()");

            dismissDialog(DIALOG_CHECKIN_PROGRESS);

            if (checkinResult == null) {
                NotificationsUtil.ToastReasonForFailure(ShoutActivity.this, mReason);
                if (mImmediateCheckin) {
                    finish();
                } else {
                    mCheckinButton.setEnabled(true);
                }
                return;

            } else {
                // Store that we completed this action.
                mStateHolder.checkinResult = checkinResult;

                // Make sure the caller knows things worked out alright.
                setResult(Activity.RESULT_OK);

                // Show the dialog that will dismiss this activity.
                createCheckinResultDialog(checkinResult).show();
            }
        }

        @Override
        public void onCancelled() {
            dismissDialog(DIALOG_CHECKIN_PROGRESS);
            if (mImmediateCheckin) {
                finish();
            } else {
                mCheckinButton.setEnabled(true);
            }
        }
    }

    private static class StateHolder {
        // These are all enumerated because we currently cannot handle parceling venues! How sad!
        Venue venue = null;
        AsyncTask<Void, Void, CheckinResult> checkinTask = null;
        CheckinResult checkinResult = null;
    }

}
