package com.digero.maestro.abc;

public class PartSection {
	public int octaveStep = 0;
	public int volumeStep = 0;
	public int fade = 0;
	public boolean resetVelocities = false;
	public boolean silence = false;
	public int dialogLine = -1;
	public Boolean[] doubling = { false, false, false, false };

	// inclusive:
	public int startBar = 0;
	public int endBar = 0;
}
