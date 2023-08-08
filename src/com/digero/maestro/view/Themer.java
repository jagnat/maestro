package com.digero.maestro.view;

import java.awt.Font;
import java.util.prefs.Preferences;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.text.StyleContext;

import com.digero.maestro.MaestroMain;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

public class Themer
{
	protected static final String[] themes =
	{
			"Flat Dark",
			"Flat Light",
	};
	
	protected static final int[] fontSizes =
	{
		10,
		11,
		12,
		13,
		14,
		15,
		16,
		17,
		18
	};
	
	public static void setLookAndFeel() throws ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException
	{
		MiscSettings settings = new MiscSettings(Preferences.userNodeForPackage(MaestroMain.class).node("miscSettings"), true);
		String theme = settings.theme;
		int fontSize = settings.fontSize;
		boolean isDefaultTheme = false;
		
		if (theme.equals(themes[0]))
		{
			UIManager.setLookAndFeel(new FlatMacDarkLaf());
		}
		else if (theme.equals(themes[1]))
		{
			UIManager.setLookAndFeel(new FlatMacLightLaf());
		}
		else
		{
			isDefaultTheme = true;
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		
		if (!isDefaultTheme)
		{
			Font font = UIManager.getFont("defaultFont");
			Font newFont = StyleContext.getDefaultStyleContext().getFont(font.getFamily(), font.getStyle(), fontSize);
			UIManager.put("defaultFont", newFont);
			FlatLaf.updateUI();
		}
	}
	
}
