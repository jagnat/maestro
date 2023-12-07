package com.digero.common.view;

import javax.swing.JLabel;

import com.digero.common.midi.IBarNumberCache;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;

@SuppressWarnings("serial")
public class BarNumberLabel extends JLabel implements Listener<SequencerEvent>, IDiscardable {
	private IBarNumberCache barNumberCache;
	private SequencerWrapper sequencer;
	private long initialOffsetTick = 0;

	public BarNumberLabel(SequencerWrapper sequencer, IBarNumberCache barNumberCache) {
		this.sequencer = sequencer;
		this.barNumberCache = barNumberCache;

		sequencer.addChangeListener(this);
	}

	@Override
	public void discard() {
		if (sequencer != null)
			sequencer.removeChangeListener(this);
	}

	public IBarNumberCache getBarNumberCache() {
		return barNumberCache;
	}

	public void setBarNumberCache(IBarNumberCache barNumberCache) {
		if (this.barNumberCache != barNumberCache) {
			this.barNumberCache = barNumberCache;
			update();
		}
	}

	public long getInitialOffsetTick() {
		return initialOffsetTick;
	}

	public void setInitialOffsetTick(long initialOffsetTick) {
		if (this.initialOffsetTick != initialOffsetTick) {
			this.initialOffsetTick = initialOffsetTick;
			update();
		}
	}

	@Override
	public void onEvent(SequencerEvent evt) {
		SequencerProperty p = evt.getProperty();
		if (p.isInMask(SequencerProperty.THUMB_POSITION_MASK | SequencerProperty.LENGTH.mask
				| SequencerProperty.TEMPO.mask | SequencerProperty.SEQUENCE.mask)) {
			update();
		}
	}

	private String lastPrintedBars = "-/-";
	
	public static String getBarString(SequencerWrapper sequencer, IBarNumberCache barNumberCache) {
		return getBarString(sequencer, barNumberCache, 0);
	}
	
	public static String getBarString(SequencerWrapper sequencer, IBarNumberCache barNumberCache, long initialOffsetTick) {
		if (barNumberCache == null || sequencer == null) {
			return "-/-";
		}
		
		long tickLength = Math.max(0, sequencer.getTickLength() - initialOffsetTick);
		long tick = Math.min(tickLength, sequencer.getThumbTick() - initialOffsetTick);

		int barNumber = (tick < 0) ? 0 : (barNumberCache.tickToBarNumber(tick) + 1);
		int barCount = barNumberCache.tickToBarNumber(tickLength) + 1;
		
		return barNumber + "/" +  barCount;
	}

	private void update() {
		if (barNumberCache == null) {
			if (!lastPrintedBars.equals("-/-")) {
				lastPrintedBars = "-/-";
				setText(lastPrintedBars);
			}
			return;
		}
		
		String bars = getBarString(sequencer, barNumberCache, initialOffsetTick);

		if (!bars.equals("-/-")) {
			lastPrintedBars = bars;
			setText(bars);
		}
	}
}
