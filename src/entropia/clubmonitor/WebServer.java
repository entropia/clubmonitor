package entropia.clubmonitor;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import entropia.clubmonitor.types.DataFormatException;

final class WebServer {
    private static Logger serverLogger =
            LoggerFactory.getLogger(WebServer.class);
    private HttpServer server;
    private HttpsServer sslserver;
    
    private final HttpLoggerFilter httpLogger = new HttpLoggerFilter();
    private final LoopbackOnlyFilter loopbackFilter = new LoopbackOnlyFilter();
    private final ClubAuthenticator auth = new ClubAuthenticator("club");
    
    private boolean initialized = false;
    
    private final SyncService syncService;
    
    WebServer(SyncService syncService) {
	this.syncService = syncService;
    }
    
    private static HttpContext createContext(@Nullable HttpServer server,
            String path) {
        return Null.assertNonNull(Null.assertNonNull(server).createContext(
                path));
    }
    
    private void init() throws Exception {
	server = HttpServer.create(Config.getWebServerPort(), 0);
	final Executor httpExecutor = Executors.newCachedThreadPool();
        server.setExecutor(httpExecutor);
        setupJsonContext(createContext(server, "/"));
        setupAuthServerContext(createContext(server, "/auth"));

        if (Config.isSSLEnabled()) {
            sslserver = HttpsServer.create(Config.getSecureWebServerPort(), 0);
            sslserver.setHttpsConfigurator(
        	    new StrictHttpsConfigurator(Null.assertNonNull(
        	            SSLContext.getDefault())));
            sslserver.setExecutor(httpExecutor);
            setupJsonContext(createContext(sslserver, "/"));
            setupAuthServerContext(createContext(sslserver, "/auth"));
        }
    }
    
    private void setupAuthServerContext(HttpContext ctx) {
        ctx.setAuthenticator(auth);
        ctx.getFilters().add(loopbackFilter);
        ctx.setHandler(new AuthServer(Null.assertNonNull(syncService)));
    }
    
    private void setupJsonContext(HttpContext ctx) {
        ctx.getFilters().add(httpLogger);
        ctx.setHandler(new StatusServer());
    }
    
    public void stopWebServer() {
        if (server != null) {
            server.stop(1);
            serverLogger.info("webserver stopped");
        }
        if (sslserver != null) {
            sslserver.stop(1);
            serverLogger.info("securewebserver stopped");
        }
    }
    
    public void startWebServer() throws Exception {
	if (initialized) {
	    throw new IllegalStateException("already initialized");
	}
        init();
        server.start();
        serverLogger.info("webserver started");
        if (sslserver != null) {
            sslserver.start();
        }
	serverLogger.info("securewebserver started");
	initialized = true;
    }

    private static void replyWithInt(HttpExchange exchange, int err)
            throws IOException {
        try {
            disableCaching(exchange);
            exchange.sendResponseHeaders(err, -1);
        } finally {
            exchange.getResponseBody().close();
        }
    }
    
    static void replyWithInternalError(HttpExchange exchange)
            throws IOException {
        replyWithInt(exchange, 500);
    }
    
    static void replyWithForbidden(HttpExchange exchange) throws IOException {
        replyWithInt(exchange, 403);
    }
    
    static void replyWithBadRequest(HttpExchange exchange) throws IOException {
        replyWithInt(exchange, 400);
    }

    static void replyWithNotFound(HttpExchange exchange) throws IOException {
        replyWithInt(exchange, 404);
    }

    static void replyWithOk(HttpExchange exchange) throws IOException {
        replyWithInt(exchange, 200);
    }
    
    private static final class HttpLoggerFilter extends Filter {
	private static Logger logger =
		LoggerFactory.getLogger(HttpLoggerFilter.class);
	
	@Override
        public void doFilter(@Nullable HttpExchange exchange,
                @Nullable Chain chain) throws IOException {
	    if (exchange == null || chain == null)
	        return;
	    final HttpPrincipal principal = exchange.getPrincipal();
	    final String user;
            if (principal != null) {
                user = principal.getUsername();
            } else {
                user = "-";
            }
            final String secure = (exchange instanceof HttpsExchange) ?
                    "https" : "http";
	    final String request = String.format("[%s] %s \"%s %s\" %s",
		    exchange.getRemoteAddress().toString(),
		    user,
		    exchange.getRequestMethod(),
		    exchange.getRequestURI(),
		    secure);
	    logger.info(request);
	    chain.doFilter(exchange);
        }

	@Override
        public String description() {
	    return "logs requests";
        }
    }
    
    private static final class LoopbackOnlyFilter extends Filter {
        private static final Logger logger =
                LoggerFactory.getLogger(LoopbackOnlyFilter.class);
        private static final InetAddress loopback4Address;
        private static final InetAddress loopback6Address;
        static {
            try {
                loopback4Address = InetAddress.getByAddress(new byte[] {
                        127,0,0,1});
                loopback6Address = InetAddress.getByAddress(new byte[] {
                        0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1 });
            } catch (UnknownHostException e) {
                throw new AssertionError(e);
            }
        }
        
        @Override
        public void doFilter(@Nullable HttpExchange exchange,
                @Nullable Chain chain) throws IOException {
            if (exchange == null || chain == null)
                return;
            final InetAddress remoteAddress =
                    exchange.getRemoteAddress().getAddress();
            if (loopback4Address.equals(remoteAddress)
                    || loopback6Address.equals(remoteAddress)) {
                chain.doFilter(exchange);
            } else {
                logger.warn("disallowed connection from "
                        + remoteAddress.toString());
                replyWithForbidden(exchange);
            }
        }

        @Override
        public String description() {
            return "loopback filter";
        }
        
    }
    
    private static final class ClubAuthenticator extends BasicAuthenticator {

        private static final String DEFAULT_USERNAME =
                Config.getKeyAPIUsername();
        private static final String DEFAULT_PASSWORD =
                Config.getKeyAPIPassword();
        
        public ClubAuthenticator(String realm) {
            super(realm);
        }

        @Override
        public boolean checkCredentials(@Nullable String username,
                @Nullable String password) {
            if (DEFAULT_USERNAME.equals(username)
                    && DEFAULT_PASSWORD.equals(password)) {
                return true;
            }
            return false;
        }
    }
    
    private static final class StrictHttpsConfigurator extends HttpsConfigurator {
        public StrictHttpsConfigurator(SSLContext context) {
            super(context);
        }

        @Override
        public void configure(@Nullable HttpsParameters params) {
            if (params == null)
                throw new NullPointerException();
            params.setProtocols(new String[] { "TLSv1", "TLSv1.1" });
            params.setCipherSuites(new String[] {
                    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA" });
        }   
    }
    
    public static Map<String,String> readReply(InputStream in, int size)
            throws IOException, DataFormatException {
        final String charset = Charsets.UTF_8.name();
        final Map<String,String> map = new HashMap<>();
        final String content = new String(IOUtils.readBytesStrict(in, size),
                Charsets.US_ASCII);
        final String[] fields = content.split("&");
        for (String f : fields) {
            String[] split = f.split("=",2);
            if (split.length != 2) {
        	throw new DataFormatException();
            }
            map.put(URLDecoder.decode(split[0], charset),
                    URLDecoder.decode(split[1], charset));
        }
        return Null.assertNonNull(Collections.unmodifiableMap(map));
    }
    
    public static void setContentType(HttpExchange exchange,
            String contentType) {
        exchange.getResponseHeaders().set("Content-Type",
                contentType);
    }
    
    private static String RFC1123_PATTERN = "EEE, dd MMM yyyy HH:mm:ss 'GMT'";
    public static String getHttpTimestamp() {
        final SimpleDateFormat formatter = new SimpleDateFormat(
                RFC1123_PATTERN);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        return Null.assertNonNull(formatter.format(new Date()));
    }
    
    public static void disableCaching(HttpExchange exchange) {
        final String timestamp = getHttpTimestamp();
        exchange.getResponseHeaders().set("Date", timestamp);
        exchange.getResponseHeaders().set("Expires", timestamp);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
    }

    static void checkEmptyResponse(HttpExchange exchange) {
        final String contentLength =
                exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength == null) {
            return;
        }
        try {
            if (Integer.parseInt(contentLength) != 0) {
        	throw new IllegalArgumentException("non-empty http content");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
