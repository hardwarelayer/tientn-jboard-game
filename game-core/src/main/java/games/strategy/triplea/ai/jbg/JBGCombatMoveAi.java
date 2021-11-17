package games.strategy.triplea.ai.jbg;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.Properties;
import games.strategy.triplea.ai.jbg.data.JBGBattleResult;
import games.strategy.triplea.ai.jbg.data.JBGOtherMoveOptions;
import games.strategy.triplea.ai.jbg.data.JBGPurchaseOption;
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
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TransportTracker;
import games.strategy.triplea.delegate.battle.AirBattle;
import games.strategy.triplea.delegate.remote.IMoveDelegate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.triplea.java.collections.CollectionUtils;

import java.util.concurrent.ThreadLocalRandom;
import games.strategy.engine.data.JBGConstants;
import lombok.Getter;
import lombok.Setter;

/** JBG combat move AI. */
public class JBGCombatMoveAi {

  private static final int MIN_BOMBING_SCORE = 4; // Avoid bombing low production factories with AA

  private final AbstractJBGAi ai;
  private final JBGData jbgData;
  private final JBGOddsCalculator calc;
  private GameData data;
  private GamePlayer player;
  private JBGTerritoryManager territoryManager;
  private boolean isDefensive;
  private boolean isBombing;
  @Setter private int gameTurnIndex = 0;
  //get ATTK opts
  private int lastPopulateAttackOptionsTurn = 0;
  private int delayPeriodForPopulateAttackOptions = 0;
  //prioritize ATTK
  private int lastPopulateEnemyAttackTurn = 0;
  private int delayPeriodForPopulateEnemyAttack = 0;
  //check contested sea
  private int lastCalcTransportAttackTurn = 0;
  private int delayPeriodForCalcTransportAttack = 0;


  JBGCombatMoveAi(final AbstractJBGAi ai) {
    this.ai = ai;
    this.jbgData = ai.getJBGData();
    calc = ai.getCalc();
  }

  Map<Territory, JBGTerritory> doCombatMove(final IMoveDelegate moveDel) {
    JBGLogger.info("Starting combat move phase");
long start = System.currentTimeMillis();
System.out.println("doCombatMove: step0, turn: " + String.valueOf(gameTurnIndex));
    // Current data at the start of combat move
    data = jbgData.getData();
    player = jbgData.getPlayer();
    territoryManager = new JBGTerritoryManager(calc, jbgData);

    // Determine whether capital is threatened and I should be in a defensive stance
    isDefensive =
        !JBGBattleUtils.territoryHasLocalLandSuperiority(
            jbgData, jbgData.getMyCapital(), JBGBattleUtils.MEDIUM_RANGE, player);
    isBombing = false;
    JBGLogger.debug("Currently in defensive stance: " + isDefensive);
System.out.println("doCombatMove: step 2: populate attack options --- " + String.valueOf((System.currentTimeMillis() - start)/1000)); start = System.currentTimeMillis();
    // Find the maximum number of units that can attack each territory and max enemy defenders
    if (delayPeriodForPopulateAttackOptions == 0) {
      delayPeriodForPopulateAttackOptions = randomBetween(
        JBGConstants.MIN_TURNS_FOR_GET_ATTK_OPTS,
        JBGConstants.MAX_TURNS_FOR_GET_ATTK_OPTS
        );
      lastPopulateAttackOptionsTurn = gameTurnIndex;
System.out.println("    JBGCombatMove: initing period for populate attack options: "+ String.valueOf(delayPeriodForPopulateAttackOptions) + " " + player.getName());
    }
    //first turn will be long
    if ((gameTurnIndex - lastPopulateAttackOptionsTurn) > delayPeriodForPopulateAttackOptions ) {
      lastPopulateAttackOptionsTurn = gameTurnIndex;

      territoryManager.populateAttackOptions(false);
System.out.println("        populate attack options: " + String.valueOf((System.currentTimeMillis() - start)/1000));
      territoryManager.populateEnemyDefenseOptions(false);
    }
    else {
System.out.println("    JBGCombatMove: rushing Populate Attack Options ...");
      territoryManager.populateAttackOptions(true);
System.out.println("        populate attack options: " + String.valueOf((System.currentTimeMillis() - start)/1000));
      territoryManager.populateEnemyDefenseOptions(true);
    }

System.out.println("doCombatMove: step3: remove territories that can't be conquered --- " + String.valueOf((System.currentTimeMillis() - start)/1000)); start = System.currentTimeMillis();
    // Remove territories that aren't worth attacking and prioritize the remaining ones
    //
    //init the period this AI waits between each time it get territories 
    //because this step is time consuming, so I split it up
    //I don't do this in constructor, because if so, all AI will be init at the same time(and may be the value is the same, so all slow down will occur on a turn)
    List<JBGTerritory> attackOptions =
        territoryManager.removeTerritoriesThatCantBeConquered();
    List<Territory> clearedTerritories = new ArrayList<>();
    for (final JBGTerritory patd : attackOptions) {
      clearedTerritories.add(patd.getTerritory());
    }
System.out.println("doCombatMove: step4: populate enemy attack options --- " + String.valueOf((System.currentTimeMillis() - start)/1000)); start = System.currentTimeMillis();
    if (delayPeriodForPopulateEnemyAttack == 0) {
      delayPeriodForPopulateEnemyAttack = randomBetween(
        JBGConstants.MIN_TURNS_FOR_POPULATE_ENEMY_ATTK,
        JBGConstants.MAX_TURNS_FOR_POPULATE_ENEMY_ATTK
        );
System.out.println("    JBGCombatMove: initing period for populate enemy attacks: "+ String.valueOf(delayPeriodForPopulateEnemyAttack) + " " + player.getName());
      lastPopulateEnemyAttackTurn = gameTurnIndex;
    }
    //first turn will be long
    if ((gameTurnIndex - lastPopulateEnemyAttackTurn) > delayPeriodForPopulateEnemyAttack ) {
      lastPopulateEnemyAttackTurn = gameTurnIndex;
System.out.println("        step 4/1: " + String.valueOf((System.currentTimeMillis() - start)/1000));
      territoryManager.populateEnemyAttackOptions(clearedTerritories, clearedTerritories, false);
System.out.println("        step 4/2: " + String.valueOf((System.currentTimeMillis() - start)/1000));
    }
    else {
System.out.println("    JBGCombatMove: rushing populate Enemy attack ...");
      territoryManager.populateEnemyAttackOptions(clearedTerritories, clearedTerritories, true);
System.out.println("        step 4/2: " + String.valueOf((System.currentTimeMillis() - start)/1000));
    }
    determineTerritoriesThatCanBeHeld(attackOptions, clearedTerritories);
System.out.println("doCombatMove: step5: prioritize attacks ---- " + String.valueOf((System.currentTimeMillis() - start)/1000)); start = System.currentTimeMillis();
    prioritizeAttackOptions(player, attackOptions);
    removeTerritoriesThatArentWorthAttacking(attackOptions);
    // Determine which territories to attack
    determineTerritoriesToAttack(attackOptions);
System.out.println("doCombatMove: step6: transport/amphib attk ---- " + String.valueOf((System.currentTimeMillis() - start)/1000)); start = System.currentTimeMillis();
    // Determine which territories can be held and remove any that aren't worth attacking
    clearedTerritories = new ArrayList<>();
    final Set<Territory> possibleTransportTerritories = new HashSet<>();

    if (delayPeriodForCalcTransportAttack == 0) {
      delayPeriodForCalcTransportAttack = randomBetween(
        JBGConstants.MIN_TURNS_FOR_CALC_TRANSPORT_ATTACK,
        JBGConstants.MAX_TURNS_FOR_CALC_TRANSPORT_ATTACK
        );
System.out.println("    JBGCombatMove: initing period for calculate transport attack: "+ String.valueOf(delayPeriodForCalcTransportAttack) + " " + player.getName());
      lastCalcTransportAttackTurn = gameTurnIndex;
    }
    //first turn will be long
    if ((gameTurnIndex - lastCalcTransportAttackTurn) > delayPeriodForCalcTransportAttack ) {
      lastCalcTransportAttackTurn = gameTurnIndex;

      for (final JBGTerritory patd : attackOptions) {
        clearedTerritories.add(patd.getTerritory());
        if (!patd.getAmphibAttackMap().isEmpty()) {
          possibleTransportTerritories.addAll(
              data.getMap().getNeighbors(patd.getTerritory(), Matches.territoryIsWater()));
        }
      }
      possibleTransportTerritories.addAll(clearedTerritories);
      territoryManager.populateEnemyAttackOptions(
          clearedTerritories, new ArrayList<>(possibleTransportTerritories));
      determineTerritoriesThatCanBeHeld(attackOptions, clearedTerritories);
      removeTerritoriesThatArentWorthAttacking(attackOptions);
    }
    else {
System.out.println("    JBGCombatMove: skipping calculate transport attack ...");
    }

System.out.println("doCombatMove: step7 - calc unit to attack " + String.valueOf((System.currentTimeMillis() - start)/1000)); start = System.currentTimeMillis();
    // Determine how many units to attack each territory with
    final List<Unit> alreadyMovedUnits =
        moveOneDefenderToLandTerritoriesBorderingEnemy(attackOptions);
    determineUnitsToAttackWith(attackOptions, alreadyMovedUnits);

    // Get all transport final territories
    JBGMoveUtils.calculateAmphibRoutes(
        jbgData, player, territoryManager.getAttackOptions().getTerritoryMap(), true);

    // Determine max enemy counter attack units and remove territories where transports are exposed
    removeTerritoriesWhereTransportsAreExposed();
System.out.println("doCombatMove: step8: remove attacks until capital safe  --- " + String.valueOf((System.currentTimeMillis() - start)/1000)); start = System.currentTimeMillis();
    // Determine if capital can be held if I still own it
    if (jbgData.getMyCapital() != null && jbgData.getMyCapital().getOwner().equals(player)) {
      if (false)
        removeAttacksUntilCapitalCanBeHeld(
            attackOptions, jbgData.getPurchaseOptions().getLandOptions());
    }
System.out.println("doCombatMove: step9: check contested  sea " + String.valueOf((System.currentTimeMillis() - start)/1000)); start = System.currentTimeMillis();
    // Check if any subs in contested territory that's not being attacked
    checkContestedSeaTerritories();
System.out.println("doCombatMove: step10 " + String.valueOf((System.currentTimeMillis() - start)/1000)); start = System.currentTimeMillis();
    // Calculate attack routes and perform moves
    doMove(territoryManager.getAttackOptions().getTerritoryMap(), moveDel, data, player);
System.out.println("doCombatMove: step11 " + String.valueOf((System.currentTimeMillis() - start)/1000)); start = System.currentTimeMillis();
    // Set strafing territories to avoid retreats
    ai.setStoredStrafingTerritories(territoryManager.getStrafingTerritories());
    JBGLogger.info("Strafing territories: " + territoryManager.getStrafingTerritories());
System.out.println("doCombatMove: step12 " + String.valueOf((System.currentTimeMillis() - start)/1000)); start = System.currentTimeMillis();
    // Log results
    JBGLogger.info("Logging results");
    logAttackMoves(attackOptions);

    final Map<Territory, JBGTerritory> result =
        territoryManager.getAttackOptions().getTerritoryMap();
    territoryManager = null;
    return result;
  }

  void doMove(
      final Map<Territory, JBGTerritory> attackMap,
      final IMoveDelegate moveDel,
      final GameData data,
      final GamePlayer player) {
    this.data = data;
    this.player = player;

    JBGMoveUtils.doMove(
        jbgData, JBGMoveUtils.calculateMoveRoutes(jbgData, player, attackMap, true), moveDel);
    JBGMoveUtils.doMove(
        jbgData, JBGMoveUtils.calculateAmphibRoutes(jbgData, player, attackMap, true), moveDel);
    JBGMoveUtils.doMove(
        jbgData, JBGMoveUtils.calculateBombardMoveRoutes(jbgData, player, attackMap), moveDel);
    isBombing = true;
    JBGMoveUtils.doMove(
        jbgData, JBGMoveUtils.calculateBombingRoutes(jbgData, player, attackMap), moveDel);
    isBombing = false;
  }

  boolean isBombing() {
    return isBombing;
  }

  private int randomBetween(int min, int max) {
    return ThreadLocalRandom.current().nextInt(min, max + 1);
  }

  private void prioritizeAttackOptions(
      final GamePlayer player, final List<JBGTerritory> attackOptions) {

    JBGLogger.info("Prioritizing territories to try to attack");

    // Calculate value of attacking territory
    for (final Iterator<JBGTerritory> it = attackOptions.iterator(); it.hasNext(); ) {
      final JBGTerritory patd = it.next();
      final Territory t = patd.getTerritory();

      // Determine territory attack properties
      final int isLand = !t.isWater() ? 1 : 0;
      final int isNeutral = JBGUtils.isNeutralLand(t) ? 1 : 0;
      final int isCanHold = patd.isCanHold() ? 1 : 0;
      final int isAmphib = patd.isNeedAmphibUnits() ? 1 : 0;
      final List<Unit> defendingUnits =
          CollectionUtils.getMatches(
              patd.getMaxEnemyDefenders(player, data),
              JBGMatches.unitIsEnemyAndNotInfa(player, data));
      final int isEmptyLand =
          (!t.isWater() && defendingUnits.isEmpty() && !patd.isNeedAmphibUnits()) ? 1 : 0;
      final boolean isAdjacentToMyCapital =
          !data.getMap().getNeighbors(t, Matches.territoryIs(jbgData.getMyCapital())).isEmpty();
      final int isNotNeutralAdjacentToMyCapital =
          (isAdjacentToMyCapital && JBGMatches.territoryIsEnemyNotNeutralLand(player, data).test(t))
              ? 1
              : 0;
      final int isFactory = JBGMatches.territoryHasInfraFactoryAndIsLand().test(t) ? 1 : 0;
      final int isFfa = JBGUtils.isFfa(data, player) ? 1 : 0;

      // Determine production value and if it is an enemy capital
      int production = 0;
      int isEnemyCapital = 0;
      final TerritoryAttachment ta = TerritoryAttachment.get(t);
      if (ta != null) {
        production = ta.getProduction();
        if (ta.isCapital()) {
          isEnemyCapital = 1;
        }
      }

      // Calculate attack value for prioritization
      double tuvSwing = patd.getMaxBattleResult().getTuvSwing();
      if (isFfa == 1 && tuvSwing > 0) {
        tuvSwing *= 0.5;
      }
      final double territoryValue =
          (1 + isLand + isCanHold * (1 + 2.0 * isFfa * isLand))
              * (1 + isEmptyLand)
              * (1 + isFactory)
              * (1 - 0.5 * isAmphib)
              * production;
      double attackValue =
          (tuvSwing + territoryValue)
              * (1 + 4.0 * isEnemyCapital)
              * (1 + 2.0 * isNotNeutralAdjacentToMyCapital)
              * (1 - 0.9 * isNeutral);

      // Check if a negative value neutral territory should be attacked
      if (attackValue <= 0 && !patd.isNeedAmphibUnits() && JBGUtils.isNeutralLand(t)) {

        // Determine enemy neighbor territory production value for neutral land territories
        double nearbyEnemyValue = 0;
        final List<Territory> cantReachEnemyTerritories = new ArrayList<>();
        final Set<Territory> nearbyTerritories =
            data.getMap().getNeighbors(t, JBGMatches.territoryCanMoveLandUnits(player, data, true));
        final List<Territory> nearbyEnemyTerritories =
            CollectionUtils.getMatches(nearbyTerritories, Matches.isTerritoryEnemy(player, data));
        final List<Territory> nearbyTerritoriesWithOwnedUnits =
            CollectionUtils.getMatches(nearbyTerritories, Matches.territoryHasUnitsOwnedBy(player));
        for (final Territory nearbyEnemyTerritory : nearbyEnemyTerritories) {
          boolean allAlliedNeighborsHaveRoute = true;
          for (final Territory nearbyAlliedTerritory : nearbyTerritoriesWithOwnedUnits) {
            final int distance =
                data.getMap()
                    .getDistanceIgnoreEndForCondition(
                        nearbyAlliedTerritory,
                        nearbyEnemyTerritory,
                        JBGMatches.territoryIsEnemyNotNeutralOrAllied(player, data));
            if (distance < 0 || distance > 2) {
              allAlliedNeighborsHaveRoute = false;
              break;
            }
          }
          if (!allAlliedNeighborsHaveRoute) {
            final double value =
                JBGTerritoryValueUtils.findTerritoryAttackValue(
                    jbgData, player, nearbyEnemyTerritory);
            if (value > 0) {
              nearbyEnemyValue += value;
            }
            cantReachEnemyTerritories.add(nearbyEnemyTerritory);
          }
        }
        JBGLogger.debug(
            t.getName()
                + " calculated nearby enemy value="
                + nearbyEnemyValue
                + " from "
                + cantReachEnemyTerritories);
        if (nearbyEnemyValue > 0) {
          JBGLogger.trace(t.getName() + " updating negative neutral attack value=" + attackValue);
          attackValue = nearbyEnemyValue * .001 / (1 - attackValue);
        } else {

          // Check if overwhelming attack strength (more than 5 times)
          final double strengthDifference =
              JBGBattleUtils.estimateStrengthDifference(
                  jbgData, t, patd.getMaxUnits(), patd.getMaxEnemyDefenders(player, data));
          JBGLogger.debug(t.getName() + " calculated strengthDifference=" + strengthDifference);
          if (strengthDifference > 500) {
            JBGLogger.trace(t.getName() + " updating negative neutral attack value=" + attackValue);
            attackValue = strengthDifference * .00001 / (1 - attackValue);
          }
        }
      }

      // Remove negative value territories
      patd.setValue(attackValue);
      if (attackValue <= 0
          || (isDefensive
              && attackValue <= 8
              && data.getMap().getDistance(jbgData.getMyCapital(), t) <= 3)) {
        JBGLogger.debug(
            "Removing territory that has a negative attack value: "
                + t.getName()
                + ", AttackValue="
                + patd.getValue());
        it.remove();
      }
    }

    // Sort attack territories by value
    attackOptions.sort(Comparator.comparingDouble(JBGTerritory::getValue).reversed());

    // Log prioritized territories
    for (final JBGTerritory patd : attackOptions) {
      JBGLogger.debug(
          "AttackValue="
              + patd.getValue()
              + ", TUVSwing="
              + patd.getMaxBattleResult().getTuvSwing()
              + ", isAmphib="
              + patd.isNeedAmphibUnits()
              + ", "
              + patd.getTerritory().getName());
    }
  }

  private void determineTerritoriesToAttack(final List<JBGTerritory> prioritizedTerritories) {

    JBGLogger.info("Determine which territories to attack");

    // Assign units to territories by prioritization
    int numToAttack = Math.min(1, prioritizedTerritories.size());
    boolean haveRemovedAllAmphibTerritories = false;
    while (true) {
      final List<JBGTerritory> territoriesToTryToAttack =
          prioritizedTerritories.subList(0, numToAttack);
      JBGLogger.debug("Current number of territories: " + numToAttack);
      tryToAttackTerritories(territoriesToTryToAttack, new ArrayList<>());

      // Determine if all attacks are successful
      boolean areSuccessful = true;
      for (final JBGTerritory patd : territoriesToTryToAttack) {
        final Territory t = patd.getTerritory();
        if (patd.getBattleResult() == null) {
          patd.estimateBattleResult(calc, player);
        }
        JBGLogger.trace(patd.getResultString() + " with attackers: " + patd.getUnits());
        final double estimate =
            JBGBattleUtils.estimateStrengthDifference(
                jbgData, t, patd.getUnits(), patd.getMaxEnemyDefenders(player, data));
        final JBGBattleResult result = patd.getBattleResult();
        if (!patd.isStrafing()
            && estimate < patd.getStrengthEstimate()
            && (result.getWinPercentage() < jbgData.getMinWinPercentage()
                || !result.isHasLandUnitRemaining())) {
          areSuccessful = false;
        }
      }

      // Determine whether to try more territories, remove a territory, or end
      if (areSuccessful) {
        for (final JBGTerritory patd : territoriesToTryToAttack) {
          patd.setCanAttack(true);
          final double estimate =
              JBGBattleUtils.estimateStrengthDifference(
                  jbgData,
                  patd.getTerritory(),
                  patd.getUnits(),
                  patd.getMaxEnemyDefenders(player, data));
          if (estimate < patd.getStrengthEstimate()) {
            patd.setStrengthEstimate(estimate);
          }
        }

        // If already used all transports then remove any remaining amphib territories
        if (!haveRemovedAllAmphibTerritories && territoryManager.haveUsedAllAttackTransports()) {
          final List<JBGTerritory> amphibTerritoriesToRemove = new ArrayList<>();
          for (int i = numToAttack; i < prioritizedTerritories.size(); i++) {
            if (prioritizedTerritories.get(i).isNeedAmphibUnits()) {
              amphibTerritoriesToRemove.add(prioritizedTerritories.get(i));
              JBGLogger.debug(
                  "Removing amphib territory since already used all transports: "
                      + prioritizedTerritories.get(i).getTerritory().getName());
            }
          }
          prioritizedTerritories.removeAll(amphibTerritoriesToRemove);
          haveRemovedAllAmphibTerritories = true;
        }

        // Can attack all territories in list so end
        numToAttack++;
        if (numToAttack > prioritizedTerritories.size()) {
          break;
        }
      } else {
        JBGLogger.debug(
            "Removing territory: "
                + prioritizedTerritories.get(numToAttack - 1).getTerritory().getName());
        prioritizedTerritories.remove(numToAttack - 1);
        if (numToAttack > prioritizedTerritories.size()) {
          numToAttack--;
        }
      }
    }
    JBGLogger.debug("Final number of territories: " + (numToAttack - 1));
  }

  private void determineTerritoriesThatCanBeHeld(
      final List<JBGTerritory> prioritizedTerritories, final List<Territory> clearedTerritories) {

    JBGLogger.info("Check if we should try to hold attack territories");

    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();
    final Map<Territory, JBGTerritory> attackMap =
        territoryManager.getAttackOptions().getTerritoryMap();

    // Determine which territories to try and hold
    final Set<Territory> territoriesToCheck = new HashSet<>();
    for (final JBGTerritory patd : prioritizedTerritories) {
      final Territory t = patd.getTerritory();
      territoriesToCheck.add(t);
      final List<Unit> nonAirAttackers =
          CollectionUtils.getMatches(patd.getMaxUnits(), Matches.unitIsNotAir());
      for (final Unit u : nonAirAttackers) {
        territoriesToCheck.add(jbgData.getUnitTerritory(u));
      }
    }
    final Map<Territory, Double> territoryValueMap =
        JBGTerritoryValueUtils.findTerritoryValues(
            jbgData, player, new ArrayList<>(), clearedTerritories, territoriesToCheck);
    for (final JBGTerritory patd : prioritizedTerritories) {
      final Territory t = patd.getTerritory();

      // If strafing then can't hold
      if (patd.isStrafing()) {
        patd.setCanHold(false);
        JBGLogger.debug(t + ", strafing so CanHold=false");
        continue;
      }

      // Set max enemy attackers
      if (enemyAttackOptions.getMax(t) != null) {
        final Set<Unit> enemyAttackingUnits =
            new HashSet<>(enemyAttackOptions.getMax(t).getMaxUnits());
        enemyAttackingUnits.addAll(enemyAttackOptions.getMax(t).getMaxAmphibUnits());
        patd.setMaxEnemyUnits(new ArrayList<>(enemyAttackingUnits));
        patd.setMaxEnemyBombardUnits(enemyAttackOptions.getMax(t).getMaxBombardUnits());
      }

      // Add strategic value for factories
      int isFactory = 0;
      if (JBGMatches.territoryHasInfraFactoryAndIsLand().test(t)) {
        isFactory = 1;
      }

      // Determine whether its worth trying to hold territory
      double totalValue = 0.0;
      final List<Unit> nonAirAttackers =
          CollectionUtils.getMatches(patd.getMaxUnits(), Matches.unitIsNotAir());
      for (final Unit u : nonAirAttackers) {
        totalValue += territoryValueMap.get(jbgData.getUnitTerritory(u));
      }
      final double averageValue = totalValue / nonAirAttackers.size() * 0.75;
      final double territoryValue = territoryValueMap.get(t) * (1 + 4.0 * isFactory);
      if (!t.isWater() && territoryValue < averageValue) {
        attackMap.get(t).setCanHold(false);
        JBGLogger.debug(
            t
                + ", CanHold=false, value="
                + territoryValueMap.get(t)
                + ", averageAttackFromValue="
                + averageValue);
        continue;
      }
      if (enemyAttackOptions.getMax(t) != null) {

        // Find max remaining defenders
        final Set<Unit> attackingUnits = new HashSet<>(patd.getMaxUnits());
        attackingUnits.addAll(patd.getMaxAmphibUnits());
        final JBGBattleResult result =
            calc.estimateAttackBattleResults(
                jbgData,
                t,
                new ArrayList<>(attackingUnits),
                patd.getMaxEnemyDefenders(player, data),
                patd.getMaxBombardUnits());
        final List<Unit> remainingUnitsToDefendWith =
            CollectionUtils.getMatches(
                result.getAverageAttackersRemaining(), Matches.unitIsAir().negate());
        JBGLogger.debug(
            t
                + ", value="
                + territoryValueMap.get(t)
                + ", averageAttackFromValue="
                + averageValue
                + ", MyAttackers="
                + attackingUnits.size()
                + ", RemainingUnits="
                + remainingUnitsToDefendWith.size());

        // Determine counter attack results to see if I can hold it
        final JBGBattleResult result2 =
            calc.calculateBattleResults(
                jbgData,
                t,
                patd.getMaxEnemyUnits(),
                remainingUnitsToDefendWith,
                enemyAttackOptions.getMax(t).getMaxBombardUnits());
        final boolean canHold =
            (!result2.isHasLandUnitRemaining() && !t.isWater())
                || (result2.getTuvSwing() < 0)
                || (result2.getWinPercentage() < jbgData.getMinWinPercentage());
        patd.setCanHold(canHold);
        JBGLogger.debug(
            t
                + ", CanHold="
                + canHold
                + ", MyDefenders="
                + remainingUnitsToDefendWith.size()
                + ", EnemyAttackers="
                + patd.getMaxEnemyUnits().size()
                + ", win%="
                + result2.getWinPercentage()
                + ", EnemyTUVSwing="
                + result2.getTuvSwing()
                + ", hasLandUnitRemaining="
                + result2.isHasLandUnitRemaining());
      } else {
        attackMap.get(t).setCanHold(true);
        JBGLogger.debug(
            t
                + ", CanHold=true since no enemy counter attackers, value="
                + territoryValueMap.get(t)
                + ", averageAttackFromValue="
                + averageValue);
      }
    }
  }

  private void removeTerritoriesThatArentWorthAttacking(
      final List<JBGTerritory> prioritizedTerritories) {
    JBGLogger.info("Remove territories that aren't worth attacking");

    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();

    // Loop through all prioritized territories
    for (final Iterator<JBGTerritory> it = prioritizedTerritories.iterator(); it.hasNext(); ) {
      final JBGTerritory patd = it.next();
      final Territory t = patd.getTerritory();
      JBGLogger.debug(
          "Checking territory="
              + patd.getTerritory().getName()
              + " with isAmphib="
              + patd.isNeedAmphibUnits());

      // Remove empty convoy zones that can't be held
      if (!patd.isCanHold()
          && enemyAttackOptions.getMax(t) != null
          && t.isWater()
          && !t.getUnitCollection().anyMatch(Matches.enemyUnit(player, data))) {
        JBGLogger.debug(
            "Removing convoy zone that can't be held: "
                + t.getName()
                + ", enemyAttackers="
                + enemyAttackOptions.getMax(t).getMaxUnits());
        it.remove();
        continue;
      }

      // Remove neutral and low value amphib land territories that can't be held
      final boolean isNeutral = JBGUtils.isNeutralLand(t);
      final double strengthDifference =
          JBGBattleUtils.estimateStrengthDifference(
              jbgData, t, patd.getMaxUnits(), patd.getMaxEnemyDefenders(player, data));
      if (!patd.isCanHold() && enemyAttackOptions.getMax(t) != null && !t.isWater()) {
        if (isNeutral && strengthDifference <= 500) {

          // Remove neutral territories that can't be held and don't have overwhelming attack
          // strength
          JBGLogger.debug(
              "Removing neutral territory that can't be held: "
                  + t.getName()
                  + ", enemyAttackers="
                  + enemyAttackOptions.getMax(t).getMaxUnits()
                  + ", enemyAmphibAttackers="
                  + enemyAttackOptions.getMax(t).getMaxAmphibUnits()
                  + ", strengthDifference="
                  + strengthDifference);
          it.remove();
          continue;
        } else if (patd.isNeedAmphibUnits() && patd.getValue() < 2) {

          // Remove amphib territories that aren't worth attacking
          JBGLogger.debug(
              "Removing low value amphib territory that can't be held: "
                  + t.getName()
                  + ", enemyAttackers="
                  + enemyAttackOptions.getMax(t).getMaxUnits()
                  + ", enemyAmphibAttackers="
                  + enemyAttackOptions.getMax(t).getMaxAmphibUnits());
          it.remove();
          continue;
        }
      }
      // Remove neutral territories where attackers are adjacent to enemy territories that aren't
      // being attacked
      if (isNeutral && !t.isWater() && strengthDifference <= 500) {

        // Get list of territories I'm attacking
        final List<Territory> prioritizedTerritoryList = new ArrayList<>();
        for (final JBGTerritory prioritizedTerritory : prioritizedTerritories) {
          prioritizedTerritoryList.add(prioritizedTerritory.getTerritory());
        }

        // Find all territories units are attacking from that are adjacent to territory
        final Set<Territory> attackFromTerritories = new HashSet<>();
        for (final Unit u : patd.getMaxUnits()) {
          attackFromTerritories.add(jbgData.getUnitTerritory(u));
        }
        attackFromTerritories.retainAll(data.getMap().getNeighbors(t));

        // Determine if any of the attacking from territories has enemy neighbors that aren't being
        // attacked
        boolean attackersHaveEnemyNeighbors = false;
        Territory attackFromTerritoryWithEnemyNeighbors = null;
        for (final Territory attackFromTerritory : attackFromTerritories) {
          final Set<Territory> enemyNeighbors =
              data.getMap()
                  .getNeighbors(
                      attackFromTerritory, JBGMatches.territoryIsEnemyNotNeutralLand(player, data));
          if (!prioritizedTerritoryList.containsAll(enemyNeighbors)) {
            attackersHaveEnemyNeighbors = true;
            attackFromTerritoryWithEnemyNeighbors = attackFromTerritory;
            break;
          }
        }
        if (attackersHaveEnemyNeighbors) {
          JBGLogger.debug(
              "Removing neutral territory that has attackers that are adjacent to enemies: "
                  + t.getName()
                  + ", attackFromTerritory="
                  + attackFromTerritoryWithEnemyNeighbors);
          it.remove();
        }
      }
    }
  }

  private List<Unit> moveOneDefenderToLandTerritoriesBorderingEnemy(
      final List<JBGTerritory> prioritizedTerritories) {

    JBGLogger.info("Determine which territories to defend with one land unit");

    final Map<Unit, Set<Territory>> unitMoveMap =
        territoryManager.getAttackOptions().getUnitMoveMap();

    // Get list of territories to attack
    final List<Territory> territoriesToAttack = new ArrayList<>();
    for (final JBGTerritory patd : prioritizedTerritories) {
      territoriesToAttack.add(patd.getTerritory());
    }

    // Find land territories with no can't move units and adjacent to enemy land units
    final List<Unit> alreadyMovedUnits = new ArrayList<>();
    for (final Territory t : jbgData.getMyUnitTerritories()) {
      final boolean hasAlliedLandUnits =
          t.getUnitCollection()
              .anyMatch(JBGMatches.unitCantBeMovedAndIsAlliedDefenderAndNotInfra(player, data, t));
      final Set<Territory> enemyNeighbors =
          data.getMap()
              .getNeighbors(
                  t,
                  Matches.territoryIsEnemyNonNeutralAndHasEnemyUnitMatching(
                      data,
                      player,
                      Matches.unitIsLand()
                          .and(Matches.unitIsNotInfrastructure())
                          .and(Matches.unitCanMove())));
      enemyNeighbors.removeAll(territoriesToAttack);
      if (!t.isWater() && !hasAlliedLandUnits && !enemyNeighbors.isEmpty()) {
        int minCost = Integer.MAX_VALUE;
        Unit minUnit = null;
        for (final Unit u :
            t.getUnitCollection()
                .getMatches(Matches.unitIsOwnedBy(player).and(Matches.unitIsNotInfrastructure()))) {
          if (jbgData.getUnitValue(u.getType()) < minCost) {
            minCost = jbgData.getUnitValue(u.getType());
            minUnit = u;
          }
        }
        if (minUnit != null) {
          unitMoveMap.remove(minUnit);
          alreadyMovedUnits.add(minUnit);
          JBGLogger.debug(t + ", added one land unit: " + minUnit);
        }
      }
    }
    return alreadyMovedUnits;
  }

  private void removeTerritoriesWhereTransportsAreExposed() {

    JBGLogger.info("Remove territories where transports are exposed");

    final Map<Territory, JBGTerritory> attackMap =
        territoryManager.getAttackOptions().getTerritoryMap();
    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();

    // Find maximum defenders for each transport territory
    final List<Territory> clearedTerritories =
        attackMap.entrySet().stream()
            .filter(e -> !e.getValue().getUnits().isEmpty())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    territoryManager.populateDefenseOptions(clearedTerritories);
    final Map<Territory, JBGTerritory> defendMap =
        territoryManager.getDefendOptions().getTerritoryMap();

    // Remove units that have already attacked
    final Set<Unit> alreadyAttackedWithUnits = new HashSet<>();
    for (final JBGTerritory t : attackMap.values()) {
      alreadyAttackedWithUnits.addAll(t.getUnits());
      alreadyAttackedWithUnits.addAll(t.getAmphibAttackMap().keySet());
    }
    for (final JBGTerritory t : defendMap.values()) {
      t.getMaxUnits().removeAll(alreadyAttackedWithUnits);
    }

    // Loop through all prioritized territories
    for (final Territory t : attackMap.keySet()) {
      final JBGTerritory patd = attackMap.get(t);
      JBGLogger.debug(
          "Checking territory="
              + patd.getTerritory().getName()
              + " with tranports size="
              + patd.getTransportTerritoryMap().size());
      if (!patd.getTerritory().isWater() && !patd.getTransportTerritoryMap().isEmpty()) {

        // Find all transports for each unload territory
        final Map<Territory, List<Unit>> territoryTransportAndBombardMap = new HashMap<>();
        for (final Unit u : patd.getTransportTerritoryMap().keySet()) {
          final Territory unloadTerritory = patd.getTransportTerritoryMap().get(u);
          if (territoryTransportAndBombardMap.containsKey(unloadTerritory)) {
            territoryTransportAndBombardMap.get(unloadTerritory).add(u);
          } else {
            final List<Unit> transports = new ArrayList<>();
            transports.add(u);
            territoryTransportAndBombardMap.put(unloadTerritory, transports);
          }
        }

        // Find all bombard units for each unload territory
        for (final Unit u : patd.getBombardTerritoryMap().keySet()) {
          final Territory unloadTerritory = patd.getBombardTerritoryMap().get(u);
          if (territoryTransportAndBombardMap.containsKey(unloadTerritory)) {
            territoryTransportAndBombardMap.get(unloadTerritory).add(u);
          } else {
            final List<Unit> transports = new ArrayList<>();
            transports.add(u);
            territoryTransportAndBombardMap.put(unloadTerritory, transports);
          }
        }

        // Determine counter attack results for each transport territory
        double enemyTuvSwing = 0.0;
        for (final Territory unloadTerritory : territoryTransportAndBombardMap.keySet()) {
          if (enemyAttackOptions.getMax(unloadTerritory) != null) {
            final List<Unit> enemyAttackers =
                enemyAttackOptions.getMax(unloadTerritory).getMaxUnits();
            final Set<Unit> defenders =
                new HashSet<>(
                    unloadTerritory
                        .getUnitCollection()
                        .getMatches(JBGMatches.unitIsAlliedNotOwned(player, data)));
            defenders.addAll(territoryTransportAndBombardMap.get(unloadTerritory));
            if (defendMap.get(unloadTerritory) != null) {
              defenders.addAll(defendMap.get(unloadTerritory).getMaxUnits());
            }
            final JBGBattleResult result =
                calc.calculateBattleResults(
                    jbgData,
                    unloadTerritory,
                    enemyAttackOptions.getMax(unloadTerritory).getMaxUnits(),
                    new ArrayList<>(defenders),
                    new HashSet<>());
            final JBGBattleResult minResult =
                calc.calculateBattleResults(
                    jbgData,
                    unloadTerritory,
                    enemyAttackOptions.getMax(unloadTerritory).getMaxUnits(),
                    territoryTransportAndBombardMap.get(unloadTerritory),
                    new HashSet<>());
            final double minTuvSwing = Math.min(result.getTuvSwing(), minResult.getTuvSwing());
            if (minTuvSwing > 0) {
              enemyTuvSwing += minTuvSwing;
            }
            JBGLogger.trace(
                unloadTerritory
                    + ", EnemyAttackers="
                    + enemyAttackers.size()
                    + ", MaxDefenders="
                    + defenders.size()
                    + ", MaxEnemyTUVSwing="
                    + result.getTuvSwing()
                    + ", MinDefenders="
                    + territoryTransportAndBombardMap.get(unloadTerritory).size()
                    + ", MinEnemyTUVSwing="
                    + minResult.getTuvSwing());
          } else {
            JBGLogger.trace("Territory=" + unloadTerritory.getName() + " has no enemy attackers");
          }
        }

        // Determine whether its worth attacking
        final JBGBattleResult result =
            calc.calculateBattleResults(
                jbgData,
                t,
                patd.getUnits(),
                patd.getMaxEnemyDefenders(player, data),
                patd.getBombardTerritoryMap().keySet());
        int production = 0;
        int isEnemyCapital = 0;
        final TerritoryAttachment ta = TerritoryAttachment.get(t);
        if (ta != null) {
          production = ta.getProduction();
          if (ta.isCapital()) {
            isEnemyCapital = 1;
          }
        }
        final double attackValue = result.getTuvSwing() + production * (1 + 3.0 * isEnemyCapital);
        if (!patd.isStrafing() && (0.75 * enemyTuvSwing) > attackValue) {
          JBGLogger.debug(
              "Removing amphib territory: "
                  + patd.getTerritory()
                  + ", enemyTUVSwing="
                  + enemyTuvSwing
                  + ", attackValue="
                  + attackValue);
          attackMap.get(t).getUnits().clear();
          attackMap.get(t).getAmphibAttackMap().clear();
          attackMap.get(t).getBombardTerritoryMap().clear();
        } else {
          JBGLogger.debug(
              "Keeping amphib territory: "
                  + patd.getTerritory()
                  + ", enemyTUVSwing="
                  + enemyTuvSwing
                  + ", attackValue="
                  + attackValue);
        }
      }
    }
  }

  private void determineUnitsToAttackWith(
      final List<JBGTerritory> prioritizedTerritories, final List<Unit> alreadyMovedUnits) {

    JBGLogger.info("Determine units to attack each territory with");

    final Map<Territory, JBGTerritory> attackMap =
        territoryManager.getAttackOptions().getTerritoryMap();
    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();
    final Map<Unit, Set<Territory>> unitAttackMap =
        territoryManager.getAttackOptions().getUnitMoveMap();

    // Assign units to territories by prioritization
    while (true) {
      Map<Unit, Set<Territory>> sortedUnitAttackOptions =
          tryToAttackTerritories(prioritizedTerritories, alreadyMovedUnits);

      // Clear bombers
      for (final JBGTerritory t : attackMap.values()) {
        t.getBombers().clear();
      }

      // Get all units that have already moved
      final Set<Unit> alreadyAttackedWithUnits = new HashSet<>();
      for (final JBGTerritory t : attackMap.values()) {
        alreadyAttackedWithUnits.addAll(t.getUnits());
        alreadyAttackedWithUnits.addAll(t.getAmphibAttackMap().keySet());
      }

      // Check to see if any territories can be bombed
      final Map<Unit, Set<Territory>> bomberMoveMap =
          territoryManager.getAttackOptions().getBomberMoveMap();
      for (final Unit unit : bomberMoveMap.keySet()) {
        if (alreadyAttackedWithUnits.contains(unit)) {
          continue;
        }
        Optional<Territory> maxBombingTerritory = Optional.empty();
        int maxBombingScore = MIN_BOMBING_SCORE;
        for (final Territory t : bomberMoveMap.get(unit)) {
          final boolean canBeBombedByThisUnit =
              t.getUnitCollection()
                  .anyMatch(
                      Matches.unitCanProduceUnitsAndCanBeDamaged()
                          .and(Matches.unitIsLegalBombingTargetBy(unit)));
          final boolean canCreateAirBattle =
              Properties.getRaidsMayBePreceededByAirBattles(data)
                  && AirBattle.territoryCouldPossiblyHaveAirBattleDefenders(t, player, data, true);
          if (canBeBombedByThisUnit
              && !canCreateAirBattle
              && canAirSafelyLandAfterAttack(unit, t)) {
            final int noAaBombingDefense =
                t.getUnitCollection().anyMatch(Matches.unitIsAaForBombingThisUnitOnly()) ? 0 : 1;
            int maxDamage = 0;
            final TerritoryAttachment ta = TerritoryAttachment.get(t);
            if (ta != null) {
              maxDamage = ta.getProduction();
            }
            final int numExistingBombers = attackMap.get(t).getBombers().size();
            final int remainingDamagePotential = maxDamage - 3 * numExistingBombers;
            final int bombingScore = (1 + 9 * noAaBombingDefense) * remainingDamagePotential;
            if (bombingScore >= maxBombingScore) {
              maxBombingScore = bombingScore;
              maxBombingTerritory = Optional.of(t);
            }
          }
        }
        if (maxBombingTerritory.isPresent()) {
          final Territory t = maxBombingTerritory.get();
          attackMap.get(t).getBombers().add(unit);
          sortedUnitAttackOptions.remove(unit);
          JBGLogger.debug("Add bomber (" + unit + ") to " + t);
        }
      }

      // Re-sort attack options
      sortedUnitAttackOptions =
          JBGSortMoveOptionsUtils.sortUnitNeededOptionsThenAttack(
              jbgData, player, sortedUnitAttackOptions, attackMap, calc);
      final List<Unit> addedUnits = new ArrayList<>();

      // Set air units in any territory with no AA (don't move planes to empty territories)
      for (final Unit unit : sortedUnitAttackOptions.keySet()) {
        final boolean isAirUnit = UnitAttachment.get(unit.getType()).getIsAir();
        if (!isAirUnit) {
          continue; // skip non-air units
        }
        Territory minWinTerritory = null;
        double minWinPercentage = Double.MAX_VALUE;
        for (final Territory t : sortedUnitAttackOptions.get(unit)) {
          final JBGTerritory patd = attackMap.get(t);

          // Check if air unit should avoid this territory due to no guaranteed safe landing
          // location
          final boolean isEnemyFactory =
              JBGMatches.territoryHasInfraFactoryAndIsEnemyLand(player, data).test(t);
          if (!isEnemyFactory && !canAirSafelyLandAfterAttack(unit, t)) {
            continue;
          }
          if (patd.getBattleResult() == null) {
            patd.estimateBattleResult(calc, player);
          }
          final JBGBattleResult result = patd.getBattleResult();
          if (result.getWinPercentage() < minWinPercentage
              || (!result.isHasLandUnitRemaining() && minWinTerritory == null)) {
            final List<Unit> attackingUnits = patd.getUnits();
            final List<Unit> defendingUnits = patd.getMaxEnemyDefenders(player, data);
            final boolean isOverwhelmingWin =
                JBGBattleUtils.checkForOverwhelmingWin(jbgData, t, attackingUnits, defendingUnits);
            final boolean hasAa = defendingUnits.stream().anyMatch(Matches.unitIsAaForAnything());
            if (!hasAa && !isOverwhelmingWin) {
              minWinPercentage = result.getWinPercentage();
              minWinTerritory = t;
            }
          }
        }
        if (minWinTerritory != null) {
          attackMap.get(minWinTerritory).setBattleResult(null);
          attackMap.get(minWinTerritory).addUnit(unit);
          addedUnits.add(unit);
        }
      }
      sortedUnitAttackOptions.keySet().removeAll(addedUnits);

      // Re-sort attack options
      sortedUnitAttackOptions =
          JBGSortMoveOptionsUtils.sortUnitNeededOptionsThenAttack(
              jbgData, player, sortedUnitAttackOptions, attackMap, calc);

      // Find territory that we can try to hold that needs unit
      for (final Unit unit : sortedUnitAttackOptions.keySet()) {
        if (addedUnits.contains(unit)) {
          continue;
        }
        Territory minWinTerritory = null;
        for (final Territory t : sortedUnitAttackOptions.get(unit)) {
          final JBGTerritory patd = attackMap.get(t);
          if (patd.isCanHold()) {

            // Check if I already have enough attack units to win in 2 rounds
            if (patd.getBattleResult() == null) {
              patd.estimateBattleResult(calc, player);
            }
            final JBGBattleResult result = patd.getBattleResult();
            final List<Unit> attackingUnits = patd.getUnits();
            final List<Unit> defendingUnits = patd.getMaxEnemyDefenders(player, data);
            final boolean isOverwhelmingWin =
                JBGBattleUtils.checkForOverwhelmingWin(jbgData, t, attackingUnits, defendingUnits);
            if (!isOverwhelmingWin && result.getBattleRounds() > 2) {
              minWinTerritory = t;
              break;
            }
          }
        }
        if (minWinTerritory != null) {
          attackMap.get(minWinTerritory).setBattleResult(null);
          final List<Unit> unitsToAdd =
              JBGTransportUtils.getUnitsToAdd(jbgData, unit, alreadyMovedUnits, attackMap);
          attackMap.get(minWinTerritory).addUnits(unitsToAdd);
          addedUnits.addAll(unitsToAdd);
        }
      }
      sortedUnitAttackOptions.keySet().removeAll(addedUnits);

      // Re-sort attack options
      sortedUnitAttackOptions =
          JBGSortMoveOptionsUtils.sortUnitNeededOptionsThenAttack(
              jbgData, player, sortedUnitAttackOptions, attackMap, calc);

      // Add sea units to any territory that significantly increases TUV gain
      for (final Unit unit : sortedUnitAttackOptions.keySet()) {
        final boolean isSeaUnit = UnitAttachment.get(unit.getType()).getIsSea();
        if (!isSeaUnit) {
          continue; // skip non-sea units
        }
        for (final Territory t : sortedUnitAttackOptions.get(unit)) {
          final JBGTerritory patd = attackMap.get(t);
          if (attackMap.get(t).getBattleResult() == null) {
            patd.estimateBattleResult(calc, player);
          }
          final JBGBattleResult result = attackMap.get(t).getBattleResult();
          final List<Unit> attackers = new ArrayList<>(patd.getUnits());
          attackers.add(unit);
          final JBGBattleResult result2 =
              calc.estimateAttackBattleResults(
                  jbgData,
                  t,
                  attackers,
                  patd.getMaxEnemyDefenders(player, data),
                  patd.getBombardTerritoryMap().keySet());
          final double unitValue = jbgData.getUnitValue(unit.getType());
          if ((result2.getTuvSwing() - unitValue / 3) > result.getTuvSwing()) {
            attackMap.get(t).setBattleResult(null);
            attackMap.get(t).addUnit(unit);
            addedUnits.add(unit);
            break;
          }
        }
      }
      sortedUnitAttackOptions.keySet().removeAll(addedUnits);

      // Determine if all attacks are worth it
      final List<Unit> usedUnits = new ArrayList<>();
      for (final JBGTerritory patd : prioritizedTerritories) {
        usedUnits.addAll(patd.getUnits());
      }
      JBGTerritory territoryToRemove = null;
      for (final JBGTerritory patd : prioritizedTerritories) {
        final Territory t = patd.getTerritory();

        // Find battle result
        if (patd.getBattleResult() == null) {
          patd.estimateBattleResult(calc, player);
        }
        final JBGBattleResult result = patd.getBattleResult();

        // Determine enemy counter attack results
        boolean canHold = true;
        double enemyCounterTuvSwing = 0;
        if (enemyAttackOptions.getMax(t) != null
            && !JBGMatches.territoryIsWaterAndAdjacentToOwnedFactory(player, data).test(t)) {
          List<Unit> remainingUnitsToDefendWith =
              CollectionUtils.getMatches(
                  result.getAverageAttackersRemaining(), Matches.unitIsAir().negate());
          JBGBattleResult result2 =
              calc.calculateBattleResults(
                  jbgData,
                  t,
                  patd.getMaxEnemyUnits(),
                  remainingUnitsToDefendWith,
                  patd.getMaxBombardUnits());
          if (patd.isCanHold() && result2.getTuvSwing() > 0) {
            final List<Unit> unusedUnits = new ArrayList<>(patd.getMaxUnits());
            unusedUnits.addAll(patd.getMaxAmphibUnits());
            unusedUnits.removeAll(usedUnits);
            unusedUnits.addAll(remainingUnitsToDefendWith);
            final JBGBattleResult result3 =
                calc.calculateBattleResults(
                    jbgData, t, patd.getMaxEnemyUnits(), unusedUnits, patd.getMaxBombardUnits());
            if (result3.getTuvSwing() < result2.getTuvSwing()) {
              result2 = result3;
              remainingUnitsToDefendWith = unusedUnits;
            }
          }
          canHold =
              (!result2.isHasLandUnitRemaining() && !t.isWater())
                  || (result2.getTuvSwing() < 0)
                  || (result2.getWinPercentage() < jbgData.getMinWinPercentage());
          if (result2.getTuvSwing() > 0) {
            enemyCounterTuvSwing = result2.getTuvSwing();
          }
          JBGLogger.trace(
              "Territory="
                  + t.getName()
                  + ", CanHold="
                  + canHold
                  + ", MyDefenders="
                  + remainingUnitsToDefendWith.size()
                  + ", EnemyAttackers="
                  + patd.getMaxEnemyUnits().size()
                  + ", win%="
                  + result2.getWinPercentage()
                  + ", EnemyTUVSwing="
                  + result2.getTuvSwing()
                  + ", hasLandUnitRemaining="
                  + result2.isHasLandUnitRemaining());
        }

        // Find attack value
        final boolean isNeutral = JBGUtils.isNeutralLand(t);
        final int isLand = !t.isWater() ? 1 : 0;
        final int isCanHold = canHold ? 1 : 0;
        final int isCantHoldAmphib = !canHold && !patd.getAmphibAttackMap().isEmpty() ? 1 : 0;
        final int isFactory = JBGMatches.territoryHasInfraFactoryAndIsLand().test(t) ? 1 : 0;
        final int isFfa = JBGUtils.isFfa(data, player) ? 1 : 0;
        final int production = TerritoryAttachment.getProduction(t);
        double capitalValue = 0;
        final TerritoryAttachment ta = TerritoryAttachment.get(t);
        if (ta != null && ta.isCapital()) {
          capitalValue = JBGUtils.getPlayerProduction(t.getOwner(), data);
        }
        final double territoryValue =
            (1
                        + isLand
                        - isCantHoldAmphib
                        + isFactory
                        + isCanHold * (1 + 2.0 * isFfa + 2.0 * isFactory))
                    * production
                + capitalValue;
        double tuvSwing = result.getTuvSwing();
        if (isFfa == 1 && tuvSwing > 0) {
          tuvSwing *= 0.5;
        }
        final double attackValue =
            1
                + tuvSwing
                + territoryValue * result.getWinPercentage() / 100
                - enemyCounterTuvSwing * 2 / 3;
        boolean allUnitsCanAttackOtherTerritory = true;
        if (isNeutral && attackValue < 0) {
          for (final Unit u : patd.getUnits()) {
            boolean canAttackOtherTerritory = false;
            for (final JBGTerritory patd2 : prioritizedTerritories) {
              if (!patd.equals(patd2)
                  && unitAttackMap.get(u) != null
                  && unitAttackMap.get(u).contains(patd2.getTerritory())) {
                canAttackOtherTerritory = true;
                break;
              }
            }
            if (!canAttackOtherTerritory) {
              allUnitsCanAttackOtherTerritory = false;
              break;
            }
          }
        }

        // Determine whether to remove attack
        if (!patd.isStrafing()
            && (result.getWinPercentage() < jbgData.getMinWinPercentage()
                || !result.isHasLandUnitRemaining()
                || (isNeutral && !canHold)
                || (attackValue < 0
                    && (!isNeutral
                        || allUnitsCanAttackOtherTerritory
                        || result.getBattleRounds() >= 4)))) {
          territoryToRemove = patd;
        }
        JBGLogger.debug(
            patd.getResultString()
                + ", attackValue="
                + attackValue
                + ", territoryValue="
                + territoryValue
                + ", allUnitsCanAttackOtherTerritory="
                + allUnitsCanAttackOtherTerritory
                + " with attackers="
                + patd.getUnits());
      }

      // Determine whether all attacks are successful or try to hold fewer territories
      if (territoryToRemove == null) {
        break;
      }

      prioritizedTerritories.remove(territoryToRemove);
      JBGLogger.debug("Removing " + territoryToRemove.getTerritory().getName());
    }
  }

  private Map<Unit, Set<Territory>> tryToAttackTerritories(
      final List<JBGTerritory> prioritizedTerritories, final List<Unit> alreadyMovedUnits) {

    final Map<Territory, JBGTerritory> attackMap =
        territoryManager.getAttackOptions().getTerritoryMap();
    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();
    final Map<Unit, Set<Territory>> unitAttackMap =
        territoryManager.getAttackOptions().getUnitMoveMap();
    final Map<Unit, Set<Territory>> transportAttackMap =
        territoryManager.getAttackOptions().getTransportMoveMap();
    final Map<Unit, Set<Territory>> bombardMap =
        territoryManager.getAttackOptions().getBombardMap();
    final List<JBGTransport> transportMapList =
        territoryManager.getAttackOptions().getTransportList();

    // Reset lists
    for (final JBGTerritory t : attackMap.values()) {
      t.getUnits().clear();
      t.getBombardTerritoryMap().clear();
      t.getAmphibAttackMap().clear();
      t.getTransportTerritoryMap().clear();
      t.setBattleResult(null);
    }

    // Loop through all units and determine attack options
    final Map<Unit, Set<Territory>> unitAttackOptions = new HashMap<>();
    for (final Unit unit : unitAttackMap.keySet()) {

      // Find number of attack options
      final Set<Territory> canAttackTerritories = new HashSet<>();
      for (final JBGTerritory attackTerritoryData : prioritizedTerritories) {
        if (unitAttackMap.get(unit).contains(attackTerritoryData.getTerritory())) {
          canAttackTerritories.add(attackTerritoryData.getTerritory());
        }
      }

      // Add units with attack options to map
      if (!canAttackTerritories.isEmpty()) {
        unitAttackOptions.put(unit, canAttackTerritories);
      }
    }

    // Sort units by number of attack options and cost
    Map<Unit, Set<Territory>> sortedUnitAttackOptions =
        JBGSortMoveOptionsUtils.sortUnitMoveOptions(jbgData, unitAttackOptions);
    final List<Unit> addedUnits = new ArrayList<>();

    // Try to set at least one destroyer in each sea territory with subs
    for (final Unit unit : sortedUnitAttackOptions.keySet()) {
      final boolean isDestroyerUnit = UnitAttachment.get(unit.getType()).getIsDestroyer();
      if (!isDestroyerUnit) {
        continue; // skip non-destroyer units
      }
      for (final Territory t : sortedUnitAttackOptions.get(unit)) {

        // Add destroyer if territory has subs and a destroyer has been already added
        final List<Unit> defendingUnits = attackMap.get(t).getMaxEnemyDefenders(player, data);
        if (defendingUnits.stream().anyMatch(Matches.unitHasSubBattleAbilities())
            && attackMap.get(t).getUnits().stream().noneMatch(Matches.unitIsDestroyer())) {
          attackMap.get(t).addUnit(unit);
          addedUnits.add(unit);
          break;
        }
      }
    }
    sortedUnitAttackOptions.keySet().removeAll(addedUnits);

    // Set enough land and sea units in territories to have at least a chance of winning
    for (final Unit unit : sortedUnitAttackOptions.keySet()) {
      final boolean isAirUnit = UnitAttachment.get(unit.getType()).getIsAir();
      final boolean isExpensiveLandUnit =
          Matches.unitIsLand().test(unit)
              && jbgData.getUnitValue(unit.getType()) > 2 * jbgData.getMinCostPerHitPoint();
      if (isAirUnit || isExpensiveLandUnit || addedUnits.contains(unit)) {
        continue; // skip air and expensive units
      }
      final TreeMap<Double, Territory> estimatesMap = new TreeMap<>();
      for (final Territory t : sortedUnitAttackOptions.get(unit)) {
        if (t.isWater() && !attackMap.get(t).isCanHold()) {
          continue; // ignore sea territories that can't be held
        }
        final List<Unit> defendingUnits = attackMap.get(t).getMaxEnemyDefenders(player, data);
        double estimate =
            JBGBattleUtils.estimateStrengthDifference(
                jbgData, t, attackMap.get(t).getUnits(), defendingUnits);
        final boolean hasAa = defendingUnits.stream().anyMatch(Matches.unitIsAaForAnything());
        if (hasAa) {
          estimate -= 10;
        }
        estimatesMap.put(estimate, t);
      }
      if (!estimatesMap.isEmpty() && estimatesMap.firstKey() < 40) {
        final Territory minWinTerritory = estimatesMap.entrySet().iterator().next().getValue();
        final List<Unit> unitsToAdd =
            JBGTransportUtils.getUnitsToAdd(jbgData, unit, alreadyMovedUnits, attackMap);
        attackMap.get(minWinTerritory).addUnits(unitsToAdd);
        addedUnits.addAll(unitsToAdd);
      }
    }
    sortedUnitAttackOptions.keySet().removeAll(addedUnits);

    // Re-sort attack options
    sortedUnitAttackOptions =
        JBGSortMoveOptionsUtils.sortUnitNeededOptionsThenAttack(
            jbgData, player, sortedUnitAttackOptions, attackMap, calc);

    // Set non-air units in territories that can be held
    for (final Unit unit : sortedUnitAttackOptions.keySet()) {
      final boolean isAirUnit = UnitAttachment.get(unit.getType()).getIsAir();
      if (isAirUnit || addedUnits.contains(unit)) {
        continue; // skip air units
      }
      Territory minWinTerritory = null;
      double minWinPercentage = jbgData.getWinPercentage();
      for (final Territory t : sortedUnitAttackOptions.get(unit)) {
        final JBGTerritory patd = attackMap.get(t);
        if (!attackMap.get(t).isCurrentlyWins() && attackMap.get(t).isCanHold()) {
          if (attackMap.get(t).getBattleResult() == null) {
            patd.estimateBattleResult(calc, player);
          }
          final JBGBattleResult result = attackMap.get(t).getBattleResult();
          if (result.getWinPercentage() < minWinPercentage
              || (!result.isHasLandUnitRemaining() && minWinTerritory == null)) {
            minWinPercentage = result.getWinPercentage();
            minWinTerritory = t;
          }
        }
      }
      if (minWinTerritory != null) {
        attackMap.get(minWinTerritory).setBattleResult(null);
        final List<Unit> unitsToAdd =
            JBGTransportUtils.getUnitsToAdd(jbgData, unit, alreadyMovedUnits, attackMap);
        attackMap.get(minWinTerritory).addUnits(unitsToAdd);
        addedUnits.addAll(unitsToAdd);
      }
    }
    sortedUnitAttackOptions.keySet().removeAll(addedUnits);

    // Re-sort attack options
    sortedUnitAttackOptions =
        JBGSortMoveOptionsUtils.sortUnitNeededOptionsThenAttack(
            jbgData, player, sortedUnitAttackOptions, attackMap, calc);

    // Set air units in territories that can't be held (don't move planes to empty territories)
    for (final Unit unit : sortedUnitAttackOptions.keySet()) {
      final boolean isAirUnit = UnitAttachment.get(unit.getType()).getIsAir();
      if (!isAirUnit) {
        continue; // skip non-air units
      }
      Territory minWinTerritory = null;
      double minWinPercentage = jbgData.getWinPercentage();
      for (final Territory t : sortedUnitAttackOptions.get(unit)) {
        final JBGTerritory patd = attackMap.get(t);
        if (!patd.isCurrentlyWins() && !patd.isCanHold()) {

          // Check if air unit should avoid this territory due to no guaranteed safe landing
          // location
          final boolean isEnemyCapital = JBGUtils.getLiveEnemyCapitals(data, player).contains(t);
          final boolean isAdjacentToAlliedCapital =
              Matches.territoryHasNeighborMatching(
                      data, Matches.territoryIsInList(JBGUtils.getLiveAlliedCapitals(data, player)))
                  .test(t);
          final int range = unit.getMovementLeft().intValue();
          final int distance =
              data.getMap()
                  .getDistanceIgnoreEndForCondition(
                      jbgData.getUnitTerritory(unit),
                      t,
                      JBGMatches.territoryCanMoveAirUnitsAndNoAa(player, data, true));
          final boolean usesMoreThanHalfOfRange = distance > range / 2;
          if (!isEnemyCapital && !isAdjacentToAlliedCapital && usesMoreThanHalfOfRange) {
            continue;
          }

          // Check battle results
          if (patd.getBattleResult() == null) {
            patd.estimateBattleResult(calc, player);
          }
          final JBGBattleResult result = patd.getBattleResult();
          if (result.getWinPercentage() < minWinPercentage
              || (!result.isHasLandUnitRemaining() && minWinTerritory == null)) {
            final List<Unit> defendingUnits = patd.getMaxEnemyDefenders(player, data);
            final boolean hasNoDefenders =
                defendingUnits.stream().noneMatch(JBGMatches.unitIsEnemyAndNotInfa(player, data));
            final boolean isOverwhelmingWin =
                JBGBattleUtils.checkForOverwhelmingWin(jbgData, t, patd.getUnits(), defendingUnits);
            final boolean hasAa = defendingUnits.stream().anyMatch(Matches.unitIsAaForAnything());
            if (!hasNoDefenders
                && !isOverwhelmingWin
                && (!hasAa || result.getWinPercentage() < minWinPercentage)) {
              minWinPercentage = result.getWinPercentage();
              minWinTerritory = t;
              if (patd.isStrafing()) {
                break;
              }
            }
          }
        }
      }
      if (minWinTerritory != null) {
        attackMap.get(minWinTerritory).setBattleResult(null);
        attackMap.get(minWinTerritory).addUnit(unit);
        addedUnits.add(unit);
      }
    }
    sortedUnitAttackOptions.keySet().removeAll(addedUnits);

    // Re-sort attack options
    sortedUnitAttackOptions =
        JBGSortMoveOptionsUtils.sortUnitNeededOptionsThenAttack(
            jbgData, player, sortedUnitAttackOptions, attackMap, calc);

    // Set remaining units in any territory that needs it (don't move planes to empty territories)
    for (final Unit unit : sortedUnitAttackOptions.keySet()) {
      if (addedUnits.contains(unit)) {
        continue;
      }
      final boolean isAirUnit = UnitAttachment.get(unit.getType()).getIsAir();
      Territory minWinTerritory = null;
      double minWinPercentage = jbgData.getWinPercentage();
      for (final Territory t : sortedUnitAttackOptions.get(unit)) {
        final JBGTerritory patd = attackMap.get(t);
        if (!patd.isCurrentlyWins()) {

          // Check if air unit should avoid this territory due to no guaranteed safe landing
          // location
          final boolean isAdjacentToAlliedFactory =
              Matches.territoryHasNeighborMatching(
                      data, JBGMatches.territoryHasInfraFactoryAndIsAlliedLand(player, data))
                  .test(t);
          final int range = unit.getMovementLeft().intValue();
          final int distance =
              data.getMap()
                  .getDistanceIgnoreEndForCondition(
                      jbgData.getUnitTerritory(unit),
                      t,
                      JBGMatches.territoryCanMoveAirUnitsAndNoAa(player, data, true));
          final boolean usesMoreThanHalfOfRange = distance > range / 2;
          final boolean territoryValueIsLessThanUnitValue =
              patd.getValue() < jbgData.getUnitValue(unit.getType());
          if (isAirUnit
              && !isAdjacentToAlliedFactory
              && usesMoreThanHalfOfRange
              && (territoryValueIsLessThanUnitValue || (!t.isWater() && !patd.isCanHold()))) {
            continue;
          }
          if (patd.getBattleResult() == null) {
            patd.estimateBattleResult(calc, player);
          }
          final JBGBattleResult result = patd.getBattleResult();
          if (result.getWinPercentage() < minWinPercentage
              || (!result.isHasLandUnitRemaining() && minWinTerritory == null)) {
            final List<Unit> defendingUnits = patd.getMaxEnemyDefenders(player, data);
            final boolean hasNoDefenders =
                defendingUnits.stream().noneMatch(JBGMatches.unitIsEnemyAndNotInfa(player, data));
            final boolean isOverwhelmingWin =
                JBGBattleUtils.checkForOverwhelmingWin(jbgData, t, patd.getUnits(), defendingUnits);
            final boolean hasAa = defendingUnits.stream().anyMatch(Matches.unitIsAaForAnything());
            if (!isAirUnit
                || (!hasNoDefenders
                    && !isOverwhelmingWin
                    && (!hasAa || result.getWinPercentage() < minWinPercentage))) {
              minWinPercentage = result.getWinPercentage();
              minWinTerritory = t;
            }
          }
        }
      }
      if (minWinTerritory != null) {
        attackMap.get(minWinTerritory).setBattleResult(null);
        final List<Unit> unitsToAdd =
            JBGTransportUtils.getUnitsToAdd(jbgData, unit, alreadyMovedUnits, attackMap);
        attackMap.get(minWinTerritory).addUnits(unitsToAdd);
        addedUnits.addAll(unitsToAdd);
      }
    }
    sortedUnitAttackOptions.keySet().removeAll(addedUnits);

    // Re-sort attack options
    sortedUnitAttackOptions =
        JBGSortMoveOptionsUtils.sortUnitNeededOptions(
            jbgData, player, sortedUnitAttackOptions, attackMap, calc);

    // If transports can take casualties try placing in naval battles first
    final List<Unit> alreadyAttackedWithTransports = new ArrayList<>();
    if (!Properties.getTransportCasualtiesRestricted(data)) {

      // Loop through all my transports and see which territories they can attack from current list
      final Map<Unit, Set<Territory>> transportAttackOptions = new HashMap<>();
      for (final Unit unit : transportAttackMap.keySet()) {

        // Find number of attack options
        final Set<Territory> canAttackTerritories = new HashSet<>();
        for (final JBGTerritory attackTerritoryData : prioritizedTerritories) {
          if (transportAttackMap.get(unit).contains(attackTerritoryData.getTerritory())) {
            canAttackTerritories.add(attackTerritoryData.getTerritory());
          }
        }
        if (!canAttackTerritories.isEmpty()) {
          transportAttackOptions.put(unit, canAttackTerritories);
        }
      }

      // Loop through transports with attack options and determine if any naval battle needs it
      for (final Unit transport : transportAttackOptions.keySet()) {

        // Find current naval battle that needs transport if it isn't transporting units
        for (final Territory t : transportAttackOptions.get(transport)) {
          final JBGTerritory patd = attackMap.get(t);
          final List<Unit> defendingUnits = patd.getMaxEnemyDefenders(player, data);
          if (!patd.isCurrentlyWins()
              && !TransportTracker.isTransporting(transport)
              && !defendingUnits.isEmpty()) {
            if (patd.getBattleResult() == null) {
              patd.estimateBattleResult(calc, player);
            }
            final JBGBattleResult result = patd.getBattleResult();
            if (result.getWinPercentage() < jbgData.getWinPercentage()
                || !result.isHasLandUnitRemaining()) {
              patd.addUnit(transport);
              patd.setBattleResult(null);
              alreadyAttackedWithTransports.add(transport);
              JBGLogger.trace("Adding attack transport to: " + t.getName());
              break;
            }
          }
        }
      }
    }

    // Loop through all my transports and see which can make amphib attack
    final Map<Unit, Set<Territory>> amphibAttackOptions = new HashMap<>();
    for (final JBGTransport proTransportData : transportMapList) {

      // If already used to attack then ignore
      if (alreadyAttackedWithTransports.contains(proTransportData.getTransport())) {
        continue;
      }

      // Find number of attack options
      final Set<Territory> canAmphibAttackTerritories = new HashSet<>();
      for (final JBGTerritory attackTerritoryData : prioritizedTerritories) {
        if (proTransportData.getTransportMap().containsKey(attackTerritoryData.getTerritory())) {
          canAmphibAttackTerritories.add(attackTerritoryData.getTerritory());
        }
      }
      if (!canAmphibAttackTerritories.isEmpty()) {
        amphibAttackOptions.put(proTransportData.getTransport(), canAmphibAttackTerritories);
      }
    }

    // Loop through transports with amphib attack options and determine if any land battle needs it
    for (final Unit transport : amphibAttackOptions.keySet()) {

      // Find current land battle results for territories that unit can amphib attack
      Territory minWinTerritory = null;
      double minWinPercentage = jbgData.getWinPercentage();
      List<Unit> minAmphibUnitsToAdd = null;
      Territory minUnloadFromTerritory = null;
      for (final Territory t : amphibAttackOptions.get(transport)) {
        final JBGTerritory patd = attackMap.get(t);
        if (!patd.isCurrentlyWins()) {
          if (patd.getBattleResult() == null) {
            patd.estimateBattleResult(calc, player);
          }
          final JBGBattleResult result = patd.getBattleResult();
          if (result.getWinPercentage() < minWinPercentage
              || (!result.isHasLandUnitRemaining() && minWinTerritory == null)) {

            // Find units that haven't attacked and can be transported
            final List<Unit> alreadyAttackedWithUnits =
                JBGTransportUtils.getMovedUnits(alreadyMovedUnits, attackMap);
            for (final JBGTransport proTransportData : transportMapList) {
              if (proTransportData.getTransport().equals(transport)) {

                // Find units to load
                final Set<Territory> territoriesCanLoadFrom =
                    proTransportData.getTransportMap().get(t);
                final List<Unit> amphibUnitsToAdd =
                    JBGTransportUtils.getUnitsToTransportFromTerritories(
                        player, transport, territoriesCanLoadFrom, alreadyAttackedWithUnits);
                if (amphibUnitsToAdd.isEmpty()) {
                  continue;
                }

                // Find best territory to move transport
                double minStrengthDifference = Double.POSITIVE_INFINITY;
                minUnloadFromTerritory = null;
                final Set<Territory> territoriesToMoveTransport =
                    data.getMap()
                        .getNeighbors(t, JBGMatches.territoryCanMoveSeaUnits(player, data, false));
                final Set<Territory> loadFromTerritories = new HashSet<>();
                for (final Unit u : amphibUnitsToAdd) {
                  loadFromTerritories.add(jbgData.getUnitTerritory(u));
                }
                for (final Territory territoryToMoveTransport : territoriesToMoveTransport) {
                  if (proTransportData.getSeaTransportMap().containsKey(territoryToMoveTransport)
                      && proTransportData
                          .getSeaTransportMap()
                          .get(territoryToMoveTransport)
                          .containsAll(loadFromTerritories)) {
                    List<Unit> attackers = new ArrayList<>();
                    if (enemyAttackOptions.getMax(territoryToMoveTransport) != null) {
                      attackers = enemyAttackOptions.getMax(territoryToMoveTransport).getMaxUnits();
                    }
                    final List<Unit> defenders =
                        territoryToMoveTransport
                            .getUnitCollection()
                            .getMatches(Matches.isUnitAllied(player, data));
                    defenders.add(transport);
                    final double strengthDifference =
                        JBGBattleUtils.estimateStrengthDifference(
                            jbgData, territoryToMoveTransport, attackers, defenders);
                    if (strengthDifference <= minStrengthDifference) {
                      minStrengthDifference = strengthDifference;
                      minUnloadFromTerritory = territoryToMoveTransport;
                    }
                  }
                }
                minWinTerritory = t;
                minWinPercentage = result.getWinPercentage();
                minAmphibUnitsToAdd = amphibUnitsToAdd;
                break;
              }
            }
          }
        }
      }
      if (minWinTerritory != null) {
        if (minUnloadFromTerritory != null) {
          attackMap
              .get(minWinTerritory)
              .getTransportTerritoryMap()
              .put(transport, minUnloadFromTerritory);
        }
        attackMap.get(minWinTerritory).addUnits(minAmphibUnitsToAdd);
        attackMap.get(minWinTerritory).putAmphibAttackMap(transport, minAmphibUnitsToAdd);
        attackMap.get(minWinTerritory).setBattleResult(null);
        for (final Unit unit : minAmphibUnitsToAdd) {
          sortedUnitAttackOptions.remove(unit);
        }
        JBGLogger.trace(
            "Adding amphibious attack to "
                + minWinTerritory
                + ", units="
                + minAmphibUnitsToAdd.size()
                + ", unloadFrom="
                + minUnloadFromTerritory);
      }
    }

    // Get all units that have already moved
    final Set<Unit> alreadyAttackedWithUnits = new HashSet<>();
    for (final JBGTerritory t : attackMap.values()) {
      alreadyAttackedWithUnits.addAll(t.getUnits());
      alreadyAttackedWithUnits.addAll(t.getAmphibAttackMap().keySet());
    }

    // Loop through all my bombard units and see which can bombard
    final Map<Unit, Set<Territory>> bombardOptions = new HashMap<>();
    for (final Unit u : bombardMap.keySet()) {

      // If already used to attack then ignore
      if (alreadyAttackedWithUnits.contains(u)) {
        continue;
      }

      // Find number of bombard options
      final Set<Territory> canBombardTerritories = new HashSet<>();
      for (final JBGTerritory patd : prioritizedTerritories) {
        final List<Unit> defendingUnits = patd.getMaxEnemyDefenders(player, data);
        final boolean hasDefenders =
            defendingUnits.stream().anyMatch(Matches.unitIsInfrastructure().negate());
        if (bombardMap.get(u).contains(patd.getTerritory())
            && !patd.getTransportTerritoryMap().isEmpty()
            && hasDefenders
            && !TransportTracker.isTransporting(u)) {
          canBombardTerritories.add(patd.getTerritory());
        }
      }
      if (!canBombardTerritories.isEmpty()) {
        bombardOptions.put(u, canBombardTerritories);
      }
    }

    // Loop through bombard units to see if any amphib battles need
    for (final Unit u : bombardOptions.keySet()) {

      // Find current land battle results for territories that unit can bombard
      Territory minWinTerritory = null;
      double minWinPercentage = Double.MAX_VALUE;
      Territory minBombardFromTerritory = null;
      for (final Territory t : bombardOptions.get(u)) {
        final JBGTerritory patd = attackMap.get(t);
        if (patd.getBattleResult() == null) {
          patd.estimateBattleResult(calc, player);
        }
        final JBGBattleResult result = patd.getBattleResult();
        if (result.getWinPercentage() < minWinPercentage
            || (!result.isHasLandUnitRemaining() && minWinTerritory == null)) {

          // Find territory to bombard from
          Territory bombardFromTerritory = null;
          for (final Territory unloadFromTerritory : patd.getTransportTerritoryMap().values()) {
            if (patd.getBombardOptionsMap().get(u).contains(unloadFromTerritory)) {
              bombardFromTerritory = unloadFromTerritory;
            }
          }
          if (bombardFromTerritory != null) {
            minWinTerritory = t;
            minWinPercentage = result.getWinPercentage();
            minBombardFromTerritory = bombardFromTerritory;
          }
        }
      }
      if (minWinTerritory != null) {
        attackMap.get(minWinTerritory).getBombardTerritoryMap().put(u, minBombardFromTerritory);
        attackMap.get(minWinTerritory).setBattleResult(null);
        sortedUnitAttackOptions.remove(u);
        JBGLogger.trace(
            "Adding bombard to "
                + minWinTerritory
                + ", units="
                + u
                + ", bombardFrom="
                + minBombardFromTerritory);
      }
    }
    return sortedUnitAttackOptions;
  }

  private void removeAttacksUntilCapitalCanBeHeld(
      final List<JBGTerritory> prioritizedTerritories,
      final List<JBGPurchaseOption> landPurchaseOptions) {

    JBGLogger.info("Check capital defenses after attack moves");

    final Map<Territory, JBGTerritory> attackMap =
        territoryManager.getAttackOptions().getTerritoryMap();

    final Territory myCapital = jbgData.getMyCapital();

    // Add max purchase defenders to capital for non-mobile factories (don't consider mobile
    // factories since they may
    // move elsewhere)
    final List<Unit> placeUnits = new ArrayList<>();
    if (JBGMatches.territoryHasNonMobileFactoryAndIsNotConqueredOwnedLand(player, data)
        .test(myCapital)) {
      placeUnits.addAll(
          JBGPurchaseUtils.findMaxPurchaseDefenders(
              jbgData, player, myCapital, landPurchaseOptions));
    }

    // Remove attack until capital can be defended
    while (true) {
      if (prioritizedTerritories.isEmpty()) {
        break;
      }

      // Determine max enemy counter attack units
      final List<Territory> territoriesToAttack = new ArrayList<>();
      for (final JBGTerritory t : prioritizedTerritories) {
        territoriesToAttack.add(t.getTerritory());
      }
      JBGLogger.trace("Remaining territories to attack=" + territoriesToAttack);
      final List<Territory> territoriesToCheck = new ArrayList<>();
      territoriesToCheck.add(myCapital);
      territoryManager.populateEnemyAttackOptions(territoriesToAttack, territoriesToCheck);
      final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();
      if (enemyAttackOptions.getMax(myCapital) == null) {
        break;
      }

      // Find max remaining defenders
      final Set<Territory> territoriesAdjacentToCapital =
          data.getMap().getNeighbors(myCapital, Matches.territoryIsLand());
      final List<Unit> defenders =
          myCapital.getUnitCollection().getMatches(Matches.isUnitAllied(player, data));
      defenders.addAll(placeUnits);
      for (final Territory t : territoriesAdjacentToCapital) {
        defenders.addAll(
            t.getUnitCollection()
                .getMatches(JBGMatches.unitCanBeMovedAndIsOwnedLand(player, false)));
      }
      for (final JBGTerritory t : attackMap.values()) {
        defenders.removeAll(t.getUnits());
      }

      // Determine counter attack results to see if I can hold it
      final Set<Unit> enemyAttackingUnits =
          new HashSet<>(enemyAttackOptions.getMax(myCapital).getMaxUnits());
      enemyAttackingUnits.addAll(enemyAttackOptions.getMax(myCapital).getMaxAmphibUnits());
      final JBGBattleResult result =
          calc.estimateDefendBattleResults(
              jbgData,
              myCapital,
              new ArrayList<>(enemyAttackingUnits),
              defenders,
              enemyAttackOptions.getMax(myCapital).getMaxBombardUnits());
      JBGLogger.trace(
          "Current capital result hasLandUnitRemaining="
              + result.isHasLandUnitRemaining()
              + ", TUVSwing="
              + result.getTuvSwing()
              + ", defenders="
              + defenders.size()
              + ", attackers="
              + enemyAttackingUnits.size());

      // Determine attack that uses the most units per value from capital and remove it
      if (result.isHasLandUnitRemaining()) {
        double maxUnitsNearCapitalPerValue = 0.0;
        Territory maxTerritory = null;
        final Set<Territory> territoriesNearCapital =
            data.getMap().getNeighbors(myCapital, Matches.territoryIsLand());
        territoriesNearCapital.add(myCapital);
        for (final Territory t : attackMap.keySet()) {
          int unitsNearCapital = 0;
          for (final Unit u : attackMap.get(t).getUnits()) {
            if (territoriesNearCapital.contains(jbgData.getUnitTerritory(u))) {
              unitsNearCapital++;
            }
          }
          final double unitsNearCapitalPerValue = unitsNearCapital / attackMap.get(t).getValue();
          JBGLogger.trace(
              t.getName() + " has unit near capital per value: " + unitsNearCapitalPerValue);
          if (unitsNearCapitalPerValue > maxUnitsNearCapitalPerValue) {
            maxUnitsNearCapitalPerValue = unitsNearCapitalPerValue;
            maxTerritory = t;
          }
        }
        if (maxTerritory != null) {
          prioritizedTerritories.remove(attackMap.get(maxTerritory));
          attackMap.get(maxTerritory).getUnits().clear();
          attackMap.get(maxTerritory).getAmphibAttackMap().clear();
          attackMap.get(maxTerritory).setBattleResult(null);
          JBGLogger.debug("Removing territory to try to hold capital: " + maxTerritory.getName());
        } else {
          break;
        }
      } else {
        JBGLogger.debug("Can hold capital: " + myCapital.getName());
        break;
      }
    }
  }

  private void checkContestedSeaTerritories() {

    final Map<Territory, JBGTerritory> attackMap =
        territoryManager.getAttackOptions().getTerritoryMap();

    for (final Territory t : jbgData.getMyUnitTerritories()) {
      if (t.isWater()
          && Matches.territoryHasEnemyUnits(player, data).test(t)
          && (attackMap.get(t) == null || attackMap.get(t).getUnits().isEmpty())) {

        // Move into random adjacent safe sea territory
        final Set<Territory> possibleMoveTerritories =
            data.getMap()
                .getNeighbors(t, JBGMatches.territoryCanMoveSeaUnitsThrough(player, data, true));
        if (!possibleMoveTerritories.isEmpty()) {
          final Territory moveToTerritory = possibleMoveTerritories.iterator().next();
          final List<Unit> mySeaUnits =
              t.getUnitCollection()
                  .getMatches(JBGMatches.unitCanBeMovedAndIsOwnedSea(player, true));
          if (attackMap.containsKey(moveToTerritory)) {
            attackMap.get(moveToTerritory).addUnits(mySeaUnits);
          } else {
            final JBGTerritory moveTerritoryData = new JBGTerritory(moveToTerritory, jbgData);
            moveTerritoryData.addUnits(mySeaUnits);
            attackMap.put(moveToTerritory, moveTerritoryData);
          }
          JBGLogger.info(t + " is a contested territory so moving subs to " + moveToTerritory);
        }
      }
    }
  }

  private void logAttackMoves(final List<JBGTerritory> prioritizedTerritories) {

    final Map<Territory, JBGTerritory> attackMap =
        territoryManager.getAttackOptions().getTerritoryMap();

    // Print prioritization
    JBGLogger.debug("Prioritized territories:");
    for (final JBGTerritory attackTerritoryData : prioritizedTerritories) {
      JBGLogger.trace(
          "  "
              + attackTerritoryData.getMaxBattleResult().getTuvSwing()
              + "  "
              + attackTerritoryData.getValue()
              + "  "
              + attackTerritoryData.getTerritory().getName());
    }

    // Print enemy territories with enemy units vs my units
    JBGLogger.debug("Territories that can be attacked:");
    int count = 0;
    for (final Territory t : attackMap.keySet()) {
      count++;
      JBGLogger.trace(count + ". ---" + t.getName());
      final Set<Unit> combinedUnits = new HashSet<>(attackMap.get(t).getMaxUnits());
      combinedUnits.addAll(attackMap.get(t).getMaxAmphibUnits());
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
      JBGLogger.trace("  --- My max bombard units ---");
      final Map<String, Integer> printBombardMap = new HashMap<>();
      for (final Unit unit : attackMap.get(t).getMaxBombardUnits()) {
        if (printBombardMap.containsKey(unit.toStringNoOwner())) {
          printBombardMap.put(
              unit.toStringNoOwner(), printBombardMap.get(unit.toStringNoOwner()) + 1);
        } else {
          printBombardMap.put(unit.toStringNoOwner(), 1);
        }
      }
      for (final String key : printBombardMap.keySet()) {
        JBGLogger.trace("    " + printBombardMap.get(key) + " " + key);
      }
      final List<Unit> units3 = attackMap.get(t).getUnits();
      JBGLogger.trace("  --- My actual units ---");
      final Map<String, Integer> printMap3 = new HashMap<>();
      for (final Unit unit : units3) {
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
      final List<Unit> units2 = attackMap.get(t).getMaxEnemyDefenders(player, data);
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
      JBGLogger.trace("  --- Enemy Counter Attack Units ---");
      final Map<String, Integer> printMap4 = new HashMap<>();
      final List<Unit> units4 = attackMap.get(t).getMaxEnemyUnits();
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
      JBGLogger.trace("  --- Enemy Counter Bombard Units ---");
      final Map<String, Integer> printMap5 = new HashMap<>();
      final Set<Unit> units5 = attackMap.get(t).getMaxEnemyBombardUnits();
      for (final Unit unit : units5) {
        if (printMap5.containsKey(unit.toStringNoOwner())) {
          printMap5.put(unit.toStringNoOwner(), printMap5.get(unit.toStringNoOwner()) + 1);
        } else {
          printMap5.put(unit.toStringNoOwner(), 1);
        }
      }
      for (final String key : printMap5.keySet()) {
        JBGLogger.trace("    " + printMap4.get(key) + " " + key);
      }
    }
  }

  private boolean canAirSafelyLandAfterAttack(final Unit unit, final Territory t) {
    final boolean isAdjacentToAlliedFactory =
        Matches.territoryHasNeighborMatching(
                data, JBGMatches.territoryHasInfraFactoryAndIsAlliedLand(player, data))
            .test(t);
    final int range = unit.getMovementLeft().intValue();
    final int distance =
        data.getMap()
            .getDistanceIgnoreEndForCondition(
                jbgData.getUnitTerritory(unit),
                t,
                JBGMatches.territoryCanMoveAirUnitsAndNoAa(player, data, true));
    final boolean usesMoreThanHalfOfRange = distance > range / 2;
    return isAdjacentToAlliedFactory || !usesMoreThanHalfOfRange;
  }
}
