package com.digero.common.abc;

import com.digero.common.midi.Note;

public interface AbcConstants
{
	// Chord
	public static final int MAX_CHORD_NOTES = 6;

	// TimingInfo
	public static final int ONE_SECOND_MICROS = 1000000;
	public static final int ONE_MINUTE_MICROS = 60 * ONE_SECOND_MICROS;
	public static final int SHORTEST_NOTE_MICROS = ONE_MINUTE_MICROS / 1000;// Some times LOTRO will play this short a note, sometimes not..
	public static final int LONGEST_NOTE_MICROS = 8 * ONE_SECOND_MICROS;
	public static final double SHORTEST_NOTE_SECONDS = 0.06;// LOTRO will accept this short note duration except at 30, 60, 90 and 120 bpm.
	public static final double LONGEST_NOTE_SECONDS = 8.0;//This limits goes for rests also
	public static final int LONGEST_NOTE_MICROS_WORST_CASE = (2 * SHORTEST_NOTE_MICROS - 1)
			* (LONGEST_NOTE_MICROS / (2 * SHORTEST_NOTE_MICROS - 1));
	public static final int MAX_TEMPO = ONE_MINUTE_MICROS / SHORTEST_NOTE_MICROS;
	public static final int MIN_TEMPO = (ONE_MINUTE_MICROS + LONGEST_NOTE_MICROS / 2) / LONGEST_NOTE_MICROS; // Round up

	// Modifications to the ABC note lengths to sound more like the instruments in the game
	public static final double NON_SUSTAINED_NOTE_HOLD_SECONDS = 1.1;
	public static final double SUSTAINED_NOTE_HOLD_SECONDS = 0.06;//A little hold to get the release to sound more like lotros linear. Lowered to 0.06s from 0.1s in 2.5.0
	public static final double NOTE_RELEASE_SECONDS = 0.5;// Sadly this is linear 0.5s dB release, not linear 0.2s power release like in lotro.

	// MIDI Preview controller values
	public static final int MIDI_REVERB = 0;//Changed to 0 from 3 in 2.5.0
	public static final int MIDI_CHORUS = 0;

	/** Note ID used in ABC files for Cowbells. Somewhat arbitrary */
	public static final int COWBELL_NOTE_ID = 71;

	/** The highest Note ID for bagpipe drones */
	public static final int BAGPIPE_LAST_DRONE_NOTE_ID = Note.B2.id;

	/** The highest Note ID for the student fiddle "flub" notes */
	public static final int STUDENT_FIDDLE_LAST_FLUB_NOTE_ID = Note.Fs2.id;
}
