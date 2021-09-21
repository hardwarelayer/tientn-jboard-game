package games.strategy.triplea.ai.jbg.data;

import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.ai.jbg.JBGData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/** The result of an AI movement analysis for its own possible moves. */
@Getter
public class JBGMyMoveOptions {

  private final Map<Territory, JBGTerritory> territoryMap;
  private final Map<Unit, Set<Territory>> unitMoveMap;
  private final Map<Unit, Set<Territory>> transportMoveMap;
  private final Map<Unit, Set<Territory>> bombardMap;
  private final List<JBGTransport> transportList;
  private final Map<Unit, Set<Territory>> bomberMoveMap;

  JBGMyMoveOptions() {
    territoryMap = new HashMap<>();
    unitMoveMap = new HashMap<>();
    transportMoveMap = new HashMap<>();
    bombardMap = new HashMap<>();
    transportList = new ArrayList<>();
    bomberMoveMap = new HashMap<>();
  }

  JBGMyMoveOptions(final JBGMyMoveOptions myMoveOptions, final JBGData jbgData) {
    this();
    for (final Territory t : myMoveOptions.territoryMap.keySet()) {
      territoryMap.put(t, new JBGTerritory(myMoveOptions.territoryMap.get(t), jbgData));
    }
    unitMoveMap.putAll(myMoveOptions.unitMoveMap);
    transportMoveMap.putAll(myMoveOptions.transportMoveMap);
    bombardMap.putAll(myMoveOptions.bombardMap);
    transportList.addAll(myMoveOptions.transportList);
    bomberMoveMap.putAll(myMoveOptions.bomberMoveMap);
  }
}
