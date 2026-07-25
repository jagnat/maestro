package com.digero.maestro.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.Note;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.Listener;
import com.digero.common.util.Pair;
import com.digero.common.view.ColorTable;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.PartSection;
import com.digero.maestro.midi.BentMidiNoteEvent;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.TrackInfo;

/**
 * Prototype tab 2 of the arrangement view: shows only the tracks enabled for the
 * selected part, with a taller note graph per track and an inline section strip
 * beneath each one. A shared inspector at the bottom edits the selected section.
 */
public class PartEditingTab extends JPanel implements SectionStripPanel.Host, SectionInspectorPanel.Host {
	private static final int HEADER_WIDTH = 190;
	private static final int GRAPH_HEIGHT = 110;

	private final NoteFilterSequencerWrapper sequencer;
	private final SequencerWrapper abcSequencer;

	private AbcPart abcPart;

	private final JLabel headerLabel = new JLabel(" ");
	private final JPanel rowsPanel;
	private final JScrollPane rowsScrollPane;
	private final SectionInspectorPanel inspector;

	private final List<NoteGraph> graphs = new ArrayList<>();
	private final List<SectionStripPanel> strips = new ArrayList<>();

	private int selectedTrack = -1;
	private PartSection selectedSection = null;

	private final Listener<AbcPartEvent> partListener = e -> {
		if (e.getProperty() == AbcPartProperty.TRACK_ENABLED || e.getProperty() == AbcPartProperty.INSTRUMENT) {
			rebuild();
		} else {
			repaint();
		}
	};

	public PartEditingTab(NoteFilterSequencerWrapper sequencer, SequencerWrapper abcSequencer) {
		super(new BorderLayout(4, 4));
		this.sequencer = sequencer;
		this.abcSequencer = abcSequencer;

		headerLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 2, 8));

		rowsPanel = new JPanel();
		rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
		rowsPanel.setBackground(ColorTable.CENTER_BACKGROUND.get());

		JPanel rowsWrapper = new JPanel(new BorderLayout());
		rowsWrapper.setBackground(ColorTable.CENTER_BACKGROUND.get());
		rowsWrapper.add(rowsPanel, BorderLayout.NORTH);

		rowsScrollPane = new JScrollPane(rowsWrapper, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		rowsScrollPane.getVerticalScrollBar().setUnitIncrement(24);
		rowsScrollPane.setBorder(BorderFactory.createEmptyBorder());

		inspector = new SectionInspectorPanel(this);

		add(headerLabel, BorderLayout.NORTH);
		add(rowsScrollPane, BorderLayout.CENTER);
		add(inspector, BorderLayout.SOUTH);
	}

	public void setAbcPart(AbcPart part) {
		if (this.abcPart == part) {
			rebuild(); // Track enablement may have changed while another tab was active
			return;
		}
		if (this.abcPart != null)
			this.abcPart.removeAbcListener(partListener);
		this.abcPart = part;
		if (this.abcPart != null)
			this.abcPart.addAbcListener(partListener);
		selectSection(-1, null);
		rebuild();
	}

	private void rebuild() {
		for (NoteGraph graph : graphs)
			graph.discard();
		graphs.clear();
		strips.clear();
		rowsPanel.removeAll();

		if (abcPart == null) {
			headerLabel.setText("No part selected");
			inspector.setSection(null, -1, null);
			revalidate();
			repaint();
			return;
		}

		// Drop a stale selection if its track is no longer enabled
		if (selectedTrack >= 0 && !abcPart.isTrackEnabled(selectedTrack))
			selectSection(-1, null);

		headerLabel.setText("<html><b>" + abcPart.getPartNumber() + ". " + abcPart.getTitle() + "</b> — "
				+ abcPart.getInstrument() + " — showing " + abcPart.getEnabledTrackCount() + " enabled track(s)</html>");

		int rowCount = 0;
		for (TrackInfo trackInfo : abcPart.getSequenceInfo().getTrackList()) {
			if (!trackInfo.hasEvents() || !abcPart.isTrackEnabled(trackInfo.getTrackNumber()))
				continue;
			rowsPanel.add(buildTrackRow(trackInfo));
			rowCount++;
		}

		if (rowCount == 0) {
			JLabel empty = new JLabel("No tracks enabled for this part — enable tracks in the Track Selection tab.",
					SwingConstants.CENTER);
			empty.setBorder(BorderFactory.createEmptyBorder(40, 10, 40, 10));
			empty.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
			empty.setAlignmentX(CENTER_ALIGNMENT);
			rowsPanel.add(empty);
		}

		revalidate();
		repaint();
	}

	private JPanel buildTrackRow(TrackInfo trackInfo) {
		final int track = trackInfo.getTrackNumber();

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 4));

		JLabel nameLabel = new JLabel(trackInfo.getName());
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
		JLabel instrumentLabel = new JLabel(trackInfo.getInstrumentNames());
		instrumentLabel.setFont(instrumentLabel.getFont().deriveFont(11f));
		int transpose = abcPart.getTrackTranspose(track);
		JLabel transposeLabel = new JLabel("Transpose: " + (transpose > 0 ? "+" : "") + transpose);
		transposeLabel.setFont(transposeLabel.getFont().deriveFont(11f));

		header.add(nameLabel);
		header.add(instrumentLabel);
		header.add(Box.createVerticalStrut(4));
		header.add(transposeLabel);

		JPanel headerWrapper = new JPanel(new BorderLayout());
		headerWrapper.setOpaque(false);
		headerWrapper.setPreferredSize(new Dimension(HEADER_WIDTH, GRAPH_HEIGHT + SectionStripPanel.STRIP_HEIGHT));
		headerWrapper.add(header, BorderLayout.NORTH);

		PartTrackNoteGraph graph = new PartTrackNoteGraph(abcPart, trackInfo);
		graph.setPreferredSize(new Dimension(100, GRAPH_HEIGHT));
		graphs.add(graph);

		SectionStripPanel strip = new SectionStripPanel(abcPart, track, sequencer, this);
		strips.add(strip);

		JPanel graphAndStrip = new JPanel(new BorderLayout());
		graphAndStrip.setOpaque(false);
		graphAndStrip.add(graph, BorderLayout.CENTER);
		graphAndStrip.add(strip, BorderLayout.SOUTH);

		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorTable.CENTER_BACKGROUND.get());
		row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER_HORIZ.get()));
		row.add(headerWrapper, BorderLayout.WEST);
		row.add(graphAndStrip, BorderLayout.CENTER);
		int rowHeight = GRAPH_HEIGHT + SectionStripPanel.STRIP_HEIGHT + 1;
		row.setPreferredSize(new Dimension(100, rowHeight));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
		return row;
	}

	private void selectSection(int track, PartSection ps) {
		selectedTrack = track;
		selectedSection = ps;
		inspector.setSection(ps == null ? null : abcPart, track, ps);
		for (SectionStripPanel strip : strips)
			strip.repaint();
	}

	// SectionStripPanel.Host

	@Override
	public void sectionClicked(int track, PartSection ps) {
		selectSection(track, ps);
	}

	@Override
	public void sectionCreated(int track, PartSection ps) {
		TreeMap<Float, PartSection> tree = abcPart.sections.get(track);
		if (tree == null) {
			tree = new TreeMap<>();
			abcPart.sections.set(track, tree);
		}
		tree.put(ps.startBar, ps);
		commitSections(track);
		selectSection(track, ps);
	}

	@Override
	public PartSection getSelectedSection(int track) {
		return (track == selectedTrack) ? selectedSection : null;
	}

	// SectionInspectorPanel.Host

	@Override
	public void sectionDeleted(int track, PartSection ps) {
		TreeMap<Float, PartSection> tree = abcPart.sections.get(track);
		if (tree != null)
			tree.remove(ps.startBar);
		selectSection(-1, null);
		commitSections(track);
	}

	/**
	 * Same commit logic as the SectionEditor dialog's APPLY button: recompute the
	 * per-bar modified flags and notify the part.
	 */
	@Override
	public void commitSections(int track) {
		TreeMap<Float, PartSection> tree = abcPart.sections.get(track);
		if (tree != null && tree.isEmpty()) {
			abcPart.sections.set(track, null);
			tree = null;
		}

		if (tree == null) {
			abcPart.sectionsModified.set(track, null);
		} else {
			float lastEnd = 0.0f;
			for (PartSection ps : tree.values())
				lastEnd = Math.max(lastEnd, ps.endBar);
			if (lastEnd > 200_000f)
				lastEnd = 200_000f;

			boolean[] booleanArray = new boolean[(int) lastEnd + 1];
			for (int m = 0; m < booleanArray.length; m++) {
				Entry<Float, PartSection> entry = tree.lowerEntry(m + 1.0f);
				booleanArray[m] = entry != null && entry.getValue().startBar < m + 1.0f
						&& entry.getValue().endBar > m;
			}
			abcPart.sectionsModified.set(track, booleanArray);
		}

		abcPart.sectionEdited(track);
		repaint();
	}

	/**
	 * A standalone track note graph for this tab, mirroring the behavior of
	 * TrackPanel.TrackNoteGraph (which is coupled to a TrackPanel instance).
	 */
	private class PartTrackNoteGraph extends NoteGraph {
		private final AbcPart part;

		PartTrackNoteGraph(AbcPart part, TrackInfo trackInfo) {
			super(PartEditingTab.this.sequencer, trackInfo, Note.MIN_PLAYABLE.id - 12, Note.MAX_PLAYABLE.id + 12);
			this.part = part;
		}

		@Override
		protected int transposeNote(int noteId, long tickStart) {
			if (!trackInfo.isDrumTrack())
				noteId += part.getTranspose(trackInfo.getTrackNumber(), tickStart, !part.getAbcSong().isHideEdits());
			return noteId;
		}

		@Override
		protected boolean audibleNote(NoteEvent ne) {
			if (part.getAbcSong().isHideEdits())
				return true;
			boolean visible = part.getAudible(trackInfo.getTrackNumber(), ne.getStartTick(), true);
			return visible && part.shouldPlay(ne, trackInfo.getTrackNumber())
					&& part.mapNoteEvent(trackInfo.getTrackNumber(), ne, true) != null;
		}

		@Override
		protected boolean[] getSectionsModified() {
			if (part.getAbcSong().isHideEdits())
				return null;
			return part.sectionsModified.get(trackInfo.getTrackNumber());
		}

		@Override
		protected List<Pair<Long, Long>> getMicrosModified(long from, long to) {
			if (part.getAbcSong().isHideEdits())
				return null;
			SequenceDataCache data = sequenceInfo.getDataCache();
			long a = data.microsToTick(from);
			long b = data.microsToTick(to);
			List<Pair<Long, Long>> list = new ArrayList<>();
			TreeMap<Long, PartSection> tree = part.sectionsTicked.get(trackInfo.getTrackNumber());
			if (tree == null)
				return list;
			NavigableMap<Long, PartSection> subtree = tree.headMap(b, false);
			for (Entry<Long, PartSection> entry : subtree.entrySet()) {
				if (entry.getValue().startTick < b && entry.getValue().endTick >= a) {
					list.add(new Pair<>(data.tickToMicros(entry.getValue().startTick),
							data.tickToMicros(entry.getValue().endTick)));
				}
			}
			return list;
		}

		@Override
		protected boolean isActiveTrack() {
			return part.isTrackEnabled(trackInfo.getTrackNumber());
		}

		@Override
		protected Float getLastBar() {
			if (part.getAbcSong().isHideEdits())
				return null;
			return part.getAbcSong().getLastBar();
		}

		@Override
		protected Float getFirstBar() {
			if (part.getAbcSong().isHideEdits())
				return null;
			return part.getAbcSong().getFirstBar();
		}

		@Override
		protected Long getLastBarTick() {
			if (part.getAbcSong().isHideEdits())
				return null;
			return part.getAbcSong().getLastBarTick();
		}

		@Override
		protected Long getFirstBarTick() {
			if (part.getAbcSong().isHideEdits())
				return null;
			return part.getAbcSong().getFirstBarTick();
		}

		@Override
		protected int[] getSectionVelocity(NoteEvent note) {
			if (part.getAbcSong().isHideEdits())
				return super.getSectionVelocity(note);
			return part.getSectionVolumeAdjust(trackInfo.getTrackNumber(), note);
		}

		@Override
		protected int getSourceNoteVelocity(NoteEvent note) {
			if (part.getAbcSong().isHideEdits())
				return note.velocity;
			return part.getSectionNoteVelocity(trackInfo.getTrackNumber(), note);
		}

		@Override
		protected Boolean[] getSectionDoubling(long tick) {
			if (part.getAbcSong().isHideEdits())
				return super.getSectionDoubling(tick);
			return part.getSectionDoubling(tick, trackInfo.getTrackNumber());
		}

		@Override
		protected boolean isNotePlayable(NoteEvent ne, int addition) {
			int midId = transposeNote(ne.note.id + addition, ne.getStartTick());

			if (midId < MidiConstants.LOWEST_NOTE_ID || midId > MidiConstants.HIGHEST_NOTE_ID)
				return false;

			if (part.isPercussionPart())
				return part.isDrumPlayable(trackInfo.getTrackNumber(), ne.note.id);

			if (ne instanceof BentMidiNoteEvent) {
				BentMidiNoteEvent be = (BentMidiNoteEvent) ne;
				int lowId = transposeNote(be.getMinNote() + addition, ne.getStartTick());
				int highId = transposeNote(be.getMaxNote() + addition, ne.getStartTick());
				if (lowId < MidiConstants.LOWEST_NOTE_ID || lowId > MidiConstants.HIGHEST_NOTE_ID)
					return false;
				if (highId < MidiConstants.LOWEST_NOTE_ID || highId > MidiConstants.HIGHEST_NOTE_ID)
					return false;
				return part.getInstrument().isPlayable(highId) && part.getInstrument().isPlayable(lowId);
			}
			return part.getInstrument().isPlayable(midId);
		}

		@Override
		boolean isOutOfLimit(int noteId, long startTick) {
			Pair<Integer, Integer> limits = part.getSectionPitchLimits(trackInfo.getTrackNumber(), startTick);
			int adjusted = noteId + part.getInstrument().octaveDelta * 12;
			return adjusted < limits.first || adjusted > limits.second;
		}

		@Override
		protected boolean isShowingNotesOn() {
			if (sequencer.isRunning())
				return ((NoteFilterSequencerWrapper) sequencer).isTrackActive(trackInfo.getTrackNumber());
			if (abcSequencer != null && abcSequencer.isRunning())
				return true;
			return false;
		}
	}
}
