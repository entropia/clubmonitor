package entropia.clubmonitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;
import com.google.common.net.InetAddresses;

public enum Config {
    @IntegerTest
    @Default("8080")
    CLUB_MONITOR_WEBSERVER_TCPPORT,
    
    @Default("false")
    CLUB_MONITOR_NETIO_ENABLE,
    @MaybeNullIfFalse(CLUB_MONITOR_NETIO_ENABLE)
    CLUB_MONITOR_NETIO_IPADDRESS,
    @IntegerTest
    @MaybeNullIfFalse(CLUB_MONITOR_NETIO_ENABLE)
    CLUB_MONITOR_NETIO_TCPPORT,
    
    @Default("false")
    CLUB_MONITOR_MULTICAST_ENABLED,
    @MaybeNullIfFalse(CLUB_MONITOR_MULTICAST_ENABLED)
    CLUB_MONITOR_MULTICAST_ADDRESS,
    @IntegerTest
    @MaybeNullIfFalse(CLUB_MONITOR_MULTICAST_ENABLED)
    CLUB_MONITOR_MULTICAST_PORT,
    @IntegerTest
    @MaybeNullIfFalse(CLUB_MONITOR_MULTICAST_ENABLED)
    CLUB_MONITOR_MULTICAST_TTL,
    @IntegerTest
    @MaybeNullIfFalse(CLUB_MONITOR_MULTICAST_ENABLED)
    CLUB_MONITOR_MULTICAST_RESEND_SECONDS,
    
    /* XMPP */
    @Default("false")
    CLUB_MONITOR_XMPP_ENABLED,
    @MaybeNullIfFalse(CLUB_MONITOR_XMPP_ENABLED)
    CLUB_MONITOR_XMPP_SERVICE,
    @MaybeNullIfFalse(CLUB_MONITOR_XMPP_ENABLED)
    CLUB_MONITOR_XMPP_USERNAME,
    @MaybeNullIfFalse(CLUB_MONITOR_XMPP_ENABLED)
    CLUB_MONITOR_XMPP_PASSWORD,
    @MaybeNullIfFalse(CLUB_MONITOR_XMPP_ENABLED)
    CLUB_MONITOR_XMPP_SERVER,
    @IntegerTest
    @MaybeNullIfFalse(CLUB_MONITOR_XMPP_ENABLED)
    CLUB_MONITOR_XMPP_PORT,
    @MaybeNullIfFalse(CLUB_MONITOR_XMPP_ENABLED)
    CLUB_MONITOR_XMPP_RESOURCE,
    @MaybeNullIfFalse(CLUB_MONITOR_XMPP_ENABLED)
    CLUB_MONITOR_XMPP_MUC_ENABLED,
    @MaybeNullIfFalse(CLUB_MONITOR_XMPP_ENABLED)
    CLUB_MONITOR_XMPP_MUC,
    @MaybeNullIfFalse(CLUB_MONITOR_XMPP_ENABLED)
    CLUB_MONITOR_XMPP_ADMINS,
    
    @DirectoryTest
    @MaybeNull
    CLUB_KEY_SVN_REPO,
    @MaybeNull
    CLUB_KEY_API_USERNAME,
    @MaybeNull
    CLUB_KEY_API_PASSWORD,
    
    @Default("false")
    CLUB_MONITOR_SSL_ENABLED,
    @FileTest
    @MaybeNullIfFalse(CLUB_MONITOR_SSL_ENABLED)
    CLUB_KEY_KEY_STORE,
    @FileTest
    @MaybeNullIfFalse(CLUB_MONITOR_SSL_ENABLED)
    CLUB_KEY_TRUST_STORE,
    @MaybeNullIfFalse(CLUB_MONITOR_SSL_ENABLED)
    CLUB_KEY_STORE_PW,
    @IntegerTest
    @MaybeNullIfFalse(CLUB_MONITOR_SSL_ENABLED)
    CLUB_MONITOR_SECURE_WEBSERVER_TCPPORT,
    
    /* music player daemon */
    @Default("false")
    MPD_ENABLE,
    @MaybeNullIfFalse(MPD_ENABLE)
    MPD_ADDRESS,
    @IntegerTest
    @MaybeNullIfFalse(MPD_ENABLE)
    MPD_PORT;
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface MaybeNull {
	/* EMPTY */
    }
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface MaybeNullIfFalse {
	Config value();
    }
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface Default {
	String value();
    }
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface BooleanTest {
	/* EMPTY */
    }
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface IntegerTest {
	/* EMPTY */
    }
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface DirectoryTest {
	/* EMPTY */
    }
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface FileTest {
	/* EMPTY */
    }
    
    private static final String NEWLINE = System.getProperty("line.separator");
    private static final Properties PROPERTIES = new Properties();
    private static boolean configLoaded = false;
    
    static void loadDefaults() {
	for (final Field f : Config.class.getDeclaredFields()) {
	    if (!f.isEnumConstant() || f.getType() != Config.class) {
		continue;
	    }
	    final Default def = f.getAnnotation(Default.class);
	    if (def != null) {
		PROPERTIES.setProperty(f.getName(), def.value());
	    }
	}
    }
    
    public static void load(File configFile) throws IOException {
	if (configLoaded) {
	    throw new IllegalStateException();
	}
	configLoaded = true;
	final InputStream inStream = new FileInputStream(configFile);
	try {
	    PROPERTIES.load(inStream);
	} catch (IOException e) {
	    throw new IOException("error reading config file", e);
	} finally {
	    inStream.close();
	}
    }
    
    public static String getConfig() {
	final StringBuilder str = new StringBuilder();
	for (Config c : Config.values()) {
	    final String key = c.toString();
	    final String value = PROPERTIES.getProperty(key);
	    str.append(String.format("%s = %s", key, value));
	    str.append(NEWLINE);
	}
	return str.toString();
    }
    
    public static String getConfigTemplate() {
        final StringBuilder str = new StringBuilder();
        for (Config c : Config.values()) {
            final String key = c.toString();
            str.append(String.format("%s =", key));
            str.append(NEWLINE);
        }
        return str.toString();
    }
    
    public static void startupCheck() {
	final List<String> errConfigs = new LinkedList<String>();
	for (final Field f : Config.class.getDeclaredFields()) {
	    if (!f.isEnumConstant()) {
		continue;
	    }
	    final String name = f.getName();
	    final String value = PROPERTIES.getProperty(f.getName());
	    if (value == null) {
		if (f.getAnnotation(MaybeNull.class) != null) {
		    // config maybe null
		    continue;
		}
		final MaybeNullIfFalse annotation = f
			.getAnnotation(MaybeNullIfFalse.class);
		if (annotation != null) {
		    boolean b = Boolean.parseBoolean(PROPERTIES
			    .getProperty(annotation.value().toString()));
		    if (b != false) {
			errConfigs.add(name);
		    }
		    continue;
		}
		errConfigs.add(name);
	    } else {
		if (f.getAnnotation(FileTest.class) != null) {
		    fileTest(name, value);
		}
		if (f.getAnnotation(DirectoryTest.class) != null) {
		    directoryTest(name, value);
		}
		if (f.getAnnotation(BooleanTest.class) != null) {
		    booleanTest(name, value);
		}
		if (f.getAnnotation(IntegerTest.class) != null) {
		    integerTest(name, value);
		}
	    }
	}
	if (!errConfigs.isEmpty()) {
	    final Joiner joiner = Joiner.on(",").skipNulls();
	    throw new IllegalArgumentException(joiner.join(errConfigs)
		    + " keys not present");
	}
    }
    
    private static final Pattern BOOLEAN_PATTERN =
	    Pattern.compile("\\Atrue|false\\z", Pattern.CASE_INSENSITIVE);
    private static void booleanTest(final String c, final String value) {
	if (!BOOLEAN_PATTERN.matcher(value).matches()) {
	    throw new IllegalArgumentException(c + ": " + value);
	}
    }

    private static void integerTest(final String c, final String value) {
	try {
	    Integer.parseInt(value);
	} catch (NumberFormatException e) {
	    throw new IllegalArgumentException(c + ": " + value, e);
	}
    }
    
    private static void fileTest(final String c, final String path) {
	final File f = new File(path);
	if (!f.canRead() || !f.isFile()) {
	    throw new IllegalArgumentException(c + ": " + f.getAbsolutePath());
	}
    }
    
    private static void directoryTest(final String c, final String path) {
	final File d = new File(path);
	if (!d.canRead() || !d.isDirectory()) {
	    throw new IllegalArgumentException(c + ": " + d.getAbsolutePath());
	}
    }

    /************** HELPER **************/
    public static boolean isNetIOEnabled() {
	return Boolean.parseBoolean(PROPERTIES.getProperty(
		CLUB_MONITOR_NETIO_ENABLE.toString()));
    }
    
    public static InetSocketAddress getNetIOAddress() {
	final InetAddress ip = InetAddresses.forString(
		PROPERTIES.getProperty(CLUB_MONITOR_NETIO_IPADDRESS.toString()));
	final int port = Integer.parseInt(
		PROPERTIES.getProperty(CLUB_MONITOR_NETIO_TCPPORT.toString()));
	return new InetSocketAddress(ip, port);
    }

    public static InetSocketAddress getWebServerPort() {
	return new InetSocketAddress(Integer.parseInt(
		PROPERTIES.getProperty(CLUB_MONITOR_WEBSERVER_TCPPORT.toString())));
    }
    
    public static InetSocketAddress getSecureWebServerPort() {
        return new InetSocketAddress(Integer.parseInt(PROPERTIES.getProperty(
                        CLUB_MONITOR_SECURE_WEBSERVER_TCPPORT.toString())));
    }


    public static boolean isMulticastEnabled() {
	return Boolean.parseBoolean(PROPERTIES.getProperty(
		CLUB_MONITOR_MULTICAST_ENABLED.toString()));
    }
    
    public static InetSocketAddress getMulticastAddress() {
	return new InetSocketAddress(InetAddresses.forString(
		PROPERTIES.getProperty(CLUB_MONITOR_MULTICAST_ADDRESS.toString())),
		Integer.parseInt(
			PROPERTIES.getProperty(CLUB_MONITOR_MULTICAST_PORT.toString())));
    }
    
    public static int getMulticastTTL() {
	return Integer.parseInt(
		PROPERTIES.getProperty(CLUB_MONITOR_MULTICAST_TTL.toString()));
    }
    
    public static int getMulticastResendSeconds() {
	return Integer.parseInt(PROPERTIES.getProperty(
		CLUB_MONITOR_MULTICAST_RESEND_SECONDS.toString()));
    }

    public static boolean isXMPPEnabled() {
        return Boolean.parseBoolean(
                PROPERTIES.getProperty(CLUB_MONITOR_XMPP_ENABLED.toString()));
    }
    
    public static String getXMPPHost() {
	return PROPERTIES.getProperty(CLUB_MONITOR_XMPP_SERVER.toString());
    }

    public static String getXMPPUsername() {
	return PROPERTIES.getProperty(CLUB_MONITOR_XMPP_USERNAME.toString());
    }

    public static String getXMPPService() {
	return PROPERTIES.getProperty(CLUB_MONITOR_XMPP_SERVICE.toString());
    }

    public static int getXMPPPort() {
	return Integer.parseInt(
		PROPERTIES.getProperty(CLUB_MONITOR_XMPP_PORT.toString()));
    }
    
    public static String getXMPPPassword() {
	return PROPERTIES.getProperty(CLUB_MONITOR_XMPP_PASSWORD.toString());
    }

    public static String getXMPPResource() {
	return PROPERTIES.getProperty(CLUB_MONITOR_XMPP_RESOURCE.toString());
    }

    public static boolean isXMPPMUCEnabled() {
        return Boolean.parseBoolean(
                PROPERTIES.getProperty(CLUB_MONITOR_XMPP_MUC_ENABLED.toString()));
    }
    
    public static String getXMPPMUC() {
	return PROPERTIES.getProperty(CLUB_MONITOR_XMPP_MUC.toString());
    }
    
    public static File getSVNRepo() {
        return new File(PROPERTIES.getProperty(CLUB_KEY_SVN_REPO.toString()));
    }

    public static String getKeyAPIUsername() {
        return PROPERTIES.getProperty(CLUB_KEY_API_USERNAME.toString());
    }

    public static String getKeyAPIPassword() {
        return PROPERTIES.getProperty(CLUB_KEY_API_PASSWORD.toString());
    }
    
    public static boolean isSSLEnabled() {
	return Boolean.parseBoolean(PROPERTIES.getProperty(
		CLUB_MONITOR_SSL_ENABLED.toString()));
    }
    
    public static File getKeyKeyStore() {
        return new File(PROPERTIES.getProperty(CLUB_KEY_KEY_STORE.toString()));
    }
    
    public static File getKeyTrustStore() {
        return new File(PROPERTIES.getProperty(CLUB_KEY_TRUST_STORE.toString()));
    }
    
    public static char[] getKeyStorePw() {
        return PROPERTIES.getProperty(CLUB_KEY_STORE_PW.toString()).toCharArray();
    }
    
    public static boolean isMPDEnabled() {
        return Boolean.parseBoolean(PROPERTIES.getProperty(MPD_ENABLE.toString()));
    }
    
    public static InetSocketAddress getMPDAddress() {
        return new InetSocketAddress(InetAddresses.forString(
                PROPERTIES.getProperty(MPD_ADDRESS.toString())),
                Integer.parseInt(PROPERTIES.getProperty(MPD_PORT.toString())));
    }
    
    public static List<String> getXMPPAdmins() {
	final String property = PROPERTIES.getProperty(
		CLUB_MONITOR_XMPP_ADMINS.toString());
	if (property == null) {
	    return Collections.emptyList();
	}
	return Collections.unmodifiableList(Arrays.asList(property.split(",")));
    }
}
