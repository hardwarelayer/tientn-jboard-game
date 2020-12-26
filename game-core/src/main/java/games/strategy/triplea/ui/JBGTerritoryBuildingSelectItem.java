package games.strategy.triplea.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

public class JBGTerritoryBuildingSelectItem {
  @Getter @Setter private int id;
  @Getter @Setter private int price;
  @Getter @Setter private String text;
  @Getter @Setter private int type;
  public JBGTerritoryBuildingSelectItem(final int id, final String text, final int price, final int type) {
    this.id = id;
    this.price = price;
    this.text = text;
    this.type = type;
  }

  @Override
  public String toString() {
    return new StringBuilder(this.text + " (" + String.valueOf(price) + " coin)").toString();
  }
}