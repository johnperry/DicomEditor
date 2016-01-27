/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomeditor;

import org.rsna.installer.SimpleInstaller;

/**
 * The DicomEditor program installer, consisting of just a
 * main method that instantiates a SimpleInstaller.
 */
public class Installer {

	static String windowTitle = "DicomEditor Installer";
	static String programName = "DicomEditor";
	static String introString = "<p><b>DicomEditor</b> is a stand-alone tool for viewing, editing, "
								+ "and anonymizing DICOM objects.</p>";

	public static void main(String args[]) {
		new SimpleInstaller(windowTitle,programName,introString);
	}
}
