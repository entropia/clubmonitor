package entropia.clubmonitor;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

final class SSLConfig {
    private SSLConfig() {}
    public static void setupSSL() throws Exception {
        final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        final SSLContext sslContext = SSLContext.getInstance("TLSv1");
        final KeyManager[] km = setupKeyManager();
        final TrustManager[] tm = setupTrustManager();
        sslContext.init(km, tm, random);
        SSLContext.setDefault(sslContext);
    }

    private static TrustManager[] setupTrustManager() throws Exception {
        TrustManagerFactory tmFactory = TrustManagerFactory.getInstance("PKIX");
        tmFactory.init(getCertsStore(Config.getKeyTrustStore()));
        return Null.assertNonNull(tmFactory.getTrustManagers());        
    }

    private static KeyManager[] setupKeyManager() throws Exception {
        final KeyManagerFactory kmFactory = KeyManagerFactory.getInstance(
                "SunX509");
        kmFactory.init(getCertsStore(Config.getKeyKeyStore()),
                Config.getKeyStorePw());
        return Null.assertNonNull(kmFactory.getKeyManagers());
    }

    private static KeyStore getCertsStore(File keyStorePath) throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("JCEKS");
        try (final FileInputStream keyStream = new FileInputStream(
                keyStorePath)) {
            keyStore.load(keyStream, Config.getKeyStorePw());
        }
        return keyStore;
    }
}
