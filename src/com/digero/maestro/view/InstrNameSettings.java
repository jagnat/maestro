package com.digero.maestro.view;

import java.util.EnumMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.digero.common.abc.LotroInstrument;

public class InstrNameSettings {
	private Map<LotroInstrument, String> nicks = new EnumMap<>(LotroInstrument.class);

	private final Preferences prefs;

	public InstrNameSettings(Preferences prefs) {
		this.prefs = prefs;

		loadPrefs(prefs);
		saveToPrefs();
	}

	private void loadPrefs(Preferences prefs) {
		for (LotroInstrument inst : LotroInstrument.values()) {
			nicks.put(inst, prefs.get(inst.toString(), inst.friendlyName));
		}
	}

	public InstrNameSettings(InstrNameSettings that) {
		this.prefs = that.prefs;
		copyFrom(that);
	}

	public void copyFrom(InstrNameSettings that) {
		for (LotroInstrument inst : LotroInstrument.values()) {
			nicks.put(inst, that.nicks.get(inst));
		}
	}

	public void saveToPrefs() {
		for (LotroInstrument inst : LotroInstrument.values()) {
			prefs.put(inst.toString(), nicks.get(inst));
		}
	}

	public void restoreDefaults() {
		try {
			prefs.clear();
		} catch (BackingStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		InstrNameSettings fresh = new InstrNameSettings(prefs);
		this.copyFrom(fresh);
	}

	public InstrNameSettings getCopy() {
		return new InstrNameSettings(this);
	}

	public void setInstrNick(LotroInstrument instrument, String nick) {
		nicks.put(instrument, nick);
	}

	public String getInstrNick(LotroInstrument instrument) {
		return nicks.getOrDefault(instrument, instrument.friendlyName);
	}
}
