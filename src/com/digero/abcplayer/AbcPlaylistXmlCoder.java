package com.digero.abcplayer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.util.ParseException;
import com.digero.common.util.Version;
import com.digero.maestro.util.SaveUtil;
import com.digero.maestro.util.XmlUtil;

public class AbcPlaylistXmlCoder {
	
	public static final Version ABC_PLAYLIST_VERSION = new Version(3, 3, 18, 300);
	
	public static Document savePlaylistToXml(List<AbcInfo> abcs) {
		Document doc = XmlUtil.createDocument();
		doc.setXmlVersion("1.1");
		
		Element playlistEle = (Element)doc.appendChild(doc.createElement("playlist"));
		playlistEle.setAttribute("fileVersion", String.valueOf(ABC_PLAYLIST_VERSION));
		
		Element trackListEle = (Element)playlistEle.appendChild(doc.createElement("trackList"));
		
		for (AbcInfo inf : abcs) {
			Element trackEle = (Element)trackListEle.appendChild(doc.createElement("track"));
			for (File file : inf.getSourceFiles()) {
				SaveUtil.appendChildTextElement(trackEle, "location", file.getAbsolutePath());
			}
		}
		
		return doc;
	}
	
	public static List<List<File>> loadPlaylist(File playlistPath) throws ParseException {
		List<List<File>> files = new ArrayList<List<File>>();
		
		try {
			Document doc = XmlUtil.openDocument(playlistPath);
			Element playlistEle = XmlUtil.selectSingleElement(doc, "playlist");
			if (playlistEle == null) {
				throw new ParseException("Does not appear to be a valid AbcPlayer playlist.",
						playlistPath.getName());
			}
			Version fileVersion = SaveUtil.parseValue(playlistEle, "@fileVersion", ABC_PLAYLIST_VERSION);
			
			if (fileVersion.compareTo(ABC_PLAYLIST_VERSION) > 0) {
				// TODO: Warn?
			}
			
			Element trackListEle = XmlUtil.selectSingleElement(playlistEle, "trackList");
			
			for (Element songEle : XmlUtil.selectElements(trackListEle, "track")) {
				List<File> songFiles = new ArrayList<File>();
				for (Element locationEle : XmlUtil.selectElements(songEle, "location")) {
					String loc = locationEle.getTextContent();
					songFiles.add(new File(loc));
				}
				if (!songFiles.isEmpty()) {
					files.add(songFiles);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return files;
	}
}
