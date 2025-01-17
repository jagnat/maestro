package com.digero.maestro.abc;

import java.util.EventObject;

@SuppressWarnings("serial")
public class AbcPartEvent extends EventObject {
	public enum AbcPartProperty {
		TITLE(false), //
		PART_NUMBER(false), //
		ENABLED, //
		INSTRUMENT, //
		BASE_TRANSPOSE, //
		TRACK_ENABLED, //
		TRACK_TRANSPOSE, //
		DRUM_ENABLED, //
		DRUM_MAPPING, //
		VOLUME_ADJUST, //
		TRACK_SECTION_EDIT, //
		DELAY_EDIT(true), //
		TRACK_PRIORITY, //
		MAX_EDIT(true), //
		STUDENT_FX(true), //
		CONCLUSION_FERMATA_EDIT(true); //

		private final boolean renderRelated;

		AbcPartProperty() {
			this.renderRelated = true;
		}

		AbcPartProperty(boolean renderRelated) {
			this.renderRelated = renderRelated;
		}

		public boolean isNoteGraphRelated() {
			return renderRelated;
		}

		public boolean isAbcPreviewRelated() {
			return renderRelated;
		}
	}

	public static final int NO_TRACK_NUMBER = -1;

	private final boolean abcPreviewRelated;
	private final AbcPartProperty property;
	private final int trackNumber;

	public AbcPartEvent(AbcPart source, AbcPartProperty property, boolean abcPreviewRelated, int trackNumber) {
		super(source);
		this.property = property;
		this.abcPreviewRelated = abcPreviewRelated;
		this.trackNumber = trackNumber;
	}

	public AbcPartProperty getProperty() {
		return property;
	}

	public boolean hasTrackNumber() {
		return trackNumber != NO_TRACK_NUMBER;
	}

	public int getTrackNumber() {
		return trackNumber;
	}

	public boolean matchesTrack(int track) {
		return !hasTrackNumber() || trackNumber == track;
	}

	public boolean isNoteGraphRelated() {
		return property.isNoteGraphRelated();
	}

	public boolean isAbcPreviewRelated() {
		return abcPreviewRelated;
	}

	@Override
	public AbcPart getSource() {
		return (AbcPart) super.getSource();
	}
}
