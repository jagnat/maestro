package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.ToolTipManager;
import javax.swing.tree.TreePath;

import com.digero.common.util.DirectoryListTreeModel;
import com.digero.common.util.DirectoryListTreeModel.AbcSongFileNode;
import com.digero.common.util.Util;
import com.digero.common.view.AbcPlaylistTreeCellRenderer;
import com.digero.maestro.abc.AbcSongStub;

public class AbcPlaylistPanel extends JPanel {
	
	private JSplitPane browserSplitPane;
	private JPanel leftPanel;
	private JPanel rightPanel;
	
	// Left
	private JTree abcFileTree;
	private DirectoryListTreeModel abcFileTreeModel;
	private JScrollPane fileTreeScrollPane;
	private JPanel fileTreeBottomControls;
	private JButton addToPlaylistButton;
	
	// Right
	private JList<AbcSongStub> playlistList;
	private JScrollPane playlistScrollPane;
	private JPanel playlistTopControls;
	
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
		
		abcFileTreeModel = new DirectoryListTreeModel(tlds);
		
		abcFileTree = new JTree();
		abcFileTree.setShowsRootHandles(true);
		abcFileTree.setRootVisible(false);
		abcFileTree.setModel(abcFileTreeModel);
		abcFileTree.setCellRenderer(new AbcPlaylistTreeCellRenderer());
		abcFileTree.collapseRow(0);
		abcFileTree.addTreeSelectionListener(e -> {
			// Only allow adding files if all the selected ones are abc files (no folders)
			boolean noFoldersSelected = true;
			TreePath[] paths = abcFileTree.getSelectionPaths();
			for (TreePath path : paths) {
				AbcSongFileNode f = (AbcSongFileNode)path.getLastPathComponent();
				if (f.getFile().isDirectory()) {
					noFoldersSelected = false;
					break;
				}
			}
			addToPlaylistButton.setEnabled(noFoldersSelected);
		});
		ToolTipManager.sharedInstance().registerComponent(abcFileTree);
		fileTreeScrollPane = new JScrollPane(abcFileTree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		
		fileTreeBottomControls = new JPanel(new FlowLayout());
		addToPlaylistButton = new JButton(">>");
		addToPlaylistButton.setEnabled(false);
		fileTreeBottomControls.add(addToPlaylistButton);
		
		leftPanel.add(fileTreeScrollPane, BorderLayout.CENTER);
		leftPanel.add(fileTreeBottomControls, BorderLayout.SOUTH);
		
		playlistList = new JList<AbcSongStub>();
//		playlistList.setCellRenderer();
		playlistScrollPane = new JScrollPane();
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
