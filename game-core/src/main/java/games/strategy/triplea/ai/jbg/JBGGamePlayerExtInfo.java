package games.strategy.triplea.ai.jbg;

public class JBGGamePlayerExtInfo {
  private String playerName;
  private int stockingTurnCount = 0;
  public int getStockingTurnCount() { return stockingTurnCount; }
  private int stockingAmount = 0;
  public void setStockingAmount(int val) {
    stockingAmount = val;
    if (val < 1) stockingTurnCount = 0;
  }
  public void addStockingAmount(int val) {
    stockingAmount += val;
    stockingTurnCount++;
  }
  public int getStockingAmount() { return stockingAmount; }
  private int aggressiveTurnMax = 0;
  private int aggressiveTurnCount = 0;
  public int getAggressiveTurnMax() {return aggressiveTurnMax;}
  public int getAggressiveTurnCount() {return aggressiveTurnCount;}
  private void initAggressiveMode() {
    if (stockingTurnCount < 1) stockingTurnCount = 1;
    aggressiveTurnMax = stockingTurnCount;
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
  private boolean defensiveStance = true;
  public void setDefensiveStance(boolean val) { 
    defensiveStance = val; 
    if (!val) {
     initAggressiveMode(); 
    }
  }
  public boolean getDefensiveStance() { return defensiveStance; }

  public JBGGamePlayerExtInfo(final String name) {
    playerName = name;
    stockingTurnCount = 0;
    stockingAmount = 0;
    aggressiveTurnCount = 0;
    aggressiveTurnMax = 0;
    defensiveStance = true;
  }
}