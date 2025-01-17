package com.digero.abcplayer.view;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataHandler;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;

public class PlaylistTransferHandler extends TransferHandler {
	
	public interface AbcLoaderCallback {
		public void load(List<File> files, int insertIdx);
	}
	
	private JTable playlistTable;
	private Consumer<File> playlistLoader;
	private AbcLoaderCallback abcFileLoader;
	private final DataFlavor indexDataFlavor =
		new ActivationDataFlavor(Integer.class, "application/x-java-Integer;class=java.lang.Integer", "Integer Row Index");
	
	
	public PlaylistTransferHandler(JTable playlistTable) {
		this.playlistTable = playlistTable;
	}
	
	public void setPlaylistLoadCallback(Consumer<File> callback) {
		playlistLoader = callback;
	}
	
	public void setAbcFileLoadCallback(AbcLoaderCallback callback) {
		abcFileLoader = callback;
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
				@SuppressWarnings("unchecked")
				List<File> files = (List<File>)(t.getTransferData(DataFlavor.javaFileListFlavor));
				
				// Playlist load
				if (files.size() == 1 && files.get(0).getName().endsWith(".abcp")) {
					playlistLoader.accept(files.get(0));
					return true;
				}
				
				abcFileLoader.load(files, idx);
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
