package app.light.arsmoon.savedgame;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.math.BigInteger;
import java.util.Random;

/**
 * Created on 12.12.2017.
 */

public class SavedGameModule {

    private static String TAG = "SavedGameModule";

    private static final int RC_SAVED_GAMES = 9009;
    private static final int RC_SIGN_IN = 9001;
    private String mCurrentSaveName = "snapshotTemp";
    private ProgressDialog mProgressDialog;
    private TextView metaDataView;
    private Activity activity;

    private Snapshot gSnapshot = null;

    private Bitmap sBitmap;

    private static SavedGameModule instance;

    private GetSavedGameValueCallback getSavedGameValueCallback;

    public interface GetSavedGameValueCallback{
        void valueResult(long value);
    }

//    /**
//     * Check if a {@link SavedGameModule} instance is already created and if no instance exist, create one
//     *
//     * @param activity The context
//     * @return a {@link SavedGameModule} instance
//     */
//    public static synchronized SavedGameModule getInstance(Activity activity, GetSavedGameValueCallback getSavedGameValueCallback) {
//        Log.d(TAG, "getInstance()");
//
//        if (instance == null) {
//            instance = new SavedGameModule(activity, getSavedGameValueCallback);
//        }
//
//        return instance;
//    }

    public SavedGameModule(Activity activity, GetSavedGameValueCallback getSavedGameValueCallback){
        this.activity = activity;
        this.getSavedGameValueCallback = getSavedGameValueCallback;
    }

    public SavedGameModule(Activity activity, ProgressDialog progressDialog, GetSavedGameValueCallback getSavedGameValueCallback){
        this.activity = activity;
        this.mProgressDialog = progressDialog;
        this.getSavedGameValueCallback = getSavedGameValueCallback;
    }

    public void setFirstSnapshootName(String snapshootName){
        mCurrentSaveName = snapshootName;
    }

    public void setTextViewMetaData(TextView metaDataView){
        this.metaDataView = metaDataView;
    }

    public void setBitmapSavedGame(Bitmap sBitmap){
        this.sBitmap = sBitmap;
    }

    public void signInSilently() {
        GoogleSignInOptions signInOption =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
                        // Add the APPFOLDER scope for Snapshot support.
                        .requestScopes(Drive.SCOPE_APPFOLDER)
                        .build();

        GoogleSignInClient signInClient = GoogleSignIn.getClient(activity, signInOption);
        signInClient.silentSignIn().addOnCompleteListener(activity,
                new OnCompleteListener<GoogleSignInAccount>() {
                    @Override
                    public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                        if (task.isSuccessful()) {
                            GoogleSignInAccount signedInAccount = task.getResult();
                            Log.d(TAG, "onConnected(task.getResult());");
                        } else {
                            // Player will need to sign-in explicitly using via UI
                            startSignInIntent();
                            Log.d(TAG, "Player will need to sign-in explicitly using via UI");
                        }
                    }
                });
    }

    private void startSignInIntent() {
        GoogleSignInClient signInClient = GoogleSignIn.getClient(activity,
                GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
        Intent intent = signInClient.getSignInIntent();
        activity.startActivityForResult(intent, RC_SIGN_IN);
    }

    public void showSavedGamesUI() {
        try {
            SnapshotsClient snapshotsClient =
                    Games.getSnapshotsClient(activity, GoogleSignIn.getLastSignedInAccount(activity));
            int maxNumberOfSavedGamesToShow = 5;

            Task<Intent> intentTask = snapshotsClient.getSelectSnapshotIntent(
                    "See My Saves", true, true, maxNumberOfSavedGamesToShow);

            intentTask.addOnSuccessListener(new OnSuccessListener<Intent>() {
                @Override
                public void onSuccess(Intent intent) {
                    activity.startActivityForResult(intent, RC_SAVED_GAMES);
                }
            });
        }catch (Exception e){
            e.printStackTrace();
            new AlertDialog.Builder(activity).setMessage(activity.getString(R.string.saved_games_select_failure))
                    .setNeutralButton(android.R.string.ok, null).show();
        }
    }

    public Task<SnapshotMetadata> writeSnapshot(byte[] data, Bitmap coverImage, String desc) {

        // Set the data payload for the snapshot
        gSnapshot.getSnapshotContents().writeBytes(data);

        // Create the change operation
        SnapshotMetadataChange metadataChange = new SnapshotMetadataChange.Builder()
                .setCoverImage(coverImage)
                .setDescription(desc)
                .setProgressValue(100)
                .build();

        SnapshotsClient snapshotsClient =
                Games.getSnapshotsClient(activity, GoogleSignIn.getLastSignedInAccount(activity));

        // Commit the operation
        return snapshotsClient.commitAndClose(gSnapshot, metadataChange);
    }

    public Task<SnapshotMetadata> writeSnapshotProgressValue(long progressValue) {

        // Create the change operation
        SnapshotMetadataChange metadataChange = new SnapshotMetadataChange.Builder()
                .setProgressValue(progressValue)
                .build();

        SnapshotsClient snapshotsClient =
                Games.getSnapshotsClient(activity, GoogleSignIn.getLastSignedInAccount(activity));

        // Commit the operation
        return snapshotsClient.commitAndClose(gSnapshot, metadataChange);
    }

    public Task<byte[]> writeSnapshotGameProgress(final long progressValue) {
        // Display a progress dialog
        showProgressDialog("Saving progress");

        try {
            // Get the SnapshotsClient from the signed in account.
            SnapshotsClient snapshotsClient =
                    Games.getSnapshotsClient(activity, GoogleSignIn.getLastSignedInAccount(activity));

            // In the case of a conflict, the most recently modified version of this snapshot will be used.
            int conflictResolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED;

            // Open the saved game using its name.
            return snapshotsClient.open(mCurrentSaveName, true, conflictResolutionPolicy)
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Error while opening Snapshot.", e);
                        }
                    }).continueWith(new Continuation<SnapshotsClient.DataOrConflict<Snapshot>, byte[]>() {
                        @Override
                        public byte[] then(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> task) throws Exception {
                            Snapshot snapshot = task.getResult().getData();
                            gSnapshot = snapshot;

                            // Opening the snapshot was a success and any conflicts have been resolved.
                            try {
                                // Extract the raw data from the snapshot.
                                if (snapshot != null) {
                                    Log.d(TAG, "snapshot.getMetadata() = "+snapshot.getMetadata());
                                    writeSnapshotProgressValue(progressValue);
                                }
                                return snapshot.getSnapshotContents().readFully();
                            } catch (Exception e) {
                                Log.e(TAG, "Error while reading Snapshot.", e);
                            }

                            return null;
                        }
                    }).addOnCompleteListener(new OnCompleteListener<byte[]>() {
                        @Override
                        public void onComplete(@NonNull Task<byte[]> task) {
                            // Dismiss progress dialog and reflect the changes in the UI when complete.
                            dismissProgressDialog();
                        }
                    });
        }catch (Exception e){
            dismissProgressDialog();
            e.printStackTrace();
            new AlertDialog.Builder(activity).setMessage(activity.getString(R.string.saved_games_update_failure))
                    .setNeutralButton(android.R.string.ok, null).show();

            return null;
        }

    }


    public Task<long[]> loadSnapshot() {
        // Display a progress dialog
        showProgressDialog("Load saved progress");

        try {

            // Get the SnapshotsClient from the signed in account.
            SnapshotsClient snapshotsClient =
                    Games.getSnapshotsClient(activity, GoogleSignIn.getLastSignedInAccount(activity));

            // In the case of a conflict, the most recently modified version of this snapshot will be used.
            int conflictResolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED;

            // Open the saved game using its name.
            return snapshotsClient.open(mCurrentSaveName, true, conflictResolutionPolicy)
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Error while opening Snapshot.", e);
                        }
                    }).continueWith(new Continuation<SnapshotsClient.DataOrConflict<Snapshot>, long[]>() {
                        @Override
                        public long[] then(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> task) throws Exception {
                            Snapshot snapshot = task.getResult().getData();
                            gSnapshot = snapshot;

                            // Opening the snapshot was a success and any conflicts have been resolved.
                            try {
                                // Extract the raw data from the snapshot.
                                if (snapshot != null) {
                                    displaySnapshotMetadata(snapshot.getMetadata());
                                    Log.d(TAG, "snapshot.getMetadata() = "+snapshot.getMetadata());
                                }
                                getSavedGameValueCallback.valueResult(snapshot.getMetadata().getProgressValue());
                                return new long[]{snapshot.getMetadata().getProgressValue()};
                            } catch (Exception e) {
                                Log.e(TAG, "Error while reading Snapshot.", e);
                                getSavedGameValueCallback.valueResult(-1);
                            }

                            getSavedGameValueCallback.valueResult(-1);
                            return new long[]{-1};
                        }
                    }).addOnCompleteListener(new OnCompleteListener<long[]>() {
                        @Override
                        public void onComplete(@NonNull Task<long[]> task) {
                            // Dismiss progress dialog and reflect the changes in the UI when complete.
                            dismissProgressDialog();
                        }
                    });

        }catch (Exception e){
            dismissProgressDialog();
            e.printStackTrace();
            new AlertDialog.Builder(activity).setMessage(activity.getString(R.string.saved_games_load_failure))
                    .setNeutralButton(android.R.string.ok, null).show();

            return null;
        }
    }

    /**
     * Display metadata about Snapshot save data.
     * @param metadata the SnapshotMetadata associated with the saved game.
     */
    public void displaySnapshotMetadata(SnapshotMetadata metadata) {

        try{
            if (metadata == null) {
                if(metaDataView != null){
                    metaDataView.setText("");
                }
                return;
            }

            String metadataStr = "Source: Saved Games" + '\n'
                    + "Description: " + metadata.getDescription() + '\n'
                    + "Name: " + metadata.getUniqueName() + '\n'
                    + "Last Modified: " + String.valueOf(metadata.getLastModifiedTimestamp()) + '\n'
                    + "Played Time: " + String.valueOf(metadata.getPlayedTime()) + '\n'
                    + "Game progress value: " + metadata.getProgressValue();

            if(metaDataView != null){
                metaDataView.setText(metadataStr);
            }
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    public void onActivityResultSavedGameHandler(int requestCode, int resultCode, Intent intent){
        if (intent != null) {
            if (intent.hasExtra(SnapshotsClient.EXTRA_SNAPSHOT_METADATA)) {
                // Load a snapshot.
                Log.d(TAG, "Load a snapshot.");
                SnapshotMetadata snapshotMetadata =
                        intent.getParcelableExtra(SnapshotsClient.EXTRA_SNAPSHOT_METADATA);
                setFirstSnapshootName(snapshotMetadata.getUniqueName());

                // Load the game data from the Snapshot
                // ...
                Log.d(TAG, "Load the game data from the Snapshot");
            } else if (intent.hasExtra(SnapshotsClient.EXTRA_SNAPSHOT_NEW)) {
                // Create a new snapshot named with a unique string
                String unique = new BigInteger(281, new Random()).toString(13);
                setFirstSnapshootName("snapshotTemp-" + unique);

                // Create the new snapshot
                // ...
                Log.d(TAG, "Create the new snapshot");
            }

            if (requestCode == getRC_SIGN()) {
                GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(intent);
                if (result.isSuccess()) {
                    // The signed in account is stored in the result.
                    GoogleSignInAccount signedInAccount = result.getSignInAccount();
                } else {
                    String message = result.getStatus().getStatusMessage();
                    if (message == null || message.isEmpty()) {
                        message = activity.getString(R.string.signin_other_error);
                    }
                    new AlertDialog.Builder(activity).setMessage(message)
                            .setNeutralButton(android.R.string.ok, null).show();
                }
            }
        }
    }

    /**
     * Determine if the Google API Client is signed in and ready to access Games APIs.
     * @return true if client exits and is signed in, false otherwise.
     */
    public boolean isSignedIn() {
        return GoogleSignIn.getLastSignedInAccount(activity) != null;
    }

    public boolean isNetworkAvailable(){

        boolean isConnected = false;

        try {
            ConnectivityManager cm =
                    (ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            isConnected = activeNetwork != null &&
                    activeNetwork.isConnectedOrConnecting();
        }catch (Exception e){
            e.printStackTrace();
        }

        return isConnected;
    }

    public int getRC_SIGN(){
        return RC_SIGN_IN;
    }

    /**
     * Show a progress dialog for asynchronous operations.
     * @param msg the message to display.
     */
    public void showProgressDialog(String msg) {
        try{
            if (mProgressDialog == null) {
                mProgressDialog = new ProgressDialog(activity.getApplication());
                mProgressDialog.setIndeterminate(true);
            }

            mProgressDialog.setMessage(msg);

            if(activity != null && !activity.isFinishing()) {
                mProgressDialog.show();
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    /**
     * Hide the progress dialog, if it was showing.
     */
    public void dismissProgressDialog() {
        try {
            if (mProgressDialog != null && mProgressDialog.isShowing()
                    && activity != null && !activity.isFinishing()) {
                mProgressDialog.dismiss();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
