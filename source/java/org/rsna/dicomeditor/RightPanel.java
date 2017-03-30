/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomeditor;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import org.dcm4che.dict.Tags;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMCorrector;
import org.rsna.ctp.stdstages.anonymizer.xml.XMLAnonymizer;
import org.rsna.ui.ApplicationProperties;
import org.rsna.ui.FileEvent;
import org.rsna.ui.FileListener;
import org.rsna.ui.GeneralFileFilter;
import org.rsna.ui.SourcePanel;
import org.w3c.dom.*;
import org.rsna.util.FileUtil;

/**
 * A JPanel that provides a user interface for the active part of
 * the DicomEditor program, including starting the anonymization
 * process and logging the results.
 */
public class RightPanel extends JPanel
						implements FileListener, ActionListener  {

	HeaderPanel headerPanel;
	JPanel centerPanel;
	FooterPanel footerPanel;
	ApplicationProperties properties;
	SourcePanel sourcePanel;
	ResultsScrollPane resultsPane;

	File currentSelection = null;
	String[] currentPath = null;
	boolean subdirectories = false;
	boolean changeNames = false;
	boolean forceIVRLE = false;
	boolean renameToSOPIUID = false;
	String dicomScriptFile = null;
	String lookupTableFile = null;
	String xmlScriptFile = null;
	GeneralFileFilter filter = null;
	Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

	/**
	 * Class constructor; creates an instance of the RightPanel and
	 * initializes the user interface for it.
	 * @param sourcePanel the panel that contains file or directory
	 * selected for anonymization.
	 */
	public RightPanel(SourcePanel sourcePanel) {
		super();
		Configuration config = Configuration.getInstance();
		this.properties = config.getProps();
		this.sourcePanel = sourcePanel;
		this.dicomScriptFile = config.dicomScriptFile;
		this.lookupTableFile = config.lookupTableFile;
		this.xmlScriptFile = config.xmlScriptFile;
		this.background = config.background;
		this.setLayout(new BorderLayout());
		headerPanel = new HeaderPanel();
		this.add(headerPanel,BorderLayout.NORTH);
		resultsPane = new ResultsScrollPane();
		this.add(resultsPane,BorderLayout.CENTER);
		footerPanel = new FooterPanel();
		this.add(footerPanel,BorderLayout.SOUTH);

		sourcePanel.getDirectoryPane().addFileListener(this);
		footerPanel.anonymize.addActionListener(this);
		footerPanel.fixVRs.addActionListener(this);
		footerPanel.clearPreamble.addActionListener(this);
		footerPanel.setPatientIDs.addActionListener(this);
	}

	/**
	 * The FileListener implementation.
	 * This method captures the current file selection when
	 * it changes in the sourcePanel.
	 * @param event the event containing the File currently selected.
	 */
	public void fileEventOccurred(FileEvent event) {
		currentSelection = event.getFile();
		currentPath = currentSelection.getAbsolutePath().split("[\\\\/]");
	}

	/**
	 * The ActionListener for the footer's action buttons.
	 * This method starts the anonymization/VR correction process.
	 * @param event the event
	 */
	public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (currentSelection != null) {
			subdirectories = sourcePanel.getSubdirectories();
			changeNames = footerPanel.changeNameBox.isSelected();
			renameToSOPIUID = footerPanel.renameToSOPIUIDBox.isSelected();
			filter = sourcePanel.getFileFilter();
			resultsPane.clear();
			resultsPane.append("<ol>");
			if (source.equals(footerPanel.anonymize)) anonymize(currentSelection);
			else if (source.equals(footerPanel.fixVRs)) fixVRs(currentSelection);
			else if (source.equals(footerPanel.clearPreamble)) clearPreamble(currentSelection);
			else if (source.equals(footerPanel.setPatientIDs)) setPatientIDs(currentSelection);
			resultsPane.append("</ol><b>Done.</b>");
			resultsPane.showText();
		}
		else Toolkit.getDefaultToolkit().beep();
	}

	// Anonymize the selected file(s).
	private void anonymize(File file) {
		if (file.isFile()) {
			File copy = file;
			if (changeNames) {
				String name = file.getName();
				int k = name.length();
				if (!name.matches("[\\d\\.]+")) {
					k = name.lastIndexOf(".");
					if (k == -1) k = name.length();
					if (name.substring(0,k).endsWith("-no-phi")) return;
				}
				name = name.substring(0,k) + "-no-phi" + name.substring(k);
				copy = new File(file.getParentFile(),name);
			}
			resultsPane.newItem("<li>Anonymizing: "+file);

			//If the filename ends in ".xml", do an XML anonymization;
			//otherwise, do a DICOM anonymization.
			String result = "";
			if (file.getName().toLowerCase().endsWith(".xml")) {
				File xmlScript = new File(xmlScriptFile);
				LookupTable lookupTable = LookupTable.getInstance( new File(lookupTableFile) );
				result = 
					XMLAnonymizer.anonymize(
						file, copy, 
						xmlScript, 
						lookupTable.getProperties()).isOK() ? "" : "failed";
			}
			else {
				DAScript dicomScript = DAScript.getInstance( new File(dicomScriptFile) );
				LookupTable lookupTable = LookupTable.getInstance( new File(lookupTableFile) );
				result =
					DICOMAnonymizer.anonymize(
						file, copy,
						dicomScript.toProperties(), lookupTable.getProperties(), (IntegerTable)null,
						forceIVRLE, renameToSOPIUID).isOK() ? "" : "failed";;
			}
			//Report the results
			if (result.equals("")) {
				resultsPane.appendItem("<br><b>OK</b></li>");
			}
			else {
				resultsPane.appendItem("<br><font color=red><b>Failed</b></font><br></li>");
			}
			return;
		}
		else {
			File[] files = file.listFiles(filter);
			for (File f : files) {
				if (f.isFile() || subdirectories) anonymize(f);
			}
		}
	}

	// Insert directory names in the PatientID elements of the selected file(s).
	private void setPatientIDs(File file) {
		if (!currentSelection.isDirectory()) {
			resultsPane.appendItem(
					"<br><font color=red><b>" +
					currentSelection +
					" is not a directory.</b></font><br></li>");
			return;
		}
		if (file.isFile()) {
			resultsPane.newItem("<li>Modifying: "+file);

			//Only process DicomObjects.
			DicomObject dob = null;
			try {
				dob = new DicomObject(file, true);
				
				//Get the directory name to use for the PatientID
				String[] path = file.getAbsolutePath().split("[\\\\/]");
				if (path.length < currentPath.length + 2) {
					resultsPane.appendItem("<br><font color=red><b>Unable to process files in the base directory.</b></font><br></li>");
				}
				else {
					String name = path[currentPath.length];

					dob.setElementValue(Tags.PatientID, name);

					File temp = File.createTempFile("DCM-", ".dcm", currentSelection);
					dob.saveAs(temp, false);
					dob.close();
					file.delete();
					temp.renameTo(file);
					resultsPane.appendItem("<br><b>OK</b></li>");
				}
			}
			catch (Exception ex) {
				resultsPane.appendItem("<br><font color=red><b>Unable to modify the file</b></font><br></li>");
			}
			if (dob != null) dob.close();
		}
		else {
			File[] files = file.listFiles(filter);
			for (File f : files) {
				setPatientIDs(f);
			}
		}
	}

	// Clear the preamble on the selected file(s) - only if they are DICOM files.
	private void clearPreamble(File file) {
		if (file.isFile()) {
			resultsPane.newItem("<li>Clearing preamble: "+file);
			String result = clearDicomPreamble(file);

			//Report the results
			if (result.equals(""))
				resultsPane.appendItem("<br><b>OK</b></li>");
			else
				resultsPane.appendItem("<br><font color=red><b>Exceptions:</b></font><br>"
					+ result + "</li>");
			return;
		}
		else {
			File[] files = file.listFiles(filter);
			for (File f : files) {
				if (f.isFile() || subdirectories) clearPreamble(f);
			}
		}
	}

	//Clear the DICOM preamble on one file
	private String clearDicomPreamble(File file) {
		try {
			RandomAccessFile raf = new RandomAccessFile(file,"rw");
			raf.seek(128L);
			byte[] type = new byte[4];
			raf.read(type);
			if ((type[0] != 0x44) || (type[1] != 0x49) ||
				(type[2] != 0x43) || (type[3] != 0x4D)) return "Not a DICOM Part 10 file";
			byte[] bytes = new byte[128];
			for (int i=0; i<bytes.length; i++) bytes[i] = 0;
			raf.seek(0L);
			raf.write(bytes);
			raf.close();
			return "";
		}
		catch (Exception ex) {
			return ex.toString();
		}
	}

	// Fix the VRs in the selected file(s).
	// Note: this method always overwrites the file,
	// even if the Change names box is checked.
	private void fixVRs(File file) {
		if (file.isFile()) {
			resultsPane.newItem("<li>Correcting: "+file);
			AnonymizerStatus status = DICOMCorrector.correct(file, file, false, false, false);
			String result = (status.isOK() || status.isSKIP()) ? "" : "Failed;";
			if (result.equals(""))
				resultsPane.appendItem("<br><b>OK</b></li>");
			else
				resultsPane.appendItem("<br><font color=red><b>Failed</b></font><br></li>");

			return;
		}
		else {
			File[] files = file.listFiles(filter);
			for (File f : files) {
				if (f.isFile() || subdirectories) fixVRs(f);
			}
		}
	}

	//Class to display the results of the processing
	class ResultsScrollPane extends JScrollPane {
		JEditorPane text;
		StringBuffer sb;
		int count;
		String item;
		public ResultsScrollPane() {
			super();
			sb = new StringBuffer();
			text = new JEditorPane();
			text.setContentType("text/html");
			text.setEditable(false);
			setViewportView(text);
			count = 0;
			item = "";
		}
		public void clear() {
			count = 0;
			item = "";
			sb = new StringBuffer();
			text.setText("");
		}
		public void newItem(String s) {
			count++;
			item = "<ol start=\""+count+"\">"+s;
			text.setText(item);
			sb.append(s);
		}
		public void appendItem(String s) {
			item += s;
			text.setText(item);
			sb.append(s);
		}
		public void append(String s) {
			sb.append(s);
		}
		public void showText() {
			text.setText(sb.toString());
		}
	}

	//Class to display the heading in the proper place
	class HeaderPanel extends JPanel {
		public HeaderPanel() {
			super();
			this.setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
			JLabel panelLabel = new JLabel(" Results",SwingConstants.LEFT);
			this.setBackground(background);
			Font labelFont = new Font("Dialog", Font.BOLD, 18);
			panelLabel.setFont(labelFont);
			this.add(panelLabel);
			this.add(Box.createHorizontalGlue());
			this.add(Box.createHorizontalStrut(17));
		}
	}

	//Class to display the footer with the action buttons and
	//the checkbox for changing the names of processed files.
	class FooterPanel extends JPanel implements ActionListener {
		public JButton anonymize;
		public JButton fixVRs;
		public JButton setPatientIDs;
		public JButton clearPreamble;
		public JCheckBox changeNameBox;
		public JCheckBox renameToSOPIUIDBox;
		public String remapperURL;
		public FooterPanel() {
			super();
			this.setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
			this.setBackground(background);

			String changeName = (String)properties.getProperty("change-name");
			if (changeName == null) {
				changeName = "yes";
				properties.setProperty("change-name",changeName);
			}
			changeNameBox = new JCheckBox("Change names of modified files",changeName.equals("yes"));
			changeNameBox.setBackground(background);
			changeNameBox.addActionListener(this);

			String useSOPIUID = (String)properties.getProperty("use-sopiuid");
			if (useSOPIUID == null) {
				useSOPIUID = "no";
				properties.setProperty("use-sopiuid",useSOPIUID);
			}

			renameToSOPIUIDBox = new JCheckBox("Use SOPIUID for name",useSOPIUID.equals("yes"));
			renameToSOPIUIDBox.setBackground(background);
			renameToSOPIUIDBox.addActionListener(this);

			anonymize = new JButton("Anonymize");
			fixVRs = new JButton("Fix VRs");
			clearPreamble = new JButton("Clear preamble");
			setPatientIDs = new JButton("Set PatientIDs");

			Box rowA = new Box(BoxLayout.X_AXIS);
			rowA.add(changeNameBox);
			rowA.add(Box.createHorizontalGlue());
			rowA.add(setPatientIDs);
			rowA.add(Box.createHorizontalStrut(4));
			rowA.add(fixVRs);
			rowA.add(Box.createHorizontalStrut(17));

			Box rowB = new Box(BoxLayout.X_AXIS);
			rowB.add(renameToSOPIUIDBox);
			rowB.add(Box.createHorizontalGlue());
			rowB.add(clearPreamble);
			rowB.add(Box.createHorizontalStrut(4));
			rowB.add(anonymize);
			rowB.add(Box.createHorizontalStrut(17));

			Dimension anSize = anonymize.getPreferredSize();
			Dimension vrSize = fixVRs.getPreferredSize();
			Dimension cpSize = clearPreamble.getPreferredSize();
			Dimension spSize = setPatientIDs.getPreferredSize();
			int maxWidth =
				Math.max(
					anSize.width,
					Math.max(
						vrSize.width,
						Math.max(
								cpSize.width,
								spSize.width)));
			anSize.width = maxWidth;
			anonymize.setPreferredSize(anSize);
			fixVRs.setPreferredSize(anSize);
			clearPreamble.setPreferredSize(anSize);
			setPatientIDs.setPreferredSize(anSize);

			this.add(rowA);
			this.add(rowB);
		}
		public void actionPerformed(ActionEvent evt) {
			properties.setProperty("change-name",(changeNameBox.isSelected() ? "yes" : "no"));
			properties.setProperty("use-sopiuid",(renameToSOPIUIDBox.isSelected() ? "yes" : "no"));
		}
	}

}
