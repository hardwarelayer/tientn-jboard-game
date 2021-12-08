package games.strategy.engine.data;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.lang.System;
import lombok.Getter;
import lombok.Setter;
import games.strategy.engine.data.JBGMovingTroopInfo;
import games.strategy.engine.data.JBGConstants;

@Getter
@Setter
public class JBGAnalyzableTurnEntry {
  private static final long serialVersionUID = -4598117601238030021L;

  @Getter @Setter private String player;

  @Getter private boolean justMobilization = false;
  private List<JBGBattleSimpleEvent> lstBattles = null;
  private List<JBGMove> lstCombatMoves = null;
  private List<JBGPlace> lstPlaces =  null;
  private List<JBGMove> lstNormalMoves =  null;
  private List<String> lstPurchases =  null;

  public JBGAnalyzableTurnEntry(final String player) {
    this.player = player;

    lstBattles = new ArrayList<>();
    lstCombatMoves = new ArrayList<>();
    lstPlaces = new ArrayList<>();
    lstNormalMoves = new ArrayList<>();
    lstPurchases = new ArrayList<>();
  }

  public void addBattle(JBGBattleSimpleEvent e) {
    lstBattles.add(e);
  }

  public void addCombatMove(JBGMove m) {
    lstCombatMoves.add(m);
  }

  public void addNormalMove(JBGMove m) {
    lstNormalMoves.add(m);
  }

  public void addPlace(final String p) {
    JBGPlace place = new JBGPlace(p);
    lstPlaces.add(place);
  }

  public void addPurchase(final String p) {
    String tmpStr = null;
    if (p.contains(JBGConstants.HI_TAG_MOBILIZATION_COST)) {
      tmpStr = p.replace(JBGConstants.HI_TAG_MOBILIZATION_COST, "");
      this.justMobilization = true;
    }
    else {
      tmpStr = p;
    }
    lstPurchases.add(tmpStr);
  }

  public boolean isPlayer(final String p) {
    return p.equals(player);
  }

  private List<String> getBattleRegionNames() {
    List<String> lstRegionNames = new ArrayList<>();
    for (JBGBattleSimpleEvent e: lstBattles) {
      String regionName = e.getLocation();
      if (!lstRegionNames.contains(regionName)) {
        lstRegionNames.add(regionName);
      }
    }
    return lstRegionNames;
  }

  private List<String> getTargetGroups() {
    Map<String, Integer> lstTargetGroups = new HashMap<>();
    for (JBGBattleSimpleEvent e: lstBattles) {
      String def = e.getDefender();
      if (!lstTargetGroups.containsKey(def)) {
        lstTargetGroups.put(def, 1);
      }
      else {
        lstTargetGroups.put(def, lstTargetGroups.get(def)+1);
      }
    }

    //only use target from 2 occurences 
    List<String> lstRes = new ArrayList<>();
    if (lstTargetGroups.size() > 0) {

      for (Map.Entry<String, Integer> entry : lstTargetGroups.entrySet()) {
         if (entry.getValue() > 1) {
          lstRes.add(entry.getKey());
         }
       }
    }
    return lstRes;
  }

  private JBGBattleSimpleEvent getBattleOfRegion(final String regionName) {
    for (JBGBattleSimpleEvent e: lstBattles) {
      if (e.getLocation().equals(regionName)) {
        return e;
      }
    }
    return null;
  }

  private List<JBGMovingTroopInfo> textToUnitInfoList(final String troops) {
    List<JBGMovingTroopInfo> lstUnitInfos = new ArrayList<>();
    String csvTroops = troops.replaceAll(" and ", "~").replaceAll(",", "~");
    String[] tmpUnits = csvTroops.split("~", 50);
    for (String u: tmpUnits) {
      u = u.trim();
      JBGMovingTroopInfo unit = new JBGMovingTroopInfo(u);
      lstUnitInfos.add(unit);
    }
    return lstUnitInfos;
  }

  private boolean isAirUnit(final String s) {
    if (s == null) return false;
    String sl = s.toLowerCase();
    if (sl.indexOf("fighter") >= 0 || 
      sl.indexOf("bomber") >= 0 ||
      sl.indexOf("stuka") >= 0
      )
      return true;
    return false;
  }
  private boolean troopIsAirforce(final String troops) {
    //precheck for resource saving on process
    if (!isAirUnit(troops)) return false;

    //if precheck ok, analyze more
    List<JBGMovingTroopInfo> lstUnits = textToUnitInfoList(troops);

    for (JBGMovingTroopInfo unit: lstUnits) {
      if (!isAirUnit(unit.getName())) {
        return false;
      }
    }
    return true;
  }

  private String getBoldText(final String s) {
    return new StringBuilder("<b>" + s + "</b>").toString();
  }

  private String writeBattleEventStory(JBGBattleSimpleEvent battleEvent, boolean isHtml) {
      StringBuilder sb = new StringBuilder();

      String lineBreak = isHtml?"<br/>":"\n";
      String regionName = battleEvent.getLocation();
      boolean bIsSeaBattle = battleEvent.isSeaBattle();
      boolean bIsPlayerVictory = false;
      boolean bIsAttackerWon = false;
      boolean bIsPlayerAttack = false;

      boolean isAirforceOnly = troopIsAirforce(battleEvent.getAttackerTroops());

      if (battleEvent.isPlayerWon(player)) {
        bIsPlayerVictory = true;
      }
      if (battleEvent.isAttackerWon()) {
        bIsAttackerWon = true;
      }
      if (battleEvent.isPlayerAttack(player)) {
        bIsPlayerAttack = true;
      }
      //battle title

      sb.append("<u>");
      if (bIsPlayerVictory) {
        if (bIsAttackerWon) {
          if (isAirforceOnly) {
            //use combined air operation here because sometime it's wrong for OP with air cover (may be on load game), not AO
            //so use this to make it usable for both case
            if (!bIsSeaBattle)
              sb.append(JBGConstants.JBGTURN_NEWS_AIROP_IMG);
            else
              sb.append(JBGConstants.JBGTURN_NEWS_SEA_AIROP_IMG);

            sb.append("Combined air operation over " + battleEvent.getDefender() + " in " + regionName);
          }
          else {
            if (bIsSeaBattle)
              sb.append(JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY_IMG);
            sb.append("Victory over " + battleEvent.getDefender() + " in " + regionName);
          }
        }
        else {
          if (bIsSeaBattle)
            sb.append(JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY_IMG);
          sb.append("Successfully defended " + regionName + " against " + battleEvent.getAttacker());
        }
      }
      else {
        //not a player victory, another player attacks this player
        if (bIsAttackerWon) {
          sb.append("Lost " + regionName + " to " + battleEvent.getAttacker());
        }
        else {
          if (isAirforceOnly) {
            if (!bIsSeaBattle)
              sb.append(JBGConstants.JBGTURN_NEWS_AIRBOMB_IMG);
            else 
              sb.append(JBGConstants.JBGTURN_NEWS_SEA_AIRBOMB_IMG);
            sb.append(getBoldText(player) + " bombed " + regionName + " of " + battleEvent.getDefender());
          }
          else {
            if (bIsSeaBattle)
              sb.append(JBGConstants.JBGTURN_NEWS_SEAOP_FAILURE_IMG);
            sb.append("Failed to take " + regionName + " from " + battleEvent.getDefender());
          }
        }
      }
      sb.append("</u>");

      //combat moves
      sb.append(lineBreak);

      StringBuilder sbMoveContent = new StringBuilder();
      boolean bFirstCombatMove = true;
      boolean bIsAmphibLanding = false;
      for (JBGMove move: lstCombatMoves) {

        if (move == null) continue;
        if (move.getDestination() == null) continue;

        if (move.getDestination().equals(regionName)) {
          boolean isSquaronMove = troopIsAirforce(move.getTroops());
          if (bFirstCombatMove) {

            if (isSquaronMove)
              sbMoveContent.append(getBoldText(player) + " squarons " + move.getTroops() + " took off from " + move.getDeparture());
            else {
              if (move.isFromSea())
                bIsAmphibLanding = true;
              sbMoveContent.append(getBoldText(player) + " mobilized " + move.getTroops() + " from " + move.getDeparture());              
            }

            bFirstCombatMove = false;
          }
          else {

            if (isSquaronMove)
              sbMoveContent.append(" and squarons " + move.getTroops() + " took off from " + move.getDeparture());
            else {
              if (move.isFromSea())
                bIsAmphibLanding = true;
              sbMoveContent.append(" and " + move.getTroops() + " from " + move.getDeparture());
            }

          }
        }
      }

      if (!bIsSeaBattle && bIsAmphibLanding)
        sb.append(JBGConstants.JBGTURN_NEWS_AMPHIB_IMG);
      sb.append(sbMoveContent.toString());

      if (!bFirstCombatMove) //has at least 1 move
        sb.append(lineBreak);

      //participated troops
      sb.append("<i>Order of Battle:</i>");
      sb.append(lineBreak);
      if (isAirforceOnly)
        sb.append(battleEvent.getAttacker() + " squarons of " + battleEvent.getAttackerTroops() + " attacked ");
      else
        sb.append(battleEvent.getAttacker() + " attacked with " + battleEvent.getAttackerTroops());

      if (battleEvent.isMoveTaken() && !isAirforceOnly) {
        sb.append(", and successfully captured the region ");
      }
      else {
        //order of battle for defender
        sb.append(", against " + battleEvent.getDefenderTroops() + " of " + battleEvent.getDefender());
        sb.append(lineBreak);
        //casualties of both sides
        if (bIsPlayerVictory) {
          if (bIsPlayerAttack) {
            sb.append(getBoldText(player) + " successfully crushed " + battleEvent.getDefenderCasualties());
            if (battleEvent.getAttackerCasualties().length() < 3)
              sb.append(", without any casualty.");
            else
              sb.append(", with the cost of " + battleEvent.getAttackerCasualties());
          }
          else {
            sb.append(battleEvent.getAttacker() + " lost " + battleEvent.getAttackerCasualties());
            if (battleEvent.getDefenderCasualties().length() < 3)
              sb.append(", but failed to score any achievement!");
            else
              if (battleEvent.getDefenderCasualties().length() > 2)
                sb.append(", destroyed " + getBoldText(player) + "'s " + battleEvent.getDefenderCasualties());
              else
                sb.append(", but could not score any hit.");
          }
        }
        else {
          //failure of the player
          if (bIsPlayerAttack) {
            //consider defender casualties
            if (battleEvent.getDefenderCasualties().length() > 2)
              //consider attacker casualties, too ????
              if (battleEvent.getAttackerCasualties().length() > 2)
                sb.append(getBoldText(player) + " lost " + battleEvent.getAttackerCasualties() + ", destroyed " + battleEvent.getDefender() + "'s " + battleEvent.getDefenderCasualties());
              else
                sb.append(getBoldText(player) + " destroyed " + battleEvent.getDefender() + "'s " + battleEvent.getDefenderCasualties() + " with minimal casualties.");
            else
              if (battleEvent.getAttackerCasualties().length() > 2)
                sb.append(getBoldText(player) + " lost " + battleEvent.getAttackerCasualties() + ", but could not score any hit on " + battleEvent.getDefender());
              else
                sb.append("The two armies only had minor encounters with minimal casualties. ");
          }
          else {
            if (battleEvent.getAttackerCasualties().length() > 2)
              if (battleEvent.getDefenderCasualties().length() > 2)
                sb.append(getBoldText(player) + " lost  " + battleEvent.getDefenderCasualties() + ", and caused " + battleEvent.getAttacker() + "'s losses " + battleEvent.getAttackerCasualties());
              else
                sb.append(getBoldText(player) + " lost  " + battleEvent.getDefenderCasualties() + ", but could not score any hit on " + battleEvent.getAttacker() );
            else
              if (battleEvent.getDefenderCasualties().length() > 2)
                sb.append(getBoldText(player) + " lost  " + battleEvent.getDefenderCasualties() + ", but could not score any hit on " + battleEvent.getAttacker());
              else
                sb.append("The two armies had only minor clashes without loss. ");

          }
        }
        sb.append(lineBreak);
      }

      //characteristic of the battle
      //this is in same sentence as the previous one
      int iRounds = battleEvent.getRounds();
      if (!isAirforceOnly) {
        if (iRounds < 2) {
          sb.append("In a blitz movement, ");
        }
        else if (iRounds < 4) {
          sb.append("After a week of moderate clashes, ");
        }
        else {
          sb.append("After a month of fierce fighting, ");
        }        
      }
      else {
        if (iRounds < 2) {
          sb.append("Conducted random air attacks, ");
        }
        else if (iRounds < 4) {
          sb.append("After a week of moderate air attacks, ");
        }
        else {
          sb.append("After a month of carpet bombings, ");
        }        
      }
      //no new line
      int iScore = battleEvent.getScore();
      if (iScore < 1) {
        if (battleEvent.isAttackerWon()) {
          if (battleEvent.getAttackerCasualties().length() > 2)
            if (iRounds < 2)
              sb.append(getBoldText(player) + " quickly seized the territory. ");
            else
              sb.append(getBoldText(player) + " got a pyrrhic victory. ");
          else
            sb.append(getBoldText(player) + " annexed the territory. ");          
        }
        else {
          if (!isAirforceOnly)
            sb.append(getBoldText(player) + " got a sounding defeat. ");
          else //airforce can't win alone
            sb.append(getBoldText(player) + " stopped air operation.");
        }
      }
      else if (iScore < 5) {
        sb.append(getBoldText(player) + " won. ");
      }
      else if (iScore < 25) {
        sb.append(getBoldText(player) + " gained a decisive victory. ");
      }
      else {
        sb.append(getBoldText(player) + " gained a strategic victory. ");
      }
      sb.append(lineBreak);

      return sb.toString();
  }

  public List<String> writeBattlesHeadlines(boolean isHtml) {
    List<String> lstBattleGroupInfos = new ArrayList<>();
    List<String> lstBattlesInfos = new ArrayList<>();

    List<JBGBattleSimpleEvent> lstUsedBattles = new ArrayList<>();
    List<JBGBattleSimpleEvent> lstRemainingBattles = new ArrayList<>();

    //1. Handling of small separated battles
    List<String> lstTargetGroups = getTargetGroups();


    if (lstTargetGroups.size() < 1 && lstBattles.size() > 0) {
      //not groupable battles
      StringBuilder limitedOperations = new StringBuilder();
      StringBuilder attackAreas = new StringBuilder();
      StringBuilder successAreas = new StringBuilder();
      int iSuccessCount = 0;

      limitedOperations.append(player + "'s operation");
      if (lstBattles.size() > 1)
        limitedOperations.append("s:");
      else
        limitedOperations.append(":");


      boolean onlyAirOperation = true;
      //we only list region names
      for (JBGBattleSimpleEvent e: lstBattles) {
          attackAreas.append( e.getLocation() + ", " );
          if (e.isAttackerWon()) {
            successAreas.append( e.getLocation() + ", ");
            iSuccessCount++;
          }
          if (!troopIsAirforce( e.getAttackerTroops())) {
            onlyAirOperation = false;
          }
      }

      if (attackAreas.length() > 2) {
        if (successAreas.length() > 2) {

          if (successAreas.length() == attackAreas.length()) {
            attackAreas.setLength(attackAreas.length()-2);
            if (onlyAirOperation)
              limitedOperations.append( player + " successfully bombed " + attackAreas.toString() );
            else
              limitedOperations.append( player + " attacked and captured " + attackAreas.toString() );
          }
          else {
            if (onlyAirOperation) {
              limitedOperations.append( player + " bombing " + attackAreas.toString() );
              successAreas.setLength(successAreas.length()-2); //remove last ", "
              limitedOperations.append( " and successfully swept " + ((iSuccessCount>1)?"s":"") + "of " + successAreas.toString() + "." );
            }
            else {
              limitedOperations.append( player + " attacked " + attackAreas.toString() );
              successAreas.setLength(successAreas.length()-2); //remove last ", "
              limitedOperations.append( " and won the battle" + ((iSuccessCount>1)?"s":"") + "of " + successAreas.toString() + "." );
            }
          }
        }
        else {
          if (onlyAirOperation) {
            //use combined air operation here because sometime it's wrong for OP with air cover (may be on load game), not AO
            //so use this to make it usable for both case
            attackAreas.setLength(attackAreas.length()-2);
            limitedOperations.append( " by airforce against " + attackAreas.toString() );
          }
          else
            limitedOperations.append( " against " + attackAreas.toString() + ", but lost the battle!" );
        }

        if (isHtml)
          limitedOperations.append("<br/>");
        else
          limitedOperations.append("\n");
      }
      lstBattleGroupInfos.add(limitedOperations.toString());

      //it seems, defending battle is not in a player's turn history data, so I tried to skip processing here
      //will come back later

      return lstBattleGroupInfos;

    }

    //2. handling groupable battles
    //has at least one group of target, or more
    for (String target: lstTargetGroups) {

      StringBuilder targetStory = new StringBuilder();
      StringBuilder attackAreas = new StringBuilder();
      StringBuilder successAreas = new StringBuilder();
      int iGroupSuccessCount = 0;
      int iTotalAttackAreas = 0;
      int iTotalSuccessAreas = 0;

      //targetStory.append(player + "'s Operation Against " + target + ":\n");
      //we only list region names

      boolean isAllAirAttacks = true;
      for (JBGBattleSimpleEvent e: lstBattles) {
          if (e.getDefender().equals(target) && e.getAttacker().equals(player)) {
              if (!troopIsAirforce(e.getAttackerTroops())) {
                isAllAirAttacks = false;
              }
              attackAreas.append( e.getLocation() + ", " );
              iTotalAttackAreas++;
              lstUsedBattles.add(e);

              if (e.isAttackerWon()) {
                successAreas.append( e.getLocation() + ", ");
                iTotalSuccessAreas++;
                iGroupSuccessCount++;
              }
          }
      }

      boolean isShockedForDefender = false;
      if (iTotalAttackAreas > 1 && iTotalAttackAreas == iTotalSuccessAreas) {
        isShockedForDefender = true;
      }

      if (attackAreas.length() > 2) {
        if (successAreas.length() > 2) {
          if (successAreas.length() == attackAreas.length()) {
            attackAreas.setLength(attackAreas.length()-2);
            if (isShockedForDefender) {
              targetStory.append( target + "'s shocked! " + player + " conquered all " + attackAreas.toString() + ".");
            }
            else {
              targetStory.append( player + " attacked " + target + " and captured " + attackAreas.toString() + ".");
            }
          }
          else {
            if (isAllAirAttacks) {
              targetStory.append( player + " launched air attack on " + target + "'s " + attackAreas.toString());
              successAreas.setLength(successAreas.length()-2); //remove last ", "
              targetStory.append( " and successfully swept " + successAreas.toString() + "." );
            }
            else {
              targetStory.append( player + " attacked " + target + "'s territory of " + attackAreas.toString() );
              successAreas.setLength(successAreas.length()-2); //remove last ", "
              targetStory.append( " won the battle" + ((iGroupSuccessCount>1)?"s":"") + " of " + successAreas.toString() + "." );
            }
          }
        }
        else {

          attackAreas.setLength(attackAreas.length()-2);
          if (isAllAirAttacks)
            targetStory.append( player + " bombed " + target + "'s " + attackAreas.toString() + "!" );
          else
            targetStory.append( player + " attacked " + target + "'s " + attackAreas.toString() + " but failed!" );
        }
        targetStory.append("\n");
      }
      lstBattleGroupInfos.add(targetStory.toString());

    }

    if (lstUsedBattles.size() > 0) {
     for (JBGBattleSimpleEvent e: lstBattles) {
       boolean battleUsed = false;
       for (JBGBattleSimpleEvent uBtl: lstUsedBattles) {
          if (uBtl == e) {
            battleUsed = true;
            break;
          }
       }

       if (!battleUsed) {
         lstRemainingBattles.add(e);
       }
     }
    }
    else {
    lstRemainingBattles = lstBattles;
    }

    if (lstRemainingBattles.size() < lstBattles.size()) {
      StringBuilder smallAttackStory = new StringBuilder();
      StringBuilder defenseStory = new StringBuilder();

      StringBuilder smallAttackAreas = new StringBuilder();
      StringBuilder smallAttackSuccessAreas = new StringBuilder();
      StringBuilder defendAreas = new StringBuilder();
      StringBuilder defendFailAreas = new StringBuilder();
      //some were used for headlines, not we write briefing about smaller battles, also only region names
      for (JBGBattleSimpleEvent e: lstRemainingBattles) {
        if (e.getAttacker().equals(player)) {
          smallAttackAreas.append(e.getLocation() + ", ");
          if (e.getWinner().equals(player)) {
            smallAttackSuccessAreas.append(e.getLocation() + " of " + e.getDefender() + ", ");
          }
        }
        else {
          //other player attack
          //this player defend
          defendAreas.append(e.getLocation() + ", ");
          if (!e.getWinner().equals(player)) {
            defendFailAreas.append(e.getLocation() + " to " + e.getWinner() + ", ");
          }
        }
      }

      //write story for small attacks
      if (smallAttackAreas.length() > 2) {
        smallAttackStory.append(JBGConstants.JBGTURN_NEWS_SMALLATTACK_PREFIX);
        if (smallAttackSuccessAreas.length() > 2) {
          if (smallAttackSuccessAreas.length() == smallAttackAreas.length()) {
            smallAttackStory.append(player + " also attacked and won battle in " + smallAttackAreas.toString() );
          }
          else {
            smallAttackStory.append(player + " also attacked " + smallAttackAreas.toString() );
            smallAttackSuccessAreas.setLength(smallAttackSuccessAreas.length()-2);
            smallAttackStory.append(" and captured " + smallAttackSuccessAreas.toString() + ".");
          }
        }
        else {
          smallAttackStory.append(player + " also attacked " + smallAttackAreas.toString() );
          smallAttackStory.append( " but failed to reach any target!" );
        }
        lstBattleGroupInfos.add(smallAttackStory.toString());
      }

      //write story for defenses
      if (defendAreas.length() > 2 ) {
        defendAreas.setLength(defendAreas.length()-2);
        defenseStory.append(player + " had to defend areas of " + defendAreas.toString() );
        if (defendFailAreas.length() > 2) {
          defendFailAreas.setLength(defendFailAreas.length()-2);
          defenseStory.append(" and lost " + defendFailAreas.toString() + ".");
        }
        else {
          defenseStory.append(" and proudly repelled all attackers!");
        }
        lstBattleGroupInfos.add(defenseStory.toString());
      }
      //
    }

    return lstBattleGroupInfos;
  }

  public List<String> writeBattlesDetails(boolean isHtml) {
    List<String> lstBattleDetails = new ArrayList<>();

    for (JBGBattleSimpleEvent e: lstBattles) {
      String detailStory = writeBattleEventStory(e, isHtml);
      lstBattleDetails.add(detailStory);
    }
    return lstBattleDetails;
  }

  private List<String> getNormalMovesTargetGroups() {
    Map<String, Integer> lstTargetGroups = new HashMap<>();
    for (JBGMove m: lstNormalMoves) {
      String des = m.getDestination();
      if (!lstTargetGroups.containsKey(des)) {
        lstTargetGroups.put(des, 1);
      }
      else {
        lstTargetGroups.put(des, lstTargetGroups.get(des)+1);
      }
    }

    List<String> lstRes = new ArrayList<>();
    if (lstTargetGroups.size() > 0) {

      for (Map.Entry<String, Integer> entry : lstTargetGroups.entrySet()) {
         if (entry.getValue() >= 1) {
          lstRes.add(entry.getKey());
         }
       }
    }
    return lstRes;
  }

  private List<String> getPlaceTargetGroups() {
    Map<String, Integer> lstTargetGroups = new HashMap<>();
    for (JBGPlace p: lstPlaces) {
      String loc = p.getLocation();
      if (!lstTargetGroups.containsKey(loc)) {
        lstTargetGroups.put(loc, 1);
      }
      else {
        lstTargetGroups.put(loc, lstTargetGroups.get(loc)+1);
      }
    }

    List<String> lstRes = new ArrayList<>();
    if (lstTargetGroups.size() > 0) {

      for (Map.Entry<String, Integer> entry : lstTargetGroups.entrySet()) {
         if (entry.getValue() >= 1) {
          lstRes.add(entry.getKey());
         }
       }
    }
    return lstRes;
  }

  private List<String> sumPlaceTargetGroups() {
    Map<String, Integer> lstTargetGroups = new HashMap<>();
    for (JBGPlace p: lstPlaces) {
      String loc = p.getLocation();
      if (!lstTargetGroups.containsKey(loc)) {
        lstTargetGroups.put(loc, 1);
      }
      else {
        lstTargetGroups.put(loc, lstTargetGroups.get(loc)+1);
      }
    }

    List<String> lstRes = new ArrayList<>();
    if (lstTargetGroups.size() > 0) {

      for (Map.Entry<String, Integer> entry : lstTargetGroups.entrySet()) {
         if (entry.getValue() >= 1) {
          lstRes.add(entry.getKey());
         }
       }
    }
    return lstRes;
  }

  private String writeNormalMovesContent() {
    StringBuilder sb = new StringBuilder();
    List<String> lstMoveTargets = getNormalMovesTargetGroups();

    if (lstMoveTargets == null) return "No moves.";

    for (String des: lstMoveTargets) {
      sb.append(player + " moved into " + des + ": ");
      for (JBGMove m: lstNormalMoves) {
        if (m != null && m.getDestination() != null)
          if (m.getDestination().equals(des)) {
            sb.append(m.getTroops() + " from " + m.getDeparture() + ", ");
          }
      }
      sb.setLength(sb.length()-2);
      sb.append(".<br/>");
    }
    return sb.toString();
  }

  private String writePlacesContent() {
    StringBuilder sb = new StringBuilder();
    List<String> lstTargets = getPlaceTargetGroups();
    if (lstTargets == null) return "No production.";
    for (String loc: lstTargets) {
      sb.append(loc + ": ");
      List<String> placeItems = new ArrayList<>();
      for (JBGPlace p: lstPlaces) {
        if (p.getLocation().equals(loc)) {
          sb.append(p.getTroops() + "<br/>");
          placeItems.add(p.getTroops());
        }
      }
    }
    return sb.toString();
  }

  public String toHTMLWithBasicNews() {

    //sort by score, highest first
    Collections.sort(lstBattles, Collections.reverseOrder());


    StringBuilder sb = new StringBuilder();
    sb.append("<p><span class=\"content-group\">" + player + "</span></p>");
    List<String> details = writeBattlesDetails(true);
    for (String dtl: details) {
        sb.append("<p>" + dtl + "</p>");
    }
    sb.append("<br/>");
    sb.append("<p><u>NonCombat Moves:</u><br/>");
    sb.append(writeNormalMovesContent());
    sb.append("</p");

    sb.append("<p><u>Purchases:</u><br/>");
    for (String p: lstPurchases)
      sb.append(p + "<br/>");
    sb.append("</p");

    sb.append("<p><u>" + player + " production:</u><br/>");
    sb.append(writePlacesContent());
    sb.append("</p");

    return sb.toString(); //moved to JBGTurnHistoryParser addImageToBasicNews(sb.toString());

  }

  public String toStringWithBasicNews() {

    //sort by score, highest first
    Collections.sort(lstBattles, Collections.reverseOrder());


    StringBuilder sb = new StringBuilder();
    sb.append(player + "\n");
    sb.append("Details by our frontline correspondents:\n");
    sb.append(writeBattlesDetails(false));
    sb.append("\n");
    sb.append("\n");
    sb.append("NonCombat Moves: \n");
    for (JBGMove m: lstNormalMoves)
      sb.append(m.toString() + "\n");
    sb.append("Purchases: \n");
    for (String p: lstPurchases)
      sb.append(p + "\n");
    sb.append("Places: \n");
    for (JBGPlace p: lstPlaces)
      sb.append(p.toString() + "\n");
    return sb.toString();

  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(player + "\n");
    sb.append("Combat Moves: \n");
    for (JBGMove m: lstCombatMoves)
      sb.append(m.toString() + "\n");
    sb.append("Battle Moves: \n");
    for (JBGBattleSimpleEvent e: lstBattles)
      sb.append(e.toString() + "\n");
    sb.append("Normal Moves: \n");
    for (JBGMove m: lstNormalMoves)
      sb.append(m.toString() + "\n");
    sb.append("Purchases: \n");
    for (String p: lstPurchases)
      sb.append(p + "\n");
    sb.append("Places: \n");
    for (JBGPlace p: lstPlaces)
      sb.append(p.toString() + "\n");
    return sb.toString();
  }

}