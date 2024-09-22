package com.digero.common.midi;

public interface IBarNumberCache {
	int tickToBarNumber(long tick);
	long getBarToTick(int bar);
}
