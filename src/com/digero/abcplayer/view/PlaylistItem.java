package com.digero.abcplayer.view;

import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import com.digero.common.util.IDiscardable;

public class PlaylistItem extends JPanel implements IDiscardable {

	private static final long serialVersionUID = -1232443636769475120L;
	
	private JLabel title;
	
	public PlaylistItem(String titleStr) {
		super(new FlowLayout());
		
		setBackground(new JList<String>().getBackground());
		
		title = new JLabel(titleStr);
		title.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
		add(title);
	}

	@Override
	public void discard() {
		
	}
}
