package com.digero.maestro.view;

import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartMetadataSource;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.AbcSongEvent;

import info.clearthought.layout.TableLayoutConstants;

@SuppressWarnings("serial")
public class PartsList extends JPanel implements
	IDiscardable, TableLayoutConstants
{
	private DefaultListModel<AbcPart> model;
	private BoxLayout layout;
	
	private List<PartsListItem> parts = new ArrayList<PartsListItem>();
	private AbcPart selectedPart = null;
	private int selectedIndex = 0;
	
	public PartsList()
	{
		layout = new BoxLayout(this, BoxLayout.Y_AXIS);
		setLayout(layout);
		setBackground(new JList<AbcPartMetadataSource>().getBackground());
		
		model = new DefaultListModel<AbcPart>();
	}
	
	public void regenerateParts()
	{
		parts = new ArrayList<PartsListItem>();
		removeAll();
		
		for (int i = 0; i < model.getSize(); i++)
		{
			addPart(i);
		}
		
		this.revalidate();
		this.repaint();
	}
	
	private void addPart(int idx)
	{
		AbcPart part = model.elementAt(idx);
		PartsListItem item = new PartsListItem(part);
		
		item.setListSelectionListener(selectionListener);
		
		if (part == selectedPart)
		{
			selectedIndex = idx;
			item.setSelected(true);
		}
		
		parts.add(idx, item);
		add(item);
	}
	
	public void selectPart(int idx)
	{
		for (int i = 0; i < parts.size(); i++)
		{
			PartsListItem item = parts.get(i);
			item.setSelected(i == idx);
		}
		selectedIndex = idx;
		selectedPart = parts.get(idx).getPart();
		
		for (ListSelectionListener listener : listenerList.getListeners(ListSelectionListener.class))
		{
			ListSelectionEvent event = new ListSelectionEvent(parts.get(idx).getPart(), idx, idx, false);
			listener.valueChanged(event);
		}
		
		revalidate();
		repaint();
	}
	
	public void init()
	{
		regenerateParts();
	}

	@Override
	public void discard()
	{
		
	}
	
	int getSelectedIndex()
	{
		return selectedIndex;
	}
	
	AbcPart getSelectedPart()
	{
		if (model == null) return null;
		return model.elementAt(getSelectedIndex());
	}
	
	private int getIndexOfPart(AbcPart part)
	{
		for (int i = 0; i < model.size(); i++)
		{
			if (part.equals(model.get(i)))
				return i;
		}
		return -1;
	}
	
	DefaultListModel<AbcPart> getModel()
	{
		return model;
	}
	
	public void setModel(DefaultListModel<AbcPart> model)
	{
		this.model = model;
		init();
	}
	
	public void addListSelectionListener(ListSelectionListener listener)
	{
		listenerList.add(ListSelectionListener.class, listener);
	}
	
	public void removeListSelectionListener(ListSelectionListener listener)
	{
		listenerList.remove(ListSelectionListener.class, listener);
	}
	
	void ensureIndexIsVisible(int index)
	{
		
	}
	
	// Listens to the PartsListItems for selection events
	// (only using AbcPartEvent out of convenience)
	public Listener<AbcPartEvent> selectionListener = e -> {
		AbcPart part = (AbcPart) e.getSource();
		int i = getIndexOfPart(part);
		if (i < 0) return;
		
		selectPart(i);
	};
	
	public Listener<AbcPartEvent> partListener = e -> {
		switch(e.getProperty())
		{
		case TRACK_ENABLED:
		case INSTRUMENT:
		case TITLE:
			System.out.println(e.getProperty().name());
			regenerateParts();
			break;
		default:
			break;
		}
	};
	
	public Listener<AbcSongEvent> songListener = e -> {
		AbcSong song = e.getSource();
		if (song == null)
			return;
		
		switch(e.getProperty())
		{
		case PART_ADDED:
			e.getPart().addAbcListener(partListener);
			regenerateParts();
			System.out.println(e.getProperty().name());
			break;
		case BEFORE_PART_REMOVED:
			e.getPart().removeAbcListener(partListener);
			System.out.println(e.getProperty().name());
			break;
		case PART_LIST_ORDER:
			System.out.println(e.getProperty().name());
			regenerateParts();
			break;
		default:
			break;
		}
	};
}
