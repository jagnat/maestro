package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;

import com.digero.common.util.IDiscardable;

public class PlaylistListPanel extends JPanel implements IDiscardable {

	private static final long serialVersionUID = -2499955428132608338L;
	private BorderLayout layout;
	
	private JPanel wrapperPanel;
	
	private List<PlaylistItem> songs = new ArrayList<PlaylistItem>();
	private int selectedIndex = -1;
	
	String data[][] = {{"song1", "3"},{"song2", "5"},{"song3", "8"}};
	String cols[] = {"Title", "Part Count"};
	private JTable table = new JTable(data, cols);

	public PlaylistListPanel() {
		super();
		layout = new BorderLayout();
		setLayout(layout);
		setBackground(new JList<String>().getBackground());
		
		wrapperPanel = new JPanel();
		BoxLayout wrapperLayout = new BoxLayout(wrapperPanel, BoxLayout.Y_AXIS);
		wrapperPanel.setLayout(wrapperLayout);
		
		add(wrapperPanel, BorderLayout.PAGE_START);
		
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
//		table.head
//		table.sh
		
		wrapperPanel.add(table);
		
		for (int i = 0; i < 10; i++) {
//			addItem(new PlaylistItem("Item No. " + (i+1)));
		}
//		updatePlaylist();
	}
	
	public void addItem(PlaylistItem item) {
		songs.add(item);
	}
	
	public void updatePlaylist() {
		wrapperPanel.removeAll();
		
		for (int i = 0; i < songs.size(); i++) {
			wrapperPanel.add(songs.get(i));
		}
		
		revalidate();
		repaint();
	}
	
	@Override
	public void discard() {
		
	}
}
