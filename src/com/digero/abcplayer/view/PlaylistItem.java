package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.JLabel;
import javax.swing.JPanel;

import com.digero.common.util.IDiscardable;

public class PlaylistItem extends JPanel implements IDiscardable {

	private static final long serialVersionUID = -1232443636769475120L;
	
	private JLabel title;
	
	public PlaylistItem(String titleStr) {
		super(new BorderLayout());
		
		title = new JLabel(titleStr);
		
		setBackground(Color.RED);
		
		add(title, BorderLayout.CENTER);
	}

	@Override
	public void discard() {
		
	}
}
