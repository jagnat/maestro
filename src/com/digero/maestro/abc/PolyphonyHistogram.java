package com.digero.maestro.abc;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.List;
import java.util.TreeMap;

import com.digero.common.abc.LotroInstrumentSampleDuration;
import com.digero.common.midi.Note;
import com.digero.maestro.midi.NoteEvent;

public class PolyphonyHistogram {
	private static Map<AbcPart, TreeMap<Long, Integer>> histogramData = new HashMap<>();
	private static TreeMap<Long, Integer> sum = new TreeMap<>();
	private static boolean dirty = false;
	private static int max = 0;
	public static boolean enabled = false;// set to true to enable this system, set to false to save cpu power.

	/**
	 * Called from AbcExporter.java
	 * 
	 * @param part
	 * @param events
	 * @throws IOException 
	 */
	public static void count(AbcPart part, List<NoteEvent> events) throws IOException {
		if (!enabled) return;
		TreeMap<Long, Integer> partMap = new TreeMap<>();
		for (NoteEvent event : events) {
			if (event.note.id == Note.REST.id) {
				continue;
			}
			long start = event.getStartMicros() + part.delay*1000;
			long end = event.getEndMicros() + part.delay*1000;
			if (part.getInstrument().isSustainable(event.note.id)) {
				end += 200000L;// 200ms
			} else {
				Double seconds = LotroInstrumentSampleDuration.getDura(part.getInstrument().friendlyName, event.note.id);
				if (seconds == null) {
					System.err.println("Error: LotroInstrumentSampleDuration has no "+part.getInstrument().friendlyName+" with note "+event.note.id);
					seconds = 1.0d;
				}
				long dura = (long) (1000000L * seconds);
				end = start + dura;
			}
			Integer oldStart = partMap.get(start);
			if (oldStart == null) {
				oldStart = 0;
			}
			oldStart += 1;
			partMap.put(start, oldStart);
			Integer oldEnd = partMap.get(end);
			if (oldEnd == null) {
				oldEnd = 0;
			}
			oldEnd -= 1;
			partMap.put(end, oldEnd);
		}
		histogramData.put(part, partMap);
		dirty = true;
	}
	
	/**
	 * Expensive method, so only run when needed.
	 * 
	 * @param song
	 */
	public static void sumUp(AbcSong song) {
		sum = new TreeMap<>();
		max = 0;
		Set<AbcPart> partSet = new HashSet<>(histogramData.keySet());
		Set<TreeMap<Long, Integer>> treeSet = new HashSet<TreeMap<Long, Integer>>();
		for (AbcPart part : partSet) {
			if (part.discarded) {
				histogramData.remove(part);
			} else {
				treeSet.add(histogramData.get(part));
			}
		}
		TreeMap<Long, Integer> songMap = new TreeMap<>();
		for (TreeMap<Long, Integer> partMap : treeSet) {
			Set<Entry<Long, Integer>> entrySet = partMap.entrySet();
			
			for (Entry<Long, Integer> entry : entrySet) {
				long key = entry.getKey();
				int value = entry.getValue();
				
				Integer oldValue = songMap.get(key);
				if (oldValue == null) {
					oldValue = 0;
				}
				oldValue += value;
				songMap.put(key, oldValue);
			}
		}
		int polyphony = 0;
		Set<Entry<Long, Integer>> entrySongSet = songMap.entrySet();
		for (Entry<Long, Integer> entry : entrySongSet) {
			polyphony += entry.getValue();
			sum.put(entry.getKey(), polyphony);
			if (polyphony > max) {
				max = polyphony;
			}
		}
		assert polyphony == 0;
		dirty = false;
	}
	
	/**
	 * Request the number of concurrently playing notes.
	 * Be sure to call sumUp first if is dirty.
	 * 
	 * @param microsecond Time of request
	 * @return Number of notes being played at this time
	 */
	public static int get(long microsecond) {
		Long key = sum.floorKey(microsecond);
		if (key == null) {
			return 0;
		}
		return sum.get(key);
	}
	
	/**
	 * Request the number of concurrently playing notes.
	 * Be sure to call sumUp first if is dirty.
	 * 
	 * @return Number of notes being played
	 */
	public static Set<Entry<Long, Integer>> getAll() {
		return sum.entrySet();
	}
	
	/**
	 * If the sum might need to be recalculated before result is reliable.
	 * 
	 * @return dirty boolean
	 */
	public static boolean isDirty() {
		return dirty;
	}

	public static int max() {
		return max;
	}
}