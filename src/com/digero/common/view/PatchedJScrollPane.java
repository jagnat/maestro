package com.digero.common.view;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JScrollPane;

@SuppressWarnings("serial")
public class PatchedJScrollPane extends JScrollPane {
	
	@Override
	public Dimension getPreferredSize() {
		Dimension d = super.getPreferredSize();
		d.width = d.width + new JScrollPane().getVerticalScrollBar().getPreferredSize().width;
		return d;
	}
	
	public PatchedJScrollPane(Component component) {
		super(component);
	}
}
