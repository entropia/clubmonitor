package entropia.clubmonitor.types;

import java.math.BigInteger;
import java.util.regex.Pattern;

public abstract class Key {
    private static final Pattern PATTERN = Pattern.compile("\\A[A-F0-9]{32}\\z");
    private final String key;
    
    protected Key(String doorKey) throws DataFormatException {
        if (doorKey == null) {
            throw new NullPointerException();
        }
        if (!PATTERN.matcher(doorKey).matches()) {
            throw new DataFormatException();
        }
        this.key = doorKey;
    }

    @Override
    public String toString() {
        return key;
    }
    
    public BigInteger asBigInteger() {
        return new BigInteger(key, 16);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
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
