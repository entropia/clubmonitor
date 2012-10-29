package entropia.clubmonitor;

public enum ADCRegister {
    Temperature;
    
    private long value;
    
    private static final Object lock = new Object();
    
    public void set(long value) {
	synchronized (lock) {
	    this.value = value;
	}
    }
    
    public long get() {
	synchronized (lock) {
	    return this.value;
	}
    }
}
