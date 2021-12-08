package games.strategy.triplea.ui.history;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.triplea.ui.mapdata.MapData;

import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Resource;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.JBGTurnLogItem;
import games.strategy.engine.data.JBGAnalyzableTurnEntry;
import games.strategy.engine.data.JBGBattleSimpleEvent;
import games.strategy.engine.data.JBGMove;
import games.strategy.engine.history.HistoryNode;
import games.strategy.engine.history.Renderable;
import games.strategy.engine.history.Round;
import games.strategy.engine.history.Step;
import games.strategy.engine.random.IRandomStats;
import games.strategy.engine.random.RandomStatsDetails;
import games.strategy.triplea.Constants;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.delegate.DiceRoll;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.MoveDelegate;
import games.strategy.triplea.delegate.OriginalOwnerTracker;
import games.strategy.triplea.formatter.MyFormatter;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.Insets;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JTabbedPane;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.DefaultListModel;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.*;
import javax.swing.JTextArea;

import lombok.extern.java.Log;
import org.triplea.java.collections.IntegerMap;

import games.strategy.engine.data.JBGConstants;
import static games.strategy.triplea.image.UnitImageFactory.ImageKey;
import games.strategy.triplea.ui.UiContext;

@Log
public class JBGTurnHistoryParser {
  private static final long serialVersionUID = 4880602702815333376L;
  private final StringBuilder stringBuilder = new StringBuilder();
  private Collection<Territory> territories = null;
  private List<String> seaTerritoryNames = new ArrayList<String>();

  private int iCurrentAirOPTagIdx = 0;
  private int iCurrentSeaAirOPTagIdx = 0;
  private int iCurrentSeaOPTagIdx = 0;
  private int iCurrentSeaAirBombOPTagIdx = 0;
  private int iCurrentAirBombOPTagIdx = 0;
  private int iCurrentSeaFailOPTagIdx = 0;
  private int iCurrentAmphibOPTagIdx = 0;

  public JBGTurnHistoryParser() {
  }

  private void loadSeaZoneNames() {
    if (territories == null) return;
    for (Territory t: territories) {
      if (Matches.territoryIsWater().test(t))
        if (!seaTerritoryNames.contains(t.getName()))
          seaTerritoryNames.add(t.getName());
      }
  }

  private void loadTerritories(GameData data) {
    if (territories == null) {
      data.acquireReadLock();
      try {
        territories = data.getMap().getTerritories();
      } finally {
        data.releaseReadLock();
      }
    }
  }

  public String parse(final GameData data, final boolean isLastTurn, final int posType, final List<String> excludePlayers) {

    loadTerritories(data);
    loadSeaZoneNames();

    Collection<GamePlayer> playersAllowed = data.getPlayerList().getPlayers();
    List<JBGAnalyzableTurnEntry> lst = parseFullTurn(data, true, playersAllowed);

    StringBuilder sb = new StringBuilder();
    StringBuilder sbDetailedNews = new StringBuilder();
    for (JBGAnalyzableTurnEntry e: lst) {
      if (isLastTurn) {
        if (posType == JBGConstants.TURN_SEQ_FIRST || posType == JBGConstants.TURN_SEQ_LAST) {
          sb.append(e.writeBattlesHeadlines(false) + "\n\n");
          sbDetailedNews.append(e.toStringWithBasicNews() + "\n");
        }
        else if (posType == JBGConstants.TURN_SEQ_MIDDLE) {
          if (!excludePlayers.contains(e.getPlayer())) {
            sb.append(e.writeBattlesHeadlines(false) + "\n\n");
            sbDetailedNews.append(e.toStringWithBasicNews() + "\n");
          }
        }

      }
      else {
        sb.append(e.writeBattlesHeadlines(false) + "\n\n");
        sbDetailedNews.append(e.toStringWithBasicNews() + "\n");
      }

    }
    sb.append("<p>Detail News:<p>" + sbDetailedNews.toString());
    return sb.toString();
  }

  public String parseSinglePlayerTurn(final GameData data, final boolean isLastTurn, final int posType, final String playerName) {

    loadTerritories(data);
    loadSeaZoneNames();

    Collection<GamePlayer> playersAllowed = new ArrayList<GamePlayer>();
    playersAllowed.add(data.getPlayerList().getPlayerId(playerName));
    List<JBGAnalyzableTurnEntry> lst = parseFullTurn(data, true, playersAllowed);

    StringBuilder sb = new StringBuilder();
    StringBuilder sbDetailedNews = new StringBuilder();
    for (JBGAnalyzableTurnEntry e: lst) {

      if (isLastTurn) {
        if (posType == JBGConstants.TURN_SEQ_FIRST || posType == JBGConstants.TURN_SEQ_LAST) {
          sb.append(e.writeBattlesHeadlines(false) + "\n\n");
          sbDetailedNews.append(e.toStringWithBasicNews() + "\n");
        }
        else if (posType == JBGConstants.TURN_SEQ_MIDDLE) {
            sb.append(e.writeBattlesHeadlines(false) + "\n\n");
            sbDetailedNews.append(e.toStringWithBasicNews() + "\n");
        }        
      }
      else {
        sb.append(e.writeBattlesHeadlines(false) + "\n\n");
        sbDetailedNews.append(e.toStringWithBasicNews() + "\n");
      }

    }
    sb.append("<p>Detail News:<p>" + sbDetailedNews.toString());
    return sb.toString();
  }

  private void addHeadline(final String ln, StringBuilder sb) {
    if (ln.indexOf(JBGConstants.JBGTURN_NEWS_SMALLATTACK_PREFIX) == 0) {
      sb.append(
        "<span class=\"headline-addition\">" + ln.substring(JBGConstants.JBGTURN_NEWS_SMALLATTACK_PREFIX.length()) + "</span><br/>"
        );
    }
    else {
      sb.append(
        "<span class=\"headline\">" + ln + "</span><br/>"
        );
    }
  }

  private String writeMobilizationHeadlines(List<String> lst) {
    StringBuilder sb = new StringBuilder();
    sb.append("<br><p><center>")
      .append("<img src='" + JBGConstants.JBGTURN_NEWS_MOBILIZATION_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>")
      .append("<span class=\"headline\">")
      .append(String. join(",", lst))
      .append(": mobilized population for defending the " + ((lst.size()==1)?"nation!":"nations!!!"))
      .append("</span></center></p>");
    return sb.toString();
  }

  public String parseHTML(final GameData data, final boolean isLastTurn, final int posType, final List<String> excludePlayers) {

    loadTerritories(data);
    loadSeaZoneNames();

    Collection<GamePlayer> playersAllowed = data.getPlayerList().getPlayers();
    List<JBGAnalyzableTurnEntry> lst = parseFullTurn(data, true, playersAllowed);

    List<String> mobilizationPlayerList = new ArrayList<String>();

    StringBuilder sb = new StringBuilder();
    StringBuilder sbDetailedNews = new StringBuilder();
    sb.append("<p>");
  for (JBGAnalyzableTurnEntry e: lst) {

      if (e.isJustMobilization() && !mobilizationPlayerList.contains(e.getPlayer())) {
        mobilizationPlayerList.add(e.getPlayer());
      }

      if (isLastTurn) {
        if (posType == JBGConstants.TURN_SEQ_FIRST || posType == JBGConstants.TURN_SEQ_LAST) {
          List<String> lstHeadlines = e.writeBattlesHeadlines(true);
          for (String ln: lstHeadlines) {
            addHeadline(ln, sb);
          }
          sbDetailedNews.append(e.toHTMLWithBasicNews());
        }
        else if (posType == JBGConstants.TURN_SEQ_MIDDLE) {
          if (!excludePlayers.contains(e.getPlayer())) {
            List<String> lstHeadlines = e.writeBattlesHeadlines(true);
            for (String ln: lstHeadlines) {
              addHeadline(ln, sb);
            }
            sbDetailedNews.append(e.toHTMLWithBasicNews());
          }
        }        
      }
      else {
        List<String> lstHeadlines = e.writeBattlesHeadlines(true);
        for (String ln: lstHeadlines) {
          addHeadline(ln, sb);
        }

        sbDetailedNews.append(e.toHTMLWithBasicNews());
      }

    }
    sb.append("</p><br/>"); //class row

    StringBuilder mobilizationNews = new StringBuilder();
    if (mobilizationPlayerList.size() > 0) 
      mobilizationNews.append(writeMobilizationHeadlines(mobilizationPlayerList) + "<br><br>");

    //mobilizationNews.append(testAllImageItems());

    sb.append("<center><span class=\"headline-detail\">Detailed News from our frontline correspondences</span></center>")
      .append(mobilizationNews.toString())
      .append(sbDetailedNews.toString());
    return addImageToContent(sb.toString());
  }

  private String testAllImageItems() {
    return
        "<img src='" + JBGConstants.JBGTURN_NEWS_AIROP1_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_AIROP2_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_AIROP3_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_AIROP4_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_AIRBOMB1_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_AIRBOMB2_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_AIRBOMB3_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_AIRBOMB4_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEA_AIROP1_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEA_AIROP2_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEA_AIROP3_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEA_AIROP4_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEA_AIRBOMB1_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEA_AIRBOMB2_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEA_AIRBOMB3_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEA_AIRBOMB4_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY1_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY2_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY3_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY4_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEAOP_FAILURE1_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEAOP_FAILURE2_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEAOP_FAILURE3_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>" +
        "<img src='" + JBGConstants.JBGTURN_NEWS_SEAOP_FAILURE4_IMG_URL + "' width=\"240px\" height=\"auto\"/><br/>";
  }
  private String getNewsAmphibOPImageUrl() {
    String res = null;
    switch (this.iCurrentAmphibOPTagIdx) {
      case 0:
        res = JBGConstants.JBGTURN_NEWS_AMPHIB1_IMG_URL;
        break;
      case 1:
        res = JBGConstants.JBGTURN_NEWS_AMPHIB2_IMG_URL;
        break;
      case 2:
        res = JBGConstants.JBGTURN_NEWS_AMPHIB3_IMG_URL;
        break;
      case 3:
        res = JBGConstants.JBGTURN_NEWS_AMPHIB4_IMG_URL;
        break;
    }
    if (this.iCurrentAmphibOPTagIdx + 1 < 4)
      this.iCurrentAmphibOPTagIdx++;
    else
      this.iCurrentAmphibOPTagIdx = 0;
    return res;
  }
  private String getNewsAirOPImageUrl() {
    String res = null;
    switch (this.iCurrentAirOPTagIdx) {
      case 0:
        res = JBGConstants.JBGTURN_NEWS_AIROP1_IMG_URL;
        break;
      case 1:
        res = JBGConstants.JBGTURN_NEWS_AIROP2_IMG_URL;
        break;
      case 2:
        res = JBGConstants.JBGTURN_NEWS_AIROP3_IMG_URL;
        break;
      case 3:
        res = JBGConstants.JBGTURN_NEWS_AIROP4_IMG_URL;
        break;
    }
    if (this.iCurrentAirOPTagIdx + 1 < 4)
      this.iCurrentAirOPTagIdx++;
    else
      this.iCurrentAirOPTagIdx = 0;
    return res;
  }
  private String getNewsSeaAirOPImageUrl() {
    String res = null;
    switch (this.iCurrentSeaAirOPTagIdx) {
      case 0:
        res = JBGConstants.JBGTURN_NEWS_SEA_AIROP1_IMG_URL;
        break;
      case 1:
        res = JBGConstants.JBGTURN_NEWS_SEA_AIROP2_IMG_URL;
        break;
      case 2:
        res = JBGConstants.JBGTURN_NEWS_SEA_AIROP3_IMG_URL;
        break;
      case 3:
        res = JBGConstants.JBGTURN_NEWS_SEA_AIROP4_IMG_URL;
        break;
    }
    if (this.iCurrentSeaAirOPTagIdx + 1 < 4)
      this.iCurrentSeaAirOPTagIdx++;
    else
      this.iCurrentSeaAirOPTagIdx = 0;
    return res;
  }
  private String getNewsSeaOPImageUrl() {
    String res = null;
    switch (this.iCurrentSeaOPTagIdx) {
      case 0:
        res = JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY1_IMG_URL;
        break;
      case 1:
        res = JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY2_IMG_URL;
        break;
      case 2:
        res = JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY3_IMG_URL;
        break;
      case 3:
        res = JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY4_IMG_URL;
        break;
    }
    if (this.iCurrentSeaOPTagIdx + 1 < 4)
      this.iCurrentSeaOPTagIdx++;
    else
      this.iCurrentSeaOPTagIdx = 0;
    return res;
  }
  private String getNewsSeaAirBombOPImageUrl() {
    String res = null;
    switch (this.iCurrentSeaAirBombOPTagIdx) {
      case 0:
        res = JBGConstants.JBGTURN_NEWS_SEA_AIRBOMB1_IMG_URL;
        break;
      case 1:
        res = JBGConstants.JBGTURN_NEWS_SEA_AIRBOMB2_IMG_URL;
        break;
      case 2:
        res = JBGConstants.JBGTURN_NEWS_SEA_AIRBOMB3_IMG_URL;
        break;
      case 3:
        res = JBGConstants.JBGTURN_NEWS_SEA_AIRBOMB4_IMG_URL;
        break;
    }
    if (this.iCurrentSeaAirBombOPTagIdx + 1 < 4)
      this.iCurrentSeaAirBombOPTagIdx++;
    else
      this.iCurrentSeaAirBombOPTagIdx = 0;
    return res;
  }
  private String getNewsAirBombOPImageUrl() {
    String res = null;
    switch (this.iCurrentAirBombOPTagIdx) {
      case 0:
        res = JBGConstants.JBGTURN_NEWS_AIRBOMB1_IMG_URL;
        break;
      case 1:
        res = JBGConstants.JBGTURN_NEWS_AIRBOMB2_IMG_URL;
        break;
      case 2:
        res = JBGConstants.JBGTURN_NEWS_AIRBOMB3_IMG_URL;
        break;
      case 3:
        res = JBGConstants.JBGTURN_NEWS_AIRBOMB4_IMG_URL;
        break;
    }
    if (this.iCurrentAirBombOPTagIdx + 1 < 4)
      this.iCurrentAirBombOPTagIdx++;
    else
      this.iCurrentAirBombOPTagIdx = 0;
    return res;
  }
  private String getNewsSeaFailureOPImageUrl() {
    String res = null;
    switch (this.iCurrentSeaFailOPTagIdx) {
      case 0:
        res = JBGConstants.JBGTURN_NEWS_SEAOP_FAILURE1_IMG_URL;
        break;
      case 1:
        res = JBGConstants.JBGTURN_NEWS_SEAOP_FAILURE2_IMG_URL;
        break;
      case 2:
        res = JBGConstants.JBGTURN_NEWS_SEAOP_FAILURE3_IMG_URL;
        break;
      case 3:
        res = JBGConstants.JBGTURN_NEWS_SEAOP_FAILURE4_IMG_URL;
        break;
    }
    if (this.iCurrentSeaFailOPTagIdx + 1 < 4)
      this.iCurrentSeaFailOPTagIdx++;
    else
      this.iCurrentSeaFailOPTagIdx = 0;
    return res;
  }
  private String addImageToContent(String news) {
    while (news.indexOf(JBGConstants.JBGTURN_NEWS_AIROP_IMG) >= 0)
      news = news.replaceFirst(JBGConstants.JBGTURN_NEWS_AIROP_IMG, 
        "<img src='" + getNewsAirOPImageUrl() + "' width=\"240px\" height=\"auto\"/><br/>"
        );
    while (news.indexOf(JBGConstants.JBGTURN_NEWS_AIRBOMB_IMG) >= 0)
      news = news.replaceFirst(JBGConstants.JBGTURN_NEWS_AIRBOMB_IMG, 
        "<img src='" + getNewsAirBombOPImageUrl() + "' width=\"240px\" height=\"auto\"/><br/>"
        );
    while (news.indexOf(JBGConstants.JBGTURN_NEWS_SEA_AIROP_IMG) >= 0)
      news = news.replaceFirst(JBGConstants.JBGTURN_NEWS_SEA_AIROP_IMG, 
        "<img src='" + getNewsSeaAirOPImageUrl() + "' width=\"240px\" height=\"auto\"/><br/>"
        );
    while (news.indexOf(JBGConstants.JBGTURN_NEWS_SEA_AIRBOMB_IMG) >= 0)
      news = news.replaceFirst(JBGConstants.JBGTURN_NEWS_SEA_AIRBOMB_IMG, 
        "<img src='" + getNewsSeaAirBombOPImageUrl() + "' width=\"240px\" height=\"auto\"/><br/>"
        );
    while (news.indexOf(JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY_IMG) >= 0)
      news = news.replaceFirst(JBGConstants.JBGTURN_NEWS_SEAOP_VICTORY_IMG, 
        "<img src='" + getNewsSeaOPImageUrl() + "' width=\"240px\" height=\"auto\"/><br/>"
        );
    while (news.indexOf(JBGConstants.JBGTURN_NEWS_SEAOP_FAILURE_IMG) >= 0)
      news = news.replaceFirst(JBGConstants.JBGTURN_NEWS_SEAOP_FAILURE_IMG, 
        "<img src='" + getNewsSeaFailureOPImageUrl() + "' width=\"240px\" height=\"auto\"/><br/>"
        );
    while (news.indexOf(JBGConstants.JBGTURN_NEWS_AMPHIB_IMG) >= 0)
      news = news.replaceFirst(JBGConstants.JBGTURN_NEWS_AMPHIB_IMG, 
        "<img src='" + getNewsAmphibOPImageUrl() + "' width=\"240px\" height=\"auto\"/><br/>"
        );

    return news;
  }

  public String parseHTMLSinglePlayerTurn(final GameData data, final boolean isLastTurn, final int posType, final String playerName) {

    loadTerritories(data);
    loadSeaZoneNames();

    Collection<GamePlayer> playersAllowed = new ArrayList<GamePlayer>();
    playersAllowed.add(data.getPlayerList().getPlayerId(playerName));
    List<JBGAnalyzableTurnEntry> lst = parseFullTurn(data, true, playersAllowed);

    StringBuilder sb = new StringBuilder();
    StringBuilder sbDetailedNews = new StringBuilder();
    sb.append("<p>");
    for (JBGAnalyzableTurnEntry e: lst) {

      if (isLastTurn) {
        if (posType == JBGConstants.TURN_SEQ_FIRST || posType == JBGConstants.TURN_SEQ_LAST) {
          List<String> lstHeadlines = e.writeBattlesHeadlines(true);
          for (String ln: lstHeadlines) {
            addHeadline(ln, sb);
          }
          sbDetailedNews.append(e.toHTMLWithBasicNews());
        }
        else if (posType == JBGConstants.TURN_SEQ_MIDDLE) {
            List<String> lstHeadlines = e.writeBattlesHeadlines(true);
            for (String ln: lstHeadlines) {
              addHeadline(ln, sb);
            }
            sbDetailedNews.append(e.toHTMLWithBasicNews());
        }        
      }
      else {
        List<String> lstHeadlines = e.writeBattlesHeadlines(true);
        for (String ln: lstHeadlines) {
          addHeadline(ln, sb);
        }
        sbDetailedNews.append(e.toHTMLWithBasicNews());
      }

    }
    sb.append("</p><br/>"); //class row

    sb.append("<center><span class=\"headline-detail\">Detailed News from our frontline correspondences</span></center>" + 
      sbDetailedNews.toString());
    return sb.toString();
  }

  public void append(final String string) {
    stringBuilder.append(string);
  }

  @Override
  public String toString() {
    return stringBuilder.toString();
  }

  public void clear() {
    stringBuilder.setLength(0);
  }

  /**
   * Adds details about the current turn for each player in {@code playersAllowed} to the log.
   * Information about each step and event that occurred during the turn are included.
   */
  private List<JBGAnalyzableTurnEntry> parseFullTurn(
      final GameData data, final boolean verbose, final Collection<GamePlayer> playersAllowed) {
    HistoryNode curNode = data.getHistory().getLastNode();
    final Collection<GamePlayer> players = new HashSet<>();
    if (playersAllowed != null) {
      players.addAll(playersAllowed);
    }
    // find Step node, if exists in this path
    Step stepNode = null;
    while (curNode != null) {
      if (curNode instanceof Step) {
        stepNode = (Step) curNode;
        break;
      }
      curNode = (HistoryNode) curNode.getPreviousNode();
    }
    if (stepNode != null) {
      final GamePlayer curPlayer = stepNode.getPlayerId();
      if (players.isEmpty()) {
        players.add(curPlayer);
      }
      // get first step for this turn
      Step turnStartNode;

      int iStepIdx = 0;
      while (true) {
        turnStartNode = stepNode;
        stepNode = (Step) stepNode.getPreviousSibling();
        if (stepNode == null) {
          break;
        }
        if (stepNode.getPlayerId() == null) {
          break;
        }
        if (!players.contains(stepNode.getPlayerId())) {
          break;
        }
      }
      printRemainingTurn(turnStartNode, verbose, data.getDiceSides(), players);
    } else {
      //log.severe("No step node found in!");
      return new ArrayList<>();
    }

    /*
    System.out.println("----------------------------------------Verbose entries----------------------------------------");
    for (JBGTurnLogItem item: turnLogs) {
      System.out.println(item.toString());
    }
    */
    //System.out.println("----------------------------------------Build simple entries----------------------------------------");
    List<JBGAnalyzableTurnEntry> lstEntries = new ArrayList<>();
    for (JBGTurnLogItem item: turnLogs) {

      JBGAnalyzableTurnEntry e = null;
      boolean bPlayerInEntries = false;
      for (JBGAnalyzableTurnEntry entry: lstEntries) {
        if (entry.isPlayer(item.getPlayer())) {
          bPlayerInEntries = true;
          e = entry;
          break;
        }
      }
      if (!bPlayerInEntries) {
        e = new JBGAnalyzableTurnEntry(item.getPlayer());
        lstEntries.add(e);
      }

      saveTurnLogToEntry(item, e);
      //remove object for resource saving
      item = null;
    }

    /*
    for (JBGAnalyzableTurnEntry e: lstEntries) {
      System.out.println(e.toString());
    }
    */

    return lstEntries;
  }

  private void saveTurnLogToEntry(JBGTurnLogItem item, JBGAnalyzableTurnEntry entry) {

    if (item.getBlock().equals(JBGConstants.HI_KOMBAT_MOVE_TITLE)) {

      String tmpStr = item.getInfo();
      List<String> lines = Arrays.asList(tmpStr.split("\\n"));
      for (String line: lines) {
        boolean bMoveTakeProvince = false;
        if (line.contains(JBGConstants.HI_TAG_MOVE_TAKE_PROV)) {
          bMoveTakeProvince = true;
          line = line.replaceAll(JBGConstants.HI_TAG_MOVE_TAKE_PROV, "");
        }
        JBGMove m = new JBGMove(line, seaTerritoryNames);
        entry.addCombatMove(m);

        if (bMoveTakeProvince) {
          JBGBattleSimpleEvent e = new JBGBattleSimpleEvent(m.getDestination());
          e.initMoveTake(item.getPlayer(), m.getTroops());
          entry.addBattle(e);
        }

      }
    }
    else if (item.getBlock().equals(JBGConstants.HI_NONKOMBAT_MOVE_TITLE)) {

      String tmpStr = item.getInfo();
      List<String> lines = Arrays.asList(tmpStr.split("\\n"));
      for (String line: lines) {
        entry.addNormalMove(new JBGMove(line, seaTerritoryNames));
      }
    }
    else if (item.getBlock().equals(JBGConstants.HI_PURCHASE_TITLE)) {

      String tmpStr = item.getInfo();
      List<String> lines = Arrays.asList(tmpStr.split("\\n"));
      for (String line: lines) {
        entry.addPurchase(line);

      }            
    }
    else if (item.getBlock().equals(JBGConstants.HI_BATTLE_TITLE)) {

      String tmpStr = item.getInfo();
      List<String> lines = Arrays.asList(tmpStr.split("\\n"));
      JBGBattleSimpleEvent e = null;
      for (String line: lines) {
        if (line.contains(JBGConstants.HI_TAG_START_BATTLE_LOC)) {
          if (e != null)
            entry.addBattle(e);
          e = new JBGBattleSimpleEvent(line);
          final String location = e.getLocation();
          if (seaTerritoryNames.contains(location)) {
            e.setSeaBattle(true);
          }
        }
        else if (e != null)
          e.processLine(line);

      }
      if (e != null)
        entry.addBattle(e);
    }
    else if (item.getBlock().equals(JBGConstants.HI_PLACE_TITLE)) {

      String tmpStr = item.getInfo();
      List<String> lines = Arrays.asList(tmpStr.split("\\n"));
      for (String line: lines) {
        entry.addPlace(line);

      }
    }
  }

  private static GamePlayer getPlayerId(final HistoryNode printNode) {
    DefaultMutableTreeNode curNode = printNode;
    final TreePath parentPath = new TreePath(printNode.getPath()).getParentPath();
    GamePlayer curPlayer = null;
    if (parentPath != null) {
      final Object[] pathToNode = parentPath.getPath();
      for (final Object pathNode : pathToNode) {
        final HistoryNode node = (HistoryNode) pathNode;
        if (node instanceof Step) {
          curPlayer = ((Step) node).getPlayerId();
        }
      }
    }
    do {
      final Enumeration<?> nodeEnum = curNode.preorderEnumeration();
      while (nodeEnum.hasMoreElements()) {
        final HistoryNode node = (HistoryNode) nodeEnum.nextElement();
        if (node instanceof Step) {
          final String title = node.getTitle();
          final GamePlayer gamePlayer = ((Step) node).getPlayerId();
          if (!title.equals("Initializing Delegates") && gamePlayer != null) {
            curPlayer = gamePlayer;
          }
        }
      }
      curNode = curNode.getNextSibling();
    } while ((curNode instanceof Step) && ((Step) curNode).getPlayerId().equals(curPlayer));
    return curPlayer;
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

  private String getFirstWord(final String title, final String sep) {
    if (title.length() < 1)
      return "";
    if (title.indexOf(sep) > 0) {
      return title.substring(0, title.indexOf(sep)).trim();
    }
    return "";
  }

  private boolean isFirstWordEquals(final String title, final String sep, final String word) {
    if (title.length() < 1)
      return false;

    String s = getFirstWord(title, sep);
    if (s.length()  > 0) {
      if (s.equals(word)) {
        return true;
      }
    }
    return false;
  }

  private String extractBattleLocation(final String title) {
    if (title.length() < 1)
      return "";

    if (title.contains(JBGConstants.HI_BATTLE_LOC_TITLE)) {
      return new StringBuilder(
        JBGConstants.HI_TAG_START_BATTLE_LOC +
        title.substring(JBGConstants.HI_BATTLE_LOC_TITLE.length())
        ).toString();
    }
    return "";
  }

  private String formatMoveLocation(final String title) {
    if (title.length() < 1)
      return "";

    boolean bMoveTakeProvince = false;
    //target
    StringBuilder sb = new StringBuilder();

    int iToIdx = title.indexOf(JBGConstants.HI_MOVE_TO_PATT);
    int iFromIdx = title.indexOf(JBGConstants.HI_MOVE_FROM_PATT);
    int iEndOfFirstLine = title.length();

    if ( (iToIdx > 0 && iFromIdx > 0) && (iFromIdx < iToIdx) ) {

      if (title.contains("take") && title.contains("from")) {
        //may be take without combat?
        //because take in combat use "taking" "from"
        int iTakeIdx = title.indexOf(JBGConstants.HI_MOVE_TAKE_PATT);
        if (iTakeIdx > iFromIdx) {
          int iLnBrkIdx = title.indexOf("\n");
          if (iLnBrkIdx > 0) {
            //update iEndOfFirstLine for "To" location extract
            iEndOfFirstLine = iLnBrkIdx;
            String nextLine = title.substring(iLnBrkIdx+1);
            if (nextLine.length() > 0) {
              //has a second line in a combat move w/o battle, and take a province
              bMoveTakeProvince = true;
            }
          }
        }
      }

      String destination = title.substring(iToIdx+JBGConstants.HI_MOVE_TO_PATT.length(), iEndOfFirstLine);
      if (bMoveTakeProvince)
        destination = destination + JBGConstants.HI_TAG_MOVE_TAKE_PROV;
      sb.append(
        title.substring(iFromIdx+JBGConstants.HI_MOVE_FROM_PATT.length(), iToIdx) +
        "|" +
        destination +
        "|" +
        title.substring(0, iFromIdx).trim()
        );
    }

if (sb.toString().length() < 1) System.out.println("Empty move history event: "+title);

    return sb.toString();

  }

  private boolean isNum(final String s) {
    try {
      Integer.parseInt(s);
      return true;
    }
    catch (NumberFormatException e) {
    }
    return false;
  }

  private int getRollValueFromString(final String s) {

    int iLen = s.length();
    if (iLen < 1)
      return 0;

    String tmpStr = "";
    int iRoundIdx = s.indexOf(JBGConstants.HI_ROUND_IDX_PATT);
    if (iRoundIdx > 0) {
      tmpStr = s.substring(iRoundIdx + JBGConstants.HI_ROUND_IDX_PATT.length(), s.lastIndexOf(" "));
      if (isNum(tmpStr)) {
        return Integer.parseInt( tmpStr );
      }
    }
    return 0;
  }

  private int getBattleScoreFromString(final String s) {
    String tmpStr = s.substring(s.lastIndexOf(" ")).trim();
    if (isNum(tmpStr)) {
      return Integer.parseInt( tmpStr );     
    }
    return 0;
  }

  private String getRemainingUnits(final String s) {

    if (s != null && s.length() > 0) {
      String tmpStr = s.substring(
        s.indexOf(JBGConstants.HI_BATTLE_REM_UNIT_PAT1)+JBGConstants.HI_BATTLE_REM_UNIT_PAT1.length());
      tmpStr = tmpStr.substring(tmpStr.indexOf(JBGConstants.HI_BATTLE_REM_UNIT_PAT2)+JBGConstants.HI_BATTLE_REM_UNIT_PAT2.length());
      tmpStr = JBGConstants.HI_TAG_BATTLE_REMM + tmpStr.replaceAll(" remaining", "");
      return tmpStr;
    }

    return JBGConstants.HI_TAG_BATTLE_REMM;
  }

  //I put this here, not in beginning of class because it easier to merge new tripleA code, if needed
  //same reason for getMobilizationPlayerList(), not use Getter tag
  private List<JBGTurnLogItem> turnLogs = new ArrayList<>();
  private boolean saveBlockText(final String blockName, final String playerId, final String txtVal) {
    final String plName = playerId; //extractSecondWord(playerId, ":", "PlayerId named");
    if (plName.length() < 1)
      return false;
    boolean bProcessed = false;

    for (JBGTurnLogItem item: turnLogs) {
      if (item.isMatch(plName, blockName)) {
        item.addInfo(txtVal);
        bProcessed = true;
        break;
      }
    }
    if (!bProcessed) {
      JBGTurnLogItem item = null;
      if (txtVal.equals(JBGConstants.HI_TAG_DUMMY_START)) 
        item = new JBGTurnLogItem(plName, blockName);
      else
        item = new JBGTurnLogItem(plName, blockName, txtVal);
      turnLogs.add(item);
      bProcessed = true;
    }
    return bProcessed;
  }
  /**
   * Adds details about {@code printNode} and all its sibling and child nodes that are part of the
   * current turn for each player in {@code playersAllowed} to the log.
   */
  public void printRemainingTurn(
      final HistoryNode printNode,
      final boolean verbose,
      final int diceSides,
      final Collection<GamePlayer> playersAllowed) {

    int iCurRolls = 0;
    int iCurBattleScore = 0;
    String sCurrentAttacker = "";
    String sCurrentDefender = "";
    String sCurrentWinner = "";
    boolean bNonCombatPhase = false;
    final String moreIndent = "    ";
    // print out the parent nodes
    final TreePath parentPath = new TreePath(printNode.getPath()).getParentPath();
    GamePlayer currentPlayer = null;
    if (parentPath != null) {
      final Object[] pathToNode = parentPath.getPath();
      for (final Object pathNode : pathToNode) {
        final HistoryNode node = (HistoryNode) pathNode;
        stringBuilder.append(moreIndent.repeat(Math.max(0, node.getLevel())));
        stringBuilder.append(node.getTitle()).append('\n');
        if (node.getLevel() == 0) {
          stringBuilder.append('\n');
        }
        if (node instanceof Step) {
          currentPlayer = ((Step) node).getPlayerId();
        }
      }
    }
    final Collection<GamePlayer> players = new HashSet<>();
    if (playersAllowed != null) {
      players.addAll(playersAllowed);
    }
    if (currentPlayer != null) {
      players.add(currentPlayer);
    }
    final List<String> moveList = new ArrayList<>();
    boolean moving = false;
    DefaultMutableTreeNode curNode = printNode;
    final Map<String, Double> hitDifferentialMap = new HashMap<>();


    String parsingPlayerName = "";
    if (currentPlayer != null) {
      parsingPlayerName = currentPlayer.getName();
    }

    int iPassCount = 0;
    do {
      iPassCount++;

      // keep track of conquered territory during combat
      StringBuilder conquerStr = new StringBuilder();
      final Enumeration<?> nodeEnum = curNode.preorderEnumeration();
      while (nodeEnum.hasMoreElements()) {
        final HistoryNode node = (HistoryNode) nodeEnum.nextElement();
        final String title = node.getTitle();
        final String indent = moreIndent.repeat(Math.max(0, node.getLevel()));

        if (node instanceof Step) {
          currentPlayer = ((Step) node).getPlayerId();
          if (currentPlayer != null) {
            parsingPlayerName = currentPlayer.getName();
          }
        }

        // flush move list
        if (moving && !(node instanceof Renderable)) {
          final Iterator<String> moveIter = moveList.iterator();
          while (moveIter.hasNext()) {
            //move list
            String sMoveTxt = moveIter.next();
            if (!bNonCombatPhase) {
              saveBlockText(JBGConstants.HI_KOMBAT_MOVE_TITLE, parsingPlayerName, formatMoveLocation(sMoveTxt));
            }
            else 
              saveBlockText(JBGConstants.HI_NONKOMBAT_MOVE_TITLE, parsingPlayerName, formatMoveLocation(sMoveTxt));

            stringBuilder.append(sMoveTxt).append('\n');
            moveIter.remove();
          }
          moving = false;
          bNonCombatPhase = false; //reset
        }
        if (node instanceof Renderable) {
          final Object details = ((Renderable) node).getRenderingData();
          if (details instanceof DiceRoll) {
            if (!verbose) {
              continue;
            }
            final String diceMsg1 = title.substring(0, title.indexOf(':') + 1);
            if (diceMsg1.isEmpty()) {
              // tech roll
              stringBuilder.append(indent).append(moreIndent).append(title).append('\n');
            } else {
              //fighting rolls
                iCurRolls = getRollValueFromString(diceMsg1);

              // dice roll
              stringBuilder.append(indent).append(moreIndent).append(diceMsg1);
              final String hitDifferentialKey =
                  parseHitDifferentialKeyFromDiceRollMessage(diceMsg1);
              final DiceRoll diceRoll = (DiceRoll) details;
              final int hits = diceRoll.getHits();
              int rolls = 0;
              for (int i = 1; i <= diceSides; i++) {
                rolls += diceRoll.getRolls(i).size();
              }
              //fighting hits
              final double expectedHits = diceRoll.getExpectedHits();
              stringBuilder
                  .append(" ")
                  .append(hits)
                  .append("/")
                  .append(rolls)
                  .append(" hits, ")
                  .append(String.format("%.2f", expectedHits))
                  .append(" expected hits")
                  .append('\n');
              final double hitDifferential = hits - expectedHits;
              hitDifferentialMap.merge(hitDifferentialKey, hitDifferential, Double::sum);
            }
          } else if (details instanceof MoveDescription) {
            // movement
            final Pattern p = Pattern.compile("\\w+ undo move (\\d+).");
            final Matcher m = p.matcher(title);
            if (m.matches()) {
              moveList.remove(Integer.parseInt(m.group(1)) - 1);
            } else {
              moveList.add(indent + title);
              moving = true;
            }
          } else if (details instanceof Collection) {
            @SuppressWarnings("unchecked")
            final Collection<Object> objects = (Collection<Object>) details;
            final Iterator<Object> objIter = objects.iterator();
            if (objIter.hasNext()) {
              final Object obj = objIter.next();
              if (obj instanceof Unit) {
                @SuppressWarnings("unchecked")
                final Collection<Unit> allUnitsInDetails = (Collection<Unit>) details;
                // purchase/place units - don't need details
                Unit unit = (Unit) obj;
                if (title.matches("\\w+ buy .*")
                    || title.matches("\\w+ attack with .*")
                    || title.matches("\\w+ defend with .*")) {

                  if (title.contains("buy")) {
                    saveBlockText(JBGConstants.HI_PURCHASE_TITLE, 
                      parsingPlayerName, title.substring(title.indexOf(JBGConstants.HI_PURCHASE_PATT)+JBGConstants.HI_PURCHASE_PATT.length()));
                  }
                  else {

                    String tmpStr = "", curMovePlayer = "", sTag = "";
                    if (title.contains(JBGConstants.HI_BATTLE_ATK_PATT)) {
                      sCurrentAttacker = getFirstWord(title, " ");
                      tmpStr = title.substring(title.indexOf(JBGConstants.HI_BATTLE_ATK_PATT)+JBGConstants.HI_BATTLE_ATK_PATT.length());
                      curMovePlayer = sCurrentAttacker;
                      sTag = JBGConstants.HI_TAG_BATTLE_ATK;
                    }
                    if (title.contains(JBGConstants.HI_BATTLE_DEF_PATT)) {
                      sCurrentDefender = getFirstWord(title, " ");
                      tmpStr = title.substring(title.indexOf(JBGConstants.HI_BATTLE_DEF_PATT)+JBGConstants.HI_BATTLE_DEF_PATT.length());
                      curMovePlayer = sCurrentDefender;
                      sTag = JBGConstants.HI_TAG_BATTLE_DEF;
                    }
                    saveBlockText(JBGConstants.HI_BATTLE_TITLE, parsingPlayerName, 
                      new StringBuilder(
                        sTag + 
                        curMovePlayer + "|" +
                        tmpStr
                      ).toString());

                  }

                  //origin
                  stringBuilder.append(indent).append(title).append('\n');
                } else if (title.matches("\\d+ \\w+ owned by the .*? lost .*")
                    || title.matches("\\d+ \\w+ owned by the .*? lost")) {
                  if (!verbose) {
                    continue;
                  }
                  stringBuilder.append(indent).append(moreIndent).append(title).append('\n');
                } else if (title.startsWith("Battle casualty summary:")) {

                  iCurBattleScore = getBattleScoreFromString(title.substring(title.indexOf("for attacker is")));

                  if (conquerStr.toString().contains(" win, taking ")) {
                    sCurrentWinner = getFirstWord(conquerStr.toString().trim(), " ");
                  }
                  saveBlockText(JBGConstants.HI_BATTLE_TITLE, parsingPlayerName, getRemainingUnits(conquerStr.toString()));

                  if (conquerStr.toString().contains(" a stalemate")) {
                    sCurrentWinner = "STALEMATE";
                  }

                  StringBuilder sumSb = new StringBuilder();
                  sumSb
                      .append(indent)
                      .append(conquerStr.toString())
                      .append(". Battle score ")
                      .append(title.substring(title.indexOf("for attacker is")));
                  stringBuilder.append(sumSb.toString()).append('\n');

                  conquerStr = new StringBuilder();
                  // separate units by player and show casualty summary
                  final IntegerMap<GamePlayer> unitCount = new IntegerMap<>();
                  unitCount.add(unit.getOwner(), 1);
                  while (objIter.hasNext()) {
                    unit = (Unit) objIter.next();
                    unitCount.add(unit.getOwner(), 1);
                  }

                  boolean bCasualtiesAdded = false;
                  for (final GamePlayer player : unitCount.keySet()) {
                    saveBlockText(JBGConstants.HI_BATTLE_TITLE, parsingPlayerName, new StringBuilder(
                      JBGConstants.HI_TAG_BATTLE_CASUALTIES + 
                      player.getName() + "|" +
                      MyFormatter.unitsToTextNoOwner(allUnitsInDetails, player)
                      ).toString());
                    stringBuilder.append(new StringBuilder(
                        indent + "Casualties for " + player.getName() +
                        ": " + MyFormatter.unitsToTextNoOwner(allUnitsInDetails, player)
                        ).toString()).append('\n');
                    bCasualtiesAdded = true;
                  }
                  if (bCasualtiesAdded) {
                    //successfully defend and attack, manually adjust here
                    if (sCurrentWinner.length() < 1 && sCurrentDefender.length() > 0 && iCurBattleScore < 0) {
                      sCurrentWinner = sCurrentDefender;
                    }
                    else if (sCurrentWinner.length() < 1 && sCurrentAttacker.length() > 0 && iCurBattleScore > 1) {
                      sCurrentWinner = sCurrentAttacker;
                    }

                    saveBlockText(JBGConstants.HI_BATTLE_TITLE, parsingPlayerName,
                    new StringBuilder(
                        JBGConstants.HI_TAG_BATTLE_SUMM + 
                        sCurrentWinner +
                        "|" +
                        sCurrentAttacker +
                        "|" +
                        sCurrentDefender +
                        "|" +
                        String.valueOf(iCurBattleScore) + 
                        "|" +
                        String.valueOf(iCurRolls)
                      ).toString());
                  }
                } else if (title.matches(".*? placed in .*")
                    || title.matches(".* owned by the \\w+ retreated to .*")) {
                    saveBlockText(JBGConstants.HI_BATTLE_TITLE, parsingPlayerName, title);
                  stringBuilder.append(indent).append(title).append('\n');
                } else if (title.matches("\\w+ win")) {
                  conquerStr =
                      new StringBuilder(
                          title
                              + conquerStr
                              + " with "
                              + MyFormatter.unitsToTextNoOwner(allUnitsInDetails)
                              + " remaining");
//saveBlockText(JBGConstants.HI_BATTLE_TITLE, parsingPlayerName, conquerStr.toString());
                } else {
                  //other type 1
stringBuilder.append(" -- This is other1 --\n");

                  stringBuilder.append(indent).append(title).append('\n');
                }
              } else {
                // collection of unhandled objects
stringBuilder.append(" -- This is unhandled1 --\n");
                stringBuilder.append(indent).append(title).append('\n');
              }
            } else {
              // empty collection of something
              if (title.matches("\\w+ win")) {
                conquerStr = new StringBuilder(title + conquerStr + " with no units remaining");
                saveBlockText(JBGConstants.HI_BATTLE_TITLE, parsingPlayerName, conquerStr.toString());
              } else {
                // empty collection of unhandled objects
stringBuilder.append(" -- This is unhandled2 --\n");
                stringBuilder.append(indent).append(title).append('\n');
              }
            }
          } else if (details instanceof Territory) {
            // territory details
            if (isFirstWordEquals(title, " ", "Battle"))
            {
              String s = extractBattleLocation(title);
              if (s.length() < 1)
                s = "UNKNOWN LOC";
              saveBlockText(JBGConstants.HI_BATTLE_TITLE, parsingPlayerName, s);

              //reset
              sCurrentAttacker = "";
              sCurrentDefender = "";
              sCurrentWinner = "";

            }
            stringBuilder.append(indent).append(title).append('\n');
          } else if (details == null) {
            if (titleNeedsFurtherProcessing(title)) {
              if (title.matches("\\w+ collect \\d+ PUs?.*")) {
                stringBuilder.append(indent).append(title).append('\n');
              } else if (title.matches("\\w+ takes? .*? from \\w+")) {
                // British take Libya from Germans
                if (moving) {
                  final String str = moveList.remove(moveList.size() - 1);
                  moveList.add(str + "\n  " + indent + title.replaceAll(" takes ", " take "));
                } else {
                  conquerStr.append(title.replaceAll("^\\w+ takes ", ", taking "));
                }
              } else if (title.matches("\\w+ spend \\d+ on tech rolls")) {
                stringBuilder.append(indent).append(title).append('\n');
              } else if (!title.startsWith("Rolls to resolve tech hits:")) {
                stringBuilder.append(indent).append(title).append('\n');
              }
            }
          } else {
            // unknown details object
            //place units
            stringBuilder.append(indent).append(title).append('\n');
            if (title.contains(" placed "))
              saveBlockText(JBGConstants.HI_PLACE_TITLE, parsingPlayerName, title);
          }
        } else if (node instanceof Step) {
          final GamePlayer gamePlayer = ((Step) node).getPlayerId();
          if (!title.equals("Initializing Delegates")) {
stringBuilder.append(" -- This is A class -").append(title).append("-").append(parsingPlayerName).append("-\n");
            stringBuilder.append('\n').append(indent).append(title);

            if (title.equals(JBGConstants.HI_NONKOMBAT_MOVE_TITLE)) {
              saveBlockText(title, parsingPlayerName, JBGConstants.HI_TAG_DUMMY_START);
              bNonCombatPhase = true; //this is after Combat Move, so I use this flag to separate move iter process above
            }
            else if (title.equals(JBGConstants.HI_KOMBAT_MOVE_TITLE))
              saveBlockText(title, parsingPlayerName, JBGConstants.HI_TAG_DUMMY_START);

            if (title.equals(JBGConstants.HI_PURCHASE_TITLE))
              saveBlockText(title, parsingPlayerName, JBGConstants.HI_TAG_DUMMY_START);
            if (title.equals(JBGConstants.HI_BATTLE_TITLE))
              saveBlockText(title, parsingPlayerName, JBGConstants.HI_TAG_DUMMY_START);

            if (title.equals(JBGConstants.HI_PLACE_TITLE))
              saveBlockText(title, parsingPlayerName, JBGConstants.HI_TAG_DUMMY_START);

            if (gamePlayer != null) {
              currentPlayer = gamePlayer;
              players.add(currentPlayer);
stringBuilder.append(" -- This is unknown detail 2 --\n");
              stringBuilder.append(" - ").append(gamePlayer.getName());
            }
            stringBuilder.append('\n');
          }
        } else if (node instanceof Round) {
stringBuilder.append(" -- This is B class --\n");
          stringBuilder.append('\n').append(indent).append(title).append('\n');
        } else {
stringBuilder.append(" -- This is C class --\n");
          stringBuilder.append(indent).append(title).append('\n');
        }
      }
      curNode = curNode.getNextSibling();
    } while ((curNode instanceof Step) && players.contains(((Step) curNode).getPlayerId()));

    // if we are mid-phase, this might not get flushed
    if (moving && !moveList.isEmpty()) {
      final Iterator<String> moveIter = moveList.iterator();
      while (moveIter.hasNext()) {
stringBuilder.append(" -- This is unknown detail 3 --\n");

        stringBuilder.append(moveIter.next()).append('\n');
        moveIter.remove();
      }
    }
    stringBuilder.append('\n');
    if (verbose) {
      stringBuilder.append("Combat Hit Differential Summary :\n\n");
      for (final String player : hitDifferentialMap.keySet()) {
stringBuilder.append(" -- This is unknown detail 4 --\n");
        stringBuilder
            .append(moreIndent)
            .append(player)
            .append(" : ")
            .append(String.format("%.2f", hitDifferentialMap.get(player)))
            .append('\n');
      }
    }
    stringBuilder.append('\n');
  }

  private static boolean titleNeedsFurtherProcessing(final String title) {
    return !(title.equals("Adding original owners")
        || title.equals(MoveDelegate.CLEANING_UP_DURING_MOVEMENT_PHASE)
        || title.equals("Game Loaded")
        || title.contains("now being played by")
        || title.contains("Turn Summary")
        || title.contains("Move Summary")
        || title.contains("Setting uses for triggers used")
        || title.equals("Resetting and Giving Bonus Movement to Units")
        || title.equals("Recording Battle Statistics")
        || title.equals("Preparing Airbases for Possible Scrambling"));
  }

  @VisibleForTesting
  static String parseHitDifferentialKeyFromDiceRollMessage(final String message) {
    final Pattern diceRollPattern = Pattern.compile("^(.+) roll(?: (.+))? dice");
    final Matcher matcher = diceRollPattern.matcher(message);
    if (matcher.find()) {
      return matcher.group(1) + " " + Optional.ofNullable(matcher.group(2)).orElse("regular");
    }

    final int lastColonIndex = message.lastIndexOf(" :");
    return (lastColonIndex != -1) ? message.substring(0, lastColonIndex) : message;
  }

  /**
   * Adds a territory summary for the player associated with {@code printNode} to the log. The
   * summary includes each unit present in the territory.
   */
  public void printTerritorySummary(final HistoryNode printNode, final GameData data) {
    final GamePlayer player = getPlayerId(printNode);
    if (territories == null) return;
    final Collection<GamePlayer> players = new HashSet<>();
    players.add(player);
    printTerritorySummary(players, territories);
  }

  private void printTerritorySummary(final GameData data) {
    final GamePlayer player;

    if (territories == null) return;

    data.acquireReadLock();
    try {
      player = data.getSequence().getStep().getPlayerId();
    } finally {
      data.releaseReadLock();
    }
    final Collection<GamePlayer> players = new HashSet<>();
    players.add(player);
    printTerritorySummary(players, territories);
  }

  /**
   * Adds a territory summary for each player in {@code allowedPlayers} to the log. The summary
   * includes each unit present in the territory.
   */
  public void printTerritorySummary(
      final GameData data, final Collection<GamePlayer> allowedPlayers) {
    if (allowedPlayers == null || allowedPlayers.isEmpty()) {
      printTerritorySummary(data);
      return;
    }
    final Collection<Territory> territories;
    data.acquireReadLock();
    try {
      territories = data.getMap().getTerritories();
    } finally {
      data.releaseReadLock();
    }
    printTerritorySummary(allowedPlayers, territories);
  }

  private void printTerritorySummary(
      final Collection<GamePlayer> players, final Collection<Territory> territories) {
    if (players == null || players.isEmpty() || territories == null || territories.isEmpty()) {
      return;
    }
    // print all units in all territories, including "flags"
    stringBuilder
        .append("Territory Summary for ")
        .append(MyFormatter.defaultNamedToTextList(players))
        .append(" : \n\n");
    for (final Territory t : territories) {
      final List<Unit> ownedUnits =
          t.getUnitCollection().getMatches(Matches.unitIsOwnedByOfAnyOfThesePlayers(players));
      // see if there's a flag
      final TerritoryAttachment ta = TerritoryAttachment.get(t);
      final boolean hasFlag =
          ta != null
              && t.getOwner() != null
              && players.contains(t.getOwner())
              && (ta.getOriginalOwner() == null || !players.contains(ta.getOriginalOwner()));
      if (hasFlag || !ownedUnits.isEmpty()) {
        stringBuilder.append("    ").append(t.getName()).append(" : ");
        if (hasFlag && ownedUnits.isEmpty()) {
          stringBuilder.append("1 flag").append('\n');
        } else if (hasFlag) {
          stringBuilder.append("1 flag, ");
        }
        if (!ownedUnits.isEmpty()) {
          stringBuilder.append(MyFormatter.unitsToTextNoOwner(ownedUnits)).append('\n');
        }
      }
    }
    stringBuilder.append('\n');
    stringBuilder.append('\n');
  }

  public void printDiceStatistics(final GameData data, final IRandomStats randomStats) {
    final RandomStatsDetails stats = randomStats.getRandomStats(data.getDiceSides());
    final String diceStats = stats.getAllStatsString();
    if (diceStats.length() > 0) {
      stringBuilder.append(diceStats).append('\n').append('\n').append('\n');
    }
  }

  /** Adds a production summary for each player in the game to the log. */
  public void printProductionSummary(final GameData data) {
    final Collection<GamePlayer> players;
    final Resource pus;
    data.acquireReadLock();
    try {
      pus = data.getResourceList().getResource(Constants.PUS);
      players = data.getPlayerList().getPlayers();
    } finally {
      data.releaseReadLock();
    }
    if (pus == null) {
      return;
    }
    stringBuilder.append("Production/PUs Summary :\n").append('\n');
    for (final GamePlayer player : players) {
      final int pusQuantity = player.getResources().getQuantity(pus);
      final int production = getProduction(player, data);
      stringBuilder
          .append("    ")
          .append(player.getName())
          .append(" : ")
          .append(production)
          .append(" / ")
          .append(pusQuantity)
          .append('\n');
    }
    stringBuilder.append('\n').append('\n');
  }

  private static int getProduction(final GamePlayer player, final GameData data) {
    int production = 0;
    for (final Territory place : data.getMap().getTerritories()) {
      boolean isConvoyOrLand = false;
      final TerritoryAttachment ta = TerritoryAttachment.get(place);
      if (!place.isWater()
          || (ta != null
              && !GamePlayer.NULL_PLAYERID.equals(OriginalOwnerTracker.getOriginalOwner(place))
              && player.equals(OriginalOwnerTracker.getOriginalOwner(place))
              && place.getOwner().equals(player))) {
        isConvoyOrLand = true;
      }
      if (place.getOwner().equals(player) && isConvoyOrLand && ta != null) {
        production += ta.getProduction();
      }
    }
    return production;
  }
}
