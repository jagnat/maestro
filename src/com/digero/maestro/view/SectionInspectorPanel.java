package com.digero.maestro.view;

import java.awt.BorderLayout;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.PartSection;

import net.miginfocom.swing.MigLayout;

/**
 * Prototype for the "Part Editing" tab: a shared inspector docked at the bottom of the
 * tab showing the options of the currently selected section. Edits apply immediately
 * through the same commit path the SectionEditor dialog uses.
 */
public class SectionInspectorPanel extends JPanel {
	public interface Host {
		/** Recompute sectionsModified and fire sectionEdited for the track. */
		void commitSections(int track);

		void sectionDeleted(int track, PartSection ps);
	}

	private final Host host;

	private AbcPart abcPart;
	private int track = -1;
	private PartSection section;
	private boolean updating = false;

	private final JLabel titleLabel = new JLabel("No section selected");
	private final JLabel hintLabel = new JLabel(
			"Click a section in a strip to edit it. Drag on empty strip space to create one.");

	private final JSpinner startBarSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
	private final JSpinner endBarSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 100000, 1));
	private final JSpinner octaveSpinner = new JSpinner(new SpinnerNumberModel(0, -3, 3, 1));
	private final JSpinner volumeSpinner = new JSpinner(new SpinnerNumberModel(0, -127, 127, 8));
	private final JSpinner fadeSpinner = new JSpinner(new SpinnerNumberModel(0, -100, 100, 5));
	private final JCheckBox silenceBox = new JCheckBox("Silence");
	private final JCheckBox legatoBox = new JCheckBox("Legato");
	private final JCheckBox resetVelBox = new JCheckBox("Reset vel.");
	private final JCheckBox[] doublingBoxes = { new JCheckBox("2 dn"), new JCheckBox("1 dn"), new JCheckBox("1 up"),
			new JCheckBox("2 up") };
	private final JButton deleteButton = new JButton("Delete Section");

	public SectionInspectorPanel(Host host) {
		super(new BorderLayout());
		this.host = host;

		setBorder(BorderFactory.createTitledBorder("Section Inspector"));

		JPanel controls = new JPanel(new MigLayout("insets 2, gapx 10, wrap 20", "", ""));

		controls.add(new JLabel("Bars:"));
		controls.add(startBarSpinner);
		controls.add(new JLabel("to"));
		controls.add(endBarSpinner);
		controls.add(new JLabel("Octave:"), "gapx 16");
		controls.add(octaveSpinner);
		controls.add(new JLabel("Volume:"));
		controls.add(volumeSpinner);
		controls.add(new JLabel("Fade %:"));
		controls.add(fadeSpinner);
		controls.add(silenceBox, "gapx 16");
		controls.add(legatoBox);
		controls.add(resetVelBox);
		controls.add(new JLabel("Double:"), "gapx 16");
		for (JCheckBox box : doublingBoxes)
			controls.add(box);
		controls.add(deleteButton, "gapx 16");

		JPanel top = new JPanel(new MigLayout("insets 2 6 0 6, gapx 12"));
		top.add(titleLabel);
		top.add(hintLabel, "gapx 24");
		hintLabel.setEnabled(false);

		add(top, BorderLayout.NORTH);
		add(controls, BorderLayout.CENTER);

		startBarSpinner.addChangeListener(e -> applyBars());
		endBarSpinner.addChangeListener(e -> applyBars());
		octaveSpinner.addChangeListener(e -> apply(ps -> ps.octaveStep = (Integer) octaveSpinner.getValue()));
		volumeSpinner.addChangeListener(e -> apply(ps -> ps.volumeStep = (Integer) volumeSpinner.getValue()));
		fadeSpinner.addChangeListener(e -> apply(ps -> ps.fade = (Integer) fadeSpinner.getValue()));
		silenceBox.addActionListener(e -> apply(ps -> ps.silence = silenceBox.isSelected()));
		legatoBox.addActionListener(e -> apply(ps -> ps.legato = legatoBox.isSelected()));
		resetVelBox.addActionListener(e -> apply(ps -> ps.resetVelocities = resetVelBox.isSelected()));
		for (int i = 0; i < 4; i++) {
			final int idx = i;
			doublingBoxes[i].addActionListener(e -> apply(ps -> ps.doubling[idx] = doublingBoxes[idx].isSelected()));
		}
		deleteButton.addActionListener(e -> {
			if (section != null && abcPart != null) {
				PartSection doomed = section;
				int doomedTrack = track;
				setSection(null, -1, null);
				host.sectionDeleted(doomedTrack, doomed);
			}
		});

		setSection(null, -1, null);
	}

	private interface SectionMutator {
		void mutate(PartSection ps);
	}

	private void apply(SectionMutator mutator) {
		if (updating || section == null || abcPart == null)
			return;
		mutator.mutate(section);
		host.commitSections(track);
	}

	private void applyBars() {
		if (updating || section == null || abcPart == null)
			return;

		float newStart = ((Number) startBarSpinner.getValue()).floatValue();
		float newEnd = ((Number) endBarSpinner.getValue()).floatValue();

		TreeMap<Float, PartSection> tree = abcPart.sections.get(track);
		boolean valid = newStart < newEnd;
		if (valid && tree != null) {
			for (PartSection other : tree.values()) {
				if (other == section)
					continue;
				if (!(newStart >= other.endBar || newEnd <= other.startBar)) {
					valid = false;
					break;
				}
			}
		}

		if (!valid) {
			// Revert the spinners to the section's current values
			updating = true;
			startBarSpinner.setValue((int) section.startBar);
			endBarSpinner.setValue((int) section.endBar);
			updating = false;
			return;
		}

		if (tree != null)
			tree.remove(section.startBar);
		section.startBar = newStart;
		section.endBar = newEnd;
		if (tree != null)
			tree.put(section.startBar, section);
		host.commitSections(track);
		updateTitle();
	}

	public void setSection(AbcPart part, int track, PartSection ps) {
		this.abcPart = part;
		this.track = track;
		this.section = ps;

		updating = true;
		boolean has = (ps != null);
		startBarSpinner.setEnabled(has);
		endBarSpinner.setEnabled(has);
		octaveSpinner.setEnabled(has);
		volumeSpinner.setEnabled(has);
		fadeSpinner.setEnabled(has);
		silenceBox.setEnabled(has);
		legatoBox.setEnabled(has);
		resetVelBox.setEnabled(has);
		for (JCheckBox box : doublingBoxes)
			box.setEnabled(has);
		deleteButton.setEnabled(has);

		if (has) {
			startBarSpinner.setValue((int) ps.startBar);
			endBarSpinner.setValue((int) ps.endBar);
			octaveSpinner.setValue(ps.octaveStep);
			volumeSpinner.setValue(ps.volumeStep);
			fadeSpinner.setValue(ps.fade);
			silenceBox.setSelected(ps.silence);
			legatoBox.setSelected(ps.legato);
			resetVelBox.setSelected(ps.resetVelocities);
			for (int i = 0; i < 4; i++)
				doublingBoxes[i].setSelected(ps.doubling[i]);
			updateTitle();
		} else {
			octaveSpinner.setValue(0);
			volumeSpinner.setValue(0);
			fadeSpinner.setValue(0);
			silenceBox.setSelected(false);
			legatoBox.setSelected(false);
			resetVelBox.setSelected(false);
			for (JCheckBox box : doublingBoxes)
				box.setSelected(false);
			titleLabel.setText("No section selected");
		}
		updating = false;
	}

	private void updateTitle() {
		if (section == null || abcPart == null)
			return;
		String trackName = "track " + track;
		if (abcPart.getSequenceInfo() != null && track < abcPart.getSequenceInfo().getTrackList().size())
			trackName = abcPart.getSequenceInfo().getTrackList().get(track).getName();
		titleLabel.setText("<html><b>" + trackName + "</b> — bars " + (int) section.startBar + " to "
				+ (int) section.endBar + "</html>");
	}

	public PartSection getSection() {
		return section;
	}

	public int getTrack() {
		return track;
	}
}
