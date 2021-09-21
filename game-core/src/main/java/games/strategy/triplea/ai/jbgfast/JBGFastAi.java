package games.strategy.triplea.ai.jbgfast;

import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.startup.ui.PlayerType;
import games.strategy.triplea.ai.jbg.AbstractJBGAi;
import games.strategy.triplea.ai.jbg.JBGData;

/** JBGFast AI. */
public class JBGFastAi extends AbstractJBGAi {

  public JBGFastAi(final String name) {
    this(name, new JBGData());
  }

  private JBGFastAi(final String name, final JBGData jbgData) {
    super(name, new JBGFastOddsEstimator(jbgData), jbgData);
  }

  @Override
  public PlayerType getPlayerType() {
    return PlayerType.JBG_FAST_AI;
  }

  @Override
  protected void prepareData(final GameData data) {}
}
