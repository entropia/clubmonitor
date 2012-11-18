package entropia.clubmonitor;

public enum ADCRegister {
    Temperature;
    
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
}
