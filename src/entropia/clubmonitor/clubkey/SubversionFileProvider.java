package entropia.clubmonitor.clubkey;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import com.google.common.base.Charsets;

import entropia.clubmonitor.types.Uid;

public final class SubversionFileProvider {
    private final SVNRepository repo;
    
    public SubversionFileProvider(File baseDir) throws SVNException {
        if (baseDir == null || !baseDir.isDirectory()) {
            throw new IllegalArgumentException();
        }
        this.repo = SVNRepositoryFactory.create(SVNURL.fromFile(baseDir));
    }
    
    synchronized String getFileContent(Uid uid) throws SVNException, IOException {
        if (uid == null) {
            throw new NullPointerException();
        }
        final String xmlFile = uid.toString() + ".xml";
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            repo.getFile(xmlFile, SVNRepository.INVALID_REVISION, null, out);
        } finally {
            out.close();
        }
        return out.toString(Charsets.UTF_8.name());
    }
    
    synchronized long getSvnRevision() throws SVNException {
	return repo.getLatestRevision();
    }
}
