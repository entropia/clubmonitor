package entropia.clubmonitor;

import org.eclipse.jdt.annotation.Nullable;

public class Null {
    public static <T> T assertNonNull(@Nullable T t) {
        if (t == null)
            throw new NullPointerException();
        return t;
    }
}
