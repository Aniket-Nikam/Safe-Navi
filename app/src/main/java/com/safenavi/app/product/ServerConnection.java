package com.safenavi.app.product;

import android.content.Context;
import com.safenavi.app.BuildConfig;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class ServerConnection {
    public static final String CLOUD_ORIGIN = "https://safe-navi-api.onrender.com";
    private static volatile String address=BuildConfig.RISK_API_BASE_URL;
    private ServerConnection() { }
    public static void initialize(Context context) {
        String saved=context.getSharedPreferences("safe_navi_server",0).getString("address",BuildConfig.RISK_API_BASE_URL);
        try { address=normalize(saved); } catch(IllegalArgumentException error) { address=BuildConfig.RISK_API_BASE_URL; }
    }
    public static String baseUrl() { return address; }
    public static boolean requiresCleanQueue(String current, String next) {
        return !current.equals(next) && (current.startsWith("https://") || next.startsWith("https://"));
    }
    public static void save(Context context,String value) {
        address=normalize(value);
        context.getSharedPreferences("safe_navi_server",0).edit().putString("address",address).apply();
    }
    public static String normalize(String value) {
        if(value==null) throw new IllegalArgumentException("Enter a server address.");
        String raw=value.trim(); if(!raw.contains("://")) raw="http://"+raw;
        // Application startup must not initialise OkHttp/Okio merely to validate an origin.
        final URI url;
        try { url=new URI(raw); } catch(URISyntaxException error) {
            throw new IllegalArgumentException("Enter a valid HTTP or HTTPS server address.");
        }
        String scheme=url.getScheme()==null?"":url.getScheme().toLowerCase(Locale.ROOT);
        String host=url.getHost(); int port=url.getPort();
        if(host==null || (!scheme.equals("https") && !scheme.equals("http"))
                || url.getRawUserInfo()!=null || url.getRawQuery()!=null || url.getRawFragment()!=null
                || port==0 || port>65535)
            throw new IllegalArgumentException("Enter the laptop address without credentials or query parameters.");
        host=host.toLowerCase(Locale.ROOT);
        String authority=host+(port<0?"":":"+port);
        if(!authority.equalsIgnoreCase(url.getRawAuthority())) throw new IllegalArgumentException("Enter a valid server host and port.");
        String path=url.getRawPath();
        if(path!=null && !path.isEmpty() && !path.equals("/") && !path.equals("/health")) throw new IllegalArgumentException("Use the server origin or its /health link.");
        if(!scheme.equals("https")) {
            boolean local="localhost".equals(host);
            String[] parts=host.split("\\.");
            if(parts.length==4) try {
                int a=Integer.parseInt(parts[0]),b=Integer.parseInt(parts[1]);
                int c=Integer.parseInt(parts[2]),d=Integer.parseInt(parts[3]);
                if(a<0 || a>255 || b<0 || b>255 || c<0 || c>255 || d<0 || d>255) throw new NumberFormatException();
                local|=a==127 || a==10 || (a==172 && b>=16 && b<=31) || (a==192 && b==168);
            } catch(NumberFormatException ignored) { }
            if(!BuildConfig.DEBUG || !local) throw new IllegalArgumentException("HTTP is allowed only for a private-network presentation server. Otherwise use HTTPS.");
        }
        boolean defaultPort=(scheme.equals("https") && port==443) || (scheme.equals("http") && port==80);
        return scheme+"://"+host+(port<0 || defaultPort?"":":"+port);
    }
}
