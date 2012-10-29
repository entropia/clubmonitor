package entropia.clubmonitor.clubkey;

import entropia.clubmonitor.AllTrigger;
import entropia.clubmonitor.TernaryStatusRegister;
import entropia.clubmonitor.TernaryStatusRegister.RegisterState;
import entropia.clubmonitor.TriggerPort;

public final class ClubKeyTransition extends AllTrigger {

    @Override
    public void trigger(TernaryStatusRegister register) {
        if (register == TernaryStatusRegister.KEY_DOOR_BUZZER
                && register.status() == RegisterState.HIGH
                && TernaryStatusRegister.CLUB_OFFEN.status() != RegisterState.HIGH) {
            closeDoor();
        }
    }
    
    public static void openDoor() {
        TriggerPort.TUER_OEFFNEN.onoff(1);
    }
    
    public static void closeDoor() {
        TriggerPort.TUER_SCHLIESSEN.onoff(1);	
    }
}
