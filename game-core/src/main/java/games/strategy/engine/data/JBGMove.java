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
public class JBGMove {
  private static final long serialVersionUID = -4598117601238030021L;

  @Getter @Setter private String departure;
  @Getter @Setter private String destination;
  @Getter @Setter private String troops;

  public JBGMove(final String dep, final String dest, final String t) {
    departure = dep;
    destination = dest;
    troops = t;
  }

  public JBGMove(final String ln) {
    if (ln.length() < 1)
      return;

    if (ln.indexOf(JBGConstants.HI_CSV_SEPARATOR) < 0)
      return;

    List<String> res = Arrays.asList(ln.split(Pattern.quote(JBGConstants.HI_CSV_SEPARATOR)));
    if (res.size() != 3)
      return;

    departure = res.get(0);
    destination = res.get(1);
    troops = res.get(2);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(
      " Dep: " +
      departure + 
      " Dest: " + destination +
      " Troops: " + troops
      );
    return sb.toString();
  }

}