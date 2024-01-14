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
import javax.swing.JList;
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

public class JBGTerritoryBuildingPurchasePanel {

  @Getter private JBGTerritoryManagerPanel parent = null;

  private String sOriginEcoBuilds;
  private String sOriginResBuilds;
  private String sEcoBuilds;
  private String sResBuilds;
  @Setter private int jCoin;
  @Getter private List<Integer> selectedEcoList;
  @Getter private List<Integer> selectedResList;
  @Getter private boolean validated = false; //getter will be: isValidated()
  @Getter private int spendJCoin = 0;
  //use this to disable unSelect of original selected item in selected list
  private int iTotalOriginalSelected = 0;
  
  private static final String AVAIL_LIST_NAME = "available";
  private static final String SELECT_LIST_NAME = "selected";

  JList<JBGTerritoryBuildingSelectItem> availListCtl;
  JList<JBGTerritoryBuildingSelectItem> selectListCtl;

  private JScrollPane jPane = null;

  JLabel lblSelectBuildings;
  JLabel lblAvailBuildings;
  JLabel lblJCoinAmount;
  JLabel lblSpendJCointAmount;

  public JBGTerritoryBuildingPurchasePanel(final JBGTerritoryManagerPanel p, final String sEcoBuilds, final String sResBuilds, final int jCoin) {
    this.sEcoBuilds = sEcoBuilds;
    this.sResBuilds = sResBuilds;
    //save for compare on confirmation step
    this.sOriginEcoBuilds = sEcoBuilds;
    this.sOriginResBuilds = sResBuilds;
    //
    this.jCoin = jCoin;
    this.spendJCoin = 0;

    this.selectedEcoList = new ArrayList<>();
    this.selectedResList = new ArrayList<>();

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

  private void removeItemFromList(JList<JBGTerritoryBuildingSelectItem> lst, final int idx) {
    DefaultListModel model = (DefaultListModel) lst.getModel();
    model.remove(idx);
  }

  private void addItemToList(JList<JBGTerritoryBuildingSelectItem> lst, final JBGTerritoryBuildingSelectItem val) {
    DefaultListModel model = (DefaultListModel) lst.getModel();
    model.addElement(val);
  }

  public void clickOnBuilding(final String lstName, Object selObj) {
    int iSelectedItem = -1;
     JBGTerritoryBuildingSelectItem item = (JBGTerritoryBuildingSelectItem) selObj;


    if (lstName.equals(AVAIL_LIST_NAME)) {
      availListCtl.getSelectedIndex();
    }
    else {
      selectListCtl.getSelectedValue();
    }

    if (iSelectedItem >= 0) {

    }
  }


  public void dblClickSelectBuilding(final String lstName) {
    if (lstName.equals(AVAIL_LIST_NAME)) {
      selectBuildingToBuy();
    }
    else if (lstName.equals(SELECT_LIST_NAME)) {
      unSelectBuildingToBuy();
    }
  }

  private void updateSelPrice(int iAddPrice) {
    this.spendJCoin += iAddPrice;
    lblSpendJCointAmount.setText(String.valueOf(this.spendJCoin));
  }

  private void updateBudget() {
    this.spendJCoin = Integer.parseInt(lblSpendJCointAmount.getText());
    int iCurrentBudget = this.jCoin - this.spendJCoin;
    lblJCoinAmount.setText(String.valueOf(iCurrentBudget));
  }

  private void selectBuildingToBuy() {
    int iSelectedIdx = availListCtl.getSelectedIndex();
    if (iSelectedIdx >= 0) {
      validated = false;

      JBGTerritoryBuildingSelectItem item = availListCtl.getSelectedValue();
      removeItemFromList(availListCtl, iSelectedIdx);
      addItemToList(selectListCtl, item);
      availListCtl.setSelectedIndex(-1);
      updateSelPrice(item.getPrice());
    }
  }

  private void selectAllBuildingToBuy() {
    DefaultListModel availModel = (DefaultListModel) availListCtl.getModel();

    do {
      validated = false;

      JBGTerritoryBuildingSelectItem item = (JBGTerritoryBuildingSelectItem) availModel.getElementAt(0);

      if (item == null) {
        System.out.println("Item is null at 0");
        break;
      }

      if (this.spendJCoin + item.getPrice() > this.jCoin) {
        System.out.println("price is higher than budget " + String.valueOf(this.spendJCoin) + "+" + String.valueOf(item.getPrice()) + ">" + String.valueOf(this.jCoin));
        break;
      }

      removeItemFromList(availListCtl, 0);
      addItemToList(selectListCtl, item);
      updateSelPrice(item.getPrice());
    } while (availModel.getSize() > 0);
    availListCtl.setSelectedIndex(-1);
  }

  private void unSelectBuildingToBuy() {
    int iSelectedIdx = selectListCtl.getSelectedIndex();
    if (iSelectedIdx >= iTotalOriginalSelected) {
      validated = false;

      JBGTerritoryBuildingSelectItem item = selectListCtl.getSelectedValue();
      removeItemFromList(selectListCtl, iSelectedIdx);
      addItemToList(availListCtl, item);
      selectListCtl.setSelectedIndex(-1);
      updateSelPrice(item.getPrice()*-1);
    }
  }

  private void closeDialog() {
    Window w = SwingUtilities.getWindowAncestor(this.jPane);
    if(w != null) w.setVisible(false);
  }

  private void confirmProcess() {
    DefaultListModel model = (DefaultListModel) selectListCtl.getModel();

    final String[] arrOriginEcoBlds = this.sOriginEcoBuilds.split("\\|", JBGConstants.MAP_HORZ_TILES);
    final String[] arrOriginResBlds = this.sOriginResBuilds.split("\\|", JBGConstants.MAP_HORZ_TILES);

    for (int i = 0; i < model.getSize(); i++) {
      JBGTerritoryBuildingSelectItem item = (JBGTerritoryBuildingSelectItem) model.getElementAt(i);

      //only save items which not contains in original list
      if (item.getType() == 0) {
        if (!arrOriginEcoBlds[item.getId()].equals("1")) {
          this.selectedEcoList.add(item.getId());
        }
      }
      else {
        if (!arrOriginResBlds[item.getId()].equals("1")) {
          this.selectedResList.add(item.getId());
        }
      }
    }
    updateBudget();
    validated = true;

    closeDialog();
  }

  protected JList<JBGTerritoryBuildingSelectItem> makeScrollList(final String sColName, JPanel panel, GridBagLayout gridbag, GridBagConstraints c) {
    DefaultListModel<JBGTerritoryBuildingSelectItem> listModel = new DefaultListModel<>();

    final String[] arrEcoBlds = this.sEcoBuilds.split("\\|", JBGConstants.MAP_HORZ_TILES);
    final String[] arrResBlds = this.sResBuilds.split("\\|", JBGConstants.MAP_HORZ_TILES);

    iTotalOriginalSelected = 0;
    //eco
    for (int i = 0; i < JBGConstants.MAP_HORZ_TILES; i++) {
      int iPrice = (int) Math.round((i+1)*JBGConstants.BUILDING_ECO_PRICE_MULTIPLIER)+JBGConstants.BUILDING_ECO_PRICE_MINIMUM;
      JBGTerritoryBuildingSelectItem tItem = new JBGTerritoryBuildingSelectItem(
        i, JBGConstants.ECO_BUILD_NAMES.get(i), iPrice, 0 /*EcoType*/
        );
      if (arrEcoBlds[i].equals("1")) {
        if (sColName.equals(SELECT_LIST_NAME)) {
          listModel.addElement(tItem);
          iTotalOriginalSelected++;
        }
      }
      else {
        if (sColName.equals(AVAIL_LIST_NAME)) {
          listModel.addElement(tItem);
        }
      }
    }
    //res
    for (int i = 0; i < JBGConstants.MAP_HORZ_TILES; i++) {
        int iPrice = (int) Math.round((i+1)*JBGConstants.BUILDING_RES_PRICE_MULTIPLIER)+JBGConstants.BUILDING_RES_PRICE_MINIMUM;
        JBGTerritoryBuildingSelectItem tItem = new JBGTerritoryBuildingSelectItem(
          i, JBGConstants.RES_BUILD_NAMES.get(i), iPrice, 1 /*ResType*/
          );
      if (arrResBlds[i].equals("1")) {
        if (sColName.equals(SELECT_LIST_NAME)) {
          listModel.addElement(
               tItem
            );
          iTotalOriginalSelected++;
        }
      }
      else {
        if (sColName.equals(AVAIL_LIST_NAME)) {
          listModel.addElement(
               tItem
            );
        }
      }
    }

    final JList<JBGTerritoryBuildingSelectItem> lstObj = new JList<>(listModel) {
        @Override
         public String getToolTipText(MouseEvent me) {
            int index = locationToIndex(me.getPoint());
            if (index > -1) {
               JBGTerritoryBuildingSelectItem item = (JBGTerritoryBuildingSelectItem) getModel().getElementAt(index);
               return "Price " + String.valueOf(item.getPrice());
            }
            return null;
         }
    }; //data has type Object[]
    lstObj.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    lstObj.setSelectedIndex(-1);
    //lstObj.setFont(new Font("Arial", Font.PLAIN, 17));
    lstObj.setVisibleRowCount(15);
    lstObj.setName(sColName);

    ListSelectionListener listSelFnc = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          JList<JBGTerritoryBuildingSelectItem> list = (JList<JBGTerritoryBuildingSelectItem>) e.getSource();
          //this is how to get access to parent class :D
          JBGTerritoryBuildingPurchasePanel parent = JBGTerritoryBuildingPurchasePanel.this;
          Object selObj = list.getSelectedValue();
          if (selObj != null) {
            final String lstName = list.getName();
            //System.out.println(lstName + " " + selVal);
            parent.clickOnBuilding(lstName, selObj);
          }

        }
      }
    };
    lstObj.addListSelectionListener(listSelFnc);

    MouseAdapter mAdpt = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent evt) {
          JList list = (JList)evt.getSource();
          if (evt.getClickCount() >= 2) {
              // Double-click detected
              int index = list.locationToIndex(evt.getPoint());
              if (index >= 0) {
                final String lstName = list.getName();

                //may be user double click to unknown item (not currently selected item)
                list.setSelectedIndex(index);
                JBGTerritoryBuildingPurchasePanel parent = JBGTerritoryBuildingPurchasePanel.this;
                parent.dblClickSelectBuilding(lstName);
              }
          }
      }
    };
    lstObj.addMouseListener(mAdpt);

    JScrollPane listScroller = new JScrollPane(lstObj);

    gridbag.setConstraints(listScroller, c);
    panel.add(listScroller);

    return lstObj;
  }

  //JBG
  public JScrollPane makePanel(
      final UiContext uiContext) {

    final JPanel panel = new JPanel();

    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();

    panel.setBorder(BorderFactory.createEmptyBorder(2, 1, 2, 1));
    //panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setFont(new Font("Arial", Font.PLAIN, 14));
    panel.setLayout(gridbag);

    c.fill = GridBagConstraints.BOTH;

    //1st row, 5 columns
    c.fill = GridBagConstraints.HORIZONTAL; //natural height, maximum width
    c.weightx = 1.0; //spacing
    c.gridx = 0; //col
    c.gridy = 0; //row
    c.gridwidth = 1; //column span
    makeLabel("Territory Build", panel, gridbag, c, 0);

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

    c.gridx = 3; //col
    c.gridy = 0; //row
    c.gridwidth = 1; //column span
    makeLabel("Spending:", panel, gridbag, c, 0);

    c.weightx = 1.0; //spacing
    c.gridx = 4; //col
    c.gridy = 0; //row
    c.gridwidth = 1; //column span
    this.lblSpendJCointAmount = makeLabel(String.valueOf(this.spendJCoin), panel, gridbag, c, 0);

    //2nd row
    c.weightx = 1.0;
    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 2;
    c.gridheight = 3;
    this.availListCtl = makeScrollList(AVAIL_LIST_NAME, panel, gridbag, c);

    ActionListener selectListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        selectBuildingToBuy();
      }
    };
    c.gridy = 1;
    c.gridx = 2;
    c.gridwidth = 1;
    c.gridheight = 1;
    JButton btnSelect = makeButtonWithAction("Select", panel, gridbag, c, selectListenerFnc);
    setComponentSize(btnSelect, 80, 50);

    ActionListener selectAllListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        selectAllBuildingToBuy();
      }
    };
    c.gridy = 2;
    c.gridx = 2;
    c.gridwidth = 1;
    c.gridheight = 1;
    JButton btnSelectAll = makeButtonWithAction("Select All", panel, gridbag, c, selectAllListenerFnc);
    setComponentSize(btnSelectAll, 80, 50);

    ActionListener unSelectListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        unSelectBuildingToBuy();
        //shuffleAllListModels();
        //clearWordListSelection();
      }
    };
    c.gridy = 3;
    c.gridx = 2;
    c.gridwidth = 1;
    c.gridheight = 1;
    JButton btnUnSelect = makeButtonWithAction("UnSelect", panel, gridbag, c, unSelectListenerFnc);
    setComponentSize(btnUnSelect, 80, 50);

    c.weightx = 1.0;
    c.gridx = 3;
    c.gridy = 1;
    c.gridwidth = 2;
    c.gridheight = 3;
    this.selectListCtl = makeScrollList(SELECT_LIST_NAME, panel, gridbag, c);

    //3rd row, reserve for building descriptions
    c.weightx = 1.0;
    c.gridx = 0;
    c.gridy = 3;
    c.gridwidth = 5;
    c.gridheight = 1;
    makeLabel("Selected Building", panel, gridbag, c, 0);

    c.weightx = 1.0;
    c.gridx = 0;
    c.gridy = 4;
    c.gridwidth = 5;
    makeLabel("Selected Building Description", panel, gridbag, c, 0);

    //last row
    ActionListener confirmListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        confirmProcess();
      }
    };

    c.gridy = 5;
    c.gridx = 4;
    c.gridwidth = 1;
    makeButtonWithAction("Confirm", panel, gridbag, c, confirmListenerFnc);

    final JScrollPane unitsPane = new JScrollPane();
    unitsPane.setBorder(BorderFactory.createEmptyBorder());
    unitsPane.getVerticalScrollBar().setUnitIncrement(20);
    unitsPane.setViewportView(panel);

    this.jPane = unitsPane;

    return unitsPane;
  }

}