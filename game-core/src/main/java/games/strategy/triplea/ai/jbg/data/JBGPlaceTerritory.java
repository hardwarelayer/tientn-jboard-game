package games.strategy.triplea.ai.jbg.data;

import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** The result of an AI placement analysis for a single territory. */
@EqualsAndHashCode(of = "territory")
@Getter
@Setter
public class JBGPlaceTerritory {
  private final Territory territory;
  private final List<Unit> placeUnits = new ArrayList<>();
  private List<Unit> defendingUnits = new ArrayList<>();
  private JBGBattleResult minBattleResult = new JBGBattleResult();
  private double defenseValue = 0;
  private double strategicValue = 0;
  private boolean canHold = true;

  JBGPlaceTerritory(final Territory territory) {
    this.territory = territory;
  }

  @Override
  public String toString() {
    return territory.toString();
  }
}
