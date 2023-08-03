package com.digero.maestro.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class PartsListItem extends JPanel implements IDiscardable, TableLayoutConstants
{
	private static final long serialVersionUID = -1794798972919435415L;
	
	static final int GUTTER_WIDTH = 4;
	static final int TITLE_WIDTH = 100;
	static final int SOLO_WIDTH = 8;
	static final int MUTE_WIDTH = 8;
	
	private static double[] LAYOUT_COLS = new double[] {FILL, PREFERRED, PREFERRED};
	private static double[] LAYOUT_ROWS = new double[] {PREFERRED};
	
	private JLabel title;
	private JButton soloButton;
	private JButton muteButton;
	
	private boolean isSolo = false;
	private boolean isMute = false;
	
	private AbcPart part;
	
	private Color selectedFg, selectedBg, unselectedFg, unselectedBg;
	
	private Listener<AbcPartEvent> selectListener = null;
	
	public PartsListItem(AbcPart part)
	{
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));
		
		this.setPart(part);
		
		title = new JLabel(part.toString());
		title.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
		
		JList dummy = new JList();
		selectedFg = dummy.getSelectionForeground();
		selectedBg = dummy.getSelectionBackground();
		unselectedFg = dummy.getForeground();
		unselectedBg = dummy.getBackground();
		
		setBackground(unselectedBg);
		setForeground(unselectedFg);
		
		Dimension buttonSize = new Dimension(22, 22);
		
		soloButton = new JButton("S");
		soloButton.setPreferredSize(buttonSize);
		soloButton.setMargin( new Insets(0, 0, 0, 0));
		soloButton.setFocusable(false);
		soloButton.addActionListener(e -> {
			isSolo = !isSolo;
			soloButton.setBackground(isSolo? Color.decode("#7e7eff") : new JButton().getBackground());
			soloButton.setText(isSolo? "<html><b>S</b></html>" : "S");
		});
		
		muteButton = new JButton("M");
		
		muteButton.setPreferredSize(buttonSize);
		muteButton.setMargin( new Insets(0, 0, 0, 0));
		
//		muteButton.setMargin( new Insets(2, 4, 2, 3));
		
		muteButton.setFocusable(false);
		muteButton.addActionListener(e -> {
			isMute = !isMute;
			muteButton.setBackground(isMute? Color.decode("#ff7777") : new JButton().getBackground());
			muteButton.setText(isMute? "<html><b>M</b></html>" : "M");
		});
		
		int col = -1;
		add(title, ++col + ", 0");
		add(soloButton, ++col + ", 0");
		add(muteButton, ++col + ", 0");
		
		title.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (selectListener != null)
                {
                	AbcPartEvent ev = new AbcPartEvent(part, AbcPartProperty.SELECTED, isMute, AbcPartEvent.NO_TRACK_NUMBER);
                	selectListener.onEvent(ev);
                }
            }

        });
	}
	
	public void setListSelectionListener(Listener<AbcPartEvent> l)
	{
		selectListener = l;
	}
	
	public void removeListSelectionListener(Listener<AbcPartEvent> l)
	{
		selectListener = null;
	}
	
	void setSelected(boolean selected)
	{
		setBackground(selected? selectedBg : unselectedBg);
		setForeground(selected? selectedFg : unselectedFg);
//		title.setText("<html><b><u>This is a test</u></b></html>");
	}
	
	@Override
	public void discard()
	{
		
	}

	public AbcPart getPart() {
		return part;
	}

	public void setPart(AbcPart part) {
		this.part = part;
	}
	
}
