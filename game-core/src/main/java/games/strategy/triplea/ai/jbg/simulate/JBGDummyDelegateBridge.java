package games.strategy.triplea.ai.jbg.simulate;

import games.strategy.engine.data.Change;
import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.engine.display.IDisplay;
import games.strategy.engine.history.DelegateHistoryWriter;
import games.strategy.engine.history.IDelegateHistoryWriter;
import games.strategy.engine.player.Player;
import games.strategy.engine.random.IRandomStats.DiceType;
import games.strategy.engine.random.PlainRandomSource;
import games.strategy.triplea.ai.jbg.AbstractJBGAi;
import games.strategy.triplea.ui.display.HeadlessDisplay;
import java.util.Properties;
import org.triplea.sound.HeadlessSoundChannel;
import org.triplea.sound.ISound;

/**
 * Dummy implementation of {@link IDelegateBridge} used during a battle simulation to capture all
 * changes generated during the simulation.
 */
public class JBGDummyDelegateBridge implements IDelegateBridge {
  private final PlainRandomSource randomSource = new PlainRandomSource();
  private final IDisplay display = new HeadlessDisplay();
  private final ISound soundChannel = new HeadlessSoundChannel();
  private final GamePlayer player;
  private final AbstractJBGAi jbgAi;
  private final DelegateHistoryWriter writer = DelegateHistoryWriter.NO_OP_INSTANCE;
  private final GameData gameData;
  private final CompositeChange allChanges = new CompositeChange();

  public JBGDummyDelegateBridge(
      final AbstractJBGAi jbgAi, final GamePlayer player, final GameData data) {
    this.jbgAi = jbgAi;
    gameData = data;
    this.player = player;
  }

  @Override
  public GameData getData() {
    return gameData;
  }

  @Override
  public void leaveDelegateExecution() {}

  @Override
  public Properties getStepProperties() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getStepName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Player getRemotePlayer(final GamePlayer gamePlayer) {
    return jbgAi;
  }

  @Override
  public Player getRemotePlayer() {
    return jbgAi;
  }

  @Override
  public int[] getRandom(
      final int max,
      final int count,
      final GamePlayer player,
      final DiceType diceType,
      final String annotation) {
    return randomSource.getRandom(max, count, annotation);
  }

  @Override
  public int getRandom(
      final int max, final GamePlayer player, final DiceType diceType, final String annotation) {
    return randomSource.getRandom(max, annotation);
  }

  @Override
  public GamePlayer getGamePlayer() {
    return player;
  }

  @Override
  public IDelegateHistoryWriter getHistoryWriter() {
    return writer;
  }

  @Override
  public IDisplay getDisplayChannelBroadcaster() {
    return display;
  }

  @Override
  public ISound getSoundChannelBroadcaster() {
    return soundChannel;
  }

  @Override
  public void enterDelegateExecution() {}

  @Override
  public void addChange(final Change change) {
    allChanges.add(change);
    gameData.performChange(change);
  }

  @Override
  public void stopGameSequence() {}
}
