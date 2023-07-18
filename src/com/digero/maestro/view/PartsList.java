package com.digero.maestro.view;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;

import com.digero.common.util.IDiscardable;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartMetadataSource;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class PartsList extends JPanel implements IDiscardable, TableLayoutConstants, ListDataListener
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 3832994437250655508L;
	
	private static double[] LAYOUT_COLS = new double[] {FILL};
	private static double[] LAYOUT_ROWS = new double[] {PREFERRED, PREFERRED, PREFERRED, PREFERRED};
	
	private ListModel<AbcPartMetadataSource> model;
	private TableLayout layout;
	
	public PartsList()
	{
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));
		
		layout = (TableLayout)getLayout();
		
		for (int i = 0; i < LAYOUT_ROWS.length; i++)
		{
			add(new PartsListItem(), "0, " + i);
		}
		
		PartsListItem selected = new PartsListItem();
		selected.setSelected(true);
		layout.insertRow(2, PREFERRED);
		add(selected, "0, 2");
		
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
