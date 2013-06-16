package entropia.clubmonitor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import entropia.clubmonitor.TernaryStatusRegister.RegisterState;

final class MpdNotifier extends PublicOnlyTrigger implements Runnable {
    private static final Logger logger =
            LoggerFactory.getLogger(MpdNotifier.class);
    
    static enum Status {
        PAUSE("pause 1\n"),
        UNPAUSE("pause 0\n"),
        NEXT("next\n"),
        PREVIOUS("previous\n");
        
        final String s;
        
        private Status(String s) {
            this.s = s;
        }
    }

    private static Socket initSocket() throws IOException {
        return IOUtils.connectLowLatency(Config.getMPDAddress(), true);
    }
    
    private static final LinkedBlockingDeque<Status> queue =
            new LinkedBlockingDeque<>();
    
    private static final Pattern ACK_PATTERN = Pattern.compile("\\AOK\\z");
    
    private static void process(BufferedReader in, BufferedWriter out)
            throws IOException, InterruptedException {
        final Status status = queue.takeFirst();
	try {
	    out.write(status.s);
	    out.flush();
	    final String answer = in.readLine();
	    if (answer == null) {
		throw new IOException("socket closed");
	    } else if (!ACK_PATTERN.matcher(answer).matches()) {
		throw new IOException(
			String.format("mpd error: \"%s\"", answer));
	    }
	} catch (final IOException e) {
	    queue.addFirst(status);
	    throw e;
	}
    }
    
    private static final Pattern HEADER_PATTERN = Pattern.compile("\\AOK MPD .+\\z");

    private static final long RETRY_TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    
    private static final void checkHeader(BufferedReader in) throws IOException {
        final String header = in.readLine();
        if (!HEADER_PATTERN.matcher(header).matches()) {
            throw new IOException(String.format("illegal header: \"%s\"", header));
        }
        logger.info("connected to mpd");
    }
    
    private static final void checkConnection(BufferedReader in, BufferedWriter out)
            throws IOException {
        out.write("ping\n");
        out.flush();
        final String answer = in.readLine();
        if (!"OK".equals(answer)) {
            throw new IOException("mpd does not respond");
        }
        logger.info("connection check successful");
    }
    
    @Override
    public void run() {
	final String name = Thread.currentThread().getName();
        while (!Thread.interrupted()) {
            try {
                final Socket socket = initSocket();
                try {
                    final BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));
                    try {
                        final BufferedWriter out = new BufferedWriter(
                                new OutputStreamWriter(socket.getOutputStream()));
                        try {
                            checkHeader(in);
                            checkConnection(in, out);
                            while (true) {
                                process(in, out);
                            }
                        } finally {
                            out.close();
                        }
                    } finally {
                        in.close();
                    }
                } catch (final IOException e) {
                    logger.warn(String.format("exeption in %s. Sleeping for %d" +
                    		" milliseconds.",
                	    name, RETRY_TIMEOUT), e);
                    Thread.sleep(RETRY_TIMEOUT);
                    logger.warn(name + " is ready again for sending commands.");
                    continue;
                } finally {
                    socket.close();
                }
            } catch (final IOException e) {
                logger.error("exception in " + name, e);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public static void submitEvent(final Status status) {
	logger.info("mpd submitted event: " + status.toString());
	queue.add(status);
    }

    @Override
    public void trigger(TernaryStatusRegister register) {
        if (!Config.isMPDEnabled()) {
            return;
        }
        if (register == TernaryStatusRegister.CLUB_OFFEN
                && register.status() == RegisterState.LOW) {
            logger.info("pause mpd");
            queue.add(Status.PAUSE);
            return;
        }
        if (register == TernaryStatusRegister.CLUB_OFFEN
                && register.status() == RegisterState.HIGH) {
            logger.info("unpause mpd");
            queue.add(Status.UNPAUSE);
            return;
        }
    }

    public static Thread startMpdNotifierThread() {
        final Thread t = new Thread(new MpdNotifier());
        t.setName(MpdNotifier.class.getCanonicalName());
        t.start();
        return t;
    }
}
