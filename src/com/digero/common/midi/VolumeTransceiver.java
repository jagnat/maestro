package com.digero.common.midi;

import java.util.Arrays;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

public class VolumeTransceiver implements Transceiver, MidiConstants {
	private static final int UNSET_CHANNEL_VOLUME = -1;

	private Receiver receiver;
	private int volume = MAX_VOLUME;
	private int[] channelVolume = new int[CHANNEL_COUNT_ABC];
	private boolean goesToEleven = false;

	public VolumeTransceiver() {
		Arrays.fill(channelVolume, UNSET_CHANNEL_VOLUME);
	}

	public void itGoesToEleven(boolean goesToEleven) {
		if (this.goesToEleven != goesToEleven) {
			this.goesToEleven = goesToEleven;
			sendVolumeAllChannels();
		}
	}

	public void setVolume(int volume) {
		if (volume < 0 || volume > MAX_VOLUME)
			throw new IllegalArgumentException();

		this.volume = volume;
		sendVolumeAllChannels();
	}

	public int getVolume() {
		return volume;
	}

	@Override
	public void close() {
	}

	@Override
	public Receiver getReceiver() {
		return receiver;
	}

	@Override
	public void setReceiver(Receiver receiver) {
		this.receiver = receiver;
		sendVolumeAllChannels();
	}

	private int getActualVolume(int channel) {
		int controllerVolume = channelVolume[channel];
		if (controllerVolume == UNSET_CHANNEL_VOLUME)
			controllerVolume = goesToEleven ? MAX_VOLUME : DEFAULT_CHANNEL_VOLUME;

		return Math.min(127, (controllerVolume * volume) / MAX_VOLUME);
	}

	private void sendVolumeAllChannels() {
		if (receiver != null) {
			for (int c = 0; c < CHANNEL_COUNT_ABC; c++) {
				MidiEvent evt = MidiFactory.createChannelVolumeEvent(getActualVolume(c), c, 0);
				passOn(evt.getMessage(), -1);
			}
		}
	}

	private void passOn(MidiMessage message, long timeStamp) {
		/*
		 * if (message instanceof ShortMessage) { ShortMessage m = (ShortMessage) message; if (m.getCommand() ==
		 * ShortMessage.SYSTEM_RESET) { System.out.println("Reset"); } else if (m.getCommand() ==
		 * ShortMessage.CONTROL_CHANGE && m.getData1() == CHANNEL_VOLUME_CONTROLLER_COARSE) { int c = m.getChannel();
		 * System.out.println("PassOn: Channel "+c+" set to "+m.getData2()); } else if (m.getCommand() ==
		 * ShortMessage.CONTROL_CHANGE && m.getData1() == CHANNEL_VOLUME_CONTROLLER_FINE) { int c = m.getChannel();
		 * System.out.println("Channel "+c+" set to fine "+m.getData2()); } else if (m.getCommand() ==
		 * ShortMessage.CONTROL_CHANGE && m.getData1() == CHANNEL_EXPRESSION_CONTROLLER) { int c = m.getChannel();
		 * System.out.println("Channel "+c+" expression "+m.getData2()); } }
		 */

		if (receiver != null) {
			receiver.send(message, timeStamp);
		}
	}

	@Override
	public void send(MidiMessage message, long timeStamp) {
		boolean systemReset = false;
		if (message instanceof ShortMessage) {
			ShortMessage m = (ShortMessage) message;
			if (m.getCommand() == ShortMessage.SYSTEM_RESET) {
				Arrays.fill(channelVolume, UNSET_CHANNEL_VOLUME);
				systemReset = true;
			} else if (m.getCommand() == ShortMessage.CONTROL_CHANGE
					&& m.getData1() == CHANNEL_VOLUME_CONTROLLER_COARSE) {
				/*
				 * try {
				 */
				int c = m.getChannel();
				channelVolume[c] = m.getData2();
				sendVolumeAllChannels();// This (sort of) fixes an issue with SoundBlaster Audigy 5
				return;
				// System.out.println("Channel "+c+" set to "+getActualVolume(c));
				// m.setMessage(m.getCommand(), c, CHANNEL_VOLUME_CONTROLLER_COARSE, getActualVolume(c));
				// MidiEvent evt = MidiFactory.createChannelVolumeEvent(getActualVolume(c), c, timeStamp);
				// message = evt.getMessage();
				/*
				 * } catch (InvalidMidiDataException e) { e.printStackTrace(); }
				 */
			} else if (m.getCommand() == ShortMessage.CONTROL_CHANGE
					&& m.getData1() == CHANNEL_VOLUME_CONTROLLER_FINE) {
				// int c = m.getChannel();
				// System.out.println("Channel "+c+" set to fine "+m.getData2());
			} else if (m.getCommand() == ShortMessage.CONTROL_CHANGE && m.getData1() == CHANNEL_EXPRESSION_CONTROLLER) {
				// int c = m.getChannel();
				// System.out.println("Channel "+c+" expression "+m.getData2()+"
				// becomes="+getActualVolume(c)*((float)m.getData2()/127.0));

				return; // This (sort of) fixes an issue with SoundBlaster Audigy 5
			}
		} else if (message instanceof SysexMessage) {
			SysexMessage m = (SysexMessage) message;

			byte[] sysex = m.getMessage();

			/*
			 * StringBuilder sb = new StringBuilder(); for (byte b : sysex) { sb.append(String.format("%02X ", b)); }
			 * System.err.println("SYSEX : "+sb.toString());
			 */

			if (sysex.length > 4 && (sysex[1] & 0xFF) == SYSEX_UNIVERSAL_REALTIME && (sysex[3] & 0xFF) == 0x04
					&& (sysex[4] & 0xFF) == 0x01) {
				// System.out.println("Ignored SysEx device volume command.");
				return;
			}
		}

		if (receiver != null) {
			passOn(message, timeStamp);
			if (systemReset)
				sendVolumeAllChannels();
		}
	}
}