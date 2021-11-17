package games.strategy.triplea.ai.jbg.data;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.Properties;
import games.strategy.triplea.ai.jbg.JBGData;
import games.strategy.triplea.ai.jbg.logging.JBGLogger;
import games.strategy.triplea.ai.jbg.util.JBGBattleUtils;
import games.strategy.triplea.ai.jbg.util.JBGMatches;
import games.strategy.triplea.ai.jbg.util.JBGOddsCalculator;
import games.strategy.triplea.ai.jbg.util.JBGTransportUtils;
import games.strategy.triplea.ai.jbg.util.JBGUtils;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TerritoryEffectHelper;
import games.strategy.triplea.delegate.TransportTracker;
import games.strategy.triplea.delegate.battle.ScrambleLogic;
import games.strategy.triplea.delegate.move.validation.MoveValidator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.triplea.java.collections.CollectionUtils;
import org.triplea.util.Tuple;

/** Manages info about territories. */
@SuppressWarnings("deprecation") //JBG: disable Observable warning
public class JBGTerritoryManager {

  private final JBGOddsCalculator calc;
  private final JBGData jbgData;
  private final GamePlayer player;

  private JBGMyMoveOptions attackOptions;
  private JBGMyMoveOptions potentialAttackOptions;
  private JBGMyMoveOptions defendOptions;
  private JBGOtherMoveOptions alliedAttackOptions;
  private JBGOtherMoveOptions enemyDefendOptions;
  private JBGOtherMoveOptions enemyAttackOptions;

  //default value of rushingMode
  static final boolean isRushingDefault = true; 

  public JBGTerritoryManager(final JBGOddsCalculator calc, final JBGData jbgData) {
    this.calc = calc;
    this.jbgData = jbgData;
    player = jbgData.getPlayer();
    attackOptions = new JBGMyMoveOptions();
    potentialAttackOptions = new JBGMyMoveOptions();
    defendOptions = new JBGMyMoveOptions();
    alliedAttackOptions = new JBGOtherMoveOptions();
    enemyDefendOptions = new JBGOtherMoveOptions();
    enemyAttackOptions = new JBGOtherMoveOptions();
  }

  public JBGTerritoryManager(
      final JBGOddsCalculator calc,
      final JBGData jbgData,
      final JBGTerritoryManager territoryManager) {
    this(calc, jbgData);
    attackOptions = new JBGMyMoveOptions(territoryManager.attackOptions, jbgData);
    potentialAttackOptions = new JBGMyMoveOptions(territoryManager.potentialAttackOptions, jbgData);
    defendOptions = new JBGMyMoveOptions(territoryManager.defendOptions, jbgData);
    alliedAttackOptions = territoryManager.getAlliedAttackOptions();
    enemyDefendOptions = territoryManager.getEnemyDefendOptions();
    enemyAttackOptions = territoryManager.getEnemyAttackOptions();
  }

  /** Sets 'alliedAttackOptions' field to possible available attack options. */
  public void populateAttackOptions() {
    populateAttackOptions(isRushingDefault);
  }
  public void populateAttackOptions(final boolean rushingMode) {
    findAttackOptions(
        jbgData,
        player,
        jbgData.getMyUnitTerritories(),
        attackOptions.getTerritoryMap(),
        attackOptions.getUnitMoveMap(),
        attackOptions.getTransportMoveMap(),
        attackOptions.getBombardMap(),
        attackOptions.getTransportList(),
        new ArrayList<>(),
        new ArrayList<>(),
        new ArrayList<>(),
        false,
        false,
        rushingMode);
    findBombingOptions();
    alliedAttackOptions = findAlliedAttackOptions(player, rushingMode);
  }

  public void populatePotentialAttackOptions() {
    populatePotentialAttackOptions(isRushingDefault);
  }
  public void populatePotentialAttackOptions(final boolean rushingMode) {
    findPotentialAttackOptions(
        jbgData,
        player,
        jbgData.getMyUnitTerritories(),
        potentialAttackOptions.getTerritoryMap(),
        potentialAttackOptions.getUnitMoveMap(),
        potentialAttackOptions.getTransportMoveMap(),
        potentialAttackOptions.getBombardMap(),
        potentialAttackOptions.getTransportList(),
        rushingMode);
  }

  public void populateDefenseOptions(final List<Territory> clearedTerritories) {
    populateDefenseOptions(clearedTerritories, isRushingDefault);
  }
  public void populateDefenseOptions(final List<Territory> clearedTerritories, final boolean rushingMode) {
    findDefendOptions(
        jbgData,
        player,
        jbgData.getMyUnitTerritories(),
        defendOptions.getTerritoryMap(),
        defendOptions.getUnitMoveMap(),
        defendOptions.getTransportMoveMap(),
        defendOptions.getTransportList(),
        clearedTerritories,
        false,
        rushingMode);
  }

  public void populateEnemyAttackOptions(
      final List<Territory> clearedTerritories, final List<Territory> territoriesToCheck) {
    populateEnemyAttackOptions(clearedTerritories, territoriesToCheck, isRushingDefault);
  }
  public void populateEnemyAttackOptions(
      final List<Territory> clearedTerritories, final List<Territory> territoriesToCheck, final boolean rushingMode) {
    enemyAttackOptions =
        findEnemyAttackOptions(jbgData, player, clearedTerritories, territoriesToCheck, rushingMode);
  }

  public void populateEnemyDefenseOptions() {
    populateEnemyDefenseOptions(isRushingDefault);
  }
  public void populateEnemyDefenseOptions(final boolean rushingMode) {
    if (rushingMode) return;
    findScrambleOptions(jbgData, player, attackOptions.getTerritoryMap());
    enemyDefendOptions = findEnemyDefendOptions(jbgData, player, rushingMode);
  }

  public List<JBGTerritory> removeTerritoriesThatCantBeConquered() {
    return removeTerritoriesThatCantBeConquered(
        player,
        attackOptions.getTerritoryMap(),
        attackOptions.getUnitMoveMap(),
        attackOptions.getTransportMoveMap(),
        alliedAttackOptions,
        enemyDefendOptions,
        false);
  }

  private List<JBGTerritory> removeTerritoriesThatCantBeConquered(
      final GamePlayer player,
      final Map<Territory, JBGTerritory> attackMap,
      final Map<Unit, Set<Territory>> unitAttackMap,
      final Map<Unit, Set<Territory>> transportAttackMap,
      final JBGOtherMoveOptions alliedAttackOptions,
      final JBGOtherMoveOptions enemyDefendOptions,
      final boolean isIgnoringRelationships) {

    JBGLogger.info("Removing territories that can't be conquered");
    final GameData data = jbgData.getData();

    // Determine if territory can be successfully attacked with max possible attackers
    final List<Territory> territoriesToRemove = new ArrayList<>();
    for (final Territory t : attackMap.keySet()) {
      final JBGTerritory patd = attackMap.get(t);

      // Check if I can win without amphib units
      final List<Unit> defenders =
          new ArrayList<>(
              isIgnoringRelationships
                  ? t.getUnitCollection()
                  : patd.getMaxEnemyDefenders(player, data));
      patd.setMaxBattleResult(
          calc.estimateAttackBattleResults(
              jbgData, t, patd.getMaxUnits(), defenders, new HashSet<>()));

      // Add in amphib units if I can't win without them
      if (patd.getMaxBattleResult().getWinPercentage() < jbgData.getWinPercentage()
          && !patd.getMaxAmphibUnits().isEmpty()) {
        final Set<Unit> combinedUnits = new HashSet<>(patd.getMaxUnits());
        combinedUnits.addAll(patd.getMaxAmphibUnits());
        patd.setMaxBattleResult(
            calc.estimateAttackBattleResults(
                jbgData, t, new ArrayList<>(combinedUnits), defenders, patd.getMaxBombardUnits()));
        patd.setNeedAmphibUnits(true);
      }

      // Check strafing and using allied attack if enemy capital/factory
      boolean isEnemyCapitalOrFactory = false;
      final TerritoryAttachment ta = TerritoryAttachment.get(t);
      if (!JBGUtils.isNeutralLand(t)
          && ((ta != null && ta.isCapital())
              || JBGMatches.territoryHasInfraFactoryAndIsLand().test(t))) {
        isEnemyCapitalOrFactory = true;
      }
      if (patd.getMaxBattleResult().getWinPercentage() < jbgData.getMinWinPercentage()
          && isEnemyCapitalOrFactory
          && alliedAttackOptions.getMax(t) != null) {

        // Check for allied attackers
        final JBGTerritory alliedAttack = alliedAttackOptions.getMax(t);
        final Set<Unit> alliedUnits = new HashSet<>(alliedAttack.getMaxUnits());
        alliedUnits.addAll(alliedAttack.getMaxAmphibUnits());
        if (!alliedUnits.isEmpty()) {

          // Make sure allies' capital isn't next to territory
          final GamePlayer alliedPlayer = alliedUnits.iterator().next().getOwner();
          final Territory capital =
              TerritoryAttachment.getFirstOwnedCapitalOrFirstUnownedCapital(alliedPlayer, data);
          if (capital != null && !data.getMap().getNeighbors(capital).contains(t)) {

            // Get max enemy defenders
            final Set<Unit> additionalEnemyDefenders = new HashSet<>();
            final List<GamePlayer> players = JBGUtils.getOtherPlayersInTurnOrder(player);
            for (final JBGTerritory enemyDefendOption : enemyDefendOptions.getAll(t)) {
              final Set<Unit> enemyUnits = new HashSet<>(enemyDefendOption.getMaxUnits());
              enemyUnits.addAll(enemyDefendOption.getMaxAmphibUnits());
              if (!enemyUnits.isEmpty()) {
                final GamePlayer enemyPlayer = enemyUnits.iterator().next().getOwner();
                if (JBGUtils.isPlayersTurnFirst(players, enemyPlayer, alliedPlayer)) {
                  additionalEnemyDefenders.addAll(enemyUnits);
                }
              }
            }

            // Check allied result without strafe
            final Set<Unit> enemyDefendersBeforeStrafe = new HashSet<>(defenders);
            enemyDefendersBeforeStrafe.addAll(additionalEnemyDefenders);
            final JBGBattleResult result =
                calc.estimateAttackBattleResults(
                    jbgData,
                    t,
                    new ArrayList<>(alliedUnits),
                    new ArrayList<>(enemyDefendersBeforeStrafe),
                    alliedAttack.getMaxBombardUnits());
            if (result.getWinPercentage() < jbgData.getWinPercentage()) {
              patd.setStrafing(true);

              // Try to strafe to allow allies to conquer territory
              final Set<Unit> combinedUnits = new HashSet<>(patd.getMaxUnits());
              combinedUnits.addAll(patd.getMaxAmphibUnits());
              final JBGBattleResult strafeResult =
                  calc.callBattleCalcWithRetreatAir(
                      jbgData,
                      t,
                      new ArrayList<>(combinedUnits),
                      defenders,
                      patd.getMaxBombardUnits());

              // Check allied result with strafe
              final Set<Unit> enemyDefendersAfterStrafe =
                  new HashSet<>(strafeResult.getAverageDefendersRemaining());
              enemyDefendersAfterStrafe.addAll(additionalEnemyDefenders);
              patd.setMaxBattleResult(
                  calc.estimateAttackBattleResults(
                      jbgData,
                      t,
                      new ArrayList<>(alliedUnits),
                      new ArrayList<>(enemyDefendersAfterStrafe),
                      alliedAttack.getMaxBombardUnits()));

              JBGLogger.debug(
                  "Checking strafing territory: "
                      + t
                      + ", alliedPlayer="
                      + alliedUnits.iterator().next().getOwner().getName()
                      + ", maxWin%="
                      + patd.getMaxBattleResult().getWinPercentage()
                      + ", maxAttackers="
                      + alliedUnits.size()
                      + ", maxDefenders="
                      + enemyDefendersAfterStrafe.size());
            }
          }
        }
      }

      if (patd.getMaxBattleResult().getWinPercentage() < jbgData.getMinWinPercentage()
          || (patd.isStrafing()
              && (patd.getMaxBattleResult().getWinPercentage() < jbgData.getWinPercentage()
                  || !patd.getMaxBattleResult().isHasLandUnitRemaining()))) {
        territoriesToRemove.add(t);
      }
    }

    // Remove territories that can't be successfully attacked
    Collections.sort(territoriesToRemove);
    final List<JBGTerritory> result = new ArrayList<>(attackMap.values());
    for (final Territory t : territoriesToRemove) {
      final JBGTerritory proTerritoryToRemove = attackMap.get(t);
      final Set<Unit> combinedUnits = new HashSet<>(proTerritoryToRemove.getMaxUnits());
      combinedUnits.addAll(proTerritoryToRemove.getMaxAmphibUnits());
      JBGLogger.debug(
          "Removing territory that we can't successfully attack: "
              + t
              + ", maxWin%="
              + proTerritoryToRemove.getMaxBattleResult().getWinPercentage()
              + ", maxAttackers="
              + combinedUnits.size());
      result.remove(proTerritoryToRemove);
      for (final Set<Territory> territories : unitAttackMap.values()) {
        territories.remove(t);
      }
      for (final Set<Territory> territories : transportAttackMap.values()) {
        territories.remove(t);
      }
    }
    return result;
  }

  public List<JBGTerritory> removePotentialTerritoriesThatCantBeConquered() {
    return removeTerritoriesThatCantBeConquered(
        player,
        potentialAttackOptions.getTerritoryMap(),
        potentialAttackOptions.getUnitMoveMap(),
        potentialAttackOptions.getTransportMoveMap(),
        alliedAttackOptions,
        enemyDefendOptions,
        true);
  }

  public JBGMyMoveOptions getAttackOptions() {
    return attackOptions;
  }

  public JBGMyMoveOptions getDefendOptions() {
    return defendOptions;
  }

  public JBGOtherMoveOptions getAlliedAttackOptions() {
    return alliedAttackOptions;
  }

  public JBGOtherMoveOptions getEnemyDefendOptions() {
    return enemyDefendOptions;
  }

  public JBGOtherMoveOptions getEnemyAttackOptions() {
    return enemyAttackOptions;
  }

  public List<Territory> getDefendTerritories() {
    return new ArrayList<>(defendOptions.getTerritoryMap().keySet());
  }

  public List<Territory> getStrafingTerritories() {
    final List<Territory> strafingTerritories = new ArrayList<>();
    for (final Territory t : attackOptions.getTerritoryMap().keySet()) {
      if (attackOptions.getTerritoryMap().get(t).isStrafing()) {
        strafingTerritories.add(t);
      }
    }
    return strafingTerritories;
  }

  public List<Territory> getCantHoldTerritories() {
    final List<Territory> territoriesThatCantBeHeld = new ArrayList<>();
    for (final Territory t : defendOptions.getTerritoryMap().keySet()) {
      if (!defendOptions.getTerritoryMap().get(t).isCanHold()) {
        territoriesThatCantBeHeld.add(t);
      }
    }
    return territoriesThatCantBeHeld;
  }

  public boolean haveUsedAllAttackTransports() {
    final Set<Unit> movedTransports = new HashSet<>();
    for (final JBGTerritory patd : attackOptions.getTerritoryMap().values()) {
      movedTransports.addAll(patd.getAmphibAttackMap().keySet());
      movedTransports.addAll(
          CollectionUtils.getMatches(patd.getUnits(), Matches.unitIsTransport()));
    }
    return movedTransports.size() >= attackOptions.getTransportList().size();
  }

  private void findScrambleOptions(
      final JBGData jbgData, final GamePlayer player, final Map<Territory, JBGTerritory> moveMap) {
    final GameData data = jbgData.getData();

    if (!Properties.getScrambleRulesInEffect(data)) {
      return;
    }

    final var scrambleLogic = new ScrambleLogic(data, player, moveMap.keySet());
    for (final var territoryToScramblersEntry :
        scrambleLogic.getUnitsThatCanScrambleByDestination().entrySet()) {
      final Territory to = territoryToScramblersEntry.getKey();
      for (final Tuple<Collection<Unit>, Collection<Unit>> airbasesAndScramblers :
          territoryToScramblersEntry.getValue().values()) {
        final Collection<Unit> airbases = airbasesAndScramblers.getFirst();
        final Collection<Unit> scramblers = airbasesAndScramblers.getSecond();
        final int maxCanScramble = ScrambleLogic.getMaxScrambleCount(airbases);

        final List<Unit> addTo = moveMap.get(to).getMaxScrambleUnits();
        if (scramblers.size() <= maxCanScramble) {
          addTo.addAll(scramblers);
        } else {
          scramblers.stream()
              .sorted(
                  Comparator.<Unit>comparingDouble(
                          unit ->
                              JBGBattleUtils.estimateStrength(
                                  jbgData, to, List.of(unit), List.of(), false))
                      .reversed())
              .limit(maxCanScramble)
              .forEachOrdered(addTo::add);
        }
      }
    }
  }

  private static void findAttackOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Map<Unit, Set<Territory>> transportMoveMap,
      final Map<Unit, Set<Territory>> bombardMap,
      final List<JBGTransport> transportMapList,
      final List<Territory> enemyTerritories,
      final List<Territory> alliedTerritories,
      final List<Territory> territoriesToCheck,
      final boolean isCheckingEnemyAttacks,
      final boolean isIgnoringRelationships) {
    findAttackOptions(jbgData, player, 
      myUnitTerritories,
      moveMap,
      unitMoveMap,
      transportMoveMap,
      bombardMap,
      transportMapList,
      enemyTerritories,
      alliedTerritories,
      territoriesToCheck,
      isCheckingEnemyAttacks,
      isIgnoringRelationships, 
      isRushingDefault
      );
  }
  private static void findAttackOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Map<Unit, Set<Territory>> transportMoveMap,
      final Map<Unit, Set<Territory>> bombardMap,
      final List<JBGTransport> transportMapList,
      final List<Territory> enemyTerritories,
      final List<Territory> alliedTerritories,
      final List<Territory> territoriesToCheck,
      final boolean isCheckingEnemyAttacks,
      final boolean isIgnoringRelationships,
      final boolean rushingMode) {
    final GameData data = jbgData.getData();

    final Map<Territory, Set<Territory>> landRoutesMap = new HashMap<>();
    final List<Territory> territoriesThatCantBeHeld = new ArrayList<>(enemyTerritories);
    territoriesThatCantBeHeld.addAll(territoriesToCheck);
    findNavalMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        unitMoveMap,
        transportMoveMap,
        JBGMatches.territoryIsEnemyOrHasEnemyUnitsOrCantBeHeld(
            player, data, territoriesThatCantBeHeld),
        enemyTerritories,
        true,
        isCheckingEnemyAttacks,
        rushingMode);
    findLandMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        unitMoveMap,
        landRoutesMap,
        JBGMatches.territoryIsEnemyOrCantBeHeld(player, data, territoriesThatCantBeHeld),
        enemyTerritories,
        alliedTerritories,
        true,
        isCheckingEnemyAttacks,
        isIgnoringRelationships,
        rushingMode);
    findAirMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        unitMoveMap,
        JBGMatches.territoryHasEnemyUnitsOrCantBeHeld(player, data, territoriesThatCantBeHeld),
        enemyTerritories,
        alliedTerritories,
        true,
        isCheckingEnemyAttacks,
        isIgnoringRelationships,
        rushingMode);
    findAmphibMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        transportMapList,
        landRoutesMap,
        JBGMatches.territoryIsEnemyOrCantBeHeld(player, data, territoriesThatCantBeHeld),
        true,
        isCheckingEnemyAttacks,
        isIgnoringRelationships,
        rushingMode);
    findBombardOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        bombardMap,
        transportMapList,
        isCheckingEnemyAttacks,
        rushingMode);
  }

  private void findBombingOptions() {
    for (final Unit unit : attackOptions.getUnitMoveMap().keySet()) {
      if (Matches.unitIsStrategicBomber().test(unit)) {
        attackOptions
            .getBomberMoveMap()
            .put(unit, new HashSet<>(attackOptions.getUnitMoveMap().get(unit)));
      }
    }
  }

  private JBGOtherMoveOptions findAlliedAttackOptions(final GamePlayer player) {
    return findAlliedAttackOptions(player, isRushingDefault);
  }
  private JBGOtherMoveOptions findAlliedAttackOptions(final GamePlayer player, final boolean rushingMode) {
    final GameData data = jbgData.getData();

    // Get enemy players in order of turn
    final List<GamePlayer> alliedPlayers = JBGUtils.getAlliedPlayersInTurnOrder(player);
    final List<Map<Territory, JBGTerritory>> alliedAttackMaps = new ArrayList<>();

    // Loop through each enemy to determine the maximum number of enemy units that can attack each
    // territory
    for (final GamePlayer alliedPlayer : alliedPlayers) {
      final List<Territory> alliedUnitTerritories =
          CollectionUtils.getMatches(
              data.getMap().getTerritories(), Matches.territoryHasUnitsOwnedBy(alliedPlayer));
      final Map<Territory, JBGTerritory> attackMap = new HashMap<>();
      final Map<Unit, Set<Territory>> unitAttackMap = new HashMap<>();
      final Map<Unit, Set<Territory>> transportAttackMap = new HashMap<>();
      final Map<Unit, Set<Territory>> bombardMap = new HashMap<>();
      final List<JBGTransport> transportMapList = new ArrayList<>();
      alliedAttackMaps.add(attackMap);
      findAttackOptions(
          jbgData,
          alliedPlayer,
          alliedUnitTerritories,
          attackMap,
          unitAttackMap,
          transportAttackMap,
          bombardMap,
          transportMapList,
          new ArrayList<>(),
          new ArrayList<>(),
          new ArrayList<>(),
          false,
          false,
          rushingMode);
    }
    return new JBGOtherMoveOptions(jbgData, alliedAttackMaps, player, true);
  }

  private static JBGOtherMoveOptions findEnemyAttackOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> clearedTerritories,
      final List<Territory> territoriesToCheck) {
    return findEnemyAttackOptions(jbgData, player, clearedTerritories, territoriesToCheck, isRushingDefault);
  }
  private static JBGOtherMoveOptions findEnemyAttackOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> clearedTerritories,
      final List<Territory> territoriesToCheck,
      final boolean rushingMode) {
    final GameData data = jbgData.getData();

    // Get enemy players in order of turn
    final List<GamePlayer> enemyPlayers = JBGUtils.getEnemyPlayersInTurnOrder(player);
    final List<Map<Territory, JBGTerritory>> enemyAttackMaps = new ArrayList<>();
    final Set<Territory> alliedTerritories = new HashSet<>();
    final List<Territory> enemyTerritories = new ArrayList<>(clearedTerritories);

    // Loop through each enemy to determine the maximum number of enemy units that can attack each
    // territory
    for (final GamePlayer enemyPlayer : enemyPlayers) {
      final List<Territory> enemyUnitTerritories =
          CollectionUtils.getMatches(
              data.getMap().getTerritories(), Matches.territoryHasUnitsOwnedBy(enemyPlayer));
      enemyUnitTerritories.removeAll(clearedTerritories);
      final Map<Territory, JBGTerritory> attackMap = new HashMap<>();
      final Map<Unit, Set<Territory>> unitAttackMap = new HashMap<>();
      final Map<Unit, Set<Territory>> transportAttackMap = new HashMap<>();
      final Map<Unit, Set<Territory>> bombardMap = new HashMap<>();
      final List<JBGTransport> transportMapList = new ArrayList<>();
      enemyAttackMaps.add(attackMap);
      findAttackOptions(
          jbgData,
          enemyPlayer,
          enemyUnitTerritories,
          attackMap,
          unitAttackMap,
          transportAttackMap,
          bombardMap,
          transportMapList,
          enemyTerritories,
          new ArrayList<>(alliedTerritories),
          territoriesToCheck,
          true,
          true,
          rushingMode);
      alliedTerritories.addAll(
          CollectionUtils.getMatches(attackMap.keySet(), Matches.territoryIsLand()));
      enemyTerritories.removeAll(alliedTerritories);
    }
    return new JBGOtherMoveOptions(jbgData, enemyAttackMaps, player, true);
  }

  private static void findPotentialAttackOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Map<Unit, Set<Territory>> transportMoveMap,
      final Map<Unit, Set<Territory>> bombardMap,
      final List<JBGTransport> transportMapList) {
    findPotentialAttackOptions(
      jbgData,
      player,
      myUnitTerritories,
      moveMap,
      unitMoveMap,
      transportMoveMap,
      bombardMap,
      transportMapList,
      isRushingDefault);
  }
  private static void findPotentialAttackOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Map<Unit, Set<Territory>> transportMoveMap,
      final Map<Unit, Set<Territory>> bombardMap,
      final List<JBGTransport> transportMapList,
      final boolean rushingMode) {
    final GameData data = jbgData.getData();

    final Map<Territory, Set<Territory>> landRoutesMap = new HashMap<>();
    final List<GamePlayer> otherPlayers = JBGUtils.getPotentialEnemyPlayers(player);
    findNavalMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        unitMoveMap,
        transportMoveMap,
        JBGMatches.territoryIsPotentialEnemyOrHasPotentialEnemyUnits(player, data, otherPlayers),
        new ArrayList<>(),
        true,
        false,
        rushingMode);
    findLandMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        unitMoveMap,
        landRoutesMap,
        JBGMatches.territoryIsPotentialEnemy(player, data, otherPlayers),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        false,
        true,
        rushingMode);
    findAirMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        unitMoveMap,
        JBGMatches.territoryHasPotentialEnemyUnits(player, data, otherPlayers),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        false,
        true,
        rushingMode);
    findAmphibMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        transportMapList,
        landRoutesMap,
        JBGMatches.territoryIsPotentialEnemy(player, data, otherPlayers),
        true,
        false,
        true,
        rushingMode);
    findBombardOptions(
        jbgData, player, myUnitTerritories, moveMap, bombardMap, transportMapList, false, rushingMode);
  }

  private static void findDefendOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Map<Unit, Set<Territory>> transportMoveMap,
      final List<JBGTransport> transportMapList,
      final List<Territory> clearedTerritories,
      final boolean isCheckingEnemyAttacks) {
    findDefendOptions(
      jbgData,
      player,
      myUnitTerritories,
      moveMap,
      unitMoveMap,
      transportMoveMap,
      transportMapList,
      clearedTerritories,
      isCheckingEnemyAttacks,
      isRushingDefault
      );
  }
  private static void findDefendOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Map<Unit, Set<Territory>> transportMoveMap,
      final List<JBGTransport> transportMapList,
      final List<Territory> clearedTerritories,
      final boolean isCheckingEnemyAttacks,
      final boolean rushingMode) {
    final GameData data = jbgData.getData();

    final Map<Territory, Set<Territory>> landRoutesMap = new HashMap<>();
    findNavalMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        unitMoveMap,
        transportMoveMap,
        JBGMatches.territoryHasNoEnemyUnitsOrCleared(player, data, clearedTerritories),
        clearedTerritories,
        false,
        isCheckingEnemyAttacks,
        rushingMode);
    findLandMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        unitMoveMap,
        landRoutesMap,
        Matches.isTerritoryAllied(player, data),
        new ArrayList<>(),
        clearedTerritories,
        false,
        isCheckingEnemyAttacks,
        false,
        rushingMode);
    findAirMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        unitMoveMap,
        JBGMatches.territoryCanLandAirUnits(
            player, data, false, new ArrayList<>(), new ArrayList<>()),
        new ArrayList<>(),
        new ArrayList<>(),
        false,
        isCheckingEnemyAttacks,
        false,
        rushingMode);
    findAmphibMoveOptions(
        jbgData,
        player,
        myUnitTerritories,
        moveMap,
        transportMapList,
        landRoutesMap,
        Matches.isTerritoryAllied(player, data),
        false,
        isCheckingEnemyAttacks,
        false, 
        rushingMode);
  }

  private static JBGOtherMoveOptions findEnemyDefendOptions(
      final JBGData jbgData, final GamePlayer player) {
    return findEnemyDefendOptions(jbgData, player, isRushingDefault);
  }
  private static JBGOtherMoveOptions findEnemyDefendOptions(
      final JBGData jbgData, final GamePlayer player, final boolean rushingMode) {
    final GameData data = jbgData.getData();

    // Get enemy players in order of turn
    final List<GamePlayer> enemyPlayers = JBGUtils.getEnemyPlayersInTurnOrder(player);
    final List<Map<Territory, JBGTerritory>> enemyMoveMaps = new ArrayList<>();
    final List<Territory> clearedTerritories =
        CollectionUtils.getMatches(
            data.getMap().getTerritories(), Matches.isTerritoryAllied(player, data));

    // Loop through each enemy to determine the maximum number of enemy units that can defend each
    // territory
    for (final GamePlayer enemyPlayer : enemyPlayers) {
      final List<Territory> enemyUnitTerritories =
          CollectionUtils.getMatches(
              data.getMap().getTerritories(), Matches.territoryHasUnitsOwnedBy(enemyPlayer));
      final Map<Territory, JBGTerritory> moveMap = new HashMap<>();
      final Map<Unit, Set<Territory>> unitMoveMap = new HashMap<>();
      final Map<Unit, Set<Territory>> transportMoveMap = new HashMap<>();
      final List<JBGTransport> transportMapList = new ArrayList<>();
      enemyMoveMaps.add(moveMap);
      findDefendOptions(
          jbgData,
          enemyPlayer,
          enemyUnitTerritories,
          moveMap,
          unitMoveMap,
          transportMoveMap,
          transportMapList,
          clearedTerritories,
          true,
          rushingMode);
    }

    return new JBGOtherMoveOptions(jbgData, enemyMoveMaps, player, false);
  }

  private static void findNavalMoveOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Map<Unit, Set<Territory>> transportMoveMap,
      final Predicate<Territory> moveToTerritoryMatch,
      final List<Territory> clearedTerritories,
      final boolean isCombatMove,
      final boolean isCheckingEnemyAttacks) {
    findNavalMoveOptions(
          jbgData,
          player,
          myUnitTerritories,
          moveMap,
          unitMoveMap,
          transportMoveMap,
          moveToTerritoryMatch,
          clearedTerritories,
          isCombatMove,
          isCheckingEnemyAttacks,
          isRushingDefault
          );
  }
  private static void findNavalMoveOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Map<Unit, Set<Territory>> transportMoveMap,
      final Predicate<Territory> moveToTerritoryMatch,
      final List<Territory> clearedTerritories,
      final boolean isCombatMove,
      final boolean isCheckingEnemyAttacks,
      final boolean rushingMode) {
    final GameData data = jbgData.getData();

    for (final Territory myUnitTerritory : myUnitTerritories) {

      // Find my naval units that have movement left
      final List<Unit> mySeaUnits =
          myUnitTerritory
              .getUnitCollection()
              .getMatches(JBGMatches.unitCanBeMovedAndIsOwnedSea(player, isCombatMove));

      // Check each sea unit individually since they can have different ranges
      for (final Unit mySeaUnit : mySeaUnits) {

        // If my combat move and carrier has dependent allied fighters then skip it
        if (isCombatMove && !isCheckingEnemyAttacks) {
          final Map<Unit, Collection<Unit>> carrierMustMoveWith =
              MoveValidator.carrierMustMoveWith(
                  myUnitTerritory.getUnits(), myUnitTerritory, data, player);
          if (carrierMustMoveWith.containsKey(mySeaUnit)
              && !carrierMustMoveWith.get(mySeaUnit).isEmpty()) {
            continue;
          }
        }

        // Find range
        BigDecimal range = mySeaUnit.getMovementLeft();
        if (isCheckingEnemyAttacks) {
          range = new BigDecimal(UnitAttachment.get(mySeaUnit.getType()).getMovement(player));
          if (Matches.unitCanBeGivenBonusMovementByFacilitiesInItsTerritory(
                  myUnitTerritory, player, data)
              .test(mySeaUnit)) {
            range = range.add(BigDecimal.ONE); // assumes bonus of +1 for now
          }
        }

        // Find list of potential territories to move to
        final Set<Territory> possibleMoveTerritories =
            data.getMap()
                .getNeighborsByMovementCost(
                    myUnitTerritory,
                    mySeaUnit,
                    range,
                    JBGMatches.territoryCanMoveSeaUnits(player, data, isCombatMove));
        possibleMoveTerritories.add(myUnitTerritory);
        final Set<Territory> potentialTerritories =
            new HashSet<>(
                CollectionUtils.getMatches(possibleMoveTerritories, moveToTerritoryMatch));
        if (!isCombatMove) {
          potentialTerritories.add(myUnitTerritory);
        }
        for (final Territory potentialTerritory : potentialTerritories) {

          // Find route over water
          Route myRoute =
              data.getMap()
                  .getRouteForUnit(
                      myUnitTerritory,
                      potentialTerritory,
                      JBGMatches.territoryCanMoveSeaUnitsThroughOrClearedAndNotInList(
                          player, data, isCombatMove, clearedTerritories, List.of()),
                      mySeaUnit,
                      player);
          if (isCheckingEnemyAttacks) {
            myRoute =
                data.getMap()
                    .getRouteForUnit(
                        myUnitTerritory,
                        potentialTerritory,
                        JBGMatches.territoryCanMoveSeaUnits(player, data, isCombatMove),
                        mySeaUnit,
                        player);
          }
          if (myRoute == null) {
            continue;
          }
          final BigDecimal myRouteLength = myRoute.getMovementCost(mySeaUnit);
          if (myRouteLength.compareTo(range) > 0) {
            continue;
          }

          // Populate territories with sea unit
          if (moveMap.containsKey(potentialTerritory)) {
            moveMap.get(potentialTerritory).addMaxUnit(mySeaUnit);
          } else {
            final JBGTerritory moveTerritoryData = new JBGTerritory(potentialTerritory, jbgData);
            moveTerritoryData.addMaxUnit(mySeaUnit);
            moveMap.put(potentialTerritory, moveTerritoryData);
          }

          // Populate appropriate unit move options map
          if (Matches.unitIsTransport().test(mySeaUnit)) {
            if (transportMoveMap.containsKey(mySeaUnit)) {
              transportMoveMap.get(mySeaUnit).add(potentialTerritory);
            } else {
              final Set<Territory> unitMoveTerritories = new HashSet<>();
              unitMoveTerritories.add(potentialTerritory);
              transportMoveMap.put(mySeaUnit, unitMoveTerritories);
            }
          } else {
            if (unitMoveMap.containsKey(mySeaUnit)) {
              unitMoveMap.get(mySeaUnit).add(potentialTerritory);
            } else {
              final Set<Territory> unitMoveTerritories = new HashSet<>();
              unitMoveTerritories.add(potentialTerritory);
              unitMoveMap.put(mySeaUnit, unitMoveTerritories);
            }
          }
        }

        //if force quick process, we only need minial list
        if (rushingMode && possibleMoveTerritories.size() > 0) {
          break;
        } 

      }
    }
  }

  private static void findLandMoveOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Map<Territory, Set<Territory>> landRoutesMap,
      final Predicate<Territory> moveToTerritoryMatch,
      final List<Territory> enemyTerritories,
      final List<Territory> clearedTerritories,
      final boolean isCombatMove,
      final boolean isCheckingEnemyAttacks,
      final boolean isIgnoringRelationships) {
    findLandMoveOptions(
          jbgData,
          player,
          myUnitTerritories,
          moveMap,
          unitMoveMap,
          landRoutesMap,
          moveToTerritoryMatch,
          enemyTerritories,
          clearedTerritories,
          isCombatMove,
          isCheckingEnemyAttacks,
          isIgnoringRelationships,
          isRushingDefault); 
  }
  private static void findLandMoveOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Map<Territory, Set<Territory>> landRoutesMap,
      final Predicate<Territory> moveToTerritoryMatch,
      final List<Territory> enemyTerritories,
      final List<Territory> clearedTerritories,
      final boolean isCombatMove,
      final boolean isCheckingEnemyAttacks,
      final boolean isIgnoringRelationships,
      final boolean rushingMode) {
    final GameData data = jbgData.getData();

    for (final Territory myUnitTerritory : myUnitTerritories) {

      // Find my land units that have movement left
      final List<Unit> myLandUnits =
          myUnitTerritory
              .getUnitCollection()
              .getMatches(JBGMatches.unitCanBeMovedAndIsOwnedLand(player, isCombatMove));

      // Check each land unit individually since they can have different ranges
      for (final Unit myLandUnit : myLandUnits) {
        final Territory startTerritory = jbgData.getUnitTerritory(myLandUnit);
        final BigDecimal range = myLandUnit.getMovementLeft();
        Set<Territory> possibleMoveTerritories =
            data.getMap()
                .getNeighborsByMovementCost(
                    myUnitTerritory,
                    myLandUnit,
                    range,
                    JBGMatches.territoryCanMoveSpecificLandUnit(
                        player, data, isCombatMove, myLandUnit));
        if (isIgnoringRelationships) {
          possibleMoveTerritories =
              data.getMap()
                  .getNeighborsByMovementCost(
                      myUnitTerritory,
                      myLandUnit,
                      range,
                      JBGMatches.territoryCanPotentiallyMoveSpecificLandUnit(
                          player, data, myLandUnit));
        }
        possibleMoveTerritories.add(myUnitTerritory);
        final Set<Territory> potentialTerritories =
            new HashSet<>(
                CollectionUtils.getMatches(possibleMoveTerritories, moveToTerritoryMatch));
        if (!isCombatMove) {
          potentialTerritories.add(myUnitTerritory);
        }
        for (final Territory potentialTerritory : potentialTerritories) {

          // Find route over land checking whether unit can blitz
          Route myRoute =
              data.getMap()
                  .getRouteForUnit(
                      myUnitTerritory,
                      potentialTerritory,
                      JBGMatches.territoryCanMoveLandUnitsThrough(
                          player, data, myLandUnit, startTerritory, isCombatMove, enemyTerritories),
                      myLandUnit,
                      player);
          if (isCheckingEnemyAttacks) {
            myRoute =
                data.getMap()
                    .getRouteForUnit(
                        myUnitTerritory,
                        potentialTerritory,
                        JBGMatches.territoryCanMoveLandUnitsThroughIgnoreEnemyUnits(
                            player,
                            data,
                            myLandUnit,
                            startTerritory,
                            isCombatMove,
                            enemyTerritories,
                            clearedTerritories),
                        myLandUnit,
                        player);
          }
          if (myRoute == null) {
            continue;
          }
          if (myRoute.hasMoreThenOneStep()
              && myRoute.getMiddleSteps().stream().anyMatch(Matches.isTerritoryEnemy(player, data))
              && Matches.unitIsOfTypes(
                      TerritoryEffectHelper.getUnitTypesThatLostBlitz(myRoute.getAllTerritories()))
                  .test(myLandUnit)) {
            continue; // If blitzing then make sure none of the territories cause blitz ability to
            // be lost
          }
          final BigDecimal myRouteLength = myRoute.getMovementCost(myLandUnit);
          if (myRouteLength.compareTo(range) > 0) {
            continue;
          }

          // Add to route map
          if (landRoutesMap.containsKey(potentialTerritory)) {
            landRoutesMap.get(potentialTerritory).add(myUnitTerritory);
          } else {
            final Set<Territory> territories = new HashSet<>();
            territories.add(myUnitTerritory);
            landRoutesMap.put(potentialTerritory, territories);
          }

          // Populate territories with land units
          if (moveMap.containsKey(potentialTerritory)) {
            final List<Unit> unitsToAdd =
                JBGTransportUtils.findBestUnitsToLandTransport(
                    myLandUnit, startTerritory, moveMap.get(potentialTerritory).getMaxUnits());
            moveMap.get(potentialTerritory).addMaxUnits(unitsToAdd);
          } else {
            final JBGTerritory moveTerritoryData = new JBGTerritory(potentialTerritory, jbgData);
            final List<Unit> unitsToAdd =
                JBGTransportUtils.findBestUnitsToLandTransport(myLandUnit, startTerritory);
            moveTerritoryData.addMaxUnits(unitsToAdd);
            moveMap.put(potentialTerritory, moveTerritoryData);
          }

          // Populate unit move options map
          if (unitMoveMap.containsKey(myLandUnit)) {
            unitMoveMap.get(myLandUnit).add(potentialTerritory);
          } else {
            final Set<Territory> unitMoveTerritories = new HashSet<>();
            unitMoveTerritories.add(potentialTerritory);
            unitMoveMap.put(myLandUnit, unitMoveTerritories);
          }
        }

        //if force quick process, we only need minial list
        if (rushingMode && possibleMoveTerritories.size() > 0) {
          break;
        } 

      }
    }
  }

  private static void findAirMoveOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Predicate<Territory> moveToTerritoryMatch,
      final List<Territory> enemyTerritories,
      final List<Territory> alliedTerritories,
      final boolean isCombatMove,
      final boolean isCheckingEnemyAttacks,
      final boolean isIgnoringRelationships) {
    findAirMoveOptions(
      jbgData,
      player,
      myUnitTerritories,
      moveMap,
      unitMoveMap,
      moveToTerritoryMatch,
      enemyTerritories,
      alliedTerritories,
      isCombatMove,
      isCheckingEnemyAttacks,
      isIgnoringRelationships,
      isRushingDefault);
  }
  private static void findAirMoveOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> unitMoveMap,
      final Predicate<Territory> moveToTerritoryMatch,
      final List<Territory> enemyTerritories,
      final List<Territory> alliedTerritories,
      final boolean isCombatMove,
      final boolean isCheckingEnemyAttacks,
      final boolean isIgnoringRelationships,
      final boolean rushingMode) {
    final GameData data = jbgData.getData();

    // TODO: add carriers to landing possibilities for non-enemy attacks
    // Find possible carrier landing territories
    final Set<Territory> possibleCarrierTerritories = new HashSet<>();
    if (isCheckingEnemyAttacks || !isCombatMove) {
      final Map<Unit, Set<Territory>> unitMoveMap2 = new HashMap<>();
      findNavalMoveOptions(
          jbgData,
          player,
          myUnitTerritories,
          new HashMap<>(),
          unitMoveMap2,
          new HashMap<>(),
          Matches.territoryIsWater(),
          enemyTerritories,
          false,
          true,
          rushingMode);
      for (final Unit u : unitMoveMap2.keySet()) {
        if (Matches.unitIsCarrier().test(u)) {
          possibleCarrierTerritories.addAll(unitMoveMap2.get(u));
        }
      }
      for (final Territory t : data.getMap().getTerritories()) {
        if (t.getUnitCollection().anyMatch(Matches.unitIsAlliedCarrier(player, data))) {
          possibleCarrierTerritories.add(t);
        }
      }
    }

    for (final Territory myUnitTerritory : myUnitTerritories) {

      // Find my air units that have movement left
      final List<Unit> myAirUnits =
          myUnitTerritory
              .getUnitCollection()
              .getMatches(JBGMatches.unitCanBeMovedAndIsOwnedAir(player, isCombatMove));

      // Check each air unit individually since they can have different ranges
      for (final Unit myAirUnit : myAirUnits) {

        // Find range
        BigDecimal range = myAirUnit.getMovementLeft();
        if (isCheckingEnemyAttacks) {
          range = new BigDecimal(UnitAttachment.get(myAirUnit.getType()).getMovement(player));
          if (Matches.unitCanBeGivenBonusMovementByFacilitiesInItsTerritory(
                  myUnitTerritory, player, data)
              .test(myAirUnit)) {
            range = range.add(BigDecimal.ONE); // assumes bonus of +1 for now
          }
        }

        // Find potential territories to move to
        Set<Territory> possibleMoveTerritories =
            data.getMap()
                .getNeighborsByMovementCost(
                    myUnitTerritory,
                    myAirUnit,
                    range,
                    JBGMatches.territoryCanMoveAirUnits(player, data, isCombatMove));
        if (isIgnoringRelationships) {
          possibleMoveTerritories =
              data.getMap()
                  .getNeighborsByMovementCost(
                      myUnitTerritory,
                      myAirUnit,
                      range,
                      JBGMatches.territoryCanPotentiallyMoveAirUnits(player, data));
        }
        possibleMoveTerritories.add(myUnitTerritory);
        final Set<Territory> potentialTerritories =
            new HashSet<>(
                CollectionUtils.getMatches(possibleMoveTerritories, moveToTerritoryMatch));
        if (!isCombatMove && Matches.unitCanLandOnCarrier().test(myAirUnit)) {
          potentialTerritories.addAll(
              CollectionUtils.getMatches(
                  possibleMoveTerritories, Matches.territoryIsInList(possibleCarrierTerritories)));
        }

        for (final Territory potentialTerritory : potentialTerritories) {

          // Find route ignoring impassable and territories with AA
          Predicate<Territory> canFlyOverMatch =
              JBGMatches.territoryCanMoveAirUnitsAndNoAa(player, data, isCombatMove);
          if (isCheckingEnemyAttacks) {
            canFlyOverMatch = JBGMatches.territoryCanMoveAirUnits(player, data, isCombatMove);
          }
          final Route myRoute =
              data.getMap()
                  .getRouteForUnit(
                      myUnitTerritory, potentialTerritory, canFlyOverMatch, myAirUnit, player);
          if (myRoute == null) {
            continue;
          }
          final BigDecimal myRouteLength = myRoute.getMovementCost(myAirUnit);
          final BigDecimal remainingMoves = range.subtract(myRouteLength);
          if (remainingMoves.compareTo(BigDecimal.ZERO) < 0) {
            continue;
          }

          // Check if unit can land
          if (isCombatMove
              && (remainingMoves.compareTo(myRouteLength) < 0 || myUnitTerritory.isWater())) {
            final Set<Territory> possibleLandingTerritories =
                data.getMap()
                    .getNeighborsByMovementCost(
                        potentialTerritory, myAirUnit, remainingMoves, canFlyOverMatch);
            final List<Territory> landingTerritories =
                CollectionUtils.getMatches(
                    possibleLandingTerritories,
                    JBGMatches.territoryCanLandAirUnits(
                        player, data, isCombatMove, enemyTerritories, alliedTerritories));
            List<Territory> carrierTerritories = new ArrayList<>();
            if (Matches.unitCanLandOnCarrier().test(myAirUnit)) {
              carrierTerritories =
                  CollectionUtils.getMatches(
                      possibleLandingTerritories,
                      Matches.territoryIsInList(possibleCarrierTerritories));
            }
            if (landingTerritories.isEmpty() && carrierTerritories.isEmpty()) {
              continue;
            }
          }

          // Populate enemy territories with air unit
          if (moveMap.containsKey(potentialTerritory)) {
            moveMap.get(potentialTerritory).addMaxUnit(myAirUnit);
          } else {
            final JBGTerritory moveTerritoryData = new JBGTerritory(potentialTerritory, jbgData);
            moveTerritoryData.addMaxUnit(myAirUnit);
            moveMap.put(potentialTerritory, moveTerritoryData);
          }

          // Populate unit attack options map
          if (unitMoveMap.containsKey(myAirUnit)) {
            unitMoveMap.get(myAirUnit).add(potentialTerritory);
          } else {
            final Set<Territory> unitMoveTerritories = new HashSet<>();
            unitMoveTerritories.add(potentialTerritory);
            unitMoveMap.put(myAirUnit, unitMoveTerritories);
          }
        }

        //if force quick process, we only need minial list
        if (rushingMode && possibleMoveTerritories.size() > 0) {
          break;
        } 

      }
    }
  }

  private static void findAmphibMoveOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final List<JBGTransport> transportMapList,
      final Map<Territory, Set<Territory>> landRoutesMap,
      final Predicate<Territory> moveAmphibToTerritoryMatch,
      final boolean isCombatMove,
      final boolean isCheckingEnemyAttacks,
      final boolean isIgnoringRelationships) {
    findAmphibMoveOptions(
      jbgData,
      player,
      myUnitTerritories,
      moveMap,
      transportMapList,
      landRoutesMap,
      moveAmphibToTerritoryMatch,
      isCombatMove,
      isCheckingEnemyAttacks,
      isIgnoringRelationships,
      isRushingDefault);
  }
  private static void findAmphibMoveOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final List<JBGTransport> transportMapList,
      final Map<Territory, Set<Territory>> landRoutesMap,
      final Predicate<Territory> moveAmphibToTerritoryMatch,
      final boolean isCombatMove,
      final boolean isCheckingEnemyAttacks,
      final boolean isIgnoringRelationships,
      final boolean rushingMode) {
    final GameData data = jbgData.getData();

    for (final Territory myUnitTerritory : myUnitTerritories) {

      // Find my transports and amphibious units that have movement left
      final List<Unit> myTransportUnits =
          myUnitTerritory
              .getUnitCollection()
              .getMatches(JBGMatches.unitCanBeMovedAndIsOwnedTransport(player, isCombatMove));
      Predicate<Territory> unloadAmphibTerritoryMatch =
          JBGMatches.territoryCanMoveLandUnits(player, data, isCombatMove)
              .and(moveAmphibToTerritoryMatch);
      if (isIgnoringRelationships) {
        unloadAmphibTerritoryMatch =
            JBGMatches.territoryCanPotentiallyMoveLandUnits(player, data)
                .and(moveAmphibToTerritoryMatch);
      }

      // Check each transport unit individually since they can have different ranges
      for (final Unit myTransportUnit : myTransportUnits) {

        // Get remaining moves
        int movesLeft = myTransportUnit.getMovementLeft().intValue();
        if (isCheckingEnemyAttacks) {
          movesLeft = UnitAttachment.get(myTransportUnit.getType()).getMovement(player);
          if (Matches.unitCanBeGivenBonusMovementByFacilitiesInItsTerritory(
                  myUnitTerritory, player, data)
              .test(myTransportUnit)) {
            movesLeft++; // assumes bonus of +1 for now
          }
        }

        // Find units to load and territories to unload
        final JBGTransport proTransportData = new JBGTransport(myTransportUnit);
        transportMapList.add(proTransportData);
        final Set<Territory> currentTerritories = new HashSet<>();
        currentTerritories.add(myUnitTerritory);
        while (movesLeft >= 0) {
          final Set<Territory> nextTerritories = new HashSet<>();
          for (final Territory currentTerritory : currentTerritories) {

            // Find neighbors I can move to
            final Set<Territory> possibleNeighborTerritories =
                data.getMap()
                    .getNeighbors(
                        currentTerritory,
                        JBGMatches.territoryCanMoveSeaUnitsThrough(player, data, isCombatMove));
            for (final Territory possibleNeighborTerritory : possibleNeighborTerritories) {
              final Route route = new Route(currentTerritory, possibleNeighborTerritory);
              if (new MoveValidator(data).validateCanal(route, List.of(myTransportUnit), player)
                  == null) {
                nextTerritories.add(possibleNeighborTerritory);
              }
            }

            // Get loaded units or get units that can be loaded into current territory if no enemies
            // present
            final List<Unit> units = new ArrayList<>();
            final Set<Territory> myUnitsToLoadTerritories = new HashSet<>();
            if (TransportTracker.isTransporting(myTransportUnit)) {
              units.addAll(TransportTracker.transporting(myTransportUnit));
            } else if (Matches.territoryHasEnemySeaUnits(player, data)
                .negate()
                .test(currentTerritory)) {
              final Set<Territory> possibleLoadTerritories =
                  data.getMap().getNeighbors(currentTerritory);
              for (final Territory possibleLoadTerritory : possibleLoadTerritories) {
                List<Unit> possibleUnits =
                    possibleLoadTerritory
                        .getUnitCollection()
                        .getMatches(
                            JBGMatches.unitIsOwnedTransportableUnitAndCanBeLoaded(
                                player, myTransportUnit, isCombatMove));
                if (isCheckingEnemyAttacks) {
                  possibleUnits =
                      possibleLoadTerritory
                          .getUnitCollection()
                          .getMatches(JBGMatches.unitIsOwnedCombatTransportableUnit(player));
                }
                for (final Unit possibleUnit : possibleUnits) {
                  if (UnitAttachment.get(possibleUnit.getType()).getTransportCost()
                      <= UnitAttachment.get(myTransportUnit.getType()).getTransportCapacity()) {
                    units.add(possibleUnit);
                    myUnitsToLoadTerritories.add(possibleLoadTerritory);
                  }
                }
              }
            }

            // If there are any units to be transported
            if (!units.isEmpty()) {

              // Find all water territories I can move to
              final Set<Territory> seaMoveTerritories = new HashSet<>();
              seaMoveTerritories.add(currentTerritory);
              if (movesLeft > 0) {
                Set<Territory> neighborTerritories =
                    data.getMap()
                        .getNeighbors(
                            currentTerritory,
                            movesLeft,
                            JBGMatches.territoryCanMoveSeaUnitsThrough(player, data, isCombatMove));
                if (isCheckingEnemyAttacks) {
                  neighborTerritories =
                      data.getMap()
                          .getNeighbors(
                              currentTerritory,
                              movesLeft,
                              JBGMatches.territoryCanMoveSeaUnits(player, data, isCombatMove));
                }
                for (final Territory neighborTerritory : neighborTerritories) {
                  final Route myRoute =
                      data.getMap()
                          .getRouteForUnit(
                              currentTerritory,
                              neighborTerritory,
                              JBGMatches.territoryCanMoveSeaUnitsThrough(
                                  player, data, isCombatMove),
                              myTransportUnit,
                              player);
                  if (myRoute == null) {
                    continue;
                  }
                  seaMoveTerritories.add(neighborTerritory);
                }
              }

              // Find possible unload territories
              final Set<Territory> amphibTerritories = new HashSet<>();
              for (final Territory seaMoveTerritory : seaMoveTerritories) {
                amphibTerritories.addAll(
                    data.getMap().getNeighbors(seaMoveTerritory, unloadAmphibTerritoryMatch));
              }

              // Add to transport map
              proTransportData.addTerritories(amphibTerritories, myUnitsToLoadTerritories);
              proTransportData.addSeaTerritories(seaMoveTerritories, myUnitsToLoadTerritories);
            }
          }
          currentTerritories.clear();
          currentTerritories.addAll(nextTerritories);
          movesLeft--;
        }
      }
    }

    // Remove any territories from transport map that I can move to on land and transports with no
    // amphib options
    for (final JBGTransport proTransportData : transportMapList) {
      final Map<Territory, Set<Territory>> transportMap = proTransportData.getTransportMap();
      final List<Territory> transportTerritoriesToRemove = new ArrayList<>();
      for (final Territory t : transportMap.keySet()) {
        final Set<Territory> transportMoveTerritories = transportMap.get(t);
        final Set<Territory> landMoveTerritories = landRoutesMap.get(t);
        if (landMoveTerritories != null) {
          transportMoveTerritories.removeAll(landMoveTerritories);
          if (transportMoveTerritories.isEmpty()) {
            transportTerritoriesToRemove.add(t);
          }
        }
      }
      for (final Territory t : transportTerritoriesToRemove) {
        transportMap.remove(t);
      }
    }

    // Add transport units to attack map
    for (final JBGTransport proTransportData : transportMapList) {
      final Map<Territory, Set<Territory>> transportMap = proTransportData.getTransportMap();
      final Unit transport = proTransportData.getTransport();
      for (final Territory moveTerritory : transportMap.keySet()) {

        // Get units to transport
        final Set<Territory> territoriesCanLoadFrom = transportMap.get(moveTerritory);
        List<Unit> alreadyAddedToMaxAmphibUnits = new ArrayList<>();
        if (moveMap.containsKey(moveTerritory)) {
          alreadyAddedToMaxAmphibUnits = moveMap.get(moveTerritory).getMaxAmphibUnits();
        }
        List<Unit> amphibUnits =
            JBGTransportUtils.getUnitsToTransportFromTerritories(
                player, transport, territoriesCanLoadFrom, alreadyAddedToMaxAmphibUnits);
        if (isCheckingEnemyAttacks) {
          amphibUnits =
              JBGTransportUtils.getUnitsToTransportFromTerritories(
                  player,
                  transport,
                  territoriesCanLoadFrom,
                  alreadyAddedToMaxAmphibUnits,
                  JBGMatches.unitIsOwnedCombatTransportableUnit(player));
        }

        // Add amphib units to attack map
        if (moveMap.containsKey(moveTerritory)) {
          moveMap.get(moveTerritory).addMaxAmphibUnits(amphibUnits);
        } else {
          final JBGTerritory moveTerritoryData = new JBGTerritory(moveTerritory, jbgData);
          moveTerritoryData.addMaxAmphibUnits(amphibUnits);
          moveMap.put(moveTerritory, moveTerritoryData);
        }
      }
    }
  }

  private static void findBombardOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> bombardMap,
      final List<JBGTransport> transportMapList,
      final boolean isCheckingEnemyAttacks) {
    findBombardOptions(
      jbgData,
      player,
      myUnitTerritories,
      moveMap,
      bombardMap,
      transportMapList,
      isCheckingEnemyAttacks,
      isRushingDefault);
  }
  private static void findBombardOptions(
      final JBGData jbgData,
      final GamePlayer player,
      final List<Territory> myUnitTerritories,
      final Map<Territory, JBGTerritory> moveMap,
      final Map<Unit, Set<Territory>> bombardMap,
      final List<JBGTransport> transportMapList,
      final boolean isCheckingEnemyAttacks,
      final boolean rushingMode) {
    final GameData data = jbgData.getData();

    // Find all transport unload from and to territories
    final Set<Territory> unloadFromTerritories = new HashSet<>();
    final Set<Territory> unloadToTerritories = new HashSet<>();
    for (final JBGTransport amphibData : transportMapList) {
      unloadFromTerritories.addAll(amphibData.getSeaTransportMap().keySet());
      unloadToTerritories.addAll(amphibData.getTransportMap().keySet());
    }

    // Loop through territories with my units
    for (final Territory myUnitTerritory : myUnitTerritories) {

      // Find my bombard units that have movement left
      final List<Unit> mySeaUnits =
          myUnitTerritory
              .getUnitCollection()
              .getMatches(JBGMatches.unitCanBeMovedAndIsOwnedBombard(player));

      // Check each sea unit individually since they can have different ranges
      for (final Unit mySeaUnit : mySeaUnits) {

        // Find range
        BigDecimal range = mySeaUnit.getMovementLeft();
        if (isCheckingEnemyAttacks) {
          range = new BigDecimal(UnitAttachment.get(mySeaUnit.getType()).getMovement(player));
          if (Matches.unitCanBeGivenBonusMovementByFacilitiesInItsTerritory(
                  myUnitTerritory, player, data)
              .test(mySeaUnit)) {
            range = range.add(BigDecimal.ONE); // assumes bonus of +1 for now
          }
        }

        // Find list of potential territories to move to
        final Set<Territory> potentialTerritories =
            data.getMap()
                .getNeighborsByMovementCost(
                    myUnitTerritory,
                    mySeaUnit,
                    range,
                    JBGMatches.territoryCanMoveSeaUnits(player, data, true));
        potentialTerritories.add(myUnitTerritory);
        potentialTerritories.retainAll(unloadFromTerritories);
        for (final Territory bombardFromTerritory : potentialTerritories) {

          // Find route over water with no enemy units blocking
          Route myRoute =
              data.getMap()
                  .getRouteForUnit(
                      myUnitTerritory,
                      bombardFromTerritory,
                      JBGMatches.territoryCanMoveSeaUnitsThrough(player, data, true),
                      mySeaUnit,
                      player);
          if (isCheckingEnemyAttacks) {
            myRoute =
                data.getMap()
                    .getRouteForUnit(
                        myUnitTerritory,
                        bombardFromTerritory,
                        JBGMatches.territoryCanMoveSeaUnits(player, data, true),
                        mySeaUnit,
                        player);
          }
          if (myRoute == null) {
            continue;
          }
          final BigDecimal myRouteLength = myRoute.getMovementCost(mySeaUnit);
          if (myRouteLength.compareTo(range) > 0) {
            continue;
          }

          // Find potential unload to territories
          final Set<Territory> bombardToTerritories =
              new HashSet<>(data.getMap().getNeighbors(bombardFromTerritory));
          bombardToTerritories.retainAll(unloadToTerritories);

          // Populate attack territories with bombard unit
          for (final Territory bombardToTerritory : bombardToTerritories) {
            if (moveMap.containsKey(bombardToTerritory)) { // Should always contain it
              moveMap.get(bombardToTerritory).addMaxBombardUnit(mySeaUnit);
              moveMap.get(bombardToTerritory).addBombardOptionsMap(mySeaUnit, bombardFromTerritory);
            }
          }

          // Populate bombard options map
          if (bombardMap.containsKey(mySeaUnit)) {
            bombardMap.get(mySeaUnit).addAll(bombardToTerritories);
          } else {
            bombardMap.put(mySeaUnit, bombardToTerritories);
          }
        }

        //if force quick process, we only need minial list
        if (rushingMode && bombardMap.size() > 0) {
          break;
        } 

      }
    }
  }
}
