package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.TreePath;

import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;
import com.digero.common.util.AbcFileTreeModel;
import com.digero.common.util.AbcFileTreeModel.AbcSongFileNode;
import com.digero.common.util.Listener;
import com.digero.common.util.Util;
import com.digero.common.view.AbcPlaylistTreeCellRenderer;
import com.digero.maestro.abc.AbcSongStub;

public class AbcPlaylistPanel extends JPanel {
	
	public static class PlaylistEvent extends EventObject {

		private static final long serialVersionUID = 4331827283417203704L;
		
		public enum PlaylistEventType {
			PLAY,
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
	private AbcFileTreeModel abcFileTreeModel;
	private JScrollPane fileTreeScrollPane;
	private JPanel fileTreeBottomControls;
	private JButton addToPlaylistButton;
	
	// Right
	private JTable playlistTable;
//	private DefaultTableModel playlistTableModel;
	private AbcInfoTableModel tableModel;
	private JScrollPane playlistScrollPane;
	private JPanel playlistBottomControls;
	private JPopupMenu contentPopupMenu;
	private JPopupMenu headerPopupMenu;
	
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
		ToolTipManager.sharedInstance().registerComponent(abcFileTree);
		fileTreeScrollPane = new JScrollPane(abcFileTree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		fileTreeBottomControls = new JPanel(new FlowLayout());
		addToPlaylistButton = new JButton(">>");
		addToPlaylistButton.setEnabled(false);
		fileTreeBottomControls.add(addToPlaylistButton);
		addToPlaylistButton.addActionListener(e -> {
			TreePath[] paths = abcFileTree.getSelectionPaths();
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
//	            		playlistTableModel.addRow(new String[] {info.getTitle(), ""+info.getPartCount(), info.getSongDurationStr(), info.getComposer(), info.getTranscriber()});
	            		tableModel.addRow(info);
	            	}
	            }
			}.execute();
		});
		
		JLabel abcBrowserLabel = new JLabel("ABC Browser");
		Font f = abcBrowserLabel.getFont();
		abcBrowserLabel.setFont(f.deriveFont(Font.BOLD, f.getSize2D()));
		abcBrowserLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		leftPanel.add(fileTreeScrollPane, BorderLayout.CENTER);
		leftPanel.add(fileTreeBottomControls, BorderLayout.SOUTH);
		leftPanel.add(abcBrowserLabel, BorderLayout.NORTH);
		
		tableModel = new AbcInfoTableModel();
		
//		playlistTableModel = new DefaultTableModel() {
//			private static final long serialVersionUID = -3042552892183194823L;
//
//			@Override
//			public boolean isCellEditable(int rI, int cI) {
//				return false;
//			}
//		};
//		playlistTableModel.setColumnIdentifiers(new String[] {"Song Name", "Part Count", "Duration", "Composer", "Transcriber"});
		playlistTable = new JTable(tableModel);
		playlistTable.setFocusable(false);
		
		contentPopupMenu = new JPopupMenu();
		JMenuItem playItem = new JMenuItem("Play");
		playItem.addActionListener(e -> {
			 System.out.println("playing " + playlistTable.getSelectedRow());
			 AbcInfo info = tableModel.getAbcInfoAt(playlistTable.getSelectedRow());
			 if (parentListener != null) {
				 parentListener.onEvent(new PlaylistEvent(info, PlaylistEvent.PlaylistEventType.PLAY));
			 }
		});
		contentPopupMenu.add(playItem);
		playlistTable.setComponentPopupMenu(contentPopupMenu);
		
		playlistTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				Point p = e.getPoint();
				int row = playlistTable.rowAtPoint(p);
				if (!IntStream.of(playlistTable.getSelectedRows()).anyMatch(x -> x == row)) {
					playlistTable.setRowSelectionInterval(row, row);
				}
			}
		});
		
		headerPopupMenu = new JPopupMenu();
		playlistTable.getTableHeader().setComponentPopupMenu(headerPopupMenu);
		playlistTable.getTableHeader().setReorderingAllowed(false);
		
        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();  
        centered.setHorizontalAlignment(SwingConstants.CENTER);
		
		playlistScrollPane = new JScrollPane(playlistTable,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		playlistScrollPane.setViewportView(playlistTable);
		
		playlistBottomControls = new JPanel(new FlowLayout());
		playlistBottomControls.add(new JButton("test"));
		
		JLabel abcPlaylistLabel = new JLabel("ABC Playlist");
		f = abcPlaylistLabel.getFont();
		abcPlaylistLabel.setFont(f.deriveFont(Font.BOLD, f.getSize2D()));
		abcPlaylistLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		rightPanel.add(playlistScrollPane, BorderLayout.CENTER);
		rightPanel.add(playlistBottomControls, BorderLayout.SOUTH);
		rightPanel.add(abcPlaylistLabel, BorderLayout.NORTH);
	}
	
	public void setPlaylistListener(Listener<PlaylistEvent> l) {
		parentListener = l;
	}
	
	public void removePlaylistListener() {
		parentListener = null;
	}
}
