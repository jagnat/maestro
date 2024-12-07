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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreePath;

import com.digero.abcplayer.AbcPlaylistXmlCoder;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;
import com.digero.common.icons.IconLoader;
import com.digero.common.util.AbcFileTreeModel;
import com.digero.common.util.AbcFileTreeModel.AbcSongFileNode;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.Listener;
import com.digero.common.util.ParseException;
import com.digero.common.util.Util;
import com.digero.common.view.AbcPlaylistTreeCellRenderer;
import com.digero.maestro.util.XmlUtil;

import net.miginfocom.swing.MigLayout;

public class AbcPlaylistPanel extends JPanel {
	
	public static class PlaylistEvent extends EventObject {

		private static final long serialVersionUID = 4331827283417203704L;
		private boolean showSongView = false;
		
		public enum PlaylistEventType {
			PLAY_FROM_ABCINFO, PLAY_FROM_FILE
		}
		
		private final PlaylistEventType type;

		public PlaylistEvent(Object source, PlaylistEventType t) {
			super(source);
			type = t;
		}
		
		public PlaylistEvent setShowSongView(boolean showSongView) {
			this.showSongView = showSongView;
			return this;
		}
		
		public boolean getShowSongView() {
			return showSongView;
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
	private JButton addToPlaylistButton;
	
	// Right
	private JTable playlistTable;
	private AbcInfoTableModel tableModel;
	private JScrollPane playlistScrollPane;
	private JPopupMenu playlistContentPopupMenu;
	private JPopupMenu playlistHeaderPopupMenu;
	private JCheckBox autoplayCheckBox;
	private JLabel abcPlaylistLabel;
	
	// Bottom
	private JPanel bottomControls;
	private JButton nextSongButton;
	private JButton prevSongButton;
	private JTextField delayField;
	
	private JFileChooser openPlaylistChooser = null;
	private JFileChooser savePlaylistChooser = null;
	
	private AbcInfo nowPlayingInfo = null;
	private boolean playlistDirtyFlag = false;
	private File playlistFile = null;
	
	private Listener<PlaylistEvent> parentListener;
	private List<File> topLevelDirs = new ArrayList<File>();
	
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
		
		{
			String directoryStr = prefs.get("directories", Util.getLotroMusicPath(false).getAbsolutePath());
			String[] dirs = directoryStr.split(File.pathSeparator);
			for (String dirStr : dirs) {
				topLevelDirs.add(new File(dirStr));
			}
		}
		
		abcFileTreeModel = new AbcFileTreeModel(topLevelDirs);
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
			AbcSongFileNode f = (AbcSongFileNode)(abcFileTree.getPathForRow(menuRowIdx).getLastPathComponent());
			 if (parentListener != null) {
				 parentListener.onEvent(new PlaylistEvent(f.getFile(), PlaylistEvent.PlaylistEventType.PLAY_FROM_FILE).setShowSongView(true));
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
		
		JButton dirListButton = new JButton("Directories...");
		dirListButton.setToolTipText("Configure which directories show up in the ABC Browser.");
		dirListButton.addActionListener(e -> {
			JFrame f = (JFrame)SwingUtilities.getWindowAncestor(this);
			PlaylistDirectoryDialog d = new PlaylistDirectoryDialog(f, topLevelDirs);
			if (d.isSuccess()) {
				List<String> dirs = d.getDirectories();
				String newPrefString = String.join(File.pathSeparator, dirs);
				prefs.put("directories", newPrefString);
				topLevelDirs = dirs.stream().map(File::new).collect(Collectors.toList());
				abcFileTreeModel.setDirectories(topLevelDirs);
				abcFileTreeModel.refresh();
			}
		});

		
		JButton refreshTreeButton = new JButton("Refresh");
		refreshTreeButton.setToolTipText("Refresh the ABC Browser to update it with new or deleted ABC files.");
		refreshTreeButton.addActionListener(e -> {
			abcFileTreeModel.refresh();
		});
		
		addToPlaylistButton = new JButton("Add Selected");
		addToPlaylistButton.setToolTipText("<html>Add the selected songs in the ABC Browser to the playlist.<br> Control-click or shift-click to select multiple songs.</html>");
		addToPlaylistButton.setEnabled(false);
		addToPlaylistButton.addActionListener(e -> {
			addTreePathsToPlaylist(abcFileTree.getSelectionPaths());
		});
		
		JLabel abcBrowserLabel = new JLabel("ABC Browser");
		abcBrowserLabel.setToolTipText("<html>Browser for your ABC files.<br>Double-click a song to play it, or drag selected songs to the playlist panel.</html>");
		Font f = abcBrowserLabel.getFont();
		abcBrowserLabel.setFont(f.deriveFont(Font.BOLD, f.getSize2D()));
		abcBrowserLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		leftPanel.add(fileTreeScrollPane, BorderLayout.CENTER);
		leftPanel.add(abcBrowserLabel, BorderLayout.NORTH);
		
		JPanel topPanel = new JPanel(new FlowLayout());
		abcPlaylistLabel = new JLabel("Untitled Playlist");
		f = abcPlaylistLabel.getFont();
		abcPlaylistLabel.setFont(f.deriveFont(Font.BOLD, f.getSize2D()));
		abcPlaylistLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		topPanel.add(abcPlaylistLabel);
		
		tableModel = new AbcInfoTableModel();
		tableModel.addTableModelListener(e -> {
			playlistDirtyFlag = true;
			if (playlistFile == null && tableModel.getRowCount() == 0) {
				playlistDirtyFlag = false;
			}
			updatePlaylistLabel();
		});
		
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
					f = f.deriveFont(Font.ITALIC | Font.BOLD);
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
					for (File file : inf.getSourceFiles()) {
						txt += file.getName();
						txt += "\n";
					}
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
					if (playlistTable.getSelectedRow() == -1) {
						return;
					}
					AbcInfo info = tableModel.getAbcInfoAt(playlistTable.getSelectedRow());
					setNowPlayingInfo(info);
					if (parentListener != null) {
						 parentListener.onEvent(new PlaylistEvent(info, PlaylistEvent.PlaylistEventType.PLAY_FROM_ABCINFO));
					}
				} else if (e.getButton() == MouseEvent.BUTTON2) {
					
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
		
		playlistContentPopupMenu = new JPopupMenu();
		JMenuItem playItem = new JMenuItem("Play");
		playItem.addActionListener(e -> {
			AbcInfo info = tableModel.getAbcInfoAt(playlistTable.getSelectedRow());
			setNowPlayingInfo(info);
			if (parentListener != null) {
				parentListener.onEvent(new PlaylistEvent(info, PlaylistEvent.PlaylistEventType.PLAY_FROM_ABCINFO));
			}
		});
		playlistContentPopupMenu.add(playItem);
		JMenuItem removeItem = new JMenuItem("Remove Selected");
		removeItem.addActionListener(e -> {
			while (playlistTable.getSelectedRow() != -1) {
				tableModel.removeRow(playlistTable.getSelectedRow());
			}
		});
		playlistContentPopupMenu.add(removeItem);
		playlistContentPopupMenu.addPopupMenuListener(new PopupMenuListener() {

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				boolean enabled = playlistTable.getSelectedRow() != -1;
				if (enabled) {
					AbcInfo info = tableModel.getAbcInfoAt(playlistTable.getSelectedRow());
					playItem.setText("Play '" + info.getTitle() + "'");
				} else {
					playItem.setText("Play");
				}
				playItem.setEnabled(enabled);
				removeItem.setEnabled(enabled);
			}
			
		});
		playlistTable.setComponentPopupMenu(playlistContentPopupMenu);
		
		playlistHeaderPopupMenu = new JPopupMenu();
		playlistTable.getTableHeader().setComponentPopupMenu(playlistHeaderPopupMenu);
		playlistTable.getTableHeader().setReorderingAllowed(false);
		
        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();  
        centered.setHorizontalAlignment(SwingConstants.CENTER);
		
		playlistScrollPane = new JScrollPane(playlistTable,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		playlistScrollPane.setViewportView(playlistTable);
		
		autoplayCheckBox = new JCheckBox("Autoplay");
		autoplayCheckBox.setToolTipText("When checked, playlist playback will automatically advance to the next song.");
		autoplayCheckBox.setFocusable(false);
		autoplayCheckBox.setSelected(playlistPrefs.getBoolean("autoplay", true));
		autoplayCheckBox.addActionListener(e -> {
			playlistPrefs.putBoolean("autoplay", autoplayCheckBox.isSelected());
		});
		// TODO: Unused
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
		// TODO: Unused
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
			savePlaylistAs();
		});
		JButton loadPlaylistButton = new JButton("Load Playlist");
		loadPlaylistButton.setFocusable(false);
		loadPlaylistButton.addActionListener(e -> {
			if (promptSavePlaylist()) {
				loadPlaylist();	
			}
		});
		JButton newPlaylistButton = new JButton("Clear Playlist");
		newPlaylistButton.setFocusable(false);
		newPlaylistButton.addActionListener(e -> {
			if (promptSavePlaylist()) {
				playlistFile = null;
				tableModel.clearRows();
			}
		});
		JButton playPlaylistButton = new JButton("Play");
		playPlaylistButton.setToolTipText("Play playlist starting from the first song.");
		playPlaylistButton.setFocusable(false);
		playPlaylistButton.addActionListener(e -> {
			if (tableModel.getRowCount() == 0) {
				return;
			}
			AbcInfo info = tableModel.getAbcInfoAt(0);
			setNowPlayingInfo(info);
			if (parentListener != null) {
				parentListener.onEvent(new PlaylistEvent(info, PlaylistEvent.PlaylistEventType.PLAY_FROM_ABCINFO));
			}
		});
		nextSongButton = new JButton("Next Song");
		nextSongButton.setEnabled(false);
		nextSongButton.addActionListener(e -> {
			int newIdx = tableModel.getIdxForAbcInfo(nowPlayingInfo) + 1;
			if (newIdx >= tableModel.getRowCount()) {
				return;
			}

			AbcInfo info = tableModel.getAbcInfoAt(newIdx);
			setNowPlayingInfo(info);
			if (parentListener != null) {
	 			parentListener.onEvent(new PlaylistEvent(info, PlaylistEvent.PlaylistEventType.PLAY_FROM_ABCINFO));
 			}
		});
		prevSongButton = new JButton("Prev Song");
		prevSongButton.setEnabled(false);
		prevSongButton.addActionListener(e -> {
			int newIdx = tableModel.getIdxForAbcInfo(nowPlayingInfo) - 1;
			if (newIdx < 0) {
				return;
			}

			AbcInfo info = tableModel.getAbcInfoAt(newIdx);
			setNowPlayingInfo(info);
			if (parentListener != null) {
	 			parentListener.onEvent(new PlaylistEvent(info, PlaylistEvent.PlaylistEventType.PLAY_FROM_ABCINFO));
 			}
		});
		String delayToolTipText = "<html>Configure song switch delay.<br>"+
				"Used to simulate the total setlist time, including the time it takes to switch parts between each song.<br>"+
				"Set it to the average number of seconds it takes your band to switch songs, and the total time of the set<br>"+
				"including song switches will be calculated and displayed next to the playlist title.</html>";
		JLabel delayLabel = new JLabel("Song switch delay (seconds):");
		delayLabel.setToolTipText(delayToolTipText);
		delayField = new JTextField(playlistPrefs.get("delayInSecs", ""));
		delayField.setToolTipText(delayToolTipText);
		delayField.addKeyListener(new KeyAdapter() {
			public void keyTyped(KeyEvent e) {
				char c = e.getKeyChar();
				if ((c < '0' || c > '9') && c != KeyEvent.VK_BACK_SPACE && c != KeyEvent.VK_DELETE) {
					getToolkit().beep();
					e.consume();
				}
			}
		});
		delayField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) { changed(e); }
			@Override
			public void insertUpdate(DocumentEvent e) { changed(e); }
			@Override
			public void changedUpdate(DocumentEvent e) { changed(e); }
			
			public void changed(DocumentEvent e) {
				updatePlaylistLabel();
				playlistPrefs.put("delayInSecs", delayField.getText());
			}
		});
		delayField.setColumns(4);
		
		bottomControls = new JPanel(new MigLayout("fillx"));
		bottomControls.add(dirListButton, "sg dir");
		bottomControls.add(addToPlaylistButton);
		
		bottomControls.add(new JPanel(), "pushx 200");
		
		bottomControls.add(autoplayCheckBox);
		bottomControls.add(prevSongButton);
		bottomControls.add(playPlaylistButton);
		bottomControls.add(nextSongButton, "sg play");
		bottomControls.add(savePlaylistButton, "wrap");
		
		// New row
		bottomControls.add(refreshTreeButton, "sg dir");
		bottomControls.add(delayLabel, "skip 2, span 2, align right");
		bottomControls.add(delayField, "align center");
		bottomControls.add(newPlaylistButton, "sg play");
		bottomControls.add(loadPlaylistButton);
		
		add(bottomControls, BorderLayout.SOUTH);
		
		playlistTable.getSelectionModel().addListSelectionListener(e -> {
			moveUpButton.setEnabled(playlistTable.getSelectedRowCount() == 1);
			moveDownButton.setEnabled(playlistTable.getSelectedRowCount() == 1);
		});
		
		rightPanel.add(playlistScrollPane, BorderLayout.CENTER);
		rightPanel.add(abcPlaylistLabel, BorderLayout.NORTH);
	}
	
	private boolean savePlaylistAs() {
		if (savePlaylistChooser == null) {
			savePlaylistChooser = new JFileChooser();
			savePlaylistChooser.setDialogTitle("Save ABC Playlist");
			savePlaylistChooser.setFileFilter(new ExtensionFileFilter("ABC Playlist (.abcp)", "abcp"));
		}
		
		if (playlistFile != null) {
			savePlaylistChooser.setSelectedFile(playlistFile);
		} else {
			String folder = playlistPrefs.get("playlistDirectory", Util.getLotroMusicPath(false).getAbsolutePath());
			savePlaylistChooser.setCurrentDirectory(new File(folder));
		}
		
		File file = null;
		
		while (true) {
		
			int result = savePlaylistChooser.showSaveDialog(this);
			
			if (result != JFileChooser.APPROVE_OPTION) {
				return false;
			}
		
			file = savePlaylistChooser.getSelectedFile();
			String fileName = file.getName();
			int dot = fileName.lastIndexOf('.');
			if (dot <= 0 || !fileName.substring(dot).equalsIgnoreCase(".abcp")) {
				fileName += ".abcp";
				file = new File(file.getParent(), fileName);
			}
			
			if (playlistFile != null && !file.equals(playlistFile)) {
				int res = JOptionPane.showConfirmDialog(this,
						"File \"" + fileName + "\" already exists.\n" + "Do you want to replace it?",
						"Confirm Replace File", JOptionPane.YES_NO_CANCEL_OPTION);
				if (res == JOptionPane.CANCEL_OPTION)
					return false;
				else if (res == JOptionPane.NO_OPTION)
					continue;
			}
			
			break;
		}
		
		try {
			XmlUtil.saveDocument(AbcPlaylistXmlCoder.savePlaylistToXml(tableModel.getTableData()), file);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		playlistFile = file;
		playlistDirtyFlag = false;
		playlistPrefs.put("playlistDirectory", savePlaylistChooser.getCurrentDirectory().getAbsolutePath());
		updatePlaylistLabel();
		
		return true;
	}
	
	public void loadPlaylist() {
		if (openPlaylistChooser == null) {
			openPlaylistChooser = new JFileChooser();
			openPlaylistChooser.setDialogTitle("Open ABC Playlist");
			openPlaylistChooser.setMultiSelectionEnabled(false);
			openPlaylistChooser.setFileFilter(new ExtensionFileFilter("ABC Playlist (.abcp)", "abcp"));
		}
		
		boolean markDirty = false;
		
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
			JOptionPane.showMessageDialog(this, e.getMessage(), "Failed to load playlist", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		if (songs == null) {
			return;
		}
		
		List<AbcInfo> data = new ArrayList<AbcInfo>();
		List<File> nonExistentFiles = new ArrayList<File>();
		
		for (List<File> files : songs) {
			List<FileAndData> fad = new ArrayList<FileAndData>();
			for (File f : files) {
				if (!f.exists()) {
					nonExistentFiles.add(f);
					continue;
				}
				try {
					fad.add(new FileAndData(f, AbcToMidi.readLines(f)));
					data.add(AbcToMidi.parseAbcMetadata(fad));
				} catch (Exception e) {
					String err = "Failed to parse abc:\n" + f.getAbsolutePath();
					JOptionPane.showMessageDialog(this, err, "Failed to load song", JOptionPane.ERROR_MESSAGE);
					markDirty = true;
				}
			}
		}
		
		// TODO: Add option to search in folder
		if (!nonExistentFiles.isEmpty()) {
			String err = "Failed to open songs:";
			for (File f : nonExistentFiles) {
				err = err + "\n" + f.getAbsolutePath();
			}
			JOptionPane.showMessageDialog(this, err, "Failed to open song(s)", JOptionPane.ERROR_MESSAGE);
			markDirty = true;
		}
		
		tableModel.clearRows();
		
		for (AbcInfo inf : data) {
			tableModel.addRow(inf);
		}
		
		playlistDirtyFlag = markDirty;
		playlistFile = file;
		playlistPrefs.put("playlistDirectory", openPlaylistChooser.getCurrentDirectory().getAbsolutePath());
		updatePlaylistLabel();
	}
	
	public boolean promptSavePlaylist() {
		if (!isPlaylistModified()) {
			return true;
		}
		
		String message;
		if (playlistFile == null) {
			message = "Do you want to save this untitled playlist?";
		} else {
			message = "Do you want to save changes to " + playlistFile.getName() + "?";
		}
		
		int result = JOptionPane.showConfirmDialog(this, message, "Save Changes", JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE, IconLoader.getImageIcon("abcplayer_32.png"));
		if (result == JOptionPane.CANCEL_OPTION)
			return false;
		if (result == JOptionPane.YES_OPTION) {
			return savePlaylistAs();
		}
		return true;
	}
	
	public void advanceToNextSongIfNeeded() {
		if (!autoplayCheckBox.isSelected()) {
			return;
		}
		
		if (tableModel.getRowCount() == 0) {
			return;
		}
		
		int newIdx = (tableModel.getIdxForAbcInfo(nowPlayingInfo) + 1) % tableModel.getRowCount();
		
		if (newIdx > 0) {
			AbcInfo info = tableModel.getAbcInfoAt(newIdx);
			setNowPlayingInfo(info);
			if (parentListener != null) {
	 			parentListener.onEvent(new PlaylistEvent(info, PlaylistEvent.PlaylistEventType.PLAY_FROM_ABCINFO));
 			}
		}
		else {
			setNowPlayingInfo(null);
		}
	}
	
	public boolean isPlaylistModified() {
		return playlistDirtyFlag;
	}
	
	private void setNowPlayingInfo(AbcInfo nowPlaying) {
		nowPlayingInfo = nowPlaying;
		if (nowPlayingInfo == null) {
			nextSongButton.setEnabled(false);
			prevSongButton.setEnabled(false);
		} else {
			int idx = tableModel.getIdxForAbcInfo(nowPlayingInfo);
			prevSongButton.setEnabled(idx > 0);
			nextSongButton.setEnabled(idx < tableModel.getRowCount() - 1);
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
		
		String labelStr = "";
		
		if (playlistFile != null) {
			labelStr = playlistFile.getName();
			if (labelStr.contains(".")) {
				labelStr = labelStr.substring(0, labelStr.lastIndexOf('.'));
			}
			abcPlaylistLabel.setToolTipText(playlistFile.getAbsolutePath());
		} else {
			labelStr = "Untitled Playlist";
			abcPlaylistLabel.setToolTipText("");
		}
		
		if (playlistDirtyFlag) {
			labelStr += "*";
		}
		
		long totalTimeWithDelayMicroSec = totalTimeMicroSec;
		
		if (!delayField.getText().isEmpty() && numSongs > 0) {
			try {
				int delaySec = Integer.parseInt(delayField.getText());
				// Add (songs - 1) * part switch delay to total time
				// e.g. with two songs in playlist, there is one part switch
				totalTimeWithDelayMicroSec = totalTimeWithDelayMicroSec + (numSongs - 1) * (delaySec * 1000000l);
			} catch (NumberFormatException e) {
			}
		}
		
		if (totalTimeMicroSec != 0) {
			String songs = " Song" + (numSongs > 1? "s" : "");
			labelStr += " (" + numSongs + songs +  ", Time: [" + Util.formatDuration(totalTimeMicroSec) + "]";
			if (totalTimeWithDelayMicroSec != totalTimeMicroSec) {
				labelStr += ", With Switches: [" + Util.formatDuration(totalTimeWithDelayMicroSec) + "]";
			}
			labelStr += ")";
		}
		
		abcPlaylistLabel.setText(labelStr);
	}
	
	public void resetPlaylistPosition() {
		setNowPlayingInfo(null);
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
