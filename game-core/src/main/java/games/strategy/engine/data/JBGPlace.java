package games.strategy.engine.data;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.lang.System;
import lombok.Getter;
import lombok.Setter;
import java.util.regex.Pattern;
import games.strategy.engine.data.JBGConstants;

@Getter
@Setter
public class JBGPlace {
  private static final long serialVersionUID = -4598117601238030021L;

  @Getter @Setter private String location;
  @Getter @Setter private String troops;

  public JBGPlace(final String loc, final String t) {
    location = loc;
    troops = t;
  }

  public JBGPlace(final String ln) {
    if (ln.length() < 1)
      return;

    if (ln.indexOf(JBGConstants.HI_PLACE_SEPARATOR) < 0)
      return;

    List<String> res = Arrays.asList(ln.split(JBGConstants.HI_PLACE_SEPARATOR));
    if (res.size() != 2)
      return;

    location = res.get(1);
    troops = res.get(0);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(
      " Location: " + location + 
      " Troops: " + troops
      );
    return sb.toString();
  }

}