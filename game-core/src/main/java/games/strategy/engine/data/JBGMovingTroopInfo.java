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
public class JBGMovingTroopInfo {
  private static final long serialVersionUID = -4598117601238030021L;

  @Getter @Setter private int count;
  @Getter @Setter private String name;

  public JBGMovingTroopInfo(final int ttl, final String s) {
    count = ttl;
    name = s;
  }

  public JBGMovingTroopInfo(final String ln) {
    if (ln.length() < 1)
      return;

    String sFirstWord = getFirstWord(ln);
    if (sFirstWord != null) {
      String sSecondWord = getAfterFirstWord(ln);
      if (sSecondWord != null) {
        count = 0;
        try {
          count = Integer.parseInt(sFirstWord);
        }
        catch (Exception ex) {

        }
        name = sSecondWord;
      }
    }
  }

  private String getFirstWord(final String s) {
    if (!s.contains(" ")) return null;
    if (s.indexOf(" ") < 1) return null;

    return s.substring(0, s.indexOf(" ") - 1);
  } 

  private String getAfterFirstWord(final String s) {
    if (!s.contains(" ")) return null;
    if (s.indexOf(" ") < 1) return null;

    return s.substring(s.indexOf(" ")).trim();
  } 


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(
      " total: " + String.valueOf(count) +
      " Name: " + name
      );
    return sb.toString();
  }

}