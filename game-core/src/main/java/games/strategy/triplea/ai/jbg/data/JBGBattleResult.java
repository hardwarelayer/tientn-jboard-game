package games.strategy.triplea.ai.jbg.data;

import games.strategy.engine.data.Unit;
import java.util.ArrayList;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/** The result of an AI battle analysis. */
@Getter
@ToString
@AllArgsConstructor
public class JBGBattleResult {

  private final double winPercentage;
  private final double tuvSwing;
  private final boolean hasLandUnitRemaining;
  private final Collection<Unit> averageAttackersRemaining;
  private final Collection<Unit> averageDefendersRemaining;
  private final double battleRounds;

  public JBGBattleResult() {
    this(0, -1, false, new ArrayList<>(), new ArrayList<>(), 0);
  }
}
