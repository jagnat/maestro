package com.digero.common.abctomidi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.AbcField;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiFactory;
import com.digero.common.midi.MidiUtils;
import com.digero.common.midi.Note;
import com.digero.common.midi.PanGenerator;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.LotroParseException;
import com.digero.common.util.ParseException;
import com.digero.maestro.abc.AbcExporter.ExportTrackInfo;
import com.digero.maestro.midi.SequenceInfo;

public class AbcToMidi {
	/** This is a static-only class */
	private AbcToMidi() {
	}

	public static class Params {
		public List<FileAndData> filesData;

		public boolean useLotroInstruments = true;
		public Map<Integer, LotroInstrument> instrumentOverrideMap = null;
		public boolean enableLotroErrors = false;
		public boolean stereo = true;
		public boolean generateRegions = false;
		public AbcInfo abcInfo = null;

		public Params(File file) throws IOException {
			this.filesData = new ArrayList<>();
			this.filesData.add(new FileAndData(file, readLines(file)));
		}

		public Params(List<FileAndData> filesData) {
			this.filesData = filesData;
		}
	}

	private static final Pattern INFO_PATTERN = Pattern.compile("^([A-Z]):\\s*(.*)\\s*$");
	private static final int INFO_TYPE = 1;
	private static final int INFO_VALUE = 2;

	private static final Pattern XINFO_PATTERN = Pattern.compile("^\\s*%%([A-Za-z\\-]+)((:?)|\\s)\\s*(.*)\\s*$");
	private static final int XINFO_FIELD = 1;
	private static final int XINFO_COLON = 3;
	private static final int XINFO_VALUE = 4;

	private static final Pattern NOTE_PATTERN = Pattern.compile("(_{1,2}|=|\\^{1,2})?" + "([xzA-Ga-g])"
			+ "(,{1,5}|'{1,5})?" + "(\\d+)?" + "(//?\\d*)?" + "(>{1,3}|<{1,3})?" + "(-)?");
	private static final int NOTE_ACCIDENTAL = 1;
	private static final int NOTE_LETTER = 2;
	private static final int NOTE_OCTAVE = 3;
	private static final int NOTE_LEN_NUMER = 4;
	private static final int NOTE_LEN_DENOM = 5;
	private static final int NOTE_BROKEN_RHYTHM = 6;
	private static final int NOTE_TIE = 7;

	/**
	 * Maps a note name (a, b, c, etc.) to the number of semitones it is above the beginning of the octave (c)
	 */
	private static final int[] CHR_NOTE_DELTA = { 9, 11, 0, 2, 4, 5, 7 };

	// Lots of prime factors for divisibility goodness
	static final long DEFAULT_NOTE_TICKS = (2 * 2 * 2 * 2 * 2 * 2) * (3 * 3) * 5;

	public static List<String> readLines(File inputFile) throws IOException {
		try (FileInputStream fileInputStream = new FileInputStream(inputFile);
				InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
				BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

			String line;
			ArrayList<String> lines = new ArrayList<>();
			while ((line = bufferedReader.readLine()) != null) {
				lines.add(line);
			}
			return lines;
		}
	}

	public static Sequence convert(Params params) throws ParseException {
		return convert(params.filesData, params.useLotroInstruments, params.instrumentOverrideMap, params.abcInfo,
				params.enableLotroErrors, params.stereo, params.generateRegions);
	}

	private static Sequence convert(List<FileAndData> filesData, boolean useLotroInstruments,
			Map<Integer, LotroInstrument> instrumentOverrideMap, AbcInfo abcInfo, final boolean enableLotroErrors,
			final boolean stereo, final boolean generateRegions) throws ParseException {
		if (abcInfo == null)
			abcInfo = new AbcInfo();
		else
			abcInfo.reset();

		TuneInfo info = new TuneInfo();
		Sequence seq = null;
		Track track = null;

		int channel = 0;
		int trackNumber = 0;
		int trackIndex = 0;
		int noteDivisorChangeLine = 0;

		int chordStartIndex = 0;
		double chordStartTick = 0;
		double chordEndTick = 0;
		long PPQN = 0;
		Map<Integer, AbcRegion> tiedRegions = new HashMap<>();

		Map<Integer, Integer> tiedNotes = new HashMap<>(); // noteId => (line << 16) | column
		Map<Integer, Integer> accidentals = new HashMap<>(); // noteId => deltaNoteId

		List<MidiEvent> noteOffEvents = new ArrayList<>();
		int lineNumberForRegions = -1;
		SequenceInfo.lastTrackInfos = new ArrayList<>();
		for (FileAndData fileAndData : filesData) {
			track = null;
			String fileName = fileAndData.file.getName();
			abcInfo.addSourceFile(fileAndData.file);
			int lineNumber = 0;
			int partStartLine = 0;
			for (String line : fileAndData.lines) {
				lineNumberForRegions++;
				lineNumber++;

				// Handle extended info
				Matcher xInfoMatcher = XINFO_PATTERN.matcher(line);
				if (xInfoMatcher.matches()) {
					AbcField field = AbcField
							.fromString(xInfoMatcher.group(XINFO_FIELD) + xInfoMatcher.group(XINFO_COLON));

					if (field == AbcField.TEMPO) {
						try {
							info.addTempoEvent(Math.round(chordStartTick), xInfoMatcher.group(XINFO_VALUE).trim());
						} catch (IllegalArgumentException e) {
							// Apparently that wasn't actually a tempo change
						}
					} else if (field != null) {
						String value = xInfoMatcher.group(XINFO_VALUE).trim();

						abcInfo.setExtendedMetadata(field, value);

						if (field == AbcField.PART_NAME) {
							info.setTitle(value, true);
							abcInfo.setPartName(trackNumber, value, true);

							if (instrumentOverrideMap == null || !instrumentOverrideMap.containsKey(trackNumber)) {
								LotroInstrument instrument = LotroInstrument.findInstrumentName(value, null);
								if (!info.isInstrumentDefinitiveSet() && instrument != null)
									info.setInstrument(instrument, false);
							}
						} else if (field == AbcField.MADE_FOR) {
							if (instrumentOverrideMap == null || !instrumentOverrideMap.containsKey(trackNumber)) {
								LotroInstrument instrument = LotroInstrument.findInstrumentName(value, null);
								if (instrument != null)
									info.setInstrument(instrument, true);
							}
						}
					}

					continue;
				}

				int comment = line.indexOf('%');
				if (comment >= 0)
					line = line.substring(0, comment);
				if (line.trim().length() == 0)
					continue;

				int chordSize = 0;

				Matcher infoMatcher = INFO_PATTERN.matcher(line);
				if (infoMatcher.matches()) {
					char type = Character.toUpperCase(infoMatcher.group(INFO_TYPE).charAt(0));
					String value = infoMatcher.group(INFO_VALUE).trim();

					abcInfo.setMetadata(type, value);

					try {
						switch (type) {
						case 'X':
							for (int lineAndColumn : tiedNotes.values()) {
								throw new ParseException("Tied note does not connect to another note", fileName,
										lineAndColumn >>> 16, lineAndColumn & 0xFFFF);
							}

							accidentals.clear();
							noteOffEvents.clear();

							if (trackNumber > 0)
								abcInfo.setPartEndLine(trackNumber, lineNumberForRegions - 1);

							info.newPart(Integer.parseInt(value));
							trackNumber++;
							partStartLine = lineNumber;
							chordStartTick = 0;
							abcInfo.setPartNumber(trackNumber, info.getPartNumber());
							abcInfo.setPartStartLine(trackNumber, lineNumberForRegions);
							track = null; // Will create a new track after the header is done
							if (instrumentOverrideMap != null && instrumentOverrideMap.containsKey(trackNumber)) {
								info.setInstrument(instrumentOverrideMap.get(trackNumber), false);
							}
							break;
						case 'T':
							if (track != null) {
								throw new ParseException("Can't specify the title in the middle of a part", fileName,
										lineNumber, 0);
							}

							info.setTitle(value, false);
							abcInfo.setPartName(trackNumber, value, false);
							if (instrumentOverrideMap == null || !instrumentOverrideMap.containsKey(trackNumber)) {
								if (!info.isInstrumentSet()) {
									LotroInstrument instrument = LotroInstrument.findInstrumentName(value, null);
									if (instrument != null)
										info.setInstrument(instrument, false);
								}
							}
							break;
						case 'K':
							info.setKey(value);
							break;
						case 'L':
							info.setNoteDivisor(value);
							noteDivisorChangeLine = lineNumber;
							break;
						case 'M':
							info.setMeter(value);
							noteDivisorChangeLine = lineNumber;
							break;
						case 'Q': {
							int tempo = info.getPrimaryTempoBPM();
							info.setPrimaryTempoBPM(value);
							if (seq != null && (info.getPrimaryTempoBPM() != tempo)) {
								throw new ParseException("The tempo must be the same for all parts of the song",
										fileName, lineNumber);
							}
							break;
						}
						}
					} catch (IllegalArgumentException e) {
						throw new ParseException(e.getMessage(), fileName, lineNumber, infoMatcher.start(INFO_VALUE));
					}
				} else {
					// The line contains notes

					if (trackNumber == 0) {
						// This ABC file doesn't have an "X:" line before notes. Tsk tsk.
						trackNumber = 1;
						if (instrumentOverrideMap != null && instrumentOverrideMap.containsKey(trackNumber)) {
							info.setInstrument(instrumentOverrideMap.get(trackNumber), false);
						}
					}

					if (seq == null) {
						try {
							PPQN = info.getPpqn();
							seq = new Sequence(Sequence.PPQ, (int) PPQN);

							abcInfo.setPrimaryTempoBPM(info.getPrimaryTempoBPM());

							// Create track 0, which will later be filled with the
							// tempo events and song metadata (title, etc.)
							trackIndex = 0;
							seq.createTrack();

							abcInfo.setPartNumber(0, 0);
							abcInfo.setPartName(0, info.getTitle(), false);
							abcInfo.setTimeSignature(info.getMeter());
							abcInfo.setKeySignature(info.getKey());

							track = null;
						} catch (InvalidMidiDataException mde) {
							throw new ParseException("Midi Error: " + mde.getMessage(), fileName);
						}
					}

					if (track == null) {
						trackIndex = seq.getTracks().length;
						channel = getTrackChannel(trackIndex);
						if (channel > MidiConstants.CHANNEL_COUNT_ABC - 1) {
							throw new ParseException(
									"Too many parts (max = " + (MidiConstants.CHANNEL_COUNT_ABC - 1) + ")", fileName,
									partStartLine);
						}
						track = seq.createTrack();
						track.add(MidiFactory.createLotroChangeEvent(info.getInstrument().midi.id(), channel, 0));
						SequenceInfo.lastTrackInfos
								.add(new ExportTrackInfo(0, null, null, channel, info.getInstrument().midi.id()));
						if (useLotroInstruments) {
							track.add(MidiFactory.createChannelVolumeEvent(MidiConstants.MAX_VOLUME, channel, 1));
							track.add(MidiFactory.createReverbControlEvent(AbcConstants.MIDI_REVERB, channel, 1));
							track.add(MidiFactory.createChorusControlEvent(AbcConstants.MIDI_CHORUS, channel, 1));
						}

						abcInfo.setPartInstrument(trackNumber, info.getInstrument());

					}

					Matcher m = NOTE_PATTERN.matcher(line);
					int i = 0;
					boolean inChord = false;
					Tuplet tuplet = null;
					int brokenRhythmNumerator = 1; // The numerator of the note after the broken rhythm sign
					int brokenRhythmDenominator = 1; // The denominator of the note after the broken rhythm sign
					while (true) {
						boolean found = m.find(i);
						int parseEnd = found ? m.start() : line.length();
						// Parse anything that's not a note
						for (; i < parseEnd; i++) {
							char ch = line.charAt(i);
							if (Character.isWhitespace(ch)) {
								if (inChord) {
									throw new ParseException("Unexpected whitespace inside a chord", fileName,
											lineNumber, i);
								}
								continue;
							}

							switch (ch) {
							case '[': // Chord start
								if (inChord) {
									throw new ParseException("Unexpected '" + ch + "' inside a chord", fileName,
											lineNumber, i);
								}

								if (brokenRhythmDenominator != 1 || brokenRhythmNumerator != 1) {
									throw new ParseException("Can't have broken rhythm (< or >) within a chord",
											fileName, lineNumber, i);
								}

								chordSize = 0;
								inChord = true;
								chordStartIndex = i;
								break;

							case ']': // Chord end
								if (!inChord) {
									throw new ParseException("Unexpected '" + ch + "'", fileName, lineNumber, i);
								}
								inChord = false;

								if (generateRegions) {
									abcInfo.addRegion(new AbcRegion(lineNumberForRegions, chordStartIndex, i + 1,
											Math.round(chordStartTick), Math.round(chordEndTick), null, trackIndex));
								}

								chordStartTick = chordEndTick;
								break;

							case '|': // Bar line
								if (inChord) {
									throw new ParseException("Unexpected '" + ch + "' inside a chord", fileName,
											lineNumber, i);
								}

								if (trackNumber == 1)
									abcInfo.addBar(Math.round(chordStartTick));

								accidentals.clear();
								if (i + 1 < line.length() && (line.charAt(i + 1) == ']' || line.charAt(i+1) == ':')) {
									i++; // Skip |], |:
								} else if (trackNumber == 1) {
									abcInfo.addBar(Math.round(chordStartTick));
								}
								break;
							
							case ':': // Beginning of repeat end bar line :| ::| :::::::|
								if (inChord) {
									throw new ParseException("Unexpected '" + ch + "' inside a chord", fileName,
											lineNumber, i);
								}

								boolean foundPipe = false;
								for (int j = i + 1; j < parseEnd; j++) {
									if (line.charAt(j) == '|') {
										i = j; // Skip past :::::| (legal in lotro, so we should support it.. even though lotro doesn't support |::)
										foundPipe = true;
										if (trackNumber == 1)
											abcInfo.addBar(Math.round(chordStartTick));
										break;
									}
								}
								
								if (!foundPipe) {
									throw new ParseException("Expected to see '|' after parsing '" + ch + "'", fileName,
											lineNumber, i);
								}
								
								break;

							case '+': {
								int j = line.indexOf('+', i + 1);
								if (j < 0) {
									throw new ParseException("There is no matching '+'", fileName, lineNumber, i);
								}
								try {
									info.setDynamics(line.substring(i + 1, j));
								} catch (IllegalArgumentException iae) {
									throw new ParseException("Unsupported +decoration+", fileName, lineNumber, i);
								}

								if (enableLotroErrors && inChord) {
									throw new LotroParseException("Can't include a +decoration+ inside a chord",
											fileName, lineNumber, i);
								}

								i = j;
								break;
							}

							case '(':
								// Tuplet or slur start
								if (i + 1 < line.length() && Character.isDigit(line.charAt(i + 1))) {
									// If it has a digit following it, it's a tuplet
									if (tuplet != null) {
										throw new ParseException("Unexpected '" + ch + "' before end of tuplet",
												fileName, lineNumber, i);
									}

									try {
										for (int j = i + 1; j < line.length(); j++) {
											if (line.charAt(j) != ':' && !Character.isDigit(line.charAt(j))) {
												tuplet = new Tuplet(line.substring(i + 1, j), info.isCompoundMeter());
												i = j - 1;
												break;
											}
										}
									} catch (IllegalArgumentException e) {
										throw new ParseException("Invalid tuplet", fileName, lineNumber, i);
									}
								} else {
									// Otherwise it's a slur, which LotRO conveniently ignores
									if (inChord) {
										throw new ParseException("Unexpected '" + ch + "' inside a chord", fileName,
												lineNumber, i);
									}
								}
								break;

							case ')':
								// End of a slur, ignore
								if (inChord) {
									throw new ParseException("Unexpected '" + ch + "' inside a chord", fileName,
											lineNumber, i);
								}
								break;

							case '\\':
								// Ignore backslashes
								break;

							default:
								throw new ParseException("Unknown/unexpected character '" + ch + "'", fileName,
										lineNumber, i);
							}
						}

						if (i >= line.length())
							break;

						// The matcher might find +f+, +ff+, or +fff+ and think it's a note
						if (i > m.start())
							continue;

						if (inChord)
							chordSize++;

						if (enableLotroErrors && inChord && chordSize > AbcConstants.MAX_CHORD_NOTES) {
							throw new LotroParseException("Too many notes in a chord", fileName, lineNumber, m.start());
						}

						// Parse the note
						int numerator;
						int denominator;
						int numerator_abc;
						int denominator_abc;

						numerator = (m.group(NOTE_LEN_NUMER) == null) ? 1 : Integer.parseInt(m.group(NOTE_LEN_NUMER));
						String denom = m.group(NOTE_LEN_DENOM);
						if (denom == null)
							denominator = 1;
						else if (denom.equals("/"))
							denominator = 2;
						else if (denom.equals("//"))
							denominator = 4;
						else
							denominator = Integer.parseInt(denom.substring(1));

						String abcNoteL = "";
						if (m.group(NOTE_LEN_NUMER) != null) {
							abcNoteL = m.group(NOTE_LEN_NUMER);
						}
						if (m.group(NOTE_LEN_DENOM) != null) {
							abcNoteL += m.group(NOTE_LEN_DENOM);
						}
						String abcNoteAcc = "";
						if (m.group(NOTE_ACCIDENTAL) != null) {
							abcNoteAcc = m.group(NOTE_ACCIDENTAL);
						}

						numerator_abc = numerator;
						denominator_abc = denominator;

						String brokenRhythm = m.group(NOTE_BROKEN_RHYTHM);
						if (brokenRhythm != null) {
							if (brokenRhythmDenominator != 1 || brokenRhythmNumerator != 1) {
								throw new ParseException("Invalid broken rhythm: " + brokenRhythm, fileName, lineNumber,
										m.start(NOTE_BROKEN_RHYTHM));
							}
							if (inChord) {
								throw new ParseException("Can't have broken rhythm (< or >) within a chord", fileName,
										lineNumber, m.start(NOTE_BROKEN_RHYTHM));
							}
							if (m.group(NOTE_TIE) != null) {
								throw new ParseException("Tied notes can't have broken rhythms (< or >)", fileName,
										lineNumber, m.start(NOTE_BROKEN_RHYTHM));
							}

							int factor = 1 << brokenRhythm.length();

							if (brokenRhythm.charAt(0) == '>') {
								numerator *= 2 * factor - 1;
								denominator *= factor;
								brokenRhythmDenominator = factor;
							} else {
								brokenRhythmNumerator = 2 * factor - 1;
								brokenRhythmDenominator = factor;
								denominator *= factor;
							}
						} else {
							numerator *= brokenRhythmNumerator;
							denominator *= brokenRhythmDenominator;
							brokenRhythmNumerator = 1;
							brokenRhythmDenominator = 1;
						}

						if (tuplet != null) {
							if (!inChord || chordSize == 1)
								tuplet.r--;
							numerator *= tuplet.q;
							denominator *= tuplet.p;
							if (tuplet.r == 0)
								tuplet = null;
						}

						// Convert back to the original tempo
						int curTempoBPM = info.getCurrentTempoBPM(Math.round(chordStartTick));
						int primaryTempoBPM = info.getPrimaryTempoBPM();
						numerator *= curTempoBPM;
						denominator *= primaryTempoBPM;

						// Try to guess if this note is using triplet timing
						if ((denominator % 3 == 0) && (numerator % 3 != 0)) {
							abcInfo.setHasTriplets(true);
						}

						double noteEndTick = chordStartTick
								+ DEFAULT_NOTE_TICKS * (double) numerator / (double) denominator;

						// A chord is as long as its shortest note
						if (chordEndTick == chordStartTick || noteEndTick < chordEndTick)
							chordEndTick = noteEndTick;

						char noteLetter = m.group(NOTE_LETTER).charAt(0);
						String octaveStr = m.group(NOTE_OCTAVE);
						if (octaveStr == null)
							octaveStr = "";
						if (noteLetter == 'z' || noteLetter == 'x') {
							if (m.group(NOTE_ACCIDENTAL) != null && m.group(NOTE_ACCIDENTAL).length() > 0) {
								throw new ParseException("Unexpected accidental on a rest", fileName, lineNumber,
										m.start(NOTE_ACCIDENTAL));
							}
							if (octaveStr.length() > 0) {
								throw new ParseException("Unexpected octave indicator on a rest", fileName, lineNumber,
										m.start(NOTE_OCTAVE));
							}

							double lengthSeconds = info.getWholeNoteTime() * (numerator_abc / (double) denominator_abc);

							throwExceptionsIfEnabled(enableLotroErrors, fileName, lineNumber, m, abcNoteL, noteLetter,
									lengthSeconds, info.getPrimaryTempoBPM());

							if (generateRegions) {
								abcInfo.addRegion(new AbcRegion(lineNumberForRegions, m.start(), m.end(),
										Math.round(chordStartTick), Math.round(noteEndTick), Note.REST, trackIndex));
							}
						} else {
							int octave = Character.isUpperCase(noteLetter) ? 3 : 4;
							if (octaveStr.indexOf('\'') >= 0)
								octave += octaveStr.length();
							else if (octaveStr.indexOf(',') >= 0)
								octave -= octaveStr.length();

							int noteId;
							int lotroNoteId;

							lotroNoteId = noteId = (octave + 1) * 12
									+ CHR_NOTE_DELTA[Character.toLowerCase(noteLetter) - 'a'];
							if (!useLotroInstruments)
								noteId += 12 * info.getInstrument().octaveDelta;

							if (m.group(NOTE_ACCIDENTAL) != null) {
								if (m.group(NOTE_ACCIDENTAL).startsWith("_"))
									accidentals.put(noteId, -m.group(NOTE_ACCIDENTAL).length());
								else if (m.group(NOTE_ACCIDENTAL).startsWith("^"))
									accidentals.put(noteId, m.group(NOTE_ACCIDENTAL).length());
								else if (m.group(NOTE_ACCIDENTAL).equals("="))
									accidentals.put(noteId, 0);
							}

							int noteDelta;
							if (accidentals.containsKey(noteId)) {
								noteDelta = accidentals.get(noteId);
							} else {
								// Use the key signature to determine the accidental
								noteDelta = info.getKey().getDefaultAccidental(noteId).deltaNoteId;
							}
							lotroNoteId += noteDelta;
							noteId += noteDelta;

							if (enableLotroErrors && lotroNoteId < Note.MIN_PLAYABLE.id)
								throw new LotroParseException("Note is too low", fileName, lineNumber, m.start());
							else if (enableLotroErrors && lotroNoteId > Note.MAX_PLAYABLE.id)
								throw new LotroParseException("Note is too high", fileName, lineNumber, m.start());

							if (info.getInstrument() == LotroInstrument.BASIC_COWBELL
									|| info.getInstrument() == LotroInstrument.MOOR_COWBELL) {
								if (useLotroInstruments) {
									// Randomize the noteId unless it's part of a note tie
									if (m.group(NOTE_TIE) == null && !tiedNotes.containsKey(noteId)) {
										int min = info.getInstrument().lowestPlayable.id;
										int max = info.getInstrument().highestPlayable.id;
										lotroNoteId = noteId = min + (int) (Math.random() * (max - min));
									}
								} else {
									noteId = (info.getInstrument() == LotroInstrument.BASIC_COWBELL) ? 76 : 71;
									lotroNoteId = AbcConstants.COWBELL_NOTE_ID;
								}
							}

							// Check for overlapping notes, and remove extra note off events
							Iterator<MidiEvent> noteOffIter = noteOffEvents.iterator();
							while (noteOffIter.hasNext()) {
								MidiEvent evt = noteOffIter.next();
								if (evt.getTick() <= chordStartTick) {
									noteOffIter.remove();
									continue;
								}

								int noteOffId = ((ShortMessage) evt.getMessage()).getData1();
								if (noteOffId == noteId) {
									track.remove(evt);
									evt.setTick(Math.round(chordStartTick));
									track.add(evt);
									noteOffIter.remove();
									break;
								}
							}

							if (generateRegions) {
								AbcRegion region = new AbcRegion(lineNumberForRegions, m.start(), m.end(),
										Math.round(chordStartTick), Math.round(noteEndTick), Note.fromId(noteId),
										trackIndex);

								abcInfo.addRegion(region);

								AbcRegion tiesFrom = tiedRegions.get(noteId);
								if (tiesFrom != null) {
									region.setTiesFrom(tiesFrom);
									tiesFrom.setTiesTo(region);
								}

								if (m.group(NOTE_TIE) != null)
									tiedRegions.put(noteId, region);
								else
									tiedRegions.remove(noteId);
							}

							if (!tiedNotes.containsKey(noteId)) {
								if (info.getPpqn() != PPQN) {
									throw new ParseException(
											"The default note length must be the same for all parts of the song",
											fileName, noteDivisorChangeLine);
								}
								track.add(MidiFactory.createNoteOnEventEx(noteId, channel,
										info.getDynamics().getVol(useLotroInstruments), Math.round(chordStartTick)));
							}

							handleNoteTie(useLotroInstruments, enableLotroErrors, info, track, channel, PPQN, tiedNotes,
									noteOffEvents, fileName, lineNumber, m, numerator_abc, denominator_abc, abcNoteL,
									abcNoteAcc, curTempoBPM, noteEndTick, noteLetter, octaveStr, noteId, lotroNoteId);
						}

						if (!inChord)
							chordStartTick = noteEndTick;
						i = m.end();
					}

					if (tuplet != null)
						throw new ParseException("Tuplet not finished by end of line", fileName, lineNumber, i);

					if (inChord)
						throw new ParseException("Chord not closed at end of line", fileName, lineNumber, i);

					if (brokenRhythmDenominator != 1 || brokenRhythmNumerator != 1)
						throw new ParseException("Broken rhythm unfinished at end of line", fileName, lineNumber, i);
				}
			}

			if (seq == null)
				throw new ParseException("The file contains no notes", fileName, lineNumber);

			for (int lineAndColumn : tiedNotes.values()) {
				throw new ParseException("Tied note does not connect to another note", fileName, lineAndColumn >>> 16,
						lineAndColumn & 0xFFFF);
			}
		}

		abcInfo.setPartEndLine(trackNumber, lineNumberForRegions);

		PanGenerator pan = null;
		if (stereo && trackNumber > 1)
			pan = new PanGenerator();

		Track[] tracks = seq.getTracks();

		// Add tempo events
		for (Map.Entry<Long, Integer> tempoEvent : info.getAllPartsTempoMap().entrySet()) {
			long tick = tempoEvent.getKey();
			int mpq = (int) MidiUtils.convertTempo(tempoEvent.getValue());
			tracks[0].add(MidiFactory.createTempoEvent(mpq, tick));
		}

		// Add name and pan events
		tracks[0].add(MidiFactory.createTrackNameEvent(abcInfo.getTitle()));
		for (int i = 1; i <= trackNumber; i++) {
			tracks[i].add(MidiFactory.createTrackNameEvent(abcInfo.getPartName(i)));

			int panAmount = PanGenerator.CENTER;
			if (pan != null)
				panAmount = pan.get(abcInfo.getPartInstrument(i), abcInfo.getPartName(i));
			tracks[i].add(MidiFactory.createPanEvent(panAmount, getTrackChannel(i)));
		}

		// Add time and key signature events
		tracks[0].add(MidiFactory.createTimeSignatureEvent(abcInfo.getTimeSignature(), 0));
		if (MidiFactory.isSupportedMidiKeyMode(abcInfo.getKeySignature().mode))
			tracks[0].add(MidiFactory.createKeySignatureEvent(abcInfo.getKeySignature(), 0));

		return seq;
	}

	private static void handleNoteTie(boolean useLotroInstruments, final boolean enableLotroErrors, TuneInfo info,
			Track track, int channel, long PPQN, Map<Integer, Integer> tiedNotes, List<MidiEvent> noteOffEvents,
			String fileName, int lineNumber, Matcher m, int numerator_abc, int denominator_abc, String abcNoteL,
			String abcNoteAcc, int curTempoBPM, double noteEndTick, char noteLetter, String octaveStr, int noteId,
			int lotroNoteId) throws LotroParseException {
		if (m.group(NOTE_TIE) != null) {
			double lengthSeconds = info.getWholeNoteTime() * (numerator_abc / (double) denominator_abc);

			throwExceptionsIfEnabled(enableLotroErrors, fileName, lineNumber, m, abcNoteL, abcNoteAcc, noteLetter,
					octaveStr, lengthSeconds, true, info.getPrimaryTempoBPM());
			int lineAndColumn = (lineNumber << 16) | m.start();
			tiedNotes.put(noteId, lineAndColumn);
		} else {
			double MPQN = MidiUtils.convertTempo(curTempoBPM);
			// double lengthMicros = (noteEndTick - chordStartTick) * MPQN / PPQN;
			double lengthSeconds = info.getWholeNoteTime() * (numerator_abc / (double) denominator_abc);

			throwExceptionsIfEnabled(enableLotroErrors, fileName, lineNumber, m, abcNoteL, abcNoteAcc, noteLetter,
					octaveStr, lengthSeconds, false, info.getPrimaryTempoBPM());

			// Lengthen to match the note lengths used in the game
			double noteEndTickTmp = noteEndTick;
			if (useLotroInstruments) {
				boolean sustainable = info.getInstrument().isSustainable(lotroNoteId);
				double extraSeconds = sustainable ? AbcConstants.SUSTAINED_NOTE_HOLD_SECONDS
						: AbcConstants.NON_SUSTAINED_NOTE_HOLD_SECONDS;

				noteEndTickTmp += extraSeconds * AbcConstants.ONE_SECOND_MICROS * PPQN / MPQN;
			}
			MidiEvent noteOff = MidiFactory.createNoteOffEventEx(noteId, channel,
					info.getDynamics().getVol(useLotroInstruments), Math.round(noteEndTickTmp));
			track.add(noteOff);
			noteOffEvents.add(noteOff);

			tiedNotes.remove(noteId);
		}
	}

	private static void throwExceptionsIfEnabled(final boolean enableLotroErrors, String fileName, int lineNumber,
			Matcher m, String abcNoteL, char noteLetter, double lengthSeconds, int bpm) throws LotroParseException {
		// Using double for lengthSeconds can result in rounding errors in 17 decimal
		// place.
		if (enableLotroErrors && ((float) lengthSeconds) < ((float) AbcConstants.SHORTEST_NOTE_SECONDS)) {
			throw new LotroParseException("Rest's duration is too short (" + String.format("%.3f", lengthSeconds)
					+ "s)(" + noteLetter + " " + abcNoteL + ")", fileName, lineNumber, m.start());
		} else if (enableLotroErrors && AbcConstants.getShortestNoteMicros(bpm) == 60001L && ((float) lengthSeconds) == ((float) AbcConstants.SHORTEST_NOTE_SECONDS)) {
			throw new LotroParseException("Rest's duration is too short (" + String.format("%.3f", lengthSeconds)
						+ "s)(" + noteLetter + " " + abcNoteL + ")", fileName, lineNumber, m.start());
		} else if (enableLotroErrors && lengthSeconds > AbcConstants.LONGEST_NOTE_SECONDS) {
			throw new LotroParseException("Rest's duration is too long (" + String.format("%.3f", lengthSeconds) + "s)("
					+ noteLetter + " " + abcNoteL + ")", fileName, lineNumber, m.start());
		}
	}

	private static void throwExceptionsIfEnabled(final boolean enableLotroErrors, String fileName, int lineNumber,
			Matcher m, String abcNoteL, String abcNoteAcc, char noteLetter, String octaveStr, double lengthSeconds,
			boolean shouldAddGroup, int bpm) throws LotroParseException {
		// Using double for lengthSeconds can result in rounding errors in 17 decimal
		// place.
		if (enableLotroErrors && ((float) lengthSeconds) < ((float) AbcConstants.SHORTEST_NOTE_SECONDS)) {
			throw new LotroParseException(
					"Note's duration is too short (" + String.format("%.3f", lengthSeconds) + "s)(" + abcNoteAcc
							+ noteLetter + octaveStr + abcNoteL + addGroup(m, shouldAddGroup) + ")",
					fileName, lineNumber, m.start());
		} else if (enableLotroErrors && AbcConstants.getShortestNoteMicros(bpm) == 60001L && ((float) lengthSeconds) == ((float) AbcConstants.SHORTEST_NOTE_SECONDS)) {
			throw new LotroParseException(
					"Note's duration is too short (" + String.format("%.3f", lengthSeconds) + "s)(" + abcNoteAcc
							+ noteLetter + octaveStr + abcNoteL + addGroup(m, shouldAddGroup) + ")",
					fileName, lineNumber, m.start());
		} else if (enableLotroErrors && lengthSeconds > AbcConstants.LONGEST_NOTE_SECONDS) {
			throw new LotroParseException(
					"Note's duration is too long (" + String.format("%.3f", lengthSeconds) + "s)(" + abcNoteAcc
							+ noteLetter + octaveStr + abcNoteL + addGroup(m, shouldAddGroup) + ")",
					fileName, lineNumber, m.start());
		}
	}

	private static String addGroup(Matcher m, boolean shouldReturn) {
		if (shouldReturn) {
			return m.group(NOTE_TIE);
		}
		return "";
	}

	/**
	 * @deprecated This doesn't work if changing between sustained and non-sustained instruments
	 */
	@Deprecated
	public static void updateInstrumentRealtime(SequencerWrapper sequencer, int trackIndex,
			LotroInstrument instrument) {
		Sequence sequence = sequencer.getSequence();
		if (sequence == null)
			return;

		Track[] tracks = sequencer.getSequence().getTracks();
		if (tracks == null || trackIndex < 0 || trackIndex >= tracks.length)
			return;

		Track track = tracks[trackIndex];

		// Try to find the existing program change event
		ShortMessage programChange = null;
		for (int j = 0; j < track.size(); j++) {
			MidiEvent evt = track.get(j);
			if (evt.getMessage() instanceof ShortMessage) {
				ShortMessage m = (ShortMessage) evt.getMessage();
				if (m.getCommand() == ShortMessage.PROGRAM_CHANGE) {
					programChange = m;
					break;
				}
			}
		}

		if (programChange == null)
			return;

		// Update the program change event and resend it to the sequencer's receiver
		MidiFactory.modifyProgramChangeMessage(programChange, instrument.midi.id());

		Receiver receiver = sequencer.getReceiver();
		if (receiver != null) {
			receiver.send(programChange, -1);

			// Turn off any currently-playing notes
			ShortMessage noteOff = new ShortMessage();
			for (int i = MidiConstants.LOWEST_NOTE_ID; i <= MidiConstants.HIGHEST_NOTE_ID; i++) {
				try {
					noteOff.setMessage(ShortMessage.NOTE_OFF, programChange.getChannel(), i, 0);
					receiver.send(noteOff, -1);
				} catch (InvalidMidiDataException e) {
				}
			}
		}
	}

	private static int getTrackChannel(int trackNumber) {
		if (trackNumber < MidiConstants.DRUM_CHANNEL + 1)
			return trackNumber - 1;

		return trackNumber;
	}
	
	// Used for ABC Player playlist to read metadata only from ABC to populate playlist view
	public static AbcInfo parseAbcMetadata(List<FileAndData> abc) throws ParseException {
		AbcInfo abcInfo = new AbcInfo();
		int trackNumber = 0;
		String fileName = null;
		for (FileAndData fileAndData : abc) {
			fileName = fileAndData.file.getName();
			abcInfo.addSourceFile(fileAndData.file);
			int lineNumber = 0;
			int partStartLine = 0;
			
			for (String line : fileAndData.lines) {
				lineNumber++;
				
				Matcher xInfoMatcher = XINFO_PATTERN.matcher(line);
				if (xInfoMatcher.matches()) {
					AbcField field = AbcField.fromString(xInfoMatcher.group(XINFO_FIELD) + xInfoMatcher.group(XINFO_COLON));
					if (field == AbcField.TEMPO) {
						continue;
					} else if (field != null) {
						String value = xInfoMatcher.group(XINFO_VALUE).trim();
						abcInfo.setExtendedMetadata(field, value);
						if (field == AbcField.PART_NAME) {
							abcInfo.setPartName(trackNumber, value, true);
						}
					}
					continue;
				}
				
				Matcher infoMatcher = INFO_PATTERN.matcher(line);
				if (infoMatcher.matches()) {
					char type = Character.toUpperCase(infoMatcher.group(INFO_TYPE).charAt(0));
					String value = infoMatcher.group(INFO_VALUE).trim();

					abcInfo.setMetadata(type, value);
					
					try {
						switch(type) {
						case 'X': // New part
							trackNumber++;
							abcInfo.setPartNumber(trackNumber,  Integer.parseInt(value));
							abcInfo.setPartStartLine(trackNumber, lineNumber);
							break;
						case 'T':
							abcInfo.setPartName(trackNumber, value, false);
							break;
						default:
							break;
						}
					} catch (IllegalArgumentException e) {
							throw new ParseException(e.getMessage(), fileName, lineNumber, infoMatcher.start(INFO_VALUE));
					}
				}
			}
		}
		
		if (abcInfo.isEmpty()) {
			throw new ParseException("Empty or invalid ABC files", fileName);
		}
		
		
		return abcInfo;
	}

	// From http://abcnotation.com/abc2mtex/abc.txt:
	//
	// Duplets, triplets, quadruplets, etc.
	// ====================================
	// These can be simply coded with the notation (2ab for a duplet,
	// (3abc for a triplet or (4abcd for a quadruplet, etc., up to (9.
	// The musical meanings are:
	//
	// (2 2 notes in the time of 3
	// (3 3 notes in the time of 2
	// (4 4 notes in the time of 3
	// (5 5 notes in the time of n
	// (6 6 notes in the time of 2
	// (7 7 notes in the time of n
	// (8 8 notes in the time of 3
	// (9 9 notes in the time of n
	//
	// If the time signature is compound (3/8, 6/8, 9/8, 3/4, etc.) then
	// n is three, otherwise n is two.
	//
	// More general tuplets can be specified using the syntax (p:q:r
	// which means `put p notes into the time of q for the next r
	// notes'. If q is not given, it defaults as above. If r is not
	// given, it defaults to p. For example, (3:2:2 is equivalent to
	// (3::2 and (3:2:3 is equivalent to (3:2 , (3 or even (3:: . This
	// can be useful to include notes of different lengths within a
	// tuplet, for example (3:2:2G4c2 or (3:2:4G2A2Bc and also describes
	// more precisely how the simple syntax works in cases like (3D2E2F2
	// or even (3D3EF2. The number written over the tuplet is p.
	private static class Tuplet {
		public int p;
		public int q;
		public int r;

		public Tuplet(String str, boolean compoundMeter) {
			try {
				String[] parts = str.split(":");
				if (parts.length < 1 || parts.length > 3)
					throw new IllegalArgumentException();

				p = Integer.parseInt(parts[0]);

				if (p < 2 || p > 9)
					throw new IllegalArgumentException();

				if (parts.length >= 2 && parts[1].length() > 0)
					q = Integer.parseInt(parts[1]);
				else if (p == 3 || p == 6)
					q = 2;
				else if (p == 2 || p == 4 || p == 8)
					q = 3;
				else if (p == 5 || p == 7 || p == 9)
					q = compoundMeter ? 3 : 2;
				else
					throw new IllegalArgumentException();

				if (parts.length >= 3)
					r = Integer.parseInt(parts[2]);
				else
					r = p;
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(e);
			}
		}
		
		@Override
		public String toString() {
			return "("+p+":"+q+":"+r;
		}
	}
	
	static {
		//
		// Unit test for tuplets
		// TODO: Include compound time boolean in tests
		//
		String tests[][] = {{"3","(3:2:3"},{"3:2:","(3:2:3"},{"3:2","(3:2:3"},{"3::2","(3:2:2"},{"3::","(3:2:3"}};
		for(String pair[] : tests) {
			Tuplet tuplet = new Tuplet(pair[0], false);
			boolean pass = tuplet.toString().equals(pair[1]);
			if (!pass) System.out.println("\nTuplet unit test:  ("+pair[0]+" => "+tuplet+"   PASS = "+pass+"\n\n");
			assert pass;
		}
	}
}
