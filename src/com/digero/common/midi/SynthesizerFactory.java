package com.digero.common.midi;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;

//import com.digero.common.midi.synth.LotroSoftSynthesizer;
import com.sun.media.sound.AudioSynthesizer;

public class SynthesizerFactory {
	private static Soundbank lotroSoundbank = null;
	private static File soundFontFile = new File("LotroInstruments.sf2");

	public static void setSoundFontLocation(File soundFontFile) {
		if (SynthesizerFactory.soundFontFile != soundFontFile) {
			SynthesizerFactory.soundFontFile = soundFontFile;
			lotroSoundbank = null;
		}
	}

	public static Synthesizer getLotroSynthesizer()
			throws MidiUnavailableException, InvalidMidiDataException, IOException {
		Synthesizer synth = MidiSystem.getSynthesizer();//new LotroSoftSynthesizer();
		if (synth != null)
			initLotroSynthesizer(synth);
		return synth;
	}

	public static AudioSynthesizer getLotroAudioSynthesizer()
			throws MidiUnavailableException, InvalidMidiDataException, IOException {
		AudioSynthesizer synth = findAudioSynthesizer();
		if (synth != null)
			initLotroSynthesizer(synth);
		return synth;
	}

	public static void initLotroSynthesizer(Synthesizer synth)
			throws MidiUnavailableException, InvalidMidiDataException, IOException {
		Map<String, Object> synthInfo = new HashMap();
		synthInfo.put("midi channels", 25);
		synthInfo.put("reverb", false);
		synthInfo.put("chorus", false);
		synthInfo.put("max polyphony", 128);
		synthInfo.put("auto gain control", false);
		synthInfo.put("latency", 12000L);
		((com.sun.media.sound.SoftSynthesizer)synth).open(null, synthInfo);
		//((LotroSoftSynthesizer)synth).open(null, synthInfo);
		synth.unloadAllInstruments(getLotroSoundbank());
		synth.loadAllInstruments(getLotroSoundbank());
	}

	public static Soundbank getLotroSoundbank() throws InvalidMidiDataException, IOException {
		if (lotroSoundbank == null) {
			if (!soundFontFile.exists()) {
				String folder = ".";
				try {
					// Find the path to the jar file we are executing in
					folder = new File(
							SynthesizerFactory.class.getProtectionDomain().getCodeSource().getLocation().toURI())
							.getParent();
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
				soundFontFile = new File(folder, "LotroInstruments.sf2");
			}
			try {
				lotroSoundbank = MidiSystem.getSoundbank(soundFontFile);
			} catch (NullPointerException npe) {
				// JARSoundbankReader throws a NullPointerException if the file doesn't exist
				StackTraceElement trace = npe.getStackTrace()[0];
				if (trace.getClassName().equals("com.sun.media.sound.JARSoundbankReader")
						&& trace.getMethodName().equals("isZIP")) {
					throw new IOException("Soundbank file not found");
				} else {
					throw npe;
				}
			}
		}
		return lotroSoundbank;
	}

	/**
	 * Find available AudioSynthesizer
	 */
	public static AudioSynthesizer findAudioSynthesizer() throws MidiUnavailableException {
		// First check if default synthesizer is AudioSynthesizer.
		Synthesizer synth = MidiSystem.getSynthesizer();
		if (synth instanceof AudioSynthesizer)
			return (AudioSynthesizer) synth;

		// If default synhtesizer is not AudioSynthesizer, check others.
		for (Info info : MidiSystem.getMidiDeviceInfo()) {
			MidiDevice dev = MidiSystem.getMidiDevice(info);
			if (dev instanceof AudioSynthesizer)
				return (AudioSynthesizer) dev;
		}

		// No AudioSynthesizer was found, return null.
		return null;
	}
}
