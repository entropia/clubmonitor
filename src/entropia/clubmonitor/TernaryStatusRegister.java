package entropia.clubmonitor;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public enum TernaryStatusRegister {
    CLUB_OFFEN(true),
    FENSTER_OFFEN(true),
    HW_FEHLER(true),
    CARDREADER_KAPUTT(true),
    OVERRIDE_WINDOWS(true),
    
    KEY_DOOR_BUZZER(false),
    ;
    
    private final boolean isPublic;
    
    private TernaryStatusRegister(boolean isPublic) {
        this.isPublic = isPublic;
    }
    
    public boolean isPublic() {
        return isPublic;
    }
    
    public static enum RegisterState {
	UNINITIALIZED,
	HIGH,
	LOW;
    }
    
    private static final Object lock = new Object();

    private long lastChangeTimestampSeconds =
	    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    private RegisterState state = RegisterState.UNINITIALIZED;

    public static long lastEvent() {
	synchronized (lock) {
	    long longest = 0;
	    for (final TernaryStatusRegister r : values()) {
		if (longest < r.lastChangeTimestampSeconds) {
		    longest = r.lastChangeTimestampSeconds;
		}
	    }
	    return longest;
	}
    }

    public Map<String,Object> jsonStatusMap() {
	synchronized (lock) {
	    final Map<String,Object> status = new TreeMap<>();
	    final boolean boolStatus = (status() == RegisterState.HIGH)
		    ? true : false; 
	    status.put("lastChange", lastChangeTimestampSeconds);
	    status.put("status", boolStatus);
	    return Collections.unmodifiableMap(status);
	}
    }
    
    public void off() {
	synchronized (lock) {
	    switch (state) {
	    case HIGH:
	    case UNINITIALIZED:
		state = RegisterState.LOW;
		trigger(this);
		return;
	    case LOW:
		return;
	    default:
	        throw new IllegalStateException();
	    }
	}
    }
    public void on() {
	synchronized (lock) {
	    switch (state) {
	    case LOW:
	    case UNINITIALIZED:
		state = RegisterState.HIGH;
		trigger(this);
		return;
	    case HIGH:
	        return;
	    default:
	        throw new IllegalStateException();
	    }
	}
    }

    public RegisterState status() {
	synchronized (lock) {
	    return state;
	}
    }

    private void trigger(TernaryStatusRegister register) {
	lastChangeTimestampSeconds = TimeUtils.timestamp();
	Trigger.call(register);
    }
}
