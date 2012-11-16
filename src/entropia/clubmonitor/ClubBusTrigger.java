package entropia.clubmonitor;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

import entropia.clubmonitor.TernaryStatusRegister.RegisterState;

public class ClubBusTrigger extends PublicOnlyTrigger implements Runnable {
    private static final Logger logger =
	    LoggerFactory.getLogger(ClubBusTrigger.class);
    
    enum Status {
	POWER_DOWN,
	POWER_UP;
    };
    
    private static final LinkedBlockingDeque<Status> queue =
	    new LinkedBlockingDeque<Status>();

    @Override
    public void run() {
	while (!Thread.interrupted()) {
	    try {
		notifyBus();
	    } catch (IOException e) {
		logger.warn("error contacting club notification bus", e);
	    } catch (InterruptedException e) {
		Thread.currentThread().interrupt();
	    }
	}
    }

    private static void post(final HttpURLConnection con,
	    final String param) throws IOException {
	final byte[] paramRaw = param.getBytes(Charsets.UTF_8);
	con.setDoOutput(true);
	con.setInstanceFollowRedirects(false);
	con.setRequestMethod("POST");
	con.setRequestProperty("Content-Type",
		"application/x-www-form-urlencoded; charset=UTF-8");
	con.setRequestProperty("Content-Length",
		Integer.toString(paramRaw.length));
	con.setUseCaches(false);
	con.getOutputStream().write(paramRaw);
    }
    
    private static final URL CLUB_BUS_TRIGGER_URL = Config.getClubBusURL();	    
    
    private static void notifyBus() throws IOException, InterruptedException {
	final Status poll = queue.take();
	final HttpURLConnection con = (HttpURLConnection) CLUB_BUS_TRIGGER_URL.openConnection();
	try {
	    switch (poll) {
	    case POWER_DOWN:
		post(con, "power=0");
		logger.info("request power down");
		break;
	    case POWER_UP:
		post(con, "power=1");
		logger.info("request power up");
		break;
	    default:
		throw new IllegalStateException();
	    }
	} finally {
	    con.disconnect();
	}
    }

    @Override
    public void trigger(TernaryStatusRegister register) throws IOException {
	if (!Config.isClubBusEnabled()) {
	    return;
	}
	
	if (register == TernaryStatusRegister.CLUB_OFFEN
		&& register.status() == RegisterState.LOW) {
	    logger.info("send power down notifification to clubbus");
	    queue.offer(Status.POWER_DOWN);
	}
	
	if (register == TernaryStatusRegister.CLUB_OFFEN
		&& register.status() == RegisterState.HIGH) {
	    logger.info("send power up notification to clubbus");
	    queue.offer(Status.POWER_UP);
	}
    }
    
    public static Thread startClubBusTrigger() {
	final Thread t = new Thread(new ClubBusTrigger());
	t.setName(ClubBusTrigger.class.getCanonicalName());
	t.start();
	logger.info("ClubBusTriggerThread started");
	return t;
    }
}
