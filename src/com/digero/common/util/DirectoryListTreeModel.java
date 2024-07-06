package com.digero.common.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

// Implements a file tree for JTree, but the nodes directly
// under the dummy root node are a list of directories.
// Used for abcplayer playlist

public class DirectoryListTreeModel implements TreeModel {

	private File rootFile;
	private List<File> topLevelDirectories;
	private final ExtensionFileFilter abcFilter = new ExtensionFileFilter("ABC Files", "abc", "txt"); 
	
	public DirectoryListTreeModel(List<File> directories) {
		this.rootFile = new File("a_dummy_filename_that_never_will_be_used");
		this.topLevelDirectories = directories;
	}
	
	@Override
	public void addTreeModelListener(TreeModelListener arg0) {
		
	}

	@Override
	public Object getChild(Object parentDummy, int index) {
		File parent = ((AbcSongFileNode)parentDummy).getFile();
		if (parent == rootFile) {
			return new AbcSongFileNode(topLevelDirectories.get(index));
		}
		return new AbcSongFileNode(((File)parent).listFiles(abcFilter)[index]);
	}

	@Override
	public int getChildCount(Object parentDummy) {
		File parent = ((AbcSongFileNode)parentDummy).getFile();
		if (parent == rootFile) {
			return topLevelDirectories.size();
		}
		if (parent != null && ((File)parent).listFiles(abcFilter) != null) {
			return ((File)parent).listFiles(abcFilter).length;
		}
		return 0;
	}

	@Override
	public int getIndexOfChild(Object dFile1, Object dFile2) {
		File file1 = ((AbcSongFileNode)dFile1).getFile();
		File file2 = ((AbcSongFileNode)dFile2).getFile();
		if (file1 == rootFile) {
			return topLevelDirectories.indexOf(file2);
		}
		List<File> fileList = Arrays.asList(((File)file1).listFiles(abcFilter));
		return fileList.indexOf(file2);
	}

	@Override
	public Object getRoot() {
		return new AbcSongFileNode(rootFile);
	}

	@Override
	public boolean isLeaf(Object file) {
		AbcSongFileNode f = (AbcSongFileNode)file;
		if (f.getFile() == rootFile) {
			return false;
		}
		return f.getFile().isFile();
	}

	@Override
	public void removeTreeModelListener(TreeModelListener arg0) {
		
	}

	@Override
	public void valueForPathChanged(TreePath arg0, Object arg1) {
		
	}
	
	public class AbcSongFileNode {
		private final File theFile;
		private ArrayList<AbcSongFileNode> children;
		
		public AbcSongFileNode(final File theFile) {
			this.theFile = theFile;
			this.children = new ArrayList<AbcSongFileNode>();
		}
		
		public boolean isLeaf() {
			return theFile.isFile() || children.isEmpty();
		}

	    public File getFile() {
	        return theFile;
	    }
	    
	    

	    @Override public String toString() {
	        return theFile.getName();
	    }
	}

}
