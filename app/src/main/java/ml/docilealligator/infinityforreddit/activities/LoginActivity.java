package ml.docilealligator.infinityforreddit.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.InflateException;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.r0adkll.slidr.Slidr;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import butterknife.BindView;
import butterknife.ButterKnife;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.asynctasks.ParseAndInsertNewAccountAsyncTask;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.FetchMyInfo;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class LoginActivity extends BaseActivity {

    @BindView(R.id.coordinator_layout_login_activity)
    CoordinatorLayout coordinatorLayout;
    @BindView(R.id.appbar_layout_login_activity)
    AppBarLayout appBarLayout;
    @BindView(R.id.toolbar_login_activity)
    Toolbar toolbar;
    @BindView(R.id.two_fa_infO_text_view_login_activity)
    TextView twoFAInfoTextView;
    @BindView(R.id.webview_login_activity)
    WebView webView;
    @Inject
    @Named("no_oauth")
    Retrofit mRetrofit;
    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    private String authCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicable();

        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_login);
        } catch (InflateException ie) {
            Log.e("LoginActivity", "Failed to inflate LoginActivity: " + ie.getMessage());
            Toast.makeText(LoginActivity.this, R.string.no_system_webview_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ButterKnife.bind(this);

        applyCustomTheme();

        if (mSharedPreferences.getBoolean(SharedPreferencesUtils.SWIPE_RIGHT_TO_GO_BACK, true)) {
            Slidr.attach(this);
        }

        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        webView.getSettings().setJavaScriptEnabled(true);

        Uri baseUri = Uri.parse(APIUtils.OAUTH_URL);
        Uri.Builder uriBuilder = baseUri.buildUpon();
        uriBuilder.appendQueryParameter(APIUtils.CLIENT_ID_KEY, APIUtils.CLIENT_ID);
        uriBuilder.appendQueryParameter(APIUtils.RESPONSE_TYPE_KEY, APIUtils.RESPONSE_TYPE);
        uriBuilder.appendQueryParameter(APIUtils.STATE_KEY, APIUtils.STATE);
        uriBuilder.appendQueryParameter(APIUtils.REDIRECT_URI_KEY, APIUtils.REDIRECT_URI);
        uriBuilder.appendQueryParameter(APIUtils.DURATION_KEY, APIUtils.DURATION);
        uriBuilder.appendQueryParameter(APIUtils.SCOPE_KEY, APIUtils.SCOPE);

        String url = uriBuilder.toString();

        CookieManager.getInstance().removeAllCookies(aBoolean -> {
        });

        webView.loadUrl(url);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains("&code=") || url.contains("?code=")) {
                    Uri uri = Uri.parse(url);
                    String state = uri.getQueryParameter("state");
                    if (state.equals(APIUtils.STATE)) {
                        authCode = uri.getQueryParameter("code");

                        Map<String, String> params = new HashMap<>();
                        params.put(APIUtils.GRANT_TYPE_KEY, "authorization_code");
                        params.put("code", authCode);
                        params.put("redirect_uri", APIUtils.REDIRECT_URI);

                        RedditAPI api = mRetrofit.create(RedditAPI.class);
                        Call<String> accessTokenCall = api.getAccessToken(APIUtils.getHttpBasicAuthHeader(), params);
                        accessTokenCall.enqueue(new Callback<String>() {
                            @Override
                            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                                if (response.isSuccessful()) {
                                    try {
                                        String accountResponse = response.body();
                                        if (accountResponse == null) {
                                            //Handle error
                                            return;
                                        }

                                        JSONObject responseJSON = new JSONObject(accountResponse);
                                        String accessToken = responseJSON.getString(APIUtils.ACCESS_TOKEN_KEY);
                                        String refreshToken = responseJSON.getString(APIUtils.REFRESH_TOKEN_KEY);

                                        FetchMyInfo.fetchAccountInfo(mOauthRetrofit, mRedditDataRoomDatabase,
                                                accessToken, new FetchMyInfo.FetchMyInfoListener() {
                                                    @Override
                                                    public void onFetchMyInfoSuccess(String name, String profileImageUrl, String bannerImageUrl, int karma) {
                                                        new ParseAndInsertNewAccountAsyncTask(name, accessToken, refreshToken, profileImageUrl, bannerImageUrl,
                                                                karma, authCode, mRedditDataRoomDatabase.accountDao(),
                                                                () -> {
                                                                    Intent resultIntent = new Intent();
                                                                    setResult(Activity.RESULT_OK, resultIntent);
                                                                    finish();
                                                                }).execute();
                                                    }

                                                    @Override
                                                    public void onFetchMyInfoFailed(boolean parseFailed) {
                                                        if (parseFailed) {
                                                            Toast.makeText(LoginActivity.this, R.string.parse_user_info_error, Toast.LENGTH_SHORT).show();
                                                        } else {
                                                            Toast.makeText(LoginActivity.this, R.string.cannot_fetch_user_info, Toast.LENGTH_SHORT).show();
                                                        }

                                                        finish();
                                                    }
                                        });
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                        Toast.makeText(LoginActivity.this, R.string.parse_json_response_error, Toast.LENGTH_SHORT).show();
                                        finish();
                                    }
                                } else {
                                    Toast.makeText(LoginActivity.this, R.string.retrieve_token_error, Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                                Toast.makeText(LoginActivity.this, R.string.retrieve_token_error, Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                    } else {
                        Toast.makeText(LoginActivity.this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show();
                        finish();
                    }

                } else if (url.contains("error=access_denied")) {
                    Toast.makeText(LoginActivity.this, R.string.access_denied, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    view.loadUrl(url);
                }

                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
            }
        });
    }

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    protected CustomThemeWrapper getCustomThemeWrapper() {
        return mCustomThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        coordinatorLayout.setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndToolbarTheme(appBarLayout, toolbar);
        twoFAInfoTextView.setTextColor(mCustomThemeWrapper.getPrimaryTextColor());
        Drawable infoDrawable = Utils.getTintedDrawable(this, R.drawable.ic_info_preference_24dp, mCustomThemeWrapper.getPrimaryIconColor());
        twoFAInfoTextView.setCompoundDrawablesWithIntrinsicBounds(infoDrawable, null, null, null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }
}