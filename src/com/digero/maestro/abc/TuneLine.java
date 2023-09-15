package com.digero.maestro.abc;

public class TuneLine {
	public int seminoteStep = 0;
//	public boolean remove = false;
	public int dialogLine = -1;
	public int tempo = 0;

	// inclusive:
	public int startBar = 0;
	public int endBar = 0;

	@Override
	public String toString() {
		return "Tune Line " + startBar + " to " + endBar + ": tempo=" + tempo + " seminoteStep=" + seminoteStep
				+ " dialogLine=" + dialogLine;
	}
}
