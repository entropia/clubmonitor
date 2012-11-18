package entropia.clubmonitor;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import entropia.clubmonitor.TernaryStatusRegister.RegisterState;

public class FhemTrigger extends TimerTask {
    private static final Logger logger = LoggerFactory.getLogger(FhemTrigger.class);
    
    private static final URL FHEM_URL = Config.getFhemURL();

    @Override
    public void run() {
	try {
	    logger.info("FhemTrigger timer expired");
	    final HttpURLConnection c = (HttpURLConnection) FHEM_URL.openConnection();
	    try {
		final InputStreamReader in = new InputStreamReader(c.getInputStream());
		final JsonObject o = new JsonParser().parse(in).getAsJsonObject();
		if (TernaryStatusRegister.CLUB_OFFEN.status() == RegisterState.HIGH) {
		    final String cmd = "set FHT_402e desired-temp 22.0";
		    final Map<String,String> map = new HashMap<String,String>();
		    map.put("XHR", "1");
		    map.put("cmd", cmd);
		    WebClient.post(new URL("http://localhost:8083/fhem"), map);
		} else if (TernaryStatusRegister.CLUB_OFFEN.status() == RegisterState.LOW) {
		    final String cmd = "set FHT_402e desired-temp 18.0";
		    final Map<String,String> map = new HashMap<String,String>();
		    map.put("XHR", "1");
		    map.put("cmd", cmd);
		    logger.info(cmd);
		    WebClient.post(new URL("http://localhost:8083/fhem"), map);
		} else {
		    logger.info("CLUB_OFFEN not initialized");
		}
		updateMeasuredTemp(o);
		updateDesiredTemp(o);
	    } finally {
		c.disconnect();
	    }
	} catch (Throwable e) {
	    logger.warn("FhemTrigger timer", e);
	}
    }

    private static JsonObject xxx(final JsonElement e, final String key) {
	return e.getAsJsonObject().get(key).getAsJsonObject();
    }
    
    private static void updateMeasuredTemp(JsonObject o) {
	try {
	    final JsonObject x = xxx(xxx(xxx(xxx(o, "ResultSet"), "Results"),
		    "READINGS"), "measured-temp");
	    final double temp = x.get("VAL").getAsDouble();
	    ADCRegister.Temperature.set(temp);
	} catch (NullPointerException e) {
	    /* EMPTY: we don't care for now */
	}
    }

    private static void updateDesiredTemp(JsonObject o) {
	try {
	    final JsonObject x = xxx(xxx(xxx(xxx(o, "ResultSet"), "Results"),
		    "READINGS"), "desired-temp");
	    final double temp = x.get("VAL").getAsDouble();
	    ADCRegister.DesiredTemperature.set(temp);
	} catch (NullPointerException e) {
	    /* EMPTY: we don't care for now */
	}
    }
    
    private static final long DELAY = 0;
    private static final long RATE = TimeUnit.MINUTES.toMillis(Config.getFhemSyncMinutes());
    
    public static Thread startFhemTrigger() {
	final Timer timer = new Timer();
	timer.scheduleAtFixedRate(new FhemTrigger(), DELAY, RATE);
	logger.info("FhemTriggerThread started");
	return null;
    }
}
