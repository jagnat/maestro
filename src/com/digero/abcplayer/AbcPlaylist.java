package com.digero.abcplayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class AbcPlaylist {
	private File[] abcSongs;
	private boolean shuffle;
	
	public void loadFromFile(File playlistPath) {
		try {
			BufferedReader is = new BufferedReader(new InputStreamReader(new FileInputStream(playlistPath)));
		}
	}
	
	public void deserialize(String playlistContents) {
		
	}
	
	public String serialize() {
		return "";
	}
}
