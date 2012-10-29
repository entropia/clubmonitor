package entropia.clubmonitor;

import static entropia.clubmonitor.WebServer.disableCaching;
import static entropia.clubmonitor.WebServer.readReply;
import static entropia.clubmonitor.WebServer.replyWithBadRequest;
import static entropia.clubmonitor.WebServer.replyWithInternalError;
import static entropia.clubmonitor.WebServer.replyWithNotFound;
import static entropia.clubmonitor.WebServer.setContentType;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import entropia.clubmonitor.clubkey.ClubKey;
import entropia.clubmonitor.clubkey.ClubKeyTransition;
import entropia.clubmonitor.types.Key;
import entropia.clubmonitor.types.DoorKey;
import entropia.clubmonitor.types.DoorMasterKey;
import entropia.clubmonitor.types.PiccKey;
import entropia.clubmonitor.types.Uid;
import entropia.clubmonitor.xml.clubkey.Card;

final class AuthServer implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(AuthServer.class);
    
    private final SyncService syncService;
    
    AuthServer(SyncService syncService) {
	this.syncService = syncService;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            final String path = exchange.getRequestURI().getPath();
            if (!"POST".equals(exchange.getRequestMethod())) {
                replyWithBadRequest(exchange);
                return;
            }
	    try {
		if ("/auth/cardreader/fuckedup".equals(path)) {
		    checkEmptyResponse(exchange);
		    TernaryStatusRegister.CARDREADER_KAPUTT.on();
		    sendAnswer(exchange, "");
		    return;
		} else if ("/auth/cardreader/fixed".equals(path)) {
		    checkEmptyResponse(exchange);
		    TernaryStatusRegister.CARDREADER_KAPUTT.off();
		    sendAnswer(exchange, "");
		    return;
		}
	    } catch (Exception e) {
		logger.warn("error parsing query", e);
		replyWithBadRequest(exchange);
		return;
	    }
            final Map<String, String> request;
            final Uid uid;
            try {
                final int size = Integer.parseInt(
                        exchange.getRequestHeaders().getFirst("Content-Length"));
                request = readReply(exchange.getRequestBody(), size);
                uid = new Uid(request.get("UID"));
            } catch (Exception e) {
                logger.warn("error parsing query", e);
                replyWithBadRequest(exchange);
                return;
            }
            final String answer;
            if ("/auth/getkey".equals(path)) {
                final Card card = ClubKey.validateAndGetKey(uid);
                if (card != null) {
                    // doorKey doorMasterKey pickey
                    final Key doorKey = new DoorKey(card.getCa0523DoorKey());
                    final DoorMasterKey ca0523MasterKey = new DoorMasterKey(
                	    card.getCa0523MasterKey());
                    final PiccKey piccKey = new PiccKey(card.getPiccKey());
                    answer = String.format("%s %032X %032X %032X",
                	    Boolean.TRUE.toString(),
                            doorKey.asBigInteger(),
                            ca0523MasterKey.asBigInteger(),
                            piccKey.asBigInteger());
                } else {
                    answer = String.format("%s", Boolean.FALSE.toString());
                }
            } else if ("/auth/succeeded".equals(path)) {
        	ClubKeyTransition.openDoor();
        	syncService.forceUpdate();
                answer = "";
            } else if ("/auth/failed".equals(path)) {
                answer = "";
            } else {
                replyWithNotFound(exchange);
                return;
            }
            sendAnswer(exchange, answer);
        } catch (Exception e) {
            logger.warn("exception while handling", e);
            replyWithInternalError(exchange);
        }
    }

    private void checkEmptyResponse(HttpExchange exchange) {
	final String contentLength =
	        exchange.getRequestHeaders().getFirst("Content-Length");
	if (contentLength == null) {
	    return;
	}
	final Integer size = Integer.parseInt(contentLength);
	if (size != 0) {
	    throw new IllegalArgumentException("non-empty http content");
	}
    }

    private void sendAnswer(HttpExchange exchange, final String answer)
	    throws IOException {
	final byte[] bytes = answer.getBytes(Charsets.US_ASCII);
	setContentType(exchange, "text/plain; charset=US-ASCII");
	disableCaching(exchange);
	exchange.sendResponseHeaders(200, (bytes.length > 0) ? bytes.length : -1);
	final OutputStream responseBody = exchange.getResponseBody();
	try {
	    if (bytes.length > 0) {
	        responseBody.write(bytes);
	    }
	} finally {
	    responseBody.close();
	}
    }
}
