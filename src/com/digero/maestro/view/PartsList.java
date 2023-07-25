package com.digero.maestro.view;

import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
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
	
	private DefaultListModel<AbcPart> model;
	private TableLayout layout;
	
	private List<PartsListItem> partItemList = new ArrayList<PartsListItem>();
	
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
		add(selected, "0, 2");
		
		model = new DefaultListModel<AbcPart>();
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
	
	DefaultListModel<AbcPart> getModel()
	{
		return model;
	}
	
	void setModel(DefaultListModel<AbcPart> model)
	{
		model.removeListDataListener(this);
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
	
	AbcPart getSelectedPart()
	{
		return (AbcPart)model.getElementAt(0);
	}
	
	void ensureIndexIsVisible(int index)
	{
		
	}
	
	private int thre = 0;

	// ListDataListener
	@Override
	public void contentsChanged(ListDataEvent e)
	{
		System.out.println(thre++ + " : 0 list listener " + e.getIndex0() + ", " + e.getIndex1() + "        type : " + e.getType());
	}

	// ListDataListener
	@Override
	public void intervalAdded(ListDataEvent e)
	{
		System.out.println(thre++ + " : 1 list listener " + e.getIndex0() + ", " + e.getIndex1() + "        type : " + e.getType());
	}

	// ListDataListener
	@Override
	public void intervalRemoved(ListDataEvent e)
	{
		System.out.println(thre++ + " : 2 list listener " + e.getIndex0() + ", " + e.getIndex1() + "        type : " + e.getType());
	}
}
