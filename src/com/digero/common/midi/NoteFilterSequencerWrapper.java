package com.digero.common.midi;

import java.util.Date;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.MidiDevice.Info;

import com.digero.common.midi.SequencerEvent.SequencerProperty;

public class NoteFilterSequencerWrapper extends SequencerWrapper
{
	public static final String prefMIDIHeader = "MIDI out devices";
	public static final String prefMIDISelect = "Preferred MIDI out device";
	public static Preferences prefs = Preferences.userNodeForPackage(NoteFilterSequencerWrapper.class);
	private Preferences prefsNode = null;
	private NoteFilterTransceiver filter;
	private MidiDevice device = null;

	public NoteFilterSequencerWrapper() throws MidiUnavailableException
	{
		filter = new NoteFilterTransceiver();
		addTransceiver(filter);
	}

	public NoteFilterTransceiver getFilter()
	{
		return filter;
	}

	public void setNoteSolo(int track, int noteId, boolean solo)
	{
		if (solo != getNoteSolo(track, noteId))
		{
			sequencer.setTrackSolo(track, solo);
			filter.setNoteSolo(noteId, solo);
			fireChangeEvent(SequencerProperty.TRACK_ACTIVE);
		}
	}

	public boolean getNoteSolo(int track, int noteId)
	{
		return filter.getNoteSolo(noteId) && sequencer.getTrackSolo(track);
	}

	@Override public boolean isNoteActive(int noteId)
	{
		return filter.isNoteActive(noteId);
	}
	
	@Override public Receiver createReceiver() throws MidiUnavailableException 
	{
		if (prefsNode == null) {
			prefsNode = prefs.node(prefMIDIHeader);
		}
		String preferred = prefs.get(prefMIDISelect, null);
		Info[] infos = MidiSystem.getMidiDeviceInfo();
		Info myInfo = null;
		for (Info info : infos) {
			prefsNode.putLong(info.getName(), new Date().getTime());
			//System.out.println(infoToString(info));
			if (info.getName() != null && info.getName().length() > 0 && info.getName().equals(preferred)) {
				myInfo = info;
			}
		}
		try {
			prefsNode.flush();
		} catch (BackingStoreException e) {
			//e.printStackTrace();
		}
		
		closeDevice();
		
		if (preferred == null) {
			//System.out.println("Default MIDI out selected");
			return MidiSystem.getReceiver();
		}
		if (myInfo == null) {
			System.out.println("Default MIDI out selected ("+preferred+" not available)");
			return MidiSystem.getReceiver();
		}
		
		Receiver myReciever = null;
		boolean okay = true;
		try {
			device = MidiSystem.getMidiDevice(myInfo);
			device.open();
			myReciever = device.getReceiver();
		} catch (MidiUnavailableException e) {
			okay = false;
			closeDevice();
		}
		if (!okay || myReciever == null) {
			System.out.println("Default MIDI out selected ("+preferred+" not available)");			
			return MidiSystem.getReceiver();
		}
		
		//System.out.println("\nmaxTransmitters="+myDevice.getMaxTransmitters());
		//System.out.println("maxReceivers="+myDevice.getMaxReceivers());
		
		System.out.println("Non-default MIDI out selected: "+myInfo.getName());
		return myReciever;
	}
	
	@Override public void discard()
	{
		closeDevice();		
		super.discard();
	}
	
	private void closeDevice()
	{
		if (device != null)
		{
			//System.out.println("CLOSING "+ device.getDeviceInfo().getName());
			device.close();
			device = null;
		}
	}
	
	private String infoToString(Info info) {
		String str = "";
		str += "\nName: "+info.getName();
		str += "\nVendor: "+info.getVendor();
		str += "\nDescription: "+info.getDescription();
		str += "\nVersion: "+info.getVersion();
		return str;
	}
}
