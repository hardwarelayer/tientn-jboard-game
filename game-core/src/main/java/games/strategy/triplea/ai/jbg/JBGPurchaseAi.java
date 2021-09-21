package games.strategy.triplea.ai.jbg;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.ProductionRule;
import games.strategy.engine.data.RepairRule;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.Properties;
import games.strategy.triplea.UnitUtils;
import games.strategy.triplea.ai.AbstractAi;
import games.strategy.triplea.ai.jbg.data.JBGBattleResult;
import games.strategy.triplea.ai.jbg.data.JBGOtherMoveOptions;
import games.strategy.triplea.ai.jbg.data.JBGPlaceTerritory;
import games.strategy.triplea.ai.jbg.data.JBGPurchaseOption;
import games.strategy.triplea.ai.jbg.data.JBGPurchaseOptionMap;
import games.strategy.triplea.ai.jbg.data.JBGPurchaseTerritory;
import games.strategy.triplea.ai.jbg.data.JBGResourceTracker;
import games.strategy.triplea.ai.jbg.data.JBGTerritoryManager;
import games.strategy.triplea.ai.jbg.logging.JBGLogger;
import games.strategy.triplea.ai.jbg.logging.JBGMetricUtils;
import games.strategy.triplea.ai.jbg.util.JBGBattleUtils;
import games.strategy.triplea.ai.jbg.util.JBGMatches;
import games.strategy.triplea.ai.jbg.util.JBGOddsCalculator;
import games.strategy.triplea.ai.jbg.util.JBGPurchaseUtils;
import games.strategy.triplea.ai.jbg.util.JBGPurchaseValidationUtils;
import games.strategy.triplea.ai.jbg.util.JBGTerritoryValueUtils;
import games.strategy.triplea.ai.jbg.util.JBGTransportUtils;
import games.strategy.triplea.ai.jbg.util.JBGUtils;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.data.PlaceableUnits;
import games.strategy.triplea.delegate.remote.IAbstractPlaceDelegate;
import games.strategy.triplea.delegate.remote.IPurchaseDelegate;
import games.strategy.triplea.util.TuvUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.triplea.java.collections.CollectionUtils;
import org.triplea.java.collections.IntegerMap;

/** JBG purchase AI. */
class JBGPurchaseAi {

  private final JBGOddsCalculator calc;
  private final JBGData jbgData;
  private GameData data;
  private GameData startOfTurnData; // Used to count current units on map for maxBuiltPerPlayer
  private GamePlayer player;
  private JBGResourceTracker resourceTracker;
  private JBGTerritoryManager territoryManager;
  private boolean isBid = false;

  JBGPurchaseAi(final AbstractJBGAi ai) {
    this.calc = ai.getCalc();
    this.jbgData = ai.getJBGData();
  }

  void repair(
      final int initialPusRemaining,
      final IPurchaseDelegate purchaseDelegate,
      final GameData data,
      final GamePlayer player) {
    int pusRemaining = initialPusRemaining;
    JBGLogger.info("Repairing factories with PUsRemaining=" + pusRemaining);

    // Current data at the start of combat move
    this.data = data;
    this.player = player;
    final Predicate<Unit> ourFactories =
        Matches.unitIsOwnedBy(player)
            .and(Matches.unitCanProduceUnits())
            .and(Matches.unitIsInfrastructure());
    final List<Territory> rfactories =
        CollectionUtils.getMatches(
            data.getMap().getTerritories(),
            JBGMatches.territoryHasFactoryAndIsNotConqueredOwnedLand(player, data));
    if (player.getRepairFrontier() != null
        && Properties.getDamageFromBombingDoneToUnitsInsteadOfTerritories(data)) {
      JBGLogger.debug("Factories can be damaged");
      final Map<Unit, Territory> unitsThatCanProduceNeedingRepair = new HashMap<>();
      for (final Territory fixTerr : rfactories) {
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
      }
      JBGLogger.debug("Factories that need repaired: " + unitsThatCanProduceNeedingRepair);
      for (final var repairRule : player.getRepairFrontier().getRules()) {
        for (final Unit fixUnit : unitsThatCanProduceNeedingRepair.keySet()) {
          if (fixUnit == null
              || !fixUnit.getType().equals(repairRule.getResults().keySet().iterator().next())) {
            continue;
          }
          if (!Matches.territoryIsOwnedAndHasOwnedUnitMatching(
                  player, Matches.unitCanProduceUnitsAndCanBeDamaged())
              .test(unitsThatCanProduceNeedingRepair.get(fixUnit))) {
            continue;
          }
          final int diff = fixUnit.getUnitDamage();
          if (diff > 0) {
            final IntegerMap<RepairRule> repairMap = new IntegerMap<>();
            repairMap.add(repairRule, diff);
            final Map<Unit, IntegerMap<RepairRule>> repair = new HashMap<>();
            repair.put(fixUnit, repairMap);
            pusRemaining -= diff;
            JBGLogger.debug(
                "Repairing factory=" + fixUnit + ", damage=" + diff + ", repairRule=" + repairRule);
            purchaseDelegate.purchaseRepair(repair);
          }
        }
      }
    }
  }

  /**
   * Default settings for bidding: 1) Limit one bid unit in a territory or sea zone (until set in
   * all territories then 2, etc). 2) The nation placing a unit in a territory or sea zone must have
   * started with a unit in said territory or sea zone prior to placing the bid.
   */
  Map<Territory, JBGPurchaseTerritory> bid(
      final int pus, final IPurchaseDelegate purchaseDelegate, final GameData startOfTurnData) {

    // Current data fields
    data = jbgData.getData();
    this.startOfTurnData = startOfTurnData;
    player = jbgData.getPlayer();
    resourceTracker = new JBGResourceTracker(pus, data);
    territoryManager = new JBGTerritoryManager(calc, jbgData);
    isBid = true;
    final JBGPurchaseOptionMap purchaseOptions = jbgData.getPurchaseOptions();

    JBGLogger.info("Starting bid phase with resources: " + resourceTracker);
    if (!player.getUnits().isEmpty()) {
      JBGLogger.info("Starting bid phase with unplaced units=" + player.getUnits());
    }

    // Find all purchase/place territories
    final Map<Territory, JBGPurchaseTerritory> purchaseTerritories =
        JBGPurchaseUtils.findBidTerritories(jbgData, player);

    int previousNumUnits = 0;
    while (true) {

      // Determine max enemy attack units and current allied defenders
      territoryManager.populateEnemyAttackOptions(
          new ArrayList<>(), new ArrayList<>(purchaseTerritories.keySet()));
      findDefendersInPlaceTerritories(purchaseTerritories);

      // Prioritize land territories that need defended and purchase additional defenders
      final List<JBGPlaceTerritory> needToDefendLandTerritories =
          prioritizeTerritoriesToDefend(purchaseTerritories, true);
      purchaseDefenders(
          purchaseTerritories,
          needToDefendLandTerritories,
          purchaseOptions.getLandFodderOptions(),
          purchaseOptions.getLandZeroMoveOptions(),
          purchaseOptions.getAirOptions(),
          true);

      // Find strategic value for each territory
      JBGLogger.info("Find strategic value for place territories");
      final Set<Territory> territoriesToCheck = new HashSet<>();
      for (final JBGPurchaseTerritory t : purchaseTerritories.values()) {
        for (final JBGPlaceTerritory ppt : t.getCanPlaceTerritories()) {
          territoriesToCheck.add(ppt.getTerritory());
        }
      }
      final Map<Territory, Double> territoryValueMap =
          JBGTerritoryValueUtils.findTerritoryValues(
              jbgData, player, new ArrayList<>(), new ArrayList<>(), territoriesToCheck);
      for (final JBGPurchaseTerritory t : purchaseTerritories.values()) {
        for (final JBGPlaceTerritory ppt : t.getCanPlaceTerritories()) {
          ppt.setStrategicValue(territoryValueMap.get(ppt.getTerritory()));
          JBGLogger.debug(
              ppt.getTerritory() + ", strategicValue=" + territoryValueMap.get(ppt.getTerritory()));
        }
      }

      // Prioritize land place options purchase AA then land units
      final List<JBGPlaceTerritory> prioritizedLandTerritories =
          prioritizeLandTerritories(purchaseTerritories);
      purchaseAaUnits(
          purchaseTerritories, prioritizedLandTerritories, purchaseOptions.getAaOptions());
      purchaseLandUnits(purchaseTerritories, prioritizedLandTerritories, purchaseOptions);

      // Prioritize sea territories that need defended and purchase additional defenders
      final List<JBGPlaceTerritory> needToDefendSeaTerritories =
          prioritizeTerritoriesToDefend(purchaseTerritories, false);
      purchaseDefenders(
          purchaseTerritories,
          needToDefendSeaTerritories,
          purchaseOptions.getSeaDefenseOptions(),
          List.of(),
          purchaseOptions.getAirOptions(),
          false);

      // Prioritize sea place options and purchase units
      final List<JBGPlaceTerritory> prioritizedSeaTerritories =
          prioritizeSeaTerritories(purchaseTerritories);
      purchaseSeaAndAmphibUnits(purchaseTerritories, prioritizedSeaTerritories, purchaseOptions);

      // Try to use any remaining PUs on high value units
      purchaseUnitsWithRemainingProduction(
          purchaseTerritories, purchaseOptions.getLandOptions(), purchaseOptions.getAirOptions());
      upgradeUnitsWithRemainingPUs(purchaseTerritories, purchaseOptions);

      // Check if no remaining PUs or no unit built this iteration
      final int numUnits =
          purchaseTerritories.values().stream()
              .map(JBGPurchaseTerritory::getCanPlaceTerritories)
              .map(t -> t.get(0))
              .map(JBGPlaceTerritory::getPlaceUnits)
              .mapToInt(List::size)
              .sum();
      if (resourceTracker.isEmpty() || numUnits == previousNumUnits) {
        break;
      }
      previousNumUnits = numUnits;
      JBGPurchaseUtils.incrementUnitProductionForBidTerritories(purchaseTerritories);
    }

    // Determine final count of each production rule
    final IntegerMap<ProductionRule> purchaseMap =
        populateProductionRuleMap(purchaseTerritories, purchaseOptions);

    // Purchase units
    JBGMetricUtils.collectPurchaseStats(purchaseMap);
    final String error = purchaseDelegate.purchase(purchaseMap);
    if (error != null) {
      JBGLogger.warn("Purchase error: " + error);
    }

    territoryManager = null;
    return purchaseTerritories;
  }

  Map<Territory, JBGPurchaseTerritory> purchase(
      final IPurchaseDelegate purchaseDelegate, final GameData startOfTurnData) {

    // Current data fields
    data = jbgData.getData();
    this.startOfTurnData = startOfTurnData;
    player = jbgData.getPlayer();
    resourceTracker = new JBGResourceTracker(player);
    territoryManager = new JBGTerritoryManager(calc, jbgData);
    isBid = false;
    final JBGPurchaseOptionMap purchaseOptions = jbgData.getPurchaseOptions();

    JBGLogger.info("Starting purchase phase with resources: " + resourceTracker);
    if (!player.getUnits().isEmpty()) {
      JBGLogger.info("Starting purchase phase with unplaced units=" + player.getUnits());
    }

    // Find all purchase/place territories
    final Map<Territory, JBGPurchaseTerritory> purchaseTerritories =
        JBGPurchaseUtils.findPurchaseTerritories(jbgData, player);
    final Set<Territory> placeTerritories =
        new HashSet<>(
            CollectionUtils.getMatches(
                data.getMap().getTerritoriesOwnedBy(player), Matches.territoryIsLand()));
    for (final Territory t : purchaseTerritories.keySet()) {
      for (final JBGPlaceTerritory ppt : purchaseTerritories.get(t).getCanPlaceTerritories()) {
        placeTerritories.add(ppt.getTerritory());
      }
    }

    // Determine max enemy attack units and current allied defenders
    territoryManager.populateEnemyAttackOptions(
        new ArrayList<>(), new ArrayList<>(placeTerritories));
    findDefendersInPlaceTerritories(purchaseTerritories);

    // Prioritize land territories that need defended and purchase additional defenders
    final List<JBGPlaceTerritory> needToDefendLandTerritories =
        prioritizeTerritoriesToDefend(purchaseTerritories, true);
    purchaseDefenders(
        purchaseTerritories,
        needToDefendLandTerritories,
        purchaseOptions.getLandFodderOptions(),
        purchaseOptions.getLandZeroMoveOptions(),
        purchaseOptions.getAirOptions(),
        true);

    // Find strategic value for each territory
    JBGLogger.info("Find strategic value for place territories");
    final Set<Territory> territoriesToCheck = new HashSet<>();
    for (final Territory t : purchaseTerritories.keySet()) {
      for (final JBGPlaceTerritory ppt : purchaseTerritories.get(t).getCanPlaceTerritories()) {
        territoriesToCheck.add(ppt.getTerritory());
      }
    }
    final Map<Territory, Double> territoryValueMap =
        JBGTerritoryValueUtils.findTerritoryValues(
            jbgData, player, new ArrayList<>(), new ArrayList<>(), territoriesToCheck);
    for (final Territory t : purchaseTerritories.keySet()) {
      for (final JBGPlaceTerritory ppt : purchaseTerritories.get(t).getCanPlaceTerritories()) {
        ppt.setStrategicValue(territoryValueMap.get(ppt.getTerritory()));
        JBGLogger.debug(
            ppt.getTerritory() + ", strategicValue=" + territoryValueMap.get(ppt.getTerritory()));
      }
    }

    // Prioritize land place options purchase AA then land units
    final List<JBGPlaceTerritory> prioritizedLandTerritories =
        prioritizeLandTerritories(purchaseTerritories);
    purchaseAaUnits(
        purchaseTerritories, prioritizedLandTerritories, purchaseOptions.getAaOptions());
    purchaseLandUnits(purchaseTerritories, prioritizedLandTerritories, purchaseOptions);

    // Prioritize sea territories that need defended and purchase additional defenders
    final List<JBGPlaceTerritory> needToDefendSeaTerritories =
        prioritizeTerritoriesToDefend(purchaseTerritories, false);
    purchaseDefenders(
        purchaseTerritories,
        needToDefendSeaTerritories,
        purchaseOptions.getSeaDefenseOptions(),
        List.of(),
        purchaseOptions.getAirOptions(),
        false);

    // Determine whether to purchase new land factory
    final Map<Territory, JBGPurchaseTerritory> factoryPurchaseTerritories = new HashMap<>();
    purchaseFactory(
        factoryPurchaseTerritories,
        purchaseTerritories,
        prioritizedLandTerritories,
        purchaseOptions,
        false);

    // Prioritize sea place options and purchase units
    final List<JBGPlaceTerritory> prioritizedSeaTerritories =
        prioritizeSeaTerritories(purchaseTerritories);
    purchaseSeaAndAmphibUnits(purchaseTerritories, prioritizedSeaTerritories, purchaseOptions);

    // Try to use any remaining PUs on high value units
    purchaseUnitsWithRemainingProduction(
        purchaseTerritories, purchaseOptions.getLandOptions(), purchaseOptions.getAirOptions());
    upgradeUnitsWithRemainingPUs(purchaseTerritories, purchaseOptions);

    // Try to purchase land/sea factory with extra PUs
    purchaseFactory(
        factoryPurchaseTerritories,
        purchaseTerritories,
        prioritizedLandTerritories,
        purchaseOptions,
        true);

    // Add factory purchase territory to list if not empty
    if (!factoryPurchaseTerritories.isEmpty()) {
      purchaseTerritories.putAll(factoryPurchaseTerritories);
    }

    // Determine final count of each production rule
    final IntegerMap<ProductionRule> purchaseMap =
        populateProductionRuleMap(purchaseTerritories, purchaseOptions);

    // Purchase units
    JBGMetricUtils.collectPurchaseStats(purchaseMap);
    final String error = purchaseDelegate.purchase(purchaseMap);
    if (error != null) {
      JBGLogger.warn("Purchase error: " + error);
    }

    territoryManager = null;
    return purchaseTerritories;
  }

  void place(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories,
      final IAbstractPlaceDelegate placeDelegate) {
    JBGLogger.info("Starting place phase");

    data = jbgData.getData();
    player = jbgData.getPlayer();
    territoryManager = new JBGTerritoryManager(calc, jbgData);

    if (purchaseTerritories != null) {

      // Place all units calculated during purchase phase (land then sea to reduce failed
      // placements)
      for (final JBGPurchaseTerritory t : purchaseTerritories.values()) {
        for (final JBGPlaceTerritory ppt : t.getCanPlaceTerritories()) {
          if (!ppt.getTerritory().isWater()) {
            final List<Unit> unitsToPlace = new ArrayList<>();
            for (final Unit placeUnit : ppt.getPlaceUnits()) {
              for (final Unit myUnit : player.getUnitCollection()) {
                if (myUnit.getType().equals(placeUnit.getType())
                    && !unitsToPlace.contains(myUnit)) {
                  unitsToPlace.add(myUnit);
                  break;
                }
              }
            }
            doPlace(
                data.getMap().getTerritory(ppt.getTerritory().getName()),
                unitsToPlace,
                placeDelegate);
            JBGLogger.debug(ppt.getTerritory() + " placed units: " + unitsToPlace);
          }
        }
      }
      for (final JBGPurchaseTerritory t : purchaseTerritories.values()) {
        for (final JBGPlaceTerritory ppt : t.getCanPlaceTerritories()) {
          if (ppt.getTerritory().isWater()) {
            final List<Unit> unitsToPlace = new ArrayList<>();
            for (final Unit placeUnit : ppt.getPlaceUnits()) {
              for (final Unit myUnit : player.getUnitCollection()) {
                if (myUnit.getType().equals(placeUnit.getType())
                    && !unitsToPlace.contains(myUnit)) {
                  unitsToPlace.add(myUnit);
                  break;
                }
              }
            }
            doPlace(
                data.getMap().getTerritory(ppt.getTerritory().getName()),
                unitsToPlace,
                placeDelegate);
            JBGLogger.debug(ppt.getTerritory() + " placed units: " + unitsToPlace);
          }
        }
      }
    }

    // Place remaining units (currently only implemented to handle land units, ex. WW2v3 China)
    if (player.getUnits().isEmpty()) {
      return;
    }

    // Current data at the start of place
    JBGLogger.debug("Remaining units to place: " + player.getUnits());

    // Find all place territories
    final Map<Territory, JBGPurchaseTerritory> placeNonConstructionTerritories =
        JBGPurchaseUtils.findPurchaseTerritories(jbgData, player);
    final Set<Territory> placeTerritories = new HashSet<>();
    for (final Territory t : placeNonConstructionTerritories.keySet()) {
      for (final JBGPlaceTerritory ppt :
          placeNonConstructionTerritories.get(t).getCanPlaceTerritories()) {
        placeTerritories.add(ppt.getTerritory());
      }
    }

    // Determine max enemy attack units and current allied defenders
    territoryManager.populateEnemyAttackOptions(
        new ArrayList<>(), new ArrayList<>(placeTerritories));
    findDefendersInPlaceTerritories(placeNonConstructionTerritories);

    // Prioritize land territories that need defended and place additional defenders
    final List<JBGPlaceTerritory> needToDefendLandTerritories =
        prioritizeTerritoriesToDefend(placeNonConstructionTerritories, true);
    placeDefenders(placeNonConstructionTerritories, needToDefendLandTerritories, placeDelegate);

    // Prioritize sea territories that need defended and place additional defenders
    final List<JBGPlaceTerritory> needToDefendSeaTerritories =
        prioritizeTerritoriesToDefend(placeNonConstructionTerritories, false);
    placeDefenders(placeNonConstructionTerritories, needToDefendSeaTerritories, placeDelegate);

    // Find strategic value for each territory
    JBGLogger.info("Find strategic value for place territories");
    final Set<Territory> territoriesToCheck = new HashSet<>();
    for (final JBGPurchaseTerritory t : placeNonConstructionTerritories.values()) {
      for (final JBGPlaceTerritory ppt : t.getCanPlaceTerritories()) {
        territoriesToCheck.add(ppt.getTerritory());
      }
    }
    final Map<Territory, Double> territoryValueMap =
        JBGTerritoryValueUtils.findTerritoryValues(
            jbgData, player, new ArrayList<>(), new ArrayList<>(), territoriesToCheck);
    for (final JBGPurchaseTerritory t : placeNonConstructionTerritories.values()) {
      for (final JBGPlaceTerritory ppt : t.getCanPlaceTerritories()) {
        ppt.setStrategicValue(territoryValueMap.get(ppt.getTerritory()));
        JBGLogger.debug(
            ppt.getTerritory() + ", strategicValue=" + territoryValueMap.get(ppt.getTerritory()));
      }
    }

    // Prioritize place territories
    final List<JBGPlaceTerritory> prioritizedTerritories =
        prioritizeLandTerritories(placeNonConstructionTerritories);
    for (final JBGPurchaseTerritory ppt : placeNonConstructionTerritories.values()) {
      for (final JBGPlaceTerritory placeTerritory : ppt.getCanPlaceTerritories()) {
        if (!prioritizedTerritories.contains(placeTerritory)) {
          prioritizedTerritories.add(placeTerritory);
        }
      }
    }

    // Place regular then isConstruction units (placeDelegate.getPlaceableUnits doesn't handle
    // combined)
    placeUnits(prioritizedTerritories, placeDelegate, Matches.unitIsNotConstruction());
    placeUnits(prioritizedTerritories, placeDelegate, Matches.unitIsConstruction());

    territoryManager = null;
  }

  private void findDefendersInPlaceTerritories(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories) {
    JBGLogger.info("Find defenders in possible place territories");
    for (final JBGPurchaseTerritory ppt : purchaseTerritories.values()) {
      for (final JBGPlaceTerritory placeTerritory : ppt.getCanPlaceTerritories()) {
        final Territory t = placeTerritory.getTerritory();
        final List<Unit> units =
            t.getUnitCollection().getMatches(Matches.isUnitAllied(player, data));
        placeTerritory.setDefendingUnits(units);
        JBGLogger.debug(t + " has numDefenders=" + units.size());
      }
    }
  }

  private List<JBGPlaceTerritory> prioritizeTerritoriesToDefend(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories, final boolean isLand) {

    JBGLogger.info("Prioritize territories to defend with isLand=" + isLand);

    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();

    // Determine which territories need defended
    final Set<JBGPlaceTerritory> needToDefendTerritories = new HashSet<>();
    for (final JBGPurchaseTerritory ppt : purchaseTerritories.values()) {

      // Check if any of the place territories can't be held with current defenders
      for (final JBGPlaceTerritory placeTerritory : ppt.getCanPlaceTerritories()) {
        final Territory t = placeTerritory.getTerritory();
        if (enemyAttackOptions.getMax(t) == null
            || (t.isWater() && placeTerritory.getDefendingUnits().isEmpty())
            || (isLand && t.isWater())
            || (!isLand && !t.isWater())) {
          continue;
        }

        // Find current battle result
        final Set<Unit> enemyAttackingUnits =
            new HashSet<>(enemyAttackOptions.getMax(t).getMaxUnits());
        enemyAttackingUnits.addAll(enemyAttackOptions.getMax(t).getMaxAmphibUnits());
        final JBGBattleResult result =
            calc.calculateBattleResults(
                jbgData,
                t,
                new ArrayList<>(enemyAttackingUnits),
                placeTerritory.getDefendingUnits(),
                enemyAttackOptions.getMax(t).getMaxBombardUnits());
        placeTerritory.setMinBattleResult(result);
        double holdValue = 0;
        if (t.isWater()) {
          final double unitValue =
              TuvUtils.getTuv(
                  CollectionUtils.getMatches(
                      placeTerritory.getDefendingUnits(), Matches.unitIsOwnedBy(player)),
                  jbgData.getUnitValueMap());
          holdValue = unitValue / 8;
        }
        JBGLogger.trace(
            t.getName()
                + " TUVSwing="
                + result.getTuvSwing()
                + ", win%="
                + result.getWinPercentage()
                + ", hasLandUnitRemaining="
                + result.isHasLandUnitRemaining()
                + ", holdValue="
                + holdValue
                + ", enemyAttackers="
                + enemyAttackingUnits
                + ", defenders="
                + placeTerritory.getDefendingUnits());

        // If it can't currently be held then add to list
        final boolean isLandAndCanOnlyBeAttackedByAir =
            !t.isWater()
                && !enemyAttackingUnits.isEmpty()
                && enemyAttackingUnits.stream().allMatch(Matches.unitIsAir());
        if ((!t.isWater() && result.isHasLandUnitRemaining())
            || result.getTuvSwing() > holdValue
            || (t.equals(jbgData.getMyCapital())
                && !isLandAndCanOnlyBeAttackedByAir
                && result.getWinPercentage() > (100 - jbgData.getWinPercentage()))) {
          needToDefendTerritories.add(placeTerritory);
        }
      }
    }

    // Calculate value of defending territory
    for (final JBGPlaceTerritory placeTerritory : needToDefendTerritories) {
      final Territory t = placeTerritory.getTerritory();

      // Determine if it is my capital or adjacent to my capital
      int isMyCapital = 0;
      if (t.equals(jbgData.getMyCapital())) {
        isMyCapital = 1;
      }

      // Determine if it has a factory
      int isFactory = 0;
      if (JBGMatches.territoryHasInfraFactoryAndIsOwnedLand(player).test(t)) {
        isFactory = 1;
      }

      // Determine production value and if it is an enemy capital
      int production = 0;
      final TerritoryAttachment ta = TerritoryAttachment.get(t);
      if (ta != null) {
        production = ta.getProduction();
      }

      // Determine defending unit value
      double defendingUnitValue =
          TuvUtils.getTuv(placeTerritory.getDefendingUnits(), jbgData.getUnitValueMap());
      if (t.isWater()
          && placeTerritory.getDefendingUnits().stream().noneMatch(Matches.unitIsOwnedBy(player))) {
        defendingUnitValue = 0;
      }

      // Calculate defense value for prioritization
      final double territoryValue =
          (2.0 * production + 4.0 * isFactory + 0.5 * defendingUnitValue)
              * (1 + isFactory)
              * (1 + 10.0 * isMyCapital);
      placeTerritory.setDefenseValue(territoryValue);
    }

    // Remove any territories with negative defense value
    needToDefendTerritories.removeIf(ppt -> ppt.getDefenseValue() <= 0);

    // Sort territories by value
    final List<JBGPlaceTerritory> sortedTerritories = new ArrayList<>(needToDefendTerritories);
    sortedTerritories.sort(
        Comparator.comparingDouble(JBGPlaceTerritory::getDefenseValue).reversed());
    for (final JBGPlaceTerritory placeTerritory : sortedTerritories) {
      JBGLogger.debug(
          placeTerritory.toString() + " defenseValue=" + placeTerritory.getDefenseValue());
    }
    return sortedTerritories;
  }

  private void purchaseDefenders(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories,
      final List<JBGPlaceTerritory> needToDefendTerritories,
      final List<JBGPurchaseOption> defensePurchaseOptions,
      final List<JBGPurchaseOption> zeroMoveDefensePurchaseOptions,
      final List<JBGPurchaseOption> airPurchaseOptions,
      final boolean isLand) {

    if (resourceTracker.isEmpty()) {
      return;
    }
    JBGLogger.info("Purchase defenders with resources: " + resourceTracker + ", isLand=" + isLand);

    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();

    // Loop through prioritized territories and purchase defenders
    for (final JBGPlaceTerritory placeTerritory : needToDefendTerritories) {
      final Territory t = placeTerritory.getTerritory();
      JBGLogger.debug(
          "Purchasing defenders for "
              + t.getName()
              + ", enemyAttackers="
              + enemyAttackOptions.getMax(t).getMaxUnits()
              + ", amphibEnemyAttackers="
              + enemyAttackOptions.getMax(t).getMaxAmphibUnits()
              + ", defenders="
              + placeTerritory.getDefendingUnits());

      // Find local owned units
      final List<Unit> ownedLocalUnits =
          t.getUnitCollection().getMatches(Matches.unitIsOwnedBy(player));
      int unusedCarrierCapacity =
          Math.min(0, JBGTransportUtils.getUnusedCarrierCapacity(player, t, new ArrayList<>()));
      int unusedLocalCarrierCapacity =
          JBGTransportUtils.getUnusedLocalCarrierCapacity(player, t, new ArrayList<>());
      JBGLogger.trace(
          t
              + ", unusedCarrierCapacity="
              + unusedCarrierCapacity
              + ", unusedLocalCarrierCapacity="
              + unusedLocalCarrierCapacity);

      // Determine if need destroyer
      boolean needDestroyer = false;
      if (enemyAttackOptions.getMax(t).getMaxUnits().stream()
              .anyMatch(Matches.unitHasSubBattleAbilities())
          && ownedLocalUnits.stream().noneMatch(Matches.unitIsDestroyer())) {
        needDestroyer = true;
      }

      // Find all purchase territories for place territory
      final List<Unit> unitsToPlace = new ArrayList<>();
      JBGBattleResult finalResult = new JBGBattleResult();
      final List<JBGPurchaseTerritory> selectedPurchaseTerritories =
          getPurchaseTerritories(placeTerritory, purchaseTerritories);
      for (final JBGPurchaseTerritory purchaseTerritory : selectedPurchaseTerritories) {

        // Check remaining production
        int remainingUnitProduction = purchaseTerritory.getRemainingUnitProduction();
        int remainingConstructions =
            JBGPurchaseUtils.getMaxConstructions(
                purchaseTerritory.getTerritory(), data, player, zeroMoveDefensePurchaseOptions);
        JBGLogger.debug(
            purchaseTerritory.getTerritory()
                + ", remainingUnitProduction="
                + remainingUnitProduction
                + ", remainingConstructions="
                + remainingConstructions);
        if (remainingUnitProduction <= 0 && remainingConstructions <= 0) {
          continue;
        }

        // Find defenders that can be produced in this territory
        final List<JBGPurchaseOption> allDefensePurchaseOptions =
            new ArrayList<>(defensePurchaseOptions);
        allDefensePurchaseOptions.addAll(zeroMoveDefensePurchaseOptions);
        final List<JBGPurchaseOption> purchaseOptionsForTerritory =
            JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
                jbgData,
                player,
                allDefensePurchaseOptions,
                t,
                purchaseTerritory.getTerritory(),
                isBid);
        purchaseOptionsForTerritory.addAll(airPurchaseOptions);

        // Purchase necessary defenders
        while (true) {

          // Select purchase option
          JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
              player,
              startOfTurnData,
              purchaseOptionsForTerritory,
              resourceTracker,
              remainingUnitProduction,
              unitsToPlace,
              purchaseTerritories,
              remainingConstructions,
              t);
          final Map<JBGPurchaseOption, Double> defenseEfficiencies = new HashMap<>();
          for (final JBGPurchaseOption ppo : purchaseOptionsForTerritory) {
            if (isLand) {
              defenseEfficiencies.put(
                  ppo, ppo.getDefenseEfficiency(1, data, ownedLocalUnits, unitsToPlace));
            } else {
              defenseEfficiencies.put(
                  ppo,
                  ppo.getSeaDefenseEfficiency(
                      data,
                      ownedLocalUnits,
                      unitsToPlace,
                      needDestroyer,
                      unusedCarrierCapacity,
                      unusedLocalCarrierCapacity));
            }
          }
          final Optional<JBGPurchaseOption> optionalSelectedOption =
              JBGPurchaseUtils.randomizePurchaseOption(defenseEfficiencies, "Defense");
          if (optionalSelectedOption.isEmpty()) {
            break;
          }
          final JBGPurchaseOption selectedOption = optionalSelectedOption.get();
          if (selectedOption.isDestroyer()) {
            needDestroyer = false;
          }

          // Create new temp units
          resourceTracker.tempPurchase(selectedOption);
          if (selectedOption.isConstruction()) {
            remainingConstructions -= selectedOption.getQuantity();
          } else {
            remainingUnitProduction -= selectedOption.getQuantity();
          }
          unitsToPlace.addAll(
              selectedOption.getUnitType().create(selectedOption.getQuantity(), player, true));
          if (selectedOption.isCarrier() || selectedOption.isAir()) {
            unusedCarrierCapacity =
                JBGTransportUtils.getUnusedCarrierCapacity(player, t, unitsToPlace);
            unusedLocalCarrierCapacity =
                JBGTransportUtils.getUnusedLocalCarrierCapacity(player, t, unitsToPlace);
          }
          JBGLogger.trace(
              "Selected unit="
                  + selectedOption.getUnitType().getName()
                  + ", unusedCarrierCapacity="
                  + unusedCarrierCapacity
                  + ", unusedLocalCarrierCapacity="
                  + unusedLocalCarrierCapacity);

          // Find current battle result
          final Set<Unit> enemyAttackingUnits =
              new HashSet<>(enemyAttackOptions.getMax(t).getMaxUnits());
          enemyAttackingUnits.addAll(enemyAttackOptions.getMax(t).getMaxAmphibUnits());
          final List<Unit> defenders = new ArrayList<>(placeTerritory.getDefendingUnits());
          defenders.addAll(unitsToPlace);
          finalResult =
              calc.calculateBattleResults(
                  jbgData,
                  t,
                  new ArrayList<>(enemyAttackingUnits),
                  defenders,
                  enemyAttackOptions.getMax(t).getMaxBombardUnits());

          // Break if it can be held
          if ((!t.equals(jbgData.getMyCapital())
                  && !finalResult.isHasLandUnitRemaining()
                  && finalResult.getTuvSwing() <= 0)
              || (t.equals(jbgData.getMyCapital())
                  && finalResult.getWinPercentage() < (100 - jbgData.getWinPercentage())
                  && finalResult.getTuvSwing() <= 0)) {
            break;
          }
        }
      }

      // Check to see if its worth trying to defend the territory
      final boolean hasLocalSuperiority =
          JBGBattleUtils.territoryHasLocalLandSuperiority(
              jbgData, t, JBGBattleUtils.SHORT_RANGE, player, purchaseTerritories);
      if (!finalResult.isHasLandUnitRemaining()
          || (finalResult.getTuvSwing() - resourceTracker.getTempPUs(data) / 2f)
              < placeTerritory.getMinBattleResult().getTuvSwing()
          || t.equals(jbgData.getMyCapital())
          || (!t.isWater() && hasLocalSuperiority)) {
        resourceTracker.confirmTempPurchases();
        JBGLogger.trace(
            t
                + ", placedUnits="
                + unitsToPlace
                + ", TUVSwing="
                + finalResult.getTuvSwing()
                + ", hasLandUnitRemaining="
                + finalResult.isHasLandUnitRemaining()
                + ", hasLocalSuperiority="
                + hasLocalSuperiority);
        addUnitsToPlaceTerritory(placeTerritory, unitsToPlace, purchaseTerritories);
      } else {
        resourceTracker.clearTempPurchases();
        setCantHoldPlaceTerritory(placeTerritory, purchaseTerritories);
        JBGLogger.trace(
            t
                + ", unable to defend with placedUnits="
                + unitsToPlace
                + ", TUVSwing="
                + finalResult.getTuvSwing()
                + ", minTUVSwing="
                + placeTerritory.getMinBattleResult().getTuvSwing());
      }
    }
  }

  private List<JBGPlaceTerritory> prioritizeLandTerritories(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories) {

    JBGLogger.info("Prioritize land territories to place");

    // Get all land place territories
    final List<JBGPlaceTerritory> prioritizedLandTerritories = new ArrayList<>();
    for (final JBGPurchaseTerritory ppt : purchaseTerritories.values()) {
      for (final JBGPlaceTerritory placeTerritory : ppt.getCanPlaceTerritories()) {
        final Territory t = placeTerritory.getTerritory();
        if (!t.isWater() && placeTerritory.getStrategicValue() >= 1 && placeTerritory.isCanHold()) {
          final boolean hasEnemyNeighbors =
              !data.getMap()
                  .getNeighbors(t, JBGMatches.territoryIsEnemyLand(player, data))
                  .isEmpty();
          final Set<Territory> nearbyLandTerritories =
              data.getMap()
                  .getNeighbors(
                      t, 9, JBGMatches.territoryCanPotentiallyMoveLandUnits(player, data));
          final int numNearbyEnemyTerritories =
              CollectionUtils.countMatches(
                  nearbyLandTerritories,
                  Matches.isTerritoryOwnedBy(JBGUtils.getPotentialEnemyPlayers(player)));
          final boolean hasLocalLandSuperiority =
              JBGBattleUtils.territoryHasLocalLandSuperiority(
                  jbgData, t, JBGBattleUtils.SHORT_RANGE, player);
          if (hasEnemyNeighbors || numNearbyEnemyTerritories >= 3 || !hasLocalLandSuperiority) {
            prioritizedLandTerritories.add(placeTerritory);
          }
        }
      }
    }

    // Sort territories by value
    prioritizedLandTerritories.sort(
        Comparator.comparingDouble(JBGPlaceTerritory::getStrategicValue).reversed());
    for (final JBGPlaceTerritory placeTerritory : prioritizedLandTerritories) {
      JBGLogger.debug(
          placeTerritory.toString() + " strategicValue=" + placeTerritory.getStrategicValue());
    }
    return prioritizedLandTerritories;
  }

  private void purchaseAaUnits(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories,
      final List<JBGPlaceTerritory> prioritizedLandTerritories,
      final List<JBGPurchaseOption> specialPurchaseOptions) {

    if (resourceTracker.isEmpty()) {
      return;
    }
    JBGLogger.info("Purchase AA units with resources: " + resourceTracker);

    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();

    // Loop through prioritized territories and purchase AA units
    for (final JBGPlaceTerritory placeTerritory : prioritizedLandTerritories) {
      final Territory t = placeTerritory.getTerritory();
      JBGLogger.debug("Checking AA place for " + t);

      // Check if any enemy attackers
      if (enemyAttackOptions.getMax(t) == null) {
        continue;
      }

      // Check remaining production
      final int remainingUnitProduction = purchaseTerritories.get(t).getRemainingUnitProduction();
      JBGLogger.debug(t + ", remainingUnitProduction=" + remainingUnitProduction);
      if (remainingUnitProduction <= 0) {
        continue;
      }

      // Check if territory needs AA
      final boolean enemyCanBomb =
          enemyAttackOptions.getMax(t).getMaxUnits().stream()
              .anyMatch(Matches.unitIsStrategicBomber());
      final boolean territoryCanBeBombed =
          t.getUnitCollection().anyMatch(Matches.unitCanProduceUnitsAndCanBeDamaged());
      final boolean hasAaBombingDefense =
          t.getUnitCollection().anyMatch(Matches.unitIsAaForBombingThisUnitOnly());
      JBGLogger.debug(
          t
              + ", enemyCanBomb="
              + enemyCanBomb
              + ", territoryCanBeBombed="
              + territoryCanBeBombed
              + ", hasAABombingDefense="
              + hasAaBombingDefense);
      if (!enemyCanBomb || !territoryCanBeBombed || hasAaBombingDefense) {
        continue;
      }

      // Remove options that cost too much PUs or production
      final List<JBGPurchaseOption> purchaseOptionsForTerritory =
          JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
              jbgData, player, specialPurchaseOptions, t, isBid);
      JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
          player,
          startOfTurnData,
          purchaseOptionsForTerritory,
          resourceTracker,
          remainingUnitProduction,
          new ArrayList<>(),
          purchaseTerritories);
      if (purchaseOptionsForTerritory.isEmpty()) {
        continue;
      }

      // Determine most cost efficient units that can be produced in this territory
      JBGPurchaseOption bestAaOption = null;
      int minCost = Integer.MAX_VALUE;
      for (final JBGPurchaseOption ppo : purchaseOptionsForTerritory) {
        final boolean isAaForBombing =
            Matches.unitTypeIsAaForBombingThisUnitOnly().test(ppo.getUnitType());
        if (isAaForBombing
            && ppo.getCost() < minCost
            && !Matches.unitTypeConsumesUnitsOnCreation().test(ppo.getUnitType())) {
          bestAaOption = ppo;
          minCost = ppo.getCost();
        }
      }

      // Check if there aren't any available units
      if (bestAaOption == null) {
        continue;
      }
      JBGLogger.trace("Best AA unit: " + bestAaOption.getUnitType().getName());

      // Create new temp units
      resourceTracker.purchase(bestAaOption);
      final List<Unit> unitsToPlace =
          bestAaOption.getUnitType().create(bestAaOption.getQuantity(), player, true);
      placeTerritory.getPlaceUnits().addAll(unitsToPlace);
      JBGLogger.trace(t + ", placedUnits=" + unitsToPlace);
    }
  }

  private void purchaseLandUnits(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories,
      final List<JBGPlaceTerritory> prioritizedLandTerritories,
      final JBGPurchaseOptionMap purchaseOptions) {

    final List<Unit> unplacedUnits = player.getUnitCollection().getMatches(Matches.unitIsNotSea());
    if (resourceTracker.isEmpty() && unplacedUnits.isEmpty()) {
      return;
    }
    JBGLogger.info("Purchase land units with resources: " + resourceTracker);
    if (!unplacedUnits.isEmpty()) {
      JBGLogger.info("Purchase land units with unplaced units=" + unplacedUnits);
    }

    // Loop through prioritized territories and purchase land units
    final Set<Territory> territoriesToCheck = new HashSet<>();
    for (final JBGPlaceTerritory placeTerritory : prioritizedLandTerritories) {
      final Set<Territory> landTerritories =
          data.getMap()
              .getNeighbors(
                  placeTerritory.getTerritory(),
                  9,
                  JBGMatches.territoryCanPotentiallyMoveLandUnits(player, data));
      final List<Territory> enemyLandTerritories =
          CollectionUtils.getMatches(
              landTerritories, Matches.isTerritoryOwnedBy(JBGUtils.getEnemyPlayers(player)));
      territoriesToCheck.addAll(enemyLandTerritories);
    }
    final Map<Territory, Double> territoryValueMap =
        JBGTerritoryValueUtils.findTerritoryValues(
            jbgData, player, new ArrayList<>(), new ArrayList<>(), territoriesToCheck);
    for (final JBGPlaceTerritory placeTerritory : prioritizedLandTerritories) {
      final Territory t = placeTerritory.getTerritory();
      JBGLogger.debug("Checking land place for " + t.getName());

      // Check remaining production
      int remainingUnitProduction = purchaseTerritories.get(t).getRemainingUnitProduction();
      JBGLogger.debug(t + ", remainingUnitProduction=" + remainingUnitProduction);
      if (remainingUnitProduction <= 0) {
        continue;
      }

      // Determine most cost efficient units that can be produced in this territory
      final List<JBGPurchaseOption> landFodderOptions =
          JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
              jbgData, player, purchaseOptions.getLandFodderOptions(), t, isBid);
      final List<JBGPurchaseOption> landAttackOptions =
          JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
              jbgData, player, purchaseOptions.getLandAttackOptions(), t, isBid);
      final List<JBGPurchaseOption> landDefenseOptions =
          JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
              jbgData, player, purchaseOptions.getLandDefenseOptions(), t, isBid);

      // Determine enemy distance and locally owned units
      int enemyDistance =
          JBGUtils.getClosestEnemyOrNeutralLandTerritoryDistance(
              data, player, t, territoryValueMap);
      if (enemyDistance <= 0) {
        enemyDistance = 10;
      }
      final int fodderPercent = 80 - enemyDistance * 5;
      JBGLogger.debug(t + ", enemyDistance=" + enemyDistance + ", fodderPercent=" + fodderPercent);
      final Set<Territory> neighbors =
          data.getMap()
              .getNeighbors(t, 2, JBGMatches.territoryCanMoveLandUnits(player, data, false));
      neighbors.add(t);
      final List<Unit> ownedLocalUnits = new ArrayList<>();
      for (final Territory neighbor : neighbors) {
        ownedLocalUnits.addAll(
            neighbor.getUnitCollection().getMatches(Matches.unitIsOwnedBy(player)));
      }

      // Check for unplaced units
      final List<Unit> unitsToPlace = new ArrayList<>();
      for (final Iterator<Unit> it = unplacedUnits.iterator(); it.hasNext(); ) {
        final Unit u = it.next();
        if (remainingUnitProduction > 0
            && JBGPurchaseValidationUtils.canUnitsBePlaced(jbgData, List.of(u), player, t, isBid)) {
          remainingUnitProduction--;
          unitsToPlace.add(u);
          it.remove();
          JBGLogger.trace("Selected unplaced unit=" + u);
        }
      }

      // Purchase as many units as possible
      int addedFodderUnits = 0;
      double attackAndDefenseDifference = 0;
      boolean selectFodderUnit = true;
      while (true) {

        // Remove options that cost too much PUs or production
        JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
            player,
            startOfTurnData,
            landFodderOptions,
            resourceTracker,
            remainingUnitProduction,
            unitsToPlace,
            purchaseTerritories);
        JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
            player,
            startOfTurnData,
            landAttackOptions,
            resourceTracker,
            remainingUnitProduction,
            unitsToPlace,
            purchaseTerritories);
        JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
            player,
            startOfTurnData,
            landDefenseOptions,
            resourceTracker,
            remainingUnitProduction,
            unitsToPlace,
            purchaseTerritories);

        // Select purchase option
        Optional<JBGPurchaseOption> optionalSelectedOption = Optional.empty();
        if (!selectFodderUnit && attackAndDefenseDifference > 0 && !landDefenseOptions.isEmpty()) {
          final Map<JBGPurchaseOption, Double> defenseEfficiencies = new HashMap<>();
          for (final JBGPurchaseOption ppo : landDefenseOptions) {
            defenseEfficiencies.put(
                ppo, ppo.getDefenseEfficiency(enemyDistance, data, ownedLocalUnits, unitsToPlace));
          }
          optionalSelectedOption =
              JBGPurchaseUtils.randomizePurchaseOption(defenseEfficiencies, "Land Defense");
        } else if (!selectFodderUnit && !landAttackOptions.isEmpty()) {
          final Map<JBGPurchaseOption, Double> attackEfficiencies = new HashMap<>();
          for (final JBGPurchaseOption ppo : landAttackOptions) {
            attackEfficiencies.put(
                ppo, ppo.getAttackEfficiency(enemyDistance, data, ownedLocalUnits, unitsToPlace));
          }
          optionalSelectedOption =
              JBGPurchaseUtils.randomizePurchaseOption(attackEfficiencies, "Land Attack");
        } else if (!landFodderOptions.isEmpty()) {
          final Map<JBGPurchaseOption, Double> fodderEfficiencies = new HashMap<>();
          for (final JBGPurchaseOption ppo : landFodderOptions) {
            fodderEfficiencies.put(
                ppo, ppo.getFodderEfficiency(enemyDistance, data, ownedLocalUnits, unitsToPlace));
          }
          optionalSelectedOption =
              JBGPurchaseUtils.randomizePurchaseOption(fodderEfficiencies, "Land Fodder");
          if (optionalSelectedOption.isPresent()) {
            addedFodderUnits += optionalSelectedOption.get().getQuantity();
          }
        }
        if (optionalSelectedOption.isEmpty()) {
          break;
        }
        final JBGPurchaseOption selectedOption = optionalSelectedOption.get();

        // Create new temp units
        resourceTracker.purchase(selectedOption);
        remainingUnitProduction -= selectedOption.getQuantity();
        unitsToPlace.addAll(
            selectedOption.getUnitType().create(selectedOption.getQuantity(), player, true));
        attackAndDefenseDifference += (selectedOption.getAttack() - selectedOption.getDefense());
        selectFodderUnit = ((double) addedFodderUnits / unitsToPlace.size() * 100) <= fodderPercent;
        JBGLogger.trace("Selected unit=" + selectedOption.getUnitType().getName());
      }

      // Add units to place territory
      placeTerritory.getPlaceUnits().addAll(unitsToPlace);
      JBGLogger.debug(t + ", placedUnits=" + unitsToPlace);
    }
  }

  private void purchaseFactory(
      final Map<Territory, JBGPurchaseTerritory> factoryPurchaseTerritories,
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories,
      final List<JBGPlaceTerritory> prioritizedLandTerritories,
      final JBGPurchaseOptionMap purchaseOptions,
      final boolean hasExtraPUs) {

    if (resourceTracker.isEmpty()) {
      return;
    }
    JBGLogger.info(
        "Purchase factory with resources: " + resourceTracker + ", hasExtraPUs=" + hasExtraPUs);

    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();

    // Only try to purchase a factory if all production was used in prioritized land territories
    for (final JBGPlaceTerritory placeTerritory : prioritizedLandTerritories) {
      for (final Territory t : purchaseTerritories.keySet()) {
        if (placeTerritory.getTerritory().equals(t)
            && purchaseTerritories.get(t).getRemainingUnitProduction() > 0) {
          JBGLogger.debug("Not purchasing a factory since remaining land production in " + t);
          return;
        }
      }
    }

    // Find all owned land territories that weren't conquered and don't already have a factory
    final List<Territory> possibleFactoryTerritories =
        CollectionUtils.getMatches(
            data.getMap().getTerritories(),
            JBGMatches.territoryHasNoInfraFactoryAndIsNotConqueredOwnedLand(player, data));
    possibleFactoryTerritories.removeAll(factoryPurchaseTerritories.keySet());
    final Set<Territory> purchaseFactoryTerritories = new HashSet<>();
    final List<Territory> territoriesThatCantBeHeld = new ArrayList<>();
    for (final Territory t : possibleFactoryTerritories) {

      // Only consider territories with production of at least 3 unless there are still remaining
      // PUs
      final int production = TerritoryAttachment.get(t).getProduction();
      if ((production < 3 && !hasExtraPUs) || production < 2) {
        continue;
      }

      // Check if no enemy attackers and that it wasn't conquered this turn
      if (enemyAttackOptions.getMax(t) == null) {
        purchaseFactoryTerritories.add(t);
        JBGLogger.trace("Possible factory since no enemy attackers: " + t.getName());
      } else {

        // Find current battle result
        final List<Unit> defenders =
            t.getUnitCollection().getMatches(Matches.isUnitAllied(player, data));
        final Set<Unit> enemyAttackingUnits =
            new HashSet<>(enemyAttackOptions.getMax(t).getMaxUnits());
        enemyAttackingUnits.addAll(enemyAttackOptions.getMax(t).getMaxAmphibUnits());
        final JBGBattleResult result =
            calc.estimateDefendBattleResults(
                jbgData,
                t,
                new ArrayList<>(enemyAttackingUnits),
                defenders,
                enemyAttackOptions.getMax(t).getMaxBombardUnits());

        // Check if it can't be held or if it can then that it wasn't conquered this turn
        if (result.isHasLandUnitRemaining() || result.getTuvSwing() > 0) {
          territoriesThatCantBeHeld.add(t);
          JBGLogger.trace(
              "Can't hold territory: "
                  + t.getName()
                  + ", hasLandUnitRemaining="
                  + result.isHasLandUnitRemaining()
                  + ", TUVSwing="
                  + result.getTuvSwing()
                  + ", enemyAttackers="
                  + enemyAttackingUnits.size()
                  + ", myDefenders="
                  + defenders.size());
        } else {
          purchaseFactoryTerritories.add(t);
          JBGLogger.trace(
              "Possible factory: "
                  + t.getName()
                  + ", hasLandUnitRemaining="
                  + result.isHasLandUnitRemaining()
                  + ", TUVSwing="
                  + result.getTuvSwing()
                  + ", enemyAttackers="
                  + enemyAttackingUnits.size()
                  + ", myDefenders="
                  + defenders.size());
        }
      }
    }
    JBGLogger.debug("Possible factory territories: " + purchaseFactoryTerritories);

    // Remove any territories that don't have local land superiority
    if (!hasExtraPUs) {
      purchaseFactoryTerritories.removeIf(
          t ->
              !JBGBattleUtils.territoryHasLocalLandSuperiority(
                  jbgData, t, JBGBattleUtils.MEDIUM_RANGE, player, purchaseTerritories));
      JBGLogger.debug(
          "Possible factory territories that have land superiority: " + purchaseFactoryTerritories);
    }

    // Find strategic value for each territory
    final Map<Territory, Double> territoryValueMap =
        JBGTerritoryValueUtils.findTerritoryValues(
            jbgData,
            player,
            territoriesThatCantBeHeld,
            new ArrayList<>(),
            purchaseFactoryTerritories);
    double maxValue = 0.0;
    Territory maxTerritory = null;
    for (final Territory t : purchaseFactoryTerritories) {
      final int production = TerritoryAttachment.get(t).getProduction();
      final double value = territoryValueMap.get(t) * production + 0.1 * production;
      final boolean isAdjacentToSea =
          Matches.territoryHasNeighborMatching(data, Matches.territoryIsWater()).test(t);
      final Set<Territory> nearbyLandTerritories =
          data.getMap()
              .getNeighbors(t, 9, JBGMatches.territoryCanMoveLandUnits(player, data, false));
      final int numNearbyEnemyTerritories =
          CollectionUtils.countMatches(
              nearbyLandTerritories, Matches.isTerritoryEnemy(player, data));
      JBGLogger.trace(
          t
              + ", strategic value="
              + territoryValueMap.get(t)
              + ", value="
              + value
              + ", numNearbyEnemyTerritories="
              + numNearbyEnemyTerritories);
      if (value > maxValue
          && ((numNearbyEnemyTerritories >= 4 && territoryValueMap.get(t) >= 1)
              || (isAdjacentToSea && hasExtraPUs))) {
        maxValue = value;
        maxTerritory = t;
      }
    }
    JBGLogger.debug("Try to purchase factory for territory: " + maxTerritory);

    // Determine whether to purchase factory
    if (maxTerritory != null) {

      // Find most expensive placed land unit to consider removing for a factory
      JBGPurchaseOption maxPlacedOption = null;
      JBGPlaceTerritory maxPlacedTerritory = null;
      Unit maxPlacedUnit = null;
      for (final JBGPlaceTerritory placeTerritory : prioritizedLandTerritories) {
        for (final Unit u : placeTerritory.getPlaceUnits()) {
          for (final JBGPurchaseOption ppo : purchaseOptions.getLandOptions()) {
            if (u.getType().equals(ppo.getUnitType())
                && ppo.getQuantity() == 1
                && (maxPlacedOption == null || ppo.getCost() >= maxPlacedOption.getCost())) {
              maxPlacedOption = ppo;
              maxPlacedTerritory = placeTerritory;
              maxPlacedUnit = u;
            }
          }
        }
      }

      // Determine units that can be produced in this territory
      final List<JBGPurchaseOption> purchaseOptionsForTerritory =
          JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
              jbgData, player, purchaseOptions.getFactoryOptions(), maxTerritory, isBid);
      resourceTracker.removeTempPurchase(maxPlacedOption);
      JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
          player,
          startOfTurnData,
          purchaseOptionsForTerritory,
          resourceTracker,
          0,
          new ArrayList<>(),
          purchaseTerritories,
          1,
          maxTerritory);
      resourceTracker.clearTempPurchases();

      // Determine most expensive factory option (currently doesn't buy mobile factories)
      JBGPurchaseOption bestFactoryOption = null;
      double maxFactoryEfficiency = 0;
      for (final JBGPurchaseOption ppo : purchaseOptionsForTerritory) {
        if (ppo.getMovement() == 0 && ppo.getCost() > maxFactoryEfficiency) {
          bestFactoryOption = ppo;
          maxFactoryEfficiency = ppo.getCost();
        }
      }

      // Check if there are enough PUs to buy a factory
      if (bestFactoryOption != null) {
        JBGLogger.debug("Best factory unit: " + bestFactoryOption.getUnitType().getName());
        final JBGPurchaseTerritory factoryPurchaseTerritory =
            new JBGPurchaseTerritory(maxTerritory, data, player, 0);
        factoryPurchaseTerritories.put(maxTerritory, factoryPurchaseTerritory);
        for (final JBGPlaceTerritory ppt : factoryPurchaseTerritory.getCanPlaceTerritories()) {
          if (ppt.getTerritory().equals(maxTerritory)) {
            final List<Unit> factory =
                bestFactoryOption
                    .getUnitType()
                    .create(bestFactoryOption.getQuantity(), player, true);
            ppt.getPlaceUnits().addAll(factory);
            if (resourceTracker.hasEnough(bestFactoryOption)) {
              resourceTracker.purchase(bestFactoryOption);
              JBGLogger.debug(maxTerritory + ", placedFactory=" + factory);
            } else {
              resourceTracker.purchase(bestFactoryOption);
              resourceTracker.removePurchase(maxPlacedOption);
              if (maxPlacedTerritory != null) {
                maxPlacedTerritory.getPlaceUnits().remove(maxPlacedUnit);
              }
              JBGLogger.debug(
                  maxTerritory + ", placedFactory=" + factory + ", removedUnit=" + maxPlacedUnit);
            }
          }
        }
      }
    }
  }

  private List<JBGPlaceTerritory> prioritizeSeaTerritories(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories) {

    JBGLogger.info("Prioritize sea territories");

    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();

    // Determine which sea territories can be placed in
    final Set<JBGPlaceTerritory> seaPlaceTerritories = new HashSet<>();
    for (final JBGPurchaseTerritory ppt : purchaseTerritories.values()) {
      for (final JBGPlaceTerritory placeTerritory : ppt.getCanPlaceTerritories()) {
        final Territory t = placeTerritory.getTerritory();
        if (t.isWater() && placeTerritory.getStrategicValue() > 0 && placeTerritory.isCanHold()) {
          seaPlaceTerritories.add(placeTerritory);
        }
      }
    }

    // Calculate value of territory
    JBGLogger.debug("Determine sea place value:");
    for (final JBGPlaceTerritory placeTerritory : seaPlaceTerritories) {
      final Territory t = placeTerritory.getTerritory();

      // Find number of local naval units
      final List<Unit> units = new ArrayList<>(placeTerritory.getDefendingUnits());
      units.addAll(JBGPurchaseUtils.getPlaceUnits(t, purchaseTerritories));
      final List<Unit> myUnits = CollectionUtils.getMatches(units, Matches.unitIsOwnedBy(player));
      final int numMyTransports = CollectionUtils.countMatches(myUnits, Matches.unitIsTransport());
      final int numSeaDefenders = CollectionUtils.countMatches(units, Matches.unitIsNotTransport());

      // Determine needed defense strength
      int needDefenders = 0;
      if (enemyAttackOptions.getMax(t) != null) {
        final double strengthDifference =
            JBGBattleUtils.estimateStrengthDifference(
                jbgData, t, enemyAttackOptions.getMax(t).getMaxUnits(), units);
        if (strengthDifference > 50) {
          needDefenders = 1;
        }
      }
      final boolean hasLocalNavalSuperiority =
          JBGBattleUtils.territoryHasLocalNavalSuperiority(jbgData, t, player, Map.of(), List.of());
      if (!hasLocalNavalSuperiority) {
        needDefenders = 1;
      }

      // Calculate sea value for prioritization
      final double territoryValue =
          placeTerritory.getStrategicValue()
              * (1 + numMyTransports + 0.1 * numSeaDefenders)
              / (1 + 3.0 * needDefenders);
      JBGLogger.debug(
          t
              + ", value="
              + territoryValue
              + ", strategicValue="
              + placeTerritory.getStrategicValue()
              + ", numMyTransports="
              + numMyTransports
              + ", numSeaDefenders="
              + numSeaDefenders
              + ", needDefenders="
              + needDefenders);
      placeTerritory.setStrategicValue(territoryValue);
    }

    // Sort territories by value
    final List<JBGPlaceTerritory> sortedTerritories = new ArrayList<>(seaPlaceTerritories);
    sortedTerritories.sort(
        Comparator.comparingDouble(JBGPlaceTerritory::getStrategicValue).reversed());
    JBGLogger.debug("Sorted sea territories:");
    for (final JBGPlaceTerritory placeTerritory : sortedTerritories) {
      JBGLogger.debug(placeTerritory.toString() + " value=" + placeTerritory.getStrategicValue());
    }
    return sortedTerritories;
  }

  private void purchaseSeaAndAmphibUnits(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories,
      final List<JBGPlaceTerritory> prioritizedSeaTerritories,
      final JBGPurchaseOptionMap purchaseOptions) {

    if (resourceTracker.isEmpty()) {
      return;
    }
    JBGLogger.info("Purchase sea and amphib units with resources: " + resourceTracker);

    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();

    // Loop through prioritized territories and purchase sea units
    for (final JBGPlaceTerritory placeTerritory : prioritizedSeaTerritories) {
      final Territory t = placeTerritory.getTerritory();
      JBGLogger.debug("Checking sea place for " + t.getName());

      // Find all purchase territories for place territory
      final List<JBGPurchaseTerritory> selectedPurchaseTerritories =
          getPurchaseTerritories(placeTerritory, purchaseTerritories);

      // Find local owned units
      final Set<Territory> neighbors =
          data.getMap()
              .getNeighbors(t, 2, JBGMatches.territoryCanMoveSeaUnits(player, data, false));
      neighbors.add(t);
      final List<Unit> ownedLocalUnits = new ArrayList<>();
      for (final Territory neighbor : neighbors) {
        ownedLocalUnits.addAll(
            neighbor.getUnitCollection().getMatches(Matches.unitIsOwnedBy(player)));
      }
      int unusedCarrierCapacity =
          Math.min(0, JBGTransportUtils.getUnusedCarrierCapacity(player, t, new ArrayList<>()));
      int unusedLocalCarrierCapacity =
          JBGTransportUtils.getUnusedLocalCarrierCapacity(player, t, new ArrayList<>());
      JBGLogger.trace(
          t
              + ", unusedCarrierCapacity="
              + unusedCarrierCapacity
              + ", unusedLocalCarrierCapacity="
              + unusedLocalCarrierCapacity);

      // If any enemy attackers then purchase sea defenders until it can be held
      boolean needDestroyer = false;
      if (enemyAttackOptions.getMax(t) != null) {

        // Determine if need destroyer
        if (enemyAttackOptions.getMax(t).getMaxUnits().stream()
                .anyMatch(Matches.unitHasSubBattleAbilities())
            && t.getUnitCollection().getMatches(Matches.unitIsOwnedBy(player)).stream()
                .noneMatch(Matches.unitIsDestroyer())) {
          needDestroyer = true;
        }
        JBGLogger.trace(
            t
                + ", needDestroyer="
                + needDestroyer
                + ", checking defense since has enemy attackers: "
                + enemyAttackOptions.getMax(t).getMaxUnits());
        final List<Unit> initialDefendingUnits =
            new ArrayList<>(placeTerritory.getDefendingUnits());
        initialDefendingUnits.addAll(JBGPurchaseUtils.getPlaceUnits(t, purchaseTerritories));
        JBGBattleResult result =
            calc.calculateBattleResults(
                jbgData,
                t,
                enemyAttackOptions.getMax(t).getMaxUnits(),
                initialDefendingUnits,
                enemyAttackOptions.getMax(t).getMaxBombardUnits());
        boolean hasOnlyRetreatingSubs =
            Properties.getSubRetreatBeforeBattle(data)
                && !initialDefendingUnits.isEmpty()
                && initialDefendingUnits.stream().allMatch(Matches.unitCanEvade())
                && enemyAttackOptions.getMax(t).getMaxUnits().stream()
                    .noneMatch(Matches.unitIsDestroyer());
        final List<Unit> unitsToPlace = new ArrayList<>();
        for (final JBGPurchaseTerritory purchaseTerritory : selectedPurchaseTerritories) {

          // Check remaining production
          int remainingUnitProduction = purchaseTerritory.getRemainingUnitProduction();
          JBGLogger.trace(
              t
                  + ", purchaseTerritory="
                  + purchaseTerritory.getTerritory()
                  + ", remainingUnitProduction="
                  + remainingUnitProduction);
          if (remainingUnitProduction <= 0) {
            continue;
          }

          // Determine sea and transport units that can be produced in this territory
          final List<JBGPurchaseOption> seaPurchaseOptionsForTerritory =
              JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
                  jbgData,
                  player,
                  purchaseOptions.getSeaDefenseOptions(),
                  t,
                  purchaseTerritory.getTerritory(),
                  isBid);
          seaPurchaseOptionsForTerritory.addAll(purchaseOptions.getAirOptions());

          // Purchase enough sea defenders to hold territory
          while (true) {

            // If it can be held then break
            if (!hasOnlyRetreatingSubs
                && (result.getTuvSwing() < -1
                    || result.getWinPercentage() < (100.0 - jbgData.getWinPercentage()))) {
              break;
            }

            // Select purchase option
            JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
                player,
                startOfTurnData,
                seaPurchaseOptionsForTerritory,
                resourceTracker,
                remainingUnitProduction,
                unitsToPlace,
                purchaseTerritories);
            final Map<JBGPurchaseOption, Double> defenseEfficiencies = new HashMap<>();
            for (final JBGPurchaseOption ppo : seaPurchaseOptionsForTerritory) {
              defenseEfficiencies.put(
                  ppo,
                  ppo.getSeaDefenseEfficiency(
                      data,
                      ownedLocalUnits,
                      unitsToPlace,
                      needDestroyer,
                      unusedCarrierCapacity,
                      unusedLocalCarrierCapacity));
            }
            final Optional<JBGPurchaseOption> optionalSelectedOption =
                JBGPurchaseUtils.randomizePurchaseOption(defenseEfficiencies, "Sea Defense");
            if (optionalSelectedOption.isEmpty()) {
              break;
            }
            final JBGPurchaseOption selectedOption = optionalSelectedOption.get();
            if (selectedOption.isDestroyer()) {
              needDestroyer = false;
            }

            // Create new temp defenders
            resourceTracker.tempPurchase(selectedOption);
            remainingUnitProduction -= selectedOption.getQuantity();
            unitsToPlace.addAll(
                selectedOption.getUnitType().create(selectedOption.getQuantity(), player, true));
            if (selectedOption.isCarrier() || selectedOption.isAir()) {
              unusedCarrierCapacity =
                  JBGTransportUtils.getUnusedCarrierCapacity(player, t, unitsToPlace);
              unusedLocalCarrierCapacity =
                  JBGTransportUtils.getUnusedLocalCarrierCapacity(player, t, unitsToPlace);
            }
            JBGLogger.trace(
                t
                    + ", added sea defender for defense: "
                    + selectedOption.getUnitType().getName()
                    + ", TUVSwing="
                    + result.getTuvSwing()
                    + ", win%="
                    + result.getWinPercentage()
                    + ", unusedCarrierCapacity="
                    + unusedCarrierCapacity
                    + ", unusedLocalCarrierCapacity="
                    + unusedLocalCarrierCapacity);

            // Find current battle result
            final List<Unit> defendingUnits = new ArrayList<>(placeTerritory.getDefendingUnits());
            defendingUnits.addAll(JBGPurchaseUtils.getPlaceUnits(t, purchaseTerritories));
            defendingUnits.addAll(unitsToPlace);
            result =
                calc.estimateDefendBattleResults(
                    jbgData,
                    t,
                    enemyAttackOptions.getMax(t).getMaxUnits(),
                    defendingUnits,
                    enemyAttackOptions.getMax(t).getMaxBombardUnits());
            hasOnlyRetreatingSubs =
                Properties.getSubRetreatBeforeBattle(data)
                    && !defendingUnits.isEmpty()
                    && defendingUnits.stream().allMatch(Matches.unitCanEvade())
                    && enemyAttackOptions.getMax(t).getMaxUnits().stream()
                        .noneMatch(Matches.unitIsDestroyer());
          }
        }

        // Check to see if its worth trying to defend the territory
        if (result.getTuvSwing() < 0
            || result.getWinPercentage() < (100.0 - jbgData.getWinPercentage())) {
          resourceTracker.confirmTempPurchases();
          JBGLogger.trace(
              t
                  + ", placedUnits="
                  + unitsToPlace
                  + ", TUVSwing="
                  + result.getTuvSwing()
                  + ", win%="
                  + result.getWinPercentage());
          addUnitsToPlaceTerritory(placeTerritory, unitsToPlace, purchaseTerritories);
        } else {
          resourceTracker.clearTempPurchases();
          setCantHoldPlaceTerritory(placeTerritory, purchaseTerritories);
          JBGLogger.trace(
              t
                  + ", can't defend TUVSwing="
                  + result.getTuvSwing()
                  + ", win%="
                  + result.getWinPercentage()
                  + ", tried to placeDefenders="
                  + unitsToPlace
                  + ", enemyAttackers="
                  + enemyAttackOptions.getMax(t).getMaxUnits());
          continue;
        }
      }

      // TODO: update to use JBGBattleUtils method
      // Check to see if local naval superiority
      int landDistance = JBGUtils.getClosestEnemyLandTerritoryDistanceOverWater(data, player, t);
      if (landDistance <= 0) {
        landDistance = 10;
      }
      final int enemyDistance = Math.max(3, (landDistance + 1));
      final Set<Territory> nearbyTerritories =
          data.getMap()
              .getNeighbors(
                  t, enemyDistance, JBGMatches.territoryCanMoveAirUnits(player, data, false));
      final List<Territory> nearbyLandTerritories =
          CollectionUtils.getMatches(nearbyTerritories, Matches.territoryIsLand());
      final Set<Territory> nearbyEnemySeaTerritories =
          data.getMap().getNeighbors(t, enemyDistance, Matches.territoryIsWater());
      nearbyEnemySeaTerritories.add(t);
      final int alliedDistance = (enemyDistance + 1) / 2;
      final Set<Territory> nearbyAlliedSeaTerritories =
          data.getMap().getNeighbors(t, alliedDistance, Matches.territoryIsWater());
      nearbyAlliedSeaTerritories.add(t);
      final List<Unit> enemyUnitsInLandTerritories = new ArrayList<>();
      for (final Territory nearbyLandTerritory : nearbyLandTerritories) {
        enemyUnitsInLandTerritories.addAll(
            nearbyLandTerritory
                .getUnitCollection()
                .getMatches(JBGMatches.unitIsEnemyAir(player, data)));
      }
      final List<Unit> enemyUnitsInSeaTerritories = new ArrayList<>();
      for (final Territory nearbySeaTerritory : nearbyEnemySeaTerritories) {
        final List<Unit> enemySeaUnits =
            nearbySeaTerritory
                .getUnitCollection()
                .getMatches(JBGMatches.unitIsEnemyNotLand(player, data));
        if (enemySeaUnits.isEmpty()) {
          continue;
        }
        final Route route =
            data.getMap()
                .getRouteForUnits(
                    t,
                    nearbySeaTerritory,
                    Matches.territoryIsWater(),
                    enemySeaUnits,
                    enemySeaUnits.get(0).getOwner());
        if (route == null) {
          continue;
        }
        final int routeLength = route.numberOfSteps();
        if (routeLength <= enemyDistance) {
          enemyUnitsInSeaTerritories.addAll(enemySeaUnits);
        }
      }
      final List<Unit> myUnitsInSeaTerritories = new ArrayList<>();
      for (final Territory nearbySeaTerritory : nearbyAlliedSeaTerritories) {
        myUnitsInSeaTerritories.addAll(
            nearbySeaTerritory
                .getUnitCollection()
                .getMatches(JBGMatches.unitIsOwnedNotLand(player)));
        myUnitsInSeaTerritories.addAll(
            JBGPurchaseUtils.getPlaceUnits(nearbySeaTerritory, purchaseTerritories));
      }

      // Check if destroyer is needed
      final int numEnemySubs =
          CollectionUtils.countMatches(
              enemyUnitsInSeaTerritories, Matches.unitHasSubBattleAbilities());
      final int numMyDestroyers =
          CollectionUtils.countMatches(myUnitsInSeaTerritories, Matches.unitIsDestroyer());
      if (numEnemySubs > 2 * numMyDestroyers) {
        needDestroyer = true;
      }
      JBGLogger.trace(
          t
              + ", enemyDistance="
              + enemyDistance
              + ", alliedDistance="
              + alliedDistance
              + ", enemyAirUnits="
              + enemyUnitsInLandTerritories
              + ", enemySeaUnits="
              + enemyUnitsInSeaTerritories
              + ", mySeaUnits="
              + myUnitsInSeaTerritories
              + ", needDestroyer="
              + needDestroyer);

      // Purchase naval defenders until I have local naval superiority
      final List<Unit> unitsToPlace = new ArrayList<>();
      for (final JBGPurchaseTerritory purchaseTerritory : selectedPurchaseTerritories) {

        // Check remaining production
        int remainingUnitProduction = purchaseTerritory.getRemainingUnitProduction();
        JBGLogger.trace(
            t
                + ", purchaseTerritory="
                + purchaseTerritory.getTerritory()
                + ", remainingUnitProduction="
                + remainingUnitProduction);
        if (remainingUnitProduction <= 0) {
          continue;
        }

        // Determine sea and transport units that can be produced in this territory
        final List<JBGPurchaseOption> seaPurchaseOptionsForTerritory =
            JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
                jbgData,
                player,
                purchaseOptions.getSeaDefenseOptions(),
                t,
                purchaseTerritory.getTerritory(),
                isBid);
        seaPurchaseOptionsForTerritory.addAll(purchaseOptions.getAirOptions());
        while (true) {

          // If I have naval attack/defense superiority then break
          if (JBGBattleUtils.territoryHasLocalNavalSuperiority(
              jbgData, t, player, purchaseTerritories, unitsToPlace)) {
            break;
          }

          // Select purchase option
          JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
              player,
              startOfTurnData,
              seaPurchaseOptionsForTerritory,
              resourceTracker,
              remainingUnitProduction,
              unitsToPlace,
              purchaseTerritories);
          final Map<JBGPurchaseOption, Double> defenseEfficiencies = new HashMap<>();
          for (final JBGPurchaseOption ppo : seaPurchaseOptionsForTerritory) {
            defenseEfficiencies.put(
                ppo,
                ppo.getSeaDefenseEfficiency(
                    data,
                    ownedLocalUnits,
                    unitsToPlace,
                    needDestroyer,
                    unusedCarrierCapacity,
                    unusedLocalCarrierCapacity));
          }
          final Optional<JBGPurchaseOption> optionalSelectedOption =
              JBGPurchaseUtils.randomizePurchaseOption(defenseEfficiencies, "Sea Defense");
          if (optionalSelectedOption.isEmpty()) {
            break;
          }
          final JBGPurchaseOption selectedOption = optionalSelectedOption.get();
          if (selectedOption.isDestroyer()) {
            needDestroyer = false;
          }

          // Create new temp units
          resourceTracker.purchase(selectedOption);
          remainingUnitProduction -= selectedOption.getQuantity();
          unitsToPlace.addAll(
              selectedOption.getUnitType().create(selectedOption.getQuantity(), player, true));
          if (selectedOption.isCarrier() || selectedOption.isAir()) {
            unusedCarrierCapacity =
                JBGTransportUtils.getUnusedCarrierCapacity(player, t, unitsToPlace);
            unusedLocalCarrierCapacity =
                JBGTransportUtils.getUnusedLocalCarrierCapacity(player, t, unitsToPlace);
          }
          JBGLogger.trace(
              t
                  + ", added sea defender for naval superiority: "
                  + selectedOption.getUnitType().getName()
                  + ", unusedCarrierCapacity="
                  + unusedCarrierCapacity
                  + ", unusedLocalCarrierCapacity="
                  + unusedLocalCarrierCapacity);
        }
      }

      // Add sea defender units to place territory
      addUnitsToPlaceTerritory(placeTerritory, unitsToPlace, purchaseTerritories);

      // Loop through adjacent purchase territories and purchase transport/amphib units
      final int distance =
          JBGTransportUtils.findMaxMovementForTransports(purchaseOptions.getSeaTransportOptions());
      final Set<Territory> territoriesToCheck = new HashSet<>();
      for (final JBGPurchaseTerritory purchaseTerritory : selectedPurchaseTerritories) {
        final Territory landTerritory = purchaseTerritory.getTerritory();
        final Set<Territory> seaTerritories =
            data.getMap()
                .getNeighbors(
                    landTerritory,
                    distance,
                    JBGMatches.territoryCanMoveSeaUnits(player, data, false));
        for (final Territory seaTerritory : seaTerritories) {
          final Set<Territory> territoriesToLoadFrom =
              new HashSet<>(data.getMap().getNeighbors(seaTerritory, distance));
          territoriesToCheck.addAll(territoriesToLoadFrom);
        }
        final Set<Territory> landNeighbors =
            data.getMap().getNeighbors(t, Matches.territoryIsLand());
        territoriesToCheck.addAll(landNeighbors);
      }
      final Map<Territory, Double> territoryValueMap =
          JBGTerritoryValueUtils.findTerritoryValues(
              jbgData, player, new ArrayList<>(), new ArrayList<>(), territoriesToCheck);
      JBGLogger.trace(t + ", transportMovement=" + distance);
      for (final JBGPurchaseTerritory purchaseTerritory : selectedPurchaseTerritories) {
        final Territory landTerritory = purchaseTerritory.getTerritory();

        // Check if territory can produce units and has remaining production
        int remainingUnitProduction = purchaseTerritory.getRemainingUnitProduction();
        JBGLogger.trace(
            t
                + ", purchaseTerritory="
                + landTerritory
                + ", remainingUnitProduction="
                + remainingUnitProduction);
        if (remainingUnitProduction <= 0) {
          continue;
        }

        // Find local owned units
        final List<Unit> ownedLocalAmphibUnits =
            landTerritory.getUnitCollection().getMatches(Matches.unitIsOwnedBy(player));

        // Determine sea and transport units that can be produced in this territory
        final List<JBGPurchaseOption> seaTransportPurchaseOptionsForTerritory =
            JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
                jbgData, player, purchaseOptions.getSeaTransportOptions(), t, landTerritory, isBid);
        final List<JBGPurchaseOption> amphibPurchaseOptionsForTerritory =
            JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
                jbgData, player, purchaseOptions.getLandOptions(), landTerritory, isBid);

        // Find transports that need loaded and units to ignore that are already paired up
        final List<Unit> transportsThatNeedUnits = new ArrayList<>();
        final Set<Unit> potentialUnitsToLoad = new HashSet<>();
        final Set<Territory> seaTerritories =
            data.getMap()
                .getNeighbors(
                    landTerritory,
                    distance,
                    JBGMatches.territoryCanMoveSeaUnits(player, data, false));
        for (final Territory seaTerritory : seaTerritories) {
          final List<Unit> unitsInTerritory =
              JBGPurchaseUtils.getPlaceUnits(seaTerritory, purchaseTerritories);
          unitsInTerritory.addAll(seaTerritory.getUnits());
          final List<Unit> transports =
              CollectionUtils.getMatches(unitsInTerritory, JBGMatches.unitIsOwnedTransport(player));
          for (final Unit transport : transports) {
            transportsThatNeedUnits.add(transport);
            final Set<Territory> territoriesToLoadFrom =
                new HashSet<>(data.getMap().getNeighbors(seaTerritory, distance));
            territoriesToLoadFrom.removeIf(
                potentialTerritory ->
                    potentialTerritory.isWater()
                        || territoryValueMap.get(potentialTerritory) > 0.25);
            final List<Unit> units =
                JBGTransportUtils.getUnitsToTransportFromTerritories(
                    player,
                    transport,
                    territoriesToLoadFrom,
                    potentialUnitsToLoad,
                    JBGMatches.unitIsOwnedCombatTransportableUnit(player));
            potentialUnitsToLoad.addAll(units);
          }
        }

        // Determine whether transports, amphib units, or both are needed
        final Set<Territory> landNeighbors =
            data.getMap().getNeighbors(t, Matches.territoryIsLand());
        for (final Territory neighbor : landNeighbors) {
          if (territoryValueMap.get(neighbor) <= 0.25) {
            final List<Unit> unitsInTerritory = new ArrayList<>(neighbor.getUnits());
            unitsInTerritory.addAll(JBGPurchaseUtils.getPlaceUnits(neighbor, purchaseTerritories));
            potentialUnitsToLoad.addAll(
                CollectionUtils.getMatches(
                    unitsInTerritory, JBGMatches.unitIsOwnedCombatTransportableUnit(player)));
          }
        }
        JBGLogger.trace(
            t
                + ", potentialUnitsToLoad="
                + potentialUnitsToLoad
                + ", transportsThatNeedUnits="
                + transportsThatNeedUnits);

        // Purchase transports and amphib units
        final List<Unit> amphibUnitsToPlace = new ArrayList<>();
        final List<Unit> transportUnitsToPlace = new ArrayList<>();
        while (true) {
          if (!transportsThatNeedUnits.isEmpty()) {

            // Get next empty transport and find its capacity
            final Unit transport = transportsThatNeedUnits.get(0);
            int transportCapacity = UnitAttachment.get(transport.getType()).getTransportCapacity();

            // Find any existing units that can be transported
            final List<Unit> selectedUnits =
                JBGTransportUtils.selectUnitsToTransportFromList(
                    transport, new ArrayList<>(potentialUnitsToLoad));
            if (!selectedUnits.isEmpty()) {
              potentialUnitsToLoad.removeAll(selectedUnits);
              transportCapacity -= JBGTransportUtils.findUnitsTransportCost(selectedUnits);
            }

            // Purchase units until transport is full
            while (transportCapacity > 0) {

              // Select amphib purchase option and add units
              JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
                  player,
                  startOfTurnData,
                  amphibPurchaseOptionsForTerritory,
                  resourceTracker,
                  remainingUnitProduction,
                  amphibUnitsToPlace,
                  purchaseTerritories);
              final Map<JBGPurchaseOption, Double> amphibEfficiencies = new HashMap<>();
              for (final JBGPurchaseOption ppo : amphibPurchaseOptionsForTerritory) {
                if (ppo.getTransportCost() <= transportCapacity) {
                  amphibEfficiencies.put(
                      ppo,
                      ppo.getAmphibEfficiency(data, ownedLocalAmphibUnits, amphibUnitsToPlace));
                }
              }
              final Optional<JBGPurchaseOption> optionalSelectedOption =
                  JBGPurchaseUtils.randomizePurchaseOption(amphibEfficiencies, "Amphib");
              if (optionalSelectedOption.isEmpty()) {
                break;
              }
              final JBGPurchaseOption ppo = optionalSelectedOption.get();

              // Add amphib unit
              final List<Unit> amphibUnits =
                  ppo.getUnitType().create(ppo.getQuantity(), player, true);
              amphibUnitsToPlace.addAll(amphibUnits);
              resourceTracker.purchase(ppo);
              remainingUnitProduction -= ppo.getQuantity();
              transportCapacity -= ppo.getTransportCost();
              JBGLogger.trace("Selected unit=" + ppo.getUnitType().getName());
            }
            transportsThatNeedUnits.remove(transport);
          } else {

            // Select purchase option
            JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
                player,
                startOfTurnData,
                seaTransportPurchaseOptionsForTerritory,
                resourceTracker,
                remainingUnitProduction,
                transportUnitsToPlace,
                purchaseTerritories);
            final Map<JBGPurchaseOption, Double> transportEfficiencies = new HashMap<>();
            for (final JBGPurchaseOption ppo : seaTransportPurchaseOptionsForTerritory) {
              transportEfficiencies.put(ppo, ppo.getTransportEfficiencyRatio());
            }
            final Optional<JBGPurchaseOption> optionalSelectedOption =
                JBGPurchaseUtils.randomizePurchaseOption(transportEfficiencies, "Sea Transport");
            if (optionalSelectedOption.isEmpty()) {
              break;
            }
            final JBGPurchaseOption ppo = optionalSelectedOption.get();

            // Add transports
            final List<Unit> transports = ppo.getUnitType().create(ppo.getQuantity(), player, true);
            transportUnitsToPlace.addAll(transports);
            resourceTracker.purchase(ppo);
            remainingUnitProduction -= ppo.getQuantity();
            transportsThatNeedUnits.addAll(transports);
            JBGLogger.trace(
                "Selected unit="
                    + ppo.getUnitType().getName()
                    + ", potentialUnitsToLoad="
                    + potentialUnitsToLoad
                    + ", transportsThatNeedUnits="
                    + transportsThatNeedUnits);
          }
        }

        // Add transport units to sea place territory and amphib units to land place territory
        for (final JBGPlaceTerritory ppt : purchaseTerritory.getCanPlaceTerritories()) {
          if (landTerritory.equals(ppt.getTerritory())) {
            ppt.getPlaceUnits().addAll(amphibUnitsToPlace);
          } else if (placeTerritory.equals(ppt)) {
            ppt.getPlaceUnits().addAll(transportUnitsToPlace);
          }
        }
        JBGLogger.trace(
            t
                + ", purchaseTerritory="
                + landTerritory
                + ", transportUnitsToPlace="
                + transportUnitsToPlace
                + ", amphibUnitsToPlace="
                + amphibUnitsToPlace);
      }
    }
  }

  private void purchaseUnitsWithRemainingProduction(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories,
      final List<JBGPurchaseOption> landPurchaseOptions,
      final List<JBGPurchaseOption> airPurchaseOptions) {

    if (resourceTracker.isEmpty()) {
      return;
    }
    JBGLogger.info(
        "Purchase units in territories with remaining production with resources: "
            + resourceTracker);

    // Get all safe/unsafe land place territories with remaining production
    final List<JBGPlaceTerritory> prioritizedLandTerritories = new ArrayList<>();
    final List<JBGPlaceTerritory> prioritizedCantHoldLandTerritories = new ArrayList<>();
    for (final JBGPurchaseTerritory ppt : purchaseTerritories.values()) {
      for (final JBGPlaceTerritory placeTerritory : ppt.getCanPlaceTerritories()) {
        final Territory t = placeTerritory.getTerritory();
        if (!t.isWater()
            && placeTerritory.isCanHold()
            && purchaseTerritories.get(t).getRemainingUnitProduction() > 0) {
          prioritizedLandTerritories.add(placeTerritory);
        } else if (!t.isWater() && purchaseTerritories.get(t).getRemainingUnitProduction() > 0) {
          prioritizedCantHoldLandTerritories.add(placeTerritory);
        }
      }
    }

    // Sort territories by value
    prioritizedLandTerritories.sort(
        Comparator.comparingDouble(JBGPlaceTerritory::getStrategicValue).reversed());
    JBGLogger.debug(
        "Sorted land territories with remaining production: " + prioritizedLandTerritories);

    // Loop through territories and purchase long range attack units
    for (final JBGPlaceTerritory placeTerritory : prioritizedLandTerritories) {
      final Territory t = placeTerritory.getTerritory();
      JBGLogger.debug("Checking territory: " + t);

      // Determine units that can be produced in this territory
      final List<JBGPurchaseOption> airAndLandPurchaseOptions = new ArrayList<>(airPurchaseOptions);
      airAndLandPurchaseOptions.addAll(landPurchaseOptions);
      final List<JBGPurchaseOption> purchaseOptionsForTerritory =
          JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
              jbgData, player, airAndLandPurchaseOptions, t, isBid);

      // Purchase long range attack units for any remaining production
      int remainingUnitProduction = purchaseTerritories.get(t).getRemainingUnitProduction();
      while (true) {

        // Remove options that cost too much PUs or production
        JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
            player,
            startOfTurnData,
            purchaseOptionsForTerritory,
            resourceTracker,
            remainingUnitProduction,
            List.of(),
            purchaseTerritories);
        if (purchaseOptionsForTerritory.isEmpty()) {
          break;
        }

        // Determine best long range attack option (prefer air units)
        JBGPurchaseOption bestAttackOption = null;
        double maxAttackEfficiency = 0;
        for (final JBGPurchaseOption ppo : purchaseOptionsForTerritory) {
          double attackEfficiency =
              ppo.getAttackEfficiency() * ppo.getMovement() / ppo.getQuantity();
          if (ppo.isAir()) {
            attackEfficiency *= 10;
          }
          if (attackEfficiency > maxAttackEfficiency) {
            bestAttackOption = ppo;
            maxAttackEfficiency = attackEfficiency;
          }
        }
        if (bestAttackOption == null) {
          break;
        }

        // Purchase unit
        resourceTracker.purchase(bestAttackOption);
        remainingUnitProduction -= bestAttackOption.getQuantity();
        final List<Unit> newUnit =
            bestAttackOption.getUnitType().create(bestAttackOption.getQuantity(), player, true);
        placeTerritory.getPlaceUnits().addAll(newUnit);
        JBGLogger.trace(t + ", addedUnit=" + newUnit);
      }
    }

    // Sort territories by value
    prioritizedCantHoldLandTerritories.sort(
        Comparator.comparingDouble(JBGPlaceTerritory::getDefenseValue).reversed());
    JBGLogger.debug(
        "Sorted can't hold land territories with remaining production: "
            + prioritizedCantHoldLandTerritories);

    // Loop through territories and purchase defense units
    for (final JBGPlaceTerritory placeTerritory : prioritizedCantHoldLandTerritories) {
      final Territory t = placeTerritory.getTerritory();
      JBGLogger.debug("Checking territory: " + t);

      // Find local owned units
      final List<Unit> ownedLocalUnits =
          t.getUnitCollection().getMatches(Matches.unitIsOwnedBy(player));

      // Determine units that can be produced in this territory
      final List<JBGPurchaseOption> airAndLandPurchaseOptions = new ArrayList<>(airPurchaseOptions);
      airAndLandPurchaseOptions.addAll(landPurchaseOptions);
      final List<JBGPurchaseOption> purchaseOptionsForTerritory =
          JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
              jbgData, player, airAndLandPurchaseOptions, t, isBid);

      // Purchase defense units for any remaining production
      int remainingUnitProduction = purchaseTerritories.get(t).getRemainingUnitProduction();
      while (true) {

        // Select purchase option
        JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
            player,
            startOfTurnData,
            purchaseOptionsForTerritory,
            resourceTracker,
            remainingUnitProduction,
            new ArrayList<>(),
            purchaseTerritories);
        final Map<JBGPurchaseOption, Double> defenseEfficiencies = new HashMap<>();
        for (final JBGPurchaseOption ppo : purchaseOptionsForTerritory) {
          defenseEfficiencies.put(
              ppo,
              Math.pow(ppo.getCost(), 2)
                  * ppo.getDefenseEfficiency(
                      1, data, ownedLocalUnits, placeTerritory.getPlaceUnits()));
        }
        final Optional<JBGPurchaseOption> optionalSelectedOption =
            JBGPurchaseUtils.randomizePurchaseOption(defenseEfficiencies, "Defense");
        if (optionalSelectedOption.isEmpty()) {
          break;
        }
        final JBGPurchaseOption selectedOption = optionalSelectedOption.get();

        // Purchase unit
        resourceTracker.purchase(selectedOption);
        remainingUnitProduction -= selectedOption.getQuantity();
        final List<Unit> newUnit =
            selectedOption.getUnitType().create(selectedOption.getQuantity(), player, true);
        placeTerritory.getPlaceUnits().addAll(newUnit);
        JBGLogger.trace(t + ", addedUnit=" + newUnit);
      }
    }
  }

  private void upgradeUnitsWithRemainingPUs(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories,
      final JBGPurchaseOptionMap purchaseOptions) {

    if (resourceTracker.isEmpty()) {
      return;
    }
    JBGLogger.info("Upgrade units with resources: " + resourceTracker);

    // Get all safe land place territories
    final List<JBGPlaceTerritory> prioritizedLandTerritories = new ArrayList<>();
    for (final JBGPurchaseTerritory ppt : purchaseTerritories.values()) {
      for (final JBGPlaceTerritory placeTerritory : ppt.getCanPlaceTerritories()) {
        final Territory t = placeTerritory.getTerritory();
        if (!t.isWater() && placeTerritory.isCanHold()) {
          prioritizedLandTerritories.add(placeTerritory);
        }
      }
    }

    // Sort territories by ascending value (try upgrading units in far away territories first)
    prioritizedLandTerritories.sort(
        Comparator.comparingDouble(JBGPlaceTerritory::getStrategicValue));
    JBGLogger.debug("Sorted land territories: " + prioritizedLandTerritories);

    // Loop through territories and upgrade units to long range attack units
    for (final JBGPlaceTerritory placeTerritory : prioritizedLandTerritories) {
      final Territory t = placeTerritory.getTerritory();
      JBGLogger.debug("Checking territory: " + t);

      // Determine units that can be produced in this territory
      final List<JBGPurchaseOption> airAndLandPurchaseOptions =
          new ArrayList<>(purchaseOptions.getAirOptions());
      airAndLandPurchaseOptions.addAll(purchaseOptions.getLandOptions());
      final List<JBGPurchaseOption> purchaseOptionsForTerritory =
          JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
              jbgData, player, airAndLandPurchaseOptions, t, isBid);

      // Purchase long range attack units for any remaining production
      int remainingUpgradeUnits = purchaseTerritories.get(t).getUnitProduction() / 3;
      while (true) {
        if (remainingUpgradeUnits <= 0) {
          break;
        }

        // Find cheapest placed purchase option
        JBGPurchaseOption minPurchaseOption = null;
        for (final Unit u : placeTerritory.getPlaceUnits()) {
          for (final JBGPurchaseOption ppo : airAndLandPurchaseOptions) {
            if (u.getType().equals(ppo.getUnitType())
                && (minPurchaseOption == null || ppo.getCost() < minPurchaseOption.getCost())) {
              minPurchaseOption = ppo;
            }
          }
        }
        if (minPurchaseOption == null) {
          break;
        }

        // Remove options that cost too much PUs or production
        resourceTracker.removeTempPurchase(minPurchaseOption);
        JBGPurchaseValidationUtils.removeInvalidPurchaseOptions(
            player,
            startOfTurnData,
            purchaseOptionsForTerritory,
            resourceTracker,
            1,
            new ArrayList<>(),
            purchaseTerritories);
        resourceTracker.clearTempPurchases();
        if (purchaseOptionsForTerritory.isEmpty()) {
          break;
        }

        // Determine best upgrade option (prefer air units)
        // TODO: ensure map has carriers or air unit has range to reach enemy land mass
        JBGPurchaseOption bestUpgradeOption = null;
        double maxEfficiency =
            findUpgradeUnitEfficiency(minPurchaseOption, placeTerritory.getStrategicValue());
        for (final JBGPurchaseOption ppo : purchaseOptionsForTerritory) {
          if (ppo.getCost() > minPurchaseOption.getCost()
              && (ppo.isAir()
                  || placeTerritory.getStrategicValue() >= 0.25
                  || ppo.getTransportCost() <= minPurchaseOption.getTransportCost())) {
            double efficiency = findUpgradeUnitEfficiency(ppo, placeTerritory.getStrategicValue());
            if (ppo.isAir()) {
              efficiency *= 10;
            }
            if (ppo.getCarrierCost() > 0) {
              final int unusedLocalCarrierCapacity =
                  JBGTransportUtils.getUnusedLocalCarrierCapacity(
                      player, t, placeTerritory.getPlaceUnits());
              final int neededFighters = unusedLocalCarrierCapacity / ppo.getCarrierCost();
              efficiency *= (1 + neededFighters);
            }
            if (efficiency > maxEfficiency) {
              bestUpgradeOption = ppo;
              maxEfficiency = efficiency;
            }
          }
        }
        if (bestUpgradeOption == null) {
          airAndLandPurchaseOptions.remove(minPurchaseOption);
          continue;
        }

        // Find units to remove
        final List<Unit> unitsToRemove = new ArrayList<>();
        int numUnitsToRemove = minPurchaseOption.getQuantity();
        for (final Unit u : placeTerritory.getPlaceUnits()) {
          if (numUnitsToRemove <= 0) {
            break;
          }
          if (u.getType().equals(minPurchaseOption.getUnitType())) {
            unitsToRemove.add(u);
            numUnitsToRemove--;
          }
        }
        if (numUnitsToRemove > 0) {
          airAndLandPurchaseOptions.remove(minPurchaseOption);
          continue;
        }

        // Replace units
        resourceTracker.removePurchase(minPurchaseOption);
        remainingUpgradeUnits -= minPurchaseOption.getQuantity();
        placeTerritory.getPlaceUnits().removeAll(unitsToRemove);
        JBGLogger.trace(t + ", removedUnits=" + unitsToRemove);
        for (int i = 0; i < unitsToRemove.size(); i++) {
          if (resourceTracker.hasEnough(bestUpgradeOption)) {
            resourceTracker.purchase(bestUpgradeOption);
            final List<Unit> newUnit =
                bestUpgradeOption
                    .getUnitType()
                    .create(bestUpgradeOption.getQuantity(), player, true);
            placeTerritory.getPlaceUnits().addAll(newUnit);
            JBGLogger.trace(t + ", addedUnit=" + newUnit);
          }
        }
      }
    }
  }

  /**
   * Determine efficiency value for upgrading to the given purchase option. If the strategic value
   * of the territory is low then favor high movement units as its far from the enemy otherwise
   * favor high defense.
   */
  private static double findUpgradeUnitEfficiency(
      final JBGPurchaseOption ppo, final double strategicValue) {
    final double multiplier =
        (strategicValue >= 1) ? ppo.getDefenseEfficiency() : ppo.getMovement();
    return ppo.getAttackEfficiency() * multiplier * ppo.getCost() / ppo.getQuantity();
  }

  private IntegerMap<ProductionRule> populateProductionRuleMap(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories,
      final JBGPurchaseOptionMap purchaseOptions) {

    JBGLogger.info("Populate production rule map");
    final List<Unit> unplacedUnits = player.getUnitCollection().getMatches(Matches.unitIsNotSea());
    final IntegerMap<ProductionRule> purchaseMap = new IntegerMap<>();
    for (final JBGPurchaseOption ppo : purchaseOptions.getAllOptions()) {
      final int numUnits =
          (int)
              purchaseTerritories.values().stream()
                  .map(JBGPurchaseTerritory::getCanPlaceTerritories)
                  .flatMap(Collection::stream)
                  .map(JBGPlaceTerritory::getPlaceUnits)
                  .flatMap(Collection::stream)
                  .filter(u -> u.getType().equals(ppo.getUnitType()))
                  .filter(u -> !unplacedUnits.contains(u))
                  .count();
      if (numUnits > 0) {
        final int numProductionRule = numUnits / ppo.getQuantity();
        purchaseMap.put(ppo.getProductionRule(), numProductionRule);
        JBGLogger.info(numProductionRule + " " + ppo.getProductionRule());
      }
    }
    return purchaseMap;
  }

  private void placeDefenders(
      final Map<Territory, JBGPurchaseTerritory> placeNonConstructionTerritories,
      final List<JBGPlaceTerritory> needToDefendTerritories,
      final IAbstractPlaceDelegate placeDelegate) {

    JBGLogger.info("Place defenders with units=" + player.getUnits());

    final JBGOtherMoveOptions enemyAttackOptions = territoryManager.getEnemyAttackOptions();

    // Loop through prioritized territories and purchase defenders
    for (final JBGPlaceTerritory placeTerritory : needToDefendTerritories) {
      final Territory t = placeTerritory.getTerritory();
      JBGLogger.debug(
          "Placing defenders for "
              + t.getName()
              + ", enemyAttackers="
              + enemyAttackOptions.getMax(t).getMaxUnits()
              + ", amphibEnemyAttackers="
              + enemyAttackOptions.getMax(t).getMaxAmphibUnits()
              + ", defenders="
              + placeTerritory.getDefendingUnits());

      // Check if any units can be placed
      final PlaceableUnits placeableUnits =
          placeDelegate.getPlaceableUnits(
              player.getUnitCollection().getMatches(Matches.unitIsNotConstruction()), t);
      if (placeableUnits.isError()) {
        JBGLogger.trace(t + " can't place units with error: " + placeableUnits.getErrorMessage());
        continue;
      }

      // Find remaining unit production
      int remainingUnitProduction = placeableUnits.getMaxUnits();
      if (remainingUnitProduction == -1) {
        remainingUnitProduction = Integer.MAX_VALUE;
      }
      JBGLogger.trace(t + ", remainingUnitProduction=" + remainingUnitProduction);

      // Place defenders and check battle results
      final List<Unit> unitsThatCanBePlaced = new ArrayList<>(placeableUnits.getUnits());
      final int landPlaceCount = Math.min(remainingUnitProduction, unitsThatCanBePlaced.size());
      final List<Unit> unitsToPlace = new ArrayList<>();
      JBGBattleResult finalResult = new JBGBattleResult();
      for (int i = 0; i < landPlaceCount; i++) {

        // Add defender
        unitsToPlace.add(unitsThatCanBePlaced.get(i));

        // Find current battle result
        final Set<Unit> enemyAttackingUnits =
            new HashSet<>(enemyAttackOptions.getMax(t).getMaxUnits());
        enemyAttackingUnits.addAll(enemyAttackOptions.getMax(t).getMaxAmphibUnits());
        final List<Unit> defenders = new ArrayList<>(placeTerritory.getDefendingUnits());
        defenders.addAll(unitsToPlace);
        finalResult =
            calc.calculateBattleResults(
                jbgData,
                t,
                new ArrayList<>(enemyAttackingUnits),
                defenders,
                enemyAttackOptions.getMax(t).getMaxBombardUnits());

        // Break if it can be held
        if ((!t.equals(jbgData.getMyCapital())
                && !finalResult.isHasLandUnitRemaining()
                && finalResult.getTuvSwing() <= 0)
            || (t.equals(jbgData.getMyCapital())
                && finalResult.getWinPercentage() < (100 - jbgData.getWinPercentage())
                && finalResult.getTuvSwing() <= 0)) {
          break;
        }
      }

      // Check to see if its worth trying to defend the territory
      if (!finalResult.isHasLandUnitRemaining()
          || finalResult.getTuvSwing() < placeTerritory.getMinBattleResult().getTuvSwing()
          || t.equals(jbgData.getMyCapital())) {
        JBGLogger.trace(
            t + ", placedUnits=" + unitsToPlace + ", TUVSwing=" + finalResult.getTuvSwing());
        doPlace(t, unitsToPlace, placeDelegate);
      } else {
        setCantHoldPlaceTerritory(placeTerritory, placeNonConstructionTerritories);
        JBGLogger.trace(
            t
                + ", unable to defend with placedUnits="
                + unitsToPlace
                + ", TUVSwing="
                + finalResult.getTuvSwing()
                + ", minTUVSwing="
                + placeTerritory.getMinBattleResult().getTuvSwing());
      }
    }
  }

  private void placeUnits(
      final List<JBGPlaceTerritory> prioritizedTerritories,
      final IAbstractPlaceDelegate placeDelegate,
      final Predicate<Unit> unitMatch) {

    JBGLogger.info("Place units=" + player.getUnits());

    // Loop through prioritized territories and place units
    for (final JBGPlaceTerritory placeTerritory : prioritizedTerritories) {
      final Territory t = placeTerritory.getTerritory();
      JBGLogger.debug("Checking place for " + t.getName());

      // Check if any units can be placed
      final PlaceableUnits placeableUnits =
          placeDelegate.getPlaceableUnits(player.getUnitCollection().getMatches(unitMatch), t);
      if (placeableUnits.isError()) {
        JBGLogger.trace(t + " can't place units with error: " + placeableUnits.getErrorMessage());
        continue;
      }

      // Find remaining unit production
      int remainingUnitProduction = placeableUnits.getMaxUnits();
      if (remainingUnitProduction == -1) {
        remainingUnitProduction = Integer.MAX_VALUE;
      }
      JBGLogger.trace(t + ", remainingUnitProduction=" + remainingUnitProduction);

      // Place as many units as possible
      final List<Unit> unitsThatCanBePlaced = new ArrayList<>(placeableUnits.getUnits());
      final int placeCount = Math.min(remainingUnitProduction, unitsThatCanBePlaced.size());
      final List<Unit> unitsToPlace = unitsThatCanBePlaced.subList(0, placeCount);
      JBGLogger.trace(t + ", placedUnits=" + unitsToPlace);
      doPlace(t, unitsToPlace, placeDelegate);
    }
  }

  private void addUnitsToPlaceTerritory(
      final JBGPlaceTerritory placeTerritory,
      final List<Unit> unitsToPlace,
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories) {

    // Add units to place territory
    for (final JBGPurchaseTerritory purchaseTerritory : purchaseTerritories.values()) {
      for (final JBGPlaceTerritory ppt : purchaseTerritory.getCanPlaceTerritories()) {

        // If place territory is equal to the current place territory and has remaining production
        if (placeTerritory.equals(ppt)
            && purchaseTerritory.getRemainingUnitProduction() > 0
            && JBGPurchaseValidationUtils.canUnitsBePlaced(
                jbgData,
                unitsToPlace,
                player,
                ppt.getTerritory(),
                purchaseTerritory.getTerritory(),
                isBid)) {
          final List<Unit> constructions =
              CollectionUtils.getMatches(unitsToPlace, Matches.unitIsConstruction());
          unitsToPlace.removeAll(constructions);
          ppt.getPlaceUnits().addAll(constructions);
          final int numUnits =
              Math.min(purchaseTerritory.getRemainingUnitProduction(), unitsToPlace.size());
          final List<Unit> units = unitsToPlace.subList(0, numUnits);
          ppt.getPlaceUnits().addAll(units);
          units.clear();
        }
      }
    }
  }

  private static void setCantHoldPlaceTerritory(
      final JBGPlaceTerritory placeTerritory,
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories) {

    // Add units to place territory
    for (final JBGPurchaseTerritory t : purchaseTerritories.values()) {
      for (final JBGPlaceTerritory ppt : t.getCanPlaceTerritories()) {
        // If place territory is equal to the current place territory
        if (placeTerritory.equals(ppt)) {
          ppt.setCanHold(false);
        }
      }
    }
  }

  private static List<JBGPurchaseTerritory> getPurchaseTerritories(
      final JBGPlaceTerritory placeTerritory,
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories) {
    final List<JBGPurchaseTerritory> territories = new ArrayList<>();
    for (final JBGPurchaseTerritory t : purchaseTerritories.values()) {
      for (final JBGPlaceTerritory ppt : t.getCanPlaceTerritories()) {
        if (placeTerritory.equals(ppt)) {
          territories.add(t);
        }
      }
    }
    return territories;
  }

  private static void doPlace(
      final Territory t, final Collection<Unit> toPlace, final IAbstractPlaceDelegate del) {
    for (final Unit unit : toPlace) {
      final String message =
          del.placeUnits(List.of(unit), t, IAbstractPlaceDelegate.BidMode.NOT_BID);
      if (message != null) {
        JBGLogger.warn(message);
        JBGLogger.warn("Attempt was at: " + t + " with: " + unit);
      }
    }
    AbstractAi.movePause();
  }
}
