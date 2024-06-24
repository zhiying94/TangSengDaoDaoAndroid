package com.chat.base.net;

import static java.util.Collections.emptySet;

import android.util.Log;

import com.chat.base.WKBaseApplication;
import com.chat.base.utils.WKNetUtil;
import com.chuckerteam.chucker.api.ChuckerCollector;
import com.chuckerteam.chucker.api.ChuckerInterceptor;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * 2020-07-17 14:55
 */
public class OkHttpUtils {
    private OkHttpUtils() {
    }

    private static class OkHttpUtilsBinder {
        final static OkHttpUtils okHttp = new OkHttpUtils();
    }

    public static OkHttpUtils getInstance() {
        return OkHttpUtilsBinder.okHttp;
    }

    private OkHttpClient sOkHttpClient;
    //缓存天数
    private static final long CACHE_STALE_SEC = 60 * 60 * 24 * 2;

    public OkHttpClient getOkHttpClient() {
        if (sOkHttpClient == null) {
            synchronized (OkHttpUtils.class) {
                HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                logging.setLevel(HttpLoggingInterceptor.Level.BODY);
                Cache cache = new Cache(new File(WKBaseApplication.getInstance().getContext().getCacheDir(), "HttpCache"),
                        1024 * 1024 * 100);
                ChuckerInterceptor chuckerInterceptor =
                        new ChuckerInterceptor.Builder(WKBaseApplication.getInstance().application)
                                .collector(new ChuckerCollector(WKBaseApplication.getInstance().application))
                                .maxContentLength(250000L)
                                .redactHeaders(emptySet())
                                .alwaysReadResponseBody(false)
                                .build();
                if (sOkHttpClient == null) {
                    sOkHttpClient = new OkHttpClient.Builder().cache(cache)
                            .connectTimeout(60, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .writeTimeout(60, TimeUnit.SECONDS)
                            .sslSocketFactory(SSLSocketClient.getSSLSocketFactory(), new X509TrustManager() {
                                @Override
                                public void checkClientTrusted(X509Certificate[] chain, String authType) {

                                }

                                @Override
                                public void checkServerTrusted(X509Certificate[] chain, String authType) {

                                }

                                @Override
                                public X509Certificate[] getAcceptedIssuers() {
                                    return new X509Certificate[0];
                                }
                            })
                            .hostnameVerifier(SSLSocketClient.getHostnameVerifier())
                            .addInterceptor(mRewriteCacheControlInterceptor)
                            .addInterceptor(new CommonRequestParamInterceptor())
                            .addInterceptor(chuckerInterceptor)
                            .addInterceptor(logging)
                            .addNetworkInterceptor(mRewriteCacheControlInterceptor)
                            .build();

                }
            }
        }
        return sOkHttpClient;
    }

    private final Interceptor mRewriteCacheControlInterceptor = chain -> {
        Request request = chain.request();
        if (!WKNetUtil.isNetworkAvailable(WKBaseApplication.getInstance().getContext())) {
            request = request.newBuilder()
                    .cacheControl(CacheControl.FORCE_CACHE)
                    .build();
            Log.e("无网络连接：", "------->");
        }
        Response originalResponse = chain.proceed(request);
        if (WKNetUtil.isNetworkAvailable(WKBaseApplication.getInstance().getContext())) {
            //有网的时候读接口上的@Headers里的配置，你可以在这里进行统一的设置
            String cacheControl = request.cacheControl().toString();
            return originalResponse.newBuilder()
                    .header("Cache-Control", cacheControl)
                    .removeHeader("Pragma")
                    .build();
        } else {
            return originalResponse.newBuilder()
                    .header("Cache-Control", "public, only-if-cached, max-stale=" + CACHE_STALE_SEC)
                    .removeHeader("Pragma")
                    .build();
        }
    };

}
