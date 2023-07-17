package com.digero.maestro.view;

import javax.swing.DefaultListModel;
import javax.swing.JPanel;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;

import com.digero.common.util.IDiscardable;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartMetadataSource;

import info.clearthought.layout.TableLayoutConstants;

public class PartsList extends JPanel implements IDiscardable, TableLayoutConstants, ListDataListener
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 3832994437250655508L;
	
	private static double[] LAYOUT_COLS = new double[] {FILL};
	
	private ListModel<AbcPartMetadataSource> model;
	
	public PartsList()
	{
		model = new DefaultListModel<AbcPartMetadataSource>();
	}
	
	public void init()
	{
		model.addListDataListener(this);
	}

	@Override
	public void discard()
	{
		
	}
	
	int getSelectedIndex()
	{
		return 0;
	}
	
	void setSelectedIndex(int i)
	{
		
	}
	
	ListModel<AbcPartMetadataSource> getModel()
	{
		return model;
	}
	
	public void addListSelectionListener(ListSelectionListener listener)
	{
		
	}
	
	AbcPart getSelectedPart()
	{
		return (AbcPart)model.getElementAt(0);
	}
	
	void ensureIndexIsVisible(int index)
	{
		
	}

	// ListDataListener
	@Override
	public void contentsChanged(ListDataEvent e)
	{
		
	}

	// ListDataListener
	@Override
	public void intervalAdded(ListDataEvent e)
	{
		
	}

	// ListDataListener
	@Override
	public void intervalRemoved(ListDataEvent e)
	{
		
	}
}
