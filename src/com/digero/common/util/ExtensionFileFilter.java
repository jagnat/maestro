package com.digero.common.util;

import java.io.File;
import java.util.regex.Pattern;

public class ExtensionFileFilter extends javax.swing.filechooser.FileFilter implements java.io.FileFilter {
	private Pattern fileNameRegex;
	private String description;
	private boolean matchDirectories;

	public ExtensionFileFilter(String description, String... fileTypes) {
		this(description, true, fileTypes);
	}

	public ExtensionFileFilter(String description, boolean matchDirectories, String... fileTypes) {
		this.description = description;
		this.matchDirectories = matchDirectories;

		StringBuilder regex = new StringBuilder(".*\\.(" + fileTypes[0]);
		for (int i = 1; i < fileTypes.length; i++)
			regex.append("|").append(fileTypes[i]);
		regex.append(")$");
		fileNameRegex = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
	}

	@Override
	public boolean accept(File f) {
		if (f.isDirectory())
			return matchDirectories;

		return fileNameRegex.matcher(f.getName()).matches();
	}

	@Override
	public String getDescription() {
		return description;
	}

}
