package entropia.clubmonitor;

import static entropia.clubmonitor.TernaryStatusRegister.CLUB_OFFEN;
import static entropia.clubmonitor.TernaryStatusRegister.FENSTER_OFFEN;
import static entropia.clubmonitor.TernaryStatusRegister.HW_FEHLER;
import static entropia.clubmonitor.TernaryStatusRegister.CARDREADER_KAPUTT;
import static entropia.clubmonitor.TernaryStatusRegister.OVERRIDE_WINDOWS;
import static entropia.clubmonitor.TernaryStatusRegister.RegisterState.HIGH;
import static entropia.clubmonitor.TernaryStatusRegister.RegisterState.LOW;

import java.io.IOException;

import org.jivesoftware.smack.packet.Presence.Mode;

import entropia.clubmonitor.TernaryStatusRegister.RegisterState;

final class XMPPNotifier extends PublicOnlyTrigger {
    public static enum StatusChange {
	CLUB_IST_OFFEN("Club ist offen", Mode.available, true, false),
	CLUB_IST_ZU("Club ist geschlossen", Mode.away, true, false),
	FENSTER_ALARM("Fensteralarm!", Mode.away, true, true),
	FENSTER_ALARM_GESCHLOSSEN("Fensteralarm vorbei"
			+ " - Fenster wurden geschlossen!", Mode.available, true, true),
	FENSTER_ALARM_CLUB_OFFEN("Fensteralarm vorbei"
			+ " - Club wurde geoeffnet!", Mode.available, true, true),
	FEHLER_ON("Allgemeine Ausnahmebedingung!", Mode.xa, true, true),
	FEHLER_OFF("Allgemeine Ausnahmebedingung behoben!", Mode.away, true, true),
	FEHLER_SCHLOSS_ON("Kartenleser abgefallen!", Mode.dnd, true, false),
	FEHLER_SCHLOSS_OFF("Kartenleser wiedergefunden!", Mode.available, true, false),
	;
	
	private final String msg;
	private final boolean toMuc;
	private final boolean toAll;
	private final Mode mode;
	private StatusChange(String msg, Mode mode, boolean toMuc,
		boolean toAll) {
	    this.mode = mode;
	    this.msg = msg;
	    this.toMuc = toMuc;
	    this.toAll = toAll;
	}
	
	public String getMessage() {
	    return msg;
	}
	
	public boolean deliverToMuc() {
	    return toMuc;
	}
	
	public boolean deliverToAll() {
	    return toAll;
	}
	
	public Mode isOnline() {
	    return mode;
	}

	public Mode getMode() {
	    return mode;
        }
    }

    @Override
    public void trigger(final TernaryStatusRegister changed)
	    throws IOException {
        if (!Config.isXMPPEnabled()) {
            return;
        }
	switch (changed) {
	case OVERRIDE_WINDOWS:
	case KEY_DOOR_BUZZER:
	    // we don't care about these and normally don't get them
	    return;
	case CLUB_OFFEN:
	    if (changed.status() == HIGH) {
		// club nun offen
		if (FENSTER_OFFEN.status() == HIGH 
			&& OVERRIDE_WINDOWS.status() == LOW) {
		    send(StatusChange.FENSTER_ALARM_CLUB_OFFEN);
		    return;
		}
		send(StatusChange.CLUB_IST_OFFEN);
		return;
	    } else if (changed.status() == LOW) {
		// club nun geschlossen
		if (FENSTER_OFFEN.status() == HIGH
			&& OVERRIDE_WINDOWS.status() == LOW) {
		    send(StatusChange.FENSTER_ALARM);
		    return;
		}
		send(StatusChange.CLUB_IST_ZU);
		return;
	    }
	    return;
	case FENSTER_OFFEN:
	    if (changed.status() == HIGH) {
		// fenster nun offen
		if (CLUB_OFFEN.status() == LOW
			&& OVERRIDE_WINDOWS.status() == LOW) {
		    send(StatusChange.FENSTER_ALARM);
		    return;
		}
	    } else if (changed.status() == LOW) {
		if (CLUB_OFFEN.status() == LOW
			&& OVERRIDE_WINDOWS.status() == LOW) {
		    send(StatusChange.FENSTER_ALARM_GESCHLOSSEN);
		    return;
		}
	    }
	    return;
	case HW_FEHLER:
	    if (changed.status() == HIGH) {
		send(StatusChange.FEHLER_ON);
	    } else if (changed.status() == LOW) {
		send(StatusChange.FEHLER_OFF);
		recover(changed);
	    }
	    return;
	case CARDREADER_KAPUTT:
	    if (changed.status() == HIGH) {
		send(StatusChange.FEHLER_SCHLOSS_ON);
	    } else if (changed.status() == LOW) {
		send(StatusChange.FEHLER_SCHLOSS_OFF);
		recover(changed);
	    }
	    return;
	}
	throw new IllegalStateException();
    }

    private void recover(final TernaryStatusRegister changed) {
	if (HW_FEHLER.status() == HIGH) {
	    send(StatusChange.FEHLER_ON);
	    return;
	} else if (FENSTER_OFFEN.status() == HIGH
		&& CLUB_OFFEN.status() == LOW
		&& OVERRIDE_WINDOWS.status() == LOW) {
	    send(StatusChange.FENSTER_ALARM);
	    return;
	} else if (CARDREADER_KAPUTT.status() == HIGH) {
	    send(StatusChange.FEHLER_SCHLOSS_ON);
	    return;
	} else if (CLUB_OFFEN.status() == HIGH) {
	    send(StatusChange.CLUB_IST_OFFEN);
	    return;
	} else if (CLUB_OFFEN.status() == LOW) {
	    // this is the default case - no need to change anything
	    return;
	} else if (CLUB_OFFEN.status() == RegisterState.UNINITIALIZED) {
	    return;
	}
	throw new IllegalStateException("illegal state");
    }

    private void send(StatusChange status) {
	XMPPThread.INSTANCE.status.addLast(status);
    }
}
