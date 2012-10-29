package entropia.clubmonitor.types;

import java.math.BigInteger;
import java.util.regex.Pattern;

public final class Uid {
    private static final Pattern pattern = Pattern.compile("\\A[A-F0-9]{14}\\z");
    private final String uid;
    public Uid(String uid) throws DataFormatException {
        if (uid == null) {
            throw new NullPointerException();
        }
        if (!pattern.matcher(uid).matches()) {
            throw new DataFormatException();
        }
        this.uid = uid;
    }
    
    @Override
    public String toString() {
        return uid;
    }
    
    public BigInteger asBigInteger() {
        return new BigInteger(uid, 16);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Uid other = (Uid) obj;
        if (!uid.equals(other.uid)) {
            return false;
        }
        return true;
    }
}
