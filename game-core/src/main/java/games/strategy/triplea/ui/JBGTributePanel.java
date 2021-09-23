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

public class JBGTributePanel {

  @Getter private JBGTerritoryManagerPanel parent = null;

  private String sPlayerNames;
  @Getter private String sTargetPlayerName;
  @Setter private int jCoin;
  @Getter private boolean validated = false; //getter will be: isValidated()
  @Getter private int spendJCoin = 0;
  //use this to disable unSelect of original selected item in selected list
  private int iTotalOriginalSelected = 0;
  
  private static final String TARGET_PLAYER_LIST_NAME = "TributeTo";
  private static final String TRIBUTE_AMOUNT_NAME = "Amount";

  private JScrollPane jPane = null;

  JLabel lblTargetPlayer;
  JLabel lblTributeAmount;
  JLabel lblJCoinAmount;
  JComboBox jcbPlayerNames;

  @Getter private String targetPlayer;
  @Getter private int tributeAmount;

  public JBGTributePanel(final JBGTerritoryManagerPanel p, final String sPlayers, final int jCoin) {

    this.sPlayerNames = sPlayers;
    this.jCoin = jCoin;
    this.spendJCoin = 0;
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

  protected JComboBox makeComboBox(final String[] names, JPanel panel, 
    GridBagLayout gridbag, GridBagConstraints c, int width) {
     JComboBox jcb = new JComboBox(names);
     gridbag.setConstraints(jcb, c);
     if (width == 0) width = 200;
     setComponentSize(jcb, width, 20);
     panel.add(jcb);
     return jcb;
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

  private void confirmProcess() {
    if (lblTargetPlayer.getText().length() > 1) {
      int iAmount = Integer.valueOf(lblTributeAmount.getText());
      if (iAmount > 0) {
        targetPlayer = lblTargetPlayer.getText();
        tributeAmount = iAmount;
        validated = true;
        closeDialog();
        return;
      }
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
    makeLabel("Current budget:", panel, gridbag, c, 0);

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
    makeLabel("Tribute Amount:", panel, gridbag, c, 0);

    c.weightx = 1.0; //spacing
    c.gridx = 1; //col
    c.gridy = 1; //row
    c.gridwidth = 1; //column span
    this.lblTributeAmount = makeLabel(String.valueOf(this.jCoin), panel, gridbag, c, 0);
    this.lblTributeAmount.setHorizontalAlignment(SwingConstants.RIGHT);

    c.gridx = 2; //col
    c.gridy = 1; //row
    c.gridwidth = 1; //column span
    makeLabel("jCoin", panel, gridbag, c, 50);

    //3rd row
    c.gridx = 0; //col
    c.gridy = 2; //row
    c.gridwidth = 1; //column span
    makeLabel("Tribute To:", panel, gridbag, c, 0);
    //5th
    c.gridx = 1; //col
    c.gridy = 2; //row
    c.gridwidth = 2; //column span
    lblTargetPlayer = makeLabel("?", panel, gridbag, c, 0);

    //4th
    c.weightx = 1.0;
    c.gridx = 0;
    c.gridy = 3;
    c.gridwidth = 3;
    c.gridheight = 1;
    final String[] arrPlayerNames = this.sPlayerNames.split("\\|", 20);
    this.jcbPlayerNames  = makeComboBox(arrPlayerNames, panel, gridbag, c, 200);
    this.jcbPlayerNames.setSelectedIndex(-1);
    jcbPlayerNames.addActionListener( new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int iSelected = jcbPlayerNames.getSelectedIndex();
        if (iSelected >= 0) {
          String sSelected = (String) jcbPlayerNames.getItemAt(iSelected);
          lblTargetPlayer.setText(sSelected);
System.out.println(sSelected);
        }
      }
    });

    //6th
    c.gridx = 0;
    c.gridy = 4;
    c.gridwidth = 2;
    c.gridheight = 1;
    ActionListener tributeListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        confirmProcess();
      }
    };
    JButton btnSelect = makeButtonWithAction("Tribute", panel, gridbag, c, tributeListenerFnc);
    setComponentSize(btnSelect, 80, 50);
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

    this.jPane = unitsPane;

    return unitsPane;
  }

}