/* Copyright (c) 2010 Ben Howell
 * This software is licensed under the MIT License
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */

package com.digero.common.abc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.digero.common.midi.Note;
import com.digero.common.util.Pair;

// @formatter:off
public enum LotroInstrument
{
	//                    		friendlyName            sustainable   midiProgramId   octaveDelta   isPercussion  dBVolumeAdjust  C4 equals(measured using spectrum analyzer)
	LUTE_OF_AGES        		( "Lute of Ages",             false,         24,        0,           false,         0.0f        ),//C4
	BASIC_LUTE          		( "Basic Lute",               false,         25,        0,           false,       -19.0f        ),//C4
	HARP                		( "Harp",                     false,         46,        0,           false,         6.0f        ),//C4
	MISTY_MOUNTAIN_HARP 		( "Misty Mountain Harp",      false,         27,        0,           false,       -12.5f        ),//C4
	THEORBO             		( "Theorbo",                  false,         32,       -1,           false,       -12.0f        ),//C3
	FLUTE               		( "Flute",                     true,         73,        2,           false,        -0.5f        ),//C6
	CLARINET            		( "Clarinet",                  true,         71,        1,           false,        -2.5f        ),//C5
	HORN                		( "Horn",                      true,         69,        0,           false,         0.0f        ),//C4
	BAGPIPE             		( "Bagpipe",                   true,        109,        1,           false,        -3.2f        ),//C5
	PIBGORN             		( "Pibgorn",                   true,         84,        2,           false,        -3.5f        ),//C6
	DRUMS               		( "Drums",                    false,        118,        0,            true,         0.0f        ),
	COWBELL             		( "Cowbell",                  false,        115,        0,            true,         0.0f        ),//E5
	MOOR_COWBELL        		( "Moor Cowbell",             false,        114,        0,            true,         0.0f        ),//C6
	STUDENT_FIDDLE				( "Student Fiddle",		       true,		 60,		1,			 false,		   -3.0f		),//C5  noises are:C2,C#2,D2
	LONELY_MOUNTAIN_FIDDLE 		( "Lonely Mountain Fiddle",    true,		 41,	    1,			 false,		   -3.0f		),//C5
	SPRIGHTLY_FIDDLE			( "Sprightly Fiddle",	      false,		 49,		1,			 false,		   -3.0f		),//C5
	TRAVELLERS_TRUSTY_FIDDLE 	( "Travellers Trusty Fiddle", false,		 45, 		1,			 false,		   -3.0f		);//C5
// @formatter:on

	public static final LotroInstrument DEFAULT_LUTE = LUTE_OF_AGES;
	public static final LotroInstrument DEFAULT_INSTRUMENT = LUTE_OF_AGES;

	public final Note lowestPlayable;
	public final Note highestPlayable;
	public final String friendlyName;
	public final boolean sustainable;
	public final boolean isPercussion;
	public final int midiProgramId;
	public final int octaveDelta;
	public final float dBVolumeAdjust;

	private LotroInstrument(String friendlyName, boolean sustainable, int midiProgramId, int octaveDelta,
			boolean isPercussion, float dBVolumeAdjust)
	{
		if (midiProgramId == 60) {
			//this.lowestPlayable = AbcConstants.STUDENT_FIDDLE_LOWEST_MUSICAL_NOTE;
			this.lowestPlayable = Note.MIN_PLAYABLE;
		} else if (midiProgramId == 41) {
			this.lowestPlayable = AbcConstants.LONELY_MOUNTAIN_FIDDLE_LOWEST_NOTE;
		} else if (midiProgramId == 49) {
			this.lowestPlayable = AbcConstants.SPRIGHTLY_FIDDLE_LOWEST_NOTE;
		} else if (midiProgramId == 45) {
			this.lowestPlayable = AbcConstants.TRAVELLERS_TRUSTY_FIDDLE_LOWEST_NOTE;
		} else {
			this.lowestPlayable = Note.MIN_PLAYABLE;			
		}
		this.highestPlayable = Note.MAX_PLAYABLE;
		this.friendlyName = friendlyName;
		this.sustainable = sustainable;
		this.midiProgramId = midiProgramId;
		this.octaveDelta = octaveDelta;
		this.isPercussion = isPercussion;
		this.dBVolumeAdjust = dBVolumeAdjust;
	}

	public boolean isSustainable(int noteId)
	{
		return sustainable && isPlayable(noteId);
	}

	public boolean isPlayable(int noteId)
	{
		return noteId >= lowestPlayable.id && noteId <= highestPlayable.id;
	}

	@Override public String toString()
	{
		return friendlyName;
	}

	public static LotroInstrument parseInstrument(String string) throws IllegalArgumentException
	{
		string = string.toUpperCase().replaceAll("[\\s_]", "");
		if (string.equals("LUTE"))
			return DEFAULT_LUTE;
		if (string.equals("MISTYHARP"))
			return MISTY_MOUNTAIN_HARP;
		if (string.equals("SPRIGHTLY"))
			return SPRIGHTLY_FIDDLE;
		if (string.equals("TRAVELLER"))
			return TRAVELLERS_TRUSTY_FIDDLE;
		if (string.equals("LONELY"))
			return LONELY_MOUNTAIN_FIDDLE;

		for (LotroInstrument i : values())
		{
			if (i.name().replace("_", "").equals(string))
				return i;
		}
		return LotroInstrument.valueOf(string);
	}

	private static Pattern instrumentsRegex = null;
	private static List<Pair<Pattern, LotroInstrument>> instrumentNicknames = new ArrayList<Pair<Pattern, LotroInstrument>>();

	@SafeVarargs private static <T> Pattern makeInstrumentRegex(T... names)
	{
		String regex = "";
		for (T name : names)
		{
			if (regex.length() > 0)
				regex += "|";
			regex += name.toString().replace(" ", "\\s*");
		}
		return Pattern.compile("\\b(" + regex + ")\\b", Pattern.CASE_INSENSITIVE);
	}

	private static void addNicknames(LotroInstrument instrument, String... nicknames)
	{
		instrumentNicknames.add(new Pair<Pattern, LotroInstrument>(makeInstrumentRegex(nicknames), instrument));
	}

	public static LotroInstrument findInstrumentName(String str, LotroInstrument defaultInstrument)
	{
		if (instrumentNicknames.size() == 0)
		{
			// the order matters here, so make sure words that are used as part of names but also as standalone descriptions, like lute, harp and fiddle is added last.
			// also since nicknames are checked first, "basic lute" could be seen as Lute and therefore lute of ages if not listed as nickname. Thats why some friendly names are relisted as nicknames.
			addNicknames(LotroInstrument.BASIC_LUTE, "Basic Lute", "New Lute", "LuteB", "Banjo");
			addNicknames(LotroInstrument.LUTE_OF_AGES, "Age Lute", "LuteA", "LOA", "Guitar");
			addNicknames(LotroInstrument.DEFAULT_LUTE, "Lute");
			addNicknames(LotroInstrument.MISTY_MOUNTAIN_HARP, "Misty Mountain Harp", "Misty Harp", "MM Harp", "MMH", "MMHarp");
			addNicknames(LotroInstrument.HARP, "Basic Harp", "Gleowine's Harp", "Gleowines Harp");
			addNicknames(LotroInstrument.THEORBO, "Theo", "Bass");
			addNicknames(LotroInstrument.DRUMS, "Drum");
			addNicknames(LotroInstrument.CLARINET, "Clari");
			addNicknames(LotroInstrument.BAGPIPE, "Bagpipes", "Pipes");
			addNicknames(LotroInstrument.MOOR_COWBELL, "Moor Cowbell", "More Cowbell");
			addNicknames(LotroInstrument.LONELY_MOUNTAIN_FIDDLE, "Lonely Mountain Fiddle", "LMFiddle", "Lonely Fiddle", "Mountain Fiddle");
			addNicknames(LotroInstrument.SPRIGHTLY_FIDDLE, "Sprightly Fiddle", "SpFiddle");
			addNicknames(LotroInstrument.TRAVELLERS_TRUSTY_FIDDLE, "Travellers Trusty Fiddle", "TTFiddle", "Travellers Fiddle", "Trusty Fiddle", "Traveller's Trusty Fiddle");
			addNicknames(LotroInstrument.STUDENT_FIDDLE, "Fiddle", "StFiddle", "Student's Fiddle", "Students Fiddle");
		}

		// first check nicknames
		for (Pair<Pattern, LotroInstrument> patternAndInstrument : instrumentNicknames)
		{
			if (patternAndInstrument.first.matcher(str).find()) {
				return patternAndInstrument.second;
			}
		}

		if (instrumentsRegex == null) {
			instrumentsRegex = makeInstrumentRegex(LotroInstrument.values());
		}

		// second check friendly names
		Matcher m = instrumentsRegex.matcher(str);
		if (m.find())
			return LotroInstrument.parseInstrument(m.group(1));

		return defaultInstrument;
	}
}