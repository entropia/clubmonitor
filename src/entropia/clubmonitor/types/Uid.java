package entropia.clubmonitor.types;

import java.math.BigInteger;
import java.util.Objects;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;

import entropia.clubmonitor.Null;

public final class Uid {
    private static final Pattern pattern = Pattern.compile("\\A[A-F0-9]{14}\\z");
    private final String uid;
    public Uid(@Nullable String uid) throws DataFormatException {
        Objects.requireNonNull(uid);
        if (!pattern.matcher(uid).matches()) {
            throw new DataFormatException();
        }
        this.uid = uid;
    }
    
    @Override
    public String toString() {
        return Null.assertNonNull(uid);
    }
    
    public BigInteger asBigInteger() {
        return new BigInteger(uid, 16);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Uid other = (Uid) obj;
        return uid.equals(other.uid);
    }
}
