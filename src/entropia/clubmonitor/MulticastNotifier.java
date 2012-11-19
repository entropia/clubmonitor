package entropia.clubmonitor;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

final class MulticastNotifier extends PublicOnlyTrigger implements Runnable {
    private static Logger logger =
	    LoggerFactory.getLogger(MulticastNotifier.class);
    
    private final MulticastSocket socket;
    private static final InetSocketAddress address = 
    	Config.getMulticastAddress();
    
    public MulticastNotifier() {
	socket = initSocket();
	if (socket != null) {
	    initTimer();
	    logger.info("started");
	}
    }

    private static MulticastSocket initSocket() {
	try {
	    MulticastSocket socket = new MulticastSocket();
	    socket.setTimeToLive(Config.getMulticastTTL());
	    return socket;
	} catch (IOException e) {
	    logger.warn("multicast socket initialization", e);
	    return null;
	}
    }

    
    private void initTimer() {
	final int resendSeconds = Config.getMulticastResendSeconds();
	TimeUtils.scheduler.scheduleAtFixedRate(this, resendSeconds,
	        resendSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void trigger(
            TernaryStatusRegister register) {
        TimeUtils.scheduler.schedule(this, 0, TimeUnit.NANOSECONDS);
    }

    private static DatagramPacket getPacket() throws SocketException {
	byte[] bytes = StatusServer.json().getBytes(Charsets.US_ASCII);
	return new DatagramPacket(bytes, bytes.length, address);
    }


    private void timedTrigger() throws SocketException, IOException {
	synchronized (MulticastNotifier.class) {
	    if (socket == null) {
		return;
	    }
	    socket.send(getPacket());
	}
    }
    
    @Override
    public void run() {
	try {
	    timedTrigger();
	} catch (final Exception e) {
	    logger.warn("trigger caused exception", e);
	}
    }
}
