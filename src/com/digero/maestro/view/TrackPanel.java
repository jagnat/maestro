package com.digero.maestro.view;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;
import info.clearthought.layout.TableLayoutConstraints;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.Note;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.ParseException;
import com.digero.common.util.Util;
import com.digero.common.view.ColorTable;
import com.digero.common.view.LinkButton;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.DrumNoteMap;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.TrackInfo;

@SuppressWarnings("serial")
public class TrackPanel extends JPanel implements IDiscardable, TableLayoutConstants, ICompileConstants
{
	private static final String DRUM_NOTE_MAP_DIR_PREF_KEY = "DrumNoteMap.directory";

	//     0           1              2               3
	//   +---+-------------------+----------+--------------------+
	//   |   |     TRACK NAME    | octave   |  +--------------+  |
	// 0 |   |[X]                | +----^-+ |  | (note graph) |  |
	//   |   |     Instrument(s) | +----v-+ |  +--------------+  |
	//   +   +-------------------+----------+                    |
	// 1 |   |Drum save controls (optional) |                    |
	//   +---+------------------------------+--------------------+
	static final int GUTTER_COLUMN = 0;
	static final int TITLE_COLUMN = 1;
	static final int CONTROL_COLUMN = 2;
	static final int NOTE_COLUMN = 3;

	static final int HGAP = 4;
	static final int SECTIONBUTTON_WIDTH = 22;
	static final int GUTTER_WIDTH = 8;
	static final int TITLE_WIDTH = 150;
	static final int CONTROL_WIDTH = 64;
	private static final double[] LAYOUT_COLS = new double[] { GUTTER_WIDTH, TITLE_WIDTH, CONTROL_WIDTH, FILL };
	private static final double[] LAYOUT_ROWS = new double[] { 48, PREFERRED };

	private final TrackInfo trackInfo;
	private final NoteFilterSequencerWrapper seq;
	private final SequencerWrapper abcSequencer;
	private final AbcPart abcPart;

	private JPanel gutter;
	private JCheckBox checkBox;
	private TableLayoutConstraints checkBoxLayout_ControlsHidden;
	private TableLayoutConstraints checkBoxLayout_ControlsVisible;
	private JButton sectionButton;
	private JSpinner transposeSpinner;
	private TrackVolumeBar trackVolumeBar;
	private JPanel drumSavePanel;
	private TrackNoteGraph noteGraph;
	private ArrayList<DrumPanel> dPanels;

	private Listener<AbcPartEvent> abcListener;
	private Listener<SequencerEvent> seqListener;

	private boolean showDrumPanels;
	private boolean wasDrumPart;
	private boolean isAbcPreviewMode = false;
	
	private String badString = ""; 

	public TrackPanel(TrackInfo info, NoteFilterSequencerWrapper sequencer, AbcPart part, SequencerWrapper abcSequencer_)
	{
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));

		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER.get()));

		this.trackInfo = info;
		this.seq = sequencer;
		this.abcPart = part;
		this.abcSequencer = abcSequencer_;

		TableLayout tableLayout = (TableLayout) getLayout();
		tableLayout.setHGap(HGAP);

		gutter = new JPanel((LayoutManager) null);
		gutter.setOpaque(false);

		checkBox = new JCheckBox();
		checkBox.setOpaque(false);
		checkBox.setSelected(abcPart.isTrackEnabled(trackInfo.getTrackNumber()));

		checkBox.addActionListener(new ActionListener()
		{
			@Override public void actionPerformed(ActionEvent e)
			{
				int track = trackInfo.getTrackNumber();
				boolean enabled = checkBox.isSelected();
				abcPart.setTrackEnabled(track, enabled);
				if (MUTE_DISABLED_TRACKS)
					seq.setTrackMute(track, !enabled);
				updateBadTooltipText();
				updateTitleText();
			}
		});

		noteGraph = new TrackNoteGraph(seq, trackInfo);
		noteGraph.addMouseListener(new MouseAdapter()
		{
			private int soloAbcTrack = -1;
			private int soloMidiTrack = -1;

			@Override public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON3)
				{
					int trackNumber = trackInfo.getTrackNumber();
					if (isAbcPreviewMode())
					{
						if (abcPart.isTrackEnabled(trackNumber))
						{
							soloAbcTrack = abcPart.getPreviewSequenceTrackNumber();
						}
						else
						{
							for (AbcPart part : abcPart.getAbcSong().getParts())
							{
								if (part.isTrackEnabled(trackNumber))
								{
									soloAbcTrack = part.getPreviewSequenceTrackNumber();
									break;
								}
							}
						}

						if (soloAbcTrack >= 0)
							abcSequencer.setTrackSolo(soloAbcTrack, true);
					}
					else
					{
						soloMidiTrack = trackNumber;
						seq.setTrackSolo(soloMidiTrack, true);
					}
				}
			}

			@Override public void mouseReleased(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON3)
				{
					if (abcSequencer != null && soloAbcTrack >= 0)
						abcSequencer.setTrackSolo(soloAbcTrack, false);
					soloAbcTrack = -1;

					if (soloMidiTrack >= 0)
						seq.setTrackSolo(soloMidiTrack, false);
					soloMidiTrack = -1;
				}
			}
		});

		if (!trackInfo.isDrumTrack())
		{
			int currentTranspose = abcPart.getTrackTranspose(trackInfo.getTrackNumber());
			transposeSpinner = new JSpinner(new TrackTransposeModel(currentTranspose, -48, 48, 12));
			transposeSpinner.setToolTipText("Transpose this track by octaves (12 semitones)");

			transposeSpinner.addChangeListener(new ChangeListener()
			{
				@Override public void stateChanged(ChangeEvent e)
				{
					int track = trackInfo.getTrackNumber();
					int value = (Integer) transposeSpinner.getValue();
					if (value % 12 != 0)
					{
						value = (abcPart.getTrackTranspose(track) / 12) * 12;
						transposeSpinner.setValue(value);
					}
					else
					{
						abcPart.setTrackTranspose(trackInfo.getTrackNumber(), value);
					}
					updateBadTooltipText();
					updateTitleText();
				}
			});
			
			
		}
		
		sectionButton = new JButton();
		sectionButton.setPreferredSize(new Dimension(SECTIONBUTTON_WIDTH, SECTIONBUTTON_WIDTH));
		sectionButton.setMargin( new Insets(5, 5, 5, 5) );
		sectionButton.setText("s");
		sectionButton.setToolTipText("<html><b> Edit sections of this track </b><br> Use the bar counter in lower right corner to find your sections. </html>");
		sectionButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				int track = trackInfo.getTrackNumber();
				SectionEditor.show((JFrame)sectionButton.getTopLevelAncestor(), noteGraph, abcPart, track, abcPart.getInstrument().isPercussion, dPanels);// super hack! :(
			}
			
		});

		trackVolumeBar = new TrackVolumeBar(trackInfo.getMinVelocity(), trackInfo.getMaxVelocity());
		trackVolumeBar.setToolTipText("Adjust this track's volume");
		trackVolumeBar.setDeltaVolume(abcPart.getTrackVolumeAdjust(trackInfo.getTrackNumber()));
		trackVolumeBar.addActionListener(new ActionListener()
		{
			@Override public void actionPerformed(ActionEvent e)
			{
				// Only update the actual ABC part when the user stops dragging the trackVolumeBar
				if (!trackVolumeBar.isDragging())
					abcPart.setTrackVolumeAdjust(trackInfo.getTrackNumber(), trackVolumeBar.getDeltaVolume());

				updateState();
			}
		});

		JPanel controlPanel = new JPanel(new BorderLayout(0, 4));
		controlPanel.setOpaque(false);
		if (sectionButton != null)
			controlPanel.add(sectionButton, BorderLayout.WEST);
		if (transposeSpinner != null)
			controlPanel.add(transposeSpinner, BorderLayout.CENTER);
		controlPanel.add(trackVolumeBar, BorderLayout.SOUTH);

		checkBoxLayout_ControlsHidden = new TableLayoutConstraints(TITLE_COLUMN, 0, CONTROL_COLUMN, 0);
		checkBoxLayout_ControlsVisible = new TableLayoutConstraints(TITLE_COLUMN, 0);

		add(gutter, GUTTER_COLUMN + ", 0, " + GUTTER_COLUMN + ", 1, f, f");
		add(checkBox, checkBoxLayout_ControlsHidden);
		add(controlPanel, CONTROL_COLUMN + ", 0, f, c");
		add(noteGraph, NOTE_COLUMN + ", 0, " + NOTE_COLUMN + ", 1");

		updateBadTooltipText();
		updateTitleText();

		abcPart.addAbcListener(abcListener = new Listener<AbcPartEvent>()
		{
			@Override public void onEvent(AbcPartEvent e)
			{
				if (e.isNoteGraphRelated())
				{
					updateState();
					noteGraph.repaint();
					updateBadTooltipText();
					updateTitleText();
				}

				if (e.getProperty() == AbcPartProperty.INSTRUMENT || e.getProperty() == AbcPartProperty.TRACK_ENABLED) {
					updateColors();
					updateBadTooltipText();
					updateTitleText();
				}
			}
		});

		seq.addChangeListener(seqListener = new Listener<SequencerEvent>()
		{
			@Override public void onEvent(SequencerEvent evt)
			{
				if (evt.getProperty() == SequencerProperty.TRACK_ACTIVE)
				{
					updateColors();
				}
				else if (evt.getProperty() == SequencerProperty.IS_RUNNING)
				{
					noteGraph.repaint();
				}
			}
		});

		if (abcSequencer != null)
			abcSequencer.addChangeListener(seqListener);

		addPropertyChangeListener("enabled", new PropertyChangeListener()
		{
			@Override public void propertyChange(PropertyChangeEvent evt)
			{
				updateState();
			}
		});

		updateState(true);
	}

	private void initDrumSavePanel()
	{
		JLabel intro = new JLabel("Drum Map: ");
		intro.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());

		LinkButton saveButton = new LinkButton("Export");
		saveButton.setForeground(ColorTable.PANEL_LINK.get());
		saveButton.addActionListener(new ActionListener()
		{
			@Override public void actionPerformed(ActionEvent e)
			{
				if(!abcPart.isFXPart()) {
					saveDrumMapping();
				}
			}
		});

		JLabel divider = new JLabel(" | ");
		divider.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());

		LinkButton loadButton = new LinkButton("Import");
		loadButton.setForeground(ColorTable.PANEL_LINK.get());
		loadButton.addActionListener(new ActionListener()
		{
			@Override public void actionPerformed(ActionEvent e)
			{
				if(!abcPart.isFXPart()) {
					loadDrumMapping();
				}
			}
		});

		drumSavePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		drumSavePanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 0));
		drumSavePanel.setOpaque(false);
		drumSavePanel.add(intro);
		drumSavePanel.add(loadButton);
		drumSavePanel.add(divider);
		drumSavePanel.add(saveButton);
	}

	public TrackInfo getTrackInfo()
	{
		return trackInfo;
	}

	public void setAbcPreviewMode(boolean isAbcPreviewMode)
	{
		if (this.isAbcPreviewMode != isAbcPreviewMode)
		{
			this.isAbcPreviewMode = isAbcPreviewMode;
			updateColors();
			for (Component child : getComponents())
			{
				if (child instanceof DrumPanel)
				{
					((DrumPanel) child).setAbcPreviewMode(isAbcPreviewMode);
				}
			}
		}
	}

	private boolean isAbcPreviewMode()
	{
		return abcSequencer != null && isAbcPreviewMode;
	}
	
	private void updateBadTooltipText() {
		if (abcPart.getInstrument().ordinal() == LotroInstrument.BASIC_CLARINET.ordinal()) {
			int g3count = 0;
			List<NoteEvent> nel = trackInfo.getEvents();
			for (NoteEvent ne : nel) {
				if (ne.tiesFrom != null) {
					continue;
				}
				Note mn = abcPart.mapNote(trackInfo.getTrackNumber(), ne.note.id, ne.getStartTick());
				if (mn != null && mn.id == Note.G3.id) {
					g3count += 1;
				}
			}		
			if (g3count == 0) {
				badString = "</b><br>" + "Bad G3 notes: " + g3count;
			} else {
				badString = "</b><br><p style='color:red;'>" + "Bad G3 notes: " + g3count + "</p>";
			}
			
		} else if (abcPart.getInstrument().ordinal() == LotroInstrument.BASIC_PIBGORN.ordinal()) {
			int acount = 0;
			List<NoteEvent> nel = trackInfo.getEvents();
			for (NoteEvent ne : nel) {
				if (ne.tiesFrom != null) {
					continue;
				}
				Note mn = abcPart.mapNote(trackInfo.getTrackNumber(), ne.note.id, ne.getStartTick());
				if (mn != null && (mn.id == Note.A2.id || mn.id == Note.A3.id || mn.id == Note.A4.id)) {
					acount += 1;
				}
			}		
			if (acount == 0) {
				badString = "</b><br>" + "Bad A notes: " + acount;
			} else {
				badString = "</b><br><p style='color:red;'>" + "Bad A notes: " + acount + "</p>";
			}
		} else {
			badString = "";
		}
	}

	private void updateTitleText()
	{
		final int ELLIPSIS_OFFSET = 28;

		String title = trackInfo.getTrackNumber() + ". " + trackInfo.getName();
		String instr = trackInfo.getInstrumentNames();
				
		checkBox.setToolTipText("<html><b>" + title + "</b><br>" + instr + badString + "</html>");

		int titleWidth = TITLE_WIDTH;
		if (!trackVolumeBar.isVisible())
			titleWidth += CONTROL_WIDTH;

		title = Util.ellipsis(title, titleWidth - ELLIPSIS_OFFSET, checkBox.getFont().deriveFont(Font.BOLD));
		instr = Util.ellipsis(instr, titleWidth - ELLIPSIS_OFFSET, checkBox.getFont());
		checkBox.setText("<html><b>" + title + "</b><br>" + instr + "</html>");
	}

	@Override public void setBackground(Color bg)
	{
		super.setBackground(bg);
		if (trackVolumeBar != null)
			trackVolumeBar.setBackground(bg);
	}

	private void updateColors()
	{
		boolean abcPreviewMode = isAbcPreviewMode();
		int trackNumber = trackInfo.getTrackNumber();
		boolean trackEnabled = abcPart.isTrackEnabled(trackNumber);
		boolean trackEnabledOtherPart = trackEnabled;

		boolean trackActive;
		boolean trackSolo;

		if (abcPreviewMode)
		{
			// Set in the loop below
			trackActive = false;
			trackSolo = false;
		}
		else
		{
			trackActive = seq.isTrackActive(trackNumber);
			trackSolo = seq.getTrackSolo(trackNumber);
		}

		for (AbcPart part : abcPart.getAbcSong().getParts())
		{
			if (part.isTrackEnabled(trackNumber))
			{
				if (part != this.abcPart)
					trackEnabledOtherPart = true;
				else if (sectionButton != null) {
					if (this.abcPart.sections.get(trackNumber) == null && this.abcPart.nonSection.get(trackNumber) == null) {
						sectionButton.setForeground(new Color(0.5f, 0.5f, 0.5f));
					} else {
						sectionButton.setForeground(new Color(0.2f, 0.8f, 0.2f));
					}
				}

				if (abcPreviewMode)
				{
					if (abcSequencer.isTrackActive(part.getPreviewSequenceTrackNumber()))
						trackActive = true;
					if (abcSequencer.getTrackSolo(part.getPreviewSequenceTrackNumber()))
						trackSolo = true;
				}
			}
		}

		gutter.setOpaque(trackEnabled || trackEnabledOtherPart);
		if (trackEnabled)
			gutter.setBackground(ColorTable.PANEL_HIGHLIGHT.get());
		else if (trackEnabledOtherPart)
			gutter.setBackground(ColorTable.PANEL_HIGHLIGHT_OTHER_PART.get());

		// Gray out the main drum panel if one of its child drum notes is solo
		if (trackActive && trackSolo && showDrumPanels)
		{
			SequencerWrapper activeSeq = abcPreviewMode ? abcSequencer : seq;
			if (activeSeq instanceof NoteFilterSequencerWrapper)
			{
				if (((NoteFilterSequencerWrapper) activeSeq).getFilter().isAnyNoteSolo())
				{
					trackActive = false;
				}
			}
		}

		noteGraph.setShowingAbcNotesOn(trackActive);

		if (!trackActive)
		{
			noteGraph.setNoteColor(ColorTable.NOTE_OFF);
			noteGraph.setBadNoteColor(ColorTable.NOTE_BAD_OFF);
			setBackground(ColorTable.GRAPH_BACKGROUND_OFF.get());
		}
		else if (trackEnabled)
		{
			noteGraph.setNoteColor(ColorTable.NOTE_ENABLED);
			noteGraph.setBadNoteColor(ColorTable.NOTE_BAD_ENABLED);
			setBackground(ColorTable.GRAPH_BACKGROUND_ENABLED.get());
		}
		else if (trackSolo)
		{
			noteGraph.setNoteColor(ColorTable.NOTE_DISABLED);
			noteGraph.setBadNoteColor(ColorTable.NOTE_BAD_DISABLED);
			setBackground(ColorTable.GRAPH_BACKGROUND_ENABLED.get());
		}
		else
		{
			boolean pseudoOff = !abcPreviewMode && (abcPart.isDrumPart() != trackInfo.isDrumTrack());
			noteGraph.setNoteColor(pseudoOff ? ColorTable.NOTE_OFF : ColorTable.NOTE_DISABLED);
			noteGraph.setBadNoteColor(pseudoOff ? ColorTable.NOTE_BAD_OFF : ColorTable.NOTE_BAD_DISABLED);
			setBackground(ColorTable.GRAPH_BACKGROUND_DISABLED.get());
		}

		if (trackEnabled)
		{
			checkBox.setForeground(ColorTable.PANEL_TEXT_ENABLED.get());
		}
		else
		{
			boolean inputEnabled = abcPart.isDrumPart() == trackInfo.isDrumTrack();
			checkBox.setForeground(inputEnabled ? ColorTable.PANEL_TEXT_DISABLED.get() : ColorTable.PANEL_TEXT_OFF
					.get());
		}

		noteGraph.setOctaveLinesVisible(!trackInfo.isDrumTrack()
				&& !(abcPart.getInstrument().isPercussion && abcPart.isTrackEnabled(trackInfo.getTrackNumber())));
	}

	private void updateState()
	{
		updateState(false);
	}

	private void updateState(boolean initDrumPanels)
	{
		updateColors();

		boolean trackEnabled = abcPart.isTrackEnabled(trackInfo.getTrackNumber());
		checkBox.setSelected(trackEnabled);

		// Update the visibility of controls
		trackVolumeBar.setVisible(trackEnabled);
		if (transposeSpinner != null)
			transposeSpinner.setVisible(trackEnabled && !abcPart.isDrumPart());
		if (sectionButton != null)
			sectionButton.setVisible(trackEnabled);

		TableLayout layout = (TableLayout) getLayout();
		TableLayoutConstraints newCheckBoxLayout = trackEnabled ? checkBoxLayout_ControlsVisible
				: checkBoxLayout_ControlsHidden;

		if (layout.getConstraints(checkBox) != newCheckBoxLayout)
		{
			layout.setConstraints(checkBox, newCheckBoxLayout);
			updateTitleText();
		}
		
		noteGraph.setShowingNoteVelocity(trackVolumeBar.isDragging());

		if (trackVolumeBar.isDragging())
		{
			noteGraph.setDeltaVolume(trackVolumeBar.getDeltaVolume());
		}
		else
		{
			noteGraph.setDeltaVolume(abcPart.getTrackVolumeAdjust(trackInfo.getTrackNumber()));
		}

		boolean showDrumPanelsNew = abcPart.isDrumPart() && trackEnabled;
		
		if (showDrumPanelsNew || initDrumPanels) {
			this.setPreferredSize(null);
		}
		
		if (initDrumPanels || showDrumPanels != showDrumPanelsNew || wasDrumPart != abcPart.isDrumPart())
		{
			if (showDrumPanels != showDrumPanelsNew)
			{
				noteGraph.repaint();
				showDrumPanels = showDrumPanelsNew;				
			}
			wasDrumPart = abcPart.isDrumPart();

			for (int i = getComponentCount() - 1; i >= 0; --i)
			{
				Component child = getComponent(i);
				if (child instanceof DrumPanel)
				{
					((DrumPanel) child).discard();
					remove(i);
				}
			}
			dPanels = null;
			if (drumSavePanel != null)
				remove(drumSavePanel);

			if (showDrumPanels)
			{
				if (drumSavePanel == null)
					initDrumSavePanel();

				add(drumSavePanel, TITLE_COLUMN + ", 1," + CONTROL_COLUMN + ", 1, l, c");
				int row = LAYOUT_ROWS.length;
				for (int noteId : trackInfo.getNotesInUse())
				{
					DrumPanel panel = new DrumPanel(trackInfo, seq, abcPart, noteId, abcSequencer, trackVolumeBar);
					if (row <= layout.getNumRow())
						layout.insertRow(row, PREFERRED);
					add(panel, "0, " + row + ", " + NOTE_COLUMN + ", " + row);
					if (dPanels == null)
						dPanels = new ArrayList<DrumPanel>();
					dPanels.add(panel);
				}
			}

			updateTitleText();

			revalidate();
		}
	}

	private boolean saveDrumMapping()
	{
		Preferences prefs = Preferences.userNodeForPackage(TrackPanel.class);

		String dirPath = prefs.get(DRUM_NOTE_MAP_DIR_PREF_KEY, null);
		File dir;
		if (dirPath == null || !(dir = new File(dirPath)).isDirectory())
			dir = Util.getLotroMusicPath(false /* create */);

		JFileChooser fileChooser = new JFileChooser(dir);
		fileChooser.setFileFilter(new ExtensionFileFilter("Drum Map (*." + DrumNoteMap.FILE_SUFFIX + ")",
				DrumNoteMap.FILE_SUFFIX));

		File saveFile;
		do
		{
			if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
				return false;

			saveFile = fileChooser.getSelectedFile();

			if (saveFile.getName().indexOf('.') < 0)
			{
				saveFile = new File(saveFile.getParentFile(), saveFile.getName() + "." + DrumNoteMap.FILE_SUFFIX);
			}

			if (saveFile.exists())
			{
				int result = JOptionPane.showConfirmDialog(this, "File " + saveFile.getName()
						+ " already exists. Overwrite?", "Confirm overwrite", JOptionPane.OK_CANCEL_OPTION);
				if (result != JOptionPane.OK_OPTION)
					continue;
			}
		} while (false);

		try
		{
			abcPart.getDrumMap(trackInfo.getTrackNumber()).save(saveFile);
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, "Failed to save drum map:\n\n" + e.getMessage(),
					"Failed to save drum map", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		prefs.put(DRUM_NOTE_MAP_DIR_PREF_KEY, fileChooser.getCurrentDirectory().getAbsolutePath());
		return true;
	}

	private boolean loadDrumMapping()
	{
		Preferences prefs = Preferences.userNodeForPackage(TrackPanel.class);

		String dirPath = prefs.get(DRUM_NOTE_MAP_DIR_PREF_KEY, null);
		File dir;
		if (dirPath == null || !(dir = new File(dirPath)).isDirectory())
			dir = Util.getLotroMusicPath(false /* create */);

		JFileChooser fileChooser = new JFileChooser(dir);
		fileChooser.setFileFilter(new ExtensionFileFilter("Drum Map (*." + DrumNoteMap.FILE_SUFFIX + ")",
				DrumNoteMap.FILE_SUFFIX, "txt"));

		if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
			return false;

		File loadFile = fileChooser.getSelectedFile();

		try
		{
			abcPart.getDrumMap(trackInfo.getTrackNumber()).load(loadFile);
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, "Failed to load drum map:\n\n" + e.getMessage(),
					"Failed to load drum map", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		catch (ParseException e)
		{
			JOptionPane.showMessageDialog(this, "Failed to load drum map:\n\n" + e.getMessage(),
					"Failed to load drum map", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		prefs.put(DRUM_NOTE_MAP_DIR_PREF_KEY, fileChooser.getCurrentDirectory().getAbsolutePath());
		return true;
	}

	@Override public void discard()
	{
		for (int i = getComponentCount() - 1; i >= 0; --i)
		{
			Component child = getComponent(i);
			if (child instanceof IDiscardable)
			{
				((IDiscardable) child).discard();
			}
		}
		dPanels = null;
		abcPart.removeAbcListener(abcListener);
		seq.removeChangeListener(seqListener);
		if (abcSequencer != null)
			abcSequencer.removeChangeListener(seqListener);
		noteGraph.discard();
	}

	private class TrackTransposeModel extends SpinnerNumberModel
	{
		public TrackTransposeModel(int value, int minimum, int maximum, int stepSize)
		{
			super(value, minimum, maximum, stepSize);
		}

		@Override public void setValue(Object value)
		{
			if (!(value instanceof Integer))
				throw new IllegalArgumentException();

			if ((Integer) value % 12 != 0)
				throw new IllegalArgumentException();

			super.setValue(value);
		}
	}

	private class TrackNoteGraph extends NoteGraph
	{
		private boolean showingAbcNotesOn = true;

		public TrackNoteGraph(SequencerWrapper sequencer, TrackInfo trackInfo)
		{
			super(sequencer, trackInfo, Note.MIN_PLAYABLE.id - 12, Note.MAX_PLAYABLE.id + 12);
		}

		public void setShowingAbcNotesOn(boolean showingAbcNotesOn)
		{
			if (this.showingAbcNotesOn != showingAbcNotesOn)
			{
				this.showingAbcNotesOn = showingAbcNotesOn;
				repaint();
			}
		}
		
		@Override Color getNoteColor(NoteEvent ne)
		{
			if (ne.isPruned(abcPart) && ProjectFrame.abcPreviewMode) {
				return ColorTable.NOTE_PRUNED.get();
			}
			return super.getNoteColor(ne);
		}

		@Override Color getBadNoteColor(NoteEvent ne)
		{
			if (ne.isPruned(abcPart) && ProjectFrame.abcPreviewMode) {
				return ColorTable.NOTE_PRUNED.get();
			}
			return super.getBadNoteColor(ne);
		}

		@Override protected int transposeNote(int noteId, long tickStart)
		{
			if (!trackInfo.isDrumTrack())
			{
				noteId += abcPart.getTranspose(trackInfo.getTrackNumber(), tickStart);
			}
			return noteId;
		}
		
		@Override protected boolean audibleNote(NoteEvent ne)
		{
			return abcPart.getAudible(trackInfo.getTrackNumber(), ne.getStartTick());
		}
		
		@Override protected boolean[] getSectionsModified() {
			if (!abcPart.isTrackEnabled(trackInfo.getTrackNumber())) {
				return null;
			}
			return abcPart.sectionsModified.get(trackInfo.getTrackNumber());
		}
		
		@Override protected int[] getSectionVelocity(NoteEvent note)
		{
			return abcPart.getSectionVolumeAdjust(trackInfo.getTrackNumber(), note);
			/*
			int[] empty = new int[2];
			empty[0] = 0;
			empty[1] = 100;
			return empty;*/
		}
		
		@Override protected Boolean[] getSectionDoubling(long tick)
		{
			return abcPart.getSectionDoubling(tick, trackInfo.getTrackNumber());
		}

		@Override protected boolean isNotePlayable(int noteId)
		{
			if (noteId < MidiConstants.LOWEST_NOTE_ID || noteId > MidiConstants.HIGHEST_NOTE_ID)
				return false;

			if (abcPart.isDrumPart())
				return abcPart.isDrumPlayable(trackInfo.getTrackNumber(), noteId);

			if (trackInfo.isDrumTrack() && !abcPart.isTrackEnabled(trackInfo.getTrackNumber()))
				return true;

			return abcPart.getInstrument().isPlayable(noteId);
		}

		@Override protected boolean isShowingNotesOn()
		{
			int trackNumber = trackInfo.getTrackNumber();

			if (sequencer.isRunning())
				return sequencer.isTrackActive(trackNumber);

			if (abcSequencer != null && abcSequencer.isRunning())
				return showingAbcNotesOn;

			return false;
		}

		@Override protected List<NoteEvent> getEvents()
		{
			if (showDrumPanels)
				return Collections.emptyList();

			return super.getEvents();
		}
	}

	public void setVerticalSize(int vert) {
		((TableLayout)this.getLayout()).setRow(0, vert);
		this.getLayout().layoutContainer(this);
	}
}
