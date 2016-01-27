/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomeditor;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.*;
import javax.swing.event.*;
import org.apache.log4j.*;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ui.ApplicationProperties;
import org.rsna.ui.FileEvent;
import org.rsna.ui.FileListener;
import org.rsna.ui.PropertyEvent;
import org.rsna.ui.PropertyListener;
import org.rsna.util.FileUtil;

/**
 * A JPanel that provides a DICOM editor.
 */
public class Editor extends JPanel implements FileListener {

	static final Logger logger = Logger.getLogger(Editor.class);

	DicomObject		dicomObject = null;
    TextPanel 		textPanel;

	/**
	 * Class constructor; creates a Editor JPanel.
	 */
    public Editor() {
		super();
		this.setLayout(new BorderLayout());
		textPanel = new TextPanel();
		this.add(textPanel, BorderLayout.CENTER);
		this.setBackground(Configuration.getInstance().background);
    }

	/**
	 * The FileListener implementation; tracks the current
	 * selection from any file selectors with which the object is
	 * registered. Note: this class does not register itself with
	 * a file selector; it is up to the parent class to do it.
	 * Note: events received are in the event dispatch thread because
	 * they come from a DirectoryPane and it sends its events that way.
	 * @param event the event containing the current file selection.
	 */
	public void fileEventOccurred(FileEvent event) {
		if (event.isSELECT()) {
			try {
				File file = event.getFile();
				if (file.isFile()) {
					dicomObject = new DicomObject(file);
					textPanel.displayElements(dicomObject);
				}
			}
			catch (Exception unable) {
				dicomObject = null;
				textPanel.clear();
			}
		}
	}

	class TextPanel extends JPanel {
		JEditorPane editor;
		JScrollPane jsp;
		public TextPanel() {
			super();
			Color background = Configuration.getInstance().background;
			setBackground(background);
			editor = new JEditorPane("text/html", "");
			editor.setEditable(false);
			editor.setBackground(background);
			jsp = new JScrollPane();
			jsp.setViewportView(editor);
			jsp.getVerticalScrollBar().setUnitIncrement(25);
			this.setLayout(new BorderLayout());
			this.add(jsp, BorderLayout.CENTER);
			EditorKit kit = editor.getEditorKit();
			if (kit instanceof HTMLEditorKit) {
				HTMLEditorKit htmlKit = (HTMLEditorKit)kit;
				StyleSheet sheet = htmlKit.getStyleSheet();
				sheet.addRule("body {font-family: Arial; font-size:16;}");
				sheet.addRule("table {text-align: center; font-family: monospace; font-size:14;}");
				sheet.addRule("th {font-family: Arial; font-size:16;}");
				sheet.addRule("td {color:black; background:white; font-weight:bold;}");
				sheet.addRule("h1 {text-align: center;}");
				sheet.addRule("h2 {text-align: center;}");
				sheet.addRule("h3 {text-align: center;}");
				htmlKit.setStyleSheet(sheet);
			}
		}
		public void displayElements(DicomObject dicomObject) {
			editor.setText(dicomObject.getElementTablePage(false));
			editor.setCaretPosition(0);
			scrollToTop();
		}
		public void clear() {
			editor.setText("");
		}
		public void scrollToTop() {
			JScrollBar jsb = jsp.getVerticalScrollBar();
			jsb.setValue(jsb.getMinimum());
		}
	}

}
