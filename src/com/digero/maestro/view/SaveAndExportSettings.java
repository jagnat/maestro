package com.digero.maestro.view;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class SaveAndExportSettings {
	public boolean promptSaveNewSong = true;
	public boolean showExportFileChooser = false;
	public boolean skipSilenceAtStart = true;
	// public boolean showPruned = false;
	public boolean convertABCStringsToBasicAscii = true;

	private final Preferences prefs;

	public SaveAndExportSettings(Preferences prefs) {
		this.prefs = prefs;
		promptSaveNewSong = prefs.getBoolean("promptSaveNewSong", promptSaveNewSong);
		showExportFileChooser = prefs.getBoolean("showExportFileChooser", showExportFileChooser);
		skipSilenceAtStart = prefs.getBoolean("skipSilenceAtStart", skipSilenceAtStart);
		// showPruned = prefs.getBoolean("showPruned", showPruned);
		convertABCStringsToBasicAscii = prefs.getBoolean("convertABCStringsToBasicAscii",
				convertABCStringsToBasicAscii);
	}

	public SaveAndExportSettings(SaveAndExportSettings that) {
		this.prefs = that.prefs;
		copyFrom(that);
	}

	public void copyFrom(SaveAndExportSettings that) {
		promptSaveNewSong = that.promptSaveNewSong;
		showExportFileChooser = that.showExportFileChooser;
		skipSilenceAtStart = that.skipSilenceAtStart;
		// showPruned = that.showPruned;
		convertABCStringsToBasicAscii = that.convertABCStringsToBasicAscii;
	}

	public void saveToPrefs() {
		prefs.putBoolean("promptSaveNewSong", promptSaveNewSong);
		prefs.putBoolean("showExportFileChooser", showExportFileChooser);
		prefs.putBoolean("skipSilenceAtStart", skipSilenceAtStart);
		// prefs.putBoolean("showPruned", showPruned);
		prefs.putBoolean("convertABCStringsToBasicAscii", convertABCStringsToBasicAscii);
	}

	public void restoreDefaults() {
		try {
			prefs.clear();
		} catch (BackingStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		SaveAndExportSettings fresh = new SaveAndExportSettings(prefs);
		this.copyFrom(fresh);
	}

	public SaveAndExportSettings getCopy() {
		return new SaveAndExportSettings(this);
	}
}
