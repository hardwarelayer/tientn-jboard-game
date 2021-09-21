package games.strategy.triplea.ai.jbg.util;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.ProductionFrontier;
import games.strategy.engine.data.ProductionRule;
import games.strategy.engine.data.Resource;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.Constants;
import games.strategy.triplea.UnitUtils;
import games.strategy.triplea.ai.jbg.JBGData;
import games.strategy.triplea.ai.jbg.data.JBGPlaceTerritory;
import games.strategy.triplea.ai.jbg.data.JBGPurchaseOption;
import games.strategy.triplea.ai.jbg.data.JBGPurchaseTerritory;
import games.strategy.triplea.ai.jbg.logging.JBGLogger;
import games.strategy.triplea.attachments.RulesAttachment;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TransportTracker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import org.triplea.java.collections.CollectionUtils;
import org.triplea.java.collections.IntegerMap;

/** JBG AI purchase utilities. */
@UtilityClass
public final class JBGPurchaseUtils {

  /**
   * Randomly selects one of the specified purchase options of the specified type.
   *
   * @return The selected purchase option or empty if no purchase option of the specified type is
   *     available.
   */
  public static Optional<JBGPurchaseOption> randomizePurchaseOption(
      final Map<JBGPurchaseOption, Double> purchaseEfficiencies, final String type) {

    JBGLogger.trace("Select purchase option for " + type);
    double totalEfficiency = 0;
    for (final Double efficiency : purchaseEfficiencies.values()) {
      totalEfficiency += efficiency;
    }
    if (totalEfficiency == 0) {
      return Optional.empty();
    }
    final Map<JBGPurchaseOption, Double> purchasePercentages = new LinkedHashMap<>();
    double upperBound = 0.0;
    for (final JBGPurchaseOption ppo : purchaseEfficiencies.keySet()) {
      final double chance = purchaseEfficiencies.get(ppo) / totalEfficiency * 100;
      upperBound += chance;
      purchasePercentages.put(ppo, upperBound);
      JBGLogger.trace(
          ppo.getUnitType().getName() + ", probability=" + chance + ", upperBound=" + upperBound);
    }
    final double randomNumber = Math.random() * 100;
    JBGLogger.trace("Random number: " + randomNumber);
    for (final JBGPurchaseOption ppo : purchasePercentages.keySet()) {
      if (randomNumber <= purchasePercentages.get(ppo)) {
        return Optional.of(ppo);
      }
    }
    return Optional.of(purchasePercentages.keySet().iterator().next());
  }

  /**
   * Returns the list of units to purchase that will maximize defense in the specified territory
   * based on the specified purchase options.
   */
  public static List<Unit> findMaxPurchaseDefenders(
      final JBGData jbgData,
      final GamePlayer player,
      final Territory t,
      final List<JBGPurchaseOption> landPurchaseOptions) {

    JBGLogger.info("Find max purchase defenders for " + t.getName());
    final GameData data = jbgData.getData();

    // Determine most cost efficient defender that can be produced in this territory
    final Resource pus = data.getResourceList().getResource(Constants.PUS);
    final int pusRemaining = player.getResources().getQuantity(pus);
    final List<JBGPurchaseOption> purchaseOptionsForTerritory =
        JBGPurchaseValidationUtils.findPurchaseOptionsForTerritory(
            jbgData, player, landPurchaseOptions, t, false);
    JBGPurchaseOption bestDefenseOption = null;
    double maxDefenseEfficiency = 0;
    for (final JBGPurchaseOption ppo : purchaseOptionsForTerritory) {
      if (ppo.getDefenseEfficiency() > maxDefenseEfficiency && ppo.getCost() <= pusRemaining) {
        bestDefenseOption = ppo;
        maxDefenseEfficiency = ppo.getDefenseEfficiency();
      }
    }

    // Determine number of defenders I can purchase
    final List<Unit> placeUnits = new ArrayList<>();
    if (bestDefenseOption != null) {
      JBGLogger.debug("Best defense option: " + bestDefenseOption.getUnitType().getName());
      int remainingUnitProduction = getUnitProduction(t, data, player);
      int pusSpent = 0;
      while (bestDefenseOption.getCost() <= (pusRemaining - pusSpent)
          && remainingUnitProduction >= bestDefenseOption.getQuantity()) {

        // If out of PUs or production then break

        // Create new temp defenders
        pusSpent += bestDefenseOption.getCost();
        remainingUnitProduction -= bestDefenseOption.getQuantity();
        placeUnits.addAll(
            bestDefenseOption.getUnitType().create(bestDefenseOption.getQuantity(), player, true));
      }
      JBGLogger.debug("Potential purchased defenders: " + placeUnits);
    }
    return placeUnits;
  }

  /**
   * Find all territories that bid units can be placed in and initialize data holders for them.
   *
   * @param jbgData - the pro AI data
   * @param player - current AI player
   * @return - map of all available purchase and place territories
   */
  public static Map<Territory, JBGPurchaseTerritory> findBidTerritories(
      final JBGData jbgData, final GamePlayer player) {

    JBGLogger.info("Find all bid territories");
    final GameData data = jbgData.getData();

    // Find all territories that I can place units on
    final Set<Territory> ownedOrHasUnitTerritories =
        new HashSet<>(data.getMap().getTerritoriesOwnedBy(player));
    ownedOrHasUnitTerritories.addAll(jbgData.getMyUnitTerritories());
    final List<Territory> potentialTerritories =
        CollectionUtils.getMatches(
            ownedOrHasUnitTerritories,
            Matches.territoryIsPassableAndNotRestrictedAndOkByRelationships(
                player, data, false, false, false, false, false));

    // Create purchase territory holder for each factory territory
    final Map<Territory, JBGPurchaseTerritory> purchaseTerritories = new HashMap<>();
    for (final Territory t : potentialTerritories) {
      final JBGPurchaseTerritory ppt = new JBGPurchaseTerritory(t, data, player, 1, true);
      purchaseTerritories.put(t, ppt);
      JBGLogger.debug(ppt.toString());
    }
    return purchaseTerritories;
  }

  public static void incrementUnitProductionForBidTerritories(
      final Map<Territory, JBGPurchaseTerritory> purchaseTerritories) {
    purchaseTerritories.values().forEach(ppt -> ppt.setUnitProduction(ppt.getUnitProduction() + 1));
  }

  /** Returns all possible territories within which {@code player} can place purchased units. */
  public static Map<Territory, JBGPurchaseTerritory> findPurchaseTerritories(
      final JBGData jbgData, final GamePlayer player) {

    JBGLogger.info("Find all purchase territories");
    final GameData data = jbgData.getData();

    // Find all territories that I can place units on
    final RulesAttachment ra = player.getRulesAttachment();
    List<Territory> ownedAndNotConqueredFactoryTerritories;
    if (ra != null && ra.getPlacementAnyTerritory()) {
      ownedAndNotConqueredFactoryTerritories = data.getMap().getTerritoriesOwnedBy(player);
    } else {
      ownedAndNotConqueredFactoryTerritories =
          CollectionUtils.getMatches(
              data.getMap().getTerritories(),
              JBGMatches.territoryHasFactoryAndIsNotConqueredOwnedLand(player, data));
    }
    ownedAndNotConqueredFactoryTerritories =
        CollectionUtils.getMatches(
            ownedAndNotConqueredFactoryTerritories,
            JBGMatches.territoryCanMoveLandUnits(player, data, false));

    // Create purchase territory holder for each factory territory
    final Map<Territory, JBGPurchaseTerritory> purchaseTerritories = new HashMap<>();
    for (final Territory t : ownedAndNotConqueredFactoryTerritories) {
      final int unitProduction = getUnitProduction(t, data, player);
      final JBGPurchaseTerritory ppt = new JBGPurchaseTerritory(t, data, player, unitProduction);
      purchaseTerritories.put(t, ppt);
      JBGLogger.debug(ppt.toString());
    }
    return purchaseTerritories;
  }

  private static int getUnitProduction(
      final Territory territory, final GameData data, final GamePlayer player) {
    final Predicate<Unit> factoryMatch =
        Matches.unitIsOwnedAndIsFactoryOrCanProduceUnits(player)
            .and(Matches.unitIsBeingTransported().negate())
            .and((territory.isWater() ? Matches.unitIsLand() : Matches.unitIsSea()).negate());
    final Collection<Unit> factoryUnits = territory.getUnitCollection().getMatches(factoryMatch);
    final TerritoryAttachment ta = TerritoryAttachment.get(territory);
    final boolean originalFactory = (ta != null && ta.getOriginalFactory());
    final boolean playerIsOriginalOwner =
        !factoryUnits.isEmpty() && player.equals(getOriginalFactoryOwner(territory, player));
    final RulesAttachment ra = player.getRulesAttachment();
    if (originalFactory && playerIsOriginalOwner) {
      if (ra != null && ra.getMaxPlacePerTerritory() != -1) {
        return Math.max(0, ra.getMaxPlacePerTerritory());
      }
      return Integer.MAX_VALUE;
    }
    if (ra != null && ra.getPlacementAnyTerritory()) {
      return Integer.MAX_VALUE;
    }
    return UnitUtils.getProductionPotentialOfTerritory(
        territory.getUnits(), territory, player, data, true, true);
  }

  /**
   * Calculates how many of each of the specified construction units can be placed in the specified
   * territory.
   */
  public static int getMaxConstructions(
      final Territory territory,
      final GameData data,
      final GamePlayer player,
      final List<JBGPurchaseOption> zeroMoveDefensePurchaseOptions) {
    final IntegerMap<String> constructionTypesPerTurn = new IntegerMap<>();
    for (final JBGPurchaseOption ppo : zeroMoveDefensePurchaseOptions) {
      if (ppo.isConstruction()) {
        constructionTypesPerTurn.put(ppo.getConstructionType(), ppo.getConstructionTypePerTurn());
      }
    }
    return constructionTypesPerTurn.totalValues();
  }

  private static GamePlayer getOriginalFactoryOwner(
      final Territory territory, final GamePlayer player) {

    final Collection<Unit> factoryUnits =
        territory.getUnitCollection().getMatches(Matches.unitCanProduceUnits());
    if (factoryUnits.isEmpty()) {
      throw new IllegalStateException("No factory in territory:" + territory);
    }
    for (final Unit factory2 : factoryUnits) {
      if (player.equals(factory2.getOriginalOwner())) {
        return factory2.getOriginalOwner();
      }
    }
    return factoryUnits.iterator().next().getOriginalOwner();
  }

  /** Comparator that sorts cheaper units before expensive ones. */
  public static Comparator<Unit> getCostComparator(final JBGData jbgData) {
    return Comparator.comparingDouble((unit) -> JBGPurchaseUtils.getCost(jbgData, unit));
  }

  /**
   * How many PU's does it cost the given player to produce the given unit including any dependents.
   */
  public static double getCost(final JBGData jbgData, final Unit unit) {
    final Resource pus = unit.getData().getResourceList().getResource(Constants.PUS);
    final Collection<Unit> units = TransportTracker.transportingAndUnloaded(unit);
    units.add(unit);
    double cost = 0.0;
    for (final Unit u : units) {
      final ProductionRule rule = getProductionRule(u.getType(), u.getOwner());
      if (rule == null) {
        cost += jbgData.getUnitValue(u.getType());
      } else {
        cost += ((double) rule.getCosts().getInt(pus)) / rule.getResults().totalValues();
      }
    }
    return cost;
  }

  /**
   * Get the production rule for the given player, for the given unit type.
   *
   * <p>If no such rule can be found, then return null.
   */
  private static ProductionRule getProductionRule(
      final UnitType unitType, final GamePlayer player) {
    final ProductionFrontier frontier = player.getProductionFrontier();
    if (frontier == null) {
      return null;
    }
    for (final ProductionRule rule : frontier) {
      if (rule.getResults().getInt(unitType) > 0) {
        return rule;
      }
    }
    return null;
  }

  /**
   * Returns the list of units to place in the specified territory based on the specified purchases.
   */
  public static List<Unit> getPlaceUnits(
      final Territory t, final Map<Territory, JBGPurchaseTerritory> purchaseTerritories) {

    final List<Unit> placeUnits = new ArrayList<>();
    for (final JBGPurchaseTerritory purchaseTerritory : purchaseTerritories.values()) {
      for (final JBGPlaceTerritory ppt : purchaseTerritory.getCanPlaceTerritories()) {
        if (t.equals(ppt.getTerritory())) {
          placeUnits.addAll(ppt.getPlaceUnits());
        }
      }
    }
    return placeUnits;
  }
}
