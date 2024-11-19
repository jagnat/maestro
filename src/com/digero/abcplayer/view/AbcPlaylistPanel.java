package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.IntStream;

import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
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
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreePath;

import com.digero.abcplayer.AbcPlaylistXmlCoder;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;
import com.digero.common.util.AbcFileTreeModel;
import com.digero.common.util.AbcFileTreeModel.AbcSongFileNode;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.Listener;
import com.digero.common.util.ParseException;
import com.digero.common.util.Util;
import com.digero.common.view.AbcPlaylistTreeCellRenderer;
import com.digero.maestro.util.XmlUtil;

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
	private JCheckBox autoplayCheckBox;
	private JLabel abcPlaylistLabel;
	
	private JFileChooser openPlaylistChooser = null;
	private JFileChooser savePlaylistChooser = null;
	
	private AbcInfo nowPlayingInfo = null;
	
	private Listener<PlaylistEvent> parentListener;
	
	private Preferences playlistPrefs;
	
	public AbcPlaylistPanel(Preferences prefs) {
		super (new BorderLayout());
		
		playlistPrefs = prefs;
		
		browserSplitPane = new JSplitPane();
		browserSplitPane.setResizeWeight(0.5);
		
		leftPanel = new JPanel(new BorderLayout());
		rightPanel = new JPanel(new BorderLayout());
		
		browserSplitPane.setLeftComponent(leftPanel);
		browserSplitPane.setRightComponent(rightPanel);
		int pos = prefs.getInt("splitPanePos", -1);
		if (pos != -1) {
			browserSplitPane.setDividerLocation(pos);
		}
		browserSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
			prefs.putInt("splitPanePos", (Integer)e.getNewValue());
		});
		add(browserSplitPane, BorderLayout.CENTER);
		
		ArrayList<File> tlds = new ArrayList<File>();
		tlds.add(Util.getLotroMusicPath(false));
		
		abcFileTreeModel = new AbcFileTreeModel(tlds);
		abcFileTreeModel.refresh();
		
		abcFileTree = new JTree();
		abcFileTree.setShowsRootHandles(true);
		abcFileTree.setFocusable(true);
		abcFileTree.setRootVisible(false);
		abcFileTree.setModel(abcFileTreeModel);
		abcFileTree.setCellRenderer(new AbcPlaylistTreeCellRenderer());
		abcFileTree.collapseRow(0);
		abcFileTree.setDragEnabled(true);
		abcFileTree.setToggleClickCount(0);
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
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
					int idx = abcFileTree.getClosestRowForLocation(e.getX(), e.getY());
					if (idx == -1) {
						return;
					}
					AbcSongFileNode f = (AbcSongFileNode)(abcFileTree.getPathForRow(idx).getLastPathComponent());
					if (f.getFile().isDirectory()) {
						return;
					}
					if (parentListener != null) {
						parentListener.onEvent(new PlaylistEvent(f.getFile(), PlaylistEvent.PlaylistEventType.PLAY_FROM_FILE));
					 }
				}
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
		
		JButton refreshTreeButton = new JButton("Refresh");
		refreshTreeButton.addActionListener(e -> {
			abcFileTreeModel.refresh();
		});
		fileTreeBottomControls.add(refreshTreeButton);
		
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
		
		abcPlaylistLabel = new JLabel("ABC Playlist");
		f = abcPlaylistLabel.getFont();
		abcPlaylistLabel.setFont(f.deriveFont(Font.BOLD, f.getSize2D()));
		abcPlaylistLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		tableModel = new AbcInfoTableModel();
		
		playlistTable = new JTable(tableModel) {
			private static final long serialVersionUID = 7474807902173697106L;
			
			@Override
			public Component prepareRenderer(TableCellRenderer render, int row, int col) {
				Component c = super.prepareRenderer(render, row, col);
				JComponent jc = (JComponent)c;
				AbcInfo inf = (row != -1) ? tableModel.getAbcInfoAt(row) : null;
				
				if (nowPlayingInfo != null && inf == nowPlayingInfo) {
					Color foreground = UIManager.getColor("Label.foreground");
					Border border = BorderFactory.createCompoundBorder(
							BorderFactory.createMatteBorder(2, 0, 2, 0, foreground),
							BorderFactory.createEmptyBorder(0, 3, 0, 3));
					jc.setBorder(border);
					Font f = new JLabel().getFont();
					f = f.deriveFont(Font.ITALIC);
					jc.setFont(f);
				}
				
				return jc;
			}
			
			@Override
			public String getToolTipText(MouseEvent e) {
				String txt = null;
				Point p = e.getPoint();
				int row = rowAtPoint(p);
				
				if (row != -1) {
					txt = "";
					AbcInfo inf = tableModel.getAbcInfoAt(row);
					for (int i = 0; i < inf.getPartCount(); i++) {
						if (i != 0) {
							txt += "\n";
						}
						txt = txt + inf.getPartFullName(i + 1);
					}
				}
				return txt;
			}
		};
		playlistTable.setFocusable(true);
		playlistTable.setFillsViewportHeight(true);
		playlistTable.setDragEnabled(true);
		playlistTable.setDropMode(DropMode.INSERT_ROWS);
		playlistTable.setTransferHandler(new PlaylistTransferHandler(abcFileTree, playlistTable));
		playlistTable.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_DELETE) {
					while (playlistTable.getSelectedRow() != -1) {
						tableModel.removeRow(playlistTable.getSelectedRow());
					}
				}
			}
		});
		playlistTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
					 AbcInfo info = tableModel.getAbcInfoAt(playlistTable.getSelectedRow());
					 nowPlayingInfo = info;
					if (parentListener != null) {
						 parentListener.onEvent(new PlaylistEvent(info, PlaylistEvent.PlaylistEventType.PLAY_FROM_ABCINFO));
					 }
					playlistTable.repaint();
				}
			}
			
			@Override
			public void mousePressed(MouseEvent e) {
				Point p = e.getPoint();
				int row = playlistTable.rowAtPoint(p);
				if (row != -1 && !IntStream.of(playlistTable.getSelectedRows()).anyMatch(x -> x == row)) {
					playlistTable.setRowSelectionInterval(row, row);
				}
			}
		});
		
		tableModel.addTableModelListener(e -> {
			updatePlaylistLabel();
		});
		
		playlistContentPopupMenu = new JPopupMenu();
		JMenuItem playItem = new JMenuItem("Play");
		playItem.addActionListener(e -> {
			 AbcInfo info = tableModel.getAbcInfoAt(playlistTable.getSelectedRow());
			 nowPlayingInfo = info;
			 playlistTable.repaint();
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
		
		playlistHeaderPopupMenu = new JPopupMenu();
		playlistTable.getTableHeader().setComponentPopupMenu(playlistHeaderPopupMenu);
		playlistTable.getTableHeader().setReorderingAllowed(false);
		
        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();  
        centered.setHorizontalAlignment(SwingConstants.CENTER);
		
		playlistScrollPane = new JScrollPane(playlistTable,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		playlistScrollPane.setViewportView(playlistTable);
		
		autoplayCheckBox = new JCheckBox("Autoplay");
		autoplayCheckBox.setFocusable(false);
		autoplayCheckBox.setSelected(playlistPrefs.getBoolean("autoplay", false));
		autoplayCheckBox.addActionListener(e -> {
			playlistPrefs.putBoolean("autoplay", autoplayCheckBox.isSelected());
		});
		JButton moveUpButton = new JButton("Move Up");
		moveUpButton.setFocusable(false);
		moveUpButton.setEnabled(false);
		moveUpButton.addActionListener(e -> {
			int row = playlistTable.getSelectedRow();
			if (row == 0) {
				return;
			}
			tableModel.moveRows(new int[] {row}, row - 1);
			playlistTable.setRowSelectionInterval(row - 1, row - 1);
		});
		JButton moveDownButton = new JButton("Move Down");
		moveDownButton.setFocusable(false);
		moveDownButton.setEnabled(false);
		moveDownButton.addActionListener(e ->{
			int row = playlistTable.getSelectedRow();
			if (row == tableModel.getRowCount() - 1) {
				return;
			}
			tableModel.moveRows(new int[] {row}, row + 2);
			playlistTable.setRowSelectionInterval(row + 1, row + 1);
		});
		JButton savePlaylistButton = new JButton("Save Playlist");
		savePlaylistButton.setFocusable(false);
		savePlaylistButton.addActionListener(e -> {
			savePlaylist();
		});
		JButton loadPlaylistButton = new JButton("Load Playlist");
		loadPlaylistButton.setFocusable(false);
		loadPlaylistButton.addActionListener(e -> {
			loadPlaylist();
		});
		
		playlistBottomControls = new JPanel(new FlowLayout());
		playlistBottomControls.add(autoplayCheckBox);
		playlistBottomControls.add(savePlaylistButton);
		playlistBottomControls.add(loadPlaylistButton);
		
		playlistTable.getSelectionModel().addListSelectionListener(e -> {
			moveUpButton.setEnabled(playlistTable.getSelectedRowCount() == 1);
			moveDownButton.setEnabled(playlistTable.getSelectedRowCount() == 1);
		});
		
		rightPanel.add(playlistScrollPane, BorderLayout.CENTER);
		rightPanel.add(playlistBottomControls, BorderLayout.SOUTH);
		rightPanel.add(abcPlaylistLabel, BorderLayout.NORTH);
	}
	
	public void savePlaylist() {
		if (savePlaylistChooser == null) {
			savePlaylistChooser = new JFileChooser();
			savePlaylistChooser.setFileFilter(new ExtensionFileFilter("ABC Playlist (.abcp)", "abcp"));
		}
		
		String folder = playlistPrefs.get("playlistDirectory", Util.getLotroMusicPath(false).getAbsolutePath());
		savePlaylistChooser.setCurrentDirectory(new File(folder));
		
		int result = savePlaylistChooser.showSaveDialog(this);
		
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		
		File file = savePlaylistChooser.getSelectedFile();
		String fileName = file.getName();
		int dot = fileName.lastIndexOf('.');
		if (dot <= 0 || !fileName.substring(dot).equalsIgnoreCase(".abcp")) {
			fileName += ".abcp";
			file = new File(file.getParent(), fileName);
		}
		
		try {
			XmlUtil.saveDocument(AbcPlaylistXmlCoder.savePlaylistToXml(tableModel.getTableData()), file);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		playlistPrefs.put("playlistDirectory", savePlaylistChooser.getCurrentDirectory().getAbsolutePath());
	}
	
	public void loadPlaylist() {
		if (openPlaylistChooser == null) {
			openPlaylistChooser = new JFileChooser();
			openPlaylistChooser.setMultiSelectionEnabled(false);
			openPlaylistChooser.setFileFilter(new ExtensionFileFilter("ABC Playlist (.abcp)", "abcp"));
		}
		
		String folder = playlistPrefs.get("playlistDirectory", Util.getLotroMusicPath(false).getAbsolutePath());
		openPlaylistChooser.setCurrentDirectory(new File(folder));
		
		int result = openPlaylistChooser.showOpenDialog(this);
		File file = null;
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		
		file = openPlaylistChooser.getSelectedFile();
		
		List<List<File>> songs = null;
		try {
			songs = AbcPlaylistXmlCoder.loadPlaylist(file);
		} catch (ParseException e) {
			e.printStackTrace();
			return;
		}
		
		if (songs == null) {
			return;
		}
		
		List<AbcInfo> data = new ArrayList<AbcInfo>();
		
		for (List<File> files : songs) {
			List<FileAndData> fad = new ArrayList<FileAndData>();
			for (File f : files) {
				try {
					fad.add(new FileAndData(f, AbcToMidi.readLines(f)));
					data.add(AbcToMidi.parseAbcMetadata(fad));
				} catch (Exception e) { // TODO: Error msg
					e.printStackTrace();
				}
			}
		}
		
		tableModel.clearRows();
		
		for (AbcInfo inf : data) {
			tableModel.addRow(inf);
		}
		
		playlistPrefs.put("playlistDirectory", openPlaylistChooser.getCurrentDirectory().getAbsolutePath());
	}
	
	public void advanceToNextSongIfNeeded() {
		if (!autoplayCheckBox.isSelected()) {
			return;
		}
		
		int newIdx = (tableModel.getIdxForAbcInfo(nowPlayingInfo) + 1) % tableModel.getRowCount();
		
		if (newIdx > 0) {
			AbcInfo info = tableModel.getAbcInfoAt(newIdx);
			nowPlayingInfo = info;
			if (parentListener != null) {
	 			parentListener.onEvent(new PlaylistEvent(info, PlaylistEvent.PlaylistEventType.PLAY_FROM_ABCINFO));
 			}
		}
		else {
			nowPlayingInfo = null;
		}
		
		playlistTable.repaint();
	}
	
	private void updatePlaylistLabel() {
		long totalTimeMicroSec = 0;
		int numSongs = tableModel.getRowCount();
		for (AbcInfo inf : tableModel.getTableData()) {
			String dur = inf.getSongDurationStr();
			if (dur == null || dur.isEmpty() || !dur.contains(":")) {
				continue;
			}
			String[] split = dur.split(":");
			int m = Integer.parseInt(split[0]);
			int s = Integer.parseInt(split[1]);
			totalTimeMicroSec += 60000000 * m;
			totalTimeMicroSec += 1000000 * s;
		}
		
		String labelStr = "ABC Playlist";
		
		if (totalTimeMicroSec != 0) {
			String songs = " Song" + (numSongs > 1? "s" : "");
			labelStr += " (" + numSongs + songs +  ", Total time: " + Util.formatDuration(totalTimeMicroSec) + ")";
		}
		
		abcPlaylistLabel.setText(labelStr);
	}
	
	public void resetPlaylistPosition() {
		nowPlayingInfo = null;
		playlistTable.repaint();
	}
	
	private void addTreePathsToPlaylist(TreePath[] paths) {
		List<AbcInfo> data = new ArrayList<>();
		new SwingWorker<Boolean, Boolean>() {
			
            @Override
            protected Boolean doInBackground(){
            	for (TreePath path : paths) {
            		AbcSongFileNode node = (AbcSongFileNode)path.getLastPathComponent();
            		File file = node.getFile();
            		List<FileAndData> fad = new ArrayList<FileAndData>();
            		try {
                		fad.add(new FileAndData(file, AbcToMidi.readLines(file)));
                		data.add(AbcToMidi.parseAbcMetadata(fad));
            		} catch (Exception e) {
            			continue;
            		}
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
