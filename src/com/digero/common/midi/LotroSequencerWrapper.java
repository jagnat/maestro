package com.digero.common.midi;

import java.io.IOException;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;
import javax.sound.midi.VoiceStatus;

import com.digero.maestro.abc.AbcExporter.ExportTrackInfo;
import com.digero.maestro.midi.SequenceInfo;

public class LotroSequencerWrapper extends NoteFilterSequencerWrapper
{
	private static Synthesizer lotroSynth;
	private static String loadLotroSynthError;

	static
	{
		try
		{
			lotroSynth = SynthesizerFactory.getLotroSynthesizer();
		}
		catch (InvalidMidiDataException | MidiUnavailableException | IOException e)
		{
			loadLotroSynthError = e.getMessage();
		}
	}
	
	@Override public void setRunning(boolean isRunning)
	{
		
		//resetNotes();
		List<ExportTrackInfo> infos = SequenceInfo.lastTrackInfos;
		if (infos != null) {
			for (ExportTrackInfo info : infos) {
				receiver.send(MidiFactory.createAllNotesOff(info.channel),-1L);
				receiver.send(MidiFactory.createLotroChangeEvent(info.patch,info.channel,sequencer.getTickPosition()).getMessage(),-1L);//-1 means realtime
			}
		}
		super.setRunning(isRunning);
	}
	
	@Override public void setPosition(long position) {
		super.setPosition(position);
		//resetNotes();
		List<ExportTrackInfo> infos = SequenceInfo.lastTrackInfos;
		if (infos != null) {
			for (ExportTrackInfo info : infos) {
				receiver.send(MidiFactory.createAllNotesOff(info.channel),-1L);
				receiver.send(MidiFactory.createLotroChangeEvent(info.patch,info.channel,sequencer.getTickPosition()).getMessage(),-1L);//-1 means realtime
			}
		}
	}
	
	public void resetNotes()
	{
		//stop();
		//setPosition(0);
		//trackActiveCache = null;
		boolean fullReset = false;
		if (fullReset)
		{
			try
			{
				ShortMessage msg = new ShortMessage();
				msg.setMessage(ShortMessage.SYSTEM_RESET);
				receiver.send(msg, -1);
			}
			catch (InvalidMidiDataException e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			// Not a full reset
			try
			{
				LotroShortMessage msg = new LotroShortMessage();
				for (int i = 0; i < CHANNEL_COUNT_ABC; i++)
				{
					msg.setMessage(ShortMessage.PROGRAM_CHANGE, i, 0, 0);
					receiver.send(msg, -1);
					msg.setMessage(ShortMessage.CONTROL_CHANGE, i, ALL_CONTROLLERS_OFF, 0);
					receiver.send(msg, -1);
				}
				msg.setMessage(ShortMessage.SYSTEM_RESET);
				receiver.send(msg, -1);
			}
			catch (InvalidMidiDataException e)
			{
				// Ignore
				e.printStackTrace();
			}
		}
	}

	public static String getLoadLotroSynthError()
	{
		return loadLotroSynthError;
	}

	public LotroSequencerWrapper() throws MidiUnavailableException
	{
	}

	public boolean isUsingLotroInstruments()
	{
		return lotroSynth != null;
	}

	@Override protected Receiver createReceiver() throws MidiUnavailableException
	{
		return (lotroSynth != null) ? lotroSynth.getReceiver() : MidiSystem.getReceiver();
	}
	
	public static int getNoteCount () {
		if (lotroSynth == null) return 0;
		
		VoiceStatus[] voices = lotroSynth.getVoiceStatus();
		if (voices != null && voices.length != 0) {
			int notes = 0;
			for (VoiceStatus voice : voices) {
				if (voice.active) {
					notes++;
				}
			}
			return notes;
		}		
		return 0;
	}
}
