package com.digero.abcplayer.view;

import java.util.ArrayList;

import javax.swing.table.AbstractTableModel;

import com.digero.common.abctomidi.AbcInfo;

public class AbcInfoTableModel extends AbstractTableModel {

	private static final long serialVersionUID = -7672178885656979023L;
	
	private static final int COL_COUNT = 5;
	
	private ArrayList<AbcInfo> data = new ArrayList<AbcInfo>();
	
	public void addRow(AbcInfo inf) {
		data.add(inf);
		fireTableRowsInserted(0, getRowCount());
	}
	
	@Override
	public int getColumnCount() {
		return COL_COUNT;
	}

	@Override
	public int getRowCount() {
		return data.size();
	}
	
	@Override
	public String getColumnName(int colIndex) {
		switch (colIndex) {
		case 0:
			return "Song Name";
		case 1:
			return "Part Count";
		case 2:
			return "Duration";
		case 3:
			return "Composer";
		case 4:
			return "Transcriber";
		}
		return "ERR";
	}

	@Override
	public Object getValueAt(int rowIndex, int colIndex) {
		AbcInfo inf = data.get(rowIndex);
		switch(colIndex) {
		case 0: return inf.getTitle();
		case 1: return inf.getPartCount();
		case 2: return inf.getSongDurationStr();
		case 3: return inf.getComposer();
		case 4: return inf.getTranscriber();
		}
		return null;
	}
	
	public AbcInfo getAbcInfoAt(int rowIndex) {
		return data.get(rowIndex);
	}
	
	public void removeRow(int rowIdx) {
		data.remove(rowIdx);
		fireTableRowsDeleted(rowIdx, rowIdx);
	}

}
