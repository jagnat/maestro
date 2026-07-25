package com.digero.maestro.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.digero.common.midi.Note;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.view.ColorTable;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.TrackInfo;

/**
 * Prototype tab 3 of the arrangement view: one row per ABC part, each showing the
 * combined note graph of that part's enabled tracks. Whole-song features (tune editor)
 * and per-part settings (delay, etc.) live here.
 */
public class SongEditingTab extends JPanel {
	private static final int HEADER_WIDTH = 190;
	private static final int GRAPH_HEIGHT = 90;

	private final NoteFilterSequencerWrapper sequencer;
	private final SequencerWrapper abcSequencer;
	private final Consumer<AbcPart> partSelector;

	private AbcSong abcSong;

	private final JPanel rowsPanel;
	private final List<NoteGraph> graphs = new ArrayList<>();

	public SongEditingTab(NoteFilterSequencerWrapper sequencer, SequencerWrapper abcSequencer,
			Consumer<AbcPart> partSelector) {
		super(new BorderLayout(4, 4));
		this.sequencer = sequencer;
		this.abcSequencer = abcSequencer;
		this.partSelector = partSelector;

		JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		JLabel title = new JLabel("Song overview — one row per part");
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		toolbar.add(title);

		JButton tuneEditorButton = new JButton("Tune Editor…");
		tuneEditorButton.setToolTipText("Song-wide tempo/key/bar edits (currently the existing dialog)");
		tuneEditorButton.addActionListener(e -> {
			if (abcSong != null)
				TuneEditor.show((JFrame) SwingUtilities.getWindowAncestor(this), abcSong);
		});
		toolbar.add(tuneEditorButton);

		JLabel hint = new JLabel("Double-click a part header to jump to it in Part Editing");
		hint.setEnabled(false);
		toolbar.add(hint);

		rowsPanel = new JPanel();
		rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
		rowsPanel.setBackground(ColorTable.CENTER_BACKGROUND.get());

		JPanel rowsWrapper = new JPanel(new BorderLayout());
		rowsWrapper.setBackground(ColorTable.CENTER_BACKGROUND.get());
		rowsWrapper.add(rowsPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(rowsWrapper, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(24);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());

		add(toolbar, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
	}

	public void setAbcSong(AbcSong song) {
		this.abcSong = song;
		rebuild();
	}

	private void rebuild() {
		for (NoteGraph graph : graphs)
			graph.discard();
		graphs.clear();
		rowsPanel.removeAll();

		if (abcSong == null) {
			JLabel empty = new JLabel("No song loaded", SwingConstants.CENTER);
			empty.setBorder(BorderFactory.createEmptyBorder(40, 10, 40, 10));
			empty.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
			empty.setAlignmentX(CENTER_ALIGNMENT);
			rowsPanel.add(empty);
		} else {
			for (AbcPart part : abcSong.getParts())
				rowsPanel.add(buildPartRow(part));
		}

		revalidate();
		repaint();
	}

	private JPanel buildPartRow(AbcPart part) {
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 4));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel nameLabel = new JLabel(part.getPartNumber() + ". " + part.getTitle());
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
		JLabel instrumentLabel = new JLabel(part.getInstrument().toString());
		instrumentLabel.setFont(instrumentLabel.getFont().deriveFont(11f));
		JLabel tracksLabel = new JLabel(part.getEnabledTrackCount() + " track(s)");
		tracksLabel.setFont(tracksLabel.getFont().deriveFont(11f));

		JPanel delayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		delayPanel.setOpaque(false);
		JLabel delayLabel = new JLabel("Delay ms:");
		delayLabel.setFont(delayLabel.getFont().deriveFont(11f));
		JTextField delayField = new JTextField(Integer.toString(part.delay), 4);
		delayField.addActionListener(e -> commitDelay(part, delayField));
		delayField.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusLost(java.awt.event.FocusEvent e) {
				commitDelay(part, delayField);
			}
		});
		delayPanel.add(delayLabel);
		delayPanel.add(delayField);
		delayPanel.setAlignmentX(LEFT_ALIGNMENT);

		header.add(nameLabel);
		header.add(instrumentLabel);
		header.add(tracksLabel);
		header.add(Box.createVerticalStrut(4));
		header.add(delayPanel);

		header.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() >= 2)
					partSelector.accept(part);
			}
		});

		JPanel headerWrapper = new JPanel(new BorderLayout());
		headerWrapper.setOpaque(false);
		headerWrapper.setPreferredSize(new Dimension(HEADER_WIDTH, GRAPH_HEIGHT));
		headerWrapper.add(header, BorderLayout.NORTH);

		PartNoteGraph graph = new PartNoteGraph(part);
		graph.setPreferredSize(new Dimension(100, GRAPH_HEIGHT));
		graphs.add(graph);

		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorTable.CENTER_BACKGROUND.get());
		row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER_HORIZ.get()));
		row.add(headerWrapper, BorderLayout.WEST);
		row.add(graph, BorderLayout.CENTER);
		int rowHeight = GRAPH_HEIGHT + 1;
		row.setPreferredSize(new Dimension(100, rowHeight));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
		return row;
	}

	private void commitDelay(AbcPart part, JTextField field) {
		try {
			int value = Integer.parseInt(field.getText().trim());
			if (value != part.delay) {
				part.delay = value;
				part.delayEdited();
			}
		} catch (NumberFormatException ex) {
			field.setText(Integer.toString(part.delay));
		}
	}

	/**
	 * Combined note graph of all tracks enabled for a part. Prototype limitation:
	 * notes are drawn at their source pitch (per-track transposition is not applied,
	 * since the base graph pipeline doesn't attribute notes to tracks).
	 */
	private class PartNoteGraph extends NoteGraph {
		private final AbcPart part;

		PartNoteGraph(AbcPart part) {
			super(SongEditingTab.this.sequencer, part.getSequenceInfo(), Note.MIN_PLAYABLE.id - 12,
					Note.MAX_PLAYABLE.id + 12);
			this.part = part;
		}

		@Override
		protected List<NoteEvent> getEvents() {
			List<NoteEvent> list = new ArrayList<>();
			for (TrackInfo trackInfo : part.getSequenceInfo().getTrackList()) {
				if (trackInfo.hasEvents() && part.isTrackEnabled(trackInfo.getTrackNumber()))
					list.addAll(trackInfo.getEvents());
			}
			list.sort(Comparator.comparingLong(NoteEvent::getStartTick));
			return list;
		}

		@Override
		protected boolean isNotePlayable(NoteEvent ne, int addition) {
			if (part.isPercussionPart())
				return true;
			return part.getInstrument().isPlayable(ne.note.id + addition);
		}

		@Override
		protected boolean isActiveTrack() {
			return part.getEnabledTrackCount() > 0;
		}

		@Override
		protected boolean isShowingNotesOn() {
			return sequencer.isRunning() || (abcSequencer != null && abcSequencer.isRunning());
		}
	}
}
