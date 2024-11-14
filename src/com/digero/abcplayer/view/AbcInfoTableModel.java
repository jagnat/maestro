package com.digero.abcplayer.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.digero.common.abctomidi.AbcInfo;

public class AbcInfoTableModel extends AbstractTableModel {

	private static final long serialVersionUID = -7672178885656979023L;
	
	private static final int COL_COUNT = 5;
	
	private ArrayList<AbcInfo> data = new ArrayList<AbcInfo>();
	
	public List<AbcInfo> getTableData() {
		return data;
	}
	
	public void clearRows() {
		int sz = getRowCount();
		data.clear();
		fireTableRowsDeleted(0, sz);
	}
	
	public void addRow(AbcInfo inf) {
		data.add(inf);
		fireTableRowsInserted(getRowCount() - 1, getRowCount() - 1);
	}
	
	public void insertRow(AbcInfo inf, int idx) {
		data.add(idx, inf);
		fireTableRowsInserted(idx, idx + 2);
	}
	
	public void moveRows(int rowsToMove[], int toIdx) {
		int effectiveInsertIdx = toIdx;
		ArrayList<AbcInfo> tmpSwap = new ArrayList<AbcInfo>();
		Arrays.sort(rowsToMove);

		for (int i = rowsToMove.length - 1; i >= 0; i--) {
			int r = rowsToMove[i];
			if (r < toIdx) {
				effectiveInsertIdx--;
			}
			tmpSwap.add(data.get(r));
			data.remove(r);
		}
		
		for (int i = 0; i < tmpSwap.size(); i++) {
			data.add(effectiveInsertIdx, tmpSwap.get(i));
		}
		
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
	
	public int getIdxForAbcInfo(AbcInfo inf) {
		for (int i = 0; i < data.size(); i++) {
			if (data.get(i) == inf) {
				return i;
			}
		}
		return -1;
	}
	
	public AbcInfo getAbcInfoAt(int rowIndex) {
		return data.get(rowIndex);
	}
	
	public void removeRow(int rowIdx) {
		data.remove(rowIdx);
		fireTableRowsDeleted(rowIdx, rowIdx);
	}

}
