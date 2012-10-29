package entropia.clubmonitor.clubkey;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.Date;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;

import entropia.clubmonitor.Config;
import entropia.clubmonitor.types.Uid;
import entropia.clubmonitor.xml.clubkey.Card;

public final class ClubKey {
    private ClubKey() {}
    private static Logger logger = LoggerFactory.getLogger(ClubKey.class);
    
    private static final Schema schema;
    private static final SubversionFileProvider svn;
    private static final JAXBContext jaxbContext;
    
    static {
        try {
            final URL schemaFile = ClubKey.class.getResource("clubkey.xsd");
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            schema = factory.newSchema(schemaFile);
            svn = new SubversionFileProvider(Config.getSVNRepo());
            jaxbContext = JAXBContext.newInstance(Card.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
    
    /* perhaps we need this later on */
    @SuppressWarnings("unused")
    private static Marshaller createMarshaller() throws JAXBException {
        final Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setSchema(schema);
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        return marshaller;
    }
    
    private static Unmarshaller createUnmarshaller() throws JAXBException {
        final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        unmarshaller.setSchema(schema);
        return unmarshaller;
    }
    
    public static Card validateAndGetKey(final Uid uid) {
        try {
            return _validateAndGetKey(uid);
        } catch (Exception e) {
            logger.warn("error checking uid " + uid, e);
            return null;
        }
    }

    private static Card _validateAndGetKey(final Uid uid) throws JAXBException,
	    SVNException, IOException {
	final Unmarshaller unmarshaller = createUnmarshaller();
	final String xmlContent = svn.getFileContent(uid);
	final StreamSource source = new StreamSource(new StringReader(xmlContent));
	final Card card = unmarshaller.unmarshal(source, Card.class).getValue();
	if (!card.isActive()) {
	    return null;
	}
	if (!inValidTimeRange(card)) {
	    return null;
	}
	return card;
    }

    private static boolean inValidTimeRange(Card card) {
        final Date now = new Date();
        final XMLGregorianCalendar notbefore = card.getNotbefore();
        final XMLGregorianCalendar notafter = card.getNotafter();

        boolean allow = true;
        if (notbefore != null) {
            allow &= now.after(notbefore.toGregorianCalendar().getTime());
        }
        if (notafter != null) {
            allow &=  now.before(notafter.toGregorianCalendar().getTime());
        }
        return allow;
    }
}
