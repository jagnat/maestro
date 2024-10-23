package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.TreePath;

import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;
import com.digero.common.util.AbcFileTreeModel;
import com.digero.common.util.AbcFileTreeModel.AbcSongFileNode;
import com.digero.common.util.Util;
import com.digero.common.view.AbcPlaylistTreeCellRenderer;
import com.digero.maestro.abc.AbcSongStub;

public class AbcPlaylistPanel extends JPanel {
	
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
//	private JList<AbcSongStub> playlistList;
	private PlaylistListPanel playlistList;
	private JTable playlistTable;
	private DefaultTableModel playlistTableModel;
	private JScrollPane playlistScrollPane;
	private JPanel playlistBottomControls;
	
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
        	List<FileAndData> data = new ArrayList<>();
			new SwingWorker<Boolean, Boolean>() {
				
	            @Override
	            protected Boolean doInBackground() throws Exception {
	            	System.out.println("Running from EDT: " + SwingUtilities.isEventDispatchThread());
	            	for (TreePath path : paths) {
	            		AbcSongFileNode node = (AbcSongFileNode)path.getLastPathComponent();
	            		File file = node.getFile();
	            		data.add(new FileAndData(file, AbcToMidi.readLines(file)));
	            		System.out.println(file.getPath());
	            	}
	            	return true;
	            }
	            
	            @Override
	            protected void done() {
	            	System.out.println("Done and running from EDT thread: " + SwingUtilities.isEventDispatchThread());
	            	System.out.println("Data sz: " + data.size());
	            	for (FileAndData dataItem : data) {
//	            		playlistList.addItem(new PlaylistItem(dataItem.file.getName()));
	            		playlistTableModel.addRow(new String[] {dataItem.file.getName(), "8", "3:40"});
	            	}
//	            	playlistList.updatePlaylist();
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
		
		playlistTableModel = new DefaultTableModel() {
			@Override
			public boolean isCellEditable(int rI, int cI) {
				return false;
			}
		};
		playlistTableModel.setColumnIdentifiers(new String[] {"Song Name", "Part Count", "Duration"});
//		playlistTableModel.addRow(new String[]{"Song 1", "asdf", "ghjkl"});
//		playlistTableModel.addRow(new String[]{"Song 2", "asdf", "ghjkl"});
//		playlistTableModel.addRow(new String[]{"Song 3", "asdf", "ghjkl"});
		playlistTable = new JTable(playlistTableModel);
		playlistTable.setFocusable(false);
		playlistTable.setModel(playlistTableModel);
		
        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();  
        centered.setHorizontalAlignment(SwingConstants.CENTER);
		
//        playlistTable.getColumnModel().getColumn(1).setCellRenderer(centered);
//        playlistTable.getColumnModel().getColumn(2).setCellRenderer(centered);
		
//		playlistList = new JList<AbcSongStub>();
//		playlistList.setCellRenderer();
//		playlistList = new PlaylistListPanel();
		playlistScrollPane = new JScrollPane(playlistTable,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		playlistScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
//		playlistScrollPane.add(playlistList);
		playlistScrollPane.setViewportView(playlistTable);
		
		playlistBottomControls = new JPanel(new FlowLayout());
		playlistBottomControls.add(new JButton("test"));
		
		JLabel abcPlaylistLabel = new JLabel("ABC Playlist");
		f = abcPlaylistLabel.getFont();
		abcPlaylistLabel.setFont(f.deriveFont(Font.BOLD, f.getSize2D()));
		abcPlaylistLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
//		abcPlaylistLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
		
		rightPanel.add(playlistScrollPane, BorderLayout.CENTER);
		rightPanel.add(playlistBottomControls, BorderLayout.SOUTH);
		rightPanel.add(abcPlaylistLabel, BorderLayout.NORTH);
	}
}

class PlaylistListRenderer extends JPanel implements ListCellRenderer<AbcSongStub> {

	private static final long serialVersionUID = -2206202371930321058L;

	@Override
	public Component getListCellRendererComponent(JList<? extends AbcSongStub> list, AbcSongStub value, int index,
			boolean isSelected, boolean cellHasFocus) {
		
		
		return this;
	}
}