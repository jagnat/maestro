package com.digero.common.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

public class MyShortMessage extends ShortMessage {
	public void setMessage(int command, int channel, int data1, int data2) throws InvalidMidiDataException {
        // check for valid values
        if (command >= 0xF0 || command < 0x80) {
            throw new InvalidMidiDataException("command out of range: 0x" + Integer.toHexString(command));
        }
        if (channel<0 || channel>25) { // <=> (channel<0 || channel>15)
            throw new InvalidMidiDataException("channel out of range: " + channel);
        }
        setMessage((command & 0xF0) | (channel & 0x0F), data1, data2);
    }
}