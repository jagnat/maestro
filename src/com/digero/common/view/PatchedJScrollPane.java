package com.digero.common.view;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JScrollPane;

@SuppressWarnings("serial")
public class PatchedJScrollPane extends JScrollPane {
	
	@Override
	public Dimension getPreferredSize() {
		Dimension d = super.getPreferredSize();
		if (super.getVerticalScrollBar().isVisible()) {
			d.width = d.width + new JScrollPane().getVerticalScrollBar().getPreferredSize().width;
		}
		
		if (super.getHorizontalScrollBar().isVisible()) {
			d.height = d.height + new JScrollPane().getHorizontalScrollBar().getPreferredSize().height;
		}
		return d;
	}
	
	public PatchedJScrollPane(Component component) {
		super(component);
	}
	
	public PatchedJScrollPane(Component component, int vertPolicy, int horizPolicy) {
		super(component, vertPolicy, horizPolicy);
	}
}
