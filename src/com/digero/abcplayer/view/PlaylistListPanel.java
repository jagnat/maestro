package com.digero.abcplayer.view;

import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import com.digero.common.util.IDiscardable;

public class PlaylistListPanel extends JPanel implements IDiscardable {

	private static final long serialVersionUID = -2499955428132608338L;
	private BoxLayout layout;
	
	private List<PlaylistItem> songs = new ArrayList<PlaylistItem>();
	private int selectedIndex = -1;

	public PlaylistListPanel() {
		super();
		layout = new BoxLayout(this, BoxLayout.Y_AXIS);
		setLayout(layout);
//		setSize(400, 400);
//		setBackground(new JList<Integer>().getBackground());
	}
	
	public void addItem(PlaylistItem item) {
		songs.add(item);
	}
	
	public void updatePlaylist() {
		removeAll();
		
		for (int i = 0; i < songs.size(); i++) {
			add(songs.get(i));
		}
		
		revalidate();
		repaint();
	}
	
	@Override
	public void discard() {
		
	}
}
