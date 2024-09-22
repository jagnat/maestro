package com.digero.maestro.abc;

public class TuneLine {
	public int seminoteStep = 0;
//	public boolean remove = false;
	public int dialogLine = -1;
	public int tempo = 0;
	public int fade = 0;

	// inclusive:
	public float startBar = 0;
	public long startTick = -1L;
	
	// exclusive:
	public float endBar = 0;
	public long endTick = -1L;

	@Override
	public String toString() {
		return "Tune Line " + startBar + " to " + endBar + ": tempo=" + tempo + " seminoteStep=" + seminoteStep
				 + " fade=" + fade + " dialogLine=" + dialogLine;
	}
}
