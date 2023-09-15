package com.digero.maestro.view;

import java.util.Arrays;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class MiscSettings {
	public boolean showMaxPolyphony = true;
	public boolean showBadger = false;
	public boolean allBadger = false;
	public String theme = "Flat Light";
	public int fontSize = 12;
	public int maxRangeForNewBendMethod = 12;

	private final Preferences prefs;

	public MiscSettings(Preferences prefs, boolean checkFallback) {
		this.prefs = prefs;
		boolean useFallback = false;
		Preferences saveExportNode = prefs.parent().node("saveAndExportSettings");
		if (checkFallback) {
			try {
				if (!Arrays.asList(prefs.keys()).contains("showMaxPolyphony")) {
					useFallback = true;
				}
			} catch (Exception e) {
				useFallback = true;
			}
		}

		loadPrefs(useFallback ? saveExportNode : prefs);
		saveToPrefs();
	}

	private void loadPrefs(Preferences prefs) {
		showMaxPolyphony = prefs.getBoolean("showMaxPolyphony", showMaxPolyphony);
		showBadger = prefs.getBoolean("showBadger", showBadger);
		allBadger = prefs.getBoolean("allBadger", allBadger);
		theme = prefs.get("theme", theme);
		fontSize = prefs.getInt("fontSize", fontSize);
		maxRangeForNewBendMethod = prefs.getInt("maxRangeForNewBendMethod", maxRangeForNewBendMethod);
	}

	public MiscSettings(MiscSettings that) {
		this.prefs = that.prefs;
		copyFrom(that);
	}

	public void copyFrom(MiscSettings that) {
		showMaxPolyphony = that.showMaxPolyphony;
		showBadger = that.showBadger;
		allBadger = that.allBadger;
		theme = that.theme;
		fontSize = that.fontSize;
		maxRangeForNewBendMethod = that.maxRangeForNewBendMethod;
	}

	public void saveToPrefs() {
		prefs.putBoolean("showMaxPolyphony", showMaxPolyphony);
		prefs.putBoolean("showBadger", showBadger);
		prefs.putBoolean("allBadger", allBadger);
		prefs.put("theme", theme);
		prefs.putInt("fontSize", fontSize);
		prefs.putInt("maxRangeForNewBendMethod", maxRangeForNewBendMethod);
	}

	public void restoreDefaults() {
		try {
			prefs.clear();
		} catch (BackingStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		MiscSettings fresh = new MiscSettings(prefs, false);
		this.copyFrom(fresh);
	}

	public MiscSettings getCopy() {
		return new MiscSettings(this);
	}
}
