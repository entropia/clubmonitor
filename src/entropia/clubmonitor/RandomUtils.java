package entropia.clubmonitor;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

public final class RandomUtils {
    private RandomUtils() {}
    public static final Random random;
    static {
	try {
	    random = SecureRandom.getInstance("SHA1PRNG");
	} catch (final NoSuchAlgorithmException e) {
	    throw new AssertionError(e);
	}
    }

    public static long generation() {
	synchronized (random) {
	    long r = random.nextLong();
	    while (r <= 0) {
		r = random.nextLong();
	    }
	    return r;
	}
    }
}
