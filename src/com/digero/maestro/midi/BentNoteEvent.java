package com.digero.maestro.midi;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map.Entry;

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
	
	public Integer getBend(long tick) {
		Entry<Long, Integer> entry = bends.floorEntry(tick);
		if (entry == null) return null;
		return entry.getValue();
	}
	
	public int getMaxBend() {
		if (cacheMax != -1) return cacheMax;
		int maxBend = -128;
		for (int value : bends.values()) {
			if (value > maxBend) {
				maxBend = value;
			}
		}
		cacheMax = maxBend;
		return cacheMax;
	}
	
	public int getMinBend() {
		if (cacheMin != -1) return cacheMin;
		int minBend = 128;
		for (int value : bends.values()) {
			if (value < minBend) {
				minBend = value;
			}
		}
		cacheMin = minBend;
		return cacheMin;
	}
	
	public int getMinNote() {
		getMinBend();
		return note.id + cacheMin;
	}
	
	public int getMaxNote() {
		getMaxBend();
		return note.id + cacheMax;
	}
	
	/**
	 * Split this bent note into smaller note events.
	 * Will not take actual grid into consideration,
	 * so is only for bent notes that has a big range.
	 * 
	 * @return list of NoteEvents
	 */
	public List<NoteEvent> split() {
		List<NoteEvent> splits = new ArrayList<>();
		Note currNote = Note.fromId(note.id + bends.get(getStartTick()));
		if (currNote == null) return new ArrayList<>();// Too bad, lets cancel
		NoteEvent curr = new NoteEvent(currNote, velocity, getStartTick(), getEndTick(), getTempoCache());
		curr.setMidiPan(midiPan);
		for (Long tick : bends.keySet()) {
			if (tick == getStartTick()) continue;
			curr.setEndTick(tick);
			splits.add(curr);
			currNote = Note.fromId(note.id + bends.get(tick));
			if (currNote == null) return new ArrayList<>();// Too bad, lets cancel
			curr = new NoteEvent(currNote, velocity, tick, getEndTick(), getTempoCache());
		}
		splits.add(curr);
		return splits;
	}
}