package com.digero.maestro.midi;

import java.util.NavigableMap;
import java.util.TreeMap;

import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;

public class BentNoteEvent extends NoteEvent {
	
	private int cacheMin = -1;
	private int cacheMax = -1;
	
	public NavigableMap<Long, Integer> bends = new TreeMap<>();// tick -> relative seminote bend 

	public BentNoteEvent(Note note, int velocity, long startTick, long endTick, ITempoCache tempoCache) {
		super(note, velocity, startTick, endTick, tempoCache);
	}
	
	public void addBend(long tick, int bend) {
		bends.put(tick, bend);
		cacheMin = -1;
		cacheMax = -1;
	}
	
	public void setBends(NavigableMap<Long, Integer> bends) {
		this.bends = bends;
		cacheMin = -1;
		cacheMax = -1;
	}
	
	public int getMaxNoteId() {
		if (cacheMax != -1) return cacheMax;
		int maxBend = 0;// consider making this start from a lower number
		for (int value : bends.values()) {
			if (value > maxBend) {
				maxBend = value;
			}
		}
		cacheMax = note.id + maxBend;
		return cacheMax;
	}
	
	public int getMinNoteId() {
		if (cacheMin != -1) return cacheMin;
		int minBend = 0;// consider making this start from a higher number
		for (int value : bends.values()) {
			if (value < minBend) {
				minBend = value;
			}
		}
		cacheMin = note.id + minBend;
		return cacheMin;
	}
}
