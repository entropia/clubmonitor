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
    
    
    private final static Collection<Trigger> trigger;
    static {
	final Collection<Trigger> c = new LinkedList<>(Arrays.asList(
	            new ClubKeyTransition(),
		    new ClubStatusTransition(),
		    new XMPPNotifier(),
		    new MpdNotifier(),
		    new ClubBusTrigger()));
	if (Config.isMulticastEnabled()) {
	    c.add(new MulticastNotifier());
	}
	trigger = Collections.unmodifiableCollection(c);
    }
    
    private final boolean handleNonPublic;
    
    Trigger(boolean handleNonPublic) {
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
    
    private static void log(TernaryStatusRegister register) {
        logger.info(String.format("%s changed to %s", register,
                register.status()));
    }
    
    protected abstract void trigger(TernaryStatusRegister register);
}
