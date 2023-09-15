package com.digero.common.midi;

import java.util.BitSet;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import com.digero.common.util.ICompileConstants;

public class NoteFilterTransceiver implements Transceiver, MidiConstants, ICompileConstants {
	private Receiver receiver = null;
	private boolean hasAbcPart = false;
	private BitSet[] notesOn = new BitSet[CHANNEL_COUNT_ABC];
	private BitSet solos = new BitSet();

	public void onAbcPartChanged(boolean hasAbcPart) {
		this.hasAbcPart = hasAbcPart;
		for (BitSet set : notesOn) {
			if (set != null)
				set.clear();
		}
	}

	public void setNoteSolo(int drumId, boolean solo) {
		solos.set(drumId, solo);
		if (solo)
			turnOffInactiveNotes();
	}

	public boolean getNoteSolo(int noteId) {
		return solos.get(noteId);
	}

	public boolean isNoteActive(int noteId) {
		return solos.isEmpty() || solos.get(noteId);
	}

	public boolean isAnyNoteSolo() {
		return !solos.isEmpty();
	}

	@Override
	public void close() {
		// Nothing to do
	}

	@Override
	public Receiver getReceiver() {
		return receiver;
	}

	@Override
	public void setReceiver(Receiver receiver) {
		this.receiver = receiver;
	}

	private boolean isNoteOn(int channel, int noteId) {
		if (notesOn[channel] == null)
			return false;

		return notesOn[channel].get(noteId);
	}

	private void setNoteOn(int channel, int noteId, boolean on) {
		if (notesOn[channel] == null)
			notesOn[channel] = new BitSet();

		notesOn[channel].set(noteId, on);
	}

	private void turnOffInactiveNotes() {
		if (receiver == null)
			return;

		for (int c = 0; c < CHANNEL_COUNT_ABC; c++) {
			BitSet set = notesOn[c];
			if (set == null)
				continue;

			for (int noteId = set.nextSetBit(0); noteId >= 0; noteId = set.nextSetBit(noteId + 1)) {
				if (!isNoteActive(noteId)) {
					set.clear(noteId);
					MidiEvent evt = MidiFactory.createNoteOffEvent(noteId, c, -1);
					receiver.send(evt.getMessage(), evt.getTick());
				}
			}
		}
	}

	@Override
	public void send(MidiMessage message, long timeStamp) {
		if (receiver == null)
			return;
		// if (message instanceof LotroShortMessage) System.out.println("So far..");
		if (hasAbcPart && message instanceof ShortMessage) {
			ShortMessage m = (ShortMessage) message;
			int cmd = m.getCommand();
			if (cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF) {

				int c = m.getChannel();
				int noteId = m.getData1();
				int speed = m.getData2();
				boolean noteOnMsg = (cmd == ShortMessage.NOTE_ON) && speed > 0;
				boolean noteOffMsg = (cmd == ShortMessage.NOTE_OFF) || (cmd == ShortMessage.NOTE_ON && speed == 0);

				if (!isNoteActive(noteId) && !(isNoteOn(c, noteId) && noteOffMsg))
					return;

				if (noteOnMsg)
					setNoteOn(c, noteId, true);
				else if (noteOffMsg)
					setNoteOn(c, noteId, false);
			}
		}
		/*
		 * if (message instanceof ShortMessage) { ShortMessage m = (ShortMessage) message; if (m.getCommand() ==
		 * ShortMessage.PROGRAM_CHANGE) { int c = m.getChannel();
		 * System.out.println("NoteFilterTransceiver.send: Channel "+c+" patch "+m.getData1()); } } if (message
		 * instanceof LotroShortMessage) { LotroShortMessage m = (LotroShortMessage) message; if (m.getCommand() ==
		 * ShortMessage.PROGRAM_CHANGE) { int c = m.getChannel();
		 * System.out.println("++NoteFilterTransceiver.send: Channel "+c+" patch "+m.getData1()); } }
		 */
		receiver.send(message, timeStamp);
	}
}
