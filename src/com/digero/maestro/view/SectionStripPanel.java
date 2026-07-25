package com.digero.maestro.view;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.swing.JPanel;

import com.digero.common.midi.SequencerWrapper;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.PartSection;
import com.digero.maestro.midi.SequenceDataCache;

/**
 * Prototype for the "Part Editing" tab: a horizontal strip under a track's note graph
 * showing that track's section edits as colored blocks, aligned with the note graph's
 * micros-based x axis. Click a block to select it (options appear in the shared
 * inspector); drag on empty space to create a new section snapped to whole bars.
 */
public class SectionStripPanel extends JPanel {
	public interface Host {
		void sectionClicked(int track, PartSection ps);

		void sectionCreated(int track, PartSection ps);

		PartSection getSelectedSection(int track);
	}

	public static final int STRIP_HEIGHT = 26;

	private final AbcPart abcPart;
	private final int track;
	private final SequencerWrapper sequencer;
	private final Host host;

	private boolean dragging = false;
	private int dragAnchorBar = -1;
	private int dragEndBar = -1;

	private static final Color GRID_COLOR = new Color(128, 128, 128, 70);
	private static final Color SECTION_FILL = new Color(90, 140, 210, 150);
	private static final Color SECTION_BORDER = new Color(90, 140, 210, 220);
	private static final Color SECTION_SELECTED_FILL = new Color(250, 170, 60, 170);
	private static final Color SECTION_SELECTED_BORDER = new Color(250, 170, 60, 255);
	private static final Color DRAG_FILL = new Color(120, 200, 120, 120);
	private static final Color BG = new Color(0, 0, 0, 40);

	public SectionStripPanel(AbcPart abcPart, int track, SequencerWrapper sequencer, Host host) {
		super(null);
		this.abcPart = abcPart;
		this.track = track;
		this.sequencer = sequencer;
		this.host = host;

		setOpaque(false);
		setPreferredSize(new Dimension(100, STRIP_HEIGHT));
		setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		setToolTipText("Click a section to edit it; drag on empty space to create a new section");

		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (sequencer.getLength() <= 0)
					return;
				PartSection hit = sectionAt(xToBarFloat(e.getX()));
				if (hit != null) {
					host.sectionClicked(track, hit);
				} else {
					dragging = true;
					dragAnchorBar = Math.max(0, Math.round(xToBarFloat(e.getX())));
					dragEndBar = dragAnchorBar;
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (!dragging)
					return;
				dragEndBar = Math.max(0, Math.round(xToBarFloat(e.getX())));
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (!dragging)
					return;
				dragging = false;
				int s = Math.min(dragAnchorBar, dragEndBar);
				int en = Math.max(dragAnchorBar, dragEndBar);
				repaint();
				if (en <= s)
					return;

				// Shrink the new range so it doesn't overlap existing sections
				TreeMap<Float, PartSection> tree = abcPart.sections.get(track);
				float start = s;
				float end = en;
				if (tree != null) {
					for (PartSection other : tree.values()) {
						if (start >= other.endBar || end <= other.startBar)
							continue;
						if (dragAnchorBar <= other.startBar)
							end = Math.min(end, other.startBar);
						else
							start = Math.max(start, other.endBar);
					}
				}
				if (start >= end)
					return;

				PartSection ps = new PartSection();
				ps.startBar = start;
				ps.endBar = end;
				host.sectionCreated(track, ps);
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	private SequenceDataCache data() {
		return abcPart.getSequenceInfo().getDataCache();
	}

	private float xToBarFloat(int x) {
		long lengthMicros = sequencer.getLength();
		if (lengthMicros <= 0 || getWidth() <= 0)
			return 0;
		long micros = (long) ((x / (double) getWidth()) * lengthMicros);
		return data().tickToBarNumberFloat(data().microsToTick(micros));
	}

	private int barToX(float bar) {
		long lengthMicros = sequencer.getLength();
		if (lengthMicros <= 0)
			return 0;
		long micros = data().tickToMicros(data().barFloatToTick(bar));
		return (int) ((micros / (double) lengthMicros) * getWidth());
	}

	private PartSection sectionAt(float bar) {
		TreeMap<Float, PartSection> tree = abcPart.sections.get(track);
		if (tree == null)
			return null;
		Entry<Float, PartSection> entry = tree.floorEntry(bar);
		if (entry != null && entry.getValue().endBar > bar)
			return entry.getValue();
		return null;
	}

	static String describe(PartSection ps) {
		StringBuilder sb = new StringBuilder();
		if (ps.silence)
			append(sb, "silent");
		if (ps.octaveStep != 0)
			append(sb, (ps.octaveStep > 0 ? "+" : "") + ps.octaveStep + " oct");
		if (ps.volumeStep != 0)
			append(sb, "vol " + (ps.volumeStep > 0 ? "+" : "") + ps.volumeStep);
		if (ps.fade != 0)
			append(sb, "fade " + ps.fade);
		if (ps.legato)
			append(sb, "legato");
		if (ps.resetVelocities)
			append(sb, "reset vel");
		if (ps.doubling[0] || ps.doubling[1] || ps.doubling[2] || ps.doubling[3])
			append(sb, "dbl");
		if (sb.length() == 0)
			sb.append("(no effects)");
		return sb.toString();
	}

	private static void append(StringBuilder sb, String s) {
		if (sb.length() > 0)
			sb.append(", ");
		sb.append(s);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int w = getWidth();
		int h = getHeight();

		g2.setColor(BG);
		g2.fillRect(0, 0, w, h);

		long lengthMicros = sequencer.getLength();
		if (lengthMicros <= 0)
			return;

		// Bar grid: pick a step so lines are at least ~8 px apart
		SequenceDataCache data = data();
		int barCount = data.tickToBarNumber(data.microsToTick(lengthMicros)) + 1;
		int step = 1;
		while (barCount / step > 0 && (w / (double) (barCount / (double) step)) < 8)
			step *= 2;

		g2.setColor(GRID_COLOR);
		for (int bar = 0; bar <= barCount; bar += step) {
			int x = barToX(bar);
			g2.drawLine(x, 0, x, h);
		}

		// Bar number labels where there's room (~48 px apart)
		int labelStep = step;
		while ((w / (double) (barCount / (double) labelStep)) < 48)
			labelStep *= 2;
		g2.setFont(getFont().deriveFont(9f));
		for (int bar = 0; bar <= barCount; bar += labelStep) {
			g2.drawString(Integer.toString(bar), barToX(bar) + 2, h - 3);
		}

		// Sections
		TreeMap<Float, PartSection> tree = abcPart.sections.get(track);
		PartSection selected = host.getSelectedSection(track);
		if (tree != null) {
			for (PartSection ps : tree.values()) {
				int x1 = barToX(ps.startBar);
				int x2 = barToX(ps.endBar);
				boolean sel = (ps == selected);
				g2.setColor(sel ? SECTION_SELECTED_FILL : SECTION_FILL);
				g2.fillRoundRect(x1, 1, Math.max(2, x2 - x1), h - 2, 6, 6);
				g2.setColor(sel ? SECTION_SELECTED_BORDER : SECTION_BORDER);
				g2.drawRoundRect(x1, 1, Math.max(2, x2 - x1), h - 2, 6, 6);

				String text = describe(ps);
				int textW = g2.getFontMetrics().stringWidth(text);
				if (textW < x2 - x1 - 6) {
					g2.setColor(Color.WHITE);
					g2.drawString(text, x1 + 4, h / 2 + 4);
				}
			}
		}

		// Drag preview
		if (dragging && dragEndBar != dragAnchorBar) {
			int x1 = barToX(Math.min(dragAnchorBar, dragEndBar));
			int x2 = barToX(Math.max(dragAnchorBar, dragEndBar));
			g2.setColor(DRAG_FILL);
			g2.fillRoundRect(x1, 1, x2 - x1, h - 2, 6, 6);
		}
	}
}
