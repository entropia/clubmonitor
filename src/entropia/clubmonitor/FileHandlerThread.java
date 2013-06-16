package entropia.clubmonitor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingDeque;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

enum FileHandlerThread implements Runnable {
    INSTANCE
    ;

    private static final Logger logger =
	    LoggerFactory.getLogger(FileHandlerThread.class);
    
    private static final LinkedBlockingDeque<FileTransferRequest> queue =
	    new LinkedBlockingDeque<FileTransferRequest>();
    
    
    private static final File tempDir = new File("/tmp");
    static {
	if (!tempDir.isDirectory()) {
	    throw new AssertionError("/tmp");
	}
    }
    
    public static Thread startFileHandlerThread() {
	final Thread t = new Thread(INSTANCE);
	t.setName(FileHandlerThread.class.getCanonicalName());
	t.setDaemon(true);
	t.start();
	return t;
    }
    
    private static void _run()
	    throws InterruptedException, IOException, XMPPException {
	while (true) {
	    final FileTransferRequest request = queue.takeFirst();
	    final IncomingFileTransfer fileTransfer = request.accept();
	    logger.info("starting file transfer from " + fileTransfer.getPeer());
	    final File tempFile = File.createTempFile(
	            FileHandlerThread.class.getName(), ".mp3", tempDir);
	    try {
		copyFileFromJabber(fileTransfer, tempFile);
		play(tempFile);
	    } finally {
		if (!tempFile.delete()) {
		    logger.warn("error deleting file " + tempFile.getPath());
		}
	    }
	}
    }

    private static void copyFileFromJabber(IncomingFileTransfer first,
            final File tempFile) throws XMPPException, FileNotFoundException,
            IOException {
	logger.debug("writing jabber stream to " + tempFile.getPath());
	final InputStream in = first.recieveFile();
	try {
	    final OutputStream out = new FileOutputStream(tempFile);
	    try {
		copyStream(in, out);
	    } finally {
		out.close();
	    }
	} finally {
	    in.close();
	}
    }   

    private static final String MPG123_CMD = "/usr/bin/mpg123";
    private static void play(final File tempFile)
	    throws IOException, InterruptedException {
	logger.debug("playing file " + tempFile.getPath());
	final ProcessBuilder processBuilder = new ProcessBuilder(
	        Arrays.asList(MPG123_CMD, tempFile.getPath()));
	processBuilder.directory(tempDir);
	processBuilder.redirectErrorStream(true);
	final Process process = processBuilder.start();
	logger.info(String.format("mpg123 exited with %d", process.waitFor()));
	writeProcessOutput(process);
    }

    private static void writeProcessOutput(final Process process)
            throws IOException {
	final InputStream inputStream = new BufferedInputStream(
		process.getInputStream());
	final String mpg123Output;
	try {
	    mpg123Output = slurp(inputStream);
	} finally {
	    inputStream.close();
	}
	if (!mpg123Output.isEmpty()) {
	    final String s = stripTrailingNL(mpg123Output);
	    logger.warn("mpg123 output:\n" + s);	    
	}
    }

    private static String stripTrailingNL(final String str) {
	if (str.endsWith("\n")) {
	    return str.substring(0, str.length()-1);
	}
	return str;
    }

    private static void copyStream(InputStream in, OutputStream out)
	    throws IOException {
	final byte[] buffer = new byte[4096];
	while (true) {
	    final int read = in.read(buffer);
	    if (read == -1) {
		break;
	    }
	    out.write(buffer, 0, read);
	}
    }
    
    private static String slurp(InputStream in) throws IOException {
	final ByteArrayOutputStream out = new ByteArrayOutputStream();
	copyStream(in, out);
	return out.toString();
    }

    @Override
    public void run() {
	final String name = Thread.currentThread().getName();
	logger.info("started");
	while (!Thread.interrupted()) {
	    try {
		_run();
	    } catch (InterruptedException e) {
		Thread.currentThread().interrupt();
	    } catch (Exception e) {
		logger.warn("exception acting on file in " + name, e);
	    }
	}
    }

    public static void add(FileTransferRequest request) {
	queue.addLast(request);
    }
}
