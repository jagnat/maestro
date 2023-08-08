package com.digero.maestro.view;

import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.digero.maestro.abc.AbcPart;

import info.clearthought.layout.TableLayout;

public class DelayDialog {
	
	protected static Point lastLocation = new Point(0,0);

	public static void show(ProjectFrame jf, AbcPart abcPart) {
		@SuppressWarnings("serial")
		class DelayDialogWindow extends JDialog {
			
			private final double[] LAYOUT_COLS = new double[] { 0.1, 0.4, 0.4, 0.1 };
			private double[] LAYOUT_ROWS = new double[] { 0.30, 0.20, 0.20, 0.15, 0.15};
			
		    public DelayDialogWindow(final ProjectFrame jf, final AbcPart abcPart) {
		        super(jf, "Delay Part Editor", true);
		        
		        DelayDialogWindow.this.addWindowListener(new WindowAdapter() {

		            @Override
		            public void windowClosing(WindowEvent we) {
		            	DelayDialog.lastLocation = DelayDialogWindow.this.getLocation();
		            	jf.updateDelayButton();
		            }
		        });
		        
		        this.setSize(250,170);
		        JPanel panel=new JPanel();
		        
		        panel.setLayout(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));
		        
		        final JTextField delayField = new JTextField(String.format("%.3f",abcPart.delay*0.001f));
		        delayField.setHorizontalAlignment(SwingConstants.CENTER);
		        
		        JButton okButton = new JButton("APPLY");
		        okButton.addActionListener(e -> {
                    try {
                        float delay = Float.parseFloat(delayField.getText().replace(',', '.'));
                        if (delay >= 0.000f && delay <= 1.00f) {
                            abcPart.delay = (int)(delay*1000);
                            abcPart.delayEdited();
                        }
                    } catch (NumberFormatException nfe) {

                    }
                    delayField.setText(String.format("%.3f",abcPart.delay*0.001f));
                });
		        panel.add(new JLabel("<html><b> Delay on " + abcPart.getTitle() + " </html>"), "0, 0, 3, 0, C, C");
		        panel.add(delayField, "1, 1, f, f");
		        panel.add(new JLabel("Seconds"), "2, 1, C, C");
		        panel.add(okButton, "1, 2, f, f");
		        panel.add(new JLabel("Put a delay from 0s to 1.00s on a part."), "0, 3, 3, 3, C, C");
		        panel.add(new JLabel("Have no effect if tempo lower than 50."), "0, 4, 3, 4, C, C");
		        delayField.setToolTipText("Seconds of delay");
		        
		        this.getContentPane().add(panel);
		        this.setLocation(DelayDialog.lastLocation);
		        this.setVisible(true);
		    }
		}

		new DelayDialogWindow(jf, abcPart);
	}
	
}