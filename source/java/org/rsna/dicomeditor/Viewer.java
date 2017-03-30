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
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import org.apache.log4j.*;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ui.*;
import org.rsna.util.*;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.VRs;

/**
 * A JPanel that provides a DICOM viewer.
 */
public class Viewer extends JPanel implements ActionListener, FileListener, MouseWheelListener, KeyEventDispatcher, ChangeListener {

	static final Logger logger = Logger.getLogger(Viewer.class);

	JFileChooser saveAsChooser = null;
	int jpegQuality = -1;
	DicomObject dicomObject = null;
    ButtonPanel buttonPanel;
    ImagePanel imagePanel;
    int currentFrame = 0;
    int nFrames = 0;
    double currentZoom = 1.0;
    JScrollPane jsp;
    boolean isDragging = false;
    boolean monitoringKeys = false;
    int currentX = 0;
    int currentY = 0;
    FooterPanel footerPanel;

	/**
	 * Class constructor; creates a Viewer JPanel.
	 */
    public Viewer() {
		super();
		this.setLayout(new BorderLayout());
		buttonPanel = new ButtonPanel();
		imagePanel = new ImagePanel();
		footerPanel = new FooterPanel();
		jsp = new JScrollPane();
		jsp.getVerticalScrollBar().setUnitIncrement(25);
		jsp.getHorizontalScrollBar().setMinimum(25);
		jsp.setViewportView(imagePanel);
		this.add(buttonPanel, BorderLayout.NORTH);
		this.add(jsp, BorderLayout.CENTER);
		this.add(footerPanel, BorderLayout.SOUTH);
		this.setBackground(Configuration.getInstance().background);
		buttonPanel.addActionListener(this);
		buttonPanel.addMouseWheelListener(this);
		Dragger dragger = new Dragger();
		imagePanel.addMouseListener(dragger);
		imagePanel.addMouseMotionListener(dragger);
		imagePanel.addMouseWheelListener(dragger);
    }
    
	//ChaangeListener implementation
    public void stateChanged(ChangeEvent e) {
		if (this.isVisible() && !monitoringKeys) {
			KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
			manager.addKeyEventDispatcher(this);
			monitoringKeys = true;
		}
		else if (!this.isVisible() && monitoringKeys) {
			KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
			manager.removeKeyEventDispatcher(this);
			monitoringKeys = false;
		}
	}
    
	//KeyEventDispatcher implementation
	public boolean dispatchKeyEvent(KeyEvent e) {
		Object source = e.getSource();
		if (source instanceof JTextComponent) return false;
		if (e.getID() == KeyEvent.KEY_PRESSED) {
			int k = e.getKeyCode();
			if ((k == KeyEvent.VK_RIGHT) || (k == KeyEvent.VK_DOWN)) {
				displayFrame(currentFrame+1, currentZoom);
			}
			else if ((k == KeyEvent.VK_LEFT) || (k == KeyEvent.VK_UP)) {
				displayFrame(currentFrame-1, currentZoom);
			}
		}
		return false;
	}

    class Dragger extends MouseInputAdapter {
		int originalX = 0;
		int originalY = 0;
		int originalScrollX = 0;
		int originalScrollY = 0;
		int originalWW = 0;
		int originalWL = 0;
		long startTime = 0;
		public Dragger() {
			super();
			jsp.setWheelScrollingEnabled(false);
		}
		public void mousePressed(MouseEvent e) {
			originalX = e.getXOnScreen();
			originalY = e.getYOnScreen();
			originalScrollX = jsp.getHorizontalScrollBar().getValue();
			originalScrollY = jsp.getVerticalScrollBar().getValue();
			originalWW = buttonPanel.ww.getValue();
			originalWL = buttonPanel.wl.getValue();
			isDragging = true;
			startTime = System.currentTimeMillis();
			isDragging = true;
			setTheCursor();
		}
		public void mouseReleased(MouseEvent e) {
			isDragging = false;
			if (buttonPanel.wwwl.isPressed()) {
				displayFrame(currentFrame, currentZoom);
			}
			setTheCursor();
		}
		public void mouseDragged(MouseEvent e) {
			if (isDragging) {
				int currentX = e.getXOnScreen();
				int currentY = e.getYOnScreen();
				int deltaX = currentX - originalX;
				int deltaY = currentY - originalY;
				if (buttonPanel.drag.isPressed()) {
					int newScrollX = originalScrollX - deltaX;
					int newScrollY = originalScrollY - deltaY;
					jsp.getHorizontalScrollBar().setValue(newScrollX);
					jsp.getVerticalScrollBar().setValue(newScrollY);
				}
				else if (buttonPanel.wwwl.isPressed()) {
					buttonPanel.wl.setValue(originalWL + deltaY);
					buttonPanel.ww.setValue(originalWW + deltaX);
					long time = System.currentTimeMillis();
					if ((time - startTime) > 500) {
						displayFrame(currentFrame, currentZoom);
						startTime = System.currentTimeMillis();
					}
				}
			}
		}
		public void mouseMoved(MouseEvent e) {
			currentX = (int) (e.getX()/currentZoom);
			currentY = (int) (e.getY()/currentZoom);
			footerPanel.setParams();
		}
		public void mouseWheelMoved(MouseWheelEvent e) {
			if (dicomObject != null) {
				boolean altKeyDown = (e.getModifiersEx() & e.ALT_DOWN_MASK) == e.ALT_DOWN_MASK;
				int delta = -e.getWheelRotation();
				if (buttonPanel.zoom.isPressed() && !altKeyDown) {
					if (delta > 0) displayFrame(currentFrame, currentZoom + 0.05);
					if (delta < 0) displayFrame(currentFrame, currentZoom - 0.05);
				}
				else if (buttonPanel.drag.isPressed() && altKeyDown) {
					if (delta > 0) displayFrame(currentFrame, currentZoom + 0.05);
					if (delta < 0) displayFrame(currentFrame, currentZoom - 0.05);					
				}
				else {
					if (delta > 0) displayFrame(currentFrame-1, currentZoom);
					if (delta < 0) displayFrame(currentFrame+1, currentZoom);
				}
			}
		}
	}

	/**
	 * The FileListener implementation.
	 * @param event the event containing the current file selection.
	 */
	public void fileEventOccurred(FileEvent event) {
		if (event.isSELECT()) {
			try {
				File file = event.getFile();
				if (file.isFile()) {
					dicomObject = new DicomObject(file);
					if (dicomObject.isImage()) {
						nFrames = Math.max(dicomObject.getNumberOfFrames(), 1);
						setWWWL(dicomObject);
						currentFrame = 0;
						currentZoom = 1.0;
						jsp.getHorizontalScrollBar().setValue(0);
						jsp.getVerticalScrollBar().setValue(0);
						fitToWindow();
						buttonPanel.setFrameNumber();
						footerPanel.setFile(file);
						buttonPanel.clearButtons();
						setTheCursor();
						return;
					}
				}
			}
			catch (Exception unable) {
				dicomObject = null;
				imagePanel.clear();
				buttonPanel.setFrameNumber();
			}
		}
	}
	
	private void setWWWL(DicomObject dob) {
		int ww, wl;
		Point p = getWWWL(dob.getDataset());
		if (p != null) {
			wl = p.x;
			ww = p.y;
		}
		else {
			int bs = dob.getBitsStored();
			wl = 1000; //1 << (bs-1);
			ww = 1000; //wl / 2;
		}
		buttonPanel.ww.setValue(ww);
		buttonPanel.wl.setValue(wl);
	}
	
	private Point getWWWL(Dataset ds) {
		if (ds.contains(Tags.WindowCenter) && ds.contains(Tags.WindowWidth)) {
			try {
				DcmElement deWL = ds.get(Tags.WindowCenter);
				DcmElement deWW = ds.get(Tags.WindowWidth);
				String[] wlStrings = deWL.getStrings(ds.getSpecificCharacterSet());
				String[] wwStrings = deWW.getStrings(ds.getSpecificCharacterSet());
				int len = Math.min( wlStrings.length, wwStrings.length );
				if (len > 0) {
					return new Point(
								StringUtil.getInt(wlStrings[0]),
								StringUtil.getInt(wwStrings[0]) );
				}
			}
			catch (Exception ex) { ex.printStackTrace(); }
			return null;
		}
		else {
			for (Iterator it=ds.iterator(); it.hasNext(); ) {
				DcmElement de = (DcmElement)it.next();
				if (VRs.toString(de.vr()).equals("SQ")) {
					Dataset itemDS;
					int item = 0;
					while ((itemDS = de.getItem(item)) != null) {
						Point p = getWWWL(itemDS);
						if (p != null) return p;
						item++;
					}
				}
			}
			return null;
		}
	}
	
	private void setTheCursor() {
		if (buttonPanel.drag.isPressed()) {
			if (isDragging) {
				imagePanel.setCursor(CustomCursors.getInstance().getCursor("cursor_drag_hand", 16, 16));
			}
			else {
				imagePanel.setCursor(CustomCursors.getInstance().getCursor("cursor_hand", 16, 16));
			}
		}
		else if (buttonPanel.wwwl.isPressed()) {
			imagePanel.setCursor(CustomCursors.getInstance().getCursor("wwwl", 16, 16));
		}
		else if (buttonPanel.zoom.isPressed()) {
			imagePanel.setCursor(CustomCursors.getInstance().getCursor("zoom", 16, 16));
		}
		else {
			imagePanel.setCursor(Cursor.getDefaultCursor());
		}
	}
	
    /**
     * The ActionListener implementation; listens for buttons on the ButtonPanel.
     * @param e the event indicating which button was clicked.
     */
    public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source.equals(buttonPanel.firstFrame)) displayFrame(0, currentZoom);
		else if (source.equals(buttonPanel.prevFrame)) displayFrame(currentFrame-1, currentZoom);
		else if (source.equals(buttonPanel.nextFrame)) displayFrame(currentFrame+1, currentZoom);
		else if (source.equals(buttonPanel.lastFrame)) {
			displayFrame(nFrames-1, currentZoom);
		}
		else if (source.equals(buttonPanel.drag)) {
			buttonPanel.drag.toggle();
			buttonPanel.wwwl.setPressed(false);
			buttonPanel.zoom.setPressed(false);
		}
		else if (source.equals(buttonPanel.wwwl)) {
			buttonPanel.drag.setPressed(false);
			buttonPanel.wwwl.toggle();
			buttonPanel.zoom.setPressed(false);
		}
		else if (source.equals(buttonPanel.zoom)) {
			buttonPanel.drag.setPressed(false);
			buttonPanel.wwwl.setPressed(false);
			buttonPanel.zoom.toggle();
		}
		else if (source.equals(buttonPanel.fitToWindow)) {
			boolean altKeyDown = (e.getModifiers() & e.ALT_MASK) == e.ALT_MASK;
			if (!altKeyDown) fitToWindow();
			else displayFrame(currentFrame, 1.0);
		}
		else if (source.equals(buttonPanel.saveAsJPEG)) saveAsJPEG();
		setTheCursor();
	}
	
	private void fitToWindow() {
		Dimension d = jsp.getViewport().getExtentSize();
		int width = d.width;
		int height = d.height;
		int columns = dicomObject.getColumns();
		int rows = dicomObject.getRows();
		double widthZoom = ((double)width)/((double)columns);
		double heightZoom = ((double)height)/((double)rows);
		double zoom = Math.min(widthZoom, heightZoom);
		displayFrame(currentFrame, zoom);
	}		

    /**
     * The MouseWheelListener implementation; listens for mouse motion.
     * @param event the event indicating the motion of the wheel.
     */
    public void mouseWheelMoved(MouseWheelEvent event) {
		int delta = -event.getWheelRotation();
		Object source = event.getSource();
		if (source.equals(buttonPanel.frameLabel)) {
			if (delta > 0) displayFrame(currentFrame-1, currentZoom);
			if (delta < 0) displayFrame(currentFrame+1, currentZoom);
		}
		else if (source.equals(buttonPanel.ww)) {
			buttonPanel.ww.increment(delta);
			displayFrame(currentFrame, currentZoom);
		}
		else if (source.equals(buttonPanel.wl)) {
			buttonPanel.wl.increment(delta);
			displayFrame(currentFrame, currentZoom);
		}
	}

	//Open an image, given the file.
	private void displayFrame(int frame, double zoom) {
		if (frame < 0) frame = 0;
		if (frame >= nFrames) frame = nFrames - 1;
		currentFrame = frame;
		try {
			int width = dicomObject.getColumns();
			int desiredWidth = (int)(width * zoom);
			if (desiredWidth > 2048) desiredWidth = 2048;
			if (desiredWidth < 64) desiredWidth = 64;
			double scale = (double)desiredWidth / (double)width;
			int ww = buttonPanel.ww.getValue();
			int wl = buttonPanel.wl.getValue();
			BufferedImage bufferedImage = dicomObject.getScaledAndWindowLeveledBufferedImage(frame, scale, wl, ww);
			imagePanel.saveScrollState();
			currentZoom = (double)bufferedImage.getWidth() / (double)width;
			imagePanel.setImage(bufferedImage);
			imagePanel.resetScrollState();
			footerPanel.setParams();
		}
		catch (Exception e) {
			logger.warn("Exception while getting the BufferedImage", e);
			JOptionPane.showMessageDialog(this, "Exception:\n\n"+e.getMessage());
		}
		this.validate();
		buttonPanel.setFrameNumber();
	}

	//Create a JPEG image from the currently open DICOM image.
	private void saveAsJPEG() {
		DialogPanel dialog = new SaveAsJPEGDialog(currentFrame+1, nFrames, dicomObject.getColumns());
		int result = JOptionPane.showOptionDialog(
				this,
				dialog,
				"Save as JPEG",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null, //icon
				null, //options
				null); //initialValue
		if (result != JOptionPane.OK_OPTION) return;
		try {
			int width = Integer.parseInt(dialog.getParam("width"));
			jpegQuality = Integer.parseInt(dialog.getParam("quality"));
			LinkedList<Integer> frames = new LinkedList<Integer>();
			Tokenizer tokenizer = new Tokenizer(dialog.getParam("frames"));
			int sign = 1;
			String text = "";
			Token token;
			while ((token=tokenizer.getNextToken(",-")) != null) {
				if (!token.isEmpty()) {
					int i = sign * Integer.parseInt(token.getText());
					frames.add(new Integer(i));
					sign = (token.getDelimiter() == '-') ? -1 : +1;
				}
			}
			if (saveAsChooser == null) {
				saveAsChooser = new JFileChooser();
				saveAsChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				Dimension d = saveAsChooser.getPreferredSize();
				d.width = 800;
				saveAsChooser.setPreferredSize(d);
			}
			File dobFile = dicomObject.getFile().getAbsoluteFile();
			File dobDir = dobFile.getParentFile();
			saveAsChooser.setCurrentDirectory(dobDir.getParentFile());
			saveAsChooser.setSelectedFile(dobDir);
			saveAsChooser.setDialogTitle("Select directory for frame storage");
			if (saveAsChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				File dir = saveAsChooser.getSelectedFile();
				String name = dicomObject.getFile().getName();
				if (name.toLowerCase().endsWith(".dcm")) name = name.substring(0, name.length()-4);
				int lastFrame = 0;
				for (Integer frameInteger : frames) {
					int nextFrame = frameInteger.intValue();
					if (nextFrame > 0) {
						saveFrame(dir, name, nextFrame, width, jpegQuality);
						lastFrame = nextFrame;
					}
					else if (nextFrame < 0) {
						nextFrame = -nextFrame;
						for (int f=lastFrame+1; f<=nextFrame; f++) {
							saveFrame(dir, name, f, width, jpegQuality);
						}
						lastFrame = nextFrame;
					}
				}
				JOptionPane.showMessageDialog(this, "Success");
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error:\n"+e.getMessage());
		}
	}
	
	private void saveFrame(File dir, String name, int frame, int width, int jpegQuality) {
		String filename = name + "["+frame+"].jpeg";
		File file = new File(dir, filename);
		int ww = buttonPanel.ww.getValue();
		int wl = buttonPanel.wl.getValue();
		int dobWidth = dicomObject.getColumns();
		double scale = (double)width / (double)dobWidth;
		dicomObject.saveAsWindowLeveledJPEG(file, frame-1, scale, wl, ww, jpegQuality);
	}
	
	class SaveAsJPEGDialog extends DialogPanel {
		public SaveAsJPEGDialog(int frame, int nFrames, int width) {
			super();
			addH("Save as JPEG");
			addP("Specify the compression quality (0-100, default=-1)", "left");
			addParam("quality", "Quality", "-1", false);
			space(5);
			addP("Specify the width of the saved JPEG image", "left");
			addParam("width", "Width", Integer.toString(width), false);
			space(5);
			addP("Specify the frames to be saved (e.g., 1-3,4,7)", "left");
			addParam("frames", "Frames", "1-"+nFrames, false);
			space(5);
		}
	}

	class ImagePanel extends JPanel {
		BufferedImage bufferedImage;
		double savedZoom = 0.0;
		Point savedMousePosition = null;
		Point savedScrollBarValues = null;
		public ImagePanel() {
			super();
			setBackground(Color.black);
		}
		public void setImage(BufferedImage bufferedImage) {
			this.bufferedImage = bufferedImage;
			int width = bufferedImage.getWidth();
			int height = bufferedImage.getHeight();
			setPreferredSize(new Dimension(width, height));
			this.getParent().invalidate();
			this.getParent().validate();
			repaint();
		}
		public void clear() {
			this.bufferedImage = null;
			repaint();
		}
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (bufferedImage != null) {
				g.drawImage(bufferedImage,0,0,null);
			}
		}
		public void saveScrollState() {
			savedMousePosition = getMousePosition();
			if (savedMousePosition != null) {
				savedZoom = currentZoom;
				JScrollBar hsb = jsp.getHorizontalScrollBar();
				JScrollBar vsb = jsp.getVerticalScrollBar();
				savedScrollBarValues = new Point(hsb.getValue(), vsb.getValue());
			}
		}
		public void resetScrollState() {
			if (savedMousePosition != null) {
				JScrollBar hsb = jsp.getHorizontalScrollBar();
				JScrollBar vsb = jsp.getVerticalScrollBar();
				int origVisibleX = savedMousePosition.x - savedScrollBarValues.x;
				int origVisibleY = savedMousePosition.y - savedScrollBarValues.y;
				double scale = currentZoom / savedZoom;
				double newZoomedX = scale * savedMousePosition.x;
				double newZoomedY = scale * savedMousePosition.y;
				hsb.setValue((int)(newZoomedX - origVisibleX));
				vsb.setValue((int)(newZoomedY - origVisibleY));
				savedMousePosition = null;
			}
		}
	}

	class ButtonPanel extends JPanel {
		public IconButton firstFrame;
		public IconButton prevFrame;
		public JLabel frameLabel;
		public IconButton nextFrame;
		public IconButton lastFrame;
		public IconButton zoom;
		public IconButton drag;
		public IconButton wwwl;
		public NumericField ww;
		public NumericField wl;
		public IconButton fitToWindow;
		public IconButton saveAsJPEG;
		private Box box;
		Color background = Color.white;
		public ButtonPanel() {
			super();
			this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			background = Configuration.getInstance().background;
			this.setBackground(background);
			makeComponents();
			box = new Box(BoxLayout.X_AXIS);
			box.setBackground(background);
			addComponents(box);
			this.add(Box.createVerticalStrut(3));
			this.add(box);
			this.add(Box.createVerticalStrut(3));
		}
		private void makeComponents() {
			try {
				frameLabel = new JLabel("");
				firstFrame = new IconButton("/icons/go-first.png", "First Frame");
				prevFrame = new IconButton("/icons/go-previous.png", "Prev Frame");
				nextFrame = new IconButton("/icons/go-next.png", "Next Frame");
				lastFrame = new IconButton("/icons/go-last.png", "Last Frame");
				zoom = new IconButton("/cursors/zoom.png", "Zoom");
				drag = new IconButton("/cursors/cursor_hand.png", "Pan");
				wwwl = new IconButton("/cursors/wwwl.png", "Window Level & Width");
				ww = new NumericField("WW", 0, 2, 65535);
				wl = new NumericField("WL", 0, -65536, 65535);
				fitToWindow = new IconButton("/icons/fullscreen.png", "Fit to Window");
				saveAsJPEG = new IconButton("/icons/floppy.png", "Save as JPEG");
				wwwl.setPressed(true);
			}
			catch (Exception e) {
				JOptionPane.showMessageDialog(this,"Exception:\n\n"+e.getMessage());
			}
		}
		private void addComponents(Box box) {
			box.add(Box.createHorizontalStrut(5));
			box.add(firstFrame);
			box.add(prevFrame);
			box.add(Box.createHorizontalStrut(10));
			box.add(frameLabel);
			box.add(Box.createHorizontalStrut(10));
			box.add(nextFrame);
			box.add(lastFrame);
			box.add(Box.createHorizontalGlue());
			box.add(Box.createHorizontalStrut(5));
			box.add(zoom);
			box.add(Box.createHorizontalStrut(5));
			box.add(drag);
			box.add(Box.createHorizontalStrut(5));
			box.add(wwwl);
			box.add(Box.createHorizontalStrut(5));
			box.add(wl);
			box.add(Box.createHorizontalStrut(5));
			box.add(ww);
			box.add(Box.createHorizontalStrut(10));
			box.add(fitToWindow);
			box.add(Box.createHorizontalStrut(5));
			box.add(saveAsJPEG);
			box.add(Box.createHorizontalStrut(5));
		}
		public void setFrameNumber() {
			boolean isImage = (dicomObject!=null) && dicomObject.isImage();
			if (isImage) {
				int nFrames = dicomObject.getNumberOfFrames();
				if (nFrames == 0) nFrames = 1;
				frameLabel.setText("Frame "+(currentFrame+1)+" of "+nFrames);
			}
			this.setVisible(isImage);
		}
		public void clearButtons() {
			zoom.setPressed(false);
			drag.setPressed(false);
			wwwl.setPressed(false);
		}
		public void addActionListener(ActionListener listener) {
			firstFrame.addActionListener(listener);
			prevFrame.addActionListener(listener);
			nextFrame.addActionListener(listener);
			lastFrame.addActionListener(listener);
			drag.addActionListener(listener);
			wwwl.addActionListener(listener);
			zoom.addActionListener(listener);
			fitToWindow.addActionListener(listener);
			saveAsJPEG.addActionListener(listener);
		}
		public void addMouseWheelListener(MouseWheelListener listener) {
			frameLabel.addMouseWheelListener(listener);
			ww.addMouseWheelListener(listener);
			wl.addMouseWheelListener(listener);
		}
		class IconButton extends JButton {
			boolean pressed = false;
			public IconButton(String resource, String tip) throws Exception {
				super();
				byte[] bytes = FileUtil.getBytes(FileUtil.getStream(resource));
				Icon icon = new ImageIcon(bytes);
				setIcon(icon);
				setToolTipText(tip);
				fixBorder();
			}
			public boolean isPressed() {
				return pressed;
			}
			public void setPressed(boolean pressed) {
				this.pressed = pressed;
				fixBorder();
			}
			public void toggle() {
				this.pressed = !pressed;
				fixBorder();
			}
			private void fixBorder() {
				if (pressed) setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
				else setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
			}				
		}
		class NumericField extends JPanel implements ActionListener {
			String name;
			JTextField text;
			int value;
			int min;
			int max;
			public NumericField(String name, int value, int min, int max) {
				super();
				Border inner = BorderFactory.createEmptyBorder(2, 0, 0, 0);
				Border outer = BorderFactory.createBevelBorder(BevelBorder.LOWERED);
				setBorder(BorderFactory.createCompoundBorder(outer, inner));
				setBackground(background);
				text = new JTextField("-0000");
				Dimension d = text.getPreferredSize();
				d.width = 40;
				text.setPreferredSize(d);
				text.setMinimumSize(d);
				this.name = name;
				this.min = min;
				this.max = max;
				add(new JLabel(name+":"));
				add(text);
				d = this.getMinimumSize();
				this.setPreferredSize(d);
				this.setMaximumSize(d);
				text.setHorizontalAlignment(JTextField.RIGHT);
				text.addActionListener(this);
				setValue(value);
			}
			public void setValue(int value) {
				if (value < min) value = min;
				if (value > max) value = max;
				this.value = value;
				text.setText(String.format("%d", value));
			}
			public void increment(int increment) {
				try { value = Integer.parseInt(text.getText().trim()); }
				catch (Exception ex) { }
				value += increment;
				setValue(this.value);
			}
			public int getValue() {
				try { return Integer.parseInt(text.getText().trim()); }
				catch (Exception ex) { setValue(value); }
				return value;
			}
			public void actionPerformed(ActionEvent e) {
				displayFrame(currentFrame, currentZoom);
			}
		}
	}
	
	class FooterPanel extends JPanel {
		JLabel filename;
		JLabel params;
		public FooterPanel() {
			super();
			this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			this.setBackground(Configuration.getInstance().background);
			filename = new JLabel(" ");
			params = new JLabel(" ");
			this.add(filename);
			this.add(Box.createHorizontalGlue());
			this.add(params);
		}
		public void setFilename(String name) {
			if (name.equals("")) name = " ";
			filename.setText(name);
		}
		public void setFile(File file) {
			setFilename(file.getName());
		}
		public void setParams() {
			String s = String.format("(%d,%d)  z=%.2f", currentX, currentY, currentZoom);
			params.setText(s);
		}
	}

}
