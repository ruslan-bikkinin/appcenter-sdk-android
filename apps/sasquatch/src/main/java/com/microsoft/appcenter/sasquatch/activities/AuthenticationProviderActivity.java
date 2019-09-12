/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter.sasquatch.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.microsoft.appcenter.analytics.AuthenticationProvider;
import com.microsoft.appcenter.auth.Auth;
import com.microsoft.appcenter.auth.SignInResult;
import com.microsoft.appcenter.auth.UserInformation;
import com.microsoft.appcenter.sasquatch.R;
import com.microsoft.appcenter.sasquatch.features.TestFeatures;
import com.microsoft.appcenter.sasquatch.features.TestFeaturesListAdapter;
import com.microsoft.appcenter.utils.async.AppCenterConsumer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.microsoft.appcenter.sasquatch.SasquatchConstants.ACCOUNT_ID;
import static com.microsoft.appcenter.sasquatch.SasquatchConstants.USER_INFORMATION_ACCESS_TOKEN;
import static com.microsoft.appcenter.sasquatch.SasquatchConstants.USER_INFORMATION_ID;
import static com.microsoft.appcenter.sasquatch.SasquatchConstants.USER_INFORMATION_ID_TOKEN;
import static com.microsoft.appcenter.sasquatch.activities.MainActivity.LOG_TAG;

public class AuthenticationProviderActivity extends AppCompatActivity {

    private static final int FIREBASE_ACTIVITY_RESULT_CODE = 123;

    private boolean mUserLeaving;

    private static UserInformation sUserInformation;

    private static FirebaseUser sFirebaseUser;

    private static String sFirebaseIdToken;

    private TestFeatures.TestFeature mAuthInfoTestFeature;

    private List<TestFeatures.TestFeatureModel> mFeatureList;

    private ListView mListView;

    private static boolean isAuthenticated() {
        switch (MainActivity.sAuthType) {
            case FIREBASE:
                return sFirebaseUser != null;

            case AAD:
            case B2C:
            default:
                if (sUserInformation == null) {
                    return false;
                }
                return sUserInformation.getAccessToken() != null;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_provider_list);

        /* Populate UI. */
        mFeatureList = new ArrayList<>();
        mFeatureList.add(new TestFeatures.TestFeatureTitle(R.string.msa_title));
        mFeatureList.add(new TestFeatures.TestFeature(R.string.msa_compact_title, R.string.msa_compact_description, new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startMSALoginActivity(AuthenticationProvider.Type.MSA_COMPACT);
            }
        }));
        mFeatureList.add(new TestFeatures.TestFeature(R.string.msa_delegate_title, R.string.msa_delegate_description, new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startMSALoginActivity(AuthenticationProvider.Type.MSA_DELEGATE);
            }
        }));
        mFeatureList.add(new TestFeatures.TestFeatureTitle(R.string.auth_title));
        mFeatureList.add(new TestFeatures.TestFeature(R.string.sign_in_title, R.string.sign_in_description, new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                switch (MainActivity.sAuthType) {
                    case FIREBASE:
                        List<AuthUI.IdpConfig> providers = Arrays.asList(
                                new AuthUI.IdpConfig.EmailBuilder().build(),
                                new AuthUI.IdpConfig.GoogleBuilder().build());
                        startActivityForResult(
                                AuthUI.getInstance()
                                        .createSignInIntentBuilder()
                                        .setAvailableProviders(providers)
                                        .build(),
                                FIREBASE_ACTIVITY_RESULT_CODE);
                        break;
                    case B2C:
                    case AAD:
                    default:
                        Auth.signIn().thenAccept(new AppCenterConsumer<SignInResult>() {

                            @Override
                            public void accept(SignInResult signInResult) {
                                try {
                                    Exception exception = signInResult.getException();
                                    if (exception != null) {
                                        throw exception;
                                    }
                                    sUserInformation = signInResult.getUserInformation();
                                    loadAuthStatus(false);
                                    String accountId = sUserInformation.getAccountId();
                                    SharedPreferences.Editor edit = MainActivity.sSharedPreferences.edit();
                                    edit.putString("accountId", accountId);
                                    edit.apply();
                                    Log.i(LOG_TAG, "Auth.signIn succeeded, accountId=" + accountId);
                                } catch (Exception e) {
                                    sUserInformation = null;
                                    loadAuthStatus(false);
                                    Log.e(LOG_TAG, "Auth.signIn failed", e);
                                }
                            }
                        });
                }
            }
        }));
        mFeatureList.add(new TestFeatures.TestFeature(R.string.sign_out_title, R.string.sign_out_description, new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                switch (MainActivity.sAuthType) {
                    case FIREBASE:
                        FirebaseAuth.getInstance().signOut();
                        unsetFirebaseAuth();
                        break;

                    case B2C:
                    case AAD:
                    default:
                        Auth.signOut();
                        sUserInformation = null;
                        SharedPreferences.Editor edit = MainActivity.sSharedPreferences.edit();
                        edit.putString(ACCOUNT_ID, null);
                        edit.apply();
                }
                loadAuthStatus(false);
            }
        }));
        mListView = findViewById(R.id.list);
        loadAuthStatus(sUserInformation == null && sFirebaseUser == null);
        mListView.setOnItemClickListener(TestFeatures.getOnItemClickListener());
    }

    private void loadAuthStatus(boolean loadDefaultStatus) {
        if (mAuthInfoTestFeature != null) {
            mFeatureList.remove(mAuthInfoTestFeature);
        }
        mAuthInfoTestFeature = getAuthenticationDefaultTestFeature();
        if (!loadDefaultStatus) {
            mAuthInfoTestFeature = isAuthenticated() ? getAuthenticatedTestFeature() : getNotAuthenticatedTestFeature();
        }
        mFeatureList.add(mAuthInfoTestFeature);
        mListView.setAdapter(new TestFeaturesListAdapter(mFeatureList));
    }

    private TestFeatures.TestFeature getAuthenticationDefaultTestFeature() {
        return getAuthenticationTestFeature(R.string.authentication_status_description);
    }

    private TestFeatures.TestFeature getAuthenticatedTestFeature() {
        return getAuthenticationTestFeature(R.string.authentication_status_authenticated);
    }

    private TestFeatures.TestFeature getNotAuthenticatedTestFeature() {
        return getAuthenticationTestFeature(R.string.authentication_status_not_authenticated);
    }

    private TestFeatures.TestFeature getAuthenticationTestFeature(int valueStringId) {
        return new TestFeatures.TestFeature(R.string.authentication_status_title, valueStringId, new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (isAuthenticated()) {
                    startUserInfoActivity();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(AuthenticationProviderActivity.this);
                    builder.setTitle(R.string.authentication_status_dialog_unavailable_title)
                            .setMessage(R.string.authentication_status_dialog_unavailable_description)
                            .setPositiveButton(R.string.alert_ok, new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                }
                            });
                    builder.create().show();
                }
            }
        });
    }

    private void startMSALoginActivity(AuthenticationProvider.Type type) {
        Intent intent = new Intent(getApplication(), MSALoginActivity.class);
        intent.putExtra(AuthenticationProvider.Type.class.getName(), type);
        startActivity(intent);
    }

    private void startUserInfoActivity() {
        Intent intent = new Intent(getApplication(), UserInformationActivity.class);
        switch (MainActivity.sAuthType) {
            case FIREBASE:
                intent.putExtra(USER_INFORMATION_ID, sFirebaseUser.getUid());
                intent.putExtra(USER_INFORMATION_ID_TOKEN, sFirebaseIdToken);
                break;

            case B2C:
            case AAD:
            default:
                intent.putExtra(USER_INFORMATION_ID, sUserInformation.getAccountId());
                intent.putExtra(USER_INFORMATION_ID_TOKEN, sUserInformation.getIdToken());
                intent.putExtra(USER_INFORMATION_ACCESS_TOKEN, sUserInformation.getAccessToken());
        }
        startActivity(intent);
    }

    @Override
    protected void onUserLeaveHint() {
        mUserLeaving = true;
    }

    @Override
    protected void onRestart() {

        /* When coming back from browser, finish this intermediate menu screen too. */
        super.onRestart();
        if (mUserLeaving) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FIREBASE_ACTIVITY_RESULT_CODE) {
            IdpResponse response = IdpResponse.fromResultIntent(data);
            if (response != null) {
                if (resultCode == RESULT_OK) {
                    Log.i(LOG_TAG, "Firebase login UI ok, getting ID token...");
                    final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null) {
                        Log.e(LOG_TAG, "Failed to get firebase user.");
                        unsetFirebaseAuth();
                    } else {
                        user.getIdToken(true).addOnSuccessListener(new OnSuccessListener<GetTokenResult>() {

                            @Override
                            public void onSuccess(GetTokenResult getTokenResult) {
                                sFirebaseUser = user;
                                sFirebaseIdToken = getTokenResult.getToken();
                                Log.i(LOG_TAG, "Got Firebase token");
                                BYOIUtils.setAuthToken(sFirebaseIdToken);
                                loadAuthStatus(false);
                                String accountId = user.getUid();
                                SharedPreferences.Editor edit = MainActivity.sSharedPreferences.edit();
                                edit.putString("accountId", accountId);
                                edit.apply();
                            }
                        }).addOnFailureListener(new OnFailureListener() {

                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(LOG_TAG, "Failed to get Firebase token", e);
                                unsetFirebaseAuth();
                            }
                        });
                    }
                } else {
                    Log.e(LOG_TAG, "Firebase login failed", response.getError());
                    unsetFirebaseAuth();
                }
            } else {
                Log.w(LOG_TAG, "Firebase login canceled");
                unsetFirebaseAuth();
            }
        }
    }

    private void unsetFirebaseAuth() {
        BYOIUtils.setAuthToken(null);
        sFirebaseUser = null;
        sFirebaseIdToken = null;
        SharedPreferences.Editor edit = MainActivity.sSharedPreferences.edit();
        edit.remove("accountId");
        edit.apply();
        loadAuthStatus(false);
    }
}
