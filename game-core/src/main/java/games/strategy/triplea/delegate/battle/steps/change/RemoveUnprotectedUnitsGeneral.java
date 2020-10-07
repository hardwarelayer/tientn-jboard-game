package games.strategy.triplea.delegate.battle.steps.change;

import games.strategy.triplea.delegate.battle.BattleActions;
import games.strategy.triplea.delegate.battle.BattleState;
import java.util.List;

public class RemoveUnprotectedUnitsGeneral extends RemoveUnprotectedUnits {
  private static final long serialVersionUID = 5004515340964828564L;

  public RemoveUnprotectedUnitsGeneral(
      final BattleState battleState, final BattleActions battleActions) {
    super(battleState, battleActions);
  }

  @Override
  public List<String> getNames() {
    return List.of();
  }

  @Override
  public Order getOrder() {
    return Order.REMOVE_UNPROTECTED_UNITS_GENERAL;
  }
}
