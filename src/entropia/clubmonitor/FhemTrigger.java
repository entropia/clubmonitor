package entropia.clubmonitor;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
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

    private static final URL FHEM_CMD_URL = Config.getFhemCmdURL();
    
    private static String getCmdDesiredTemp(final String temp) {
        return String.format("set FHT_402e desired-temp %s", temp);
    }
    
    private static Map<String,String> createCmdMap(final String cmd) {
        final Map<String,String> map = new HashMap<String,String>();
        map.put("XHR", "1");
        map.put("cmd", cmd);
        return Collections.unmodifiableMap(map);
    }
    
    @Override
    public void run() {
	try {
	    logger.info("FhemTrigger timer expired");
	    final URL url = new URL(FHEM_CMD_URL, "?cmd=jsonlist FHZ_420e&XHR=1");
	    final HttpURLConnection c = (HttpURLConnection) url.openConnection();
	    try {
		final InputStreamReader in = new InputStreamReader(c.getInputStream());
		final JsonObject o = new JsonParser().parse(in).getAsJsonObject();
		if (TernaryStatusRegister.CLUB_OFFEN.status() == RegisterState.HIGH) {
		    final String cmd = getCmdDesiredTemp("22.0");
		    final Map<String, String> map = createCmdMap(cmd);
		    WebClient.post(FHEM_CMD_URL, map);
		} else if (TernaryStatusRegister.CLUB_OFFEN.status() == RegisterState.LOW) {
		    final String cmd = getCmdDesiredTemp("18.0");
		    final Map<String, String> map = createCmdMap(cmd);
		    logger.info(cmd);
		    WebClient.post(FHEM_CMD_URL, map);
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
    
    private static JsonElement walkJson(JsonObject o, String... path) {
        JsonElement _o = o;
        for (final String k : path) {
            _o = _o.getAsJsonObject().get(k);
        }
        return _o;
    }
    
    private static void updateMeasuredTemp(JsonObject o) {
	try {
	    final JsonObject x = walkJson(o, "ResultSet", "Results", "READINGS",
	            "measured-temp").getAsJsonObject();
	    final double temp = x.get("VAL").getAsDouble();
	    ADCRegister.Temperature.set(temp);
	} catch (Exception e) {
	    logger.warn("updateMeausredTemp", e);
	}
    }

    private static void updateDesiredTemp(JsonObject o) {
	try {
	    final JsonObject x = walkJson(o, "ResultSet", "Results",
		    "READINGS", "desired-temp").getAsJsonObject();
	    final double temp = x.get("VAL").getAsDouble();
	    ADCRegister.DesiredTemperature.set(temp);
	} catch (Exception e) {
	    logger.warn("updateDesiredTemp", e);
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
