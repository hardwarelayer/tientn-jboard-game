package games.strategy.triplea.ui;

import static games.strategy.triplea.image.UnitImageFactory.ImageKey;
import games.strategy.triplea.ui.UiContext;
import games.strategy.triplea.ui.TooltipProperties;
import games.strategy.engine.data.JBGConstants;

import com.google.common.collect.ImmutableList;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.JBGKanjiItem;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.TerritoryEffect;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.Properties;
import games.strategy.triplea.ResourceLoader;
import games.strategy.triplea.attachments.TechAttachment;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.TerritoryEffectHelper;
import games.strategy.triplea.delegate.AbstractMoveDelegate.MoveType;
import games.strategy.triplea.delegate.BaseEditDelegate;
import games.strategy.triplea.delegate.GameStepPropertiesHelper;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TransportTracker;
import games.strategy.triplea.delegate.UnitComparator;
import games.strategy.triplea.delegate.battle.ScrambleLogic;
import games.strategy.triplea.delegate.data.MustMoveWithDetails;
import games.strategy.triplea.delegate.move.validation.MoveValidator;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.ui.AbstractMovePanel;
import games.strategy.triplea.ui.DefaultMapSelectionListener;
import games.strategy.triplea.ui.MouseDetails;
import games.strategy.triplea.ui.SimpleUnitPanel;
import games.strategy.triplea.ui.TripleAFrame;
import games.strategy.triplea.ui.UndoableMovesPanel;
import games.strategy.triplea.ui.UnitChooser;
import games.strategy.triplea.ui.panel.jbg.JBGTerritoryViewCanvasPanel;
import games.strategy.triplea.ui.panels.map.MapPanel;
import games.strategy.triplea.ui.panels.map.MapSelectionListener;
import games.strategy.triplea.ui.panels.map.MouseOverUnitListener;
import games.strategy.triplea.ui.panels.map.UnitSelectionListener;
import games.strategy.triplea.ui.unit.scroller.UnitScroller;
import games.strategy.triplea.util.TransportUtils;
import games.strategy.triplea.util.UnitCategory;
import games.strategy.triplea.util.UnitSeparator;
import games.strategy.ui.OverlayIcon;

import games.strategy.triplea.ui.UiContext;
import games.strategy.triplea.ui.JBGKanjiUnits;
import games.strategy.triplea.ui.mapdata.MapData;

import games.strategy.triplea.ui.screen.drawable.BaseMapDrawable;
import games.strategy.triplea.ui.screen.drawable.BattleDrawable;
import games.strategy.triplea.ui.screen.drawable.BlockadeZoneDrawable;
import games.strategy.triplea.ui.screen.drawable.CapitolMarkerDrawable;
import games.strategy.triplea.ui.screen.drawable.ConvoyZoneDrawable;
import games.strategy.triplea.ui.screen.drawable.DecoratorDrawable;
import games.strategy.triplea.ui.screen.drawable.IDrawable;
import games.strategy.triplea.ui.screen.drawable.KamikazeZoneDrawable;
import games.strategy.triplea.ui.screen.drawable.LandTerritoryDrawable;
import games.strategy.triplea.ui.screen.drawable.ReliefMapDrawable;
import games.strategy.triplea.ui.screen.drawable.SeaZoneOutlineDrawable;
import games.strategy.triplea.ui.screen.drawable.TerritoryEffectDrawable;
import games.strategy.triplea.ui.screen.drawable.TerritoryNameDrawable;
import games.strategy.triplea.ui.screen.drawable.VcDrawable;
import games.strategy.ui.Util;
import org.triplea.util.Tuple;

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
import javax.swing.ListSelectionModel;
import javax.swing.DefaultListModel;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

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

import lombok.Getter;
import lombok.Setter;
import org.triplea.java.ObjectUtils;
import org.triplea.java.PredicateBuilder;
import org.triplea.java.collections.CollectionUtils;
import org.triplea.java.collections.IntegerMap;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.jpanel.JPanelBuilder;
import javax.swing.SwingConstants;
import org.triplea.swing.key.binding.KeyCode;
import org.triplea.swing.key.binding.SwingKeyBinding;

public class JBGWordMatchPanel {

  private final String sWordMatchEmptyValue = "....................";
  @Getter private JBGTerritoryManagerPanel parent = null;

  private static final String KANJI_LIST_NAME = "kanji";
  private static final String HIRA_LIST_NAME = "hira";
  private static final String HV_LIST_NAME = "hv";
  private static final String MEANING_LIST_NAME = "meaning";

  private static final String MATCH_WORD_OK = "OK";
  private static final String MATCH_WORD_NG = "NG";

  private boolean bWordMatchStart = false;
  private List<JBGKanjiItem> kanjiList = null;

  JList<String> kanjiListCtl;
  JList<String> hiraListCtl;
  JList<String> hvListCtl;
  JList<String> vnListCtl;

  JLabel lblSelKanji;
  JLabel lblSelHiragana;
  JLabel lblSelHV;
  JLabel lblSelViet;
  JLabel lblJCoinAmount;

  public JBGWordMatchPanel(JBGTerritoryManagerPanel p) {
    this.kanjiList = p.getNewKanjiList();
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

  private void setLabelSize(JLabel lbl, final int width, final int height) {
    lbl.setMinimumSize(new Dimension(width, height));
    lbl.setPreferredSize(new Dimension(width, height));
    lbl.setMaximumSize(new Dimension(width, height));
  }

  protected JLabel makeLabel(String name, JPanel panel, GridBagLayout gridbag, GridBagConstraints c) {
     JLabel lbl = new JLabel(name);
     gridbag.setConstraints(lbl, c);
     setLabelSize(lbl, 200, 20);
     panel.add(lbl);
     return lbl;
  }

  private String[] isSelectedWordMatched(final String kanji, final String hira, final String hv, final String meaning) {
    String[] res = new String[]{"", MATCH_WORD_NG};
    for (JBGKanjiItem item: this.kanjiList) {
      if (kanji.equals(item.getKanji()) &&
          hira.equals(item.getHiragana()) &&
          hv.equals(item.getHv()) && 
          meaning.equals(item.getMeaning())
          ) {
            res[0] = item.getKanji();
            res[1] = MATCH_WORD_OK;
          }
    }
    return res;
  }

  private boolean updateWordStat(final String kanji, final String isOK) {
      for (JBGKanjiItem item: this.kanjiList) {
        if (item.getKanji().equals(kanji) ) {
          item.increaseTest(isOK.equals(MATCH_WORD_OK)?true:false);
          return true;
        }
      }
      return false;
  }

  private void clearWordListSelection() {
    lblSelKanji.setText(sWordMatchEmptyValue);
    lblSelHiragana.setText(sWordMatchEmptyValue);
    lblSelHV.setText(sWordMatchEmptyValue);
    lblSelViet.setText(sWordMatchEmptyValue);

    kanjiListCtl.clearSelection();
    hiraListCtl.clearSelection();
    hvListCtl.clearSelection();
    vnListCtl.clearSelection();

    kanjiListCtl.setSelectedIndex(-1);
    hiraListCtl.setSelectedIndex(-1);
    hvListCtl.setSelectedIndex(-1);
    vnListCtl.setSelectedIndex(-1);
  }

  private void removeItemFromList(JList lst, final int idx) {
    DefaultListModel model = (DefaultListModel) lst.getModel();
    model.remove(idx);
  }

  private boolean validateKanjiSelection() {
    boolean allFieldSet = true;

    final int iKanjiSel = kanjiListCtl.getSelectedIndex();
    final int iHiraSel = hiraListCtl.getSelectedIndex();
    final int iHvSel = hvListCtl.getSelectedIndex();
    final int iVnSel = vnListCtl.getSelectedIndex();

    if (iKanjiSel == -1) {
      allFieldSet = false;
    }
    if (iHiraSel == -1) {
      allFieldSet = false;
    }
    if (iHvSel == -1) {
      allFieldSet = false;
    }
    if (iVnSel == -1) {
      allFieldSet = false;
    }

    if (allFieldSet) {

      final String kanji = lblSelKanji.getText();
      final String hira = lblSelHiragana.getText();
      final String hv = lblSelHV.getText();
      final String viet = lblSelViet.getText();

      if (kanji.length() == 0) allFieldSet = false;
      if (hira.length() == 0) allFieldSet = false;
      if (hv.length() == 0) allFieldSet = false;
      if (viet.length() == 0) allFieldSet = false;

      if (allFieldSet) {

        final String[] matchRes = isSelectedWordMatched(kanji, hira, hv, viet);
        if (matchRes[1].equals(MATCH_WORD_OK) && matchRes[0].length() > 0) {
          //remove the correct matched word set from lists
          removeItemFromList(kanjiListCtl, iKanjiSel);
          removeItemFromList(hiraListCtl, iHiraSel);
          removeItemFromList(hvListCtl, iHvSel);
          removeItemFromList(vnListCtl, iVnSel);

          int totalJCoin = parent.getJCoin();
          totalJCoin++;
          parent.setJCoin(totalJCoin);
          lblJCoinAmount.setText(String.valueOf(totalJCoin));
        }
        //update the statistic of word
        if (!updateWordStat(matchRes[0], matchRes[1])) {
          //can't update
          System.out.println("cannot update work of" + matchRes[1]);
        }
        clearWordListSelection();

      }

    }

    return allFieldSet;
  }

  public boolean updateSelectedKanjis(final String sColName, final String value) {

    if (!this.bWordMatchStart) return false;

    switch (sColName) {
      case KANJI_LIST_NAME:
        lblSelKanji.setText(value);
        break;
      case HIRA_LIST_NAME:
        lblSelHiragana.setText(value);
        break;
      case HV_LIST_NAME:
        lblSelHV.setText(value);
        break;
      case MEANING_LIST_NAME:
        lblSelViet.setText(value);
        break;
    }
    return validateKanjiSelection();
  }

  private DefaultListModel<String> shuffleListModel(DefaultListModel<String> mdl) {
    for(int i=0;i<mdl.size();i++){
        int swapWith = (int)(Math.random()*(mdl.size()-i))+i;
        if(swapWith==i) continue;
        mdl.add(i, mdl.remove(swapWith));
        mdl.add(swapWith, mdl.remove(i+1));
    }
    return mdl;
  }

  public void shuffleAllListModels() {
    shuffleListModel( (DefaultListModel<String>) this.kanjiListCtl.getModel() );
    shuffleListModel( (DefaultListModel<String>) this.hiraListCtl.getModel() );
    shuffleListModel( (DefaultListModel<String>) this.hvListCtl.getModel() );
    shuffleListModel( (DefaultListModel<String>) this.vnListCtl.getModel() );
  }

  protected JList<String> makeScrollList(final String sColName, JPanel panel, GridBagLayout gridbag, GridBagConstraints c) {
    DefaultListModel<String> listModel = new DefaultListModel<String>();

    for (JBGKanjiItem kanjiItem: this.kanjiList) {
      switch (sColName) {
      case KANJI_LIST_NAME:
        listModel.addElement(kanjiItem.getKanji());
        break;
      case HIRA_LIST_NAME:
        listModel.addElement(kanjiItem.getHiragana());
        break;
      case HV_LIST_NAME:
        listModel.addElement(kanjiItem.getHv());
        break;
      case MEANING_LIST_NAME:
        listModel.addElement(kanjiItem.getMeaning());
        break;
      }
    }

    //not shuffle here, let's user have a time to learn before shuffle (start Button)
    //listModel = shuffleListModel(listModel);

    final JList<String> lstObj = new JList<>(listModel); //data has type Object[]
    lstObj.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    lstObj.setSelectedIndex(-1);
    lstObj.setFont(new Font("Arial", Font.PLAIN, 17));
    lstObj.setVisibleRowCount(15);
    lstObj.setName(sColName);

    ListSelectionListener listSelFnc = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          JList list = (JList) e.getSource();
          //this is how to get access to parent class :D
          JBGWordMatchPanel parent = JBGWordMatchPanel.this;
          Object selObj = list.getSelectedValue();
          if (selObj != null) {
            final String selVal = selObj.toString();
            final String lstName = list.getName();
            //System.out.println(lstName + " " + selVal);
            parent.updateSelectedKanjis(lstName, selVal);
          }

        }
      }
    };
    lstObj.addListSelectionListener(listSelFnc);

    JScrollPane listScroller = new JScrollPane(lstObj);

    gridbag.setConstraints(listScroller, c);
    panel.add(listScroller);

    return lstObj;
  }

  //JBG
  public JScrollPane makeKanjiGamePanel(
      final UiContext uiContext) {

    final JPanel panel = new JPanel();

    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();

    panel.setBorder(BorderFactory.createEmptyBorder(2, 1, 2, 1));
    //panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setFont(new Font("Arial", Font.PLAIN, 14));
    panel.setLayout(gridbag);

    c.fill = GridBagConstraints.BOTH;

    c.fill = GridBagConstraints.HORIZONTAL; //natural height, maximum width
    c.weightx = 1.0; //spacing
    c.gridx = 0; //col
    c.gridy = 0; //row
    c.gridwidth = 2; //column span
    makeLabel("Kanji matching task", panel, gridbag, c);

    c.weightx = 1.0; //spacing
    c.gridx = 2; //col
    c.gridy = 0; //row
    c.gridwidth = 1; //column span
    makeLabel("JCoin:", panel, gridbag, c);

    c.weightx = 1.0; //spacing
    c.gridx = 3; //col
    c.gridy = 0; //row
    c.gridwidth = 1; //column span
    this.lblJCoinAmount = makeLabel(String.valueOf(parent.getJCoin()), panel, gridbag, c);

    c.weightx = 1.0;
    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 4;
    makeLabel("Selected:", panel, gridbag, c);

    c.gridx = 0;
    c.gridy = 2;
    c.gridwidth = 1;
    this.lblSelKanji = makeLabel(sWordMatchEmptyValue, panel, gridbag, c);
    c.gridx = 1;
    this.lblSelHiragana = makeLabel(sWordMatchEmptyValue, panel, gridbag, c);
    c.gridx = 2;
    this.lblSelHV = makeLabel(sWordMatchEmptyValue, panel, gridbag, c);
    c.gridx = 3;
    this.lblSelViet = makeLabel(sWordMatchEmptyValue, panel, gridbag, c);

    c.weightx = 1.0;
    c.gridx = 0;
    c.gridy = 3;
    c.gridwidth = 1;
    this.kanjiListCtl = makeScrollList(KANJI_LIST_NAME, panel, gridbag, c);

    c.weightx = 1.0;
    c.gridx = 1;
    c.gridy = 3;
    c.gridwidth = 1;
    this.hiraListCtl = makeScrollList(HIRA_LIST_NAME, panel, gridbag, c);

    c.weightx = 1.0;
    c.gridx = 2;
    c.gridy = 3;
    c.gridwidth = 1;
    this.hvListCtl = makeScrollList(HV_LIST_NAME, panel, gridbag, c);

    c.weightx = 1.0;
    c.gridx = 3;
    c.gridy = 3;
    c.gridwidth = 1;
    this.vnListCtl = makeScrollList(MEANING_LIST_NAME, panel, gridbag, c);

    ActionListener startListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        shuffleAllListModels();
        clearWordListSelection();
        bWordMatchStart = true;
      }
    };

    ActionListener resetListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        clearWordListSelection();
      }
    };

    c.gridy = 4;
    c.gridx = 0;
    c.gridwidth = 2;
    makeButtonWithAction("Start", panel, gridbag, c, startListenerFnc);
    c.gridx = 3;
    c.gridwidth = 2;
    makeButtonWithAction("Reset", panel, gridbag, c, resetListenerFnc);


    final JScrollPane unitsPane = new JScrollPane();
    unitsPane.setBorder(BorderFactory.createEmptyBorder());
    unitsPane.getVerticalScrollBar().setUnitIncrement(20);
    unitsPane.setViewportView(panel);

    return unitsPane;
  }

}