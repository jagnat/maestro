package com.digero.maestro.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

public class ConvertPPQ {

	private static int halfRequirement = 4;

	public static Sequence convert(Sequence orig) {
		if (orig.getDivisionType() != Sequence.PPQ) {
			// System.out.println("PPQ old");
			return orig;
		}

		int origPPQ = orig.getResolution();

		int halfTimes = 0;
		int tempResult = origPPQ;
		for (int i = halfTimes; i <= halfRequirement; i++) {
			if (tempResult % 2 == 0) {
				tempResult /= 2;
				halfTimes++;
			} else {
				break;
			}
		}

		int doubleTimes = 0;
		if (halfTimes < halfRequirement) {
			doubleTimes = halfRequirement - halfTimes;
		} else {
			// System.out.println("PPQ fine . Old="+origPPQ);
			return orig;
		}

		int multi = 1 << doubleTimes; // (int)Math.pow(2, doubleTimes);

		int newPPQ = origPPQ * multi;

		while (newPPQ > 3000) {
			// Protection against too high PPQ
			doubleTimes -= 1;
			if (doubleTimes < 1) {
				// System.out.println("PPQ high . Old="+origPPQ);
				return orig;
			}
			multi = 1 << doubleTimes;
			newPPQ = origPPQ * multi;
		}

		// System.out.println("PPQ scaling. Old="+origPPQ+" New="+newPPQ);

		Sequence edit = null;
		try {
			edit = new Sequence(Sequence.PPQ, (int) newPPQ);
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
			return orig;
		}

		Track[] origTracks = orig.getTracks();

		for (Track origTrack : origTracks) {
			Track editTrack = edit.createTrack();
			int eventSize = origTrack.size();
			for (int j = 0; j < eventSize; j++) {
				MidiEvent origEvent = origTrack.get(j);
				origEvent.setTick(origEvent.getTick() * multi);
				editTrack.add(origEvent);
			}
		}

		return edit;
	}
}