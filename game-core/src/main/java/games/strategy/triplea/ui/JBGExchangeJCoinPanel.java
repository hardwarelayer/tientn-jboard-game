package games.strategy.triplea.ui;

import static games.strategy.triplea.image.UnitImageFactory.ImageKey;
import games.strategy.triplea.ui.UiContext;
import games.strategy.engine.data.JBGConstants;
import games.strategy.triplea.ui.UiContext;
import games.strategy.triplea.ui.JBGKanjiUnits;

import java.lang.Math;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.nio.file.Path;

import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.Iterator;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JTabbedPane;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.DefaultListModel;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import javax.swing.SwingConstants;
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
import java.awt.Window;
import java.awt.event.MouseAdapter;

import lombok.Getter;
import lombok.Setter;

public class JBGExchangeJCoinPanel {

  @Getter private JBGTerritoryManagerPanel parent = null;

  @Setter private int jCoin;
  @Setter private int pus;
  @Getter private boolean validated = false; //getter will be: isValidated()
  
  private static final String TARGET_PLAYER_LIST_NAME = "ExchangeTo";
  private static final String TRIBUTE_AMOUNT_NAME = "Amount";

  private JScrollPane jPane = null;

  JLabel lblPUsAmount;
  JLabel lblJCoinAmount;

  @Getter private int exchangeAmount;
  @Getter private boolean xchgJCoin2PUs;

  public JBGExchangeJCoinPanel(final JBGTerritoryManagerPanel p, final int jCoin, final int pus) {

    this.jCoin = jCoin;
    this.pus = pus;
    this.exchangeAmount = 0;
    this.xchgJCoin2PUs = false;
    this.validated = false;
    this.parent = p;
  }

  protected JButton makebutton(String name,
                            JPanel panel,
                           GridBagLayout gridbag,
                           GridBagConstraints c) {
     JButton button = new JButton(name);
     gridbag.setConstraints(button, c);
     panel.add(button);
     return button;
  }

  protected JButton makeButtonWithAction(String name,
                            JPanel panel,
                           GridBagLayout gridbag,
                           GridBagConstraints c,
                           ActionListener listener) {
     JButton button = new JButton(name);
     button.addActionListener(listener);
     gridbag.setConstraints(button, c);
     panel.add(button);
     return button;
  }

  private void setComponentSize(JComponent jCom, final int width, final int height) {
    jCom.setMinimumSize(new Dimension(width, height));
    jCom.setPreferredSize(new Dimension(width, height));
    jCom.setMaximumSize(new Dimension(width, height));
  }

  protected JLabel makeLabel(String name, JPanel panel, GridBagLayout gridbag, GridBagConstraints c, int width) {
     JLabel lbl = new JLabel(name);
     gridbag.setConstraints(lbl, c);
     if (width == 0) width = 200;
     setComponentSize(lbl, width, 20);
     panel.add(lbl);
     return lbl;
  }

  private void closeDialog() {
    Window w = SwingUtilities.getWindowAncestor(this.jPane);
    if(w != null) w.setVisible(false);
  }

  private void confirmProcess(boolean fromJCoin2PU) {
    int iAmount = 0;
    if (fromJCoin2PU) {
      iAmount = Integer.valueOf(lblJCoinAmount.getText());
    }
    else {
      iAmount = Integer.valueOf(lblPUsAmount.getText());
    }
    if (iAmount > 0) {
      this.exchangeAmount = iAmount;
      this.xchgJCoin2PUs = fromJCoin2PU;
      validated = true;
      closeDialog();
      return;
    }
    validated = false;
  }

  //JBG
  public JScrollPane makePanel(
      final UiContext uiContext) {

    final JPanel panel = new JPanel();

    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();

    panel.setBorder(BorderFactory.createEmptyBorder(2, 1, 2, 1));
    panel.setFont(new Font("Arial", Font.PLAIN, 14));
    panel.setLayout(gridbag);

    c.fill = GridBagConstraints.BOTH;

    //1st row, 5 columns
    c.fill = GridBagConstraints.HORIZONTAL; //natural height, maximum width
    c.weightx = 1.0; //spacing
    c.gridx = 0; //col
    c.gridy = 0; //row
    c.gridwidth = 1; //column span
    makeLabel("Current jCoin:", panel, gridbag, c, 0);

    c.weightx = 1.0; //spacing
    c.gridx = 1; //col
    c.gridy = 0; //row
    c.gridwidth = 1; //column span
    this.lblJCoinAmount = makeLabel(
      String.valueOf(this.jCoin), // parent.getJCoin()), 
      panel, gridbag, c, 0);
    this.lblJCoinAmount.setHorizontalAlignment(SwingConstants.RIGHT);

    c.gridx = 2; //col
    c.gridy = 0; //row
    c.gridwidth = 1; //column span
    makeLabel("jCoin", panel, gridbag, c, 50);

    //2nd row
    c.gridx = 0; //col
    c.gridy = 1; //row
    c.gridwidth = 1; //column span
    makeLabel("Current PUs:", panel, gridbag, c, 0);

    c.weightx = 1.0; //spacing
    c.gridx = 1; //col
    c.gridy = 1; //row
    c.gridwidth = 1; //column span
    this.lblPUsAmount = makeLabel(String.valueOf(this.pus), panel, gridbag, c, 0);
    this.lblPUsAmount.setHorizontalAlignment(SwingConstants.RIGHT);

    c.gridx = 2; //col
    c.gridy = 1; //row
    c.gridwidth = 1; //column span
    makeLabel("PUs", panel, gridbag, c, 50);

    //6th
    c.gridx = 0;
    c.gridy = 4;
    c.gridwidth = 1;
    c.gridheight = 1;
    ActionListener j2pListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        confirmProcess(true);
      }
    };
    JButton btnExchangeJCoin2PU = makeButtonWithAction("JCoin->PUs", panel, gridbag, c, j2pListenerFnc);
    setComponentSize(btnExchangeJCoin2PU, 80, 50);
    c.gridx = 1;
    c.gridy = 4;
    c.gridwidth = 1;
    c.gridheight = 1;
    ActionListener p2jListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        confirmProcess(false);
      }
    };
    JButton btnExchangePU2JCoin = makeButtonWithAction("PUs->JCoin", panel, gridbag, c, p2jListenerFnc);
    setComponentSize(btnExchangePU2JCoin, 80, 50);

    c.gridx = 2;
    c.gridy = 4;
    c.gridwidth = 1;
    c.gridheight = 1;
    ActionListener cancelListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        closeDialog();
      }
    };
    JButton btnCancel = makeButtonWithAction("Cancel", panel, gridbag, c, cancelListenerFnc);
    setComponentSize(btnCancel, 80, 50);

    final JScrollPane unitsPane = new JScrollPane();
    unitsPane.setBorder(BorderFactory.createEmptyBorder());
    unitsPane.getVerticalScrollBar().setUnitIncrement(20);
    unitsPane.setViewportView(panel);

    if (this.jCoin < 1) btnExchangeJCoin2PU.setEnabled(false);
    if (this.pus < 1) btnExchangePU2JCoin.setEnabled(false);

    this.jPane = unitsPane;

    return unitsPane;
  }

}