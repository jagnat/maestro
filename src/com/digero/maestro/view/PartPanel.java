package com.digero.maestro.view;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.text.ParseException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.icons.IconLoader;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.view.ColorTable;
import com.digero.common.view.InstrumentComboBox;
import com.digero.common.view.PatchedJScrollPane;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.PartAutoNumberer;
import com.digero.maestro.midi.TrackInfo;
import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

/**
 * We should really rename this class. It has nothing to do with parts. Very confusing. ~Aifel
 * 
 * This is the panel that holds the tracks and their controls.
 * It also hold the part header at top, and tempopanel and histogram panel.
 * 
 */
@SuppressWarnings("serial")
public class PartPanel extends JPanel implements ICompileConstants, TableLayoutConstants {
	private static final int HGAP = 4;
	private static final int VGAP = 4;

	private AbcPart abcPart;// The currently selected abcPart in left part panel
	private PartAutoNumberer partAutoNumberer;
	private NoteFilterSequencerWrapper sequencer;
	private SequencerWrapper abcSequencer;
	private boolean isAbcPreviewMode = false;
	private boolean showMaxPolyphony = false;

	private JSpinner numberSpinner;
	private SpinnerNumberModel numberSpinnerModel;
	private JButton numberSettingsButton;
	private JTextField nameTextField;
	private JComboBox<LotroInstrument> instrumentComboBox;
	private JLabel messageLabel;
	
	private JPanel splitPanel;
	
	private JPanel controlPanel;
	
	private PatchedJScrollPane noteGraphScrollPane;
	private JPanel noteGraphPanel;

	private boolean initialized = false;

	private boolean zoomed = false;
	private boolean textnoteVisible = false;
	private JTextArea noteContent = new JTextArea();
	private JScrollPane notePanel = null;
	private boolean syncUpdate = false;

	public PartPanel(NoteFilterSequencerWrapper sequencer, PartAutoNumberer partAutoNumberer,
			SequencerWrapper abcSequencer, boolean showMaxPolyphony) {
		super(new TableLayout(//
				new double[] { FILL, PREFERRED },  // x  tracks, note
				new double[] { PREFERRED, FILL }));// y  part-header, tracks

		TableLayout layout = (TableLayout) getLayout();
		layout.setHGap(HGAP);
		layout.setVGap(VGAP);

		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER.get()));
		
		this.showMaxPolyphony = showMaxPolyphony;

		this.sequencer = sequencer;
		this.abcSequencer = abcSequencer;
		this.partAutoNumberer = partAutoNumberer;

		numberSpinnerModel = new SpinnerNumberModel(0, 0, 999, partAutoNumberer.getIncrement());
		numberSpinner = new JSpinner(numberSpinnerModel);
		numberSpinner.addChangeListener(e -> {
			if (abcPart != null)
				PartPanel.this.partAutoNumberer.setPartNumber(abcPart, (Integer) numberSpinner.getValue());
		});

		numberSettingsButton = new JButton(IconLoader.getImageIcon("gear_16.png"));
		numberSettingsButton.setMargin(new Insets(0, 0, 0, 0));
		numberSettingsButton.setToolTipText("Automatic part numbering options");
		numberSettingsButton.setVisible(false);

		nameTextField = new JTextField(32);
		nameTextField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				if (abcPart != null && !syncUpdate)
					abcPart.setTitle(nameTextField.getText());
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				if (abcPart != null && !syncUpdate)
					abcPart.setTitle(nameTextField.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				if (abcPart != null && !syncUpdate)
					abcPart.setTitle(nameTextField.getText());
			}
		});

		instrumentComboBox = new InstrumentComboBox();
		instrumentComboBox.addActionListener(e -> {
			if (abcPart != null) {
				LotroInstrument newInstrument = (LotroInstrument) instrumentComboBox.getSelectedItem();
				LotroInstrument oldInstrument = abcPart.getInstrument();
				PartPanel.this.partAutoNumberer.setInstrument(abcPart, newInstrument);
				abcPart.replaceTitleInstrument(newInstrument, oldInstrument);
				nameTextField.setText(abcPart.getTitle());
				//updateTracksVisible();
			}
		});

		JPanel dataPanel = new JPanel(new BorderLayout(0, VGAP));
		JPanel dataPanel2 = new JPanel(new FlowLayout(FlowLayout.LEFT, HGAP, 0));
		dataPanel2.add(new JLabel("X:"));
		dataPanel2.add(numberSpinner);
		dataPanel2.add(numberSettingsButton);
		dataPanel2.add(new JLabel(" I:"));
		dataPanel2.add(instrumentComboBox);
		dataPanel2.add(new JLabel(" Part name:"));
		dataPanel.add(dataPanel2, BorderLayout.WEST);
		dataPanel.add(nameTextField, BorderLayout.CENTER);

		boolean dbg = false;
//		dbg = true;
//		splitPanel = new JPanel(new MigLayout((dbg? "debug, " : "") + "wrap 2, gap 0, ins 0, novisualpadding, filly", "[]0[grow]"));
		splitPanel = new JPanel(new TableLayout(new double[] { PREFERRED, FILL }, //
				new double[] { FILL }));
		splitPanel.setBorder(BorderFactory.createEmptyBorder());
		splitPanel.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
		
		//controlPanel = new JPanel(new MigLayout((dbg? "debug, " : "") + "wrap 1, gap 0, novisualpadding, ins 0"));
		noteGraphPanel = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				int widestWidth = (int) (noteGraphScrollPane.getViewport().getExtentSize().width * graphLayout.getZoomHorizontal());
				int height = controlLayout.getPreferredHeight();
				return new Dimension(widestWidth, height);
			}
		};
		noteGraphScrollPane = new PatchedJScrollPane(noteGraphPanel, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_AS_NEEDED);
		controlLayout = new ControlLayout(TrackPanel.calculateTrackDims().rowHeight + 1, noteGraphPanel, noteGraphScrollPane);
		controlPanel = new JPanel(controlLayout) {
			@Override
			public Dimension getPreferredSize() {
				int widestWidth = 0;
				for (Component c : getComponents()) {
					if (c.isVisible()) {
						Dimension cDim = c.getPreferredSize();
						if (cDim.width > widestWidth) {
							widestWidth = cDim.width;
						}
					}
				}
				return new Dimension(widestWidth, controlLayout.getPreferredHeight());
			}
		};
		controlPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0)); // top, left, bottom, right
		controlPanel.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
		
		graphLayout = new GraphLayout(TrackPanel.calculateTrackDims().rowHeight + 1, controlLayout);
		graphLayout.setViewport(noteGraphScrollPane.getViewport());
		noteGraphPanel.setLayout(graphLayout);
		noteGraphPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0)); // top, left, bottom, right

		noteGraphPanel.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
		
		
		noteGraphScrollPane.setBorder(BorderFactory.createEmptyBorder());
		noteGraphScrollPane.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, new JPanel());
		noteGraphScrollPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		});
		noteGraphScrollPane.getViewport().addChangeListener(ch -> {
			controlPanel.invalidate();
			validate();
			repaint();
		});
		
		// Link control scroll bar model to note graph scroll bar
		// so they're both controlled by note graph scroll bar
		JScrollBar noteGraphBar = noteGraphScrollPane.getVerticalScrollBar();		
		noteGraphBar.setUnitIncrement(TrackPanel.calculateTrackDims().rowHeight / 2);

		
		splitPanel.add(controlPanel, "0, 0");
		splitPanel.add(noteGraphScrollPane, "1, 0, f, f");//noteGraphScrollPane
		
//		splitPanel.add(controlScrollPane, "top");
//		splitPanel.add(noteGraphScrollPane, "top");
		
//		horizInnerScrollPane.add()
		
		
		messageLabel = new JLabel();
		messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
		messageLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 20));
		messageLabel.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
		messageLabel.setVisible(false);

		// notePanel is the textfield with project notes
		notePanel = new JScrollPane(noteContent, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
		notePanel.setPreferredSize(new Dimension(225, 200));
		noteContent.setLineWrap(true);
		noteContent.setWrapStyleWord(true);
		noteContent.setTabSize(4);

		add(dataPanel, "0, 0");
		add(messageLabel, "0, 1, C, C");
		add(splitPanel, "0, 1");

		JPanel t = this; 
		// Remove focus if any empty space in the window is clicked
		MouseAdapter listenForFocus = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
			
			@Override
			public void mouseWheelMoved(MouseWheelEvent arg0) {
				noteGraphScrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(t, arg0, noteGraphScrollPane));
			}
		};
		addMouseListener(listenForFocus);
		addMouseWheelListener(listenForFocus);

		setAbcPart(null, false);
		initialized = true;
	}

	public void setNewTitle(AbcPart thePart) {
		if (thePart != abcPart || nameTextField.getText().equals(thePart.getTitle()))
			return;
		syncUpdate = true;
		nameTextField.setText(thePart.getTitle());
		syncUpdate = false;
	}

	public void addSettingsActionListener(ActionListener listener) {
		numberSettingsButton.addActionListener(listener);
		numberSettingsButton.setVisible(true);
	}

	private Listener<AbcPartEvent> abcPartListener = e -> {
		if (e.getProperty() == AbcPartProperty.PART_NUMBER) {
			numberSpinner.setValue(abcPart.getPartNumber());
		}
	};
	private ControlLayout controlLayout;
	private GraphLayout graphLayout;

	public void settingsChanged() {
		numberSpinnerModel.setStepSize(partAutoNumberer.getIncrement());
	}

	public void setAbcPart(AbcPart abcPart, boolean force) {
		messageLabel.setVisible(false);

		if (this.abcPart == abcPart && initialized && !force)
			return;

		if (this.abcPart != null) {
			try {
				numberSpinner.commitEdit();
			} catch (ParseException e) {
			}
			this.abcPart.removeAbcListener(abcPartListener);
			this.abcPart = null;
		}

		if (abcPart == null) {
			numberSpinner.setEnabled(false);
			nameTextField.setEnabled(false);
			instrumentComboBox.setEnabled(false);

			numberSpinner.setValue(0);
			nameTextField.setText("");
			instrumentComboBox.setSelectedItem(LotroInstrument.DEFAULT_INSTRUMENT);

			clearTrackListPanel();
		} else {
			numberSpinner.setEnabled(true);
			nameTextField.setEnabled(true);
			instrumentComboBox.setEnabled(true);

			numberSpinner.setValue(abcPart.getPartNumber());
			nameTextField.setText(abcPart.getTitle());
			instrumentComboBox.setSelectedItem(abcPart.getInstrument());

			clearTrackListPanel();

			// Add the tempo panel if this song contains tempo changes
			if (abcPart.getSequenceInfo().hasTempoChanges() || abcPart.getAbcSong().tuneBarsModified != null) {
				TempoPanel tempoPanel = new TempoPanel(abcPart.getSequenceInfo(), sequencer, abcSequencer,
						abcPart.getAbcSong());
				tempoPanel.setAbcPreviewMode(isAbcPreviewMode);
				tempoPanel.revalidate();
				controlPanel.add(tempoPanel,"x");
				noteGraphPanel.add(tempoPanel.getNoteGraph(),"x");
			}
			
			// Add the histogram panel
			HistogramPanel histogramPanel = new HistogramPanel(abcPart.getSequenceInfo(), sequencer, abcSequencer,
						abcPart.getAbcSong());
			histogramPanel.setAbcPreviewMode(isAbcPreviewMode, showMaxPolyphony);
			histogramPanel.revalidate();
			controlPanel.add(histogramPanel,"x");
			noteGraphPanel.add(histogramPanel.getNoteGraph(),"x");

			// Add the tracks and note graphs
			for (TrackInfo track : abcPart.getSequenceInfo().getTrackList()) {
				int trackNumber = track.getTrackNumber();
				if (track.hasEvents()) {
					TrackPanel trackPanel = new TrackPanel(track, sequencer, abcPart, abcSequencer, controlLayout);
					trackPanel.setAbcPreviewMode(isAbcPreviewMode);
					controlPanel.add(trackPanel,"x");
					noteGraphPanel.add(trackPanel.getNoteGraph(),"x");
					
//					if (trackPanel.hasDrumPanels()) {
//						ArrayList<DrumPanel> drums = trackPanel.getDrumPanels();
//						for (DrumPanel dp : drums) {
//							noteGraphPanel.add(dp.getNoteGraph(), "growx");
//						}
//					}

					if (MUTE_DISABLED_TRACKS)
						sequencer.setTrackMute(trackNumber, !abcPart.isTrackEnabled(trackNumber));
				}

				if (!MUTE_DISABLED_TRACKS)
					sequencer.setTrackMute(trackNumber, false);

				sequencer.setTrackSolo(trackNumber, false);
			}
			
			// add dummy space at the end to fix scroll bar calcuation swing bug
			
			int scrollbarHeight = new JScrollPane().getHorizontalScrollBar().getPreferredSize().height;
			
			class Dummy extends JPanel implements PartPanelItem {
				@Override
				public JPanel getNoteGraph() {
					return null;
				}

				@Override
				public boolean isVerticalZoomForbidden() {
					return true;
				}				
			}			
			Dummy dummy1 = new Dummy();
			dummy1.setPreferredSize(new Dimension(100, scrollbarHeight * 2));
			dummy1.setOpaque(true);
			dummy1.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
			controlPanel.add(dummy1, "x");
			
			JPanel dummy2 = new JPanel();
			dummy2.setPreferredSize(new Dimension(100, scrollbarHeight * 2));
			dummy2.setOpaque(true);
			dummy2.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
			noteGraphPanel.add(dummy2,"x");
		}

		this.abcPart = abcPart;
		if (this.abcPart != null) {
			this.abcPart.addAbcListener(abcPartListener);
		}

		//updateTracksVisible();
		validate();
		repaint();
	}

	public AbcPart getAbcPart() {
		return abcPart;
	}

	public void setAbcPreviewMode(boolean isAbcPreviewMode) {
		//if (this.isAbcPreviewMode != isAbcPreviewMode) {
			this.isAbcPreviewMode = isAbcPreviewMode;
			for (Component child : controlPanel.getComponents()) {
				if (child instanceof TrackPanel) {
					((TrackPanel) child).setAbcPreviewMode(isAbcPreviewMode);
				} else if (child instanceof DrumPanel) {
					((DrumPanel) child).setAbcPreviewMode(isAbcPreviewMode);
				} else if (child instanceof TempoPanel) {
					((TempoPanel) child).setAbcPreviewMode(isAbcPreviewMode);
				} else if (child instanceof HistogramPanel) {
					((HistogramPanel) child).setAbcPreviewMode(isAbcPreviewMode, showMaxPolyphony);
				}
			}
		//}
	}

	public boolean isAbcPreviewMode() {
		return isAbcPreviewMode;
	}

	public void showInfoMessage(String message) {
		setAbcPart(null, false);

		messageLabel.setText(message);
		messageLabel.setVisible(true);
	}

	private void clearTrackListPanel() {
		for (Component child : controlPanel.getComponents()) {
			if (child instanceof IDiscardable) {
				((IDiscardable) child).discard();
			}
		}
		for (Component child : noteGraphPanel.getComponents()) {
			if (child instanceof IDiscardable) {
				((IDiscardable) child).discard();
			}
		}
		controlPanel.removeAll();
		noteGraphPanel.removeAll();
	}

	public void setSequencer(NoteFilterSequencerWrapper sequencer) {
		AbcPart abcPartTmp = this.abcPart;
		setAbcPart(null, false);
		this.sequencer = sequencer;
		setAbcPart(abcPartTmp, false);
	}

	public void commitAllFields() {
		try {
			numberSpinner.commitEdit();
		} catch (java.text.ParseException e) {
			// Ignore
		}
	}

	public void toggleZoom() {
		
		if (!zoomed) {
			graphLayout.setZoomHorizontal(6.0f);
			controlLayout.setZoomVertical(1.75f);
		} else {
			graphLayout.setZoomHorizontal(1.0f);
			controlLayout.setZoomVertical(1.0f);
		}
		
		zoomed = !zoomed;
		
		//Note invalidate does not invalidate sub components, hence why its called on the panels directly
		noteGraphPanel.invalidate();
		controlPanel.invalidate();
		revalidate();
		repaint();
	}
	
	public void unZoom() {
		// Called from ProjectFrame when song closes

		zoomed = false;
		
		graphLayout.setZoomHorizontal(1.0f);
		controlLayout.setZoomVertical(1.0f);
		
		//Note invalidate does not invalidate sub components, hence why its called on the panels directly
		noteGraphPanel.invalidate();
		controlPanel.invalidate();
		revalidate();
		repaint();
	}

	public void textnoteToggle() {
		textnoteVisible(!textnoteVisible);
	}

	public void textnoteVisible(boolean vis) {
		textnoteVisible = vis;
		if (textnoteVisible) {
			add(notePanel, "1, 0, 1, 1, F, F");
		} else {
			remove(notePanel);
		}
		validate();
		repaint();
	}

	public String getTextnote() {
		return noteContent.getText();
	}

	public void setTextnote(String note) {
		noteContent.setText(note);
	}

	public void setPolyphony(boolean showMaxPolyphony) {
		this.showMaxPolyphony = showMaxPolyphony;
		setAbcPreviewMode(isAbcPreviewMode());
	}
}
