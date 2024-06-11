package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;

import com.digero.common.util.DirectoryListTreeModel;
import com.digero.common.util.DirectoryListTreeModel.DummyFile;
import com.digero.common.util.Util;
import com.digero.common.view.AbcPlaylistTreeCellRenderer;
import com.digero.maestro.abc.AbcSongStub;

public class AbcPlaylistPanel extends JPanel {
	
	private JSplitPane browserSplitPane;
	private JPanel leftPanel;
	private JPanel rightPanel;
	
	// Left
	private JTree abcFileTree;
	private JScrollPane fileTreeScrollPane;
	private JPanel fileTreeBottomControls;
	private JButton addToPlaylistButton;
	
	// Right
	private JList playlistList;
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
		
		abcFileTree = new JTree();
		abcFileTree.setShowsRootHandles(true);
		abcFileTree.setRootVisible(false);
		abcFileTree.setModel(new DirectoryListTreeModel(tlds));
		abcFileTree.setCellRenderer(new AbcPlaylistTreeCellRenderer());
		abcFileTree.collapseRow(0);
		abcFileTree.addTreeSelectionListener(e -> {
			DummyFile f = (DummyFile)abcFileTree.getLastSelectedPathComponent();
			addToPlaylistButton.setEnabled(!f.getFile().isDirectory());
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
		playlistScrollPane = new JScrollPane();
	}
}
