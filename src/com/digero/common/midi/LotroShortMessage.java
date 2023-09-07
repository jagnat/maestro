package com.digero.common.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

public class LotroShortMessage extends ShortMessage {
	private int channel2 = -1;
	
	@Override public void setMessage(int command, int channel, int data1, int data2) throws InvalidMidiDataException {
        // check for valid value
        if (channel < 0 || channel >= MidiConstants.CHANNEL_COUNT_ABC) {
            throw new InvalidMidiDataException("channel out of range: " + channel);
        }
        channel2 = channel;
        /*if (command == ShortMessage.PROGRAM_CHANGE) {
        	data2 = channel;
        }*/
        super.setMessage(command, channel & 0xF, data1, data2);
        /*if (command == ShortMessage.PROGRAM_CHANGE) {
        	length = 3;
        }*/
    }
	
	@Override public int getChannel() {
		return channel2;
	}
	
	@Override public Object clone() {
		LotroShortMessage clone = new LotroShortMessage();
        try {
            clone.setMessage(getCommand(), getChannel(), getData1(), getData2());
        } catch (InvalidMidiDataException e) {
        	e.printStackTrace();
            throw new IllegalArgumentException(e.getMessage());
        }
        Thread.dumpStack();
        return clone;
    }
	
	@Override public byte[] getMessage() {
        byte[] returnedArray = new byte[length];
        System.arraycopy(data, 0, returnedArray, 0, length);
        Thread.dumpStack();
        return returnedArray;
    }
}