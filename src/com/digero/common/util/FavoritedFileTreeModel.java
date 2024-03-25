package com.digero.common.util;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

// Implements a file tree for JTree, but all the nodes directly
// under the root node are specified directories.

public class FavoritedFileTreeModel implements TreeModel {

	private File root;
	private List<File> favorites;
	
	public FavoritedFileTreeModel(List<File> favorites) {
		this.root = new File("an_empty_file");
		this.favorites = favorites;
	}
	
	@Override
	public void addTreeModelListener(TreeModelListener arg0) {
		
	}

	@Override
	public Object getChild(Object parent, int index) {
		if (parent == root) {
			return favorites.get(index);
		}
		return ((File)parent).listFiles()[index];
	}

	@Override
	public int getChildCount(Object parent) {
		if (parent == root) {
			return favorites.size();
		}
		if (parent != null && ((File)parent).listFiles() != null) {
			return ((File)parent).listFiles().length;
		}
		return 0;
	}

	@Override
	public int getIndexOfChild(Object file1, Object file2) {
		if (file1 == root) {
			return favorites.indexOf(file2);
		}
		List<File> fileList = Arrays.asList(((File)file1).listFiles());
		return fileList.indexOf(file2);
	}

	@Override
	public Object getRoot() {
		return root;
	}

	@Override
	public boolean isLeaf(Object file) {
		if (file == root) {
			return false;
		}
		return ((File)file).isFile();
	}

	@Override
	public void removeTreeModelListener(TreeModelListener arg0) {
		
	}

	@Override
	public void valueForPathChanged(TreePath arg0, Object arg1) {
		
	}

}
