package games.strategy.triplea.ui.history;

import games.strategy.engine.data.GameData;
import games.strategy.engine.random.IRandomStats;
import games.strategy.engine.random.RandomStatsDetails;
import games.strategy.triplea.Constants;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.Insets;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import javax.swing.border.EmptyBorder;
import javax.swing.SwingUtilities;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JTabbedPane;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.DefaultListModel;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.*;
import javax.swing.SwingConstants;
import javax.swing.JEditorPane;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.text.html.HTMLDocument;

import lombok.extern.java.Log;
import org.triplea.java.collections.IntegerMap;

import games.strategy.engine.data.JBGConstants;
import static games.strategy.triplea.image.UnitImageFactory.ImageKey;
import games.strategy.triplea.ui.UiContext;

import javax.swing.text.BadLocationException;
import java.io.IOException;

/**
 * A window used to display a textual summary of a particular history node, including all of its
 * descendants, if applicable.
 */
@Log
public class JBGTurnLogPaper {
  private static final long serialVersionUID = 4880602702815333376L;
  private JEditorPane textArea;
  HTMLEditorKit htmlKit;
  HTMLDocument doc;
  private JLabel newsLabel;
  private JScrollPane scrollingArea;
  private Component parent = null;

  public JBGTurnLogPaper(final Component parent) {
    this.parent = parent;
  }

  public void showNewsDialog(final GameData data) {

    JPanel p = makeNewsPaperPanel();

    int iGameStep = data.getJbgInternalTurnStep();

    //JBGTurnHistoryParser histParser = new JBGTurnHistoryParser();
    try {
      data.acquireReadLock();

      htmlKit.insertHTML(doc, doc.getLength(),
        "<p><center>" + 
        "<img src='file:jbg/assets/turnnews/turnnews_banner.png' width=\"800px\" height=\"auto\"/><br/>" +
        "<span class=\"papername\">" + 
        JBGConstants.JBGTURN_NEWS_PAPER_NAME + ", Volume " + (iGameStep<1?"Zero":String.valueOf(iGameStep)) + 
        "</span></center></p>", 0, 0, null);
      htmlKit.insertHTML(doc, doc.getLength(), data.getTurnNews(true), 0, 0, null);
    }
    catch (BadLocationException ex) {
      System.out.println(ex);
    }
    catch (IOException ex) {
      System.out.println(ex);
    }
    finally {
      data.releaseReadLock();
    }
    final int iPaper =
        JOptionPane.showOptionDialog(
            this.parent,
            p,
            JBGConstants.JBGTURN_NEWS_PAPER_NAME,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            null);
  }
  
  private JPanel makeNewsPaperPanel() {

    //Tien's note:
    //because JEditorPane must be resizable to make JScrollPane works
    //we have to set size of JPanel, and not set size of JEditorPane
    //It's different from JTextPane
    final JPanel content = new JPanel()  {
      @Override
      public Dimension getPreferredSize() {
          return new Dimension(800, 800);
      }
      @Override
      public Dimension getMinimumSize() {
          return new Dimension(800, 800);
      }
      @Override
      public Dimension getMaximumSize() {
          return new Dimension(800, 800);
      }
    };
    content.setLayout(new BorderLayout());
    content.setBorder(new EmptyBorder(0,0,0,0));
    //content.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;

    /*
    newsLabel = new JLabel() {
      @Override
      public Dimension getPreferredSize() {
          return new Dimension(800, 20);
      }
      @Override
      public Dimension getMinimumSize() {
          return new Dimension(800, 20);
      }
      @Override
      public Dimension getMaximumSize() {
          return new Dimension(800, 20);
      }
    };
    newsLabel.setVerticalAlignment(SwingConstants.CENTER);
    newsLabel.setHorizontalAlignment(SwingConstants.CENTER);
    content.add(newsLabel, gbc);
    */

    textArea = new JEditorPane();
    textArea.setEditable(false);
    textArea.setContentType("text/html");

    htmlKit = new HTMLEditorKit();
    textArea.setEditorKit(htmlKit);
    StyleSheet styleSheet = htmlKit.getStyleSheet();
    //JEditorPane sometime don't understand color name like: lightgray :)
    //background-image: 'file:jbg/assets/turnnews/turnnews_bkgnd.png'; 
    styleSheet.addRule("body {background-color: #f8debe; font: 11px times; margin: 4px; line-height: 12px; }");
    styleSheet.addRule("h1 {color: blue; line-height: 0.5;}");
    styleSheet.addRule("h2 {color: #ff0000; line-height: 0.5;}");
    styleSheet.addRule("h3 {color: #006666; line-height: 5px;}");
    styleSheet.addRule("span.papername { color: blue; line-height: 10px; font: bold 15px times; text-align: center; }");
    styleSheet.addRule("span.headline-detail { text-color: blue; line-height: 10px; font: bold 14px times; text-align: center; }");
    styleSheet.addRule("span.headline { line-height: 10px; font: bold 12px times;}");
    styleSheet.addRule("span.headline-addition { line-height: 10px; font: italic 11px monaco,times;}");
    styleSheet.addRule("span.content-group { line-height: 10px; font: bold 11px times;}");
    styleSheet.addRule("span.content { line-height: 10px; font: normal 11px times;}");
    styleSheet.addRule("p.tiny { line-height: 2px; font: 4px monaco;}");
    styleSheet.addRule("pre {font : 11px monaco; color : black; background-color : #fafafa; }");

    doc = (HTMLDocument) htmlKit.createDefaultDocument();
    textArea.setDocument(doc);

/* TODO
    try {
      String css = new StringBuilder()
                      .append(".column {")
                      .append("  float: left;")
                      .append("}")
                      .append(".left, .right {")
                      .append("  width: 25%;")
                      .append("}")
                      .append(".middle {")
                      .append("  width: 50%;")
                      .append("}")
                      .toString();
      styleSheet.addRule(css);
      String sContent = new StringBuilder()
      .append("<div class=\"row\">")
        .append("<div class=\"column\">Hello1</div>")
        .append("<div class=\"column\">Hello2</div>")
        .append("<div class=\"column\">Hello3</div>")
      .append("</div>")
      .toString();
      htmlKit.insertHTML(doc, doc.getLength(), "<p style=\"line-height:10px\">Hello, World</p>", 0, 0, null);
      htmlKit.insertHTML(doc, doc.getLength(), sContent, 0, 0, null);
    }
    catch (BadLocationException ex) {
      System.out.println(ex);
    }
    catch (IOException ex) {
      System.out.println(ex);
    }
*/

    scrollingArea = new JScrollPane(textArea,
        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollingArea.getVerticalScrollBar().setValue(0);
    // ... Get the content pane, set layout, add to center
    content.add(scrollingArea, BorderLayout.CENTER);//gbc);
    gbc.gridy++;

    return content;

  }

}
