package com.digero.tools.soundfont;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.LotroInstrument;
import com.digero.tools.soundfont.SampleInfo.Key;

public class LonelyMountainFiddleInfo extends InstrumentInfo {
	public final SortedSet<SampleInfo> usedSamples;
	protected final int notesBelowSample;
	protected final int notesAboveSample;

	public LonelyMountainFiddleInfo(LotroInstrument lotroInstrument, int notesPerSample, Map<Key, SampleInfo> samples) {
//		super(lotroInstrument, notesPerSample, samples);
		this(lotroInstrument, lotroInstrument.toString(), lotroInstrument.lowestPlayable.id,
				lotroInstrument.highestPlayable.id, notesPerSample, samples);
	}
	
	public LonelyMountainFiddleInfo(LotroInstrument lotroInstrument, String name, int lowestNoteId, int highestNoteId,
			int notesPerSample, Map<Key, SampleInfo> samples) {
//		super(lotroInstrument, name, lowestNoteId, highestNoteId, notesPerSample, samples);
		
		super(lotroInstrument, name, lowestNoteId, highestNoteId);

		this.notesBelowSample = (notesPerSample - 1) / 2;
		this.notesAboveSample = (notesPerSample - 1) - notesBelowSample;

		SortedSet<SampleInfo> usedSamples = new TreeSet<SampleInfo>();
		int startId = 43 + notesBelowSample;
		//System.err.println(name);
		for (int id = startId; id <= highestNoteId; id += notesPerSample) {
			//System.err.println(lotroInstrument.friendlyName+" "+Integer.toString(id));
			usedSamples.add(samples.get(new Key(lotroInstrument, id)));
		}

		this.usedSamples = Collections.unmodifiableSortedSet(usedSamples);
	}

	@Override public void print(PrintStream out)
	{
		out.println();
		out.println("    InstrumentName=" + name);
		out.println();

		int i = 0;
		for (SampleInfo sample : usedSamples)
		{
			int lowKey = sample.key.noteId - notesBelowSample;
			if (lowKey == 43) {
				// SSG made artificial lower notes below 43:
				lowKey = 36;
			}
			int highKey = sample.key.noteId + notesAboveSample;
			if (++i == usedSamples.size())
				highKey = highestNoteId;

			out.println("        Sample=" + sample.name);
			out.println("            Z_LowKey=" + lowKey);
			out.println("            Z_HighKey=" + highKey);
			out.println("            Z_LowVelocity=0");
			out.println("            Z_HighVelocity=127");
			//out.println("            Z_exclusiveClass=1");//once a new note starts, stop the previous note.
			// after some investigation of playing sprightly in lotro, this does not seem to be the case
			// the problem seems to be that lotro drops some notes when playing sprightly.
			out.println();
		}
	}

}
