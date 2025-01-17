package com.digero.maestro.abc;

import static java.awt.Frame.getFrames;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.NavigableMap;

import javax.sound.midi.InvalidMidiDataException;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.StringCleaner;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.TimeSignature;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.ListenerList;
import com.digero.common.util.ParseException;
import com.digero.common.util.Util;
import com.digero.common.util.Version;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.AbcSongEvent.AbcSongProperty;
import com.digero.maestro.abc.QuantizedTimingInfo.TimingInfoEvent;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.midi.TrackInfo;
import com.digero.maestro.util.FileResolver;
import com.digero.maestro.util.ListModelWrapper;
import com.digero.maestro.util.SaveUtil;
import com.digero.maestro.util.XmlUtil;
import com.digero.maestro.view.InstrNameSettings;
import com.digero.maestro.view.MiscSettings;

public class AbcSong implements IDiscardable, AbcMetadataSource {
	public static final String MSX_FILE_DESCRIPTION = MaestroMain.APP_NAME + " Song";
	public static final String MSX_FILE_DESCRIPTION_PLURAL = MaestroMain.APP_NAME + " Songs";
	public static final String MSX_FILE_EXTENSION_NO_DOT = "msx";
	public static final String MSX_FILE_EXTENSION = "." + MSX_FILE_EXTENSION_NO_DOT;
	public static final Version SONG_FILE_VERSION = new Version(3, 4, 3, 300);// Keep build above 117 to make earlier
																				// Maestro releases know msx is
																				// made by newer version.

	private String title = "";
	private String composer = "";
	private String transcriber = "";
	private String genre = "";
	private String mood = "";
	private String note = "";// not continuously updated
	private boolean badger = false;
	private boolean allOut = false;
	private float tempoFactor = 1.0f;
	private int transpose = 0;
	private KeySignature keySignature = KeySignature.C_MAJOR;
	private TimeSignature timeSignature = TimeSignature.FOUR_FOUR;
	private boolean tripletTiming = false;
	private boolean mixTiming = true;
	private int mixVersion = 2;// TODO: make UI?
	private boolean priorityActive = false;
	private boolean skipSilenceAtStart = true;
	private boolean deleteMinimalNotes = false;
	// private boolean showPruned = false;
	public NavigableMap<Float, TuneLine> tuneBars = null;
	public boolean[] tuneBarsModified = null;
	private Float firstBar = null;
	private Float lastBar = null;
	private long firstBarTick = -1L;
	private long lastBarTick = -1L;

	private final boolean fromAbcFile;
	private final boolean fromXmlFile;
	private SequenceInfo sequenceInfo;// TODO: Refactor name to sourceSequenceInfo
	private final PartAutoNumberer partAutoNumberer;
	private final PartNameTemplate partNameTemplate;
	private final ExportFilenameTemplate exportFilenameTemplate;
	private final InstrNameSettings instrNameSettings;
	private QuantizedTimingInfo timingInfo;
	private AbcExporter abcExporter;
	private File sourceFile; // The MIDI or ABC file that this song was loaded from
	private File exportFile; // The ABC export file
	private File saveFile; // The XML Maestro song file
	private boolean usingOldVelocities = false;
	private boolean hideEdits = false;

	private final ListModelWrapper<AbcPart> parts = new ListModelWrapper<>(new DefaultListModel<>());

	private final ListenerList<AbcSongEvent> listeners = new ListenerList<>();
	boolean mixDirty = true;
	private Date firstExportTime = null;// UTC date and time for first time this project was exported to abc.

	public AbcSong(File file, PartAutoNumberer partAutoNumberer, PartNameTemplate partNameTemplate,
			ExportFilenameTemplate exportFilenameTemplate, InstrNameSettings instrNameSettings,
			FileResolver fileResolver, MiscSettings miscSettings)
			throws IOException, InvalidMidiDataException, ParseException, SAXException {
		sourceFile = file;

		this.partAutoNumberer = partAutoNumberer;
		this.partAutoNumberer.setParts(Collections.unmodifiableList(parts));

		this.partNameTemplate = partNameTemplate;
		this.partNameTemplate.setMetadataSource(this);

		this.exportFilenameTemplate = exportFilenameTemplate;
		this.exportFilenameTemplate.setMetadataSource(this);

		this.instrNameSettings = instrNameSettings;

		String fileName = file.getName().toLowerCase();
		fromXmlFile = fileName.endsWith(MSX_FILE_EXTENSION);
		fromAbcFile = fileName.endsWith(".abc") || fileName.endsWith(".txt");

		if (fromXmlFile)
			initFromXml(file, fileResolver, miscSettings);
		else if (fromAbcFile)
			initFromAbc(file, miscSettings);
		else
			initFromMidi(file, miscSettings);
	}

	@Override
	public void discard() {
		fireChangeEvent(AbcSongProperty.SONG_CLOSING);

		if (partAutoNumberer != null)
			partAutoNumberer.setParts(null);

		if (partNameTemplate != null)
			partNameTemplate.setMetadataSource(null);

		listeners.discard();

		for (AbcPart part : parts) {
			if (part != null)
				part.discard();
		}
		parts.clear();

		tuneBarsModified = null;
		tuneBars = null;
		firstBar = null;
		lastBar = null;
		
		hideEdits = false;

		/*
		 * if (sequenceInfo != null) { // Make life easier for Garbage Collector for (TrackInfo ti :
		 * sequenceInfo.getTrackList()) { for (NoteEvent ne : ti.getEvents()) { ne.resetAllPruned(); } } }
		 */
	}

	private void initFromMidi(File file, MiscSettings miscSettings)
			throws IOException, InvalidMidiDataException, ParseException {
		usingOldVelocities = miscSettings.ignoreExpressionMessages;
		sequenceInfo = SequenceInfo.fromMidi(file, miscSettings, usingOldVelocities);
		title = sequenceInfo.getTitle();
		composer = sequenceInfo.getComposer();
		keySignature = (ICompileConstants.SHOW_KEY_FIELD) ? sequenceInfo.getKeySignature() : KeySignature.C_MAJOR;
		timeSignature = sequenceInfo.getTimeSignature();
		note = "";
	}

	private void initFromAbc(File file, MiscSettings miscSettings)
			throws IOException, InvalidMidiDataException, ParseException {
		AbcInfo abcInfo = new AbcInfo();

		AbcToMidi.Params params = new AbcToMidi.Params(file);
		params.abcInfo = abcInfo;
		params.useLotroInstruments = false;
		// params.stereo = false;
		usingOldVelocities = true;// The abc volumes are tuned to old volume scheme
		sequenceInfo = SequenceInfo.fromAbc(params, miscSettings, usingOldVelocities);
		exportFile = file;

		title = sequenceInfo.getTitle();
		composer = sequenceInfo.getComposer();
		keySignature = (ICompileConstants.SHOW_KEY_FIELD) ? abcInfo.getKeySignature() : KeySignature.C_MAJOR;
		timeSignature = abcInfo.getTimeSignature();

		int t = 0;
		for (TrackInfo trackInfo : sequenceInfo.getTrackList()) {
			if (!trackInfo.hasEvents()) {
				t++;
				continue;
			}

			AbcPart newPart = new AbcPart(this);

			newPart.setTitle(abcInfo.getPartName(t));
			newPart.setPartNumber(abcInfo.getPartNumber(t));
			newPart.setTrackEnabled(t, true);

			Set<Integer> midiInstruments = trackInfo.getInstruments();
			for (LotroInstrument lotroInst : LotroInstrument.values()) {
				if (midiInstruments.contains(lotroInst.midi.id())) {
					newPart.setInstrument(lotroInst);
					break;
				}
			}
			if (newPart.getInstrument() == LotroInstrument.STUDENT_FIDDLE) {
				newPart.setStudentOverride(true);
			}

			int ins = Collections.binarySearch(parts, newPart, partNumberComparator);
			if (ins < 0)
				ins = -ins - 1;
			parts.add(ins, newPart);
			
			newPart.addAbcListener(abcPartListener);
			t++;
		}

		tripletTiming = abcInfo.hasTriplets();
		mixTiming = abcInfo.hasMixTimings();
		priorityActive = false;
		transcriber = abcInfo.getTranscriber();
		genre = abcInfo.getGenre();
		mood = abcInfo.getMood();
		note = "";
	}

	private void initFromXml(File file, FileResolver fileResolver, MiscSettings miscSettings)
			throws SAXException, IOException, ParseException {
		try {
			saveFile = file;
			Document doc = XmlUtil.openDocument(sourceFile);
			Element songEle = XmlUtil.selectSingleElement(doc, "song");
			if (songEle == null) {
				throw new ParseException("Does not appear to be a valid Maestro file. Missing <song> root element.",
						sourceFile.getName());
			}
			Version fileVersion = SaveUtil.parseValue(songEle, "@fileVersion", SONG_FILE_VERSION);

			if (isFileNewer(fileVersion) && getFrames().length > 0) {
				JOptionPane.showMessageDialog(getFrames()[0],
						"This project may contain new features that this Maestro cannot use. It is suggested to upgrade this Maestro to load this project.",
						"Warning", JOptionPane.WARNING_MESSAGE);
			}

			usingOldVelocities = SaveUtil.parseValue(songEle, "importSettings/@useOldVelocities", true);// must be
																										// before
																										// tryToLoadFromFile

			sourceFile = SaveUtil.parseValue(songEle, "sourceFile", (File) null);
			if (sourceFile == null) {
				throw SaveUtil.missingValueException(songEle, "<sourceFile>");
			}
			File origSourceFile = sourceFile;

			exportFile = SaveUtil.parseValue(songEle, "exportFile", exportFile);

			sequenceInfo = null;
			while (sequenceInfo == null) {
				String name = sourceFile.getName().toLowerCase();
				boolean isAbc = name.endsWith(".abc") || name.endsWith(".txt");

				tryToLoadFromFile(fileResolver, isAbc, miscSettings);

				if (sourceFile == null)
					throw new ParseException("Failed to load file", name);
			}

			if (!sourceFile.equals(origSourceFile)) {
				MaestroMain.setMIDIFileResolved();
			}
			title = SaveUtil.parseValue(songEle, "title", sequenceInfo.getTitle());
			composer = SaveUtil.parseValue(songEle, "composer", sequenceInfo.getComposer());
			transcriber = SaveUtil.parseValue(songEle, "transcriber", transcriber);
			genre = SaveUtil.parseValue(songEle, "genre", genre);
			mood = SaveUtil.parseValue(songEle, "mood", mood);
			note = SaveUtil.parseValue(songEle, "note", "");
			
			String exportTimeStr = SaveUtil.parseValue(songEle, "firstExportTime", "");
			if (exportTimeStr != "") {
				DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
				df.setTimeZone(TimeZone.getTimeZone("GMT"));
				try {
					firstExportTime = df.parse(exportTimeStr);
				} catch (java.text.ParseException e) {					
				}
			} else if (exportFile != null) {
				// Project has been saved before, but we don't know when.
				firstExportTime = new Date(0);// 1970-01-01-00:00:00
			}
			
			if (fileVersion.compareTo(new Version(3, 3, 7, 300)) < 0) {
				int lastBarI = SaveUtil.parseValue(songEle, "lastBar", -1);
				if (lastBarI < 1) lastBar = null;
				else lastBar = (float)lastBarI;
				int firstBarI = SaveUtil.parseValue(songEle, "firstBar", -1);
				if (firstBarI < 1) firstBar = null;
				else firstBar = (float)(firstBarI - 1);
			} else {
				lastBar = SaveUtil.parseValue(songEle, "lastBar", -1.0f);
				if (lastBar < 0.0f) lastBar = null;
				firstBar = SaveUtil.parseValue(songEle, "firstBar", -1.0f);
				if (firstBar < 0.0f) firstBar = null;
			}

			tempoFactor = SaveUtil.parseValue(songEle, "exportSettings/@tempoFactor", tempoFactor);
			transpose = SaveUtil.parseValue(songEle, "exportSettings/@transpose", transpose);
			if (ICompileConstants.SHOW_KEY_FIELD)
				keySignature = SaveUtil.parseValue(songEle, "exportSettings/@keySignature", keySignature);
			timeSignature = SaveUtil.parseValue(songEle, "exportSettings/@timeSignature", timeSignature);
			tripletTiming = SaveUtil.parseValue(songEle, "exportSettings/@tripletTiming", tripletTiming);

			mixTiming = SaveUtil.parseValue(songEle, "exportSettings/@mixTiming", false);// default false as old
																							// projects did not have
																							// that available. This
																							// means for old project
																							// with source abc that was
																							// exported with mix
																							// timings, the project will
																							// decide and it will be
																							// false.

			// if (mixTiming)
			// mixVersion = SaveUtil.parseValue(songEle, "exportSettings/@mixVersion", 1);// default 1 as old projects
			// did not have that available.

			priorityActive = SaveUtil.parseValue(songEle, "exportSettings/@combinePriorities", false);

			handleTuneSections(songEle, fileVersion);

			addListenerToParts(songEle, fileVersion);
		} catch (XPathExpressionException e) {
			e.printStackTrace();
			throw new ParseException("XPath error: " + e.getMessage(), null);
		}
	}

	private boolean isFileNewer(Version fileVersion) {
		if (fileVersion.getMajor() == 1 && fileVersion.getMinor() == 0) {
			// Convert the msx 1.0.X format into Maestro version format.
			fileVersion = new Version(2, 5, 0, fileVersion.getRevision());
		}
		if (fileVersion.compareTo(SONG_FILE_VERSION) > 0) {
			return true;
		}
		return false;
	}

	private void tryToLoadFromFile(FileResolver fileResolver, boolean isAbc, MiscSettings miscSettings) {
		try {
			File sourceInCurrentDir = new File(saveFile.getParentFile(), sourceFile.getName());
			if (!sourceFile.exists() && sourceInCurrentDir.exists()) {
				sourceFile = sourceInCurrentDir;
			}
			
			if (isAbc) {
				AbcInfo abcInfo = new AbcInfo();

				AbcToMidi.Params params = new AbcToMidi.Params(sourceFile);
				params.abcInfo = abcInfo;
				params.useLotroInstruments = false;
				// params.stereo = false;
				usingOldVelocities = true;// The abc volumes are tuned to old volume scheme
				sequenceInfo = SequenceInfo.fromAbc(params, miscSettings, usingOldVelocities);

				tripletTiming = abcInfo.hasTriplets();
				mixTiming = abcInfo.hasMixTimings();
				priorityActive = false;
				transcriber = abcInfo.getTranscriber();
			} else {
				sequenceInfo = SequenceInfo.fromMidi(sourceFile, miscSettings, usingOldVelocities);
			}

			title = sequenceInfo.getTitle();
			composer = sequenceInfo.getComposer();
			keySignature = (ICompileConstants.SHOW_KEY_FIELD) ? sequenceInfo.getKeySignature() : KeySignature.C_MAJOR;
			timeSignature = sequenceInfo.getTimeSignature();
		} catch (FileNotFoundException e) {
			String msg = "Could not find the file used to create this song:\n" + sourceFile;
			sourceFile = fileResolver.locateFile(sourceFile, msg);
		} catch (InvalidMidiDataException | IOException | ParseException e) {
			String msg = "Could not load the file used to create this song:\n" + sourceFile + "\n\n" + e.getMessage();
			sourceFile = fileResolver.resolveFile(sourceFile, msg);
		}
	}
	
	public void convertTunelinesToLongs () {
		SequenceInfo se = getSequenceInfo();
		
		if (se == null) {
			throw new RuntimeException("Error in floating point tuneline");
		}
		
		SequenceDataCache data = se.getDataCache();
		long barLengthTicks = data.getBarLengthTicks();
		
		if (tuneBars != null) {
			for (TuneLine tuneLine : tuneBars.values()) {
				assert tuneLine.startTick == -1L;
				assert tuneLine.endTick == -1L;
				
				tuneLine.startTick = (long)(barLengthTicks * tuneLine.startBar);
				tuneLine.endTick   = (long)(barLengthTicks * tuneLine.endBar);// don't use ceil() here
			}
		}
		if (firstBar != null) {
			firstBarTick = (long)(barLengthTicks * firstBar);
		} else {
			firstBarTick = -1L;
		}
		if (lastBar != null) {
			lastBarTick = (long)(barLengthTicks * lastBar);
		} else {
			lastBarTick = -1L;
		}
	}

	/**
	 * Loading tuneline from xml
	 * 
	 * @param songElement
	 * @param fileVersion
	 * @throws XPathExpressionException
	 * @throws ParseException
	 */
	private void handleTuneSections(Element songElement, Version fileVersion) throws XPathExpressionException, ParseException {
		float lastEnd = 0;
		for (Element tuneEle : XmlUtil.selectElements(songElement, "tuneSection")) {
			TuneLine tl = new TuneLine();
			if (fileVersion.compareTo(new Version(3, 3, 4, 300)) < 0) {
				tl.startBar = SaveUtil.parseValue(tuneEle, "startBar", 0);
				tl.endBar = SaveUtil.parseValue(tuneEle, "endBar", 0);
				tl.startBar -= 1.0f;
			} else {
				tl.startBar = SaveUtil.parseValue(tuneEle, "startBar", 0.0f);
				tl.endBar = SaveUtil.parseValue(tuneEle, "endBar", 0.0f);
			}
			tl.seminoteStep = SaveUtil.parseValue(tuneEle, "seminoteStep", 0);
			tl.tempo = SaveUtil.parseValue(tuneEle, "tempoChange", 0);
			tl.accelerando = SaveUtil.parseValue(tuneEle, "tempoAccelerando", 0);
			int fade = SaveUtil.parseValue(tuneEle, "fade", 0);
			if (fade != 0) {
				tl.fade = fade;
			}
			tl.dialogLine = SaveUtil.parseValue(tuneEle, "dialogLine", -1);
			if (tl.startBar >= 0.0f && tl.endBar > tl.startBar) {
				if (tuneBars == null) {
					tuneBars = new TreeMap<>();
				}
				if (tl.endBar > lastEnd) {
					lastEnd = tl.endBar;
				}
				tuneBars.put(tl.startBar, tl);
			}
		}
		boolean[] booleanArray = new boolean[(int)(lastEnd) + 1];
		if (tuneBars != null) {
			for (int i = 0; i < (int)(lastEnd) + 1; i++) {
				Entry<Float, TuneLine> entry = tuneBars.lowerEntry(i + 1.0f);
				booleanArray[i] = entry != null && entry.getValue().startBar < i+1
						&& entry.getValue().endBar > i;
			}
			tuneBarsModified = booleanArray;
		}
		convertTunelinesToLongs();
	}

	private void addListenerToParts(Element songEle, Version fileVersion)
			throws XPathExpressionException, ParseException {
		for (Element ele : XmlUtil.selectElements(songEle, "part")) {
			AbcPart part = AbcPart.loadFromXml(this, ele, fileVersion);
			int ins = Collections.binarySearch(parts, part, partNumberComparator);
			if (ins < 0)
				ins = -ins - 1;
			parts.add(ins, part);
			part.convertSectionsToLongTrees();
			part.addAbcListener(abcPartListener);
		}
	}

	public String getNote() {
		// Only call this just after loading a msx file.
		return note;
	}

	public void setNote(String note) {
		// Only call this just before saving a msx file.
		this.note = note;
	}

	public Document saveToXml() {
		Document doc = XmlUtil.createDocument();
		doc.setXmlVersion("1.1");// This will allow project files with numerical chars to later be loaded fine.
									// Like "&#11;".
		Element songEle = (Element) doc.appendChild(doc.createElement("song"));
		songEle.setAttribute("fileVersion", String.valueOf(SONG_FILE_VERSION));
		songEle.setAttribute("maestroVersion", String.valueOf(MaestroMain.APP_VERSION));

		SaveUtil.appendChildTextElement(songEle, "sourceFile", String.valueOf(sourceFile));
		if (exportFile != null)
			SaveUtil.appendChildTextElement(songEle, "exportFile", String.valueOf(exportFile));

		SaveUtil.appendChildTextElement(songEle, "title", title);
		SaveUtil.appendChildTextElement(songEle, "composer", composer);
		SaveUtil.appendChildTextElement(songEle, "transcriber", transcriber);
		if (genre.length() > 0)
			SaveUtil.appendChildTextElement(songEle, "genre", genre);
		if (mood.length() > 0)
			SaveUtil.appendChildTextElement(songEle, "mood", mood);
		if (note.length() > 0)
			SaveUtil.appendChildTextElement(songEle, "note", note);
		if (firstExportTime != null && firstExportTime.getTime() != 0L) {
			DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
			SaveUtil.appendChildTextElement(songEle, "firstExportTime", df.format(firstExportTime));
		}

		appendImportSettings(doc, songEle);
		appendExportSettings(doc, songEle);

		if (tuneBars != null && tuneBarsModified != null) {
			appendTuneSections(doc, songEle);
		}
		if (lastBar != null) {
			SaveUtil.appendChildTextElement(songEle, "lastBar", String.valueOf(lastBar));
		}
		if (firstBar != null) {
			SaveUtil.appendChildTextElement(songEle, "firstBar", String.valueOf(firstBar));
		}

		for (AbcPart part : parts) {
			part.saveToXml((Element) songEle.appendChild(doc.createElement("part")));
		}

		return doc;
	}

	private void appendExportSettings(Document doc, Element songEle) {
		Element exportSettingsEle = doc.createElement("exportSettings");
		if (tempoFactor != 1.0f)
			exportSettingsEle.setAttribute("tempoFactor", String.valueOf(tempoFactor));
		if (transpose != 0)
			exportSettingsEle.setAttribute("transpose", String.valueOf(transpose));
		if (ICompileConstants.SHOW_KEY_FIELD) {
			if (!keySignature.equals(sequenceInfo.getKeySignature()))
				exportSettingsEle.setAttribute("keySignature", String.valueOf(keySignature));
		}
		if (!timeSignature.equals(sequenceInfo.getTimeSignature()))
			exportSettingsEle.setAttribute("timeSignature", String.valueOf(timeSignature));
		if (tripletTiming)
			exportSettingsEle.setAttribute("tripletTiming", String.valueOf(tripletTiming));
		exportSettingsEle.setAttribute("mixTiming", String.valueOf(mixTiming));
		if (mixTiming) {
			exportSettingsEle.setAttribute("combinePriorities", String.valueOf(priorityActive));
			// exportSettingsEle.setAttribute("mixVersion", String.valueOf(mixVersion));
		}
		if (exportSettingsEle.getAttributes().getLength() > 0 || exportSettingsEle.getChildNodes().getLength() > 0)
			songEle.appendChild(exportSettingsEle);
	}

	private void appendImportSettings(Document doc, Element songEle) {
		Element importSettingsEle = doc.createElement("importSettings");
		importSettingsEle.setAttribute("useOldVelocities", String.valueOf(usingOldVelocities));
		if (importSettingsEle.getAttributes().getLength() > 0 || importSettingsEle.getChildNodes().getLength() > 0)
			songEle.appendChild(importSettingsEle);
	}

	private void appendTuneSections(Document doc, Element songEle) {
		for (TuneLine tuneLine : tuneBars.values()) {
			Element tuneEle = (Element) songEle.appendChild(doc.createElement("tuneSection"));
			SaveUtil.appendChildTextElement(tuneEle, "startBar", String.valueOf(tuneLine.startBar));
			SaveUtil.appendChildTextElement(tuneEle, "endBar", String.valueOf(tuneLine.endBar));
			SaveUtil.appendChildTextElement(tuneEle, "seminoteStep", String.valueOf(tuneLine.seminoteStep));
			SaveUtil.appendChildTextElement(tuneEle, "tempoChange", String.valueOf(tuneLine.tempo));
			SaveUtil.appendChildTextElement(tuneEle, "tempoAccelerando", String.valueOf(tuneLine.accelerando));
			SaveUtil.appendChildTextElement(tuneEle, "fade", String.valueOf(tuneLine.fade));
			SaveUtil.appendChildTextElement(tuneEle, "dialogLine", String.valueOf(tuneLine.dialogLine));
		}
	}

	public void exportAbc(File exportFile) throws IOException, AbcConversionException {
		boolean delayEnabled = false;
		for (AbcPart part : parts) {
			if (part.delay != 0) {
				delayEnabled = true;
				break;
			}
		}
		try (FileOutputStream out = new FileOutputStream(exportFile)) {
			getAbcExporter().exportToAbc(out, delayEnabled);
			if (firstExportTime == null) firstExportTime = new Date();
		}
	}

	public AbcPart createNewPart() {
		AbcPart newPart = new AbcPart(this);
		newPart.addAbcListener(abcPartListener);
		partAutoNumberer.onPartAdded(newPart);

		int idx = Collections.binarySearch(parts, newPart, partNumberComparator);
		if (idx < 0)
			idx = (-idx - 1);
		parts.add(idx, newPart);

		setMixDirty(true);
		fireChangeEvent(AbcSongProperty.PART_ADDED, newPart);
		return newPart;
	}

	public void deletePart(AbcPart part) {
		if (part == null || !parts.contains(part))
			return;
		setMixDirty(true);
		fireChangeEvent(AbcSongProperty.BEFORE_PART_REMOVED, part);
		parts.remove(part);
		partAutoNumberer.onPartDeleted(part);
		part.discard();
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		title = Util.emptyIfNull(title);
		if (!this.title.equals(title)) {
			this.title = title;
			fireChangeEvent(AbcSongProperty.TITLE);
		}
	}

	@Override
	public String getGenre() {
		return genre;
	}

	public void setGenre(String genre) {
		genre = Util.emptyIfNull(genre);
		if (!this.genre.equals(genre)) {
			this.genre = genre;
			fireChangeEvent(AbcSongProperty.GENRE);
		}
	}

	@Override
	public String getMood() {
		return mood;
	}

	public void setMood(String mood) {
		mood = Util.emptyIfNull(mood);
		if (!this.genre.equals(mood)) {
			this.mood = mood;
			fireChangeEvent(AbcSongProperty.MOOD);
		}
	}

	@Override
	public String getComposer() {
		return composer;
	}

	public void setComposer(String composer) {
		composer = Util.emptyIfNull(composer);
		if (!this.composer.equals(composer)) {
			this.composer = composer;
			fireChangeEvent(AbcSongProperty.COMPOSER);
		}
	}

	@Override
	public String getAllParts() {
		if (!allOut) {
			return null;
		}
		String str = "N: TS  ";
		StringBuilder str2 = new StringBuilder();
		ListModelWrapper<AbcPart> prts = getParts();
		int count = 0;
		for (AbcPart prt : prts) {
			if (prt.getEnabledTrackCount() > 0) {
				count += 1;
				str2.append("  ").append(prt.getPartNumber());
			}
		}
		if (count == 0)
			return null;
		str += count + ", ";
		return str + str2;
	}

	@Override
	public int getActivePartCount() {
		// TODO: Cache this to not have to recalculate
		ListModelWrapper<AbcPart> prts = getParts();
		int counter = 0;
		for (AbcPart part : prts) {
			if (part.getEnabledTrackCount() > 0) {
				counter++;
			}
		}
		return counter;
	}
	
	public boolean isAnyPartsSoloed() {
		for (AbcPart part : getParts()) {
			if (part.isSoloed()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String getTranscriber() {
		return transcriber;
	}

	public void setTranscriber(String transcriber) {
		transcriber = Util.emptyIfNull(transcriber);
		if (!this.transcriber.equals(transcriber)) {
			this.transcriber = transcriber;
			fireChangeEvent(AbcSongProperty.TRANSCRIBER);
		}
	}

	public float getTempoFactor() {
		return tempoFactor;
	}

	public void setTempoFactor(float tempoFactor) {
		if (this.tempoFactor != tempoFactor) {
			this.tempoFactor = tempoFactor;
			fireChangeEvent(AbcSongProperty.TEMPO_FACTOR);
		}
	}

	/**
	 * Get the main export tempo.
	 * 
	 * @return bpm
	 */
	public int getTempoBPM() {
		return Math.round(tempoFactor * sequenceInfo.getPrimaryTempoBPM());
	}

	/**
	 * Set the the main tempo for export and preview.
	 * Will call setTempoFactor()
	 * 
	 * @param tempoBPM new tempo
	 */
	public void setTempoBPM(int tempoBPM) {
		setTempoFactor((float) tempoBPM / sequenceInfo.getPrimaryTempoBPM());
	}

	public int getTranspose() {
		return transpose;
	}

	public void setTranspose(int transpose) {
		if (this.transpose != transpose) {
			this.transpose = transpose;
			fireChangeEvent(AbcSongProperty.TRANSPOSE);
		}
	}

	public KeySignature getKeySignature() {
		if (ICompileConstants.SHOW_KEY_FIELD)
			return keySignature;
		else
			return KeySignature.C_MAJOR;
	}

	public void setKeySignature(KeySignature keySignature) {
		if (!ICompileConstants.SHOW_KEY_FIELD)
			keySignature = KeySignature.C_MAJOR;

		if (!this.keySignature.equals(keySignature)) {
			this.keySignature = keySignature;
			fireChangeEvent(AbcSongProperty.KEY_SIGNATURE);
		}
	}

	public TimeSignature getTimeSignature() {
		return timeSignature;
	}

	public void setTimeSignature(TimeSignature timeSignature) {
		if (!this.timeSignature.equals(timeSignature)) {
			this.timeSignature = timeSignature;
			fireChangeEvent(AbcSongProperty.TIME_SIGNATURE);
		}
	}

	public boolean isTripletTiming() {
		return tripletTiming;
	}

	public boolean isMixTiming() {
		return mixTiming;
	}

	public int getMixVersion() {
		return mixVersion;
	}

	public void setMixTiming(boolean mixTiming) {
		if (this.mixTiming != mixTiming) {
			this.mixTiming = mixTiming;
			fireChangeEvent(AbcSongProperty.MIX_TIMING);
		}
	}

	public void setMixVersion(int mixVersion) {
		if (this.mixVersion != mixVersion) {
			this.mixVersion = mixVersion;
			fireChangeEvent(AbcSongProperty.MIX_TIMING);// We can use same event as for mixtiming
		}
	}

	public void setTripletTiming(boolean tripletTiming) {
		if (this.tripletTiming != tripletTiming) {
			this.tripletTiming = tripletTiming;
			fireChangeEvent(AbcSongProperty.TRIPLET_TIMING);
		}
	}

	public boolean isSkipSilenceAtStart() {
		return skipSilenceAtStart;
	}

	public void setSkipSilenceAtStart(boolean skipSilenceAtStart) {
		if (this.skipSilenceAtStart != skipSilenceAtStart) {
			this.skipSilenceAtStart = skipSilenceAtStart;
			fireChangeEvent(AbcSongProperty.SKIP_SILENCE_AT_START);
		}
	}
	
	public boolean isDeleteMinimalNotes() {
		return deleteMinimalNotes;
	}

	public void setDeleteMinimalNotes(boolean deleteMinimalNotes) {
		if (this.deleteMinimalNotes != deleteMinimalNotes) {
			this.deleteMinimalNotes = deleteMinimalNotes;
			fireChangeEvent(AbcSongProperty.DELETE_MINIMAL_NOTES);
		}
	}

	/*
	 * public void setShowPruned(boolean showPruned) { if (this.showPruned != showPruned) { this.showPruned =
	 * showPruned; //fireChangeEvent(AbcSongProperty.SHOW_PRUNED); } }
	 * 
	 * public boolean isShowPruned() { return showPruned; }
	 */

	public void setBadger(boolean badger) {
		this.badger = badger;
	}

	public void setAllOut(boolean allOut) {
		this.allOut = allOut;
	}

	public SequenceInfo getSequenceInfo() {
		return sequenceInfo;
	}

	public boolean isFromAbcFile() {
		return fromAbcFile;
	}

	public boolean isFromXmlFile() {
		return fromXmlFile;
	}

	@Override
	public String getPartName(AbcPartMetadataSource abcPart) {
		return partNameTemplate.formatName(abcPart);
	}

	public File getSourceFile() {
		return sourceFile;
	}
	
	public void setSourceFile(File sourceFile) {
		this.sourceFile = sourceFile;
	}

	@Override
	public String getSourceFilename() {
		String ret = "ERROR";
		if (sourceFile != null) {
			ret = sourceFile.getName();
		}
		return ret;
	}

	public File getSaveFile() {
		return saveFile;
	}

	public void setSaveFile(File saveFile) {
		this.saveFile = saveFile;
	}

	@Override
	public File getExportFile() {
		return exportFile;
	}

	public void setExportFile(File exportFile) {
		if (this.exportFile == null && exportFile == null)
			return;
		if (this.exportFile != null && this.exportFile.equals(exportFile))
			return;

		this.exportFile = exportFile;
		fireChangeEvent(AbcSongProperty.EXPORT_FILE);
	}

	@Override
	public String getSongTitle() {
		return getTitle();
	}

	@Override
	public long getSongLengthMicros() {
		if (parts.isEmpty() || sequenceInfo == null)
			return 0;

		try {
			AbcExporter exporter = getAbcExporter();

			return (long) ((exporter.getExportEndMicros() - exporter.getExportStartMicros())
					/ (double) timingInfo.getExportTempoFactor());
		} catch (AbcConversionException e) {
			return 0;
		}
	}

	public ListModelWrapper<AbcPart> getParts() {
		return parts;
	}

	public void addSongListener(Listener<AbcSongEvent> l) {
		listeners.add(l);
	}

	public void removeSongListener(Listener<AbcSongEvent> l) {
		listeners.remove(l);
	}

	private void fireChangeEvent(AbcSongProperty property) {
		fireChangeEvent(property, null);
	}

	private void fireChangeEvent(AbcSongProperty property, AbcPart part) {
		if (listeners.size() > 0)
			listeners.fire(new AbcSongEvent(this, property, part));
	}

	public QuantizedTimingInfo getAbcTimingInfo() throws AbcConversionException {
		if (timingInfo == null //
				|| timingInfo.getExportTempoFactor() != getTempoFactor() //
				|| timingInfo.getMeter() != getTimeSignature() //
				|| timingInfo.isTripletTiming() != isTripletTiming() //
				|| timingInfo.isMixTiming() != isMixTiming() //
				|| timingInfo.getMixVersion() != getMixVersion() //
				|| isMixDirty()) {
			setMixDirty(false);
			timingInfo = new QuantizedTimingInfo(sequenceInfo, getTempoFactor(), getTimeSignature(), isTripletTiming(),
					getTempoBPM(), this, isMixTiming(), getMixVersion());
		}

		return timingInfo;
	}

	public AbcExporter getAbcExporter() throws AbcConversionException {
		QuantizedTimingInfo qtm = getAbcTimingInfo();
		KeySignature key = getKeySignature();

		if (abcExporter == null) {
			abcExporter = new AbcExporter(parts, qtm, key, this, skipSilenceAtStart);
		}
		if (abcExporter.getTimingInfo() != qtm) {
			abcExporter.setTimingInfo(qtm);
		}

		if (abcExporter.getKeySignature() != key)
			abcExporter.setKeySignature(key);

		if (abcExporter.isSkipSilenceAtStart() != skipSilenceAtStart)
			abcExporter.setSkipSilenceAtStart(skipSilenceAtStart);

		if (abcExporter.isDeleteMinimalNotes() != deleteMinimalNotes)
			abcExporter.setDeleteMinimalNotes(deleteMinimalNotes);
		
		return abcExporter;
	}

	/**
	 * 
	 * 1st sort according to instrument base number
	 * 2nd sort according to part number
	 * 
	 */
	private Comparator<AbcPart> partNumberComparator = new Comparator<AbcPart>() {
		@Override
		public int compare(AbcPart p1, AbcPart p2) {
			int base1 = partAutoNumberer.getFirstNumber(p1.getInstrument());
			int base2 = partAutoNumberer.getFirstNumber(p2.getInstrument());

			if (base1 != base2)
				return base1 - base2;
			return p1.getPartNumber() - p2.getPartNumber();
		}
	};

	private Listener<AbcPartEvent> abcPartListener = e -> {
		if (e.getProperty() == AbcPartProperty.PART_NUMBER) {
			parts.sort(partNumberComparator);
			fireChangeEvent(AbcSongProperty.PART_LIST_ORDER, e.getSource());
		}
	};

	@Override
	public String getBadgerTitle() {
		if (!badger)
			return null;
		return "N: Title: " + StringCleaner.cleanForABC(getComposer()) + " - "
				+ StringCleaner.cleanForABC(getSongTitle());
	}

	public void assignNumbersToSimilarPartTypes() {
		// System.out.println();
		// System.out.println("=== assignNumbersToSimilarPartTypes ===");
		for (LotroInstrument instr : LotroInstrument.values()) {
			List<AbcPart> instrParts = new ArrayList<>();
			for (AbcPart part : parts) {
				if (part.getInstrument().equals(instr)) {
					instrParts.add(part);
				}
			}
			// int partIndex = 0;
			if (instrParts.size() > 1) {// This is not super tight, as one or more of them might not have assigned any
										// track. TODO.
				AbcHelper.setTypeNumbers(instrParts);
			} else if (instrParts.size() == 1) {
				// System.out.println(partIndex + ": " + instrParts.get(0).getInstrument() + " =
				// " + 0);
				instrParts.get(0).setTypeNumber(0);
			}
		}
		// System.out.println();
	}

	public boolean isPriorityActive() {
		return priorityActive;
	}

	public void setPriorityActive(boolean priorityActive) {
		if (this.priorityActive != priorityActive) {
			setMixDirty(true);
			this.priorityActive = priorityActive;
			fireChangeEvent(AbcSongProperty.MIX_TIMING_COMBINE_PRIORITIES);
		}
	}

	public void tuneEdited() {
		convertTunelinesToLongs();
		setMixDirty(true); // Tempo might have changed, in which case the mixTimings need to be recomputed
		fireChangeEvent(AbcSongProperty.TUNE_EDIT);
	}

	public int getTuneTranspose(long tickStart) {
		int tuneTrans = 0;
		NavigableMap<Float, TuneLine> tree = tuneBars;
		if (tree != null) {
			for (TuneLine value : tuneBars.values()) {
				if (tickStart < value.endTick && tickStart >= value.startTick) {
					tuneTrans = value.seminoteStep;
				}
			}
		}
		return tuneTrans;
	}

	public NavigableMap<Long, Integer> getTuneTempoChanges() {
		SortedMap<Float, TuneLine> tree = tuneBars;
		TreeMap<Long, Integer> treeChanges = new TreeMap<>();
		if (tree != null) {
			Collection<TuneLine> lines = tree.values();
			for (TuneLine line : lines) {
				if (line.accelerando != 0) {
					int steps = Math.abs(line.accelerando);
					int step = line.accelerando < 0?-1:1;
					long domain = line.endTick - line.startTick;
					long domainStep = domain/steps;
					while (domainStep == 0L && steps > 1) {
						// very short number of ticks compared to number of tempo steps
						steps /= 2;
						domainStep = domain/steps;
						step *= 2;
					}
					if (domainStep > 0L) {
						for (int i = 0; Math.abs(step)*(i+1) <= steps; i++) {
							long distance = i*domainStep;
							int newTempoOffset = step*(i+1)+line.tempo;
							treeChanges.put(line.startTick+distance, newTempoOffset);
							assert line.startTick+distance < line.endTick : "steps="+steps+" step="+step+"\n domainStep="+domainStep+" domain="+domain;
						}
						if(treeChanges.get(line.endTick) == null || treeChanges.get(line.endTick) == 0) {
							treeChanges.put(line.endTick, 0);
						}
					} else {
						System.err.println("Tune-editor accelerando so short that it was skipped.");
					}
					
					/* example:
					
					bar 30 to 40 at 4 accelerando and 10 offset
					
					steps=4
					step=1
					domain=10
					domain/steps=2.5
					
					forloop 1 to 3:
					30.0 at 1 +10
					32.5 at 2 +10
					35.0 at 3 +10
					37.5 at 4 +10
					
					final:
					40 at 0 unless another tempochange or accelerando start here, then that will take precedence
					 
					*/
				} else if (line.tempo != 0) {
					treeChanges.put(line.startTick, line.tempo);
					if(treeChanges.get(line.endTick) == null || treeChanges.get(line.endTick) == 0) {
						treeChanges.put(line.endTick, 0);
					}
				}
			}
		}
		return treeChanges;
	}
	
	public void setLastBar(Float lastBar) {
		this.lastBar = lastBar;
	}
	
	public Float getLastBar() {
		return lastBar;
	}
	
	public void setFirstBar(Float firstBar) {
		this.firstBar = firstBar;
	}
	
	public Float getFirstBar() {
		return firstBar;
	}
	
	public long getLastBarTick() {
		return lastBarTick;
	}
	
	public long getFirstBarTick() {
		return firstBarTick;
	}

	public InstrNameSettings getInstrNameSettings() {
		return instrNameSettings;
	}

	public boolean isMixDirty() {
		return mixDirty;
	}

	public void setMixDirty(boolean mixDirty) {
		this.mixDirty = mixDirty;
	}

	public boolean isUsingOldVelocities() {
		return usingOldVelocities;
	}
	
	public QuantizedTimingInfo getQTM() {
		try {
			if (getAbcTimingInfo() == null) return null;
		} catch (AbcConversionException e) {
			timingInfo = null;// To make sure at some point the user will see the exception.
			return null;
		}
		return timingInfo;
	}

	public void setHideEdits(boolean selected) {
		this.hideEdits = selected;
		fireChangeEvent(AbcSongProperty.HIDE_EDITS_UPDATE);
	}
	
	public boolean isHideEdits() {
		return hideEdits; 
	}

	public int getAbcTempoMPQ(long thumbTick) {
		int mpq;
		try {
			if (getAbcTimingInfo() == null) return 0;
			mpq = getAbcTimingInfo().getAbcTempoMPQForTick(thumbTick);
		} catch (AbcConversionException e) {
			timingInfo = null;// To make sure at some point the user will see the exception.
			return 0;
		}
		return mpq;
	}

	public Collection<TimingInfoEvent> getTimingInfoByTick() {
		try {
			if (getAbcTimingInfo() == null) return null;
		} catch (AbcConversionException e) {
			timingInfo = null;// To make sure at some point the user will see the exception.
			return null;
		}
		return timingInfo.getTimingInfoByTick().values();
	}
}
