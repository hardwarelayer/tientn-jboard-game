package games.strategy.engine.data;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import games.strategy.triplea.Properties;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.Matches;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.triplea.java.collections.CollectionUtils;
import games.strategy.engine.data.JBGConstants;
import java.time.Instant;

@Log
public class JBGTerritoryMapBuilder {
  private static final long serialVersionUID = -79061939642779999L;

  private static JBGTerritoryMapBuilder mb = null;
  private static Instant timestamp = Instant.EPOCH;
  public static final String TREE_OBJECT = "tree";

  /** Creates new Unit. Owner can be null. */
  public JBGTerritoryMapBuilder() {
  }

  private List<JBGConstants.Tile> buildSkyHorizon(int type, int w) {
    List<JBGConstants.Tile> horzList = new ArrayList();
    for (int i = 0; i < w; i++) {
      horzList.add(JBGConstants.Tile.SKY_BG);
    }
    return horzList;
  }

  private List<JBGConstants.Tile> buildRoadHorizon(int type, int w) {
    List<JBGConstants.Tile> horzList = new ArrayList();
    for (int i = 0; i < w; i++) {
      horzList.add(JBGConstants.Tile.ROAD_H);
    }
    return horzList;
  }

  private List<JBGConstants.Tile> buildHillHorizon(int type, int w) {
    List<JBGConstants.Tile> horzList = new ArrayList();
    for (int i = 0; i < w; i++) {
      horzList.add(JBGConstants.Tile.HILL);
    }
    return horzList;
  }

  private List<JBGConstants.Tile> buildNeighourhoodHorizon(int type, int w, int h) {
      List<JBGConstants.Tile> resList = new ArrayList();
      for (int i = 0; i < w; i++) {
        for (int j = 0; j < h; j++) {
          resList.add(JBGConstants.Tile.FIELDS);
        }
      }
      return resList;
  }

  private List<JBGConstants.Tile> buildSeaHorizon(int type, int w, int h) {
      List<JBGConstants.Tile> resList = new ArrayList();
      for (int i = 0; i < w; i++) {
        for (int j = 0; j < h; j++) {
          resList.add(JBGConstants.Tile.SEA_BG);
        }
      }
      return resList;
  }

  private List<JBGConstants.Tile> buildDesertHorizon(int type, int w, int h) {
      List<JBGConstants.Tile> resList = new ArrayList();
      for (int i = 0; i < w; i++) {
        for (int j = 0; j < h; j++) {
          resList.add(JBGConstants.Tile.DESERT_BG);
        }
      }
      return resList;
  }

  private List<JBGConstants.Tile> buildHorzCityRoad(int type, int w, int h) {
    List<JBGConstants.Tile> lst = buildRoadHorizon(type, w);
    return lst;
  }

  private List<JBGConstants.Tile> buildEmptyGround(int w, int h, boolean flgHasDesert) {
      List<JBGConstants.Tile> resList = new ArrayList();

      int iTopEmptyRows = h - 6; //from below: 2 rows for eco + 1 rows for middle road + 2 rows for res + 1 row for fence
      for (int i = 0; i < w; i++) {
        for (int j = 0; j < iTopEmptyRows; j++) {
          if (flgHasDesert) {
            resList.add(JBGConstants.Tile.DESERT_BG);
          }
          else {
            resList.add(JBGConstants.Tile.EMPTY);
          }
        }
      }

      //fence ground
      for (int i = 0; i < w; i++) {
        for (int j = 0; j < 1; j++) {
          if (flgHasDesert) {
            resList.add(JBGConstants.Tile.DESERT_FENCE_BG);
          }
          else {
            resList.add(JBGConstants.Tile.GRASS_FENCE_BG);
          }
        }
      }

      //res buildings ground
      for (int i = 0; i < w; i++) {
        for (int j = 0; j < 2; j++) {
          if (flgHasDesert) {
            resList.add(JBGConstants.Tile.DESERT_BG);
          }
          else {
            resList.add(JBGConstants.Tile.GRASS);
          }
        }
      }

      //middle road
      int iMiddleRoadLength = 0;
      for (int i = 0; i < 4; i++) {
        resList.add(JBGConstants.Tile.MIDDLE_ROAD_H);
      }
      iMiddleRoadLength+=4;
      resList.add(JBGConstants.Tile.MIDDLE_ROAD_CROSS);
      iMiddleRoadLength++;
      for (int i = 0; i < 4; i++) {
        resList.add(JBGConstants.Tile.MIDDLE_ROAD_H);
      }
      iMiddleRoadLength+=4;
      resList.add(JBGConstants.Tile.MIDDLE_ROAD_CROSS);
      iMiddleRoadLength++;
      for (int i = 0; i < 4; i++) {
        resList.add(JBGConstants.Tile.MIDDLE_ROAD_H);
      }
      iMiddleRoadLength+=4;
      resList.add(JBGConstants.Tile.MIDDLE_ROAD_CROSS);
      iMiddleRoadLength++;
      for (int i = 0; i < JBGConstants.MAP_HORZ_TILES - iMiddleRoadLength; i++) {
        resList.add(JBGConstants.Tile.MIDDLE_ROAD_H);       
      }

      //eco buildings ground
      for (int i = 0; i < w; i++) {
        for (int j = 0; j < 2; j++) {
          if (flgHasDesert) {
            resList.add(JBGConstants.Tile.DESERT_BG);
          }
          else {
            resList.add(JBGConstants.Tile.GRASS);
          }
        }
      }

      return resList;
  }

  private List<JBGConstants.Tile> buildCityBackground(int type, int w, int h, boolean flgHasSea, boolean flgHasDesert) {
    List<JBGConstants.Tile> lst = new ArrayList();

    int iTotalRows = 0;
    int iTotalEmptyRows = 0;
    int iTotalBuildableRows = h - 2; //save 2 bottom rows

    iTotalEmptyRows = iTotalBuildableRows;

    List<JBGConstants.Tile> emptyGroundMap = buildEmptyGround(w, iTotalEmptyRows, flgHasDesert);
    lst.addAll(emptyGroundMap);
    iTotalRows += iTotalEmptyRows;

    //next to last row
    if (iTotalRows == iTotalBuildableRows) {
      //bottom road, above last row
      lst.addAll(buildHorzCityRoad(type, w, h));
      iTotalRows++;
    }

    //last row
    if (iTotalRows < h) {
      if (flgHasDesert) {
        lst.addAll(buildDesertHorizon(0, w, 1));
      }
      else if (flgHasSea) {
        lst.addAll(buildSeaHorizon(0, w, 1));
      }
      else {
        lst.addAll(buildNeighourhoodHorizon(0, w, 1));
      }
      iTotalRows++;
    }

    return lst;

  }

  //must be called through getInstance()
  public static List<JBGConstants.Tile> buildMapBackground(int w, int h, boolean flgHasSea, boolean flgHasDesert) {
    List<JBGConstants.Tile> map = new ArrayList();

    int iRemainRows = JBGConstants.MAP_VERT_TILES;

    List<JBGConstants.Tile> skyline1 = mb.buildSkyHorizon(0, w);
    List<JBGConstants.Tile> skyline2 = mb.buildSkyHorizon(0, w);
    List<JBGConstants.Tile> skyline3 = mb.buildSkyHorizon(0, w);
    List<JBGConstants.Tile> skyline4 = mb.buildSkyHorizon(0, w);
    List<JBGConstants.Tile> hillBelowSky = mb.buildHillHorizon(0, w);

    //remember addAll only add reference to the list
    map.addAll(skyline1);
    iRemainRows--;
    map.addAll(skyline2);
    iRemainRows--;
    map.addAll(skyline3);
    iRemainRows--;
    map.addAll(skyline4);
    iRemainRows--;
    map.addAll(hillBelowSky);
    iRemainRows--;

    int iCityMapHeight = iRemainRows;
    List<JBGConstants.Tile> cityBackground = mb.buildCityBackground(0, w, iCityMapHeight, flgHasSea, flgHasDesert);
    map.addAll(cityBackground);
    iRemainRows-= iCityMapHeight;

    return map;

  }

  private int str2Int(final String sVal) {
    int iValue = 0;
    try {
      iValue = Integer.parseInt(sVal);
    }
    catch(NumberFormatException e) {
      System.out.println("JBGTerritoryMapBuilder.str2Int:error" + e.getMessage());
    }
    return iValue;
  }

  private List<Integer> parseBuildingListString(final String sBlds) {
    List<Integer> iItems = new ArrayList();
    String[] arrBlds = sBlds.split("\\|", 20); //if not escape, it will use pipe for regexp so the result is strange
    for (String b : arrBlds) {
      if (b.length() < 1) b = "0";
      int iValue = str2Int(b);
      iItems.add(iValue);
    }
    return iItems;
  }

  public List<JBGConstants.Tile> buildResBuildingList(int w, int h, final String sResBuildings) {
    List<JBGConstants.Tile> resList = new ArrayList();
    List<JBGConstants.Tile> resListTop = new ArrayList();
    List<JBGConstants.Tile> resListBottom = new ArrayList();

    List<Integer> lstBlds = parseBuildingListString(sResBuildings);
    for (int i = 0; i < lstBlds.size(); i++) {
      if (lstBlds.get(i) > 0) {
        resListTop.add(JBGConstants.RES_BUILD_MAP_ROW_TOP.get(i));
        resListBottom.add(JBGConstants.RES_BUILD_MAP_ROW_BOTTOM.get(i));
      }
      else {
        resListTop.add(JBGConstants.Tile.VILLAGE2);
        resListBottom.add(JBGConstants.Tile.VILLAGE2);
      }
    }
    resList.addAll(resListTop);
    resList.addAll(resListBottom);
    return resList;
  }

  public List<JBGConstants.Tile> buildEcoBuildingList(int w, int h, final String sEcoBuildings) {
    List<JBGConstants.Tile> resList = new ArrayList();
    List<JBGConstants.Tile> resListTop = new ArrayList();
    List<JBGConstants.Tile> resListBottom = new ArrayList();

    List<Integer> lstBlds = parseBuildingListString(sEcoBuildings);
    for (int i = 0; i < lstBlds.size(); i++) {
      if (lstBlds.get(i) > 0) {
        resListTop.add(JBGConstants.ECO_BUILD_MAP_ROW_TOP.get(i));
        resListBottom.add(JBGConstants.ECO_BUILD_MAP_ROW_BOTTOM.get(i));
      }
      else {
        resListTop.add(JBGConstants.Tile.VILLAGE2);
        resListBottom.add(JBGConstants.Tile.VILLAGE2);
      }
    }
    resList.addAll(resListTop);
    resList.addAll(resListBottom);
    return resList;
  }

  public static JBGTerritoryMapBuilder getInstance() {
    // cache properties for 5 seconds
    if (mb == null || timestamp.plusSeconds(5).isBefore(Instant.now())) {
      mb = new JBGTerritoryMapBuilder();
      timestamp = Instant.now();
    }
    return mb;
  }

}
