package com.digero.common.midi;

import java.util.BitSet;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import com.digero.common.util.ICompileConstants;
import com.digero.maestro.abc.LotroCombiDrumInfo;

/**
 * The purpose of this class is to facilitate drum track soloing of specific notes.
 */
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
		// Handle solo for Xtra notes - also add the two underlying 'real' notes to the solo set
		if (LotroCombiDrumInfo.noteIdIsXtraNote(drumId)) {
			int id1 = LotroCombiDrumInfo.firstNotes.get(Note.fromId(drumId)).id;
			int id2 = LotroCombiDrumInfo.secondNotes.get(Note.fromId(drumId)).id;
			solos.set(id1, solo);
			solos.set(id2, solo);
		}

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

	/**
	 * This method is very time consuming considering its inserted into midi core message loop.
	 * There must be some way to skip this code if no notes have been soloed. ~Aifel
	 */
	@Override
	public void send(MidiMessage message, long timeStamp) {
		if (receiver == null)
			return;

		if (hasAbcPart && message instanceof ShortMessage) {
			ShortMessage m = (ShortMessage) message;
			int cmd = m.getCommand();
			if (cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF) {

				int c = m.getChannel();
				int noteId = m.getData1();
				int velocity = m.getData2();
				boolean noteOnMsg = (cmd == ShortMessage.NOTE_ON) && velocity > 0;
				boolean noteOffMsg = (cmd == ShortMessage.NOTE_OFF) || (cmd == ShortMessage.NOTE_ON && velocity == 0);

				if (!isNoteActive(noteId) && !(isNoteOn(c, noteId) && noteOffMsg))
					return;

				if (noteOnMsg)
					setNoteOn(c, noteId, true);
				else if (noteOffMsg)
					setNoteOn(c, noteId, false);
			}
		}
		
		receiver.send(message, timeStamp);
	}
}
