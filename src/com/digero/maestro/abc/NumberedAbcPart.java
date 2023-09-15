package com.digero.maestro.abc;

import com.digero.common.abc.LotroInstrument;

public interface NumberedAbcPart {
	LotroInstrument getInstrument();

	void setInstrument(LotroInstrument instrument);

	int getPartNumber();

	void setPartNumber(int partNumber);
}
