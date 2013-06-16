package entropia.clubmonitor;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static entropia.clubmonitor.IOUtils.*;

import org.junit.Test;

@SuppressWarnings("static-method")
public final class IOUtilsTest {
    
    private static final Charset charset = Charset.forName("US-ASCII");
    private static InputStream inputStreamFromString(String s) {
	final byte[] bytes = s.getBytes(charset);
	return new ByteArrayInputStream(bytes);
    }
    
    @Test
    public void testReadBytesStrict1() throws IOException {
	try (InputStream one = inputStreamFromString("1\r\n");
	InputStream zero = inputStreamFromString("0\r\n")) {
	    byte[] bytes = readBytesStrict(one, 3);
	    verifyBinaryDigitLine(bytes);
	    bytes = readBytesStrict(zero, 3);
	    verifyBinaryDigitLine(bytes);
	}
    }
    
    @Test
    public void testReadBytesEmpty() throws IOException {
	try (InputStream empty = inputStreamFromString("")) {
	    byte[] bytes = readBytesStrict(empty, 0);
	    assertEquals(0, bytes.length);
	}
    }
    
    @Test(expected=IOException.class)
    public void testReadBytesStrict2() throws IOException {
	try (InputStream broken = inputStreamFromString("1")) {
	    readBytesStrict(broken, 2);
	}
    }
    
    
    @Test
    public void testReadDigits1() throws IOException {
	final long[] longs = new long[] {
		0, 1, 10, 100, 1000, 10000, 100000
	};
	for (long l : longs) {
	    try (InputStream in =
		    inputStreamFromString(Long.toString(l) + "\r\n")) {
	        final long r = readDigits(in);
	        assertEquals(l, r);
	    }
	}
    }
    
    @Test(expected=IllegalStateException.class)
    public void testReadDigits2() throws IOException {
	final long[] longs = new long[] {
		0, 1, 10, 100, 1000, 10000, 100000
	};
	for (long l : longs) {
	    try (InputStream in =
		    inputStreamFromString(Long.toString(l) + "\r")) {
	        final long r = readDigits(in);
	        assertEquals(l, r);
	    }
	}
    }
    
    @Test(expected=IllegalStateException.class)
    public void testReadDigits3() throws IOException {
	final long[] longs = new long[] {
		0, 1, 10, 100, 1000, 10000, 100000
	};
	for (long l : longs) {
	    try (InputStream in =
		    inputStreamFromString(Long.toString(l) + "\n")) {
	        final long r = readDigits(in);
	        assertEquals(l, r);
	    }
	}
    }
    
    @Test(expected=IllegalStateException.class)
    public void testCheckBytes() {
	checkByte(1, 2);
    }
    
    @Test
    public void testVerifyBinaryDigitLine() throws IOException {
	verifyBinaryDigitLine(new byte[] {'0', '\r', '\n'});
	verifyBinaryDigitLine(new byte[] {'1', '\r', '\n'});
    }
}
