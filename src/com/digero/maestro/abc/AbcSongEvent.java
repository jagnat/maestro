package com.digero.maestro.abc;

import java.util.EventObject;

@SuppressWarnings("serial")
public class AbcSongEvent extends EventObject {
	public enum AbcSongProperty {
		TITLE, //
		COMPOSER, //
		TRANSCRIBER, //
		//
		TEMPO_FACTOR, //
		TRANSPOSE, //
		KEY_SIGNATURE, //
		TIME_SIGNATURE, //
		TRIPLET_TIMING, //
		SKIP_SILENCE_AT_START, //
		//
		PART_ADDED, //
		BEFORE_PART_REMOVED, //
		PART_LIST_ORDER, //
		//
		EXPORT_FILE, //
		MIX_TIMING, //
		GENRE, //
		MOOD, //
		MIX_TIMING_COMBINE_PRIORITIES,
		// SHOW_PRUNED, //
		TUNE_EDIT, SONG_CLOSING, DELETE_MINIMAL_NOTES, HIDE_EDITS_UPDATE
	}

	private final AbcSongProperty property;
	private final AbcPart part;

	public AbcSongEvent(AbcSong source, AbcSongProperty property, AbcPart part) {
		super(source);
		this.property = property;
		this.part = part;
	}

	public AbcSongProperty getProperty() {
		return property;
	}

	/**
	 * @return The part associated with this event, or null if this event doesn't apply to a particular part.
	 */
	public AbcPart getPart() {
		return part;
	}

	@Override
	public AbcSong getSource() {
		return (AbcSong) super.getSource();
	}
}
