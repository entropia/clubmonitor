package entropia.clubmonitor;

import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Locale;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClubMonitor {
    static {
	Config.loadDefaults();
    }
    private static final Logger logger = LoggerFactory.getLogger(
            ClubMonitor.class);
    private static Thread xmppThread;
    private static Thread fileHandlerThread;
    private static Thread netioPoller;
    private static Thread mpdThread;
    private static Thread clubBusTriggerThread;
    private static Thread MQTTTriggerThread;
    private static WebServer webServer;
    
    private static final SyncService SYNC_SERVICE = new SyncService();
    
    private static void start() throws Exception {
	Config.startupCheck();
	setupPerThreadExceptionHandler();
	if (Config.isSSLEnabled()) {
	    SSLConfig.setupSSL();
	}
	TernaryStatusRegister.OVERRIDE_WINDOWS.off();
	
	if (Config.isNetIOEnabled()) {
	    netioPoller = HauptRaumNetIOHandler.startNetIOPoller(
	            Null.assertNonNull(SYNC_SERVICE));
	}
	if (Config.isXMPPEnabled()) {
	    xmppThread = XMPPThread.startXMPPThread();
	    fileHandlerThread = FileHandlerThread.startFileHandlerThread();
	}
	if (Config.isMPDEnabled()) {
	    mpdThread = MpdNotifier.startMpdNotifierThread();
	}
	if (Config.isClubBusEnabled()) {
	    clubBusTriggerThread = ClubBusTrigger.startClubBusTrigger();
	}
	if (Config.isMQTTEnabled()) {
	    MQTTTriggerThread = MQTTTrigger.startMQTTTrigger();
	}
	webServer = new WebServer(Null.assertNonNull(SYNC_SERVICE));
	webServer.startWebServer();
    }

    private static void setupPerThreadExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(
                new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@Nullable Thread t,
                    @Nullable Throwable e) {
                logger.error(t != null ? t.toString() : "<unkown thread>",
                        e != null ? e : "<unkown exception>");
            }
        });
    }

    private static void loadConfig(String configPath) throws IOException {
        final File properties = new File(configPath);
	if (!properties.isFile() || !properties.canRead()) {
	    throw new IllegalArgumentException("config file could not be read");
	}
	Config.load(properties);
    }
    
    // currently unused
    public static void stop() {
	if (xmppThread != null) {
	    xmppThread.interrupt();
	}
	if (netioPoller != null) {
	    netioPoller.interrupt();
	}
	if (fileHandlerThread != null) {
	    fileHandlerThread.interrupt();
	}
	if (mpdThread != null) {
	    mpdThread.interrupt();
	}
	if (webServer != null) {
	    webServer.stopWebServer();
	}
	if (clubBusTriggerThread != null) {
	    clubBusTriggerThread.interrupt();
	}
    }
    
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        if (args.length == 0 || args.length > 1) {
            throw new IllegalArgumentException();
        } else if ("--print-template".equals(args[0])) {
	    System.out.println(Config.getConfigTemplate());
	    return;
        } else {
            loadConfig(Null.assertNonNull(args[0]));
	}
	ClubMonitor.start();
    }
}
