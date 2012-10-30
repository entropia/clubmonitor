package entropia.clubmonitor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import entropia.clubmonitor.clubkey.ClubKeyTransition;

public abstract class Trigger {
    private static final Logger logger = LoggerFactory.getLogger(Trigger.class);
    
    
    public final static Collection<Trigger> trigger;
    static {
	final Collection<Trigger> c = new LinkedList<Trigger>(Arrays.asList(
	            new ClubKeyTransition(),
		    new ClubStatusTransition(),
		    new XMPPNotifier(),
		    new MpdNotifier()));
	if (Config.isMulticastEnabled()) {
	    c.add(new MulticastNotifier());
	}
	trigger = Collections.unmodifiableCollection(c);
    }
    
    private final boolean handleNonPublic;
    
    public Trigger(boolean handleNonPublic) {
        this.handleNonPublic = handleNonPublic;
    }
    
    public static void call(TernaryStatusRegister register) {
        if (register.isPublic()) {
            log(register);
        }
        for (Trigger t : trigger) {
            if (register.isPublic() || t.handleNonPublic) {
                try {
                    t.trigger(register);
                } catch (Exception e) {
                    logger.warn("exception while calling trigger "
                            + t.getClass().getName(), e);
                }
            }
        }
    }
    
    protected static void log(TernaryStatusRegister register) {
        logger.info(String.format("%s changed to %s", register,
                register.status()));
    }
    
    public abstract void trigger(TernaryStatusRegister register)
            throws IOException;
}
