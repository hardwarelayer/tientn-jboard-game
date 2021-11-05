package games.strategy.triplea.ai.jbg;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import games.strategy.engine.data.JBGConstants;

@Getter
@Setter
public class JBGGamePlayerExtInfo implements Serializable {
  @Getter @Setter private String playerName;
  @Getter @Setter private int tributeAmount = 0;
  @Getter @Setter private String lastTributePlayerName;
  @Getter @Setter private int stockingTurnCount = 0;
  @Getter private int stockingAmount = 0;
  public void setStockingAmount(int val) {
    stockingAmount = val;
    if (val < 1) stockingTurnCount = 0;
  }
  public void addStockingAmount(int val) {
    stockingAmount += val;
    stockingTurnCount++;
  }
  @Setter private int aggressiveTurnMax = 0;
  public int getAggressiveTurnMax() {
    if (this.aggressiveTurnMax > 4) {
      this.aggressiveTurnMax = 4; //minimum aggressive turn is 4
    }
    return this.aggressiveTurnMax;
  }
  @Getter @Setter private int aggressiveTurnCount = 0;
  private void initAggressiveMode() {
    if (stockingTurnCount < 1) stockingTurnCount = 1;
    aggressiveTurnMax = stockingTurnCount;
    if (aggressiveTurnMax < JBGConstants.AI_MINIMUM_AGGRESSIVE_TURNS)
      aggressiveTurnMax = JBGConstants.AI_MINIMUM_AGGRESSIVE_TURNS;
    aggressiveTurnCount = 0;
    setStockingAmount(0);
  }
  public void continueOrStopAggressive() {
   if (aggressiveTurnCount >= aggressiveTurnMax) {
      aggressiveTurnCount = 0;
      aggressiveTurnMax = 0;
      setStockingAmount(0);
      defensiveStance = true;
    } else { 
      aggressiveTurnCount++; 
    }
  }
  @Getter private boolean defensiveStance = true;
  public void setDefensiveStance(boolean val) { 
    defensiveStance = val; 
    if (!val) {
     initAggressiveMode(); 
    }
  }

  public JBGGamePlayerExtInfo(final String name) {
    playerName = name;
    tributeAmount = 0;
    lastTributePlayerName = null;
    stockingTurnCount = 0;
    stockingAmount = 0;
    aggressiveTurnCount = 0;
    aggressiveTurnMax = 0;
    defensiveStance = true;
  }
}