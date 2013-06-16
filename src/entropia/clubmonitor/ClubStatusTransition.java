package entropia.clubmonitor;

import entropia.clubmonitor.TernaryStatusRegister.RegisterState;

final class ClubStatusTransition extends PublicOnlyTrigger {

    @Override
    public void trigger(@SuppressWarnings("unused") TernaryStatusRegister changed) {
	final RegisterState clubOffen =
		TernaryStatusRegister.CLUB_OFFEN.status();
	final RegisterState fensterOffenStatus =
		TernaryStatusRegister.FENSTER_OFFEN.status();
	switch (clubOffen) {
	case HIGH:
	    switch (fensterOffenStatus) {
	    case HIGH:
		TriggerPort.PIEZO.off();
		TriggerPort.GRUEN.off();
		TriggerPort.ROT.on();
		return;
	    case LOW:
	        TriggerPort.PIEZO.off();
		TriggerPort.GRUEN.on();
		TriggerPort.ROT.off();
		return;
	    case UNINITIALIZED:
		return;
	    default:
	        throw new IllegalArgumentException("illegal state "
	                + fensterOffenStatus.toString());
	    }
	case LOW:
	    switch (fensterOffenStatus) {
	    case HIGH:
		TriggerPort.PIEZO.on();
		TriggerPort.GRUEN.off();
		TriggerPort.ROT.on();
		return;
	    case LOW:
		TriggerPort.PIEZO.off();
		TriggerPort.GRUEN.off();
		TriggerPort.ROT.off();
		return;
	    case UNINITIALIZED:
		return;
	    default:
	        throw new IllegalArgumentException("illegal state "
	                + fensterOffenStatus.toString());
	    }
	case UNINITIALIZED:
	    return;
	default:
	    throw new IllegalArgumentException("illegal state "
	            + clubOffen.toString());
	}
    }
}
