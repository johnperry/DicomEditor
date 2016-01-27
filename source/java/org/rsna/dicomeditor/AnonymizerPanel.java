/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomeditor;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import org.rsna.ui.ApplicationProperties;
import org.rsna.ui.PropertyEvent;
import org.rsna.ui.PropertyListener;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.w3c.dom.*;

/**
 * A JPanel that provides a user interface for editing the scripts of
 * the MIRC anonymizer. The anonymizer provides de-identification and
 * re-identification of DICOM objects for clinical trials. Each element
 * as well as certain groups of elements are scriptable. The script
 * language is defined in "How to Configure the Anonymizer for MIRC
 * Clinical Trial Services". The scripts are stored as a properties
 * file. The AnonymizerPanel edits the text of the file rather than
 * the values in the properties hashtable in order to keep the
 * elements in sequence and because elements that are not enabled
 * are not present in the hashtable.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class AnonymizerPanel extends JPanel implements ActionListener {

	private JScrollPane configSP;
	private ConfiguratorPanel configuratorPanel;
	private FooterPanel footerPanel;
	File scriptFile;
	Color background;
	boolean showAll = true;

	/**
	 * Class constructor.
	 */
    public AnonymizerPanel() {
		super();
		Configuration config = Configuration.getInstance();
		setLayout(new BorderLayout());
		scriptFile = new File(config.dicomScriptFile);
		background = config.background;
		setBackground(background);

		configuratorPanel = new ConfiguratorPanel();
		configSP = new JScrollPane();
		configSP.setViewportView(configuratorPanel);
		configSP.getVerticalScrollBar().setUnitIncrement(25);
		configSP.getHorizontalScrollBar().setUnitIncrement(15);
		footerPanel = new FooterPanel();
		footerPanel.showItems.addActionListener(this);
		footerPanel.uncheck.addActionListener(this);
		footerPanel.save.addActionListener(this);
		footerPanel.reset.addActionListener(this);
		this.add(footerPanel,BorderLayout.SOUTH);
		this.add(configSP,BorderLayout.CENTER);
   }

	/**
	 * Implementation of the ActionListener for the Save Changes button.
	 * @param event the event.
	 */
    public void actionPerformed(ActionEvent event) {
		if (event.getSource().equals(footerPanel.save)) {
			configuratorPanel.save();
		}
		else if (event.getSource().equals(footerPanel.reset)) {
			configuratorPanel = new ConfiguratorPanel();
			configSP.setViewportView(configuratorPanel);
		}
		else if (event.getSource().equals(footerPanel.uncheck)) {
			configuratorPanel.uncheckAll();
		}
		else if (event.getSource().equals(footerPanel.showItems)) {
			showAll = !showAll;
			configuratorPanel.showItems(showAll);
		}
	}

	// JPanel to display the editable fields for the anonymizer scripts
	class ConfiguratorPanel extends JPanel {
		public ConfiguratorPanel() {
			super();
			setBackground(background);
			setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
			DAScript script = DAScript.getInstance( scriptFile );
			Document doc = script.toXML();
			Element root = doc.getDocumentElement();
			Node child = root.getFirstChild();
			while (child != null) {
				if (child instanceof Element) {
					Element el = (Element)child;
					String tag = el.getTagName();
					if (tag.equals("p"))
						this.add(new ParamProp(el));
					else if (tag.equals("e") )
						this.add(new SetProp(el));
					else if (tag.equals("r"))
						this.add(new RemoveProp(el));
					else if (tag.equals("k"))
						this.add(new KeepProp(el));
				}
				child = child.getNextSibling();
			}
		}
		public void showItems(boolean all) {
			Component[] components = getComponents();
			for (int i=0; i<components.length; i++) {
				if (components[i] instanceof Prop) {
					boolean checked = ((Prop)components[i]).isChecked();
					((Prop)components[i]).makeVisible(checked || all);
				}
			}
		}
		public void uncheckAll() {
			Component[] components = getComponents();
			for (int i=0; i<components.length; i++) {
				if (components[i] instanceof Prop) {
					((Prop)components[i]).uncheck();
				}
			}
		}
		public void save() {
			boolean ok = true;
			try {
				Component[] components = getComponents();
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement("script");
				doc.appendChild(root);
				for (int i=0; i<components.length; i++) {
					if (components[i] instanceof Prop) {
						((Prop)components[i]).appendPropTo(root);
					}
				}
				StringBuffer sb = new StringBuffer();
				sb.append("<" + root.getTagName() + ">\n");
				Node child = root.getFirstChild();
				while (child != null) {
					if (child instanceof Element) {
						Element el = (Element)child;
						String tag = el.getTagName();
						String en = el.getAttribute("en");
						String t = el.getAttribute("t");
						String n = XmlUtil.escapeChars(el.getAttribute("n"));
						String value = XmlUtil.escapeChars(el.getTextContent());
						sb.append(" <"+tag);
						if (!en.equals("")) sb.append(" en=\""+en+"\"");
						sb.append(" t=\""+t+"\"");
						if (!n.equals("")) sb.append(" n=\""+n+"\"");
						sb.append(">"+value+"</"+tag+">\n");
					}
					child = child.getNextSibling();
				}
				sb.append("</" + root.getTagName() + ">\n");
				ok = FileUtil.setText(scriptFile, sb.toString());
			}
			catch (Exception e) { ok = false; }
			if (!ok) {
				JOptionPane.showMessageDialog(this,
					"An error has occurred while saving the changes to\n" +
					"the anonymizer configuration. You should stop the\n" +
					"program now and consult IT to ensure that anonymization\n" +
					"has not been damaged in such a way as to allow PHI to\n" +
					"be transmitted on the internet.",
					"Error Saving the Anonymizer Configuration",
					JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	// Interface to provide a call to get the text of a script
	interface Prop {
		public void appendPropTo(Element parent);
		public void uncheck();
		public boolean isChecked();
		public void makeVisible(boolean vis);
	}

	// Prop to handle P elements
	// <p t="PROFILENAME">CTP Clinical Trial Default</p>
	class ParamProp extends JPanel implements Prop {
		JLabel label;
		JTextField value;
		String t;
		public ParamProp(Element prop) {
			super();
			setLayout(new FlowLayout(FlowLayout.LEFT));
			setBackground(background);
			t = prop.getAttribute("t").trim();
			label = new PropLabel(t);
			value = new PropTextField();
			value.setText(prop.getTextContent().trim());
			add(Box.createHorizontalStrut(39));
			add(label);
			add(value);
			add(Box.createHorizontalGlue());
		}
		public void appendPropTo(Element parent) {
			Element el = parent.getOwnerDocument().createElement("p");
			el.setAttribute("t", t);
			el.setTextContent(value.getText().trim());
			parent.appendChild(el);
		}
		public void uncheck() {
			//do nothing
		}
		public boolean isChecked() {
			return true;
		}
		public void makeVisible(boolean vis) {
			this.setVisible(vis);
		}
	}

	// Prop to handle E elements
	// <e en="T" t="04000402" n="RefDigitalSignatureSeq">@remove()</e>
	class SetProp extends JPanel implements Prop {
		JCheckBox cb;
		JLabel label;
		JTextField value;
		String t;
		String n;
		public SetProp(Element prop) {
			super();
			setLayout(new FlowLayout(FlowLayout.LEFT));
			setBackground(background);
			cb = new PropCheckBox();
			cb.setSelected(prop.getAttribute("en").equals("T"));
			t = prop.getAttribute("t").trim();
			n = prop.getAttribute("n").trim();
			label = new PropLabel("["+t.substring(0,4)+","+t.substring(4,8)+"] "+n);
			value = new PropTextField();
			value.setText(prop.getTextContent().trim());
			add(Box.createHorizontalStrut(5));
			add(cb);
			add(Box.createHorizontalStrut(3));
			add(label);
			add(value);
			add(Box.createHorizontalGlue());
		}
		public void appendPropTo(Element parent) {
			Element el = parent.getOwnerDocument().createElement("e");
			el.setAttribute("en", (cb.isSelected() ? "T" : "F"));
			el.setAttribute("t", t);
			el.setAttribute("n", n);
			el.setTextContent(value.getText().trim());
			parent.appendChild(el);
		}
		public void uncheck() {
			cb.setSelected(false);
		}
		public boolean isChecked() {
			return cb.isSelected();
		}
		public void makeVisible(boolean vis) {
			this.setVisible(vis);
		}
	}

	// Prop to handle R elements
	// <r en="T" t="privategroups">Remove private groups</r>
	class RemoveProp extends JPanel implements Prop {
		JCheckBox cb;
		JLabel label;
		String t;
		public RemoveProp(Element prop) {
			super();
			setLayout(new FlowLayout(FlowLayout.LEFT));
			setBackground(background);
			cb = new PropCheckBox();
			cb.setSelected(prop.getAttribute("en").equals("T"));
			t = prop.getAttribute("t").trim();
			String labelText = prop.getTextContent();
			if (t.equals("privategroups")) labelText = "Remove private groups [recommended]";
			else if (t.equals("unspecifiedelements")) labelText = "Remove unchecked elements";
			else if (t.equals("overlays")) labelText = "Remove overlays (groups 60xx)";
			label = new PropLabel(labelText);
			add(Box.createHorizontalStrut(5));
			add(cb);
			add(Box.createHorizontalStrut(3));
			add(label);
			add(Box.createHorizontalGlue());
		}
		public void appendPropTo(Element parent) {
			Element el = parent.getOwnerDocument().createElement("r");
			el.setAttribute("en", (cb.isSelected() ? "T" : "F"));
			el.setAttribute("t", t);
			el.setTextContent(label.getText());
			parent.appendChild(el);
		}
		public void uncheck() {
			cb.setSelected(false);
		}
		public boolean isChecked() {
			return cb.isSelected();
		}
		public void makeVisible(boolean vis) {
			this.setVisible(vis);
		}
	}

	// Prop to handle K elements
	// <k en="F" t="0020">Keep group 0020</k>
	class KeepProp extends JPanel implements Prop {
		JCheckBox cb;
		JLabel label;
		String t;
		public KeepProp(Element prop) {
			super();
			setLayout(new FlowLayout(FlowLayout.LEFT));
			setBackground(background);
			cb = new PropCheckBox();
			cb.setSelected(prop.getAttribute("en").equals("T"));
			t = prop.getAttribute("t").trim();
			String labelText = prop.getTextContent().trim();
			if (t.equals("group18")) labelText = "Keep group 18 [recommended]";
			else if (t.equals("group20")) labelText = "Keep group 20 [recommended]";
			else if (t.equals("group28")) labelText = "Keep group 28 [recommended]";
			label = new PropLabel(labelText);
			add(Box.createHorizontalStrut(5));
			add(cb);
			add(Box.createHorizontalStrut(3));
			add(label);
			add(Box.createHorizontalGlue());
		}
		public void appendPropTo(Element parent) {
			Element el = parent.getOwnerDocument().createElement("k");
			el.setAttribute("en", (cb.isSelected() ? "T" : "F"));
			el.setAttribute("t", t);
			el.setTextContent(label.getText());
			parent.appendChild(el);
		}
		public void uncheck() {
			cb.setSelected(false);
		}
		public boolean isChecked() {
			return cb.isSelected();
		}
		public void makeVisible(boolean vis) {
			this.setVisible(vis);
		}
	}

	class PropLabel extends JLabel {
		public PropLabel(String labelText) {
			super(labelText);
			setFont(new Font("Monospaced", Font.BOLD, 12));
			Dimension d = getPreferredSize();
			d.width = 350;
			setPreferredSize(d);
		};
	}

	class PropCheckBox extends JCheckBox {
		public PropCheckBox() {
			super();
			setBackground(background);
		}
	}

	class PropTextField extends JTextField {
		public PropTextField() {
			super(100);
			setFont(new Font("Monospaced", Font.BOLD, 12));
		}
	}

	// Class to display a single property script
	class FooterPanel extends JPanel implements ActionListener {
		public JButton save;
		public JButton reset;
		public JButton uncheck;
		public JButton showItems;
		public boolean showAll = true;
		public FooterPanel() {
			super();
			setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			setLayout(new FlowLayout());
			setBackground(background);
			showItems = new JButton("Show Checked Elements Only");
			showItems.addActionListener(this);
			add(showItems);
			add(Box.createHorizontalStrut(20));
			uncheck = new JButton("Uncheck All");
			add(uncheck);
			add(Box.createHorizontalStrut(20));
			save = new JButton("Save Changes");
			add(save);
			add(Box.createHorizontalStrut(20));
			reset = new JButton("Reset");
			add(reset);
		}
	    public void actionPerformed(ActionEvent event) {
			showAll = !showAll;
			if (showAll) showItems.setText("Show Checked Elements Only");
			else showItems.setText("Show All Elements");
		}
	}

}
