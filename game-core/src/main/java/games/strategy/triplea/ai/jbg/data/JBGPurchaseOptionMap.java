package games.strategy.triplea.ai.jbg.data;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.NamedAttachable;
import games.strategy.engine.data.ProductionFrontier;
import games.strategy.engine.data.ProductionRule;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.ai.jbg.logging.JBGLogger;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.Matches;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Takes all available purchase options, filters out those which the AI can't handle, and sorts them
 * into categories.
 */
public class JBGPurchaseOptionMap {

  private final List<JBGPurchaseOption> landFodderOptions;
  private final List<JBGPurchaseOption> landAttackOptions;
  private final List<JBGPurchaseOption> landDefenseOptions;
  private final List<JBGPurchaseOption> landZeroMoveOptions;
  private final List<JBGPurchaseOption> airOptions;
  private final List<JBGPurchaseOption> seaDefenseOptions;
  private final List<JBGPurchaseOption> seaTransportOptions;
  private final List<JBGPurchaseOption> seaCarrierOptions;
  private final List<JBGPurchaseOption> seaSubOptions;
  private final List<JBGPurchaseOption> aaOptions;
  private final List<JBGPurchaseOption> factoryOptions;
  private final List<JBGPurchaseOption> specialOptions;

  public JBGPurchaseOptionMap(final GamePlayer player, final GameData data) {

    JBGLogger.info("Purchase Options");

    // Initialize lists
    landFodderOptions = new ArrayList<>();
    landAttackOptions = new ArrayList<>();
    landDefenseOptions = new ArrayList<>();
    landZeroMoveOptions = new ArrayList<>();
    airOptions = new ArrayList<>();
    seaDefenseOptions = new ArrayList<>();
    seaTransportOptions = new ArrayList<>();
    seaCarrierOptions = new ArrayList<>();
    seaSubOptions = new ArrayList<>();
    aaOptions = new ArrayList<>();
    factoryOptions = new ArrayList<>();
    specialOptions = new ArrayList<>();

    // Add each production rule to appropriate list(s)
    final ProductionFrontier productionFrontier = player.getProductionFrontier();
    if (productionFrontier == null || productionFrontier.getRules() == null) {
      return;
    }
    for (final ProductionRule rule : productionFrontier.getRules()) {

      // Check if rule is for a unit
      final NamedAttachable resourceOrUnit = rule.getResults().keySet().iterator().next();
      if (!(resourceOrUnit instanceof UnitType)) {
        continue;
      }
      final UnitType unitType = (UnitType) resourceOrUnit;

      // Add rule to appropriate purchase option list
      if (Matches.unitTypeConsumesUnitsOnCreation().test(unitType)
          || UnitAttachment.get(unitType).getIsSuicideOnHit()
          || canUnitTypeSuicide(unitType, player)) {
        final JBGPurchaseOption ppo = new JBGPurchaseOption(rule, unitType, player, data);
        specialOptions.add(ppo);
        JBGLogger.debug("Special: " + ppo);
      } else if (Matches.unitTypeCanProduceUnits().test(unitType)
          && Matches.unitTypeIsInfrastructure().test(unitType)) {
        final JBGPurchaseOption ppo = new JBGPurchaseOption(rule, unitType, player, data);
        factoryOptions.add(ppo);
        JBGLogger.debug("Factory: " + ppo);
      } else if (UnitAttachment.get(unitType).getMovement(player) <= 0
          && Matches.unitTypeIsLand().test(unitType)) {
        final JBGPurchaseOption ppo = new JBGPurchaseOption(rule, unitType, player, data);
        landZeroMoveOptions.add(ppo);
        JBGLogger.debug("Zero Move Land: " + ppo);
      } else if (Matches.unitTypeIsLand().test(unitType)) {
        final JBGPurchaseOption ppo = new JBGPurchaseOption(rule, unitType, player, data);
        if (!Matches.unitTypeIsInfrastructure().test(unitType)) {
          landFodderOptions.add(ppo);
        }
        if ((ppo.getAttack() > 0 || ppo.isAttackSupport())
            && (ppo.getAttack() >= ppo.getDefense() || ppo.getMovement() > 1)) {
          landAttackOptions.add(ppo);
        }
        if ((ppo.getDefense() > 0 || ppo.isDefenseSupport())
            && (ppo.getDefense() >= ppo.getAttack() || ppo.getMovement() > 1)) {
          landDefenseOptions.add(ppo);
        }
        if (Matches.unitTypeIsAaForBombingThisUnitOnly().test(unitType)) {
          aaOptions.add(ppo);
        }
        JBGLogger.debug("Land: " + ppo);
      } else if (Matches.unitTypeIsAir().test(unitType)) {
        final JBGPurchaseOption ppo = new JBGPurchaseOption(rule, unitType, player, data);
        airOptions.add(ppo);
        JBGLogger.debug("Air: " + ppo);
      } else if (Matches.unitTypeIsSea().test(unitType)) {
        final JBGPurchaseOption ppo = new JBGPurchaseOption(rule, unitType, player, data);
        if (!ppo.isSub()) {
          seaDefenseOptions.add(ppo);
        }
        if (ppo.isTransport()) {
          seaTransportOptions.add(ppo);
        }
        if (ppo.isCarrier()) {
          seaCarrierOptions.add(ppo);
        }
        if (ppo.isSub()) {
          seaSubOptions.add(ppo);
        }
        JBGLogger.debug("Sea: " + ppo);
      }
    }
    if (landAttackOptions.isEmpty()) {
      landAttackOptions.addAll(landDefenseOptions);
    }
    if (landDefenseOptions.isEmpty()) {
      landDefenseOptions.addAll(landAttackOptions);
    }

    // Print categorized options
    JBGLogger.info("Purchase Categories");
    logOptions(landFodderOptions, "Land Fodder Options: ");
    logOptions(landAttackOptions, "Land Attack Options: ");
    logOptions(landDefenseOptions, "Land Defense Options: ");
    logOptions(landZeroMoveOptions, "Land Zero Move Options: ");
    logOptions(airOptions, "Air Options: ");
    logOptions(seaDefenseOptions, "Sea Defense Options: ");
    logOptions(seaTransportOptions, "Sea Transport Options: ");
    logOptions(seaCarrierOptions, "Sea Carrier Options: ");
    logOptions(seaSubOptions, "Sea Sub Options: ");
    logOptions(aaOptions, "AA Options: ");
    logOptions(factoryOptions, "Factory Options: ");
    logOptions(specialOptions, "Special Options: ");
  }

  private boolean canUnitTypeSuicide(final UnitType unitType, final GamePlayer player) {
    return (UnitAttachment.get(unitType).getIsSuicideOnAttack()
            && UnitAttachment.get(unitType).getMovement(player) > 0)
        || UnitAttachment.get(unitType).getIsSuicideOnDefense();
  }

  public List<JBGPurchaseOption> getAllOptions() {
    final Set<JBGPurchaseOption> allOptions = new HashSet<>();
    allOptions.addAll(getLandOptions());
    allOptions.addAll(landZeroMoveOptions);
    allOptions.addAll(airOptions);
    allOptions.addAll(getSeaOptions());
    allOptions.addAll(aaOptions);
    allOptions.addAll(factoryOptions);
    allOptions.addAll(specialOptions);
    return new ArrayList<>(allOptions);
  }

  public List<JBGPurchaseOption> getLandOptions() {
    final Set<JBGPurchaseOption> landOptions = new HashSet<>();
    landOptions.addAll(landFodderOptions);
    landOptions.addAll(landAttackOptions);
    landOptions.addAll(landDefenseOptions);
    return new ArrayList<>(landOptions);
  }

  private List<JBGPurchaseOption> getSeaOptions() {
    final Set<JBGPurchaseOption> seaOptions = new HashSet<>();
    seaOptions.addAll(seaDefenseOptions);
    seaOptions.addAll(seaTransportOptions);
    seaOptions.addAll(seaCarrierOptions);
    seaOptions.addAll(seaSubOptions);
    return new ArrayList<>(seaOptions);
  }

  public List<JBGPurchaseOption> getLandFodderOptions() {
    return landFodderOptions;
  }

  public List<JBGPurchaseOption> getLandAttackOptions() {
    return landAttackOptions;
  }

  public List<JBGPurchaseOption> getLandDefenseOptions() {
    return landDefenseOptions;
  }

  public List<JBGPurchaseOption> getLandZeroMoveOptions() {
    return landZeroMoveOptions;
  }

  public List<JBGPurchaseOption> getAirOptions() {
    return airOptions;
  }

  public List<JBGPurchaseOption> getSeaDefenseOptions() {
    return seaDefenseOptions;
  }

  public List<JBGPurchaseOption> getSeaTransportOptions() {
    return seaTransportOptions;
  }

  public List<JBGPurchaseOption> getAaOptions() {
    return aaOptions;
  }

  public List<JBGPurchaseOption> getFactoryOptions() {
    return factoryOptions;
  }

  private static void logOptions(final List<JBGPurchaseOption> purchaseOptions, final String name) {
    final StringBuilder sb = new StringBuilder(name);
    for (final JBGPurchaseOption ppo : purchaseOptions) {
      sb.append(ppo.getUnitType().getName());
      sb.append(", ");
    }
    sb.delete(sb.length() - 2, sb.length());
    JBGLogger.debug(sb.toString());
  }
}
