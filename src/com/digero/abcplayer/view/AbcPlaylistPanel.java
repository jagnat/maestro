package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.stream.IntStream;

import javax.activation.DataHandler;
import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.TreePath;

import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;
import com.digero.common.util.AbcFileTreeModel;
import com.digero.common.util.AbcFileTreeModel.AbcSongFileNode;
import com.digero.common.util.Listener;
import com.digero.common.util.Util;
import com.digero.common.view.AbcPlaylistTreeCellRenderer;

public class AbcPlaylistPanel extends JPanel {
	
	public static class PlaylistEvent extends EventObject {

		private static final long serialVersionUID = 4331827283417203704L;
		
		public enum PlaylistEventType {
			PLAY_FROM_ABCINFO, PLAY_FROM_FILE
		}
		
		private final PlaylistEventType type;

		public PlaylistEvent(Object source, PlaylistEventType t) {
			super(source);
			type = t;
		}
		
		public PlaylistEventType getType() {
			return type;
		}
	}
	
	private static final long serialVersionUID = -2515263857830606770L;
	private JSplitPane browserSplitPane;
	private JPanel leftPanel;
	private JPanel rightPanel;
	
	// Left
	private JTree abcFileTree;
	// Set with row location of menu right click in mouse listener
	// for menu item action listeners
	private int menuRowIdx = -1;
	private AbcFileTreeModel abcFileTreeModel;
	private JScrollPane fileTreeScrollPane;
	private JPanel fileTreeBottomControls;
	private JButton addToPlaylistButton;
	
	// Right
	private JTable playlistTable;
	private AbcInfoTableModel tableModel;
	private JScrollPane playlistScrollPane;
	private JPanel playlistBottomControls;
	private JPopupMenu playlistContentPopupMenu;
	private JPopupMenu playlistHeaderPopupMenu;
	
	private Listener<PlaylistEvent> parentListener;
	
	public AbcPlaylistPanel() {
		super (new BorderLayout());
		
		browserSplitPane = new JSplitPane();
		browserSplitPane.setResizeWeight(0.5);
		add(browserSplitPane, BorderLayout.CENTER);
		
		leftPanel = new JPanel(new BorderLayout());
		rightPanel = new JPanel(new BorderLayout());
		
		browserSplitPane.setLeftComponent(leftPanel);
		browserSplitPane.setRightComponent(rightPanel);
		
		ArrayList<File> tlds = new ArrayList<File>();
		tlds.add(Util.getLotroMusicPath(false));
		
		abcFileTreeModel = new AbcFileTreeModel(tlds);
		abcFileTreeModel.refresh();
		
		abcFileTree = new JTree();
		abcFileTree.setShowsRootHandles(true);
		abcFileTree.setFocusable(false);
		abcFileTree.setRootVisible(false);
		abcFileTree.setModel(abcFileTreeModel);
		abcFileTree.setCellRenderer(new AbcPlaylistTreeCellRenderer());
		abcFileTree.collapseRow(0);
		abcFileTree.setDragEnabled(true);
		abcFileTree.setTransferHandler(new TransferHandler() {
			private static final long serialVersionUID = -3378747637795103415L;

			@Override
			protected Transferable createTransferable(JComponent c) {
				JTree tree = (JTree) c;
		        TreePath[] selectedPaths = tree.getSelectionPaths();
		        if (selectedPaths == null) {
		        	return null;
		        }

				List<File> fileList = new ArrayList<>();
				for (TreePath path : selectedPaths) {
					AbcSongFileNode node = (AbcSongFileNode) path.getLastPathComponent();
					fileList.add(node.getFile());
		        }

				// Use DataHandler to create a Transferable with the javaFileListFlavor
				return new FileListTransferable(fileList);
		    }

			@Override
			public int getSourceActions(JComponent c) {
	    		return COPY_OR_MOVE;
			}
			
			// Necessary since for some reason there is no exposed Transferable class for DataFlavor.javaFileListFlavor
			class FileListTransferable implements Transferable {
				
				private final List<File> files;
				
				public FileListTransferable(List<File> files) {
					this.files = files;
				}

				@Override
				public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
					if (!isDataFlavorSupported(flavor)) {
						throw new UnsupportedFlavorException(flavor);
					}
					return files;
				}

				@Override
				public DataFlavor[] getTransferDataFlavors() {
					return new DataFlavor[] {DataFlavor.javaFileListFlavor};
				}

				@Override
				public boolean isDataFlavorSupported(DataFlavor flavor) {
					return DataFlavor.javaFileListFlavor.equals(flavor);
				}
				
			}
		});
		abcFileTree.addTreeSelectionListener(e -> {
			// Only allow adding files if all the selected ones are abc files (no folders)
			boolean noFoldersSelected = true;
			TreePath[] paths = abcFileTree.getSelectionPaths();
			if (paths != null) {
				for (TreePath path : paths) {
					AbcSongFileNode f = (AbcSongFileNode)path.getLastPathComponent();
					if (f.getFile().isDirectory()) {
						noFoldersSelected = false;
						break;
					}
				}
			}
			addToPlaylistButton.setEnabled(noFoldersSelected);
		});
		JPopupMenu fileTreePopup = new JPopupMenu();
		
		JMenuItem fileTreeAddToPlaylist = new JMenuItem("Add to playlist");
		fileTreeAddToPlaylist.addActionListener(e -> {
			addTreePathsToPlaylist(abcFileTree.getSelectionPaths());
		});
		fileTreePopup.add(fileTreeAddToPlaylist);
		
		JMenuItem fileTreePlay = new JMenuItem("Play");
		fileTreePlay.addActionListener(e -> {
//			 AbcInfo info = tableModel.getAbcInfoAt(playlistTable.getSelectedRow());
			AbcSongFileNode f = (AbcSongFileNode)(abcFileTree.getPathForRow(menuRowIdx).getLastPathComponent());
			 if (parentListener != null) {
				 parentListener.onEvent(new PlaylistEvent(f.getFile(), PlaylistEvent.PlaylistEventType.PLAY_FROM_FILE));
			 }
		});
		fileTreePopup.add(fileTreePlay);
		
		abcFileTree.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				doPopupCheck(e);
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {
				doPopupCheck(e);
			}
			
			public void doPopupCheck(MouseEvent e) {
				if (e.isPopupTrigger()) {
					menuRowIdx = abcFileTree.getClosestRowForLocation(e.getX(), e.getY());
					if (menuRowIdx != -1) {
						abcFileTree.getSelectionRows();
						if (!IntStream.of(abcFileTree.getSelectionRows()).anyMatch(x -> x == menuRowIdx)) {
							abcFileTree.setSelectionRows(new int[] {menuRowIdx});
						}
						TreePath path = abcFileTree.getPathForRow(menuRowIdx);
						AbcSongFileNode f = (AbcSongFileNode)path.getLastPathComponent();
						fileTreePlay.setEnabled(!f.getFile().isDirectory());
						fileTreePopup.show(e.getComponent(), e.getX(), e.getY());	
					}
				}
			}
		});
		ToolTipManager.sharedInstance().registerComponent(abcFileTree);
		fileTreeScrollPane = new JScrollPane(abcFileTree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		fileTreeBottomControls = new JPanel(new FlowLayout());
		addToPlaylistButton = new JButton(">>");
		addToPlaylistButton.setEnabled(false);
		addToPlaylistButton.addActionListener(e -> {
			addTreePathsToPlaylist(abcFileTree.getSelectionPaths());
		});
		fileTreeBottomControls.add(addToPlaylistButton);
		
		JLabel abcBrowserLabel = new JLabel("ABC Browser");
		Font f = abcBrowserLabel.getFont();
		abcBrowserLabel.setFont(f.deriveFont(Font.BOLD, f.getSize2D()));
		abcBrowserLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		leftPanel.add(fileTreeScrollPane, BorderLayout.CENTER);
		leftPanel.add(fileTreeBottomControls, BorderLayout.SOUTH);
		leftPanel.add(abcBrowserLabel, BorderLayout.NORTH);
		
		tableModel = new AbcInfoTableModel();
		
		playlistTable = new JTable(tableModel);
		playlistTable.setFocusable(false);
		playlistTable.setFillsViewportHeight(true);
		playlistTable.setDragEnabled(true);
		playlistTable.setDropMode(DropMode.INSERT_ROWS);
		playlistTable.setTransferHandler(new PlaylistTransferHandler(abcFileTree, playlistTable));
		
		
		playlistContentPopupMenu = new JPopupMenu();
		JMenuItem playItem = new JMenuItem("Play");
		playItem.addActionListener(e -> {
			 AbcInfo info = tableModel.getAbcInfoAt(playlistTable.getSelectedRow());
			 if (parentListener != null) {
				 parentListener.onEvent(new PlaylistEvent(info, PlaylistEvent.PlaylistEventType.PLAY_FROM_ABCINFO));
			 }
		});
		playlistContentPopupMenu.add(playItem);
		JMenuItem removeItem = new JMenuItem("Remove");
		removeItem.addActionListener(e -> {
			while (playlistTable.getSelectedRow() != -1) {
				tableModel.removeRow(playlistTable.getSelectedRow());
			}
		});
		playlistContentPopupMenu.add(removeItem);
		playlistTable.setComponentPopupMenu(playlistContentPopupMenu);
		
		playlistTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				Point p = e.getPoint();
				int row = playlistTable.rowAtPoint(p);
				if (row != -1 && !IntStream.of(playlistTable.getSelectedRows()).anyMatch(x -> x == row)) {
					playlistTable.setRowSelectionInterval(row, row);
				}
			}
		});
		
		playlistHeaderPopupMenu = new JPopupMenu();
		playlistTable.getTableHeader().setComponentPopupMenu(playlistHeaderPopupMenu);
		playlistTable.getTableHeader().setReorderingAllowed(false);
		
        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();  
        centered.setHorizontalAlignment(SwingConstants.CENTER);
		
		playlistScrollPane = new JScrollPane(playlistTable,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		playlistScrollPane.setViewportView(playlistTable);
		
		JCheckBox autoplayCheckBox = new JCheckBox("Autoplay");
		JButton moveUpButton = new JButton("Move Up");
		JButton moveDownButton = new JButton("Move Down");
		
		playlistBottomControls = new JPanel(new FlowLayout());
		playlistBottomControls.add(autoplayCheckBox);
		playlistBottomControls.add(moveUpButton);
		playlistBottomControls.add(moveDownButton);
		
		JLabel abcPlaylistLabel = new JLabel("ABC Playlist");
		f = abcPlaylistLabel.getFont();
		abcPlaylistLabel.setFont(f.deriveFont(Font.BOLD, f.getSize2D()));
		abcPlaylistLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		rightPanel.add(playlistScrollPane, BorderLayout.CENTER);
		rightPanel.add(playlistBottomControls, BorderLayout.SOUTH);
		rightPanel.add(abcPlaylistLabel, BorderLayout.NORTH);
	}
	
	private void addTreePathsToPlaylist(TreePath[] paths) {
		List<AbcInfo> data = new ArrayList<>();
		new SwingWorker<Boolean, Boolean>() {
			
            @Override
            protected Boolean doInBackground() throws Exception {
            	for (TreePath path : paths) {
            		AbcSongFileNode node = (AbcSongFileNode)path.getLastPathComponent();
            		File file = node.getFile();
            		List<FileAndData> fad = new ArrayList<FileAndData>();
            		fad.add(new FileAndData(file, AbcToMidi.readLines(file)));
            		data.add(AbcToMidi.parseAbcMetadata(fad));
            	}
            	return true;
            }
            
            @Override
            protected void done() {
            	for (AbcInfo info : data) {
            		tableModel.addRow(info);
            	}
            }
		}.execute();
	}
	
	public void setPlaylistListener(Listener<PlaylistEvent> l) {
		parentListener = l;
	}
	
	public void removePlaylistListener() {
		parentListener = null;
	}
}
