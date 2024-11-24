package com.digero.common.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

// Implements a file tree for JTree, but the nodes directly
// under the dummy root node are a list of directories.
// Used for abcplayer playlist

public class AbcFileTreeModel implements TreeModel {

	private ArrayList<TreeModelListener> listeners = new ArrayList<TreeModelListener>();
	private AbcSongFileNode rootNode;
	private static ExtensionFileFilter abcFilter = new ExtensionFileFilter("ABC Files", "abc", "txt"); 
	
	public AbcFileTreeModel(List<File> directories) {
		this.rootNode = new AbcSongFileNode(new File("a_d7mmy_file-name_thatwillnever-9eused"));
		setDirectories(directories);
	}
	
	public void refresh() {
		for (AbcSongFileNode node : rootNode.children) {
			node.refresh();
		}
		
		for (TreeModelListener l : listeners) {
			l.treeStructureChanged(new TreeModelEvent(this, new TreePath(rootNode)));
		}
	}
	
	public void setDirectories(List<File> directories) {
		rootNode.children.clear();
		for (File file : directories) {
			rootNode.children.add(new AbcSongFileNode(file));
		}
	}
	
	@Override
	public void addTreeModelListener(TreeModelListener arg0) {
		listeners.add(arg0);
	}

	@Override
	public Object getChild(Object parentObj, int index) {
		return ((AbcSongFileNode) parentObj).getChildAt(index);
	}

	@Override
	public int getChildCount(Object parentObj) {
		return ((AbcSongFileNode) parentObj).children.size();
	}

	@Override
	public int getIndexOfChild(Object parentObj, Object childObj) {
		AbcSongFileNode parent = ((AbcSongFileNode)parentObj);
		AbcSongFileNode child = ((AbcSongFileNode)childObj);
		
		return parent.getIndexOf(child);
	}

	@Override
	public Object getRoot() {
		return rootNode;
	}

	@Override
	public boolean isLeaf(Object file) {
		AbcSongFileNode f = (AbcSongFileNode)file;
		return f != rootNode && f.getFile().isFile();
	}

	@Override
	public void removeTreeModelListener(TreeModelListener arg0) {
		listeners.remove(arg0);
	}

	@Override
	public void valueForPathChanged(TreePath arg0, Object arg1) {
		
	}
	
	public class AbcSongFileNode {
		private final File theFile;
		private ArrayList<AbcSongFileNode> children;
		
		 public void refresh() {
			children = new ArrayList<AbcSongFileNode>();
			if (!theFile.isDirectory()) {
				return;
			}
			
			for (File file : theFile.listFiles(abcFilter)) {
				AbcSongFileNode node = new AbcSongFileNode(file);
				node.refresh();
				children.add(node);
			}
		}
		
		public AbcSongFileNode(final File theFile) {
			this.theFile = theFile;
			this.children = new ArrayList<AbcSongFileNode>();
		}
		
		public boolean isLeaf() {
			return theFile.isFile() || children.isEmpty();
		}
		
		public AbcSongFileNode getChildAt(int i) {
			if (i < 0 || children == null || i >= children.size()) {
				return null;
			}
			return children.get(i);
		}
		
		public int getIndexOf(AbcSongFileNode node) {
			return children.indexOf(node);
		}

	    public File getFile() {
	        return theFile;
	    }

	    @Override public String toString() {
	        return theFile.getName();
	    }
	    
	    @Override public boolean equals(Object o) {
	        return o instanceof AbcSongFileNode && ((AbcSongFileNode)o).theFile.equals(theFile);
	    }
	}

}
