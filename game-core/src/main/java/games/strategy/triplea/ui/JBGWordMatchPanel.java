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
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.ActionMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

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
import java.awt.event.KeyEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.InputEvent;

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

  private static final String UP = "Up";
  private static final String DOWN = "Down";
  private static final String NO_1 = "NO_1";
  private static final String NO_2 = "NO_2";
  private static final String NO_3 = "NO_3";
  private static final String NO_4 = "NO_4";
  private static final String START_KEY = "START_KEY";
  private static final String LOAD_NORMAL_KEY = "LOAD_NORMAL_KEY";
  private static final String LOAD_NEW_KEY = "LOAD_NEW_KEY";
  private static final String LOAD_NOTE_KEY = "LOAD_NOTE_KEY";

  private static final String NAME_LIST_KANJI = "kanji";
  private static final String NAME_LIST_HIRAGANA = "hira";
  private static final String NAME_LIST_HANVIET = "hv";
  private static final String NAME_LIST_MEANING = "meaning";

  private static final String MATCH_WORD_OK = "OK";
  private static final String MATCH_WORD_NG = "NG";

  private boolean bWordMatchStart = false;
  private List<JBGKanjiItem> kanjiList = null;
  int     iCurrentSelectedList = 1;

  ArrayList<String> lstProblematicWords = new ArrayList<>();

  JList<String> kanjiListCtl;
  JList<String> hiraListCtl;
  JList<String> hvListCtl;
  JList<String> vnListCtl;

  JLabel lblStats = null;
  JLabel lblSneakpeek;
  JLabel lblSelKanji;
  JLabel lblSelHiragana;
  JLabel lblSelHV;
  JLabel lblSelViet;
  JLabel lblJCoinAmount;

  JButton btnLoadNormalKanji;
  JButton btnLoadNewKanji;
  JButton btnLoadNotedKanji;
  JButton btnStartTest;

  private static class ListKeyAction extends AbstractAction {
    JBGWordMatchPanel masterModel = null;
      public ListKeyAction(String name, JBGWordMatchPanel model) {//, BoundedRangeModel model, int scrollableIncrement) {
          super(name);
          System.out.println("List Key Action constructing ...");
          this.masterModel = model;
          //this.vScrollBarModel = model;
          //this.scrollableIncrement = scrollableIncrement;
      }

      @Override
      public void actionPerformed(ActionEvent ae) {
          String name = getValue(AbstractAction.NAME).toString();
          //int value = vScrollBarModel.getValue();
          if (name.equals(UP)) {
            this.masterModel.processUpKeyInList();
          } else if (name.equals(DOWN)) {
            this.masterModel.processDownKeyInList();
          } else if (name.equals(NO_1)) {
            this.masterModel.chooseKanjiList();
          } else if (name.equals(NO_2)) {
            this.masterModel.chooseHiraganaList();
          } else if (name.equals(NO_3)) {
            this.masterModel.chooseHanVietList();
          } else if (name.equals(NO_4)) {
            this.masterModel.chooseMeaningList();
          } 

          if (!this.masterModel.isGameStarted()) {
            //these keys only active on waiting step
            //in testing step, key is for selecting words inside lists
            if (name.equals(START_KEY)) {
              this.masterModel.doStartGame();
            } else if (name.equals(LOAD_NORMAL_KEY)) {
              this.masterModel.doLoadNormalKanji();
            } else if (name.equals(LOAD_NEW_KEY)) {
              this.masterModel.doLoadNewKanji();
            } else if (name.equals(LOAD_NOTE_KEY)) {
              this.masterModel.doLoadNotedKanji();
            }

          }

     }
  }

  public JBGWordMatchPanel(JBGTerritoryManagerPanel p) {
    this.parent = p;
    //loadNormalKanji();
  }

  public boolean isGameStarted() {
    return this.bWordMatchStart;
  }

  public void loadNormalKanji() {
    this.kanjiList = parent.getNewKanjiList(false);
    if (this.lblStats != null)
      this.lblStats.setText(String.valueOf(parent.getTestStatistic()));
  }

  public void loadNewKanji() {
    this.kanjiList = parent.getNewKanjiList(true);    
    if (this.lblStats != null)
      this.lblStats.setText(String.valueOf(parent.getTestStatistic()));
  }

  public void loadNotedKanji() {
    if (this.lstProblematicWords.size() > 0) {
      this.kanjiList = parent.getSpecificKanjiList(this.lstProblematicWords);
    }
    else {
      btnLoadNotedKanji.setEnabled(false);
    }
    //not load statistic because we don't recalculate it in parent
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

  protected JLabel makeLabel(String name, JPanel panel, GridBagLayout gridbag, GridBagConstraints c, final boolean doubleWidth) {
     JLabel lbl = new JLabel(name);
     gridbag.setConstraints(lbl, c);
     setLabelSize(lbl, 200, doubleWidth?40:20);
     panel.add(lbl);
     return lbl;
  }

  private String getContentOfKanjiWord(final String kanji) {
    for (JBGKanjiItem item: this.kanjiList) {
      if (kanji.equals(item.getKanji())) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getKanji());
        sb.append("-");
        sb.append(item.getHiragana());
        sb.append("-");
        sb.append(item.getHv());
        sb.append("-");
        sb.append(item.getMeaning());
        return sb.toString();
      }
    }
    return "Not found";
  }

  private String[] isSelectedWordMatched(final String kanji, final String hira, final String hv, final String meaning) {
    String[] res = new String[]{kanji, MATCH_WORD_NG};
    for (JBGKanjiItem item: this.kanjiList) {
      if (kanji.equals(item.getKanji()) &&
          hira.equals(item.getHiragana()) &&
          hv.equals(item.getHv()) && 
          meaning.equals(item.getMeaning())
          ) {
            res[0] = item.getKanji();
            res[1] = MATCH_WORD_OK;
            break;
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

  public void chooseKanjiList() {
    iCurrentSelectedList = 1;
    kanjiListCtl.setSelectedIndex(0);
    kanjiListCtl.grabFocus();
  }

  public void chooseHiraganaList() {
    iCurrentSelectedList = 2;
    hiraListCtl.setSelectedIndex(0);
    hiraListCtl.grabFocus();
  }

  public void chooseHanVietList() {
    iCurrentSelectedList = 3;
    hvListCtl.setSelectedIndex(0);
    hvListCtl.grabFocus();
  }

  public void chooseMeaningList() {
    iCurrentSelectedList = 4;
    vnListCtl.setSelectedIndex(0);
    vnListCtl.grabFocus();
  }

  public void processUpKeyInList() {
    int iListIdx = -1;
    switch (iCurrentSelectedList) {
      case 1:
        iListIdx = kanjiListCtl.getSelectedIndex();
        if (iListIdx + 1 < kanjiListCtl.getModel().getSize()) {
          iListIdx += 1;
          kanjiListCtl.setSelectedIndex(iListIdx);
        }
        break;
      case 2:
        iListIdx = hiraListCtl.getSelectedIndex();
        if (iListIdx + 1 < hiraListCtl.getModel().getSize()) {
          iListIdx += 1;
          hiraListCtl.setSelectedIndex(iListIdx);
        }
        break;
      case 3:
        iListIdx = hvListCtl.getSelectedIndex();
        if (iListIdx + 1 < hvListCtl.getModel().getSize()) {
          iListIdx += 1;
          hvListCtl.setSelectedIndex(iListIdx);
        }
        break;
      case 4:
        iListIdx = vnListCtl.getSelectedIndex();
        if (iListIdx + 1 < vnListCtl.getModel().getSize()) {
          iListIdx += 1;
          vnListCtl.setSelectedIndex(iListIdx);
        }
        break;
    }
  }

  public void processDownKeyInList() {
    int iListIdx = -1;
    switch (iCurrentSelectedList) {
      case 1:
        iListIdx = kanjiListCtl.getSelectedIndex();
        if (iListIdx > 0) {
          iListIdx -= 1;
          kanjiListCtl.setSelectedIndex(iListIdx);
        }
        break;
      case 2:
        iListIdx = hiraListCtl.getSelectedIndex();
        if (iListIdx > 0) {
          iListIdx -= 1;
          hiraListCtl.setSelectedIndex(iListIdx);
        }
        break;
      case 3:
        iListIdx = hvListCtl.getSelectedIndex();
        if (iListIdx > 0) {
          iListIdx -= 1;
          hvListCtl.setSelectedIndex(iListIdx);
        }
        break;
      case 4:
        iListIdx = vnListCtl.getSelectedIndex();
        if (iListIdx > 0) {
          iListIdx -= 1;
          vnListCtl.setSelectedIndex(iListIdx);
        }
        break;
    }
  }

  private int removeItemFromList(JList lst, final int idx) {
    DefaultListModel model = (DefaultListModel) lst.getModel();
    model.remove(idx);
    return model.size();
  }

  private void noteWord(final String kanjiWord) {
    if (!this.lstProblematicWords.contains(kanjiWord))
      this.lstProblematicWords.add(kanjiWord);
  }

  private void unnoteWord(final String kanjiWord) {
    if (this.lstProblematicWords.contains(kanjiWord))
      this.lstProblematicWords.remove(kanjiWord);
  }

  public void showSneakpeek(final String kanjiWord) {
    if (!this.isGameStarted()) return;
    final String sVal = getContentOfKanjiWord(kanjiWord); 
    lblSneakpeek.setText(sVal);

    int totalJCoin = parent.getJCoin();
    if (totalJCoin > 0) totalJCoin--;
    parent.setJCoin(totalJCoin); //set back to parent
    lblJCoinAmount.setText(String.valueOf(totalJCoin));

    noteWord(kanjiWord);

  }

  public boolean validateKanjiSelection() {
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

    int totalJCoin = parent.getJCoin();

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

          //reward
          totalJCoin++;

          StringBuilder sb = new StringBuilder();
          sb.append(kanji);
          sb.append("-");
          sb.append(hira);
          sb.append("-");
          sb.append(hv);
          sb.append("-");
          sb.append(viet);
          lblSneakpeek.setText(sb.toString());

          //remove the correct matched word set from lists
          int iRemainingItems = removeItemFromList(kanjiListCtl, iKanjiSel);
          removeItemFromList(hiraListCtl, iHiraSel);
          removeItemFromList(hvListCtl, iHvSel);
          removeItemFromList(vnListCtl, iVnSel);

          if (iRemainingItems > 0) {
            //select first list to start a new word select flow
            chooseKanjiList();
          }
          else {
            //no more word
            doEndGame();
          }
        }
        else {
          //not correct!
          if (totalJCoin > 0) totalJCoin--;
          noteWord(kanji);
        }
        //update the statistic of word
        if (!updateWordStat(matchRes[0], matchRes[1])) {
          //can't update
          System.out.println("cannot update work of" + matchRes[0]);
        }
        parent.setJCoin(totalJCoin); //set back to parent
        lblJCoinAmount.setText(String.valueOf(totalJCoin));
        clearWordListSelection();

      }

    }

    return allFieldSet;
  }

  public boolean updateSelectedKanjis(final String sColName, final String value) {

    if (!isGameStarted()) return false;

    lblSneakpeek.setText(sWordMatchEmptyValue);

    switch (sColName) {
      case NAME_LIST_KANJI:
        lblSelKanji.setText(value);
        chooseHiraganaList();
        break;
      case NAME_LIST_HIRAGANA:
        lblSelHiragana.setText(value);
        chooseHanVietList();
        break;
      case NAME_LIST_HANVIET:
        lblSelHV.setText(value);
        chooseMeaningList();
        break;
      case NAME_LIST_MEANING:
        if (lblSelViet.getText().equals(value)) {
          //double enter on same item on this list, equals to SHIFT+ENTER
          validateKanjiSelection();
        }
        else {
          lblSelViet.setText(value);
        }
        break;
    }
    return true;//validateKanjiSelection();
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

  private void reloadList(final JList<String> lstObj, final String sColName) {
    DefaultListModel<String> listModel = (DefaultListModel<String>) lstObj.getModel();
    if (listModel.getSize() > 0)
      listModel.removeAllElements();
    if (this.kanjiList == null || this.kanjiList.size() < 1)
      return;

    for (JBGKanjiItem kanjiItem: this.kanjiList) {
      switch (sColName) {
      case NAME_LIST_KANJI:
        listModel.addElement(kanjiItem.getKanji());
        break;
      case NAME_LIST_HIRAGANA:
        listModel.addElement(kanjiItem.getHiragana());
        break;
      case NAME_LIST_HANVIET:
        listModel.addElement(kanjiItem.getHv());
        break;
      case NAME_LIST_MEANING:
        listModel.addElement(kanjiItem.getMeaning());
        break;
      }
    }
  }

  private void reloadAllLists() {
    reloadList(kanjiListCtl, NAME_LIST_KANJI);
    reloadList(hiraListCtl, NAME_LIST_HIRAGANA);
    reloadList(hvListCtl, NAME_LIST_HANVIET);
    reloadList(vnListCtl, NAME_LIST_MEANING);
  }

  protected JList<String> makeScrollList(final String sColName, JPanel panel, GridBagLayout gridbag, GridBagConstraints c, final int width) {
    DefaultListModel<String> listModel = new DefaultListModel<String>();

    //not shuffle here, let's user have a time to learn before shuffle (start Button)
    //listModel = shuffleListModel(listModel);

    final JList<String> lstObj = new JList<>(listModel); //data has type Object[]
    lstObj.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    lstObj.setSelectedIndex(-1);
    lstObj.setFont(new Font("Arial", Font.PLAIN, 15));
    lstObj.setVisibleRowCount(20);
    lstObj.setName(sColName);

    lstObj.addKeyListener(new KeyAdapter() {
         @Override
         public void keyReleased(KeyEvent ke) {
            JList list = (JList) ke.getSource();
            final String lstName = list.getName();
            JBGWordMatchPanel parent = JBGWordMatchPanel.this;
            final boolean bIsShift = ( (ke.getModifiers() & InputEvent.SHIFT_MASK) != 0 )?true:false;
            if(ke.getKeyCode() == KeyEvent.VK_ENTER && bIsShift) {
              parent.validateKanjiSelection();
            }
            else if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
              //System.out.println("SPACE key on list " + list.getName());
              Object selObj = list.getSelectedValue();
              if (selObj != null) {
                final String selVal = selObj.toString();
                //System.out.println(lstName + " " + selVal);
                parent.updateSelectedKanjis(lstName, selVal);
              }
            }
            else if (ke.getKeyCode() == KeyEvent.VK_SLASH && bIsShift) {
              //press question mark
              if (lstName.equals(NAME_LIST_KANJI)) {
                Object selObj = list.getSelectedValue();
                if (selObj != null) {
                    final String selVal = selObj.toString();
                    parent.showSneakpeek(selVal);
                  }
              }
            }

         }
      });

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
    //setPrototypeCellValue will prevent the list resize on values
    final String spacingWord = "AA";
    lstObj.setPrototypeCellValue(spacingWord.repeat(width));

    JScrollPane listScroller = new JScrollPane(lstObj);

    gridbag.setConstraints(listScroller, c);
    panel.add(listScroller);

    return lstObj;
  }

  private void doStartGame() {
    if (isGameStarted())
      return;

    shuffleAllListModels();
    btnLoadNormalKanji.setEnabled(false);
    btnLoadNewKanji.setEnabled(false);
    btnLoadNotedKanji.setEnabled(false);
    btnStartTest.setEnabled(false);
    clearWordListSelection();
    bWordMatchStart = true;
    chooseKanjiList();
  }

  private void doEndGame() {
    if (!isGameStarted())
      return;

    btnLoadNormalKanji.setEnabled(true);
    btnLoadNewKanji.setEnabled(true);
    btnLoadNotedKanji.setEnabled(true);
    btnStartTest.setEnabled(true);
    clearWordListSelection();
    bWordMatchStart = false;
  }

  private void doLoadNormalKanji() {
    if (!isGameStarted()) {
      loadNormalKanji();
      reloadAllLists();
      clearWordListSelection();
    }
  }

  private void doLoadNewKanji() {
    if (!isGameStarted()) {
      loadNewKanji();
      reloadAllLists();
      clearWordListSelection();
    }
  }

  private void doLoadNotedKanji() {
    if (!isGameStarted() && this.lstProblematicWords.size() > 0) {
      loadNotedKanji();
      reloadAllLists();
      clearWordListSelection();
    }
  }

  //JBG
  public JScrollPane makeKanjiGamePanel(
      final UiContext uiContext) {

    final JPanel panel = new JPanel();

    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();

    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    //panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setFont(new Font("Arial", Font.PLAIN, 15));
    panel.setLayout(gridbag);

    c.fill = GridBagConstraints.BOTH;

    c.fill = GridBagConstraints.HORIZONTAL; //natural height, maximum width
    c.weightx = 1.0; //spacing
    c.gridx = 0; //col
    c.gridy = 0; //row
    c.gridwidth = 2; //column span
    makeLabel("Kanji matching task", panel, gridbag, c, false);

    c.weightx = 1.0; //spacing
    c.gridx = 1; //col
    c.gridy = 0; //row
    c.gridwidth = 1; //column span
    this.lblStats = makeLabel("Stats:", panel, gridbag, c, false);

    c.weightx = 1.0; //spacing
    c.gridx = 2; //col
    c.gridy = 0; //row
    c.gridwidth = 1; //column span
    makeLabel("JCoin:", panel, gridbag, c, false);

    c.weightx = 1.0; //spacing
    c.gridx = 3; //col
    c.gridy = 0; //row
    c.gridwidth = 1; //column span
    this.lblJCoinAmount = makeLabel(String.valueOf(parent.getJCoin()), panel, gridbag, c, true);
    this.lblJCoinAmount.setFont(new Font("Arial", Font.BOLD, 17));
    this.lblJCoinAmount.setForeground(Color.BLUE);

    c.weightx = 1.0;
    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 4;
    makeLabel("Selected parts:", panel, gridbag, c, false);

    c.gridx = 0;
    c.gridy = 2;
    c.gridwidth = 1;
    this.lblSelKanji = makeLabel(sWordMatchEmptyValue, panel, gridbag, c, true);
    this.lblSelKanji.setFont(new Font("Arial", Font.PLAIN, 20));
 
    c.gridx = 1;
    this.lblSelHiragana = makeLabel(sWordMatchEmptyValue, panel, gridbag, c, true);
    this.lblSelHiragana.setFont(new Font("Arial", Font.PLAIN, 17));
    c.gridx = 2;
    this.lblSelHV = makeLabel(sWordMatchEmptyValue, panel, gridbag, c, true);
    this.lblSelHV.setFont(new Font("Arial", Font.PLAIN, 17));
    c.gridx = 3;
    this.lblSelViet = makeLabel(sWordMatchEmptyValue, panel, gridbag, c, true);
    this.lblSelViet.setFont(new Font("Arial", Font.PLAIN, 17));

    c.weightx = 1.0;
    c.gridx = 0;
    c.gridy = 3;
    c.gridwidth = 1;
    this.kanjiListCtl = makeScrollList(NAME_LIST_KANJI, panel, gridbag, c, 1);

    c.weightx = 1.0;
    c.gridx = 1;
    c.gridwidth = 1;
    this.hiraListCtl = makeScrollList(NAME_LIST_HIRAGANA, panel, gridbag, c, 6);

    c.weightx = 1.0;
    c.gridx = 2;
    c.gridwidth = 1;
    this.hvListCtl = makeScrollList(NAME_LIST_HANVIET, panel, gridbag, c, 15);

    c.weightx = 1.0;
    c.gridx = 3;
    c.gridwidth = 1;
    this.vnListCtl = makeScrollList(NAME_LIST_MEANING, panel, gridbag, c, 15);

    ActionListener loadNormalKanjiListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doLoadNormalKanji();
      }
    };

    ActionListener loadNewKanjiListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doLoadNewKanji();
      }
    };

    ActionListener startListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doStartGame();
      }
    };

    ActionListener loadNotedListenerFnc = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doLoadNotedKanji();
      }
    };

    c.gridy = 4;
    c.gridx = 0;
    c.gridwidth = 1;
    btnLoadNormalKanji = makeButtonWithAction("(L)正常漢字", panel, gridbag, c, loadNormalKanjiListenerFnc);
    c.gridx = 1;
    c.gridwidth = 1;
    btnLoadNewKanji = makeButtonWithAction("(N)新漢字", panel, gridbag, c, loadNewKanjiListenerFnc);
    c.gridx = 2;
    c.gridwidth = 1;
    btnLoadNotedKanji = makeButtonWithAction("(O)問題の言葉", panel, gridbag, c, loadNotedListenerFnc);
    c.gridx = 3;
    c.gridwidth = 1;
    btnStartTest = makeButtonWithAction("(S)開始", panel, gridbag, c, startListenerFnc);

    c.weightx = 1.0;
    c.gridx = 0;
    c.gridy = 5;
    c.gridwidth = 4;
    this.lblSneakpeek = makeLabel(sWordMatchEmptyValue, panel, gridbag, c, true);
    this.lblSneakpeek.setFont(new Font("Arial", Font.PLAIN, 17));

    final JScrollPane unitsPane = new JScrollPane();
    unitsPane.setBorder(BorderFactory.createEmptyBorder());
    unitsPane.getVerticalScrollBar().setUnitIncrement(1);
    unitsPane.setViewportView(panel);

    InputMap inMap = unitsPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap actMap = unitsPane.getActionMap();

    inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), NO_1);
    inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_2, 0), NO_2);
    inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_3, 0), NO_3);
    inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_4, 0), NO_4);
    inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), START_KEY);
    inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, 0), LOAD_NORMAL_KEY);
    inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), LOAD_NEW_KEY);
    inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, 0), LOAD_NOTE_KEY);

    inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), UP);
    inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), DOWN);

    actMap.put(UP, new ListKeyAction(UP, this));
    actMap.put(DOWN, new ListKeyAction(DOWN, this));
    actMap.put(NO_1, new ListKeyAction(NO_1, this));
    actMap.put(NO_2, new ListKeyAction(NO_2, this));
    actMap.put(NO_3, new ListKeyAction(NO_3, this));
    actMap.put(NO_4, new ListKeyAction(NO_4, this));
    actMap.put(START_KEY, new ListKeyAction(START_KEY, this));
    actMap.put(LOAD_NORMAL_KEY, new ListKeyAction(LOAD_NORMAL_KEY, this));
    actMap.put(LOAD_NEW_KEY, new ListKeyAction(LOAD_NEW_KEY, this));
    actMap.put(LOAD_NOTE_KEY, new ListKeyAction(LOAD_NOTE_KEY, this));

    return unitsPane;
  }

}