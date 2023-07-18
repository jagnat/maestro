package com.digero.maestro.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import com.digero.common.util.IDiscardable;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class PartsListItem extends JPanel implements IDiscardable, TableLayoutConstants
{
	private static final long serialVersionUID = -1794798972919435415L;
	
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
	
	private boolean isSolo = false;
	private boolean isMute = false;
	
	public PartsListItem()
	{
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));
		
		gutter = new JPanel();
		
		title = new JLabel("This is test");
		
		soloButton = new JButton("S");
		soloButton.setPreferredSize(new Dimension(22, 22));
		soloButton.setMargin( new Insets(0, 0, 0, 0));
		soloButton.setFocusable(false);
		soloButton.addActionListener(e -> {
			isSolo = !isSolo;
			soloButton.setBackground(isSolo? Color.decode("#aaaaff") : new JButton().getBackground());
			soloButton.setText(isSolo? "<html><b>S</b></html>" : "S");
		});
		
		muteButton = new JButton("M");
		muteButton.setPreferredSize(new Dimension(22, 22));
		muteButton.setMargin( new Insets(0, 0, 0, 0));
		muteButton.setFocusable(false);
		muteButton.addActionListener(e -> {
			isMute = !isMute;
			muteButton.setBackground(isMute? Color.decode("#ff7777") : new JButton().getBackground());
			muteButton.setText(isMute? "<html><b>M</b></html>" : "M");
		});
		
		int col = -1;
		add(gutter, ++col + ", 0");
		add(title, ++col + ", 0");
		add(soloButton, ++col + ", 0");
		add(muteButton, ++col + ", 0");
	}
	
	void setSelected(boolean selected)
	{
		JList dummy = new JList();
		Color bg = selected? dummy.getSelectionBackground() : dummy.getBackground();
		Color fg = selected? dummy.getSelectionForeground() : dummy.getForeground();
		setBackground(bg);
		setForeground(fg);
//		title.setText("<html><b><u>This is a test</u></b></html>");
	}
	
	@Override
	public void discard()
	{
		
	}
	
}
