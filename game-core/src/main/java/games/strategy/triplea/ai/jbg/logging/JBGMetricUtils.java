package games.strategy.triplea.ai.jbg.logging;

import games.strategy.engine.data.ProductionRule;
import org.triplea.java.collections.IntegerMap;

/** JBG AI metrics. */
public final class JBGMetricUtils {
  private static final IntegerMap<ProductionRule> totalPurchaseMap = new IntegerMap<>();

  private JBGMetricUtils() {}

  public static void collectPurchaseStats(final IntegerMap<ProductionRule> purchaseMap) {
    totalPurchaseMap.add(purchaseMap);
    JBGLogger.debug(totalPurchaseMap.toString());
  }
}
