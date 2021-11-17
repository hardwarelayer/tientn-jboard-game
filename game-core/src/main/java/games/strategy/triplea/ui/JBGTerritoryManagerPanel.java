package games.strategy.triplea.ui;

import static games.strategy.triplea.image.UnitImageFactory.ImageKey;
import games.strategy.triplea.ui.UiContext;
import games.strategy.triplea.ui.TooltipProperties;
import games.strategy.engine.data.JBGConstants;

import com.google.common.collect.ImmutableList;
import com.google.common.base.Splitter;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameStep;
import games.strategy.triplea.ui.mapdata.MapData;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.framework.system.SystemProperties;
import games.strategy.triplea.ui.history.JBGTurnLogPaper;
import games.strategy.engine.data.JBGKanjiItem;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.TerritoryEffect;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.data.Change;
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


import games.strategy.triplea.ui.JBGKanjiUnits;

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
import javax.swing.UIManager;

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
import games.strategy.engine.history.History;
import games.strategy.triplea.ai.jbg.JBGGamePlayerExtInfo;
import games.strategy.triplea.Constants;
import games.strategy.engine.data.Resource;

/** Orchestrates the rendering of all map tiles. */
public class JBGTerritoryManagerPanel  extends ActionPanel {

  private static final long serialVersionUID = 5004515340964828564L;
  @Getter private final UiContext uiContext;

  @Getter private List<JBGKanjiItem> kanjiList = null;
  @Getter private String testStatistic = null;
  @Getter private boolean lastBattleEffect = false;

  private Territory territoryRef = null;

  private int iEcoLevel = 0;
  private int iResLevel = 0;
  private int iProdLevel = 0; 
  private int iJCoin = 0;
  private String sResearchBuildingList = "";
  private String sEconomyBuildingList = "";

  private JBGTerritoryViewCanvasPanel territoryCanvasPanel = null;
  private JTextField jModify_EcoLevel;
  private JTextField jModify_ResLevel;
  private JTextField jModify_ProdLevel;
  private JTextField jModify_ResBuildingList;
  private JTextField jModify_EcoBuildingList;

  String sHumanPlayerName;
  Map<String, JBGGamePlayerExtInfo> aiInteracts;

  public JBGTerritoryManagerPanel(final GameData data, final MapPanel map, final UiContext uiContext) {
    super(data, map);
    this.uiContext = uiContext;

    data.initPlayerExtendedInfo(); //should be called before getHumanPlayerName() and getJBGAiInterract()

    //some public function will be called from outside
    //and they can't access data directly
    sHumanPlayerName = data.getHumanPlayerName();
    aiInteracts = data.getJBGAiInterracts();
    //actually, the issue is solved in function increasePUs()
  }

  public List<JBGKanjiItem> getNewKanjiList(final boolean bNewKanjis) {
    GameData tData = getData();
    if (!bNewKanjis)
      this.kanjiList = JBGKanjiUnits.getInstance(tData).getData();
    else
      this.kanjiList = JBGKanjiUnits.getInstance(tData).getNewData();
    this.testStatistic = String.format("%d/%d %d", 
      JBGKanjiUnits.getInstance(tData).getTotalMatchedKanjis(),
      JBGKanjiUnits.getInstance(tData).getTotalKanjis(),
      JBGKanjiUnits.getInstance(tData).getTotalKanjiTests()
      );
    return this.kanjiList;
  }

  public List<JBGKanjiItem> getSpecificKanjiList(ArrayList<String> lstKanjiWords) {
    GameData tData = getData();
    this.kanjiList = JBGKanjiUnits.getInstance(tData).getSpecificData(lstKanjiWords);
    this.testStatistic = String.format("%d/%d %d", 
      JBGKanjiUnits.getInstance(tData).getTotalMatchedKanjis(),
      JBGKanjiUnits.getInstance(tData).getTotalKanjis(),
      JBGKanjiUnits.getInstance(tData).getTotalKanjiTests()
      );
    return this.kanjiList;
  }

  @Override
  public void performDone() {
    //techRoll = null;
    release();
  }

  @Override
  public String toString() {
    return "TerritoryManagerPanel";
  }

  /*
  public boolean hasFactory() {
    if (territoryRef == null) return false;

    final TerritoryAttachment ta = TerritoryAttachment.get(territoryRef);
    final Predicate<Unit> factoryMatch =
        Matches.unitIsOwnedAndIsFactoryOrCanProduceUnits(player)
            .and(Matches.unitIsBeingTransported().negate())
            .and(territoryRef.isWater() ? Matches.unitIsLand().negate() : Matches.unitIsSea().negate());
    final Collection<Unit> factoryUnits = territoryRef.getUnitCollection().getMatches(factoryMatch);
    if (factoryUnits.isEmpty())
      return false;
    return true;
  }
  */

  public boolean factoryInUnitList(Territory t) {
    if (t == null) return false;

    final Collection<Unit> factoryUnits =
        t.getUnitCollection().getMatches(Matches.unitCanProduceUnits());
    if (factoryUnits.isEmpty()) {
      return false;
    }
    return true;
  }
  
  private boolean isUnitBunker(final String name, UnitAttachment uat, GamePlayer pl) {
      if (name.equals("Bunker"))
        return true;
      boolean isAir = uat.getIsAir();
      boolean isSea = uat.getIsSea();
      boolean isLand = false;
      if (!isAir && !isSea)
        isLand = true;
      boolean isInfra = uat.getIsInfrastructure();

      final int attack = uat.getAttack(pl);
      final int defense = uat.getDefense(pl);
      final int movement = uat.getMovement(pl);

      if (attack < 1 && movement < 1 && defense > 0) {
        if (isLand && !isInfra) {
          return true;
        }
      }
      return false;    
  }

  public int countBunkerInUnitList(Territory t, GamePlayer pl) {
    final Collection<Unit> consUnits = t.getUnitCollection();//.getMatches(Matches.unitIsConstruction());
    //System.out.println("Unit listing: ");
    int iTotal = 0;
    for (Unit unit: consUnits) {
      UnitAttachment uat = unit.getUnitAttachment();
      if (isUnitBunker(unit.toStringNoOwner(), uat, pl))
        iTotal++;
      //String s = uat.toStringShortAndOnlyImportantDifferences(pl);
      //System.out.println(s);
    }
    return iTotal;
  }

  public int getArmyUnits(Territory t, GamePlayer pl) {
    final Collection<Unit> consUnits = t.getUnitCollection();//.getMatches(Matches.unitIsConstruction());
    //System.out.println("Unit listing: ");
    int iTotal = 0;
    for (Unit unit: consUnits) {
      UnitAttachment uat = unit.getUnitAttachment();
      if (isUnitBunker(unit.toStringNoOwner(), uat, pl))
        iTotal++;
      //String s = uat.toStringShortAndOnlyImportantDifferences(pl);
      //System.out.println(s);
    }
    return iTotal;
  }

  public int getJCoin() {
    GameData gd = this.getData();
    if (gd != null) {
      try {
        gd.acquireReadLock();
        this.iJCoin = gd.getJCoinAmount();
      }
      finally {
        gd.releaseReadLock();
      }
    }
    return this.iJCoin;
  }

  public void setJCoin(final int amt) {
    this.iJCoin = amt;
    GameData gd = this.getData();
    if (gd != null) {
      try {
        gd.acquireWriteLock();
        gd.setJCoinAmount(amt);
      }
      finally {
        gd.releaseWriteLock();
      }
    }
  }

  public int showKanjiMatchGameDialog(final Component parent) {

    JBGWordMatchPanel panel = new JBGWordMatchPanel(this);
    JScrollPane p = panel.makeKanjiGamePanel(this.uiContext);
    final int kGame =
        JOptionPane.showOptionDialog(
            parent,
            p,
            "WordMatch for JCoin",
            JOptionPane.CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            new Object[]{"Close WordMatch"}, //disable default focus
            null);

    if (territoryCanvasPanel != null) {
      territoryCanvasPanel.updateInfo(this.territoryRef.getName(),  
        this.sResearchBuildingList, this.sEconomyBuildingList,
        this.iEcoLevel, this.iResLevel, this.iProdLevel, getJCoin(),
        this.lastBattleEffect,
        makeBasicTerritoryString(this.territoryRef));
    }

    return 0;

  }

  private void getTerritoryBasicInfo(final Territory t) {

    this.territoryRef = t;

    final TerritoryAttachment ta = TerritoryAttachment.get(t);
    this.iEcoLevel = 0;
    this.iResLevel = 0;
    this.iProdLevel = 0;
    this.sEconomyBuildingList = "";
    this.sResearchBuildingList = "";

    this.iJCoin = getJCoin();

    if (ta != null) {
      this.iEcoLevel = ta.getEconomyLevel();
      this.iResLevel = ta.getResearchLevel();
      this.iProdLevel = ta.getProduction();
      this.sEconomyBuildingList = ta.getEconomyBuildingList();
      this.sResearchBuildingList = ta.getResearchBuildingList();

      GameData tData = getData();
      int iCurrentInternalTurn = tData.getJbgInternalTurnStep();
      this.lastBattleEffect = ta.isLastBattleInEffect(iCurrentInternalTurn);
    }
    else {
      this.iEcoLevel = TerritoryAttachment.getEconomyLevel(t);
      this.iResLevel = TerritoryAttachment.getResearchLevel(t);
      this.iProdLevel = TerritoryAttachment.getProduction(t);
      this.sEconomyBuildingList = TerritoryAttachment.getEconomyBuildingList(t);
      this.sResearchBuildingList = TerritoryAttachment.getResearchBuildingList(t);
    }
  }

  private void setTerritoryBasicInfo(final Territory t, final int iEco, final int iRes, final int iProd, final String sResBuild, final String sEcoBuild) {

    this.territoryRef = t;

    this.iEcoLevel = iEco;
    this.iResLevel = iRes;
    this.iProdLevel = iProd;
    this.sEconomyBuildingList = sEcoBuild;
    this.sResearchBuildingList = sResBuild;
    getJCoin();

    saveInfoToAttachment();
  }

  private void saveInfoToAttachment() {
    if (this.territoryRef == null) return;

    final TerritoryAttachment ta = TerritoryAttachment.get(this.territoryRef);

    if (ta != null) {
      ta.manualSetProduction(String.valueOf(this.iProdLevel));
      ta.manualSetEconomyLevel(String.valueOf(this.iEcoLevel));
      ta.manualSetResearchLevel(String.valueOf(this.iResLevel));
      ta.manualSetResearchBuildingList(this.sResearchBuildingList);
      ta.manualSetEconomyBuildingList(this.sEconomyBuildingList);
    }
    else {
      TerritoryAttachment.manualSetProduction(this.territoryRef, String.valueOf(this.iProdLevel));
      TerritoryAttachment.manualSetEconomyLevel(this.territoryRef, String.valueOf(this.iEcoLevel));
      TerritoryAttachment.manualSetResearchLevel(this.territoryRef, String.valueOf(this.iResLevel));
      TerritoryAttachment.manualSetResearchBuildingList(this.territoryRef, this.sResearchBuildingList);
      TerritoryAttachment.manualSetEconomyBuildingList(this.territoryRef, this.sEconomyBuildingList);
    }

  }

  public void buildNewspaper(final Component parent) {
    GameData gameData = this.getData();
    try {
      gameData.acquireReadLock();
      JBGTurnLogPaper turnLog = new JBGTurnLogPaper(parent);
      turnLog.showNewsDialog(gameData);
    } finally {
      gameData.releaseReadLock();
//System.out.println("Resetting history ...... ");
//      gameData.resetHistory(); //will cause history node error
    }

  }

  private void updateModifiableFields() {
    if (jModify_EcoLevel == null) return;

    jModify_EcoLevel.setText(String.valueOf(this.iEcoLevel));
    jModify_ResLevel.setText(String.valueOf(this.iResLevel));
    jModify_ProdLevel.setText(String.valueOf(this.iProdLevel));
    jModify_ResBuildingList.setText(this.sResearchBuildingList);
    jModify_EcoBuildingList.setText(this.sEconomyBuildingList);
  }

  public void showModifyJBGTerritoryInfo(
      final Territory t, final MouseDetails mouseDetails) {

      String sTerritoryName = "???";
      if (t != null) {
        sTerritoryName = t.getName();
      }
      final String text = "Land of " + sTerritoryName;


      getTerritoryBasicInfo(t);

      final JScrollPane unitsPane = new JScrollPane();
      jModify_EcoLevel = new JTextField(3);
      jModify_ResLevel = new JTextField(3);
      jModify_ProdLevel = new JTextField(3);
      jModify_ResBuildingList = new JTextField(20);
      jModify_EcoBuildingList = new JTextField(20);

      updateModifiableFields();

      final JPanel editPanel = valueModifyInTerritoryPanel(
        jModify_EcoLevel, jModify_ResLevel, jModify_ProdLevel, jModify_ResBuildingList, jModify_EcoBuildingList);

      UIManager.put("OptionPane.border", new EmptyBorder(1, 1, 1, 1) );

      unitsPane.getVerticalScrollBar().setUnitIncrement(20);
      final JPanel terPanel = makeInterractiveTerritoryPanel(t, editPanel, uiContext);
      unitsPane.setViewportView(terPanel);

      final int option =
          JOptionPane.showOptionDialog(
              JOptionPane.getFrameForComponent(JBGTerritoryManagerPanel.this),
              unitsPane,
              text,
              JOptionPane.DEFAULT_OPTION,
              JOptionPane.PLAIN_MESSAGE,
              null,
              new Object[]{}, //no button at all!
              null);
      if (option != JOptionPane.OK_OPTION) {
        return;
      }

      final String sEcoVal = jModify_EcoLevel.getText();
      final String sResVal = jModify_ResLevel.getText();
      final String sProdVal = jModify_ProdLevel.getText();
      final String sResBuildingVal = jModify_ResBuildingList.getText();
      final String sEcoBuildingVal = jModify_EcoBuildingList.getText();
      setTerritoryBasicInfo(t, Integer.parseInt(sEcoVal), Integer.parseInt(sResVal), Integer.parseInt(sProdVal), 
        sResBuildingVal, sEcoBuildingVal);
  }

  public void showAlert(final Component p, final String infoMessage) {
      JOptionPane.showMessageDialog(p, infoMessage, "JBG: ", JOptionPane.INFORMATION_MESSAGE);
  }

  private void setBuildingSlot(final int type, final List<Integer> lstVals, final boolean enable) {
    if (type == 0) {
      String[] arrEcoBlds = this.sEconomyBuildingList.split("\\|", JBGConstants.MAP_HORZ_TILES);
      for (int i = 0; i < lstVals.size(); i++)
        arrEcoBlds[lstVals.get(i)] = "1";
      this.sEconomyBuildingList = String.join("|", arrEcoBlds);
    }
    else {
      String[] arrResBlds = this.sResearchBuildingList.split("\\|", JBGConstants.MAP_HORZ_TILES);
      for (int i = 0; i < lstVals.size(); i++)
        arrResBlds[lstVals.get(i)] = "1";
      this.sResearchBuildingList = String.join("|", arrResBlds);
    }
  }

  public void openTributeDialog(final Component parent) {
    System.out.println("Openning tribute ...");

    StringBuilder sb = new StringBuilder();
    for (String name: aiInteracts.keySet()) {
      if (!name.equals(sHumanPlayerName))
        sb.append(name).append("|");
    }
    String sPlayers = sb.toString();
    JBGTributePanel purObj = new JBGTributePanel(
      this,
      sPlayers,
      this.iJCoin
      ); 
    purObj.setJCoin(this.iJCoin);
    JScrollPane p = purObj.makePanel(this.uiContext);

    JOptionPane.showOptionDialog(
            parent,
            p,
            "Tribute to other player",
            JOptionPane.CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            new Object[]{}, //no button at all!
            null);

    if (!purObj.isValidated()) return;
    String targetPlayer = purObj.getTargetPlayer();
    int tributeAmount = purObj.getTributeAmount();

    if (aiInteracts.containsKey(targetPlayer)) {
      if (tributeAmount == iJCoin) {
        setJCoin(this.iJCoin - tributeAmount);
        aiInteracts.get(targetPlayer).setTributeAmount(tributeAmount);
        if (territoryCanvasPanel != null) {
          territoryCanvasPanel.updateInfo( getJCoin() );
        }
      }
    }
  }

  public void openExchangeJCoinDialog(final Component parent) {
    StringBuilder sb = new StringBuilder();
    for (String name: aiInteracts.keySet()) {
      if (!name.equals(sHumanPlayerName))
        sb.append(name).append("|");
    }
    String sPlayers = sb.toString();
    int iTotalPUs = getPUsAmount();
    JBGExchangeJCoinPanel purObj = new JBGExchangeJCoinPanel(
      this,
      this.iJCoin,
      iTotalPUs
      ); 
    purObj.setJCoin(this.iJCoin);
    JScrollPane p = purObj.makePanel(this.uiContext);

    JOptionPane.showOptionDialog(
            parent,
            p,
            "Exchange jCoin",
            JOptionPane.CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            new Object[]{}, //no button at all!
            null);

    if (!purObj.isValidated()) return;
    int exchangeAmount = purObj.getExchangeAmount();
    boolean exchangeDirection = purObj.isXchgJCoin2PUs();

    if (exchangeDirection) {
      if (exchangeAmount == iJCoin) {
        final int iExchangeValue = (int) (exchangeAmount / 2); //jCoin->PUs: only 50%
        setJCoin(this.iJCoin - exchangeAmount);
        changePUs(iExchangeValue);
        if (territoryCanvasPanel != null) {
          territoryCanvasPanel.updateInfo( getJCoin() );
        }
      }
    }
    else {
      if (exchangeAmount == iTotalPUs) {
        final int iExchangeValue = (int) (exchangeAmount / 10); //PUs->jCoin: only 10%
        setJCoin(this.iJCoin + iExchangeValue);
        changePUs(0-exchangeAmount);
        if (territoryCanvasPanel != null) {
          territoryCanvasPanel.updateInfo( getJCoin() );
        }
      }
    }
  }

  private int getPUsAmount() {
    GameData pData = getData();
    final GamePlayer player = pData.getSequence().getStep().getPlayerId();
    final Resource pus = pData.getResourceList().getResource(Constants.PUS);
    return player.getResources().getQuantity(pus);
  }

  private void changePUs(int amount) {
    //we can't use data directly here because data is set to private, and we are calling from outside
    //we'll get the error: data has private access in JAction ... etc.
    //we have to get the data by getData, first
    GameData pData = getData();
    final GamePlayer player = pData.getSequence().getStep().getPlayerId();
    final Change change =
        ChangeFactory.changeResourcesChange(
            player, pData.getResourceList().getResource(Constants.PUS), amount);
    pData.performChange(change);

  }

  public void openTerritoryBuild(final Component parent) {

    if (this.territoryRef == null) return;

    JBGTerritoryBuildingPurchasePanel purObj = new JBGTerritoryBuildingPurchasePanel(
      this,
      this.sEconomyBuildingList,
      this.sResearchBuildingList,
      this.iJCoin
      ); 
    purObj.setJCoin(this.iJCoin);
    JScrollPane p = purObj.makePanel(this.uiContext);

    String[] options = {"閉じる"};
    JOptionPane.showOptionDialog(
            parent,
            p,
            "地域建設(ちいき けんせつ)",
            JOptionPane.CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            options[0]);

    if (!purObj.isValidated()) return;

    int iSpent = purObj.getSpendJCoin();
    if (iSpent <= this.iJCoin) {
      List<Integer> lstEco = purObj.getSelectedEcoList();
      List<Integer> lstRes = purObj.getSelectedResList();
      setBuildingSlot(0, lstEco, true);
      setBuildingSlot(1, lstRes, true);

      setJCoin(this.iJCoin - iSpent);

      this.iEcoLevel += lstEco.size();
      this.iResLevel += lstRes.size();
      this.iProdLevel += (lstEco.size() + lstRes.size());

      updateModifiableFields();
      saveInfoToAttachment();

      if (territoryCanvasPanel != null) {
        territoryCanvasPanel.updateInfo(
          this.territoryRef.getName(),
          this.sResearchBuildingList, this.sEconomyBuildingList,
          this.iEcoLevel, this.iResLevel, this.iProdLevel, getJCoin(),
          this.lastBattleEffect,
          makeBasicTerritoryString(this.territoryRef)
          );
      }
    }
    else {
      this.showAlert(parent, "jCoinが足りないので買えない！！！");
      //System.out.println("Can't buy, not enough money!");
    }

  }

  //JBG
  private JPanel valueModifyInTerritoryPanel(Component objEco, Component objRes, Component objProd, Component objResBuildings, Component objEcoBuildings) {
    final JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    final JLabel lEcoLevel = new JLabel();
    final JLabel lResLevel = new JLabel();
    final JLabel lProdLevel = new JLabel();
    final JLabel lResBuildingList = new JLabel("Research Buildings:");
    final JLabel lEcoBuildingList = new JLabel("Economy Buildings:");

    lEcoLevel.setText("Economy Set:");
    lResLevel.setText("Research Set:");
    lProdLevel.setText("Production Set:");
    panel.add(lEcoLevel);
    panel.add(objEco);
    panel.add(lResLevel);
    panel.add(objRes);
    panel.add(lProdLevel);
    panel.add(objProd);
    panel.add(lResBuildingList);
    panel.add(objResBuildings);
    panel.add(lEcoBuildingList);
    panel.add(objEcoBuildings);

    return panel;

  }

  private void printMapArray(List<JBGConstants.Tile> map) {
    int iTCol = 0;
    for (int ii = 0; ii < map.size(); ii++) {
      System.out.print(map.get(ii));
      iTCol++;
      if (iTCol < JBGConstants.MAP_HORZ_TILES) {
        System.out.print(" ");
      }
      else {
        System.out.println(" ");
        iTCol = 0;
      }
    }
    System.out.println("END");
  }

  private String makeBasicTerritoryString(final Territory territory) {
    final TerritoryAttachment ta = TerritoryAttachment.get(this.territoryRef);
    if (ta == null)
      return String.format("%s<br>領海(りょうかい)", territory.getName());
    return String.format("%s", ta.toStringForInfo(true, true));
  }

  //JBG
  private JPanel makeInterractiveTerritoryPanel(
      final Territory territory, 
      final Component cusObject,
      final UiContext uiContext) {

    final TerritoryAttachment ta = TerritoryAttachment.get(territory);

    getTerritoryBasicInfo(territory);

    final JPanel panel = new JPanel();
    panel.setBorder(new EmptyBorder(0, 0, 0, 0));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    final List<UnitCategory> units =
        UnitSeparator.getSortedUnitCategories(territory, uiContext.getMapData());
    @Nullable GamePlayer currentPlayer = null;

    //tabbed section
    final JTabbedPane tabs = new JTabbedPane();
    panel.add(
        tabs,
        new GridBagConstraints(
            0,
            0,
            0,0,0,0, //250, 250, 250, 250
            GridBagConstraints.WEST,
            GridBagConstraints.BOTH,
            new Insets(0, 0, 0, 0),
            0,
            0));

    JPanel territoryViewPanel = new JPanel();
    territoryViewPanel.setBorder(new EmptyBorder(0,0,0,0));
/*
    final JLabel logoLabel =
      new JLabel(
          new ImageIcon(
              ResourceLoader.loadImageAssert(Path.of("launch_screens", "triplea-logo.png"))));
    territoryViewPanel.add(logoLabel);
*/

    boolean flgFactory = factoryInUnitList(territory);

    this.territoryCanvasPanel = new JBGTerritoryViewCanvasPanel(this);
    territoryCanvasPanel.updateInfo(territory.getName(),  
        sResearchBuildingList, sEconomyBuildingList,
        iEcoLevel, iResLevel, iProdLevel, getJCoin(), 
        this.lastBattleEffect,
        makeBasicTerritoryString(territory));

    territoryViewPanel.add(territoryCanvasPanel);
    //territoryViewPanel.add(new JLabel("This is city skyline"));

    tabs.addTab("Territory View", new JScrollPane(territoryViewPanel));

    JPanel roomsPanel2 = new JPanel();
    roomsPanel2.add(new JLabel("This is tab2"));
    tabs.addTab("Edit", new JScrollPane(roomsPanel2));


    final JLabel territoryInfo = new JLabel();
    final JLabel unitInfo = new JLabel();
    final String labelText;
    if (ta == null) {
      labelText = "<html>" + territory.getName() + "<br>領海(りょうかい)" + "<br><br></html>";
    } else {
      labelText = "<html>" + ta.toStringForInfo(true, true) + "<br></html>";
    }
    territoryInfo.setText(labelText);
    unitInfo.setText(
        "Units: "
            + territory.getUnits().stream()
                .filter(u -> uiContext.getMapData().shouldDrawUnit(u.getType().getName()))
                .count());

    JPanel infoSection = new JPanel();
    infoSection.add(territoryInfo);

    JPanel unitSection = new JPanel();
    //set it smaller, and the canvas will expand it. If we don't set size, if many icons available, the width of this tab will be expanded
    unitSection.setPreferredSize(new Dimension(300, 300));
    unitSection.add(unitInfo);

    for (final UnitCategory item : units) {
      // separate players with a separator
      if (!item.getOwner().equals(currentPlayer)) {
        currentPlayer = item.getOwner();
        unitSection.add(Box.createVerticalStrut(15));
      }

      String itemType = item.getType().getName();
      //System.out.println(itemType + " " + String.valueOf(item.getUnits().size()));

      final Optional<ImageIcon> unitIcon =
          uiContext.getUnitImageFactory().getIcon(ImageKey.of(item));
      if (unitIcon.isPresent()) {
        // overlay flag onto upper-right of icon
        final ImageIcon flagIcon =
            new ImageIcon(uiContext.getFlagImageFactory().getSmallFlag(item.getOwner()));
        final Icon flaggedUnitIcon =
            new OverlayIcon(
                unitIcon.get(),
                flagIcon,
                unitIcon.get().getIconWidth() - (flagIcon.getIconWidth() / 2),
                0);
        final JLabel label =
            new JLabel("x" + item.getUnits().size(), flaggedUnitIcon, SwingConstants.LEFT);
        final String toolTipText =
            "<html>"
                + item.getType().getName()
                + ": "
                + TooltipProperties.getInstance().getTooltip(item.getType(), currentPlayer)
                + "</html>";
        label.setToolTipText(toolTipText);
        unitSection.add(label);
      }
    }

    territoryCanvasPanel.setUnits(units);

    //
    
    int iBunkerTtl = countBunkerInUnitList(territory, currentPlayer);
    territoryCanvasPanel.setBunkerTotal(iBunkerTtl);
    territoryCanvasPanel.setFactoryExists(flgFactory);

    tabs.addTab("Info", infoSection);
    tabs.addTab("Build", cusObject);
    tabs.addTab("Units", unitSection);

    return panel;
  }


}

