package entropia.clubmonitor;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;

public class WebClient {

    public static void post(final HttpURLConnection con, final String param)
	    throws IOException {
	con.setDoOutput(true);
	con.setDoInput(true);
	con.setInstanceFollowRedirects(false);
	con.setRequestMethod("POST");
	con.setUseCaches(false);
	con.getOutputStream().write(param.getBytes(Charsets.UTF_8));
	final int responseCode = con.getResponseCode();
	if (responseCode != 200) {
	    final String responseMessage = con.getResponseMessage();
	    if (responseMessage != null) {
		throw new IOException(responseMessage);
	    } else {
		throw new IOException("response code: " + responseCode);
	    }
	}
    }
    
    public static void post(final HttpURLConnection con, final Map<String,String> params)
	    throws IOException {
	final List<String> l = new ArrayList<String>(params.size());
	for (final Map.Entry<String, String> e : params.entrySet()) {
	    final String k = URLEncoder.encode(e.getKey(), "UTF-8");
	    final String v = URLEncoder.encode(e.getValue(), "UTF-8");
	    l.add(k + "=" + v);
	}
	final String param = Joiner.on("&").join(l);	
	post(con, param);
    }
    
    public static void post(final URL url, final Map<String,String> params)
	    throws IOException {
	final HttpURLConnection con = (HttpURLConnection) url.openConnection();
	try {
	    post(con, params);
	} finally {
	    con.disconnect();
	}
    }
    
    public static void post(final URL url, final String param)
	    throws IOException {
	final HttpURLConnection con = (HttpURLConnection) url.openConnection();
	try {
	    post(con, param);
	} finally {
	    con.disconnect();
	}
    }
    
}
