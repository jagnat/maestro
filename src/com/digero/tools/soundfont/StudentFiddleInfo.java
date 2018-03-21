package com.digero.tools.soundfont;

import java.io.PrintStream;
import java.util.Map;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.LotroInstrument;
import com.digero.tools.soundfont.SampleInfo.Key;

public class StudentFiddleInfo extends StandardInstrumentInfo {

	public StudentFiddleInfo(LotroInstrument lotroInstrument, int notesPerSample, Map<Key, SampleInfo> samples) {
		super(lotroInstrument, notesPerSample, samples);
	}
	
	public StudentFiddleInfo(LotroInstrument lotroInstrument, String name, int lowestNoteId, int highestNoteId,
			int notesPerSample, Map<Key, SampleInfo> samples) {
		super(lotroInstrument, name, lowestNoteId, highestNoteId, notesPerSample, samples);
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
			int highKey = sample.key.noteId + notesAboveSample;
			if (++i == usedSamples.size())
				highKey = highestNoteId;

			out.println("        Sample=" + sample.name);
			out.println("            Z_LowKey=" + lowKey);
			out.println("            Z_HighKey=" + highKey);
			out.println("            Z_LowVelocity=0");
			out.println("            Z_HighVelocity=127");
			if (lowKey >= 39 && lowKey <= 42) {
				out.println("            Z_initialAttenuation=960");//-96dB
			}
			out.println();
		}
	}

}
