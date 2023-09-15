package com.digero.maestro.abc;

import com.digero.common.abc.LotroInstrument;

public interface AbcPartMetadataSource {
	String getTitle();

	int getPartNumber();

	LotroInstrument getInstrument();
}
