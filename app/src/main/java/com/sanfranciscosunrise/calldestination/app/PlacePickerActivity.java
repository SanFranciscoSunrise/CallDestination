package com.sanfranciscosunrise.calldestination.app;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.SearchEvent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Date;
import java.util.UUID;

//import android.util.Log;


/**
 * Created by Blu-J on 5/29/17.
 *
 * PlacePickerActivity is the primary activity responsible for launching the
 * Google Places API PlaceAutocomplete floating places search bar.  It binds to our OverShowingService
 * so that we can have a little back and forth between out BAAS (Buttons as a service!) which is just
 * our overlay button.
 */

public class PlacePickerActivity extends FragmentActivity implements GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "PlacePickerActivity";

    private static final int REQUEST_ERROR = 0;
    private static final int PLACE_AUTOCOMPLETE_REQUEST_CODE = 1;

    private static UUID mUUID;
    private DatabaseReference mRef;

    private static OverlayShowingService mService;
    private static boolean mBound = false;
    private static ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //Log.i(TAG, "onServiceConnected");
            OverlayShowingService.LocalBinder binder = (OverlayShowingService.LocalBinder)service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    //private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUUID = QueryPreferences.getPrefUUID(getApplicationContext());
        if (mUUID != (new UUID(0,0))) {
                mRef = FirebaseDatabase.getInstance().getReference("place_lookup/" + mUUID.toString());
        }

        Intent serviceBindingIntent = new Intent(this, OverlayShowingService.class);
        bindService(serviceBindingIntent, mConnection, Context.BIND_AUTO_CREATE);

        //mGoogleApiClient = new GoogleApiClient.Builder(this)
        //        .addApi(Places.GEO_DATA_API)
        //        .addApi(Places.PLACE_DETECTION_API)
        //        .enableAutoManage(this, this)
        //        .build();

        Bundle b = getIntent().getExtras();
        if (b != null) {
            LatLngBounds bounds = (LatLngBounds)b.get("LOCATION");
            startPlaceAutocomplete(bounds);
        } else {
            startPlaceAutocomplete();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        //mGoogleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();

        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int errorCode = apiAvailability.isGooglePlayServicesAvailable(this);

        if (errorCode != ConnectionResult.SUCCESS) {
            Dialog errorDialog = apiAvailability
                    .getErrorDialog(this, errorCode, REQUEST_ERROR,
                            new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    // Leave if services are unavailable
                                    finish();
                                }
                            });
            errorDialog.show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {

                Place place = PlaceAutocomplete.getPlace(this, data);
                String address = place.getAddress().toString();

                Uri navigateToDestinationUri = Uri.parse("google.navigation:q=" + address);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, navigateToDestinationUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(mapIntent);

                if (mRef != null) {
                    MyPlace myPlace = new MyPlace();
                    myPlace.setName(place.getName().toString());
                    Answers.getInstance().logSearch(new SearchEvent()
                            .putQuery(myPlace.getName()));
                    myPlace.setAddress(address);
                    myPlace.setTel(place.getPhoneNumber().toString());
                    long currentTime = new Date().getTime();
                    myPlace.setLatLng(place.getLatLng());

                    mRef.child("" + currentTime).setValue(myPlace);
                }

                if (mBound) {
                    // notify OverlayShowingService that we are about to start getting directions
                    // change overlay from search to phone call
                    String phoneNumber = place.getPhoneNumber().toString();
                    mService.setDestinationPhoneNumber(phoneNumber);
                    mService.setModeSearch(false);
                }

            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                // Handle the error
                //Log.i(TAG, status.getStatusMessage());
            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation
            }
        }
        finish();
    }

    @Override
    public void onStop() {
        super.onStop();
        //mGoogleApiClient.disconnect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        //mGoogleApiClient = null;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private void startPlaceAutocomplete(LatLngBounds bounds) {
        try {
            Intent placePickingIntent =
                    new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY)
                            .setBoundsBias(bounds)
                            .build(this);
            placePickingIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivityForResult(placePickingIntent, PLACE_AUTOCOMPLETE_REQUEST_CODE);
        } catch (GooglePlayServicesRepairableException e) {
            //Log.e(TAG, "ERROR: " + e);
            // Handle the error
        } catch (GooglePlayServicesNotAvailableException e) {
            //Log.e(TAG, "ERROR: " + e);
            // Handle the error
        }
    }

    private void startPlaceAutocomplete() {
        try {
            Intent placePickingIntent =
                    new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY)
                            .build(this);
            placePickingIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivityForResult(placePickingIntent, PLACE_AUTOCOMPLETE_REQUEST_CODE);
        } catch (GooglePlayServicesRepairableException e) {
            //Log.e(TAG, "ERROR: " + e);
            // Handle the error
        } catch (GooglePlayServicesNotAvailableException e) {
            //Log.e(TAG, "ERROR: " + e);
            // Handle the error
        }
    }

}
