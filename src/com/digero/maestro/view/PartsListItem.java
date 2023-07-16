package com.digero.maestro.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JList;

import com.digero.common.util.IDiscardable;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class PartsListItem extends JPanel implements IDiscardable, TableLayoutConstants
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1794798972919435415L;
	private static final int GUTTER_COLUMN = 0;
	private static final int TITLE_COLUMN = 1;
	private static final int SOLO_COLUMN = 2;
	private static final int MUTE_COLUMN = 3;
	
	static final int GUTTER_WIDTH = 4;
	static final int TITLE_WIDTH = 100;
	static final int SOLO_WIDTH = 8;
	static final int MUTE_WIDTH = 8;
	
	private static double[] LAYOUT_COLS = new double[] {GUTTER_WIDTH, FILL, PREFERRED, PREFERRED};
	private static double[] LAYOUT_ROWS = new double[] {PREFERRED};
	
	private JPanel gutter;
	private JLabel title;
	private JButton soloButton;
	private JButton muteButton;
	
	public PartsListItem()
	{
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));
		
		gutter = new JPanel();
		
		title = new JLabel("This is test");
		
		soloButton = new JButton("<html><b>S</b></html>");
		soloButton.setPreferredSize(new Dimension(22, 22));
		soloButton.setMargin( new Insets(0, 0, 0, 0));
		soloButton.setFocusable(false);
		
		muteButton = new JButton("M");
		muteButton.setPreferredSize(new Dimension(22, 22));
		muteButton.setMargin( new Insets(0, 0, 0, 0));
		muteButton.setFocusable(false);
		
		add(gutter, GUTTER_COLUMN + ", 0");
		add(title, TITLE_COLUMN + ", 0");
		add(soloButton, SOLO_COLUMN + ", 0");
		add(muteButton, MUTE_COLUMN + ", 0");
		JList list = new JList();
		
		setBackground(list.getSelectionBackground());
	}
	
	@Override
	public void discard()
	{
		
	}
	
}
