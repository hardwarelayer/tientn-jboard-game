package games.strategy.triplea.ai.fast;

import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.startup.ui.PlayerType;
import games.strategy.triplea.ai.pro.AbstractProAi;
import games.strategy.triplea.ai.pro.ProData;

/** Fast AI. */
public class FastAi extends AbstractProAi {

  public FastAi(final String name) {
    this(name, new ProData());
  }

  private FastAi(final String name, final ProData proData) {
    super(name, new FastOddsEstimator(proData), proData);
  }

  @Override
  public PlayerType getPlayerType() {
    return PlayerType.FAST_AI;
  }

  @Override
  protected void prepareData(final GameData data) {}
}
