package games.strategy.triplea.ai.jbg;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameDataEvent;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.engine.framework.GameDataUtils;
import games.strategy.triplea.Properties;
import games.strategy.triplea.ai.AbstractAi;
import games.strategy.triplea.ai.jbg.data.JBGBattleResult;
import games.strategy.triplea.ai.jbg.data.JBGPurchaseTerritory;
import games.strategy.triplea.ai.jbg.data.JBGTerritory;
import games.strategy.triplea.ai.jbg.logging.JBGLogUi;
import games.strategy.triplea.ai.jbg.logging.JBGLogger;
import games.strategy.triplea.ai.jbg.simulate.JBGDummyDelegateBridge;
import games.strategy.triplea.ai.jbg.simulate.JBGSimulateTurnUtils;
import games.strategy.triplea.ai.jbg.util.JBGBattleUtils;
import games.strategy.triplea.ai.jbg.util.JBGMatches;
import games.strategy.triplea.ai.jbg.util.JBGOddsCalculator;
import games.strategy.triplea.ai.jbg.util.JBGPurchaseUtils;
import games.strategy.triplea.ai.jbg.util.JBGTransportUtils;
import games.strategy.triplea.attachments.PoliticalActionAttachment;
import games.strategy.triplea.delegate.DelegateFinder;
import games.strategy.triplea.delegate.DiceRoll;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.PoliticsDelegate;
import games.strategy.triplea.delegate.battle.BattleDelegate;
import games.strategy.triplea.delegate.battle.IBattle;
import games.strategy.triplea.delegate.battle.IBattle.BattleType;
import games.strategy.triplea.delegate.data.CasualtyDetails;
import games.strategy.triplea.delegate.data.CasualtyList;
import games.strategy.triplea.delegate.remote.IAbstractPlaceDelegate;
import games.strategy.triplea.delegate.remote.IMoveDelegate;
import games.strategy.triplea.delegate.remote.IPurchaseDelegate;
import games.strategy.triplea.delegate.remote.ITechDelegate;
import games.strategy.triplea.odds.calculator.IBattleCalculator;
import games.strategy.triplea.ui.TripleAFrame;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.triplea.injection.Injections;
import org.triplea.java.collections.CollectionUtils;
import org.triplea.util.Tuple;

//JBG
import com.google.common.collect.Streams;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.NamedAttachable;
import games.strategy.engine.data.ProductionRule;
import games.strategy.engine.data.RepairRule;
import games.strategy.engine.data.Resource;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.UnitType;
import games.strategy.engine.framework.startup.ui.PlayerType;
import games.strategy.triplea.Constants;
import games.strategy.triplea.UnitUtils;
import games.strategy.triplea.ai.TienAbstractAi;
import games.strategy.triplea.ai.AiUtils;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.TransportTracker;
import games.strategy.triplea.delegate.data.PlaceableUnits;
import games.strategy.triplea.delegate.PurchaseDelegate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.triplea.java.collections.IntegerMap;
import games.strategy.triplea.delegate.AbstractPlaceDelegate;
import games.strategy.engine.data.JBGConstants;

//


import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;


/** JBG AI. */
public abstract class AbstractJBGAi extends AbstractAi {

  //JBG
  protected boolean notCareAboutCost = false;
  int lastCombatTimeBySeconds = 0;
  int lastNoneCombatTimeBySeconds = 0;
  //

  private final JBGOddsCalculator calc;
  @Getter private final JBGData jbgData;
  public final JBGData getJBGData() {return jbgData;} //because lombok generate getJbgData() instead

  // Phases
  private final JBGCombatMoveAi combatMoveAi;
  private final JBGNonCombatMoveAi nonCombatMoveAi;
  private final JBGPurchaseAi purchaseAi;
  private final JBGRetreatAi retreatAi;
  private final JBGScrambleAi scrambleAi;
  private final JBGPoliticsAi politicsAi;

  // Data shared across phases
  private Map<Territory, JBGTerritory> storedCombatMoveMap;
  private Map<Territory, JBGTerritory> storedFactoryMoveMap;
  private Map<Territory, JBGPurchaseTerritory> storedPurchaseTerritories;
  private List<PoliticalActionAttachment> storedPoliticalActions;
  private List<Territory> storedStrafingTerritories;

  public AbstractJBGAi(
      final String name, final IBattleCalculator battleCalculator, final JBGData jbgData) {
    super(name);
    this.jbgData = jbgData;
    calc = new JBGOddsCalculator(battleCalculator);
    combatMoveAi = new JBGCombatMoveAi(this);
    nonCombatMoveAi = new JBGNonCombatMoveAi(this);
    purchaseAi = new JBGPurchaseAi(this);
    retreatAi = new JBGRetreatAi(this);
    scrambleAi = new JBGScrambleAi(this);
    politicsAi = new JBGPoliticsAi(this);
    storedCombatMoveMap = null;
    storedFactoryMoveMap = null;
    storedPurchaseTerritories = null;
    storedPoliticalActions = null;
    storedStrafingTerritories = new ArrayList<>();
  }

  @Override
  public void stopGame() {
    super.stopGame(); // absolutely MUST call super.stopGame() first
    calc.stop();
  }

  public JBGOddsCalculator getCalc() {
    return calc;
  }

  public static void initialize(final TripleAFrame frame) {
    JBGLogUi.initialize(frame);
    JBGLogger.info("Initialized Hard AI");
  }

  public static void showSettingsWindow() {
    JBGLogger.info("Showing Hard AI settings window");
    JBGLogUi.showSettingsWindow();
  }

  private void initializeData() {
    jbgData.initialize(this);
  }

  public void setStoredStrafingTerritories(final List<Territory> strafingTerritories) {
    storedStrafingTerritories = strafingTerritories;
  }

  /**
   * Some implementations of {@link IBattleCalculator} do require setting a GameData instance before
   * actually being able to run properly. This method should take care of that.
   */
  protected abstract void prepareData(GameData data);

  @Override
  protected void move(
      final boolean nonCombat,
      final IMoveDelegate moveDel,
      final GameData data,
      final GamePlayer player) {
    final long start = System.currentTimeMillis();
    JBGLogUi.notifyStartOfRound(data.getSequence().getRound(), player.getName());
    initializeData();
    prepareData(data);

    doJBGEventMessaging(data, "Start moving ...");

    Map<String, JBGGamePlayerExtInfo> jbgAiInterractLst = data.getJBGAiInterracts();

    if (!jbgAiInterractLst.containsKey(player.getName())) {
      JBGGamePlayerExtInfo exInfo = new JBGGamePlayerExtInfo(player.getName());
      jbgAiInterractLst.put(player.getName(), exInfo);
    }

    if (nonCombat) {
      if (lastCombatTimeBySeconds >= 30 && !jbgAiInterractLst.get(player.getName()).isDefensiveStance())
        simpleDoNonCombatMove(moveDel, player, data);
      else
        nonCombatMoveAi.doNonCombatMove(storedFactoryMoveMap, storedPurchaseTerritories, moveDel);
      storedFactoryMoveMap = null;
    } else {

      if (jbgAiInterractLst.get(player.getName()).isDefensiveStance()) {
        doJBGEventMessaging(data, "Defensive stance: " + player.getName() + " doing nothing!");
      }
      else {
        jbgAiInterractLst.get(player.getName()).continueOrStopAggressive();
        //if continueOrStopAggressive reach last turn, it will reset the aggressive stance,
        if (jbgAiInterractLst.get(player.getName()).isDefensiveStance()) {
          //new defensive stance
          doJBGEventMessaging(data, "New defensive stance: " + player.getName() + " doing nothing!");
        }
        else {

          int iAggMax = jbgAiInterractLst.get(player.getName()).getAggressiveTurnMax();
          int iAggCount = jbgAiInterractLst.get(player.getName()).getAggressiveTurnCount();
          doJBGEventMessaging(data, "Aggressive stance: " + player.getName() + " offensive " + String.valueOf(iAggCount) + "/" + String.valueOf(iAggMax));
          if (storedCombatMoveMap == null) {
            combatMoveAi.doCombatMove(moveDel);
          } else {
            combatMoveAi.doMove(storedCombatMoveMap, moveDel, data, player);
            storedCombatMoveMap = null;
          }

        }

      }
    }
    final int consumedTimeBySeconds = (int) (System.currentTimeMillis() - start) / 1000;
    if (nonCombat)
      lastNoneCombatTimeBySeconds = consumedTimeBySeconds;
    else
      lastCombatTimeBySeconds = consumedTimeBySeconds;

    JBGLogger.info(
        player.getName()
            + " time for nonCombat="
            + nonCombat
            + " time="
            + (System.currentTimeMillis() - start));
  }

  //JBG event messaging
  private void doJBGEventMessaging(final GameData data, final String msg) {
    System.out.println(msg);
    data.setEventMessageBuffer(msg); data.fireGameDataEvent(GameDataEvent.JBG_AI_MESSAGING_EVENT);
  }
  //

  //JBG mobilization feature
    private List<Territory> getMyTerritories() {
      final GameData data = getGameData();
      final GamePlayer me = this.getGamePlayer();
      final List<Territory> myTerrs = CollectionUtils.getMatches(
                        data.getMap().getTerritories(),
                        Matches.isTerritoryOwnedBy(me)
                            .and(Matches.territoryHasUnitsOwnedBy(me))
                            .and(Matches.territoryIsLand()));
      return myTerrs;
    }

    private int countMyLandTerritory() {
      List<Territory> lst = getMyTerritories();
      if (lst != null)
        return lst.size();
      return 0;
    }

    private int countMyFactories() {
      final GamePlayer me = this.getGamePlayer();
      final List<Territory> myTerrs = getMyTerritories();

      final Predicate<Unit> ownedFactories =
        Matches.unitCanProduceUnits().and(Matches.unitIsOwnedBy(me));
      final List<Territory> territoriesWithFactories =
          CollectionUtils.getMatches(
              myTerrs, Matches.territoryHasUnitsThatMatch(ownedFactories));

      return territoriesWithFactories.size();
    }

    private boolean canTriggerMobilization(final int totalTerritories, final int totalFactories, final int availPUs) {
      boolean terrMatched = false;
      boolean factoryMatched = false;
      boolean lowPUs = false;

      //cannot produce
      if (totalFactories < 1) return false;

      if (totalTerritories <= 2) terrMatched = true;
      if (totalFactories == 1) factoryMatched = true;
      if (availPUs < 10) lowPUs = true;
      if (terrMatched && factoryMatched) return true;
      if (totalTerritories <= 4 && lowPUs) return true;
      return false;
    }
  //end of JBG mobilization feature

  //JBG purchase (Tien1Ai)
    private static Route getAmphibRoute(final GamePlayer player, final GameData data) {
      if (!isAmphibAttack(player, data)) {
        return null;
      }
      final Territory ourCapitol =
          TerritoryAttachment.getFirstOwnedCapitalOrFirstUnownedCapital(player, data);
      if (ourCapitol == null) {
        return null;
      }
      final Predicate<Territory> endMatch =
          o -> {
            final boolean impassable =
                TerritoryAttachment.get(o) != null && TerritoryAttachment.get(o).getIsImpassable();
            return !impassable
                && !o.isWater()
                && Utils.hasLandRouteToEnemyOwnedCapitol(o, player, data);
          };
      final Predicate<Territory> routeCond =
          Matches.territoryIsWater().and(Matches.territoryHasNoEnemyUnits(player, data));
      final @Nullable Route withNoEnemy = Utils.findNearest(ourCapitol, endMatch, routeCond, data);
      if (withNoEnemy != null && withNoEnemy.numberOfSteps() > 0) {
        return withNoEnemy;
      }
      // this will fail if our capitol is not next to water, c'est la vie.
      final @Nullable Route route =
          Utils.findNearest(ourCapitol, endMatch, Matches.territoryIsWater(), data);
      if (route != null && route.numberOfSteps() == 0) {
        return null;
      }
      return route;
    }

    private static boolean isAmphibAttack(final GamePlayer player, final GameData data) {
      final Territory capitol =
          TerritoryAttachment.getFirstOwnedCapitalOrFirstUnownedCapital(player, data);
      // we dont own our own capitol
      if (capitol == null || !capitol.getOwner().equals(player)) {
        return false;
      }
      // find a land route to an enemy territory from our capitol
      final Route invasionRoute =
          Utils.findNearest(
              capitol,
              Matches.isTerritoryEnemyAndNotUnownedWaterOrImpassableOrRestricted(player, data),
              Matches.territoryIsLand().and(Matches.territoryIsNeutralButNotWater().negate()),
              data);
      return invasionRoute == null;
    }

    private static int countTransports(final GameData data, final GamePlayer player) {
      final Predicate<Unit> ownedTransport =
          Matches.unitIsTransport().and(Matches.unitIsOwnedBy(player));
      return Streams.stream(data.getMap())
          .map(Territory::getUnitCollection)
          .mapToInt(c -> c.countMatches(ownedTransport))
          .sum();
    }

    private static int countLandUnits(final GameData data, final GamePlayer player) {
      final Predicate<Unit> ownedLandUnit = Matches.unitIsLand().and(Matches.unitIsOwnedBy(player));
      return Streams.stream(data.getMap())
          .map(Territory::getUnitCollection)
          .mapToInt(c -> c.countMatches(ownedLandUnit))
          .sum();
    }

    @Override
    public void purchase(
        final boolean purchaseForBid,
        final int pusToSpend,
        final IPurchaseDelegate purchaseDelegate,
        final GameData data,
        final GamePlayer player) {

      if (purchaseForBid) {
        // bid will only buy land units, due to weak ai placement for bid not being able to handle sea
        // units
        final Resource pus = data.getResourceList().getResource(Constants.PUS);
        int leftToSpend = pusToSpend;
        final List<ProductionRule> rules = player.getProductionFrontier().getRules();
        final IntegerMap<ProductionRule> purchase = new IntegerMap<>();
        int minCost = Integer.MAX_VALUE;
        int i = 0;
        while ((minCost == Integer.MAX_VALUE || leftToSpend >= minCost) && i < 100000) {
          i++;
          for (final ProductionRule rule : rules) {
            final NamedAttachable resourceOrUnit = rule.getResults().keySet().iterator().next();
            if (!(resourceOrUnit instanceof UnitType)) {
              continue;
            }
            final UnitType results = (UnitType) resourceOrUnit;
            if (Matches.unitTypeIsSea().test(results)
                || Matches.unitTypeIsAir().test(results)
                || Matches.unitTypeIsInfrastructure().test(results)
                || Matches.unitTypeIsAaForAnything().test(results)
                || Matches.unitTypeHasMaxBuildRestrictions().test(results)
                || Matches.unitTypeConsumesUnitsOnCreation().test(results)
                || Matches.unitTypeIsStatic(player).test(results)) {
              continue;
            }
            final int cost = rule.getCosts().getInt(pus);
            if (cost < 1) {
              continue;
            }
            if (minCost == Integer.MAX_VALUE) {
              minCost = cost;
            }
            if (minCost > cost) {
              minCost = cost;
            }
            // give a preference to cheap units
            if (Math.random() * cost < 2 && cost <= leftToSpend) {
              leftToSpend -= cost;
              purchase.add(rule, 1);
            }
          }
        }
        purchaseDelegate.purchase(purchase);
        movePause();
        return;
      }
      final boolean isAmphib = isAmphibAttack(player, data);
      final Route amphibRoute = getAmphibRoute(player, data);
      final int transportCount = countTransports(data, player);
      final int landUnitCount = countLandUnits(data, player);
      int defUnitsAtAmpibRoute = 0;
      if (isAmphib && amphibRoute != null) {
        defUnitsAtAmpibRoute = amphibRoute.getEnd().getUnitCollection().getUnitCount();
      }
      final Resource pus = data.getResourceList().getResource(Constants.PUS);
      //JBG change by Tien
       //final int totalPu = player.getResources().getQuantity(pus);
      final int totalPu = pusToSpend;
      int leftToSpend = totalPu;

      Map<String, JBGGamePlayerExtInfo> jbgAiInterractLst = data.getJBGAiInterracts();

      //though we can call this trigger in move(), 
      //but it will not help Ai to get new units before change to aggressive
      //so this remain here
      if (jbgAiInterractLst.get(player.getName()).isDefensiveStance()) {
        int leftTerrs = countMyLandTerritory();
        int leftFactories = countMyFactories();
        int availPUs = player.getResources().getQuantity(pus); //this can be another condition for triggering
        if (canTriggerMobilization(leftTerrs, leftFactories, availPUs)) {
          //after mobilization, the player will go out of defensive immediately by the end of this function 
          notCareAboutCost = true;
          leftToSpend += JBGConstants.MOBILIZATION_VALUE;
          jbgAiInterractLst.get(player.getName()).addStockingAmount(JBGConstants.MOBILIZATION_VALUE);
          doJBGEventMessaging(data, player.getName() + " spending MOBILIZATION_VALUE");
        }
        int tributeAmount = jbgAiInterractLst.get(player.getName()).getTributeAmount();
        if (tributeAmount > 0) {
          notCareAboutCost = true;
          leftToSpend += tributeAmount;
          jbgAiInterractLst.get(player.getName()).addStockingAmount(tributeAmount);
          jbgAiInterractLst.get(player.getName()).setTributeAmount(0);
          doJBGEventMessaging(data, player.getName() + " spending tribute amount " + String.valueOf(tributeAmount));
        }
      }

      int originSpendingBudget = leftToSpend;

      final @Nullable Territory capitol =
          TerritoryAttachment.getFirstOwnedCapitalOrFirstUnownedCapital(player, data);
      final List<ProductionRule> rules = player.getProductionFrontier().getRules();
      final IntegerMap<ProductionRule> purchase = new IntegerMap<>();
      final List<RepairRule> repairRules;
      final Predicate<Unit> ourFactories =
          Matches.unitIsOwnedBy(player).and(Matches.unitCanProduceUnits());
      final List<Territory> repairFactories =
          CollectionUtils.getMatches(
              Utils.findUnitTerr(data, ourFactories), Matches.isTerritoryOwnedBy(player));

      System.out.println("Total factory: " + String.valueOf(repairFactories.size()));
      if (repairFactories.size() > 0) {
        if (notCareAboutCost) {
          final ProductionRule noCostRule = new ProductionRule(JBGConstants.JBG_NO_COST_CARE_RULE, data);
          System.out.println("Adding rule: " + noCostRule.getName());
          purchase.add(noCostRule, 1);
        }
      }

      // figure out if anything needs to be repaired
      if (player.getRepairFrontier() != null
          && Properties.getDamageFromBombingDoneToUnitsInsteadOfTerritories(data)) {
        repairRules = player.getRepairFrontier().getRules();
        final IntegerMap<RepairRule> repairMap = new IntegerMap<>();
        final Map<Unit, IntegerMap<RepairRule>> repair = new HashMap<>();
        final Map<Unit, Territory> unitsThatCanProduceNeedingRepair = new HashMap<>();
        final int minimumUnitPrice = 3;
        int diff;
        int capProduction = 0;
        Unit capUnit = null;
        Territory capUnitTerritory = null;
        int currentProduction = 0;
        // we should sort this
        Collections.shuffle(repairFactories);
        for (final Territory fixTerr : repairFactories) {
          if (!Matches.territoryIsOwnedAndHasOwnedUnitMatching(
                  player, Matches.unitCanProduceUnitsAndCanBeDamaged())
              .test(fixTerr)) {
            continue;
          }
          final Unit possibleFactoryNeedingRepair =
              UnitUtils.getBiggestProducer(
                  CollectionUtils.getMatches(fixTerr.getUnits(), ourFactories),
                  fixTerr,
                  player,
                  data,
                  false);
          if (Matches.unitHasTakenSomeBombingUnitDamage().test(possibleFactoryNeedingRepair)) {
            unitsThatCanProduceNeedingRepair.put(possibleFactoryNeedingRepair, fixTerr);
          }
          if (fixTerr.equals(capitol)) {
            capProduction =
                UnitUtils.getHowMuchCanUnitProduce(
                    possibleFactoryNeedingRepair, fixTerr, player, data, true, true);
            capUnit = possibleFactoryNeedingRepair;
            capUnitTerritory = fixTerr;
          }
          currentProduction +=
              UnitUtils.getHowMuchCanUnitProduce(
                  possibleFactoryNeedingRepair, fixTerr, player, data, true, true);
        }
        repairFactories.remove(capitol);
        unitsThatCanProduceNeedingRepair.remove(capUnit);
        // assume minimum unit price is 3, and that we are buying only that... if we over repair, oh
        // well, that is better
        // than under-repairing
        // goal is to be able to produce all our units, and at least half of that production in the
        // capitol
        //
        // if capitol is super safe, we don't have to do this. and if capitol is under siege, we
        // should repair enough to
        // place all our units here
        int maxUnits = (totalPu - 1) / minimumUnitPrice;
        if ((capProduction <= maxUnits / 2 || repairFactories.isEmpty()) && capUnit != null) {
          for (final RepairRule rrule : repairRules) {
            if (!capUnit.getType().equals(rrule.getResults().keySet().iterator().next())) {
              continue;
            }
            if (!Matches.territoryIsOwnedAndHasOwnedUnitMatching(
                    player, Matches.unitCanProduceUnitsAndCanBeDamaged())
                .test(capitol)) {
              continue;
            }
            diff = capUnit.getUnitDamage();
            final int unitProductionAllowNegative =
                UnitUtils.getHowMuchCanUnitProduce(
                        capUnit, capUnitTerritory, player, data, false, true)
                    - diff;
            if (!repairFactories.isEmpty()) {
              diff = Math.min(diff, (maxUnits / 2 - unitProductionAllowNegative) + 1);
            } else {
              diff = Math.min(diff, (maxUnits - unitProductionAllowNegative));
            }
            diff = Math.min(diff, leftToSpend - minimumUnitPrice);
            if (diff > 0) {
              if (unitProductionAllowNegative >= 0) {
                currentProduction += diff;
              } else {
                currentProduction += diff + unitProductionAllowNegative;
              }
              repairMap.add(rrule, diff);
              repair.put(capUnit, repairMap);
              leftToSpend -= diff;
              purchaseDelegate.purchaseRepair(repair);
              repair.clear();
              repairMap.clear();
              // ideally we would adjust this after each single PU spent, then re-evaluate everything.
              maxUnits = (leftToSpend - 1) / minimumUnitPrice;
            }
          }
        }
        int i = 0;
        while (currentProduction < maxUnits && i < 2) {
          for (final RepairRule rrule : repairRules) {
            for (final Unit fixUnit : unitsThatCanProduceNeedingRepair.keySet()) {
              if (fixUnit == null
                  || !fixUnit.getType().equals(rrule.getResults().keySet().iterator().next())) {
                continue;
              }
              if (!Matches.territoryIsOwnedAndHasOwnedUnitMatching(
                      player, Matches.unitCanProduceUnitsAndCanBeDamaged())
                  .test(unitsThatCanProduceNeedingRepair.get(fixUnit))) {
                continue;
              }
              // we will repair the first territories in the list as much as we can, until we fulfill
              // the condition, then
              // skip all other territories
              if (currentProduction >= maxUnits) {
                continue;
              }
              diff = fixUnit.getUnitDamage();
              final int unitProductionAllowNegative =
                  UnitUtils.getHowMuchCanUnitProduce(
                          fixUnit,
                          unitsThatCanProduceNeedingRepair.get(fixUnit),
                          player,
                          data,
                          false,
                          true)
                      - diff;
              if (i == 0) {
                if (unitProductionAllowNegative < 0) {
                  diff = Math.min(diff, (maxUnits - currentProduction) - unitProductionAllowNegative);
                } else {
                  diff = Math.min(diff, (maxUnits - currentProduction));
                }
              }
              diff = Math.min(diff, leftToSpend - minimumUnitPrice);
              if (diff > 0) {
                if (unitProductionAllowNegative >= 0) {
                  currentProduction += diff;
                } else {
                  currentProduction += diff + unitProductionAllowNegative;
                }
                repairMap.add(rrule, diff);
                repair.put(fixUnit, repairMap);
                leftToSpend -= diff;
                purchaseDelegate.purchaseRepair(repair);
                repair.clear();
                repairMap.clear();
                // ideally we would adjust this after each single PU spent, then re-evaluate
                // everything.
                maxUnits = (leftToSpend - 1) / minimumUnitPrice;
              }
            }
          }
          repairFactories.add(capitol);
          if (capUnit != null) {
            unitsThatCanProduceNeedingRepair.put(capUnit, capUnitTerritory);
          }
          i++;
        }
      }
      int minCost = Integer.MAX_VALUE;
      int i = 0;
      while ((minCost == Integer.MAX_VALUE || leftToSpend >= minCost) && i < 100000) {
        i++;
        for (final ProductionRule rule : rules) {
          final NamedAttachable resourceOrUnit = rule.getResults().keySet().iterator().next();
          if (!(resourceOrUnit instanceof UnitType)) {
            continue;
          }
          final UnitType results = (UnitType) resourceOrUnit;

          //only purchase land units, air units, infra unit 
          //other need more complex logics which this AI not supports yet
          if (Matches.unitTypeHasMaxBuildRestrictions().test(results)
              || Matches.unitTypeConsumesUnitsOnCreation().test(results)
              || Matches.unitTypeIsStatic(player).test(results)) {
            continue;
          }
          final int transportCapacity = UnitAttachment.get(results).getTransportCapacity();
          // buy transports if we can be amphibious
          if (Matches.unitTypeIsSea().test(results) && (!isAmphib || transportCapacity <= 0)) {
            continue;
          }
          final int cost = rule.getCosts().getInt(pus);
          if (cost < 1) {
            continue;
          }

          //System.out.println("Rule name: " + rule.getName() + " cost: " +String.valueOf(cost));
          /*
          if (rule.getName().equals("buyTank")) {
            purchase.add(rule, 1);
            System.out.println("order to buy 1 tank");
            continue;
          }
          */

          if (minCost == Integer.MAX_VALUE) {
            minCost = cost;
          }
          if (minCost > cost) {
            minCost = cost;
          }
          // give a preference to cheap units, and to transports but don't go overboard with buying
          // transports
          int goodNumberOfTransports = 0;
          final boolean isTransport = transportCapacity > 0;
          if (amphibRoute != null) {
            // 25% transports - can be more if frontier is far away
            goodNumberOfTransports = (landUnitCount / 4);
            // boost for transport production
            if (isTransport
                && defUnitsAtAmpibRoute > goodNumberOfTransports
                && landUnitCount > defUnitsAtAmpibRoute
                && defUnitsAtAmpibRoute > transportCount) {
              final int transports = (leftToSpend / cost);
              leftToSpend -= cost * transports;
              purchase.add(rule, transports);
              //System.out.println("order to buy " + String.valueOf(transports) + rule.getName());
              continue;
            }
          }
          final boolean buyBecauseTransport =
              (Math.random() < 0.7 && transportCount < goodNumberOfTransports)
                  || Math.random() < 0.10;
          final boolean dontBuyBecauseTooManyTransports = transportCount > 2 * goodNumberOfTransports;
          if (((!isTransport && Math.random() * cost < 2)
                  || (isTransport && buyBecauseTransport && !dontBuyBecauseTooManyTransports))
              && cost <= leftToSpend) {
            leftToSpend -= cost;
            purchase.add(rule, 1);
            //System.out.println("order to buy 1 " + rule.getName());
          }
        }
      }
      purchaseDelegate.purchase(purchase);
      movePause();

      int spentAmount = originSpendingBudget - leftToSpend;
      if (jbgAiInterractLst.get(player.getName()).isDefensiveStance()) {
        int playerStockAmount = jbgAiInterractLst.get(player.getName()).getStockingAmount() + spentAmount;
        doJBGEventMessaging(data, player.getName() + " added " + String.valueOf(spentAmount) + " point to total: " + playerStockAmount);
        if (playerStockAmount < 100) {
          jbgAiInterractLst.get(player.getName()).addStockingAmount(spentAmount);
        }
        else {
          doJBGEventMessaging(data, player.getName() + " stop stocking, will start offensive in next turn ... ");
          jbgAiInterractLst.get(player.getName()).setDefensiveStance(false); //will reset incl. setStockingAmount(0)
        }
      }
  }
    //end JBG purchase

  @Override
  protected void place(
      final boolean bid,
      final IAbstractPlaceDelegate placeDelegate,
      final GameData data,
      final GamePlayer player) {
    doJBGEventMessaging(data, player.getName() + " start placing units ... ");
    simplePlace(bid, placeDelegate, data, player);
  }

  protected void proPlace(
      final boolean bid,
      final IAbstractPlaceDelegate placeDelegate,
      final GameData data,
      final GamePlayer player) {
    final long start = System.currentTimeMillis();
    JBGLogUi.notifyStartOfRound(data.getSequence().getRound(), player.getName());
    initializeData();
    purchaseAi.place(storedPurchaseTerritories, placeDelegate);
    storedPurchaseTerritories = null;
    JBGLogger.info(player.getName() + " time for place=" + (System.currentTimeMillis() - start));
  }

  //JBG place (Tien1Ai)
    public void simplePlace(
        final boolean bid,
        final IAbstractPlaceDelegate placeDelegate,
        final GameData data,
        final GamePlayer player) {
      if (player.getUnitCollection().isEmpty()) {
        return;
      }
      final @Nullable Territory capitol =
          TerritoryAttachment.getFirstOwnedCapitalOrFirstUnownedCapital(player, data);
      if (capitol != null) {
        // place in capitol first
        placeAllWeCanOn(data, capitol, placeDelegate, player);
      }
      final List<Territory> randomTerritories = new ArrayList<>(data.getMap().getTerritories());
      Collections.shuffle(randomTerritories);
      for (final Territory t : randomTerritories) {
        if (!t.equals(capitol)
            && t.getOwner().equals(player)
            && t.getUnitCollection().anyMatch(Matches.unitCanProduceUnits())) {
          placeAllWeCanOn(data, t, placeDelegate, player);
        }
      }
    }

    private void placeAllWeCanOn(
        final GameData data,
        final Territory placeAt,
        final IAbstractPlaceDelegate placeDelegate,
        final GamePlayer player) {
      final PlaceableUnits pu = placeDelegate.getPlaceableUnits(player.getUnits(), placeAt);
      if (pu.getErrorMessage() != null) {
        return;
      }
      int placementLeft = pu.getMaxUnits();
      if (notCareAboutCost) {
        placementLeft = JBGConstants.MOBILIZATION_VALUE;
      }
      if (placementLeft == -1) {
        placementLeft = Integer.MAX_VALUE;
      }

      if (notCareAboutCost) {
        final Unit costRuleUnit = new Unit(new UnitType(JBGConstants.JBG_NO_COST_CARE_RULE, data), player, data);
        List<Unit> costRules = new ArrayList<Unit>();
        costRules.add(costRuleUnit);
        doPlace(null, costRules, placeDelegate);
      }

      final List<Unit> seaUnits =
          new ArrayList<>(player.getUnitCollection().getMatches(Matches.unitIsSea()));
      if (!seaUnits.isEmpty()) {
        final Route amphibRoute = getAmphibRoute(player, data);
        Territory seaPlaceAt = null;
        if (amphibRoute != null) {
          seaPlaceAt = amphibRoute.getAllTerritories().get(1);
        } else {
          final Set<Territory> seaNeighbors =
              data.getMap().getNeighbors(placeAt, Matches.territoryIsWater());
          if (!seaNeighbors.isEmpty()) {
            seaPlaceAt = seaNeighbors.iterator().next();
          }
        }
        if (seaPlaceAt != null) {
          final int seaPlacement = Math.min(placementLeft, seaUnits.size());
          placementLeft -= seaPlacement;
          final Collection<Unit> toPlace = seaUnits.subList(0, seaPlacement);
          doPlace(seaPlaceAt, toPlace, placeDelegate);
        }
      }
      final List<Unit> landUnits =
          new ArrayList<>(player.getUnitCollection().getMatches(Matches.unitIsLand()));
      if (!landUnits.isEmpty()) {
        final int landPlaceCount = Math.min(placementLeft, landUnits.size());
        final Collection<Unit> toPlace = landUnits.subList(0, landPlaceCount);
        //System.out.println("placing land units ..." + String.valueOf(toPlace.size()));
        doPlace(placeAt, toPlace, placeDelegate);
      }

      final List<Unit> airUnits =
          new ArrayList<>(player.getUnitCollection().getMatches(Matches.unitIsAir()));
      if (!landUnits.isEmpty()) {
        final int airPlaceCount = Math.min(placementLeft, airUnits.size());
        final Collection<Unit> toPlace = airUnits.subList(0, airPlaceCount);
        //System.out.println("placing air units ..." + String.valueOf(toPlace.size()));
        doPlace(placeAt, toPlace, placeDelegate);
      }

      if (notCareAboutCost) {
        //turn it off after placing
        notCareAboutCost = false;
      }

    }

    private static void doPlace(
        final Territory where, final Collection<Unit> toPlace, final IAbstractPlaceDelegate del) {
      del.placeUnits(new ArrayList<>(toPlace), where, IAbstractPlaceDelegate.BidMode.NOT_BID);
      movePause();
    }
  // end JBG place


  //JBG NoncombatMove
      private void simpleDoNonCombatMove(
          final IMoveDelegate moveDel, final GamePlayer player, final GameData data) {
        // load the transports first
        // they may be able to move farther
        doMove(calculateTransportLoad(data, player), moveDel);
        // do the rest of the moves
        doMove(calculateNonCombat(data, player), moveDel);
        doMove(calculateNonCombatSea(true, data, player), moveDel);
        // load the transports again if we can
        // they may be able to move farther
        doMove(calculateTransportLoad(data, player), moveDel);
        // unload the transports that can be unloaded
        doMove(calculateTransportUnloadNonCombat(data, player), moveDel);
      }

      private List<MoveDescription> calculateTransportLoad(
          final GameData data, final GamePlayer player) {
        if (!isAmphibAttack(player, data)) {
          return List.of();
        }
        final Territory capitol =
            TerritoryAttachment.getFirstOwnedCapitalOrFirstUnownedCapital(player, data);
        if (capitol == null || !capitol.getOwner().equals(player)) {
          return List.of();
        }
        final var moves = new ArrayList<MoveDescription>();
        List<Unit> unitsToLoad =
            capitol.getUnitCollection().getMatches(Matches.unitIsInfrastructure().negate());
        unitsToLoad =
            CollectionUtils.getMatches(unitsToLoad, Matches.unitIsOwnedBy(this.getGamePlayer()));
        for (final Territory neighbor : data.getMap().getNeighbors(capitol)) {
          if (!neighbor.isWater()) {
            continue;
          }
          final List<Unit> units = new ArrayList<>();
          final Map<Unit, Unit> transportMap = new HashMap<>();
          for (final Unit transport :
              neighbor.getUnitCollection().getMatches(Matches.unitIsOwnedBy(player))) {
            int free = TransportTracker.getAvailableCapacity(transport);
            if (free <= 0) {
              continue;
            }
            final Iterator<Unit> iter = unitsToLoad.iterator();
            while (iter.hasNext() && free > 0) {
              final Unit current = iter.next();
              final UnitAttachment ua = UnitAttachment.get(current.getType());
              if (ua.getIsAir()) {
                continue;
              }
              if (ua.getTransportCost() <= free) {
                iter.remove();
                free -= ua.getTransportCost();
                units.add(current);
                transportMap.put(current, transport);
              }
            }
          }
          if (!units.isEmpty()) {
            final Route route = new Route(capitol, neighbor);
            moves.add(new MoveDescription(units, route, transportMap, Map.of()));
          }
        }
        return moves;
      }

      private static void doMove(final List<MoveDescription> moves, final IMoveDelegate moveDel) {
        for (final MoveDescription move : moves) {
          moveDel.performMove(move);
          movePause();
        }
      }

      private static List<MoveDescription> calculateTransportUnloadNonCombat(
          final GameData data, final GamePlayer player) {
        final Route amphibRoute = getAmphibRoute(player, data);
        if (amphibRoute == null) {
          return List.of();
        }
        final Territory lastSeaZoneOnAmphib =
            amphibRoute.getAllTerritories().get(amphibRoute.numberOfSteps() - 1);
        final Territory landOn = amphibRoute.getEnd();
        final Predicate<Unit> landAndOwned = Matches.unitIsLand().and(Matches.unitIsOwnedBy(player));
        final List<Unit> units = lastSeaZoneOnAmphib.getUnitCollection().getMatches(landAndOwned);
        if (units.isEmpty()) {
          return List.of();
        }
        final Route route = new Route(lastSeaZoneOnAmphib, landOn);
        return List.of(new MoveDescription(units, route));
      }

        /** prepares moves for transports. */
      private static List<MoveDescription> calculateNonCombatSea(
          final boolean nonCombat, final GameData data, final GamePlayer player) {
        final Route amphibRoute = getAmphibRoute(player, data);
        Territory firstSeaZoneOnAmphib = null;
        Territory lastSeaZoneOnAmphib = null;
        if (amphibRoute != null) {
          firstSeaZoneOnAmphib = amphibRoute.getAllTerritories().get(1);
          lastSeaZoneOnAmphib = amphibRoute.getAllTerritories().get(amphibRoute.numberOfSteps() - 1);
        }
        final Predicate<Unit> ownedAndNotMoved =
            Matches.unitIsOwnedBy(player).and(Matches.unitHasNotMoved());
        final var moves = new ArrayList<MoveDescription>();
        for (final Territory t : data.getMap()) {
          // move sea units to the capitol, unless they are loaded transports
          if (t.isWater()) {
            // land units, move all towards the end point
            // and move along amphib route
            if (t.getUnitCollection().anyMatch(Matches.unitIsLand()) && lastSeaZoneOnAmphib != null) {
              // two move route to end
              final @Nullable Route r = getMaxSeaRoute(data, t, lastSeaZoneOnAmphib, player);
              if (r != null) {
                final List<Unit> unitsToMove =
                    t.getUnitCollection().getMatches(Matches.unitIsOwnedBy(player));
                moves.add(new MoveDescription(unitsToMove, r));
              }
            }
            // move toward the start of the amphib route
            if (nonCombat
                && t.getUnitCollection().anyMatch(ownedAndNotMoved)
                && firstSeaZoneOnAmphib != null) {
              final @Nullable Route r = getMaxSeaRoute(data, t, firstSeaZoneOnAmphib, player);
              if (r != null) {
                moves.add(new MoveDescription(t.getUnitCollection().getMatches(ownedAndNotMoved), r));
              }
            }
          }
        }
        return moves;
      }

      private static @Nullable Route getMaxSeaRoute(
          final GameData data,
          final Territory start,
          final Territory destination,
          final GamePlayer player) {
        final Predicate<Territory> routeCond =
            Matches.territoryIsWater()
                .and(Matches.territoryHasEnemyUnits(player, data).negate())
                .and(Matches.territoryHasNonAllowedCanal(player, data).negate());
        Route r = data.getMap().getRoute(start, destination, routeCond);
        if (r == null || r.hasNoSteps() || !routeCond.test(destination)) {
          return null;
        }
        if (r.numberOfSteps() > 2) {
          r = new Route(start, r.getAllTerritories().get(1), r.getAllTerritories().get(2));
        }
        return r;
      }

        private List<MoveDescription> calculateNonCombat(final GameData data, final GamePlayer player) {
        final Collection<Territory> territories = data.getMap().getTerritories();
        final List<MoveDescription> moves = movePlanesHomeNonCombat(player, data);
        // move our units toward the nearest enemy capitol
        for (final Territory t : territories) {
          if (t.isWater()) {
            continue;
          }
          if (TerritoryAttachment.get(t) != null && TerritoryAttachment.get(t).isCapital()) {
            // if they are a threat to take our capitol, dont move
            // compare the strength of units we can place
            final float ourStrength = AiUtils.strength(player.getUnits(), false, false);
            final float attackerStrength = Utils.getStrengthOfPotentialAttackers(t, data);
            if (attackerStrength > ourStrength) {
              continue;
            }
          }
          // these are the units we can move
          final Predicate<Unit> moveOfType =
              Matches.unitIsOwnedBy(player)
                  .and(Matches.unitIsNotAa())
                  // we can never move factories
                  .and(Matches.unitCanMove())
                  .and(Matches.unitIsNotInfrastructure())
                  .and(Matches.unitIsLand());
          final Predicate<Territory> moveThrough =
              Matches.territoryIsImpassable()
                  .negate()
                  .and(Matches.territoryIsNeutralButNotWater().negate())
                  .and(Matches.territoryIsLand());
          final List<Unit> units = t.getUnitCollection().getMatches(moveOfType);
          if (units.isEmpty()) {
            continue;
          }
          int minDistance = Integer.MAX_VALUE;
          Territory to = null;
          // find the nearest enemy owned capital
          for (final GamePlayer otherPlayer : data.getPlayerList().getPlayers()) {
            final Territory capitol =
                TerritoryAttachment.getFirstOwnedCapitalOrFirstUnownedCapital(otherPlayer, data);
            if (capitol != null
                && !data.getRelationshipTracker().isAllied(player, capitol.getOwner())) {
              final Route route = data.getMap().getRoute(t, capitol, moveThrough);
              if (route != null && moveThrough.test(capitol)) {
                final int distance = route.numberOfSteps();
                if (distance != 0 && distance < minDistance) {
                  minDistance = distance;
                  to = capitol;
                }
              }
            }
          }
          if (to != null) {
            if (!units.isEmpty()) {
              final Route routeToCapitol = data.getMap().getRoute(t, to, moveThrough);
              final Territory firstStep = routeToCapitol.getAllTerritories().get(1);
              final Route route = new Route(t, firstStep);
              moves.add(new MoveDescription(units, route));
            }
          } else { // if we cant move to a capitol, move towards the enemy
            final Predicate<Territory> routeCondition =
                Matches.territoryIsLand().and(Matches.territoryIsImpassable().negate());
            @Nullable
            Route newRoute =
                Utils.findNearest(
                    t, Matches.territoryHasEnemyLandUnits(player, data), routeCondition, data);
            // move to any enemy territory
            if (newRoute == null) {
              newRoute =
                  Utils.findNearest(t, Matches.isTerritoryEnemy(player, data), routeCondition, data);
            }
            if (newRoute != null && newRoute.numberOfSteps() != 0) {
              final Territory firstStep = newRoute.getAllTerritories().get(1);
              final Route route = new Route(t, firstStep);
              moves.add(new MoveDescription(units, route));
            }
          }
        }
        return moves;
      }

      private List<MoveDescription> movePlanesHomeNonCombat(
          final GamePlayer player, final GameData data) {
        // the preferred way to get the delegate
        final IMoveDelegate delegateRemote = (IMoveDelegate) getPlayerBridge().getRemoteDelegate();
        // this works because we are on the server
        final BattleDelegate delegate = DelegateFinder.battleDelegate(data);
        final Predicate<Territory> canLand =
            Matches.isTerritoryAllied(player, data)
                .and(o -> !delegate.getBattleTracker().wasConquered(o));
        final Predicate<Territory> routeCondition =
            Matches.territoryHasEnemyAaForFlyOver(player, data)
                .negate()
                .and(Matches.territoryIsImpassable().negate());
        final var moves = new ArrayList<MoveDescription>();
        for (final Territory t : delegateRemote.getTerritoriesWhereAirCantLand()) {
          final @Nullable Route noAaRoute = Utils.findNearest(t, canLand, routeCondition, data);
          final @Nullable Route aaRoute =
              Utils.findNearest(t, canLand, Matches.territoryIsImpassable().negate(), data);
          final Collection<Unit> airToLand =
              t.getUnitCollection().getMatches(Matches.unitIsAir().and(Matches.unitIsOwnedBy(player)));
          // don't bother to see if all the air units have enough movement points to move without aa
          // guns firing
          // simply move first over no aa, then with aa one (but hopefully not both) will be rejected
          if (noAaRoute != null) {
            moves.add(new MoveDescription(airToLand, noAaRoute));
          }
          if (aaRoute != null) {
            moves.add(new MoveDescription(airToLand, aaRoute));
          }
        }
        return moves;
      }

  //End JBG NoncombatMove


  @Override
  protected void tech(
      final ITechDelegate techDelegate, final GameData data, final GamePlayer player) {
    JBGTechAi.tech(techDelegate, data, player);

    doJBGEventMessaging(data, player.getName() + " starting ... ");
  }

  @Override
  public Territory retreatQuery(
      final UUID battleId,
      final boolean submerge,
      final Territory battleTerritory,
      final Collection<Territory> possibleTerritories,
      final String message) {
    initializeData();

    // Get battle data
    final GameData data = getGameData();
    final GamePlayer player = this.getGamePlayer();
    final BattleDelegate delegate = DelegateFinder.battleDelegate(data);
    final IBattle battle = delegate.getBattleTracker().getPendingBattle(battleId);

    // If battle is null or amphibious then don't retreat
    if (battle == null || battleTerritory == null || battle.isAmphibious()) {
      return null;
    }

    // If attacker with more unit strength or strafing and isn't land battle with only air left then
    // don't retreat
    final boolean isAttacker = player.equals(battle.getAttacker());
    final Collection<Unit> attackers = battle.getAttackingUnits();
    final Collection<Unit> defenders = battle.getDefendingUnits();
    final double strengthDifference =
        JBGBattleUtils.estimateStrengthDifference(jbgData, battleTerritory, attackers, defenders);
    final boolean isStrafing = isAttacker && storedStrafingTerritories.contains(battleTerritory);
    JBGLogger.info(
        player.getName()
            + " checking retreat from territory "
            + battleTerritory
            + ", attackers="
            + attackers.size()
            + ", defenders="
            + defenders.size()
            + ", submerge="
            + submerge
            + ", attacker="
            + isAttacker
            + ", isStrafing="
            + isStrafing);
    if ((isStrafing || (isAttacker && strengthDifference > 50))
        && (battleTerritory.isWater() || attackers.stream().anyMatch(Matches.unitIsLand()))) {
      return null;
    }
    prepareData(getGameData());
    return retreatAi.retreatQuery(battleId, battleTerritory, possibleTerritories);
  }

  @Override
  public boolean shouldBomberBomb(final Territory territory) {
    return combatMoveAi.isBombing();
  }

  // TODO: Consider supporting this functionality
  @Override
  public Collection<Unit> getNumberOfFightersToMoveToNewCarrier(
      final Collection<Unit> fightersThatCanBeMoved, final Territory from) {
    return new ArrayList<>();
  }

  @Override
  public CasualtyDetails selectCasualties(
      final Collection<Unit> selectFrom,
      final Map<Unit, Collection<Unit>> dependents,
      final int count,
      final String message,
      final DiceRoll dice,
      final GamePlayer hit,
      final Collection<Unit> friendlyUnits,
      final Collection<Unit> enemyUnits,
      final boolean amphibious,
      final Collection<Unit> amphibiousLandAttackers,
      final CasualtyList defaultCasualties,
      final UUID battleId,
      final Territory battleSite,
      final boolean allowMultipleHitsPerUnit) {
    initializeData();

    if (defaultCasualties.size() != count) {
      throw new IllegalStateException(
          "Select Casualties showing different numbers for number of hits to take vs total "
              + "size of default casualty selections");
    }
    if (defaultCasualties.getKilled().isEmpty()) {
      return new CasualtyDetails(defaultCasualties, false);
    }

    // Consider unit cost
    final CasualtyDetails myCasualties = new CasualtyDetails(false);
    myCasualties.addToDamaged(defaultCasualties.getDamaged());
    final List<Unit> selectFromSorted = new ArrayList<>(selectFrom);
    if (enemyUnits.isEmpty()) {
      selectFromSorted.sort(JBGPurchaseUtils.getCostComparator(jbgData));
    } else {

      // Get battle data
      final GameData data = getGameData();
      final GamePlayer player = this.getGamePlayer();
      final BattleDelegate delegate = DelegateFinder.battleDelegate(data);
      final IBattle battle = delegate.getBattleTracker().getPendingBattle(battleId);

      // If defender and could lose battle then don't consider unit cost as just trying to survive
      boolean needToCheck = true;
      final boolean isAttacker = player.equals(battle.getAttacker());
      if (!isAttacker) {
        final Collection<Unit> attackers = battle.getAttackingUnits();
        final Collection<Unit> defenders = new ArrayList<>(battle.getDefendingUnits());
        defenders.removeAll(defaultCasualties.getKilled());
        final double strengthDifference =
            JBGBattleUtils.estimateStrengthDifference(jbgData, battleSite, attackers, defenders);
        int minStrengthDifference = 60;
        if (!Properties.getLowLuck(data)) {
          minStrengthDifference = 55;
        }
        if (strengthDifference > minStrengthDifference) {
          needToCheck = false;
        }
      }

      // Use bubble sort to save expensive units
      while (needToCheck) {
        needToCheck = false;
        for (int i = 0; i < selectFromSorted.size() - 1; i++) {
          final Unit unit1 = selectFromSorted.get(i);
          final Unit unit2 = selectFromSorted.get(i + 1);
          final double unitCost1 = JBGPurchaseUtils.getCost(jbgData, unit1);
          final double unitCost2 = JBGPurchaseUtils.getCost(jbgData, unit2);
          if (unitCost1 > 1.5 * unitCost2) {
            selectFromSorted.set(i, unit2);
            selectFromSorted.set(i + 1, unit1);
            needToCheck = true;
          }
        }
      }
    }

    // Interleave carriers and planes
    final List<Unit> interleavedTargetList =
        new ArrayList<>(JBGTransportUtils.interleaveUnitsCarriersAndPlanes(selectFromSorted, 0));
    for (int i = 0; i < defaultCasualties.getKilled().size(); ++i) {
      myCasualties.addToKilled(interleavedTargetList.get(i));
    }
    if (count != myCasualties.size()) {
      throw new IllegalStateException("AI chose wrong number of casualties");
    }
    return myCasualties;
  }

  @Override
  public Map<Territory, Collection<Unit>> scrambleUnitsQuery(
      final Territory scrambleTo,
      final Map<Territory, Tuple<Collection<Unit>, Collection<Unit>>> possibleScramblers) {
    initializeData();

    // Get battle data
    final GameData data = getGameData();
    final GamePlayer player = this.getGamePlayer();
    final BattleDelegate delegate = DelegateFinder.battleDelegate(data);
    final IBattle battle =
        delegate.getBattleTracker().getPendingBattle(scrambleTo, BattleType.NORMAL);

    // If battle is null then don't scramble
    if (battle == null) {
      return null;
    }
    final Collection<Unit> attackers = battle.getAttackingUnits();
    final Collection<Unit> defenders = battle.getDefendingUnits();
    JBGLogger.info(
        player.getName()
            + " checking scramble to "
            + scrambleTo
            + ", attackers="
            + attackers.size()
            + ", defenders="
            + defenders.size()
            + ", possibleScramblers="
            + possibleScramblers);
    prepareData(getGameData());
    return scrambleAi.scrambleUnitsQuery(scrambleTo, possibleScramblers);
  }

  @Override
  public boolean selectAttackSubs(final Territory unitTerritory) {
    initializeData();

    // Get battle data
    final GameData data = getGameData();
    final GamePlayer player = this.getGamePlayer();
    final BattleDelegate delegate = DelegateFinder.battleDelegate(data);
    final IBattle battle =
        delegate.getBattleTracker().getPendingBattle(unitTerritory, BattleType.NORMAL);

    // If battle is null then don't attack
    if (battle == null) {
      return false;
    }
    final Collection<Unit> attackers = battle.getAttackingUnits();
    final Collection<Unit> defenders = battle.getDefendingUnits();
    JBGLogger.info(
        player.getName()
            + " checking sub attack in "
            + unitTerritory
            + ", attackers="
            + attackers
            + ", defenders="
            + defenders);
    prepareData(getGameData());

    // Calculate battle results
    final JBGBattleResult result =
        calc.calculateBattleResults(jbgData, unitTerritory, attackers, defenders, new HashSet<>());
    JBGLogger.debug(player.getName() + " sub attack TUVSwing=" + result.getTuvSwing());
    return result.getTuvSwing() > 0;
  }

  @Override
  public void politicalActions() {
    initializeData();

    if (storedPoliticalActions == null) {
      politicsAi.politicalActions();
    } else {
      politicsAi.doActions(storedPoliticalActions);
      storedPoliticalActions = null;
    }
  }
}