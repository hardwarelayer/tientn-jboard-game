package games.strategy.engine.data;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.lang.System;
import java.lang.Comparable;
import lombok.Getter;
import lombok.Setter;
import java.util.regex.Pattern;

import games.strategy.engine.data.JBGConstants;

@Getter
@Setter
public class JBGBattleSimpleEvent implements Comparable<JBGBattleSimpleEvent> {
  private static final long serialVersionUID = -4598117601238030021L;

  @Getter @Setter private String location;
  @Getter @Setter private String attacker;
  @Setter private String defender;
  @Getter @Setter private String winner;
  @Getter @Setter private String attackerTroops;
  @Getter @Setter private String defenderTroops;
  @Getter @Setter private String remainderTroops;
  @Getter @Setter private String attackerCasualties;
  @Getter @Setter private String defenderCasualties;
  @Getter @Setter private Integer score;
  @Getter @Setter private int rounds;
  @Getter private boolean moveTaken;

  public JBGBattleSimpleEvent(final String loc) {
    if (loc.contains(JBGConstants.HI_TAG_START_BATTLE_LOC))
      location = loc.substring(JBGConstants.HI_TAG_START_BATTLE_LOC.length());
    else
      location = loc;
    //these values may absent
    attacker = "";
    attackerTroops = "";
    defender = "";
    defenderTroops = "";
    winner = "";
    remainderTroops = "";
    attackerCasualties = "";
    defenderCasualties = "";

    score = 0;
    rounds = 0;
    moveTaken = false;
  }

  @Override
  public int compareTo(JBGBattleSimpleEvent b) {
    return this.getScore().compareTo(b.getScore());
  }

  public String getDefender() {
    if (this.defender.equals("None")) {
      return "Neutral";
    }
    return this.defender;
  }

  public boolean isPlayerAttack(final String playerName) {
    if (playerName.equals(attacker)) return true;
    return false;
  }
  public boolean isPlayerWon(final String playerName) {
    if (playerName.equals(winner)) return true;
    return false;
  }
  public boolean isAttackerWon() {
    if (attacker.equals(winner)) return true;
    return false;
  }

  private List<String> parseCSVLine(final String ln, final int from) {
    if (ln.length() < 1 || from < 1)
      return null;

    String s = ln.substring(from);
    if (s.length() < 1)
      return null;

    if (s.indexOf(JBGConstants.HI_CSV_SEPARATOR) < 0)
      return null;

    List<String> res = Arrays.asList(s.split(Pattern.quote(JBGConstants.HI_CSV_SEPARATOR)));
    return res;
  }

  public void processLine(final String line) {
      if (line.contains(JBGConstants.HI_TAG_BATTLE_ATK)) {
        List<String> res = parseCSVLine(line, JBGConstants.HI_TAG_BATTLE_ATK.length());
        if (res.size() > 1) {
          attacker = res.get(0);
          attackerTroops = res.get(1);
        }
      }
      else if (line.contains(JBGConstants.HI_TAG_BATTLE_DEF)) {
        List<String> res = parseCSVLine(line, JBGConstants.HI_TAG_BATTLE_DEF.length());
        if (res.size() > 1) {
          defender = res.get(0);
          defenderTroops = res.get(1);
        }
      }
      else if (line.contains(JBGConstants.HI_TAG_BATTLE_REMM)) {
        remainderTroops = line.substring(JBGConstants.HI_TAG_BATTLE_REMM.length());
      }
      else if (line.contains(JBGConstants.HI_TAG_BATTLE_CASUALTIES)) {
        List<String> res = parseCSVLine(line, JBGConstants.HI_TAG_BATTLE_CASUALTIES.length());
        if (res.size() > 1) {
          //attacker/defender always come before casualties
          if (res.get(0).equals(attacker)) {
            attackerCasualties = res.get(1);
          }
          else {
            defenderCasualties = res.get(1);
          }
        }

      }

      if (line.contains(JBGConstants.HI_TAG_BATTLE_SUMM)) {
        List<String> res = parseCSVLine(line, JBGConstants.HI_TAG_BATTLE_SUMM.length());
        if (res.size() == 5) {
          if (winner.length() < 1 && res.get(0).length() > 0)
            winner = res.get(0);
          if (attacker.length() < 1 && res.get(1).length() > 0)
            attacker = res.get(1);
          if (defender.length() < 1 && res.get(2).length() > 0)
            defender = res.get(2);
          score = Integer.parseInt(res.get(3));
          rounds = Integer.parseInt(res.get(4));
        }
      }

  }

  private String extractSecondWord(final String title, final String sep, final String word) {
    if (title.length() < 1)
      return "";
    if (title.indexOf(sep) > 0) {
      if (title.substring(0, title.indexOf(sep)).trim().equals(word)) {
        return title.substring(title.indexOf(sep) + 1).trim();
      }
    }
    return "";
  }

  //when move and take province without battle (extract from Move)
  public void initMoveTake(final String atk, final String atkTroops) {
    attacker = atk;
    attackerTroops = atkTroops;
    defender = JBGConstants.HI_NONE_VAL;
    defenderTroops = JBGConstants.HI_NONE_VAL;
    attackerCasualties = JBGConstants.HI_NONE_VAL;
    defenderCasualties = JBGConstants.HI_NONE_VAL;
    remainderTroops = atkTroops;
    winner = atk;
    score = 0;
    rounds = 1;

    moveTaken = true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(
      "Battle: " + location);

      if (moveTaken) 
        sb.append("(Move Taken)");
      sb.append("\n" +
        "Attacker: " + attacker + ", Defender: " + defender + ", Winner: " + winner + ", Score: " + String.valueOf(score) + " Rounds:" + String.valueOf(rounds) + "\n" +
        "Attacker Troops: " + attackerTroops + "\n" +
        "Defender Troops: " + defenderTroops + "\n" +
        "Remainder Troops: " + remainderTroops + "\n" +
        "Attacker Casualties: " + attackerCasualties + "\n" +
        "Defender Casualties: " + defenderCasualties
      );
    return sb.toString();
  }

}