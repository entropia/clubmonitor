package entropia.clubmonitor.types;

import java.math.BigInteger;
import java.util.Objects;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;

import entropia.clubmonitor.Null;

public abstract class Key {
    private static final Pattern PATTERN = Pattern.compile("\\A[A-F0-9]{32}\\z");
    private final String key;
    
    protected Key(@Nullable String doorKey) throws DataFormatException {
        Objects.requireNonNull(doorKey);
        if (!PATTERN.matcher(doorKey).matches()) {
            throw new DataFormatException();
        }
        this.key = doorKey;
    }

    @Override
    public String toString() {
        return Null.assertNonNull(key);
    }
    
    public BigInteger asBigInteger() {
        return new BigInteger(key, 16);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Key other = (Key) obj;
        if (!key.equals(other.key)) {
            return false;
        }
        return true;
    }
}
