package net.kdt.pojavlaunch.customcontrols;

public class ControlJoystickData extends ControlData {

    /* Whether the joystick can stay forward */
    public boolean forwardLock = false;
    /*
     * Whether the finger tracking is absolute (joystick jumps to where you touched)
     * or relative (joystick stays in the center)
     */
    public boolean absolute = false;

    /**
     * Whether a double-tap on a direction locks movement there until the stick is touched again.
     * Off by default, additive to old layouts the same way {@link ControlData#slideRepeat} is:
     * a saved file with nothing here deserialises to false and behaves exactly as it always did.
     */
    public boolean autoWalk = false;

    public ControlJoystickData(){
        super();
    }

    public ControlJoystickData(ControlJoystickData properties) {
        super(properties);
        forwardLock = properties.forwardLock;
        absolute = properties.absolute;
        autoWalk = properties.autoWalk;
    }
}
