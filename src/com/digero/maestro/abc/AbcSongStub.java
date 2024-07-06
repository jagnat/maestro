package com.digero.maestro.abc;

import com.digero.common.abc.LotroInstrument;

// Represents a .abc file song that isn't fully loaded yet..
// Some info will have been parsed from file such as
// part info, song duration, etc., but sequence not yet loaded
// used for abc player playlist feature

public class AbcSongStub {
	private String title = "";
	private int partCount = 0;
	private String songDuration = "";
	 
}

class AbcPartStub {
	private String title;
	private LotroInstrument instrument;
}