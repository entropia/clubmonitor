package entropia.clubmonitor;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Null {
    private static final Logger logger = LoggerFactory.getLogger(Null.class);
    private static final String ERROR_PREFIX = "null pointer assertion failed: ";
    
    public static <T> T assertNonNull(@Nullable T t) {
        if (t == null) {
            final NullPointerException npe = new NullPointerException();
            final StackTraceElement[] stes = npe.getStackTrace();
            if (stes != null && stes.length >= 2 && stes[1] != null)
                logger.error(ERROR_PREFIX + stes[1].toString());
            else
                logger.error(ERROR_PREFIX + "unkown location");
            throw npe;
        }
        return t;
    }
}
