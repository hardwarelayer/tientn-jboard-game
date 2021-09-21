package games.strategy.triplea.ai.jbg.data;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.ai.jbg.JBGData;
import games.strategy.triplea.ai.jbg.util.JBGBattleUtils;
import games.strategy.triplea.ai.jbg.util.JBGUtils;
import games.strategy.triplea.delegate.Matches;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The result of an AI movement analysis for another player's possible moves. */
public class JBGOtherMoveOptions {

  private final Map<Territory, JBGTerritory> maxMoveMap;
  private final Map<Territory, List<JBGTerritory>> moveMaps;

  public JBGOtherMoveOptions() {
    maxMoveMap = new HashMap<>();
    moveMaps = new HashMap<>();
  }

  public JBGOtherMoveOptions(
      final JBGData jbgData,
      final List<Map<Territory, JBGTerritory>> moveMapList,
      final GamePlayer player,
      final boolean isAttacker) {
    maxMoveMap = newMaxMoveMap(jbgData, moveMapList, player, isAttacker);
    moveMaps = newMoveMaps(moveMapList);
  }

  public JBGTerritory getMax(final Territory t) {
    return maxMoveMap.get(t);
  }

  List<JBGTerritory> getAll(final Territory t) {
    final List<JBGTerritory> result = moveMaps.get(t);
    if (result != null) {
      return result;
    }
    return new ArrayList<>();
  }

  @Override
  public String toString() {
    return maxMoveMap.toString();
  }

  private static Map<Territory, JBGTerritory> newMaxMoveMap(
      final JBGData jbgData,
      final List<Map<Territory, JBGTerritory>> moveMaps,
      final GamePlayer player,
      final boolean isAttacker) {

    final Map<Territory, JBGTerritory> result = new HashMap<>();
    final List<GamePlayer> players = JBGUtils.getOtherPlayersInTurnOrder(player);
    for (final Map<Territory, JBGTerritory> moveMap : moveMaps) {
      for (final Territory t : moveMap.keySet()) {

        // Get current player
        final Set<Unit> currentUnits = new HashSet<>(moveMap.get(t).getMaxUnits());
        currentUnits.addAll(moveMap.get(t).getMaxAmphibUnits());
        final GamePlayer movePlayer;
        if (!currentUnits.isEmpty()) {
          movePlayer = currentUnits.iterator().next().getOwner();
        } else {
          continue;
        }

        // Skip if checking allied moves and their turn doesn't come before territory owner's
        if (jbgData.getData().getRelationshipTracker().isAllied(player, movePlayer)
            && !JBGUtils.isPlayersTurnFirst(players, movePlayer, t.getOwner())) {
          continue;
        }

        // Add to max move map if its empty or its strength is greater than existing
        if (!result.containsKey(t)) {
          result.put(t, moveMap.get(t));
        } else {
          final Set<Unit> maxUnits = new HashSet<>(result.get(t).getMaxUnits());
          maxUnits.addAll(result.get(t).getMaxAmphibUnits());
          double maxStrength = 0;
          if (!maxUnits.isEmpty()) {
            maxStrength =
                JBGBattleUtils.estimateStrength(
                    jbgData, t, new ArrayList<>(maxUnits), new ArrayList<>(), isAttacker);
          }
          final double currentStrength =
              JBGBattleUtils.estimateStrength(
                  jbgData, t, new ArrayList<>(currentUnits), new ArrayList<>(), isAttacker);
          final boolean currentHasLandUnits = currentUnits.stream().anyMatch(Matches.unitIsLand());
          final boolean maxHasLandUnits = maxUnits.stream().anyMatch(Matches.unitIsLand());
          if ((currentHasLandUnits
                  && ((!maxHasLandUnits && !t.isWater()) || currentStrength > maxStrength))
              || ((!maxHasLandUnits || t.isWater()) && currentStrength > maxStrength)) {
            result.put(t, moveMap.get(t));
          }
        }
      }
    }
    return result;
  }

  private static Map<Territory, List<JBGTerritory>> newMoveMaps(
      final List<Map<Territory, JBGTerritory>> moveMapList) {

    final Map<Territory, List<JBGTerritory>> result = new HashMap<>();
    for (final Map<Territory, JBGTerritory> moveMap : moveMapList) {
      for (final Territory t : moveMap.keySet()) {
        if (!result.containsKey(t)) {
          final List<JBGTerritory> list = new ArrayList<>();
          list.add(moveMap.get(t));
          result.put(t, list);
        } else {
          result.get(t).add(moveMap.get(t));
        }
      }
    }
    return result;
  }
}
