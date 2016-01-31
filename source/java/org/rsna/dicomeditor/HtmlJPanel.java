/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomeditor;

import java.awt.BorderLayout;
import java.io.File;
import java.net.URL;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.text.html.HTMLDocument;
import org.rsna.util.FileUtil;

/**
 * An extension of JPanel containing a Scrollable JEditorPane set to display
 * HTML in a JScrollPane, with the rendered width tracking the width of the
 * JPanel. This provides the type of interface normally seen in a browser.
 */
public class HtmlJPanel extends JPanel {

	/** The text editor - provided for direct access to the JEditorPane methods. */
	public ScrollableJEditorPane editor;
	/** The scroll pane - provided for direct access to the JScrollPane methods. */
	public JScrollPane scrollPane;

	/**
	 * Class constructor creating a Scrollable HTML panel with no text.
	 */
	public HtmlJPanel() {
		this("");
	}

	/**
	 * Class constructor creating a Scrollable HTML panel with text read from a file.
	 * @param file the file containing the initial text string.
	 */
	public HtmlJPanel(File file) {
		this(FileUtil.getText(file));
	}

	/**
	 * Class constructor creating a Scrollable HTML panel with an initial text string.
	 * @param text the initial text string.
	 */
	public HtmlJPanel(String text) {
		super();
		editor = new ScrollableJEditorPane("text/html",text);
		editor.setEditable(false);
		this.setLayout(new BorderLayout());
		scrollPane = new JScrollPane();
		scrollPane.setViewportView(editor);
		scrollPane.getVerticalScrollBar().setUnitIncrement(25);
		scrollPane.getHorizontalScrollBar().setUnitIncrement(15);
		this.add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Replace the editor's current text with new text.
	 * @param text the replacement text string.
	 */
	public void setText(String text) {
		editor.setText(text);
	}

	/**
	 * Return the editor's current text.
	 */
	public String getText() {
		return editor.getText();
	}

	/**
	 * Scroll to the top of the text.
	 */
	public void scrollToTop() {
		JScrollBar sb = scrollPane.getVerticalScrollBar();
		sb.setValue(sb.getMinimum());
	}

	/**
	 * Scroll to the bottom of the text.
	 */
	public void scrollToBottom() {
		JScrollBar sb = scrollPane.getVerticalScrollBar();
		sb.setValue(sb.getMaximum());
	}

	/**
	 * An extension of JEditorPane that implements the Scrollable interface
	 * and forces the width to track the width of the Viewport.
	 */
	public class ScrollableJEditorPane extends JEditorPane implements Scrollable {
		/**
		 * Class constructor creating an editor for a specified content type
		 * (e.g., "text/html") and with an initial text string.
		 * @param type the content type.
		 * @param text the initial text string.
		 */
		public ScrollableJEditorPane(String type, String text) {
			super(type,text);
		}
		/**
		 * Inplement the Scrollable interface for tracking the Viewport width.
		 * @return true if tracking Viewport width; false otherwise.
		 */
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}
	}

}

