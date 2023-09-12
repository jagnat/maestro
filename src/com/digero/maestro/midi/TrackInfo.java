package com.digero.maestro.midi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import com.digero.common.midi.ExtensionMidiInstrument;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiInstrument;
import com.digero.common.midi.Note;
import com.digero.common.midi.TimeSignature;

/**
 * Create NoteEvents from MIDI note ON/OFF messages
 */
public class TrackInfo implements MidiConstants
{
	private SequenceInfo sequenceInfo;

	private int trackNumber;
	private String name;
	private TimeSignature timeSignature = null;// The first one in this track
	private KeySignature keySignature = null;
	private Set<Integer> instruments;
	private Set<String> instrumentExtensions;
	private List<NoteEvent> noteEvents;
	private SortedSet<Integer> notesInUse;// Used for knowing which drum sounds to display in DrumPanel
	private boolean isDrumTrack;
	private boolean isXGDrumTrack;
	private boolean isGSDrumTrack;
	private boolean isGM2DrumTrack;
	private final int minVelocity;
	private final int maxVelocity;
	
	@SuppressWarnings("unchecked")//
	TrackInfo(SequenceInfo parent, Track track, int trackNumber, SequenceDataCache sequenceCache, boolean isXGDrumTrack, boolean isGSDrumTrack, boolean wasType0, boolean isDrumsTrack, boolean isGM2DrumTrack, TreeMap<Integer, Integer> portMap)
			throws InvalidMidiDataException
	{
		this.sequenceInfo = parent;
		this.trackNumber = trackNumber;
		
		this.isXGDrumTrack = isXGDrumTrack;
		this.isGSDrumTrack = isGSDrumTrack;
		this.isGM2DrumTrack = isGM2DrumTrack;
		
		if (isXGDrumTrack || isGSDrumTrack || isDrumsTrack || isGM2DrumTrack) {
			isDrumTrack = true;
			
			// No need? Separated drum tracks already have their name. Type 0 channel tracks can keep their 'Track x', or?
			if (wasType0) {
				if (isXGDrumTrack) {
					name = ExtensionMidiInstrument.TRACK_NAME_DRUM_XG;
				} else if (isGSDrumTrack) {
					name = ExtensionMidiInstrument.TRACK_NAME_DRUM_GS;
				} else if (isGM2DrumTrack) {
					name = ExtensionMidiInstrument.TRACK_NAME_DRUM_GM2;
				} else {
					name = ExtensionMidiInstrument.TRACK_NAME_DRUM_GM;
				}
			}
		}
		
		instruments = new HashSet<>();
		instrumentExtensions = new HashSet<>();
		noteEvents = new ArrayList<>();
		notesInUse = new TreeSet<>();
		List<NoteEvent>[] notesOn = new List[CHANNEL_COUNT_ABC];
		int notesNotTurnedOff = 0;

		int minVelocity = Integer.MAX_VALUE;
		int maxVelocity = Integer.MIN_VALUE;
		
		
		int[] pitchBend = new int[CHANNEL_COUNT_ABC];
		
		
		
		List<BentNoteEvent> allBentNotes = new ArrayList<>();
		
		long tick = -10000000;
		for (int j = 0, sz = track.size(); j < sz; j++) {
 			MidiEvent evt = track.get(j);
 			MidiMessage msg = evt.getMessage();
 			
			if (evt.getTick() != tick && !isDrumTrack) {
				// Moving to new tick, lets process bends since the last tick
				for (int ch = 0; ch < CHANNEL_COUNT_ABC; ch++) {
					// Lets get all bends that happened since last tick, excluding the current tick
					Set<Entry<Long, Integer>> entries = sequenceCache.getBendMap().getEntries(ch, tick, evt.getTick());
					for (Entry<Long, Integer> entry : entries) {
						int bend = entry.getValue();
						long bendTick = entry.getKey();
						if (bend != pitchBend[ch]) {
							List<NoteEvent> bentNotes = new ArrayList<>();
							if (notesOn[ch] != null) {
								for (NoteEvent ne : notesOn[ch]) {
									if (!(ne instanceof BentNoteEvent)) {
										// This note is playing while this bend happens
										// Lets convert it to a BentNoteEvent
										BentNoteEvent be = new BentNoteEvent(ne.note, ne.velocity, ne.getStartTick(), ne.getEndTick(), ne.getTempoCache());
										allBentNotes.add(be);
										be.setMidiPan(ne.midiPan);
										be.addBend(ne.getStartTick(), 0);// we need this initial bend in NoteGraph class
										noteEvents.remove(ne);
										ne = be;
										noteEvents.add(ne);
									}
									if (((BentNoteEvent)ne).getBend(bendTick) != bend) {
										// The if statement prevents double bend commands,
										// which will make an extra split. 
										((BentNoteEvent)ne).addBend(bendTick, bend);
									}
									bentNotes.add(ne);
								}
								notesOn[ch] = bentNotes;
							}
							pitchBend[ch] = bend;
						}
					}
				}
			}
			tick = evt.getTick();
			
			if (msg instanceof ShortMessage)
			{
				ShortMessage m = (ShortMessage) msg;
				int cmd = m.getCommand();
				int c = m.getChannel();
				
				
				/*if (isXGDrumTrack || isGSDrumTrack) {
					//
				} else if (noteEvents.isEmpty() && cmd == ShortMessage.NOTE_ON)
					isDrumTrack = (c == DRUM_CHANNEL);
				else if (isDrumTrack != (c == DRUM_CHANNEL) && cmd == ShortMessage.NOTE_ON)
					System.err.println("Track "+trackNumber+" contains both notes and drums.."+(name!=null?name:""));
				*/
				if (notesOn[c] == null)
					notesOn[c] = new ArrayList<>();

				if (cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF)
				{
					int noteId = m.getData1();
					int velocity = m.getData2() * sequenceCache.getVolume(c, tick) / DEFAULT_CHANNEL_VOLUME;
					if (velocity > 127)
						velocity = 127;
					
					/*if (trackNumber == 2) {
						System.err.println();
						System.err.println("Tick: "+evt.getTick());
						System.err.println(cmd==ShortMessage.NOTE_ON?"NOTE ON":(cmd==ShortMessage.NOTE_OFF?"NOTE OFF":cmd));
						System.err.println("Channel: "+c);
						System.err.println("Velocity: "+velocity);
						System.err.println("Pitch: "+noteId);
					}*/
					
					// If this Note ON was preceded by a similar Note ON without a Note OFF, lets turn it off
					// If its a Note OFF or Note ON with zero velocity, lets do same.
					Iterator<NoteEvent> iter = notesOn[c].iterator();
					while (iter.hasNext())
					{
						NoteEvent ne = iter.next();
						if (ne.note.id == noteId)
						{
							iter.remove();
							ne.setEndTick(tick);
							break;
						}
					}
					
					if (cmd == ShortMessage.NOTE_ON && velocity > 0)
					{
						Note note = Note.fromId(noteId);
						if (note == null)
						{
							continue; // Note was probably bent out of range. Not great, but not a reason to fail.
						}

						NoteEvent ne = new NoteEvent(note, velocity, tick, tick, sequenceCache);
						if (!isDrumTrack && sequenceCache.getBendMap().get(c, tick) != 0) {
							// pitch bend active in channel already when note starts
							BentNoteEvent be = new BentNoteEvent(note, velocity, tick, tick, sequenceCache);
							allBentNotes.add(be);
							be.addBend(tick, sequenceCache.getBendMap().get(c, tick));
							ne = be;
						}
						ne.setMidiPan(sequenceCache.getPanMap().get(c, tick));// We don't set this in NoteEvent constructor as only MIDI notes will get this set, abc notes not.
						
						Iterator<NoteEvent> onIter = notesOn[c].iterator();
						while (onIter.hasNext())
						{
							NoteEvent on = onIter.next();
							if (on.note.id == ne.note.id)
							{
								onIter.remove();
								noteEvents.remove(on);
								notesNotTurnedOff++;
								break;
							}
						}

						if (velocity > maxVelocity)
							maxVelocity = velocity;
						if (velocity < minVelocity)
							minVelocity = velocity;

						instrumentExtensions.add(sequenceCache.getInstrumentExt(c, tick, isDrumTrack));
						if (!isDrumTrack) {
							instruments.add(sequenceCache.getInstrument(portMap.get(trackNumber), c, tick));
						}
						noteEvents.add(ne);
						notesInUse.add(ne.note.id);
						notesOn[c].add(ne);
					}
				}
			}
			else if (msg instanceof MetaMessage)
			{
				MetaMessage m = (MetaMessage) msg;
				int type = m.getType();

				if (type == META_TRACK_NAME && name == null)
				{
					byte[] data = m.getData();// Text that starts with any of these indicate charset: "@LATIN", "@JP", "@UTF-16LE", or "@UTF-16BE"
					String tmp = new String(data, 0, data.length, StandardCharsets.US_ASCII).trim();//"UTF-8"
					if (tmp.length() > 0 && !tmp.equalsIgnoreCase("untitled")
							&& !tmp.equalsIgnoreCase("WinJammer Demo")) {
						//System.out.println("Starts with @ "+data[0]+" "+(data[0] & 0xFF));

						/*String pattern = "\u000B";// Vertical tab in unicode
						Pattern r = Pattern.compile(pattern);
						Matcher match = r.matcher(tmp);
						tmp = match.replaceAll(" ");*/

						name = tmp;
					}
				}
				else if (type == META_KEY_SIGNATURE && keySignature == null)
				{
					keySignature = new KeySignature(m);
				}
				else if (type == META_TIME_SIGNATURE && timeSignature == null)
				{
					try {
						timeSignature = new TimeSignature(m);
					} catch (InvalidMidiDataException e) {
						// Ignore the illegal message
					}
				}
			}
		}
		
		for (BentNoteEvent be : allBentNotes) {
			// All bent notes that span more than an octave will
			// already here be split into small pieces.
			if (Math.abs(be.getMinBend() - be.getMaxBend()) > 12) {// TODO: Make this 12 into a misc setting: (-1, 6, 12, 24)
				List<NoteEvent> prematureSplit = be.split();
				noteEvents.addAll(prematureSplit);
				noteEvents.remove(be);
			}
		}

		// Turn off notes that are on at the end of the song.  This shouldn't happen...
		int ctNotesOn = 0;
		for (List<NoteEvent> notesOnChannel : notesOn)
		{
			if (notesOnChannel != null)
				ctNotesOn += notesOnChannel.size();
		}
		if (ctNotesOn > 0)
		{
			System.err.println((ctNotesOn + notesNotTurnedOff) + " note(s) not turned off at the end of the track.");

			for (List<NoteEvent> notesOnChannel : notesOn)
			{
				if (notesOnChannel != null)
					noteEvents.removeAll(notesOnChannel);
			}
		}

		if (minVelocity == Integer.MAX_VALUE)
			minVelocity = 0;
		if (maxVelocity == Integer.MIN_VALUE)
			maxVelocity = MidiConstants.MAX_VOLUME;

		this.minVelocity = minVelocity;
		this.maxVelocity = maxVelocity;

		noteEvents = Collections.unmodifiableList(noteEvents);
		notesInUse = Collections.unmodifiableSortedSet(notesInUse);
		instruments = Collections.unmodifiableSet(instruments);
	}
	
	public SequenceInfo getSequenceInfo()
	{
		return sequenceInfo;
	}

	public int getTrackNumber()
	{
		return trackNumber;
	}

	public boolean hasName()
	{
		return name != null;
	}

	public String getName()
	{
		if (name == null)
			return "Track " + trackNumber;
		return name;
	}

	public KeySignature getKeySignature()
	{
		return keySignature;
	}

	public TimeSignature getTimeSignature()
	{
		return timeSignature;
	}

	@Override public String toString()
	{
		return getName();
	}

	public boolean isDrumTrack()
	{
		return isDrumTrack;
	}

	/** Gets an unmodifiable list of the note events in this track. */
	public List<NoteEvent> getEvents()
	{
		return noteEvents;
	}

	public boolean hasEvents()
	{
		return !noteEvents.isEmpty();
	}

	public SortedSet<Integer> getNotesInUse()
	{
		return notesInUse;
	}

	public int getEventCount()
	{
		return noteEvents.size();
	}

	public String getEventCountString()
	{
		if (getEventCount() == 1)
		{
			return "1 note";
		}
		return getEventCount() + " notes";
	}

	public String getInstrumentNames()
	{
		if (isDrumTrack) {
						
			StringBuilder names = new StringBuilder();
			boolean first = true;
			
			for (String i : instrumentExtensions)
			{
				if (i == null) break;
				if (!first)
					names.append(", ");
				else
					first = false;

				names.append(i);
			}
			if (names.length() == 0) return MidiInstrument.STANDARD_DRUM_KIT;
			
			return names.toString();
		}
		
		if (instruments.isEmpty())
		{
			if (hasEvents())
				return MidiInstrument.PIANO.name;
			else
				return "<None>";
		}

		StringBuilder names = new StringBuilder();
		boolean first = true;
		
		if (!isGM()) {// Due to Maestro only supporting port assignments for GM, we make sure to use the GM instr. names for GM. 
			for (String i : instrumentExtensions)
			{
				if (i == null) break;
				if (!first)
					names.append(", ");
				else
					first = false;
	
				names.append(i);
			}
		}
		if (names.length() == 0) {
			first = true;		
			for (int i : instruments)
			{
				if (!first)
					names.append(", ");
				else
					first = false;
	
				names.append(MidiInstrument.fromId(i).name);
			}
		}

		return names.toString();
	}
	
	private boolean isGM() {
		return sequenceInfo.getDataCache().isGM();
	}

	public int getInstrumentCount()
	{
		return instruments.size();
	}

	public Set<Integer> getInstruments()
	{
		return instruments;
	}

	public int getMinVelocity()
	{
		return minVelocity;
	}

	public int getMaxVelocity()
	{
		return maxVelocity;
	}
}