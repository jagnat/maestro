package com.digero.maestro.abc;

import org.w3c.dom.Element;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiConstants;

public class PassThroughFXNoteMap extends StudentFXNoteMap {
	@Override
	protected byte getDefaultMapping(byte noteId) {
		if (noteId >= LotroInstrument.STUDENT_FIDDLE.lowestPlayable.id && noteId <= LotroInstrument.STUDENT_FX_HIGHEST.id)
			return noteId;
		else
			return DISABLED_NOTE_ID;
	}

	@Override
	public byte[] getFailsafeDefault() {
		byte[] failsafe = new byte[MidiConstants.NOTE_COUNT];

		for (int i = 0; i < failsafe.length; i++) {
			failsafe[i] = getDefaultMapping((byte) i);
		}

		return failsafe;
	}

	@Override
	public void saveToXml(Element ele) {
		ele.setAttribute("isPassthrough", String.valueOf(true));
		super.saveToXml(ele);
	}
}
