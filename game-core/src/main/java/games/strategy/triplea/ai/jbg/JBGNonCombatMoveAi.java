package games.strategy.triplea.ai.jbg;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.Properties;
import games.strategy.triplea.ai.jbg.data.JBGBattleResult;
import games.strategy.triplea.ai.jbg.data.JBGOtherMoveOptions;
import games.strategy.triplea.ai.jbg.data.JBGPlaceTerritory;
import games.strategy.triplea.ai.jbg.data.JBGPurchaseOption;
import games.strategy.triplea.ai.jbg.data.JBGPurchaseTerritory;
import games.strategy.triplea.ai.jbg.data.JBGTerritory;
import games.strategy.triplea.ai.jbg.data.JBGTerritoryManager;
import games.strategy.triplea.ai.jbg.data.JBGTransport;
import games.strategy.triplea.ai.jbg.logging.JBGLogger;
import games.strategy.triplea.ai.jbg.util.JBGBattleUtils;
import games.strategy.triplea.ai.jbg.util.JBGMatches;
import games.strategy.triplea.ai.jbg.util.JBGMoveUtils;
import games.strategy.triplea.ai.jbg.util.JBGOddsCalculator;
import games.strategy.triplea.ai.jbg.util.JBGPurchaseUtils;
import games.strategy.triplea.ai.jbg.util.JBGSortMoveOptionsUtils;
import games.strategy.triplea.ai.jbg.util.JBGTerritoryValueUtils;
import games.strategy.triplea.ai.jbg.util.JBGTransportUtils;
import games.strategy.triplea.ai.jbg.util.JBGUtils;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.AbstractMoveDelegate;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TransportTracker;
import games.strategy.triplea.delegate.data.MoveValidationResult;
import games.strategy.triplea.delegate.move.validation.MoveValidator;
import games.strategy.triplea.delegate.remote.IMoveDelegate;
import games.strategy.triplea.util.TuvUtils;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.triplea.java.collections.CollectionUtils;

/** JBG non-combat move AI. */
@SuppressWarnings("deprecation") //JBG: disable Observable warning
class JBGNonCombatMoveAi {

  private final JBGOddsCalculator calc;
  private final JBGData jbgData;
  private GameData data;
  private GamePlayer player;
  private Map<Unit, Territory> unitTerritoryMap;
  private JBGTerritoryManager territoryManager;

  JBGNonCombatMoveAi(final AbstractJBGAi ai) {
    calc = ai.getCalc();
    jbgData = ai.getJBGData();
  }

  Map<Territory, JBGTerritory> simulateNonCombatMove(final IMoveDelegate moveDel) {
    return doNonCombatMove(null, null, moveDel);
  }

  Map<Territory, JBGTerritory> doNonCombatMove(
      final Map<Territory, JBGTerritory> initialFactoryMoveMap,
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories,
      final IMoveDelegate moveDel) {
    JBGLogger.info("Starting non-combat move phase");

    // Current data at the start of non-combat move
    data = jbgData.getData();
    player = jbgData.getPlayer();
    unitTerritoryMap = jbgData.getUnitTerritoryMap();
    territoryManager = new JBGTerritoryManager(calc, jbgData);

    // Find the max number of units that can move to each allied territory
    territoryManager.populateDefenseOptions(new ArrayList<>());

    // Find number of units in each move territory that can't move and all infra units
    findUnitsThatCantMove(purchaseTerritories, jbgData.getPurchaseOptions().getLandOptions());
    final Map<Unit, Set<Territory>> infraUnitMoveMap = findInfraUnitsThatCanMove();

    // Try to have one land unit in each territory that is bordering an enemy territory
    final List<Territory> movedOneDefenderToTerritories =
        moveOneDefenderToLandTerritoriesBorderingEnemy();

    // Determine max enemy attack units and if territories can be held
    territoryManager.populateEnemyAttackOptions(
        movedOneDefenderToTerritories, territoryManager.getDefendTerritories());
    determineIfMoveTerritoriesCanBeHeld();

    // Prioritize territories to defend
    Map<Territory, JBGTerritory> factoryMoveMap = initialFactoryMoveMap;
    final List<JBGTerritory> prioritizedTerritories = prioritizeDefendOptions(factoryMoveMap);

    // Determine which territories to defend and how many units each one needs
    final int enemyDistance =
        JBGUtils.getClosestEnemyLandTerritoryDistance(data, player, jbgData.getMyCapital());
    moveUnitsToDefendTerritories(prioritizedTerritories, enemyDistance);

    // Copy data in case capital defense needs increased
    final JBGTerritoryManager territoryManagerCopy =
        new JBGTerritoryManager(calc, jbgData, territoryManager);

    // Get list of territories that can't be held and find move value for each territory
    final List<Territory> territoriesThatCantBeHeld = territoryManager.getCantHoldTerritories();
    final Map<Territory, Double> territoryValueMap =
        JBGTerritoryValueUtils.findTerritoryValues(
            jbgData,
            player,
            territoriesThatCantBeHeld,
            new ArrayList<>(),
            new HashSet<>(territoryManager.getDefendTerritories()));
    final Map<Territory, Double> seaTerritoryValueMap =
        JBGTerritoryValueUtils.findSeaTerritoryValues(
            player, territoriesThatCantBeHeld, territoryManager.getDefendTerritories());

    // Use loop to ensure capital is protected after moves
    final Territory myCapital = jbgData.getMyCapital();
    if (myCapital != null) {
      int defenseRange = -1;
      while (true) {

        // Add value to territories near capital if necessary
        for (final Territory t : territoryManager.getDefendTerritories()) {
          double value = territoryValueMap.get(t);
          final int distance =
              data.getMap()
                  .getDistance(
                      myCapital, t, JBGMatches.territoryCanMoveLandUnits(player, data, false));
          if (distance >= 0 && distance <= defenseRange) {
            value *= 10;
          }
          territoryManager.getDefendOptions().getTerritoryMap().get(t).setValue(value);
          if (t.isWater()) {
            territoryManager
                .getDefendOptions()
                .getTerritoryMap()
                .get(t)
                .setSeaValue(seaTerritoryValueMap.get(t));
          }
        }

        moveUnitsToBestTerritories();

        // Check if capital has local land superiority
        JBGLogger.info(
            "Checking if capital has local land superiority with enemyDistance=" + enemyDistance);
        if (enemyDistance >= 2
            && enemyDistance <= 3
            && defenseRange == -1
            && !JBGBattleUtils.territoryHasLocalLandSuperiorityAfterMoves(
                jbgData,
                myCapital,
                enemyDistance,
                player,
                territoryManager.getDefendOptions().getTerritoryMap())) {
          defenseRange = enemyDistance - 1;
          territoryManager = territoryManagerCopy;
          JBGLogger.debug(
              "Capital doesn't have local land superiority so setting defensive stance");
        } else {
          break;
        }
      }
    } else {
      moveUnitsToBestTerritories();
    }

    // Determine where to move infra units
    factoryMoveMap = moveInfraUnits(factoryMoveMap, infraUnitMoveMap);

    // Log a warning if any units not assigned to a territory (skip infrastructure for now)
    for (final Unit u : territoryManager.getDefendOptions().getUnitMoveMap().keySet()) {
      if (Matches.unitIsInfrastructure().negate().test(u)) {
        JBGLogger.warn(
            player
                + ": "
                + unitTerritoryMap.get(u)
                + " has unmoved unit: "
                + u
                + " with options: "
                + territoryManager.getDefendOptions().getUnitMoveMap().get(u));
      }
    }

    // Calculate move routes and perform moves
    doMove(territoryManager.getDefendOptions().getTerritoryMap(), moveDel, data, player);

    // Log results
    JBGLogger.info("Logging results");
    logAttackMoves(prioritizedTerritories);

    territoryManager = null;
    return factoryMoveMap;
  }

  void doMove(
      final Map<Territory, JBGTerritory> moveMap,
      final IMoveDelegate moveDel,
      final GameData data,
      final GamePlayer player) {

    this.data = data;
    this.player = player;

    // Calculate move routes and perform moves
    JBGMoveUtils.doMove(
        jbgData, JBGMoveUtils.calculateMoveRoutes(jbgData, player, moveMap, false), moveDel);

    // Calculate amphib move routes and perform moves
    JBGMoveUtils.doMove(
        jbgData, JBGMoveUtils.calculateAmphibRoutes(jbgData, player, moveMap, false), moveDel);
  }

  private void findUnitsThatCantMove(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories,
      final List<JBGPurchaseOption> landPurchaseOptions) {

    JBGLogger.info("Find units that can't move");

    final Map<Territory, JBGTerritory> moveMap =
        territoryManager.getDefendOptions().getTerritoryMap();
    final Map<Unit, Set<Territory>> unitMoveMap =
        territoryManager.getDefendOptions().getUnitMoveMap();
    final List<JBGTransport> transportMapList =
        territoryManager.getDefendOptions().getTransportList();

    // Add all units that can't move (allied units, 0 move units, etc)
    for (final Territory t : moveMap.keySet()) {
      moveMap
          .get(t)
          .getCantMoveUnits()
          .addAll(
              t.getUnitCollection()
                  .getMatches(JBGMatches.unitCantBeMovedAndIsAlliedDefender(player, data, t)));
    }

    // Add all units that only have 1 move option and can't be transported
    for (final Iterator<Unit> it = unitMoveMap.keySet().iterator(); it.hasNext(); ) {
      final Unit u = it.next();
      if (unitMoveMap.get(u).size() == 1) {
        final Territory onlyTerritory = unitMoveMap.get(u).iterator().next();
        if (onlyTerritory.equals(unitTerritoryMap.get(u))) {
          boolean canBeTransported = false;
          for (final JBGTransport pad : transportMapList) {
            for (final Territory t : pad.getTransportMap().keySet()) {
              if (pad.getTransportMap().get(t).contains(onlyTerritory)) {
                canBeTransported = true;
              }
            }
            for (final Territory t : pad.getSeaTransportMap().keySet()) {
              if (pad.getSeaTransportMap().get(t).contains(onlyTerritory)) {
                canBeTransported = true;
              }
            }
          }
          if (!canBeTransported) {
            moveMap.get(onlyTerritory).getCantMoveUnits().add(u);
            it.remove();
          }
        }
      }
    }

    // Check if purchase units are known yet
    if (purchaseTerritories != null) {

      // Add all units that will be purchased
      for (final JBGPurchaseTerritory ppt : purchaseTerritories.values()) {
        for (final JBGPlaceTerritory placeTerritory : ppt.getCanPlaceTerritories()) {
          final Territory t = placeTerritory.getTerritory();
          if (moveMap.get(t) != null) {
            moveMap.get(t).getCantMoveUnits().addAll(placeTerritory.getPlaceUnits());
          }
        }
      }
    } else {

      // Add max defenders that can be purchased to each territory
      for (final Territory t : moveMap.keySet()) {
        if (JBGMatches.territoryHasNonMobileFactoryAndIsNotConqueredOwnedLand(player, data)
            .test(t)) {
          moveMap
              .get(t)
              .getCantMoveUnits()
              .addAll(
                  JBGPurchaseUtils.findMaxPurchaseDefenders(
                      jbgData, player, t, landPurchaseOptions));
        }
      }
    }

    // Log can't move units per territory
    for (final Territory t : moveMap.keySet()) {
      if (!moveMap.get(t).getCantMoveUnits().isEmpty()) {
        JBGLogger.trace(t + " has units that can't move: " + moveMap.get(t).getCantMoveUnits());
      }
    }
  }

  private Map<Unit, Set<Territory>> findInfraUnitsThatCanMove() {

    JBGLogger.info("Find non-combat infra units that can move");

    final Map<Unit, Set<Territory>> unitMoveMap =
        territoryManager.getDefendOptions().getUnitMoveMap();

    // Add all units that are infra
    final Map<Unit, Set<Territory>> infraUnitMoveMap = new HashMap<>();
    for (final Iterator<Unit> it = unitMoveMap.keySet().iterator(); it.hasNext(); ) {
      final Unit u = it.next();
      if (JBGMatches.unitCanBeMovedAndIsOwnedNonCombatInfra(player).test(u)) {
        infraUnitMoveMap.put(u, unitMoveMap.get(u));
        JBGLogger.trace(u + " is infra unit with move options: " + unitMoveMap.get(u));
        it.remove();
      }
    }
    return infraUnitMoveMap;
  }

  private List<Territory> moveOneDefenderToLandTerritoriesBorderingEnemy() {

    JBGLogger.info("Determine which territories to defend with one land unit");

    final Map<Territory, JBGTerritory> moveMap =
        territoryManager.getDefendOptions().getTerritoryMap();
    final Map<Unit, Set<Territory>> unitMoveMap =
        territoryManager.getDefendOptions().getUnitMoveMap();

    // Find land territories with no can't move units and adjacent to enemy land units
    final List<Territory> territoriesToDefendWithOneUnit = new ArrayList<>();
    for (final Territory t : moveMap.keySet()) {
      final boolean hasAlliedLandUnits =
          moveMap.get(t).getCantMoveUnits().stream()
              .anyMatch(JBGMatches.unitIsAlliedLandAndNotInfra(player, data));
      if (!t.isWater()
          && !hasAlliedLandUnits
          && JBGMatches.territoryHasNeighborOwnedByAndHasLandUnit(
                  data, JBGUtils.getPotentialEnemyPlayers(player))
              .test(t)) {
        territoriesToDefendWithOneUnit.add(t);
      }
    }
    final List<Territory> result = new ArrayList<>(territoriesToDefendWithOneUnit);

    // Sort units by number of defend options and cost
    final Map<Unit, Set<Territory>> sortedUnitMoveOptions =
        JBGSortMoveOptionsUtils.sortUnitMoveOptions(jbgData, unitMoveMap);

    // Set unit with the fewest move options in each territory
    for (final Unit unit : sortedUnitMoveOptions.keySet()) {
      if (Matches.unitIsLand().test(unit)) {
        for (final Territory t : sortedUnitMoveOptions.get(unit)) {
          final int unitValue = jbgData.getUnitValue(unit.getType());
          int production = 0;
          final TerritoryAttachment ta = TerritoryAttachment.get(t);
          if (ta != null) {
            production = ta.getProduction();
          }

          // Only defend territories that either already have units (avoid abandoning territories)
          // or where unit value is less than production + 3 (avoid sacrificing expensive units to
          // block)
          if (territoriesToDefendWithOneUnit.contains(t)
              && (unitValue <= (production + 3)
                  || Matches.territoryHasUnitsOwnedBy(player).test(t))) {
            moveMap.get(t).addUnit(unit);
            unitMoveMap.remove(unit);
            territoriesToDefendWithOneUnit.remove(t);
            JBGLogger.debug(t + ", added one land unit: " + unit);
            break;
          }
        }
        if (territoriesToDefendWithOneUnit.isEmpty()) {
          break;
        }
      }
    }

    // Only return territories that received a defender
    result.removeAll(territoriesToDefendWithOneUnit);

    return result;
  }

  private void determineIfMoveTerritoriesCanBeHeld() {

    JBGLogger.info("Find max enemy attackers and if territories can be held");

    final Map<Territory, JBGTerritory> moveMap =
        territoryManager.getDefendOptions().getTerritoryMap();
    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();

    // Determine which territories can possibly be held
    for (final Territory t : moveMap.keySet()) {
      final JBGTerritory patd = moveMap.get(t);

      // Check if no enemy attackers
      if (enemyAttackOptions.getMax(t) == null) {
        JBGLogger.debug("Territory=" + t.getName() + ", CanHold=true since has no enemy attackers");
        continue;
      }

      // Check if min defenders can hold it (not considering AA)
      final Set<Unit> enemyAttackingUnits =
          new HashSet<>(enemyAttackOptions.getMax(t).getMaxUnits());
      enemyAttackingUnits.addAll(enemyAttackOptions.getMax(t).getMaxAmphibUnits());
      patd.setMaxEnemyUnits(new ArrayList<>(enemyAttackingUnits));
      patd.setMaxEnemyBombardUnits(enemyAttackOptions.getMax(t).getMaxBombardUnits());
      final List<Unit> minDefendingUnitsAndNotAa =
          CollectionUtils.getMatches(
              patd.getCantMoveUnits(), Matches.unitIsAaForAnything().negate());
      final JBGBattleResult minResult =
          calc.calculateBattleResults(
              jbgData,
              t,
              new ArrayList<>(enemyAttackingUnits),
              minDefendingUnitsAndNotAa,
              enemyAttackOptions.getMax(t).getMaxBombardUnits());
      patd.setMinBattleResult(minResult);
      if (minResult.getTuvSwing() <= 0 && !minDefendingUnitsAndNotAa.isEmpty()) {
        JBGLogger.debug(
            "Territory="
                + t.getName()
                + ", CanHold=true"
                + ", MinDefenders="
                + minDefendingUnitsAndNotAa.size()
                + ", EnemyAttackers="
                + enemyAttackingUnits.size()
                + ", win%="
                + minResult.getWinPercentage()
                + ", EnemyTUVSwing="
                + minResult.getTuvSwing()
                + ", hasLandUnitRemaining="
                + minResult.isHasLandUnitRemaining());
        continue;
      }

      // Check if max defenders can hold it (not considering AA)
      final Set<Unit> defendingUnits = new HashSet<>(patd.getMaxUnits());
      defendingUnits.addAll(patd.getMaxAmphibUnits());
      defendingUnits.addAll(patd.getCantMoveUnits());
      final List<Unit> defendingUnitsAndNotAa =
          CollectionUtils.getMatches(defendingUnits, Matches.unitIsAaForAnything().negate());
      final JBGBattleResult result =
          calc.calculateBattleResults(
              jbgData,
              t,
              new ArrayList<>(enemyAttackingUnits),
              defendingUnitsAndNotAa,
              enemyAttackOptions.getMax(t).getMaxBombardUnits());
      int isFactory = 0;
      if (JBGMatches.territoryHasInfraFactoryAndIsLand().test(t)) {
        isFactory = 1;
      }
      int isMyCapital = 0;
      if (t.equals(jbgData.getMyCapital())) {
        isMyCapital = 1;
      }
      final List<Unit> extraUnits = new ArrayList<>(defendingUnitsAndNotAa);
      extraUnits.removeAll(minDefendingUnitsAndNotAa);
      final double extraUnitValue = TuvUtils.getTuv(extraUnits, jbgData.getUnitValueMap());
      final double holdValue = extraUnitValue / 8 * (1 + 0.5 * isFactory) * (1 + 2.0 * isMyCapital);
      if (minDefendingUnitsAndNotAa.size() != defendingUnitsAndNotAa.size()
          && (result.getTuvSwing() - holdValue) < minResult.getTuvSwing()) {
        JBGLogger.debug(
            "Territory="
                + t.getName()
                + ", CanHold=true"
                + ", MaxDefenders="
                + defendingUnitsAndNotAa.size()
                + ", EnemyAttackers="
                + enemyAttackingUnits.size()
                + ", minTUVSwing="
                + minResult.getTuvSwing()
                + ", win%="
                + result.getWinPercentage()
                + ", EnemyTUVSwing="
                + result.getTuvSwing()
                + ", hasLandUnitRemaining="
                + result.isHasLandUnitRemaining()
                + ", holdValue="
                + holdValue);
        continue;
      }

      // Can't hold territory
      patd.setCanHold(false);
      JBGLogger.debug(
          "Can't hold Territory="
              + t.getName()
              + ", MaxDefenders="
              + defendingUnitsAndNotAa.size()
              + ", EnemyAttackers="
              + enemyAttackingUnits.size()
              + ", minTUVSwing="
              + minResult.getTuvSwing()
              + ", win%="
              + result.getWinPercentage()
              + ", EnemyTUVSwing="
              + result.getTuvSwing()
              + ", hasLandUnitRemaining="
              + result.isHasLandUnitRemaining()
              + ", holdValue="
              + holdValue);
    }
  }

  private List<JBGTerritory> prioritizeDefendOptions(
      final Map<Territory, JBGTerritory> factoryMoveMap) {

    JBGLogger.info("Prioritizing territories to try to defend");

    final Map<Territory, JBGTerritory> moveMap =
        territoryManager.getDefendOptions().getTerritoryMap();
    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();

    // Calculate value of defending territory
    for (final Territory t : moveMap.keySet()) {

      // Determine if it is my capital or adjacent to my capital
      int isMyCapital = 0;
      if (t.equals(jbgData.getMyCapital())) {
        isMyCapital = 1;
      }

      // Determine if it has a factory
      int isFactory = 0;
      if (JBGMatches.territoryHasInfraFactoryAndIsLand().test(t)
          || (factoryMoveMap != null && factoryMoveMap.containsKey(t))) {
        isFactory = 1;
      }

      // Determine production value and if it is an enemy capital
      int production = 0;
      int isEnemyOrAlliedCapital = 0;
      final TerritoryAttachment ta = TerritoryAttachment.get(t);
      if (ta != null) {
        production = ta.getProduction();
        if (ta.isCapital() && !t.equals(jbgData.getMyCapital())) {
          isEnemyOrAlliedCapital = 1;
        }
      }

      // Determine neighbor value
      double neighborValue = 0;
      if (!t.isWater()) {
        final Set<Territory> landNeighbors =
            data.getMap().getNeighbors(t, Matches.territoryIsLand());
        for (final Territory neighbor : landNeighbors) {
          double neighborProduction = TerritoryAttachment.getProduction(neighbor);
          if (Matches.isTerritoryAllied(player, data).test(neighbor)) {
            neighborProduction = 0.1 * neighborProduction;
          }
          neighborValue += neighborProduction;
        }
      }

      // Determine defending unit value
      final int cantMoveUnitValue =
          TuvUtils.getTuv(moveMap.get(t).getCantMoveUnits(), jbgData.getUnitValueMap());
      double unitOwnerMultiplier = 1;
      if (moveMap.get(t).getCantMoveUnits().stream().noneMatch(Matches.unitIsOwnedBy(player))) {
        if (t.isWater()
            && moveMap.get(t).getCantMoveUnits().stream()
                .noneMatch(Matches.unitIsTransportButNotCombatTransport())) {
          unitOwnerMultiplier = 0;
        } else {
          unitOwnerMultiplier = 0.5;
        }
      }

      // Calculate defense value for prioritization
      final double territoryValue =
          unitOwnerMultiplier
              * (2.0 * production
                  + 10.0 * isFactory
                  + 0.5 * cantMoveUnitValue
                  + 0.5 * neighborValue)
              * (1 + 10.0 * isMyCapital)
              * (1 + 4.0 * isEnemyOrAlliedCapital);
      moveMap.get(t).setValue(territoryValue);
    }

    // Sort attack territories by value
    final List<JBGTerritory> prioritizedTerritories = new ArrayList<>(moveMap.values());
    prioritizedTerritories.sort(Comparator.comparingDouble(JBGTerritory::getValue).reversed());

    // Remove territories that I'm not going to try to defend
    for (final Iterator<JBGTerritory> it = prioritizedTerritories.iterator(); it.hasNext(); ) {
      final JBGTerritory patd = it.next();
      final Territory t = patd.getTerritory();
      final boolean hasFactory = JBGMatches.territoryHasInfraFactoryAndIsLand().test(t);
      final JBGBattleResult minResult = patd.getMinBattleResult();
      final int cantMoveUnitValue =
          TuvUtils.getTuv(moveMap.get(t).getCantMoveUnits(), jbgData.getUnitValueMap());
      final List<Unit> maxEnemyUnits = patd.getMaxEnemyUnits();
      final boolean isLandAndCanOnlyBeAttackedByAir =
          !t.isWater()
              && !maxEnemyUnits.isEmpty()
              && maxEnemyUnits.stream().allMatch(Matches.unitIsAir());
      final boolean isNotFactoryAndShouldHold =
          !hasFactory && (minResult.getTuvSwing() <= 0 || !minResult.isHasLandUnitRemaining());
      final boolean canAlreadyBeHeld =
          minResult.getTuvSwing() <= 0
              && minResult.getWinPercentage() < (100 - jbgData.getWinPercentage());
      final boolean isNotFactoryAndHasNoEnemyNeighbors =
          !t.isWater()
              && !hasFactory
              && !JBGMatches.territoryHasNeighborOwnedByAndHasLandUnit(
                      data, JBGUtils.getPotentialEnemyPlayers(player))
                  .test(t);
      final boolean isNotFactoryAndOnlyAmphib =
          !t.isWater()
              && !hasFactory
              && moveMap.get(t).getMaxUnits().stream().noneMatch(Matches.unitIsLand())
              && cantMoveUnitValue < 5;
      if (!patd.isCanHold()
          || patd.getValue() <= 0
          || isLandAndCanOnlyBeAttackedByAir
          || isNotFactoryAndShouldHold
          || canAlreadyBeHeld
          || isNotFactoryAndHasNoEnemyNeighbors
          || isNotFactoryAndOnlyAmphib) {
        final double tuvSwing = minResult.getTuvSwing();
        final boolean hasRemainingLandUnit = minResult.isHasLandUnitRemaining();
        JBGLogger.debug(
            "Removing territory="
                + t.getName()
                + ", value="
                + patd.getValue()
                + ", CanHold="
                + patd.isCanHold()
                + ", isLandAndCanOnlyBeAttackedByAir="
                + isLandAndCanOnlyBeAttackedByAir
                + ", isNotFactoryAndShouldHold="
                + isNotFactoryAndShouldHold
                + ", canAlreadyBeHeld="
                + canAlreadyBeHeld
                + ", isNotFactoryAndHasNoEnemyNeighbors="
                + isNotFactoryAndHasNoEnemyNeighbors
                + ", isNotFactoryAndOnlyAmphib="
                + isNotFactoryAndOnlyAmphib
                + ", tuvSwing="
                + tuvSwing
                + ", hasRemainingLandUnit="
                + hasRemainingLandUnit
                + ", maxEnemyUnits="
                + patd.getMaxEnemyUnits().size());
        it.remove();
      }
    }

    // Add best sea production territory for sea factories
    List<Territory> seaFactories =
        CollectionUtils.getMatches(
            data.getMap().getTerritories(),
            JBGMatches.territoryHasFactoryAndIsNotConqueredOwnedLand(player, data));
    seaFactories =
        CollectionUtils.getMatches(
            seaFactories,
            JBGMatches.territoryHasInfraFactoryAndIsOwnedLandAdjacentToSea(player, data));
    final Set<Territory> territoriesToCheck = new HashSet<>(seaFactories);
    for (final Territory t : seaFactories) {
      territoriesToCheck.addAll(
          data.getMap().getNeighbors(t, JBGMatches.territoryCanMoveSeaUnits(player, data, true)));
    }
    final Map<Territory, Double> territoryValueMap =
        JBGTerritoryValueUtils.findTerritoryValues(
            jbgData,
            player,
            territoryManager.getCantHoldTerritories(),
            new ArrayList<>(),
            territoriesToCheck);
    for (final Territory t : seaFactories) {
      if (territoryValueMap.get(t) >= 1) {
        continue;
      }
      final Set<Territory> neighbors =
          data.getMap().getNeighbors(t, JBGMatches.territoryCanMoveSeaUnits(player, data, true));
      double maxValue = 0;
      Territory maxTerritory = null;
      for (final Territory neighbor : neighbors) {
        if (moveMap.get(neighbor) != null
            && moveMap.get(neighbor).isCanHold()
            && territoryValueMap.get(neighbor) > maxValue) {
          maxTerritory = neighbor;
          maxValue = territoryValueMap.get(neighbor);
        }
      }
      if (maxTerritory != null && enemyAttackOptions.getMax(maxTerritory) != null) {
        boolean alreadyAdded = false;
        for (final JBGTerritory patd : prioritizedTerritories) {
          if (patd.getTerritory().equals(maxTerritory)) {
            alreadyAdded = true;
            break;
          }
        }
        if (!alreadyAdded) {
          prioritizedTerritories.add(moveMap.get(maxTerritory));
        }
      }
    }

    // Log prioritized territories
    for (final JBGTerritory attackTerritoryData : prioritizedTerritories) {
      JBGLogger.debug(
          "Value="
              + attackTerritoryData.getValue()
              + ", "
              + attackTerritoryData.getTerritory().getName());
    }
    return prioritizedTerritories;
  }

  private void moveUnitsToDefendTerritories(
      final List<JBGTerritory> prioritizedTerritories, final int enemyDistance) {

    JBGLogger.info("Determine units to defend territories with");
    if (prioritizedTerritories.isEmpty()) {
      return;
    }

    final Map<Territory, JBGTerritory> moveMap =
        territoryManager.getDefendOptions().getTerritoryMap();
    final Map<Unit, Set<Territory>> unitMoveMap =
        territoryManager.getDefendOptions().getUnitMoveMap();
    final Map<Unit, Set<Territory>> transportMoveMap =
        territoryManager.getDefendOptions().getTransportMoveMap();
    final List<JBGTransport> transportMapList =
        territoryManager.getDefendOptions().getTransportList();

    // Assign units to territories by prioritization
    int numToDefend = 1;
    while (true) {

      // Reset lists
      for (final JBGTerritory t : moveMap.values()) {
        t.getTempUnits().clear();
        t.getTempAmphibAttackMap().clear();
        t.getTransportTerritoryMap().clear();
        t.setBattleResult(null);
      }

      // Determine number of territories to defend
      if (numToDefend <= 0) {
        break;
      }
      final List<JBGTerritory> territoriesToTryToDefend =
          prioritizedTerritories.subList(0, numToDefend);

      // Loop through all units and determine defend options
      final Map<Unit, Set<Territory>> unitDefendOptions = new HashMap<>();
      for (final Unit unit : unitMoveMap.keySet()) {

        // Find number of move options
        final Set<Territory> canDefendTerritories = new LinkedHashSet<>();
        for (final JBGTerritory attackTerritoryData : territoriesToTryToDefend) {
          if (unitMoveMap.get(unit).contains(attackTerritoryData.getTerritory())) {
            canDefendTerritories.add(attackTerritoryData.getTerritory());
          }
        }
        unitDefendOptions.put(unit, canDefendTerritories);
      }

      // Sort units by number of defend options and cost
      final Map<Unit, Set<Territory>> sortedUnitMoveOptions =
          JBGSortMoveOptionsUtils.sortUnitMoveOptions(jbgData, unitDefendOptions);
      final List<Unit> addedUnits = new ArrayList<>();

      // Set enough units in territories to have at least a chance of winning
      for (final Unit unit : sortedUnitMoveOptions.keySet()) {
        final boolean isAirUnit = UnitAttachment.get(unit.getType()).getIsAir();
        if (isAirUnit || Matches.unitIsCarrier().test(unit) || addedUnits.contains(unit)) {
          continue; // skip air and carrier units
        }
        final TreeMap<Double, Territory> estimatesMap = new TreeMap<>();
        for (final Territory t : sortedUnitMoveOptions.get(unit)) {
          Collection<Unit> defendingUnits =
              CollectionUtils.getMatches(
                  moveMap.get(t).getAllDefenders(),
                  JBGMatches.unitIsAlliedNotOwnedAir(player, data).negate());
          if (t.isWater()) {
            defendingUnits = moveMap.get(t).getAllDefenders();
          }
          final double estimate =
              JBGBattleUtils.estimateStrengthDifference(
                  jbgData, t, moveMap.get(t).getMaxEnemyUnits(), defendingUnits);
          estimatesMap.put(estimate, t);
        }
        if (!estimatesMap.isEmpty() && estimatesMap.lastKey() > 60) {
          final Territory minWinTerritory = estimatesMap.lastEntry().getValue();
          final List<Unit> unitsToAdd = JBGTransportUtils.getUnitsToAdd(jbgData, unit, moveMap);
          moveMap.get(minWinTerritory).addTempUnits(unitsToAdd);
          addedUnits.addAll(unitsToAdd);
        }
      }
      sortedUnitMoveOptions.keySet().removeAll(addedUnits);

      // Set non-air units in territories
      for (final Unit unit : sortedUnitMoveOptions.keySet()) {
        if (Matches.unitCanLandOnCarrier().test(unit) || addedUnits.contains(unit)) {
          continue;
        }
        Territory maxWinTerritory = null;
        double maxWinPercentage = -1;
        for (final Territory t : sortedUnitMoveOptions.get(unit)) {
          Collection<Unit> defendingUnits =
              CollectionUtils.getMatches(
                  moveMap.get(t).getAllDefenders(),
                  JBGMatches.unitIsAlliedNotOwnedAir(player, data).negate());
          if (t.isWater()) {
            defendingUnits = moveMap.get(t).getAllDefenders();
          }
          if (moveMap.get(t).getBattleResult() == null) {
            moveMap
                .get(t)
                .setBattleResult(
                    calc.estimateDefendBattleResults(
                        jbgData,
                        t,
                        moveMap.get(t).getMaxEnemyUnits(),
                        defendingUnits,
                        moveMap.get(t).getMaxEnemyBombardUnits()));
          }
          final JBGBattleResult result = moveMap.get(t).getBattleResult();
          final boolean hasFactory = JBGMatches.territoryHasInfraFactoryAndIsLand().test(t);
          if (result.getWinPercentage() > maxWinPercentage
              && ((t.equals(jbgData.getMyCapital())
                      && result.getWinPercentage() > (100 - jbgData.getWinPercentage()))
                  || (hasFactory
                      && result.getWinPercentage() > (100 - jbgData.getMinWinPercentage()))
                  || result.getTuvSwing() >= 0)) {
            maxWinTerritory = t;
            maxWinPercentage = result.getWinPercentage();
          }
        }
        if (maxWinTerritory != null) {
          moveMap.get(maxWinTerritory).setBattleResult(null);
          final List<Unit> unitsToAdd = JBGTransportUtils.getUnitsToAdd(jbgData, unit, moveMap);
          moveMap.get(maxWinTerritory).addTempUnits(unitsToAdd);
          addedUnits.addAll(unitsToAdd);

          // If carrier has dependent allied fighters then move them too
          if (Matches.unitIsCarrier().test(unit)) {
            final Territory unitTerritory = unitTerritoryMap.get(unit);
            final Map<Unit, Collection<Unit>> carrierMustMoveWith =
                MoveValidator.carrierMustMoveWith(
                    unitTerritory.getUnits(), unitTerritory, data, player);
            if (carrierMustMoveWith.containsKey(unit)) {
              moveMap.get(maxWinTerritory).getTempUnits().addAll(carrierMustMoveWith.get(unit));
            }
          }
        }
      }
      sortedUnitMoveOptions.keySet().removeAll(addedUnits);

      // Set air units in territories
      for (final Unit unit : sortedUnitMoveOptions.keySet()) {
        Territory maxWinTerritory = null;
        double maxWinPercentage = -1;
        for (final Territory t : sortedUnitMoveOptions.get(unit)) {
          if (t.isWater()
              && Matches.unitIsAir().test(unit)
              && !JBGTransportUtils.validateCarrierCapacity(
                  player, t, moveMap.get(t).getAllDefendersForCarrierCalcs(data, player), unit)) {
            continue; // skip moving air to water if not enough carrier capacity
          }
          if (!t.isWater()
              && !t.getOwner().equals(player)
              && Matches.unitIsAir().test(unit)
              && !JBGMatches.territoryHasInfraFactoryAndIsLand().test(t)) {
            continue; // skip moving air units to allied land without a factory
          }
          Collection<Unit> defendingUnits =
              CollectionUtils.getMatches(
                  moveMap.get(t).getAllDefenders(),
                  JBGMatches.unitIsAlliedNotOwnedAir(player, data).negate());
          if (t.isWater()) {
            defendingUnits = moveMap.get(t).getAllDefenders();
          }
          if (moveMap.get(t).getBattleResult() == null) {
            moveMap
                .get(t)
                .setBattleResult(
                    calc.estimateDefendBattleResults(
                        jbgData,
                        t,
                        moveMap.get(t).getMaxEnemyUnits(),
                        defendingUnits,
                        moveMap.get(t).getMaxEnemyBombardUnits()));
          }
          final JBGBattleResult result = moveMap.get(t).getBattleResult();
          final boolean hasFactory = JBGMatches.territoryHasInfraFactoryAndIsLand().test(t);
          if (result.getWinPercentage() > maxWinPercentage
              && ((t.equals(jbgData.getMyCapital())
                      && result.getWinPercentage() > (100 - jbgData.getWinPercentage()))
                  || (hasFactory
                      && result.getWinPercentage() > (100 - jbgData.getMinWinPercentage()))
                  || result.getTuvSwing() >= 0)) {
            maxWinTerritory = t;
            maxWinPercentage = result.getWinPercentage();
          }
        }
        if (maxWinTerritory != null) {
          moveMap.get(maxWinTerritory).addTempUnit(unit);
          moveMap.get(maxWinTerritory).setBattleResult(null);
          addedUnits.add(unit);
        }
      }
      sortedUnitMoveOptions.keySet().removeAll(addedUnits);

      // Loop through all my transports and see which territories they can defend from current list
      final List<Unit> alreadyMovedTransports = new ArrayList<>();
      if (!Properties.getTransportCasualtiesRestricted(data)) {
        final Map<Unit, Set<Territory>> transportDefendOptions = new HashMap<>();
        for (final Unit unit : transportMoveMap.keySet()) {

          // Find number of defend options
          final Set<Territory> canDefendTerritories = new HashSet<>();
          for (final JBGTerritory attackTerritoryData : territoriesToTryToDefend) {
            if (transportMoveMap.get(unit).contains(attackTerritoryData.getTerritory())) {
              canDefendTerritories.add(attackTerritoryData.getTerritory());
            }
          }
          if (!canDefendTerritories.isEmpty()) {
            transportDefendOptions.put(unit, canDefendTerritories);
          }
        }

        // Loop through transports with move options and determine if any naval defense needs it
        for (final Unit transport : transportDefendOptions.keySet()) {

          // Find current naval defense that needs transport if it isn't transporting units
          for (final Territory t : transportDefendOptions.get(transport)) {
            if (!TransportTracker.isTransporting(transport)) {
              final Collection<Unit> defendingUnits = moveMap.get(t).getAllDefenders();
              if (moveMap.get(t).getBattleResult() == null) {
                moveMap
                    .get(t)
                    .setBattleResult(
                        calc.estimateDefendBattleResults(
                            jbgData,
                            t,
                            moveMap.get(t).getMaxEnemyUnits(),
                            defendingUnits,
                            moveMap.get(t).getMaxEnemyBombardUnits()));
              }
              final JBGBattleResult result = moveMap.get(t).getBattleResult();
              if (result.getTuvSwing() > 0) {
                moveMap.get(t).addTempUnit(transport);
                moveMap.get(t).setBattleResult(null);
                alreadyMovedTransports.add(transport);
                JBGLogger.trace("Adding defend transport to: " + t.getName());
                break;
              }
            }
          }
        }
      }

      // Loop through all my transports and see which can make amphib move
      final Map<Unit, Set<Territory>> amphibMoveOptions = new HashMap<>();
      for (final JBGTransport proTransportData : transportMapList) {

        // If already used to defend then ignore
        if (alreadyMovedTransports.contains(proTransportData.getTransport())) {
          continue;
        }

        // Find number of amphib move options
        final Set<Territory> canAmphibMoveTerritories = new HashSet<>();
        for (final JBGTerritory attackTerritoryData : territoriesToTryToDefend) {
          if (proTransportData.getTransportMap().containsKey(attackTerritoryData.getTerritory())) {
            canAmphibMoveTerritories.add(attackTerritoryData.getTerritory());
          }
        }
        if (!canAmphibMoveTerritories.isEmpty()) {
          amphibMoveOptions.put(proTransportData.getTransport(), canAmphibMoveTerritories);
        }
      }

      // Loop through transports with amphib move options and determine if any land defense needs it
      for (final Unit transport : amphibMoveOptions.keySet()) {

        // Find current land defense results for territories that unit can amphib move
        for (final Territory t : amphibMoveOptions.get(transport)) {
          final Collection<Unit> defendingUnits = moveMap.get(t).getAllDefenders();
          if (moveMap.get(t).getBattleResult() == null) {
            moveMap
                .get(t)
                .setBattleResult(
                    calc.estimateDefendBattleResults(
                        jbgData,
                        t,
                        moveMap.get(t).getMaxEnemyUnits(),
                        defendingUnits,
                        moveMap.get(t).getMaxEnemyBombardUnits()));
          }
          final JBGBattleResult result = moveMap.get(t).getBattleResult();
          final boolean hasFactory = JBGMatches.territoryHasInfraFactoryAndIsLand().test(t);
          if ((t.equals(jbgData.getMyCapital())
                  && result.getWinPercentage() > (100 - jbgData.getWinPercentage()))
              || (hasFactory && result.getWinPercentage() > (100 - jbgData.getMinWinPercentage()))
              || result.getTuvSwing() > 0) {

            // Get all units that have already moved
            final List<Unit> alreadyMovedUnits = new ArrayList<>();
            for (final JBGTerritory t2 : moveMap.values()) {
              alreadyMovedUnits.addAll(t2.getUnits());
              alreadyMovedUnits.addAll(t2.getTempUnits());
            }

            // Find units that haven't moved and can be transported
            boolean addedAmphibUnits = false;
            for (final JBGTransport proTransportData : transportMapList) {
              if (proTransportData.getTransport().equals(transport)) {

                // Find units to transport
                final Set<Territory> territoriesCanLoadFrom =
                    proTransportData.getTransportMap().get(t);
                final List<Unit> amphibUnitsToAdd =
                    JBGTransportUtils.getUnitsToTransportFromTerritories(
                        player, transport, territoriesCanLoadFrom, alreadyMovedUnits);
                if (amphibUnitsToAdd.isEmpty()) {
                  continue;
                }

                // Find safest territory to unload from
                double minStrengthDifference = Double.POSITIVE_INFINITY;
                Territory minTerritory = null;
                final Set<Territory> territoriesToMoveTransport =
                    data.getMap()
                        .getNeighbors(t, JBGMatches.territoryCanMoveSeaUnits(player, data, false));
                final Set<Territory> loadFromTerritories = new HashSet<>();
                for (final Unit u : amphibUnitsToAdd) {
                  loadFromTerritories.add(unitTerritoryMap.get(u));
                }
                for (final Territory territoryToMoveTransport : territoriesToMoveTransport) {
                  if (proTransportData.getSeaTransportMap().containsKey(territoryToMoveTransport)
                      && proTransportData
                          .getSeaTransportMap()
                          .get(territoryToMoveTransport)
                          .containsAll(loadFromTerritories)
                      && moveMap.get(territoryToMoveTransport) != null
                      && (moveMap.get(territoryToMoveTransport).isCanHold() || hasFactory)) {
                    final List<Unit> attackers =
                        moveMap.get(territoryToMoveTransport).getMaxEnemyUnits();
                    final Collection<Unit> defenders =
                        moveMap.get(territoryToMoveTransport).getAllDefenders();
                    defenders.add(transport);
                    final double strengthDifference =
                        JBGBattleUtils.estimateStrengthDifference(
                            jbgData, territoryToMoveTransport, attackers, defenders);
                    if (strengthDifference < minStrengthDifference) {
                      minTerritory = territoryToMoveTransport;
                      minStrengthDifference = strengthDifference;
                    }
                  }
                }
                if (minTerritory != null) {

                  // Add amphib defense
                  moveMap.get(t).getTransportTerritoryMap().put(transport, minTerritory);
                  moveMap.get(t).addTempUnits(amphibUnitsToAdd);
                  moveMap.get(t).putTempAmphibAttackMap(transport, amphibUnitsToAdd);
                  moveMap.get(t).setBattleResult(null);
                  for (final Unit unit : amphibUnitsToAdd) {
                    sortedUnitMoveOptions.remove(unit);
                  }
                  JBGLogger.trace(
                      "Adding amphibious defense to: "
                          + t
                          + ", units="
                          + amphibUnitsToAdd
                          + ", unloadTerritory="
                          + minTerritory);
                  addedAmphibUnits = true;
                  break;
                }
              }
            }
            if (addedAmphibUnits) {
              break;
            }
          }
        }
      }

      // Determine if all defenses are successful
      boolean areSuccessful = true;
      boolean containsCapital = false;
      final Set<Territory> territoriesToCheck = new HashSet<>();
      for (final JBGTerritory patd : territoriesToTryToDefend) {
        final Territory t = patd.getTerritory();
        territoriesToCheck.add(t);
        final List<Unit> nonAirDefenders =
            CollectionUtils.getMatches(moveMap.get(t).getTempUnits(), Matches.unitIsNotAir());
        for (final Unit u : nonAirDefenders) {
          territoriesToCheck.add(unitTerritoryMap.get(u));
        }
      }
      final Map<Territory, Double> territoryValueMap =
          JBGTerritoryValueUtils.findTerritoryValues(
              jbgData,
              player,
              territoryManager.getCantHoldTerritories(),
              new ArrayList<>(),
              territoriesToCheck);
      JBGLogger.debug("Current number of territories: " + numToDefend);
      for (final JBGTerritory patd : territoriesToTryToDefend) {
        final Territory t = patd.getTerritory();

        // Find defense result and hold value based on used defenders TUV
        final Collection<Unit> defendingUnits = moveMap.get(t).getAllDefenders();
        moveMap
            .get(t)
            .setBattleResult(
                calc.calculateBattleResults(
                    jbgData,
                    t,
                    moveMap.get(t).getMaxEnemyUnits(),
                    defendingUnits,
                    moveMap.get(t).getMaxEnemyBombardUnits()));
        final JBGBattleResult result = patd.getBattleResult();
        int isFactory = 0;
        if (JBGMatches.territoryHasInfraFactoryAndIsLand().test(t)) {
          isFactory = 1;
        }
        int isMyCapital = 0;
        if (t.equals(jbgData.getMyCapital())) {
          isMyCapital = 1;
          containsCapital = true;
        }
        final double extraUnitValue =
            TuvUtils.getTuv(moveMap.get(t).getTempUnits(), jbgData.getUnitValueMap());
        final List<Unit> unsafeTransports = new ArrayList<>();
        for (final Unit transport : moveMap.get(t).getTransportTerritoryMap().keySet()) {
          final Territory transportTerritory =
              moveMap.get(t).getTransportTerritoryMap().get(transport);
          if (!moveMap.get(transportTerritory).isCanHold()) {
            unsafeTransports.add(transport);
          }
        }
        final int unsafeTransportValue =
            TuvUtils.getTuv(unsafeTransports, jbgData.getUnitValueMap());
        final double holdValue =
            extraUnitValue / 8 * (1 + 0.5 * isFactory) * (1 + 2.0 * isMyCapital)
                - unsafeTransportValue;

        // Find strategic value
        boolean hasHigherStrategicValue = true;
        if (!t.isWater()
            && !t.equals(jbgData.getMyCapital())
            && !JBGMatches.territoryHasInfraFactoryAndIsLand().test(t)) {
          double totalValue = 0.0;
          final List<Unit> nonAirDefenders =
              CollectionUtils.getMatches(moveMap.get(t).getTempUnits(), Matches.unitIsNotAir());
          for (final Unit u : nonAirDefenders) {
            totalValue += territoryValueMap.get(unitTerritoryMap.get(u));
          }
          final double averageValue = totalValue / nonAirDefenders.size();
          if (territoryValueMap.get(t) < averageValue) {
            hasHigherStrategicValue = false;
            JBGLogger.trace(
                t
                    + " has lower value then move from with value="
                    + territoryValueMap.get(t)
                    + ", averageMoveFromValue="
                    + averageValue);
          }
        }

        // Check if its worth defending
        if ((result.getTuvSwing() - holdValue)
                > Math.max(0, patd.getMinBattleResult().getTuvSwing())
            || (!hasHigherStrategicValue
                && (result.getTuvSwing() + extraUnitValue / 2)
                    >= patd.getMinBattleResult().getTuvSwing())) {
          areSuccessful = false;
        }
        JBGLogger.debug(
            patd.getResultString()
                + ", holdValue="
                + holdValue
                + ", minTUVSwing="
                + patd.getMinBattleResult().getTuvSwing()
                + ", hasHighStrategicValue="
                + hasHigherStrategicValue
                + ", defenders="
                + defendingUnits
                + ", attackers="
                + moveMap.get(t).getMaxEnemyUnits());
      }

      final Territory currentTerritory = prioritizedTerritories.get(numToDefend - 1).getTerritory();
      final Territory myCapital = jbgData.getMyCapital();
      if (myCapital != null) {
        // Check capital defense
        if (containsCapital
            && !currentTerritory.equals(myCapital)
            && moveMap.get(myCapital).getBattleResult().getWinPercentage()
                > (100 - jbgData.getWinPercentage())
            && !Collections.disjoint(
                moveMap.get(currentTerritory).getAllDefenders(),
                moveMap.get(myCapital).getMaxDefenders())) {
          areSuccessful = false;
          JBGLogger.debug(
              "Capital isn't safe after defense moves with winPercentage="
                  + moveMap.get(myCapital).getBattleResult().getWinPercentage());
        }

        // Check capital local superiority
        if (!currentTerritory.isWater() && enemyDistance >= 2 && enemyDistance <= 3) {
          final int distance =
              data.getMap()
                  .getDistance(
                      myCapital,
                      currentTerritory,
                      JBGMatches.territoryCanMoveLandUnits(player, data, true));
          if (distance > 0
              && (enemyDistance == distance || enemyDistance == (distance - 1))
              && !JBGBattleUtils.territoryHasLocalLandSuperiorityAfterMoves(
                  jbgData, myCapital, enemyDistance, player, moveMap)) {
            areSuccessful = false;
            JBGLogger.debug(
                "Capital doesn't have local land superiority after defense "
                    + "moves with enemyDistance="
                    + enemyDistance);
          }
        }
      }

      // Determine whether to try more territories, remove a territory, or end
      if (areSuccessful) {
        numToDefend++;
        for (final JBGTerritory patd : territoriesToTryToDefend) {
          patd.setCanAttack(true);
        }

        // Can defend all territories in list so end
        if (numToDefend > prioritizedTerritories.size()) {
          break;
        }
      } else {

        // Remove territory last territory in prioritized list since we can't hold them all
        JBGLogger.debug("Removing territory: " + currentTerritory);
        prioritizedTerritories.get(numToDefend - 1).setCanHold(false);
        prioritizedTerritories.remove(numToDefend - 1);
        if (numToDefend > prioritizedTerritories.size()) {
          numToDefend--;
        }
      }
    }

    // Add temp units to move lists
    for (final JBGTerritory t : moveMap.values()) {

      // Handle allied units such as fighters on carriers
      final List<Unit> alliedUnits =
          CollectionUtils.getMatches(t.getTempUnits(), Matches.unitIsOwnedBy(player).negate());
      for (final Unit alliedUnit : alliedUnits) {
        t.addCantMoveUnit(alliedUnit);
        t.getTempUnits().remove(alliedUnit);
      }
      t.addUnits(t.getTempUnits());
      t.putAllAmphibAttackMap(t.getTempAmphibAttackMap());
      for (final Unit u : t.getTempUnits()) {
        if (Matches.unitIsTransport().test(u)) {
          transportMoveMap.remove(u);
          transportMapList.removeIf(proTransport -> proTransport.getTransport().equals(u));
        } else {
          unitMoveMap.remove(u);
        }
      }
      for (final Unit u : t.getTempAmphibAttackMap().keySet()) {
        transportMoveMap.remove(u);
        transportMapList.removeIf(proTransport -> proTransport.getTransport().equals(u));
      }
      t.getTempUnits().clear();
      t.getTempAmphibAttackMap().clear();
    }
    JBGLogger.debug("Final number of territories: " + (numToDefend - 1));
  }

  private void moveUnitsToBestTerritories() {

    final Map<Territory, JBGTerritory> moveMap =
        territoryManager.getDefendOptions().getTerritoryMap();
    final Map<Unit, Set<Territory>> unitMoveMap =
        territoryManager.getDefendOptions().getUnitMoveMap();
    final Map<Unit, Set<Territory>> transportMoveMap =
        territoryManager.getDefendOptions().getTransportMoveMap();
    final List<JBGTransport> transportMapList =
        territoryManager.getDefendOptions().getTransportList();

    while (true) {
      JBGLogger.info("Move units to best value territories");
      final Set<Territory> territoriesToDefend = new HashSet<>();
      final Map<Unit, Set<Territory>> currentUnitMoveMap = new HashMap<>(unitMoveMap);
      final Map<Unit, Set<Territory>> currentTransportMoveMap = new HashMap<>(transportMoveMap);
      final List<JBGTransport> currentTransportMapList = new ArrayList<>(transportMapList);

      // Reset lists
      for (final JBGTerritory t : moveMap.values()) {
        t.getTempUnits().clear();
        for (final Unit transport : t.getTempAmphibAttackMap().keySet()) {
          t.getTransportTerritoryMap().remove(transport);
        }
        t.getTempAmphibAttackMap().clear();
        t.setBattleResult(null);
      }

      JBGLogger.debug("Move amphib units");

      // Transport amphib units to best territory
      for (final Iterator<JBGTransport> it = currentTransportMapList.iterator(); it.hasNext(); ) {
        final JBGTransport amphibData = it.next();
        final Unit transport = amphibData.getTransport();

        // Get all units that have already moved
        final List<Unit> alreadyMovedUnits = new ArrayList<>();
        for (final JBGTerritory t : moveMap.values()) {
          alreadyMovedUnits.addAll(t.getUnits());
          alreadyMovedUnits.addAll(t.getTempUnits());
        }

        // Transport amphib units to best land territory
        Territory maxValueTerritory = null;
        List<Unit> maxAmphibUnitsToAdd = null;
        double maxValue = Double.MIN_VALUE;
        double maxSeaValue = 0;
        Territory maxUnloadFromTerritory = null;
        for (final Territory t : amphibData.getTransportMap().keySet()) {
          if (moveMap.get(t).getValue() >= maxValue) {

            // Find units to load
            final Set<Territory> territoriesCanLoadFrom = amphibData.getTransportMap().get(t);
            final List<Unit> amphibUnitsToAdd =
                JBGTransportUtils.getUnitsToTransportThatCantMoveToHigherValue(
                    player,
                    transport,
                    territoriesCanLoadFrom,
                    alreadyMovedUnits,
                    moveMap,
                    currentUnitMoveMap,
                    moveMap.get(t).getValue());
            if (amphibUnitsToAdd.isEmpty()) {
              continue;
            }

            // Find best territory to move transport
            final Set<Territory> loadFromTerritories = new HashSet<>();
            for (final Unit u : amphibUnitsToAdd) {
              loadFromTerritories.add(unitTerritoryMap.get(u));
            }
            final Set<Territory> territoriesToMoveTransport =
                data.getMap()
                    .getNeighbors(t, JBGMatches.territoryCanMoveSeaUnits(player, data, false));
            for (final Territory territoryToMoveTransport : territoriesToMoveTransport) {
              if (amphibData.getSeaTransportMap().containsKey(territoryToMoveTransport)
                  && amphibData
                      .getSeaTransportMap()
                      .get(territoryToMoveTransport)
                      .containsAll(loadFromTerritories)
                  && moveMap.get(territoryToMoveTransport) != null
                  && moveMap.get(territoryToMoveTransport).isCanHold()
                  && (moveMap.get(t).getValue() > maxValue
                      || moveMap.get(territoryToMoveTransport).getValue() > maxSeaValue)) {
                maxValueTerritory = t;
                maxAmphibUnitsToAdd = amphibUnitsToAdd;
                maxValue = moveMap.get(t).getValue();
                maxSeaValue = moveMap.get(territoryToMoveTransport).getValue();
                maxUnloadFromTerritory = territoryToMoveTransport;
              }
            }
          }
        }
        if (maxValueTerritory != null) {
          JBGLogger.trace(
              transport
                  + " moved to "
                  + maxUnloadFromTerritory
                  + " and unloading to best land at "
                  + maxValueTerritory
                  + " with "
                  + maxAmphibUnitsToAdd
                  + ", value="
                  + maxValue);
          moveMap.get(maxValueTerritory).addTempUnits(maxAmphibUnitsToAdd);
          moveMap.get(maxValueTerritory).putTempAmphibAttackMap(transport, maxAmphibUnitsToAdd);
          moveMap
              .get(maxValueTerritory)
              .getTransportTerritoryMap()
              .put(transport, maxUnloadFromTerritory);
          currentTransportMoveMap.remove(transport);
          for (final Unit unit : maxAmphibUnitsToAdd) {
            currentUnitMoveMap.remove(unit);
          }
          territoriesToDefend.add(maxUnloadFromTerritory);
          it.remove();
          continue;
        }

        // Transport amphib units to best sea territory
        for (final Territory t : amphibData.getSeaTransportMap().keySet()) {
          if (moveMap.get(t) != null && moveMap.get(t).getValue() > maxValue) {

            // Find units to load
            final Set<Territory> territoriesCanLoadFrom = amphibData.getSeaTransportMap().get(t);
            territoriesCanLoadFrom.removeAll(
                data.getMap().getNeighbors(t)); // Don't transport adjacent units
            final List<Unit> amphibUnitsToAdd =
                JBGTransportUtils.getUnitsToTransportThatCantMoveToHigherValue(
                    player,
                    transport,
                    territoriesCanLoadFrom,
                    alreadyMovedUnits,
                    moveMap,
                    currentUnitMoveMap,
                    0.1);
            if (!amphibUnitsToAdd.isEmpty()) {
              maxValueTerritory = t;
              maxAmphibUnitsToAdd = amphibUnitsToAdd;
              maxValue = moveMap.get(t).getValue();
            }
          }
        }
        if (maxValueTerritory != null) {
          final Set<Territory> possibleUnloadTerritories =
              data.getMap()
                  .getNeighbors(
                      maxValueTerritory,
                      JBGMatches.territoryCanMoveLandUnitsAndIsAllied(player, data));
          Territory unloadToTerritory = null;
          int maxNumSeaNeighbors = 0;
          for (final Territory t : possibleUnloadTerritories) {
            final int numSeaNeighbors =
                data.getMap().getNeighbors(t, Matches.territoryIsWater()).size();
            final boolean isAdjacentToEnemy =
                JBGMatches.territoryIsOrAdjacentToEnemyNotNeutralLand(player, data).test(t);
            if (moveMap.get(t) != null
                && (moveMap.get(t).isCanHold() || !isAdjacentToEnemy)
                && numSeaNeighbors > maxNumSeaNeighbors) {
              unloadToTerritory = t;
              maxNumSeaNeighbors = numSeaNeighbors;
            }
          }
          if (unloadToTerritory != null) {
            moveMap.get(unloadToTerritory).addTempUnits(maxAmphibUnitsToAdd);
            moveMap.get(unloadToTerritory).putTempAmphibAttackMap(transport, maxAmphibUnitsToAdd);
            moveMap
                .get(unloadToTerritory)
                .getTransportTerritoryMap()
                .put(transport, maxValueTerritory);
            JBGLogger.trace(
                transport
                    + " moved to best sea at "
                    + maxValueTerritory
                    + " and unloading to "
                    + unloadToTerritory
                    + " with "
                    + maxAmphibUnitsToAdd
                    + ", value="
                    + maxValue);
          } else {
            moveMap.get(maxValueTerritory).addTempUnits(maxAmphibUnitsToAdd);
            moveMap.get(maxValueTerritory).putTempAmphibAttackMap(transport, maxAmphibUnitsToAdd);
            moveMap
                .get(maxValueTerritory)
                .getTransportTerritoryMap()
                .put(transport, maxValueTerritory);
            JBGLogger.trace(
                transport
                    + " moved to best sea at "
                    + maxValueTerritory
                    + " with "
                    + maxAmphibUnitsToAdd
                    + ", value="
                    + maxValue);
          }
          currentTransportMoveMap.remove(transport);
          for (final Unit unit : maxAmphibUnitsToAdd) {
            currentUnitMoveMap.remove(unit);
          }
          territoriesToDefend.add(maxValueTerritory);
          it.remove();
        }
      }

      JBGLogger.debug("Move empty transports to best loading territory");

      // Move remaining transports to best loading territory if safe
      // TODO: consider which territory is 'safest'
      for (final Iterator<Unit> it = currentTransportMoveMap.keySet().iterator(); it.hasNext(); ) {
        final Unit transport = it.next();
        final Territory currentTerritory = unitTerritoryMap.get(transport);
        final int moves = transport.getMovementLeft().intValue();
        if (TransportTracker.isTransporting(transport) || moves <= 0) {
          continue;
        }
        final List<JBGTerritory> priorizitedLoadTerritories = new ArrayList<>();
        for (final Territory t : moveMap.keySet()) {

          // Check if land with adjacent sea that can be reached and that I'm not already adjacent
          // to
          final boolean territoryHasTransportableUnits =
              Matches.territoryHasUnitsThatMatch(
                      JBGMatches.unitIsOwnedTransportableUnitAndCanBeLoaded(
                          player, transport, false))
                  .test(t);
          final int distance =
              data.getMap()
                  .getDistanceIgnoreEndForCondition(
                      currentTerritory, t, JBGMatches.territoryCanMoveSeaUnits(player, data, true));
          final boolean hasSeaNeighbor =
              Matches.territoryHasNeighborMatching(data, Matches.territoryIsWater()).test(t);
          final boolean hasFactory =
              JBGMatches.territoryHasInfraFactoryAndIsOwnedLand(player).test(t);
          if (!t.isWater()
              && hasSeaNeighbor
              && distance > 0
              && !(distance == 1 && territoryHasTransportableUnits && !hasFactory)) {

            // TODO: add calculation of transports vs units
            final double territoryValue = moveMap.get(t).getValue();
            final int numUnitsToLoad =
                CollectionUtils.getMatches(
                        moveMap.get(t).getAllDefenders(),
                        JBGMatches.unitIsOwnedTransportableUnit(player))
                    .size();
            final boolean hasUnconqueredFactory =
                JBGMatches.territoryHasInfraFactoryAndIsOwnedLand(player).test(t)
                    && !AbstractMoveDelegate.getBattleTracker(data).wasConquered(t);
            int factoryProduction = 0;
            if (hasUnconqueredFactory) {
              factoryProduction = TerritoryAttachment.getProduction(t);
            }
            int numTurnsAway = (distance - 1) / moves;
            if (distance <= moves) {
              numTurnsAway = 0;
            }
            final double value =
                territoryValue
                    + 0.5 * numTurnsAway
                    - 0.1 * numUnitsToLoad
                    - 0.1 * factoryProduction;
            moveMap.get(t).setLoadValue(value);
            priorizitedLoadTerritories.add(moveMap.get(t));
          }
        }

        // Sort prioritized territories
        priorizitedLoadTerritories.sort(Comparator.comparingDouble(JBGTerritory::getLoadValue));

        // Move towards best loading territory if route is safe
        for (final JBGTerritory patd : priorizitedLoadTerritories) {
          boolean movedTransport = false;
          final Set<Territory> cantHoldTerritories = new HashSet<>();
          while (true) {
            final Predicate<Territory> match =
                JBGMatches.territoryCanMoveSeaUnitsThrough(player, data, false)
                    .and(Matches.territoryIsInList(cantHoldTerritories).negate());
            final Route route =
                data.getMap()
                    .getRouteForUnits(
                        currentTerritory, patd.getTerritory(), match, List.of(transport), player);
            if (route == null) {
              break;
            }
            final List<Territory> territories = route.getAllTerritories();
            territories.remove(territories.size() - 1);
            final Territory moveToTerritory =
                territories.get(Math.min(territories.size() - 1, moves));
            final JBGTerritory patd2 = moveMap.get(moveToTerritory);
            if (patd2 != null && patd2.isCanHold()) {
              JBGLogger.trace(
                  transport
                      + " moved towards best loading territory "
                      + patd.getTerritory()
                      + " and moved to "
                      + moveToTerritory);
              patd2.addTempUnit(transport);
              territoriesToDefend.add(moveToTerritory);
              it.remove();
              movedTransport = true;
              break;
            }
            if (!cantHoldTerritories.add(moveToTerritory)) {
              break;
            }
          }
          if (movedTransport) {
            break;
          }
        }
      }

      JBGLogger.debug("Move remaining transports to safest territory");

      // Move remaining transports to safest territory
      for (final Iterator<Unit> it = currentTransportMoveMap.keySet().iterator(); it.hasNext(); ) {
        final Unit transport = it.next();

        // Get all units that have already moved
        final List<Unit> alreadyMovedUnits = new ArrayList<>();
        for (final JBGTerritory t : moveMap.values()) {
          alreadyMovedUnits.addAll(t.getUnits());
        }

        // Find safest territory
        double minStrengthDifference = Double.POSITIVE_INFINITY;
        Territory minTerritory = null;
        for (final Territory t : currentTransportMoveMap.get(transport)) {
          final List<Unit> attackers = moveMap.get(t).getMaxEnemyUnits();
          final List<Unit> defenders = moveMap.get(t).getMaxDefenders();
          defenders.removeAll(alreadyMovedUnits);
          defenders.addAll(moveMap.get(t).getUnits());
          defenders.removeAll(JBGTransportUtils.getAirThatCantLandOnCarrier(player, t, defenders));
          final double strengthDifference =
              JBGBattleUtils.estimateStrengthDifference(jbgData, t, attackers, defenders);

          // TODO: add logic to move towards closest factory
          JBGLogger.trace(
              transport
                  + " at "
                  + t
                  + ", strengthDifference="
                  + strengthDifference
                  + ", attackers="
                  + attackers
                  + ", defenders="
                  + defenders);
          if (strengthDifference < minStrengthDifference) {
            minStrengthDifference = strengthDifference;
            minTerritory = t;
          }
        }
        if (minTerritory != null) {

          // If transporting units then unload to safe territory
          // TODO: consider which is 'safest'
          if (TransportTracker.isTransporting(transport)) {
            final List<Unit> amphibUnits = TransportTracker.transporting(transport);
            final Set<Territory> possibleUnloadTerritories =
                data.getMap()
                    .getNeighbors(
                        minTerritory,
                        JBGMatches.territoryCanMoveLandUnitsAndIsAllied(player, data));
            if (!possibleUnloadTerritories.isEmpty()) {
              // Find best unload territory
              Territory unloadToTerritory = possibleUnloadTerritories.iterator().next();
              for (final Territory t : possibleUnloadTerritories) {
                if (moveMap.get(t) != null && moveMap.get(t).isCanHold()) {
                  unloadToTerritory = t;
                }
              }
              JBGLogger.trace(
                  transport
                      + " moved to safest territory at "
                      + minTerritory
                      + " and unloading to "
                      + unloadToTerritory
                      + " with "
                      + amphibUnits
                      + ", strengthDifference="
                      + minStrengthDifference);
              moveMap.get(unloadToTerritory).addTempUnits(amphibUnits);
              moveMap.get(unloadToTerritory).putTempAmphibAttackMap(transport, amphibUnits);
              moveMap
                  .get(unloadToTerritory)
                  .getTransportTerritoryMap()
                  .put(transport, minTerritory);
              for (final Unit unit : amphibUnits) {
                currentUnitMoveMap.remove(unit);
              }
              it.remove();
            } else {

              // Move transport with units since no unload options
              JBGLogger.trace(
                  transport
                      + " moved to safest territory at "
                      + minTerritory
                      + " with "
                      + amphibUnits
                      + ", strengthDifference="
                      + minStrengthDifference);
              moveMap.get(minTerritory).addTempUnits(amphibUnits);
              moveMap.get(minTerritory).putTempAmphibAttackMap(transport, amphibUnits);
              moveMap.get(minTerritory).getTransportTerritoryMap().put(transport, minTerritory);
              for (final Unit unit : amphibUnits) {
                currentUnitMoveMap.remove(unit);
              }
              it.remove();
            }
          } else {

            // If not transporting units
            JBGLogger.trace(
                transport
                    + " moved to safest territory at "
                    + minTerritory
                    + ", strengthDifference="
                    + minStrengthDifference);
            moveMap.get(minTerritory).addTempUnit(transport);
            it.remove();
          }
        }
      }

      // Get all transport final territories
      JBGMoveUtils.calculateAmphibRoutes(jbgData, player, moveMap, false);
      for (final JBGTerritory t : moveMap.values()) {
        for (final Map.Entry<Unit, Territory> entry : t.getTransportTerritoryMap().entrySet()) {
          final JBGTerritory territory = moveMap.get(entry.getValue());
          if (territory != null) {
            territory.addTempUnit(entry.getKey());
          }
        }
      }

      JBGLogger.debug("Move sea units");

      // Move sea units to defend transports
      for (final Iterator<Unit> it = currentUnitMoveMap.keySet().iterator(); it.hasNext(); ) {
        final Unit u = it.next();
        if (Matches.unitIsSea().test(u)) {
          for (final Territory t : currentUnitMoveMap.get(u)) {
            if (moveMap.get(t).isCanHold()
                && !moveMap.get(t).getAllDefenders().isEmpty()
                && moveMap.get(t).getAllDefenders().stream()
                    .anyMatch(JBGMatches.unitIsOwnedTransport(player))) {
              final List<Unit> defendingUnits =
                  CollectionUtils.getMatches(
                      moveMap.get(t).getAllDefenders(), Matches.unitIsNotLand());
              if (moveMap.get(t).getBattleResult() == null) {
                moveMap
                    .get(t)
                    .setBattleResult(
                        calc.estimateDefendBattleResults(
                            jbgData,
                            t,
                            moveMap.get(t).getMaxEnemyUnits(),
                            defendingUnits,
                            moveMap.get(t).getMaxEnemyBombardUnits()));
              }
              final JBGBattleResult result = moveMap.get(t).getBattleResult();
              JBGLogger.trace(
                  t.getName()
                      + " TUVSwing="
                      + result.getTuvSwing()
                      + ", Win%="
                      + result.getWinPercentage()
                      + ", enemyAttackers="
                      + moveMap.get(t).getMaxEnemyUnits().size()
                      + ", defenders="
                      + defendingUnits.size());
              if (result.getWinPercentage() > (100 - jbgData.getWinPercentage())
                  || result.getTuvSwing() > 0) {
                JBGLogger.trace(u + " added sea to defend transport at " + t);
                moveMap.get(t).addTempUnit(u);
                moveMap.get(t).setBattleResult(null);
                territoriesToDefend.add(t);
                it.remove();

                // If carrier has dependent allied fighters then move them too
                if (Matches.unitIsCarrier().test(u)) {
                  final Territory unitTerritory = unitTerritoryMap.get(u);
                  final Map<Unit, Collection<Unit>> carrierMustMoveWith =
                      MoveValidator.carrierMustMoveWith(
                          unitTerritory.getUnits(), unitTerritory, data, player);
                  if (carrierMustMoveWith.containsKey(u)) {
                    moveMap.get(t).getTempUnits().addAll(carrierMustMoveWith.get(u));
                  }
                }
                break;
              }
            }
          }
        }
      }

      // Move air units to defend transports
      for (final Iterator<Unit> it = currentUnitMoveMap.keySet().iterator(); it.hasNext(); ) {
        final Unit u = it.next();
        if (Matches.unitCanLandOnCarrier().test(u)) {
          for (final Territory t : currentUnitMoveMap.get(u)) {
            if (t.isWater()
                && moveMap.get(t).isCanHold()
                && !moveMap.get(t).getAllDefenders().isEmpty()
                && moveMap.get(t).getAllDefenders().stream()
                    .anyMatch(JBGMatches.unitIsOwnedTransport(player))) {
              if (!JBGTransportUtils.validateCarrierCapacity(
                  player, t, moveMap.get(t).getAllDefendersForCarrierCalcs(data, player), u)) {
                continue;
              }
              final List<Unit> defendingUnits =
                  CollectionUtils.getMatches(
                      moveMap.get(t).getAllDefenders(), Matches.unitIsNotLand());
              if (moveMap.get(t).getBattleResult() == null) {
                moveMap
                    .get(t)
                    .setBattleResult(
                        calc.estimateDefendBattleResults(
                            jbgData,
                            t,
                            moveMap.get(t).getMaxEnemyUnits(),
                            defendingUnits,
                            moveMap.get(t).getMaxEnemyBombardUnits()));
              }
              final JBGBattleResult result = moveMap.get(t).getBattleResult();
              JBGLogger.trace(
                  t.getName()
                      + " TUVSwing="
                      + result.getTuvSwing()
                      + ", Win%="
                      + result.getWinPercentage()
                      + ", enemyAttackers="
                      + moveMap.get(t).getMaxEnemyUnits().size()
                      + ", defenders="
                      + defendingUnits.size());
              if (result.getWinPercentage() > (100 - jbgData.getWinPercentage())
                  || result.getTuvSwing() > 0) {
                JBGLogger.trace(u + " added air to defend transport at " + t);
                moveMap.get(t).addTempUnit(u);
                moveMap.get(t).setBattleResult(null);
                territoriesToDefend.add(t);
                it.remove();
                break;
              }
            }
          }
        }
      }

      // Move sea units to best location or safest location
      for (final Iterator<Unit> it = currentUnitMoveMap.keySet().iterator(); it.hasNext(); ) {
        final Unit u = it.next();
        if (Matches.unitIsSea().test(u)) {
          Territory maxValueTerritory = null;
          double maxValue = 0;
          for (final Territory t : currentUnitMoveMap.get(u)) {
            if (moveMap.get(t).isCanHold()) {
              final int transports =
                  CollectionUtils.countMatches(
                      moveMap.get(t).getAllDefenders(), JBGMatches.unitIsOwnedTransport(player));
              final double value =
                  (1 + transports) * moveMap.get(t).getSeaValue()
                      + (1 + transports * 100.0) * moveMap.get(t).getValue() / 10000;
              JBGLogger.trace(
                  t
                      + ", value="
                      + value
                      + ", seaValue="
                      + moveMap.get(t).getSeaValue()
                      + ", tValue="
                      + moveMap.get(t).getValue()
                      + ", transports="
                      + transports);
              if (value > maxValue) {
                maxValue = value;
                maxValueTerritory = t;
              }
            }
          }
          if (maxValueTerritory != null) {
            JBGLogger.trace(
                u + " added to best territory " + maxValueTerritory + ", value=" + maxValue);
            moveMap.get(maxValueTerritory).addTempUnit(u);
            moveMap.get(maxValueTerritory).setBattleResult(null);
            territoriesToDefend.add(maxValueTerritory);
            it.remove();

            // If carrier has dependent allied fighters then move them too
            if (Matches.unitIsCarrier().test(u)) {
              final Territory unitTerritory = unitTerritoryMap.get(u);
              final Map<Unit, Collection<Unit>> carrierMustMoveWith =
                  MoveValidator.carrierMustMoveWith(
                      unitTerritory.getUnits(), unitTerritory, data, player);
              if (carrierMustMoveWith.containsKey(u)) {
                moveMap.get(maxValueTerritory).getTempUnits().addAll(carrierMustMoveWith.get(u));
              }
            }
          } else {

            // Get all units that have already moved
            final List<Unit> alreadyMovedUnits = new ArrayList<>();
            for (final JBGTerritory t : moveMap.values()) {
              alreadyMovedUnits.addAll(t.getUnits());
            }

            // Find safest territory
            double minStrengthDifference = Double.POSITIVE_INFINITY;
            Territory minTerritory = null;
            for (final Territory t : currentUnitMoveMap.get(u)) {
              final List<Unit> attackers = moveMap.get(t).getMaxEnemyUnits();
              final List<Unit> defenders = moveMap.get(t).getMaxDefenders();
              defenders.removeAll(alreadyMovedUnits);
              defenders.addAll(moveMap.get(t).getUnits());
              final double strengthDifference =
                  JBGBattleUtils.estimateStrengthDifference(jbgData, t, attackers, defenders);
              if (strengthDifference < minStrengthDifference) {
                minStrengthDifference = strengthDifference;
                minTerritory = t;
              }
            }
            if (minTerritory != null) {
              JBGLogger.trace(
                  u
                      + " moved to safest territory at "
                      + minTerritory
                      + ", strengthDifference="
                      + minStrengthDifference);
              moveMap.get(minTerritory).addTempUnit(u);
              moveMap.get(minTerritory).setBattleResult(null);
              it.remove();

              // If carrier has dependent allied fighters then move them too
              if (Matches.unitIsCarrier().test(u)) {
                final Territory unitTerritory = unitTerritoryMap.get(u);
                final Map<Unit, Collection<Unit>> carrierMustMoveWith =
                    MoveValidator.carrierMustMoveWith(
                        unitTerritory.getUnits(), unitTerritory, data, player);
                if (carrierMustMoveWith.containsKey(u)) {
                  moveMap.get(minTerritory).getTempUnits().addAll(carrierMustMoveWith.get(u));
                }
              }
            } else {
              final Territory currentTerritory = unitTerritoryMap.get(u);
              JBGLogger.trace(
                  u + " added to current territory since no better options at " + currentTerritory);
              moveMap.get(currentTerritory).addTempUnit(u);
              moveMap.get(currentTerritory).setBattleResult(null);
              it.remove();
            }
          }
        }
      }

      // Determine if all defenses are successful
      JBGLogger.debug("Checking if all sea moves are safe for " + territoriesToDefend);
      boolean areSuccessful = true;
      for (final Territory t : territoriesToDefend) {

        // Find result with temp units
        final Collection<Unit> defendingUnits = moveMap.get(t).getAllDefenders();
        moveMap
            .get(t)
            .setBattleResult(
                calc.calculateBattleResults(
                    jbgData,
                    t,
                    moveMap.get(t).getMaxEnemyUnits(),
                    defendingUnits,
                    moveMap.get(t).getMaxEnemyBombardUnits()));
        final JBGBattleResult result = moveMap.get(t).getBattleResult();
        int isWater = 0;
        if (t.isWater()) {
          isWater = 1;
        }
        final double extraUnitValue =
            TuvUtils.getTuv(moveMap.get(t).getTempUnits(), jbgData.getUnitValueMap());
        final double holdValue = result.getTuvSwing() - (extraUnitValue / 8 * (1 + isWater));

        // Find min result without temp units
        final List<Unit> minDefendingUnits = new ArrayList<>(defendingUnits);
        minDefendingUnits.removeAll(moveMap.get(t).getTempUnits());
        final JBGBattleResult minResult =
            calc.calculateBattleResults(
                jbgData,
                t,
                moveMap.get(t).getMaxEnemyUnits(),
                minDefendingUnits,
                moveMap.get(t).getMaxEnemyBombardUnits());

        // Check if territory is worth defending with temp units
        if (holdValue > minResult.getTuvSwing()) {
          areSuccessful = false;
          moveMap.get(t).setCanHold(false);
          moveMap.get(t).setValue(0);
          moveMap.get(t).setSeaValue(0);
          JBGLogger.trace(
              t
                  + " unable to defend so removing with holdValue="
                  + holdValue
                  + ", minTUVSwing="
                  + minResult.getTuvSwing()
                  + ", defenders="
                  + defendingUnits
                  + ", enemyAttackers="
                  + moveMap.get(t).getMaxEnemyUnits());
        }
        JBGLogger.trace(
            moveMap.get(t).getResultString()
                + ", holdValue="
                + holdValue
                + ", minTUVSwing="
                + minResult.getTuvSwing());
      }

      // Determine whether to try more territories, remove a territory, or end
      if (areSuccessful) {
        break;
      }
    }

    // Add temp units to move lists
    for (final JBGTerritory t : moveMap.values()) {

      // Handle allied units such as fighters on carriers
      final List<Unit> alliedUnits =
          CollectionUtils.getMatches(t.getTempUnits(), Matches.unitIsOwnedBy(player).negate());
      for (final Unit alliedUnit : alliedUnits) {
        t.addCantMoveUnit(alliedUnit);
        t.getTempUnits().remove(alliedUnit);
      }
      t.addUnits(t.getTempUnits());
      t.putAllAmphibAttackMap(t.getTempAmphibAttackMap());
      for (final Unit u : t.getTempUnits()) {
        if (Matches.unitIsTransport().test(u)) {
          transportMoveMap.remove(u);
          transportMapList.removeIf(proTransport -> proTransport.getTransport().equals(u));
        } else {
          unitMoveMap.remove(u);
        }
      }
      for (final Unit u : t.getTempAmphibAttackMap().keySet()) {
        transportMoveMap.remove(u);
        transportMapList.removeIf(proTransport -> proTransport.getTransport().equals(u));
      }
      t.getTempUnits().clear();
      t.getTempAmphibAttackMap().clear();
    }

    JBGLogger.info("Move land units");

    // Move land units to territory with highest value and highest transport capacity
    // TODO: consider if territory ends up being safe
    final List<Unit> addedUnits = new ArrayList<>();
    for (final Unit u : unitMoveMap.keySet()) {
      if (Matches.unitIsLand().test(u) && !addedUnits.contains(u)) {
        Territory maxValueTerritory = null;
        double maxValue = 0;
        int maxNeedAmphibUnitValue = Integer.MIN_VALUE;
        for (final Territory t : unitMoveMap.get(u)) {
          if (moveMap.get(t).isCanHold() && moveMap.get(t).getValue() >= maxValue) {

            // Find transport capacity of neighboring (distance 1) transports
            final List<Unit> transports1 = new ArrayList<>();
            final Set<Territory> seaNeighbors =
                data.getMap()
                    .getNeighbors(t, JBGMatches.territoryCanMoveSeaUnits(player, data, true));
            for (final Territory neighborTerritory : seaNeighbors) {
              if (moveMap.containsKey(neighborTerritory)) {
                transports1.addAll(
                    CollectionUtils.getMatches(
                        moveMap.get(neighborTerritory).getAllDefenders(),
                        JBGMatches.unitIsOwnedTransport(player)));
              }
            }
            int transportCapacity1 = 0;
            for (final Unit transport : transports1) {
              transportCapacity1 += UnitAttachment.get(transport.getType()).getTransportCapacity();
            }

            // Find transport capacity of nearby (distance 2) transports
            final List<Unit> transports2 = new ArrayList<>();
            final Set<Territory> nearbySeaTerritories =
                data.getMap()
                    .getNeighbors(t, 2, JBGMatches.territoryCanMoveSeaUnits(player, data, true));
            nearbySeaTerritories.removeAll(seaNeighbors);
            for (final Territory neighborTerritory : nearbySeaTerritories) {
              if (moveMap.containsKey(neighborTerritory)) {
                transports2.addAll(
                    CollectionUtils.getMatches(
                        moveMap.get(neighborTerritory).getAllDefenders(),
                        JBGMatches.unitIsOwnedTransport(player)));
              }
            }
            int transportCapacity2 = 0;
            for (final Unit transport : transports2) {
              transportCapacity2 += UnitAttachment.get(transport.getType()).getTransportCapacity();
            }
            final List<Unit> unitsToTransport =
                CollectionUtils.getMatches(
                    moveMap.get(t).getAllDefenders(),
                    JBGMatches.unitIsOwnedTransportableUnit(player));

            // Find transport cost of potential amphib units
            int transportCost = 0;
            for (final Unit unit : unitsToTransport) {
              transportCost += UnitAttachment.get(unit.getType()).getTransportCost();
            }

            // Find territory that needs amphib units that most
            int hasFactory = 0;
            if (JBGMatches.territoryHasInfraFactoryAndIsOwnedLandAdjacentToSea(player, data)
                .test(t)) {
              hasFactory = 1;
            }
            final int neededNeighborTransportValue =
                Math.max(0, transportCapacity1 - transportCost);
            final int neededNearbyTransportValue =
                Math.max(0, transportCapacity1 + transportCapacity2 - transportCost);
            final int needAmphibUnitValue =
                1000 * neededNeighborTransportValue
                    + 100 * neededNearbyTransportValue
                    + (1 + 10 * hasFactory)
                        * data.getMap()
                            .getNeighbors(
                                t, JBGMatches.territoryCanMoveSeaUnits(player, data, true))
                            .size();
            if (moveMap.get(t).getValue() > maxValue
                || needAmphibUnitValue > maxNeedAmphibUnitValue) {
              maxValue = moveMap.get(t).getValue();
              maxNeedAmphibUnitValue = needAmphibUnitValue;
              maxValueTerritory = t;
            }
          }
        }
        if (maxValueTerritory != null) {
          JBGLogger.trace(
              u
                  + " moved to "
                  + maxValueTerritory
                  + " with value="
                  + maxValue
                  + ", numNeededTransportUnits="
                  + maxNeedAmphibUnitValue);
          final List<Unit> unitsToAdd = JBGTransportUtils.getUnitsToAdd(jbgData, u, moveMap);
          moveMap.get(maxValueTerritory).addUnits(unitsToAdd);
          addedUnits.addAll(unitsToAdd);
        }
      }
    }
    unitMoveMap.keySet().removeAll(addedUnits);

    // Move land units towards nearest factory that is adjacent to the sea
    final Set<Territory> myFactoriesAdjacentToSea =
        new HashSet<>(
            CollectionUtils.getMatches(
                data.getMap().getTerritories(),
                JBGMatches.territoryHasInfraFactoryAndIsOwnedLandAdjacentToSea(player, data)));
    for (final Unit u : unitMoveMap.keySet()) {
      if (Matches.unitIsLand().test(u) && !addedUnits.contains(u)) {
        int minDistance = Integer.MAX_VALUE;
        Territory minTerritory = null;
        for (final Territory t : unitMoveMap.get(u)) {
          if (moveMap.get(t).isCanHold()) {
            for (final Territory factory : myFactoriesAdjacentToSea) {
              int distance =
                  data.getMap()
                      .getDistance(
                          t, factory, JBGMatches.territoryCanMoveLandUnits(player, data, true));
              if (distance < 0) {
                distance = 10 * data.getMap().getDistance(t, factory);
              }
              if (distance >= 0 && distance < minDistance) {
                minDistance = distance;
                minTerritory = t;
              }
            }
          }
        }
        if (minTerritory != null) {
          JBGLogger.trace(
              u.getType().getName()
                  + " moved towards closest factory adjacent to sea at "
                  + minTerritory.getName());
          final List<Unit> unitsToAdd = JBGTransportUtils.getUnitsToAdd(jbgData, u, moveMap);
          moveMap.get(minTerritory).addUnits(unitsToAdd);
          addedUnits.addAll(unitsToAdd);
        }
      }
    }
    unitMoveMap.keySet().removeAll(addedUnits);

    JBGLogger.info("Move land units to safest territory");

    // Move any remaining land units to safest territory (this is rarely used)
    for (final Unit u : unitMoveMap.keySet()) {
      if (Matches.unitIsLand().test(u) && !addedUnits.contains(u)) {

        // Get all units that have already moved
        final List<Unit> alreadyMovedUnits =
            moveMap.values().stream()
                .map(JBGTerritory::getUnits)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        // Find safest territory
        double minStrengthDifference = Double.POSITIVE_INFINITY;
        Territory minTerritory = null;
        for (final Territory t : unitMoveMap.get(u)) {
          final List<Unit> attackers = moveMap.get(t).getMaxEnemyUnits();
          final List<Unit> defenders = moveMap.get(t).getMaxDefenders();
          defenders.removeAll(alreadyMovedUnits);
          defenders.addAll(moveMap.get(t).getUnits());
          final double strengthDifference =
              JBGBattleUtils.estimateStrengthDifference(jbgData, t, attackers, defenders);
          if (strengthDifference < minStrengthDifference) {
            minStrengthDifference = strengthDifference;
            minTerritory = t;
          }
        }
        if (minTerritory != null) {
          JBGLogger.debug(
              u.getType().getName()
                  + " moved to safest territory at "
                  + minTerritory.getName()
                  + " with strengthDifference="
                  + minStrengthDifference);
          final List<Unit> unitsToAdd = JBGTransportUtils.getUnitsToAdd(jbgData, u, moveMap);
          moveMap.get(minTerritory).addUnits(unitsToAdd);
          addedUnits.addAll(unitsToAdd);
        }
      }
    }
    unitMoveMap.keySet().removeAll(addedUnits);

    JBGLogger.info("Move air units");

    // Get list of territories that can't be held
    final List<Territory> territoriesThatCantBeHeld =
        moveMap.entrySet().stream()
            .filter(e -> !e.getValue().isCanHold())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

    // Move air units to safe territory with most attack options
    for (final Iterator<Unit> it = unitMoveMap.keySet().iterator(); it.hasNext(); ) {
      final Unit u = it.next();
      if (Matches.unitIsNotAir().test(u)) {
        continue;
      }
      double maxAirValue = 0;
      Territory maxTerritory = null;
      for (final Territory t : unitMoveMap.get(u)) {
        if (!moveMap.get(t).isCanHold()) {
          continue;
        }
        if (t.isWater()
            && !JBGTransportUtils.validateCarrierCapacity(
                player, t, moveMap.get(t).getAllDefendersForCarrierCalcs(data, player), u)) {
          JBGLogger.trace(t + " already at MAX carrier capacity");
          continue;
        }

        // Check to see if the territory is safe
        final Collection<Unit> defendingUnits = moveMap.get(t).getAllDefenders();
        defendingUnits.add(u);
        if (moveMap.get(t).getBattleResult() == null) {
          moveMap
              .get(t)
              .setBattleResult(
                  calc.calculateBattleResults(
                      jbgData,
                      t,
                      moveMap.get(t).getMaxEnemyUnits(),
                      defendingUnits,
                      moveMap.get(t).getMaxEnemyBombardUnits()));
        }
        final JBGBattleResult result = moveMap.get(t).getBattleResult();
        JBGLogger.trace(
            t
                + ", TUVSwing="
                + result.getTuvSwing()
                + ", win%="
                + result.getWinPercentage()
                + ", defendingUnits="
                + defendingUnits
                + ", enemyAttackers="
                + moveMap.get(t).getMaxEnemyUnits());
        if (result.getWinPercentage() >= jbgData.getMinWinPercentage()
            || result.getTuvSwing() > 0) {
          moveMap.get(t).setCanHold(false);
          continue;
        }

        // Determine if territory can be held with owned units
        final List<Unit> myDefenders =
            CollectionUtils.getMatches(defendingUnits, Matches.unitIsOwnedBy(player));
        final JBGBattleResult result2 =
            calc.calculateBattleResults(
                jbgData,
                t,
                moveMap.get(t).getMaxEnemyUnits(),
                myDefenders,
                moveMap.get(t).getMaxEnemyBombardUnits());
        int cantHoldWithoutAllies = 0;
        if (result2.getWinPercentage() >= jbgData.getMinWinPercentage()
            || result2.getTuvSwing() > 0) {
          cantHoldWithoutAllies = 1;
        }

        // Find number of potential attack options next turn
        final int range = u.getMaxMovementAllowed();
        final Set<Territory> possibleAttackTerritories =
            data.getMap()
                .getNeighbors(
                    t, range / 2, JBGMatches.territoryCanMoveAirUnits(player, data, true));
        final int numEnemyAttackTerritories =
            CollectionUtils.countMatches(
                possibleAttackTerritories, JBGMatches.territoryIsEnemyNotNeutralLand(player, data));
        final int numLandAttackTerritories =
            CollectionUtils.countMatches(
                possibleAttackTerritories,
                JBGMatches.territoryIsEnemyOrCantBeHeldAndIsAdjacentToMyLandUnits(
                    player, data, territoriesThatCantBeHeld));
        final int numSeaAttackTerritories =
            CollectionUtils.countMatches(
                possibleAttackTerritories,
                Matches.territoryHasEnemySeaUnits(player, data)
                    .and(
                        Matches.territoryHasUnitsThatMatch(
                            Matches.unitHasSubBattleAbilities().negate())));
        final Set<Territory> possibleMoveTerritories =
            data.getMap()
                .getNeighbors(t, range, JBGMatches.territoryCanMoveAirUnits(player, data, true));
        final int numNearbyEnemyTerritories =
            CollectionUtils.countMatches(
                possibleMoveTerritories, JBGMatches.territoryIsEnemyNotNeutralLand(player, data));

        // Check if number of attack territories and value are max
        final int isntFactory = JBGMatches.territoryHasInfraFactoryAndIsLand().test(t) ? 0 : 1;
        final int hasOwnedCarrier =
            moveMap.get(t).getAllDefenders().stream()
                    .anyMatch(JBGMatches.unitIsOwnedCarrier(player))
                ? 1
                : 0;
        final double airValue =
            (200.0 * numSeaAttackTerritories
                    + 100.0 * numLandAttackTerritories
                    + 10.0 * numEnemyAttackTerritories
                    + numNearbyEnemyTerritories)
                / (1 + cantHoldWithoutAllies)
                / (1 + (double) cantHoldWithoutAllies * isntFactory)
                * (1 + hasOwnedCarrier);
        if (airValue > maxAirValue) {
          maxAirValue = airValue;
          maxTerritory = t;
        }
        JBGLogger.trace(
            "Safe territory: "
                + t
                + ", airValue="
                + airValue
                + ", numLandAttackOptions="
                + numLandAttackTerritories
                + ", numSeaAttackTerritories="
                + numSeaAttackTerritories
                + ", numEnemyAttackTerritories="
                + numEnemyAttackTerritories);
      }
      if (maxTerritory != null) {
        JBGLogger.debug(
            u.getType().getName()
                + " added to safe territory with most attack options "
                + maxTerritory
                + ", maxAirValue="
                + maxAirValue);
        moveMap.get(maxTerritory).addUnit(u);
        moveMap.get(maxTerritory).setBattleResult(null);
        it.remove();
      }
    }

    // Move air units to safest territory
    for (final Iterator<Unit> it = unitMoveMap.keySet().iterator(); it.hasNext(); ) {
      final Unit u = it.next();
      if (Matches.unitIsNotAir().test(u)) {
        continue;
      }
      double minStrengthDifference = Double.POSITIVE_INFINITY;
      Territory minTerritory = null;
      for (final Territory t : unitMoveMap.get(u)) {
        if (t.isWater()
            && !JBGTransportUtils.validateCarrierCapacity(
                player, t, moveMap.get(t).getAllDefendersForCarrierCalcs(data, player), u)) {
          JBGLogger.trace(t + " already at MAX carrier capacity");
          continue;
        }
        final List<Unit> attackers = moveMap.get(t).getMaxEnemyUnits();
        final Collection<Unit> defenders = moveMap.get(t).getAllDefenders();
        defenders.add(u);
        final double strengthDifference =
            JBGBattleUtils.estimateStrengthDifference(jbgData, t, attackers, defenders);
        JBGLogger.trace(
            "Unsafe territory: " + t + " with strengthDifference=" + strengthDifference);
        if (strengthDifference < minStrengthDifference) {
          minStrengthDifference = strengthDifference;
          minTerritory = t;
        }
      }
      if (minTerritory != null) {
        JBGLogger.debug(
            u.getType().getName()
                + " added to safest territory at "
                + minTerritory
                + " with strengthDifference="
                + minStrengthDifference);
        moveMap.get(minTerritory).addUnit(u);
        it.remove();
      }
    }
  }

  private Map<Territory, JBGTerritory> moveInfraUnits(
      final Map<Territory, JBGTerritory> initialFactoryMoveMap,
      final Map<Unit, Set<Territory>> infraUnitMoveMap) {
    JBGLogger.info("Determine where to move infra units");

    final Map<Territory, JBGTerritory> moveMap =
        territoryManager.getDefendOptions().getTerritoryMap();

    // Move factory units
    Map<Territory, JBGTerritory> factoryMoveMap = initialFactoryMoveMap;
    if (factoryMoveMap == null) {
      JBGLogger.debug("Creating factory move map");

      // Determine and store where to move factories
      factoryMoveMap = new HashMap<>();
      for (final Iterator<Unit> it = infraUnitMoveMap.keySet().iterator(); it.hasNext(); ) {
        final Unit u = it.next();

        // Only check factory units
        if (Matches.unitCanProduceUnits().test(u)) {
          Territory maxValueTerritory = null;
          double maxValue = 0;
          for (final Territory t : infraUnitMoveMap.get(u)) {
            if (!moveMap.get(t).isCanHold()) {
              continue;
            }

            // Check if territory is safe after all current moves
            if (moveMap.get(t).getBattleResult() == null) {
              final Collection<Unit> defendingUnits = moveMap.get(t).getAllDefenders();
              moveMap
                  .get(t)
                  .setBattleResult(
                      calc.calculateBattleResults(
                          jbgData,
                          t,
                          moveMap.get(t).getMaxEnemyUnits(),
                          defendingUnits,
                          moveMap.get(t).getMaxEnemyBombardUnits()));
            }
            final JBGBattleResult result = moveMap.get(t).getBattleResult();
            if (result.getWinPercentage() >= jbgData.getMinWinPercentage()
                || result.getTuvSwing() > 0) {
              moveMap.get(t).setCanHold(false);
              continue;
            }

            // Find value by checking if territory is not conquered and doesn't already have a
            // factory
            final List<Unit> units = new ArrayList<>(moveMap.get(t).getCantMoveUnits());
            units.addAll(moveMap.get(t).getUnits());
            final int production = TerritoryAttachment.get(t).getProduction();
            double value = 0.1 * moveMap.get(t).getValue();
            if (JBGMatches.territoryIsNotConqueredOwnedLand(player, data).test(t)
                && units.stream().noneMatch(Matches.unitCanProduceUnitsAndIsInfrastructure())) {
              value = moveMap.get(t).getValue() * production + 0.01 * production;
            }
            JBGLogger.trace(
                t.getName()
                    + " has value="
                    + value
                    + ", strategicValue="
                    + moveMap.get(t).getValue()
                    + ", production="
                    + production);
            if (value > maxValue) {
              maxValue = value;
              maxValueTerritory = t;
            }
          }
          if (maxValueTerritory != null) {
            JBGLogger.debug(
                u.getType().getName()
                    + " moved to "
                    + maxValueTerritory.getName()
                    + " with value="
                    + maxValue);
            moveMap.get(maxValueTerritory).addUnit(u);
            if (factoryMoveMap.containsKey(maxValueTerritory)) {
              factoryMoveMap.get(maxValueTerritory).addUnit(u);
            } else {
              final JBGTerritory patd = new JBGTerritory(maxValueTerritory, jbgData);
              patd.addUnit(u);
              factoryMoveMap.put(maxValueTerritory, patd);
            }
            it.remove();
          }
        }
      }
    } else {
      JBGLogger.debug("Using stored factory move map");

      // Transfer stored factory moves to move map
      for (final Territory t : factoryMoveMap.keySet()) {
        moveMap.get(t).addUnits(factoryMoveMap.get(t).getUnits());
      }
    }
    JBGLogger.debug("Move infra AA units");

    // Move AA units
    for (final Iterator<Unit> it = infraUnitMoveMap.keySet().iterator(); it.hasNext(); ) {
      final Unit u = it.next();
      final Territory currentTerritory = unitTerritoryMap.get(u);

      // Only check AA units whose territory can't be held and don't have factories
      if (Matches.unitIsAaForAnything().test(u)
          && !moveMap.get(currentTerritory).isCanHold()
          && !JBGMatches.territoryHasInfraFactoryAndIsLand().test(currentTerritory)) {
        Territory maxValueTerritory = null;
        double maxValue = 0;
        for (final Territory t : infraUnitMoveMap.get(u)) {
          if (!moveMap.get(t).isCanHold()) {
            continue;
          }

          // Consider max stack of 1 AA in classic
          final Route r =
              data.getMap()
                  .getRouteForUnit(
                      currentTerritory,
                      t,
                      JBGMatches.territoryCanMoveLandUnitsThrough(
                          player, data, u, currentTerritory, false, new ArrayList<>()),
                      u,
                      player);
          final MoveValidationResult mvr =
              new MoveValidator(data)
                  .validateMove(new MoveDescription(List.of(u), r), player, true, null);
          if (!mvr.isMoveValid()) {
            continue;
          }

          // Find value and try to move to territory that doesn't already have AA
          final List<Unit> units = new ArrayList<>(moveMap.get(t).getCantMoveUnits());
          units.addAll(moveMap.get(t).getUnits());
          final boolean hasAa = units.stream().anyMatch(Matches.unitIsAaForAnything());
          double value = moveMap.get(t).getValue();
          if (hasAa) {
            value *= 0.01;
          }
          JBGLogger.trace(t.getName() + " has value=" + value);
          if (value > maxValue) {
            maxValue = value;
            maxValueTerritory = t;
          }
        }
        if (maxValueTerritory != null) {
          JBGLogger.debug(
              u.getType().getName()
                  + " moved to "
                  + maxValueTerritory.getName()
                  + " with value="
                  + maxValue);
          moveMap.get(maxValueTerritory).addUnit(u);
          it.remove();
        }
      }
    }
    return factoryMoveMap;
  }

  private void logAttackMoves(final List<JBGTerritory> prioritizedTerritories) {

    final Map<Territory, JBGTerritory> moveMap =
        territoryManager.getDefendOptions().getTerritoryMap();

    // Print prioritization
    JBGLogger.debug("Prioritized territories:");
    for (final JBGTerritory attackTerritoryData : prioritizedTerritories) {
      JBGLogger.trace(
          "  "
              + attackTerritoryData.getValue()
              + "  "
              + attackTerritoryData.getTerritory().getName());
    }

    // Print enemy territories with enemy units vs my units
    JBGLogger.debug("Territories that can be attacked:");
    int count = 0;
    for (final Territory t : moveMap.keySet()) {
      count++;
      JBGLogger.trace(count + ". ---" + t.getName());
      final Set<Unit> combinedUnits = new HashSet<>(moveMap.get(t).getMaxUnits());
      combinedUnits.addAll(moveMap.get(t).getMaxAmphibUnits());
      combinedUnits.addAll(moveMap.get(t).getCantMoveUnits());
      JBGLogger.trace("  --- My max units ---");
      final Map<String, Integer> printMap = new HashMap<>();
      for (final Unit unit : combinedUnits) {
        if (printMap.containsKey(unit.toStringNoOwner())) {
          printMap.put(unit.toStringNoOwner(), printMap.get(unit.toStringNoOwner()) + 1);
        } else {
          printMap.put(unit.toStringNoOwner(), 1);
        }
      }
      for (final String key : printMap.keySet()) {
        JBGLogger.trace("    " + printMap.get(key) + " " + key);
      }
      JBGLogger.trace("  --- My max amphib units ---");
      final Map<String, Integer> printMap5 = new HashMap<>();
      for (final Unit unit : moveMap.get(t).getMaxAmphibUnits()) {
        if (printMap5.containsKey(unit.toStringNoOwner())) {
          printMap5.put(unit.toStringNoOwner(), printMap5.get(unit.toStringNoOwner()) + 1);
        } else {
          printMap5.put(unit.toStringNoOwner(), 1);
        }
      }
      for (final String key : printMap5.keySet()) {
        JBGLogger.trace("    " + printMap5.get(key) + " " + key);
      }
      final List<Unit> units3 = moveMap.get(t).getUnits();
      JBGLogger.trace("  --- My actual units ---");
      final Map<String, Integer> printMap3 = new HashMap<>();
      for (final Unit unit : units3) {
        if (unit == null) {
          continue;
        }
        if (printMap3.containsKey(unit.toStringNoOwner())) {
          printMap3.put(unit.toStringNoOwner(), printMap3.get(unit.toStringNoOwner()) + 1);
        } else {
          printMap3.put(unit.toStringNoOwner(), 1);
        }
      }
      for (final String key : printMap3.keySet()) {
        JBGLogger.trace("    " + printMap3.get(key) + " " + key);
      }
      JBGLogger.trace("  --- Enemy units ---");
      final Map<String, Integer> printMap2 = new HashMap<>();
      final List<Unit> units2 = moveMap.get(t).getMaxEnemyUnits();
      for (final Unit unit : units2) {
        if (printMap2.containsKey(unit.toStringNoOwner())) {
          printMap2.put(unit.toStringNoOwner(), printMap2.get(unit.toStringNoOwner()) + 1);
        } else {
          printMap2.put(unit.toStringNoOwner(), 1);
        }
      }
      for (final String key : printMap2.keySet()) {
        JBGLogger.trace("    " + printMap2.get(key) + " " + key);
      }
      JBGLogger.trace("  --- Enemy bombard units ---");
      final Map<String, Integer> printMap4 = new HashMap<>();
      final Set<Unit> units4 = moveMap.get(t).getMaxEnemyBombardUnits();
      for (final Unit unit : units4) {
        if (printMap4.containsKey(unit.toStringNoOwner())) {
          printMap4.put(unit.toStringNoOwner(), printMap4.get(unit.toStringNoOwner()) + 1);
        } else {
          printMap4.put(unit.toStringNoOwner(), 1);
        }
      }
      for (final String key : printMap4.keySet()) {
        JBGLogger.trace("    " + printMap4.get(key) + " " + key);
      }
    }
  }
}
