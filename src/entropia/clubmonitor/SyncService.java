package entropia.clubmonitor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;


public class SyncService {
    private static enum SyncElement { SELEM; }
    private final ArrayBlockingQueue<SyncElement> blockQ =
            new ArrayBlockingQueue<SyncElement>(1, false);
    
    void sleepUntilEvent(long sleepTime)
            throws InterruptedException {
        blockQ.poll(sleepTime, TimeUnit.MILLISECONDS);
    }
    
    void forceUpdate() {
        try {
            blockQ.offer(SyncElement.SELEM, 0, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
