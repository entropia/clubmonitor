package entropia.clubmonitor;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public final class IOUtils {
    private IOUtils() {}

    private static final int IPTOS_LOWDELAY = 0x10;
    
    static Socket connectLowLatency(final InetSocketAddress tcpAddress,
	    final boolean keepAlive) throws IOException {
	final Socket s = new Socket();
	s.setSoLinger(false, 0);
	s.setKeepAlive(keepAlive);
	s.setSoTimeout((int) TimeUnit.SECONDS.toMillis(1));
	s.setTcpNoDelay(true);
	s.setTrafficClass(IPTOS_LOWDELAY);
	s.connect(tcpAddress);
	return s;
    }
    
    static long readDigits(InputStream inputStream) throws IOException {
	final StringBuilder str = new StringBuilder();
	while (true) {
	    int read = inputStream.read();
	    if (read == -1) {
		throw new IOException("got EOF from server side");
	    } else if (asciiIsDigit(read)) {
		str.append(Character.valueOf((char) read));
		continue;
	    }
	    checkByte(read, '\r');
	    checkByte(inputStream.read(), '\n');
	    break;
	}
	return Long.parseLong(str.toString());
    }
    
    static void checkByte(int read, int c) {
	if (read != c) {
	    throw new IllegalStateException("unsynchronized input");
	}
    }

    static boolean asciiIsDigit(int read) {
	return 48 <= read && read <= 57;
    }
    

    static byte[] readBytesStrict(InputStream inputStream, int numBytes)
	    throws IOException {
	final byte[] answer = new byte[numBytes];
	for (int i = 0; i < numBytes; i++) {
	    int r = inputStream.read();
	    if (r == -1) {
		throw new IOException("got EOF from server side");
	    }
	    answer[i] = (byte) r;
	}
	return answer;
    }
    
    static void verifyBinaryDigitLine(byte[] readBytes) throws IOException {
	boolean b = readBytes[0] == '0' || readBytes[0] == '1';
	b &= readBytes[1] == '\r';
	b &= readBytes[2] == '\n';
	if (!b) {
	    throw new IOException("illegal response: "
		    + Arrays.toString(readBytes));
	}
    }
}
