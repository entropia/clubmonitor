package entropia.clubmonitor;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public enum ADCRegister {
    ;
    
    private double value;
    
    private static final Object lock = new Object();
    
    public void set(double value) {
	synchronized (lock) {
	    this.value = value;
	}
    }
    
    public double get() {
	synchronized (lock) {
	    return this.value;
	}
    }
    
    public Map<String, Object> jsonStatusMap() {
	synchronized (lock) {
	    final Map<String, Object> map = new TreeMap<>();
	    map.put("val", value);
	    return Collections.unmodifiableMap(map);
	}
    }
}
