package com.digero.maestro.abc;

import java.io.File;

public interface AbcMetadataSource {
	String getSongTitle();

	String getComposer();

	String getTranscriber();

	long getSongLengthMicros();

	File getExportFile();

	String getPartName(AbcPartMetadataSource abcPart);

	String getGenre();

	String getMood();

	String getAllParts();

	int getActivePartCount();

	String getBadgerTitle();

	String getSourceFilename();
}
