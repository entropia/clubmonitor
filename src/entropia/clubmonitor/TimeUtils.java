package entropia.clubmonitor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TimeUtils {
    private TimeUtils() {}
    public static final ScheduledExecutorService scheduler =
	    Executors.newScheduledThreadPool(2);
    
    public static long timestamp() {
	final long currentTimeMillis = System.currentTimeMillis();
	return TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis);
    }
}
