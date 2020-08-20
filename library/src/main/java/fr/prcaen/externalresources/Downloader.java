package fr.prcaen.externalresources;

import android.content.Context;
import android.content.res.Configuration;
import androidx.annotation.NonNull;
import com.squareup.okhttp.CacheControl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import fr.prcaen.externalresources.converter.Converter;
import fr.prcaen.externalresources.exception.ExternalResourceException;
import fr.prcaen.externalresources.exception.ResponseException;
import fr.prcaen.externalresources.model.Resources;
import fr.prcaen.externalresources.url.Url;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.HONEYCOMB_MR2;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;

public final class Downloader {
  private static final int CONNECT_TIMEOUT_MILLIS = 15 * 1000; // 15s
  private static final int READ_TIMEOUT_MILLIS = 20 * 1000;    // 20s
  private static final int WRITE_TIMEOUT_MILLIS = 20 * 1000;   // 20s

  @NonNull private final Context context;
  @NonNull private final OkHttpClient client;
  @NonNull private final Converter converter;
  @NonNull private final Url url;
  @NonNull private final Options options;
  @NonNull private final Cache cache;
  private boolean mustClearCache = false;

  public Downloader(@NonNull Context context, @NonNull Converter converter, @NonNull Url url,
      @NonNull Options options) {
    this(context, new OkHttpClient(), converter, url, options);
  }

  public Downloader(@NonNull Context context, @NonNull OkHttpClient client,
      @NonNull Converter converter, @NonNull Url url, @NonNull Options options) {
    this.context = context.getApplicationContext();
    this.client = client;
    this.url = url;
    this.options = options;
    this.converter = converter;
    this.cache = new Cache(context.getApplicationContext());

    client.setConnectTimeout(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    client.setReadTimeout(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    client.setWriteTimeout(WRITE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    client.setCache(new com.squareup.okhttp.Cache(this.cache.getCacheDir(), this.cache.getCacheSize()));
  }

  public void clearCache(){
    this.mustClearCache = true;
  }

  public Resources load(@Cache.Policy int policy) throws ExternalResourceException {
    buildUrl();

    Logger.i(ExternalResources.TAG, "Load configuration from url: " + url.build());

    final CacheControl cacheControl;

    if(this.mustClearCache)
    {
      CacheControl.Builder  ccb = new CacheControl.Builder().noCache();

      // NOTE : We don't handle POLICY_NONE as there should not be any cache anyway

      // NOTE : Both these policies need maxStale to max
      if(policy == Cache.POLICY_DEFAULT || policy == Cache.POLICY_OFFLINE){
        ccb = ccb.maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS);
      }
      cacheControl = ccb.build();
      mustClearCache = false;
    }
    else
    {
      switch (policy) {
        case Cache.POLICY_NONE:
          cacheControl = new CacheControl.Builder().noCache().noStore().build();
          break;
        case Cache.POLICY_OFFLINE:
          cacheControl = CacheControl.FORCE_CACHE;
          break;
        case Cache.POLICY_DEFAULT:
          cacheControl =  new CacheControl.Builder().maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS).build();
          break;
        case Cache.POLICY_ALL:
        default:
          cacheControl = new CacheControl.Builder().build();
          break;
      }
    }

    Logger.v(ExternalResources.TAG, "CachePolicy: " + policy);

    Request request = new Request.Builder().url(url.build()).cacheControl(cacheControl).build();

    try {
      Response response = client.newCall(request).execute();
      int responseCode = response.code();

      Logger.d(ExternalResources.TAG, "Response code: " + responseCode);
      if (responseCode >= 300) {
        response.body().close();
        throw new ResponseException(responseCode + " " + response.message(), policy, responseCode);
      }

      return converter.fromReader(response.body().charStream());
    } catch (IOException e) {
      throw new ExternalResourceException(e);
    }
  }

  protected void buildUrl() {
    Configuration configuration = context.getResources().getConfiguration();

    if (options.isUseFontScale()) {
      url.fontScale(configuration.fontScale);
    }

    if (options.isUseHardKeyboardHidden()) {
      url.hardKeyboardHidden(configuration.hardKeyboardHidden);
    }

    if (options.isUseKeyboard()) {
      url.keyboard(configuration.keyboard);
    }

    if (options.isUseKeyboardHidden()) {
      url.keyboardHidden(configuration.keyboardHidden);
    }

    if (options.isUseLocale()) {
      url.locale(configuration.locale);
    }

    if (options.isUseMcc()) {
      url.mcc(configuration.mcc);
    }

    if (options.isUseMnc()) {
      url.mnc(configuration.mnc);
    }

    if (options.isUseNavigation()) {
      url.navigation(configuration.navigation);
    }

    if (options.isUseNavigationHidden()) {
      url.navigationHidden(configuration.navigationHidden);
    }

    if (options.isUseOrientation()) {
      url.orientation(configuration.orientation);
    }

    if (options.isUseScreenLayout()) {
      url.screenLayout(configuration.screenLayout);
    }

    if (options.isUseTouchscreen()) {
      url.touchscreen(configuration.touchscreen);
    }

    if (options.isUseUiMode()) {
      url.uiMode(configuration.uiMode);
    }

    if (options.isUseDensityDpi() && SDK_INT >= JELLY_BEAN_MR1) {
      url.densityDpi(configuration.densityDpi);
    }

    if (SDK_INT >= HONEYCOMB_MR2) {
      if (options.isUseScreenWidthDp()) {
        url.screenWidthDp(configuration.screenWidthDp);
      }

      if (options.isUseScreenHeightDp()) {
        url.screenHeightDp(configuration.screenHeightDp);
      }

      if (options.isUseSmallestScreenWidthDp()) {
        url.smallestScreenWidthDp(configuration.smallestScreenWidthDp);
      }
    }
  }
}
