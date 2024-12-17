package com.digero.common.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

// Implements a file tree for JTree, but the nodes directly
// under the dummy root node are a list of directories.
// Used for abcplayer playlist

public class AbcFileTreeModel implements TreeModel {
	
	public enum SortType {
		NAME_ASC, NAME_DESC, LAST_MODIFIED_ASC, LAST_MODIFIED_DESC, SIZE_ASC, SIZE_DESC;
		
		// Convert LAST_MODIFIED_DESC into Last Modified (desc)
		@Override
		public String toString() {
			String[] parts = super.toString().toLowerCase().split("_");
			for (int i = 0; i < parts.length - 1; i++) {
				parts[i] = parts[i].substring(0, 1).toUpperCase() + parts[i].substring(1);
			}
			parts[parts.length - 1] = "(" + parts[parts.length - 1] + ")";
			return String.join(" ", parts);
		}
	}

	private ArrayList<TreeModelListener> listeners = new ArrayList<TreeModelListener>();
	private AbcSongFileNode rootNode;
	private static ExtensionFileFilter abcFilter = new ExtensionFileFilter("ABC Files and Playlists", "abc", "txt", "abcp"); 
	
	public AbcFileTreeModel(List<File> directories) {
		this.rootNode = new AbcSongFileNode(new File("a_d7mmy_file-name_thatwillnever-9eused"));
		setDirectories(directories);
	}
	
	public void refresh(SortType sort) {
		for (AbcSongFileNode node : rootNode.children) {
			node.refresh(getComparator(sort));
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
	
	private Comparator<File> getComparator(SortType type) {
		switch (type) {
		case NAME_ASC:
			return (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName());
		case NAME_DESC:
			return (f1, f2) -> f2.getName().compareToIgnoreCase(f1.getName());
		case LAST_MODIFIED_ASC:
			return (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified());
		case LAST_MODIFIED_DESC:
			return (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified());
		case SIZE_ASC:
			return (f1, f2) -> Long.compare(f1.length(), f2.length());
		case SIZE_DESC:
			return (f1, f2) -> Long.compare(f2.length(), f1.length());
		default:
			return (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName());
		}
	}
	
	public class AbcSongFileNode {
		private final File theFile;
		private ArrayList<AbcSongFileNode> children;
		
		public void refresh(Comparator<File> sorter) {
			children = new ArrayList<AbcSongFileNode>();
			if (!theFile.isDirectory() || !theFile.exists()) {
				return;
			}
			
			File[] childFiles = theFile.listFiles(abcFilter);
			
			if (childFiles == null) {
				return;
			}
			
			File[] files = Arrays.stream(childFiles).filter(File::isFile).toArray(File[]::new);
			File[] folders = Arrays.stream(childFiles).filter(File::isDirectory).toArray(File[]::new);
			
			Arrays.sort(files, sorter);
			Arrays.sort(folders, sorter);
			
			for (File folder: folders) {
				AbcSongFileNode node = new AbcSongFileNode(folder);
				node.refresh(sorter);
				children.add(node);
			}
			
			for (File file : files) {
				AbcSongFileNode node = new AbcSongFileNode(file);
				node.refresh(sorter);
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
