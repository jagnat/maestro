package com.digero.maestro.view;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;

import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
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
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.PartAutoNumberer;
import com.digero.maestro.midi.TrackInfo;
import com.digero.maestro.view.TrackPanel.TrackDimensions;

@SuppressWarnings("serial")
public class PartPanel extends JPanel implements ICompileConstants, TableLayoutConstants {
	private static final int HGAP = 4;
	private static final int VGAP = 4;

	private AbcPart abcPart;
	private PartAutoNumberer partAutoNumberer;
	private NoteFilterSequencerWrapper sequencer;
	private SequencerWrapper abcSequencer;
	private boolean isAbcPreviewMode = false;

	private JSpinner numberSpinner;
	private SpinnerNumberModel numberSpinnerModel;
	private JButton numberSettingsButton;
	private JTextField nameTextField;
	private JComboBox<LotroInstrument> instrumentComboBox;
	private JLabel messageLabel;

	private JScrollPane trackScrollPane;

	private JPanel trackListPanel;
	private GroupLayout trackListLayout;
	private GroupLayout.Group trackListVGroup;
	private GroupLayout.Group trackListHGroup;

	private boolean initialized = false;

	private boolean zoomed = false;
	private boolean noteVisible = false;
	private JTextArea noteContent = new JTextArea();
	private JScrollPane notePanel = null;
	private boolean syncUpdate = false;

	public PartPanel(NoteFilterSequencerWrapper sequencer, PartAutoNumberer partAutoNumberer, SequencerWrapper abcSequencer) {
		super(new TableLayout(//
				new double[] { FILL, PREFERRED }, //
				new double[] { PREFERRED, FILL }));

		TableLayout layout = (TableLayout) getLayout();
		layout.setHGap(HGAP);
		layout.setVGap(VGAP);

		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER.get()));

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
				updateTracksVisible();
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

		trackListPanel = new JPanel();
		trackListLayout = new GroupLayout(trackListPanel);
		trackListLayout.setVerticalGroup(trackListVGroup = trackListLayout.createSequentialGroup());
		trackListLayout.setHorizontalGroup(trackListHGroup = trackListLayout.createParallelGroup());
		trackListLayout.setHonorsVisibility(true);
		trackListPanel.setLayout(trackListLayout);
		trackListPanel.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());

		trackScrollPane = new JScrollPane(trackListPanel, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_AS_NEEDED);
		// Remove focus from text boxes if area under midi tracks is clicked
		trackScrollPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		});

		messageLabel = new JLabel();
		messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
		messageLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 20));
		messageLabel.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
		messageLabel.setVisible(false);

		notePanel = new JScrollPane(noteContent, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
		notePanel.setPreferredSize(new Dimension(225, 200));
		noteContent.setLineWrap(true);
		noteContent.setWrapStyleWord(true);
		noteContent.setTabSize(4);

		add(dataPanel, "0, 0");
		add(messageLabel, "0, 1, C, C");
		add(trackScrollPane, "0, 1");

		// Remove focus if any empty space in the window is clicked
		MouseAdapter listenForFocus = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		};
		addMouseListener(listenForFocus);

		setAbcPart(null);
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

	public void settingsChanged() {
		numberSpinnerModel.setStepSize(partAutoNumberer.getIncrement());
	}

	public void setAbcPart(AbcPart abcPart) {
		messageLabel.setVisible(false);

		if (this.abcPart == abcPart && initialized)
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
				TempoPanel tempoPanel = new TempoPanel(abcPart.getSequenceInfo(), sequencer, abcSequencer, abcPart.getAbcSong());
				tempoPanel.setAbcPreviewMode(isAbcPreviewMode);
				trackScrollPane.getVerticalScrollBar().setUnitIncrement(tempoPanel.getPreferredSize().height);
				trackListVGroup.addComponent(tempoPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE);
				trackListHGroup.addComponent(tempoPanel);
			}

			for (TrackInfo track : abcPart.getSequenceInfo().getTrackList()) {
				int trackNumber = track.getTrackNumber();
				if (track.hasEvents()) {
					TrackPanel trackPanel = new TrackPanel(track, sequencer, abcPart, abcSequencer);
					trackPanel.setAbcPreviewMode(isAbcPreviewMode);
					trackScrollPane.getVerticalScrollBar().setUnitIncrement(trackPanel.getPreferredSize().height);
					trackListVGroup.addComponent(trackPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE);
					trackListHGroup.addComponent(trackPanel);

					if (MUTE_DISABLED_TRACKS)
						sequencer.setTrackMute(trackNumber, !abcPart.isTrackEnabled(trackNumber));
				}

				if (!MUTE_DISABLED_TRACKS)
					sequencer.setTrackMute(trackNumber, false);

				sequencer.setTrackSolo(trackNumber, false);
			}
		}

		this.abcPart = abcPart;
		if (this.abcPart != null) {
			this.abcPart.addAbcListener(abcPartListener);
		}

		updateTracksVisible();
		validate();
		repaint();
	}

	public void tuneUpdated(AbcPart abcPart) {
		messageLabel.setVisible(false);

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
				TempoPanel tempoPanel = new TempoPanel(abcPart.getSequenceInfo(), sequencer, abcSequencer, abcPart.getAbcSong());
				tempoPanel.setAbcPreviewMode(isAbcPreviewMode);
				trackScrollPane.getVerticalScrollBar().setUnitIncrement(tempoPanel.getPreferredSize().height);
				trackListVGroup.addComponent(tempoPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE);
				trackListHGroup.addComponent(tempoPanel);
			}

			for (TrackInfo track : abcPart.getSequenceInfo().getTrackList()) {
				int trackNumber = track.getTrackNumber();
				if (track.hasEvents()) {
					TrackPanel trackPanel = new TrackPanel(track, sequencer, abcPart, abcSequencer);
					trackPanel.setAbcPreviewMode(isAbcPreviewMode);
					trackScrollPane.getVerticalScrollBar().setUnitIncrement(trackPanel.getPreferredSize().height);
					trackListVGroup.addComponent(trackPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE);
					trackListHGroup.addComponent(trackPanel);

					if (MUTE_DISABLED_TRACKS)
						sequencer.setTrackMute(trackNumber, !abcPart.isTrackEnabled(trackNumber));
				}

				if (!MUTE_DISABLED_TRACKS)
					sequencer.setTrackMute(trackNumber, false);

				sequencer.setTrackSolo(trackNumber, false);
			}
		}

		this.abcPart = abcPart;
		if (this.abcPart != null) {
			this.abcPart.addAbcListener(abcPartListener);
		}

		updateTracksVisible();
		validate();
		repaint();
	}

	public AbcPart getAbcPart() {
		return abcPart;
	}

	public void setAbcPreviewMode(boolean isAbcPreviewMode) {
		if (this.isAbcPreviewMode != isAbcPreviewMode) {
			this.isAbcPreviewMode = isAbcPreviewMode;
			for (Component child : trackListPanel.getComponents()) {
				if (child instanceof TrackPanel) {
					((TrackPanel) child).setAbcPreviewMode(isAbcPreviewMode);
				} else if (child instanceof DrumPanel) {
					((DrumPanel) child).setAbcPreviewMode(isAbcPreviewMode);
				} else if (child instanceof TempoPanel) {
					((TempoPanel) child).setAbcPreviewMode(isAbcPreviewMode);
				}
			}
		}
	}

	public boolean isAbcPreviewMode() {
		return isAbcPreviewMode;
	}

	public void showInfoMessage(String message) {
		setAbcPart(null);

		messageLabel.setText(message);
		messageLabel.setVisible(true);
	}

	private void updateTracksVisible() {
		if (abcPart == null)
			return;

		boolean percussion = abcPart.getInstrument().isPercussion;
		boolean setHeight = false;

		for (Component child : trackListPanel.getComponents()) {
			if (child instanceof TrackPanel) {
				TrackPanel trackPanel = (TrackPanel) child;
				child.setEnabled(percussion || trackPanel.getTrackInfo().hasEvents());
				if (!setHeight && !percussion) {
					trackScrollPane.getVerticalScrollBar().setUnitIncrement(child.getPreferredSize().height);
					setHeight = true;
				}
			} else if (child instanceof DrumPanel) {
				child.setVisible(percussion);
				if (!setHeight && percussion) {
					trackScrollPane.getVerticalScrollBar().setUnitIncrement(child.getPreferredSize().height);
					setHeight = true;
				}
			}
		}
	}

	private void clearTrackListPanel() {
		for (Component child : trackListPanel.getComponents()) {
			if (child instanceof IDiscardable) {
				((IDiscardable) child).discard();
			}
		}
		trackListPanel.removeAll();
		trackListLayout.setVerticalGroup(trackListVGroup = trackListLayout.createSequentialGroup());
		trackListLayout.setHorizontalGroup(trackListHGroup = trackListLayout.createParallelGroup());
		zoomed = false;
	}

	public void setSequencer(NoteFilterSequencerWrapper sequencer) {
		AbcPart abcPartTmp = this.abcPart;
		setAbcPart(null);
		this.sequencer = sequencer;
		setAbcPart(abcPartTmp);
	}

	public void commitAllFields() {
		try {
			numberSpinner.commitEdit();
		} catch (java.text.ParseException e) {
			// Ignore
		}
	}

	public void zoom() {
		// Notice that when instruement track selection is changed clearTrackListPanel()
		// will be called and view will be unzoomed.
		int horiz = 1920 * 3;
		try {
			int width = Toolkit.getDefaultToolkit().getScreenSize().width;
			if (width > 1920) {
				horiz = Math.max(horiz, width * 2);
			}
		} catch (java.awt.HeadlessException e) {

		}

		TrackDimensions dims = TrackPanel.calculateTrackDims();

		int scaledHeight = (int) (dims.rowHeight * 1.25);

		for (Component child : trackListPanel.getComponents()) {
			if (child instanceof TrackPanel) {
				if (!zoomed && child.getHeight() == dims.rowHeight + 1) {
					((TrackPanel) child).setVerticalSize(scaledHeight);
					child.setPreferredSize(new Dimension(horiz, scaledHeight + 1));
				} else {
					((TrackPanel) child).setVerticalSize(dims.rowHeight);
					child.setPreferredSize(null);
				}
				child.validate();
				child.invalidate();
			}
		}
		revalidate();
		repaint();
		zoomed = !zoomed;
	}

	public void noteToggle() {
		noteVisible(!noteVisible);
	}

	public void noteVisible(boolean vis) {
		noteVisible = vis;
		if (noteVisible) {
			add(notePanel, "1, 0, 1, 1, F, F");
		} else {
			remove(notePanel);
		}
		validate();
		repaint();
	}

	public String getNote() {
		return noteContent.getText();
	}

	public void setNote(String note) {
		noteContent.setText(note);
	}
}
