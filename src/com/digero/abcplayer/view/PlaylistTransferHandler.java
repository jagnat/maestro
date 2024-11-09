package com.digero.abcplayer.view;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataHandler;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;

import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;

public class PlaylistTransferHandler extends TransferHandler {
	
	private JTree fileTree;
	private JTable playlistTable;
	private final DataFlavor indexDataFlavor =
		new ActivationDataFlavor(Integer.class, "application/x-java-Integer;class=java.lang.Integer", "Integer Row Index");
	
	
	public PlaylistTransferHandler(JTree fileTree, JTable playlistTable) {
		this.fileTree=fileTree;
		this.playlistTable = playlistTable;
	}
	
	@Override
	public boolean canImport(TransferHandler.TransferSupport info) {
		if (info.getComponent() != playlistTable) {
			return false;
		}
		if (!info.isDrop()) {
			return false;
		}
		
		// String from internal GUI components or file list from file explorer
		if (!(info.isDataFlavorSupported(indexDataFlavor) || info.isDataFlavorSupported(DataFlavor.javaFileListFlavor))) {
			return false;
		}

		return true;
	}
	
	@Override
	public boolean importData(TransferHandler.TransferSupport info) {
		if (!info.isDrop() || info.getComponent() != playlistTable) {
			return false;
		}
		
		JTable.DropLocation dl = (JTable.DropLocation)info.getDropLocation();
		int idx = dl.getRow();
		AbcInfoTableModel model = (AbcInfoTableModel)playlistTable.getModel();
		
		Transferable t = info.getTransferable();
		try {
			// Dragging from file explorer or from JTree explorer
			if (info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				List<File> files = (List<File>)(t.getTransferData(DataFlavor.javaFileListFlavor));
				
				List<AbcInfo> data = new ArrayList<>();
				
				new SwingWorker<Boolean, Boolean>() {	
		            @Override
		            protected Boolean doInBackground() throws Exception {
		            	for (File file : files) {
		            		if (file.isDirectory()) {
		            			continue;
		            		}
		            		List<FileAndData> fad = new ArrayList<FileAndData>();
		            		fad.add(new FileAndData(file, AbcToMidi.readLines(file)));
		            		data.add(AbcToMidi.parseAbcMetadata(fad));
		            	}
		            	return true;
		            }
		            
		            @Override
		            protected void done() {
		            	for (AbcInfo info : data) {
		            		model.insertRow(info, idx);
		            	}
		            }
				}.execute();
			}
			// Reorder operation within table using index
			else if (info.isDataFlavorSupported(indexDataFlavor)) {
				Integer rowSrc = (Integer)t.getTransferData(indexDataFlavor);
				if (rowSrc >= 0) {
					int selectedRows[] = playlistTable.getSelectedRows();
					model.moveRows(selectedRows, idx);
					playlistTable.clearSelection();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	@Override
	protected Transferable createTransferable(JComponent c) {
		int rows[] = playlistTable.getSelectedRows();
		
		if (rows == null) {
			return null;
		}
		return new DataHandler(new Integer(rows[0]), indexDataFlavor.getMimeType());
	}

	@Override
	protected void exportDone(JComponent source, Transferable data, int action) {
	}

	@Override
	public int getSourceActions(JComponent c) {
		return COPY_OR_MOVE;
	}

	private static final long serialVersionUID = 7948705873203228584L;

}
