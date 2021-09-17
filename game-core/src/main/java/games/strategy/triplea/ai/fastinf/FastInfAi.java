package games.strategy.triplea.ai.fastinf;

import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.startup.ui.PlayerType;
import games.strategy.triplea.ai.pro.AbstractProAi;
import games.strategy.triplea.ai.pro.ProData;

/** Fast Infantry AI. */
public class FastInfAi extends AbstractProAi {

  public FastInfAi(final String name) {
    this(name, new ProData());
  }

  private FastInfAi(final String name, final ProData proData) {
    super(name, new FastInfOddsEstimator(proData), proData);
  }

  @Override
  public PlayerType getPlayerType() {
    return PlayerType.FAST_INF_AI;
  }

  @Override
  protected void prepareData(final GameData data) {}
}
