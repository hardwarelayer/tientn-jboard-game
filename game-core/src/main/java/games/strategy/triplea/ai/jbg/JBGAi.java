package games.strategy.triplea.ai.jbg;

import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.startup.ui.PlayerType;
import games.strategy.triplea.ai.jbg.logging.JBGLogUi;
import games.strategy.triplea.odds.calculator.ConcurrentBattleCalculator;

public class JBGAi extends AbstractJBGAi {
  // Odds calculator
  private static final ConcurrentBattleCalculator concurrentCalc = new ConcurrentBattleCalculator();

  public JBGAi(final String name) {
    super(name, concurrentCalc, new JBGData());
  }

  public static void gameOverClearCache() {
    // Are static, clear so that we don't keep the data around after a game is exited
    concurrentCalc.setGameData(null);
    JBGLogUi.clearCachedInstances();
  }

  @Override
  public PlayerType getPlayerType() {
    return PlayerType.JBG_AI;
  }

  @Override
  public void stopGame() {
    super.stopGame(); // absolutely MUST call super.stopGame() first
    concurrentCalc.cancel();
  }

  @Override
  protected void prepareData(final GameData data) {
    concurrentCalc.setGameData(data);
  }
}
