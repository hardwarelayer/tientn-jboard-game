package games.strategy.engine.data;

//import com.google.common.collect.ImmutableMap; //google Guava (already in triplea's build.gradle)
import java.util.Map;
import static java.util.Map.entry;

public interface JBGConstants {
  enum Tile {
      GRASS, GRASS_STONE, GRASS_BAGS, CLOUD, HORZ_TREE, SKY_BG, SEA_BG, DESERT_BG, EMPTY, FIELDS,
      TREE, TREE_CHOMP, TREE_DEAD, MIDDLE_ROAD_H, MIDDLE_ROAD_CROSS, SAVANA, HILL, T17, VILLAGE2, VILLAGE1,
      ROAD_H, ROAD_V, ROAD_HV_DOWN, ROAD_HV_UP, ROAD_VH_RIGHT, ROAD_VH_LEFT, ROAD_CROSS, DESERT_FENCE_BG, GRASS_FENCE_BG, T29,
      APARTMENT_TOP, GOV_BLD_TOP, GEN_FACTORY_TOP, SCI_BLD_TOP, COMBINI_TOP, O_FACTORY_TOP, E_FACTORY_TOP, T37, T38, T39,
      APARTMENT_BASE, GOV_BLD_BASE, GEN_FACTORY_BASE, SCI_BLD_BASE, COMBINI_BASE, O_FACTORY_BASE, E_FACTORY_BASE, T47, T48, T49,
      FACTORY11_TOP, FACTORY12_TOP, FACTORY21_TOP, FACTORY22_TOP, BUSS_1, BUSS_2, HOSP_1, HOSP_2, MARK_1, MARK_2,
      FACTORY11_BASE, FACTORY12_BASE, FACTORY21_BASE, FACTORY22_BASE, BUNKER, BUSS_4, HOSP_3, HOSP_4, MARK_3, MARK_4,
      INFANTRY, TANK, APC, ARTY, FLAK, MECH_INFANTRY, ELITE_INFANTRY, MARINE, FIGHTER, BOMBER,
      T80, T81, T82, T83, T84, T85, T86, T87, T88, T89,
      CARSET_GO_RIGHT_4, CARSET_GO_RIGHT_3, CARSET_GO_RIGHT_2, CARSET_GO_RIGHT_1, CARSET_GO_LEFT_4, CARSET_GO_LEFT_3, CARSET_GO_LEFT_2, CARSET_GO_LEFT_1, BUILD_ICONS_1, T99
  };

  final int TILE_WIDTH = 32; // tile width
  final int TILE_HEIGHT = 32; // tile height
  final int MAP_HORZ_TILES = 20;
  final int MAP_VERT_TILES = 18;
  final int TILESET_COLS = 10; //size of Tile enum array, theory X
  final int TILESET_ROWS = 10; //size of Tile enum array, theory Y

  static final Map<Integer, JBGConstants.Tile> ECO_BUILD_MAP_ROW_TOP = Map.ofEntries(
    entry(0, JBGConstants.Tile.APARTMENT_TOP),
    entry(1, JBGConstants.Tile.APARTMENT_TOP),
    entry(2, JBGConstants.Tile.APARTMENT_TOP),
    entry(3, JBGConstants.Tile.APARTMENT_TOP),
    entry(4, JBGConstants.Tile.GOV_BLD_TOP),
    entry(5, JBGConstants.Tile.GOV_BLD_TOP),
    entry(6, JBGConstants.Tile.COMBINI_TOP),
    entry(7, JBGConstants.Tile.COMBINI_TOP),
    entry(8, JBGConstants.Tile.GEN_FACTORY_TOP),
    entry(9, JBGConstants.Tile.GEN_FACTORY_TOP),
    entry(10, JBGConstants.Tile.GEN_FACTORY_TOP),
    entry(11, JBGConstants.Tile.GEN_FACTORY_TOP),
    entry(12, JBGConstants.Tile.E_FACTORY_TOP),
    entry(13, JBGConstants.Tile.E_FACTORY_TOP),
    entry(14, JBGConstants.Tile.E_FACTORY_TOP),
    entry(15, JBGConstants.Tile.E_FACTORY_TOP),
    entry(16, JBGConstants.Tile.O_FACTORY_TOP),
    entry(17, JBGConstants.Tile.O_FACTORY_TOP),
    entry(18, JBGConstants.Tile.O_FACTORY_TOP),
    entry(19, JBGConstants.Tile.O_FACTORY_TOP)
    );

  static final Map<Integer, JBGConstants.Tile> ECO_BUILD_MAP_ROW_BOTTOM = Map.ofEntries(
    entry(0, JBGConstants.Tile.APARTMENT_BASE),
    entry(1, JBGConstants.Tile.APARTMENT_BASE),
    entry(2, JBGConstants.Tile.APARTMENT_BASE),
    entry(3, JBGConstants.Tile.APARTMENT_BASE),
    entry(4, JBGConstants.Tile.GOV_BLD_BASE),
    entry(5, JBGConstants.Tile.GOV_BLD_BASE),
    entry(6, JBGConstants.Tile.COMBINI_BASE),
    entry(7, JBGConstants.Tile.COMBINI_BASE),
    entry(8, JBGConstants.Tile.GEN_FACTORY_BASE),
    entry(9, JBGConstants.Tile.GEN_FACTORY_BASE),
    entry(10, JBGConstants.Tile.GEN_FACTORY_BASE),
    entry(11, JBGConstants.Tile.GEN_FACTORY_BASE),
    entry(12, JBGConstants.Tile.E_FACTORY_BASE),
    entry(13, JBGConstants.Tile.E_FACTORY_BASE),
    entry(14, JBGConstants.Tile.E_FACTORY_BASE),
    entry(15, JBGConstants.Tile.E_FACTORY_BASE),
    entry(16, JBGConstants.Tile.O_FACTORY_BASE),
    entry(17, JBGConstants.Tile.O_FACTORY_BASE),
    entry(18, JBGConstants.Tile.O_FACTORY_BASE),
    entry(19, JBGConstants.Tile.O_FACTORY_BASE)
    );

  static final Map<Integer, String> ECO_BUILD_NAMES = Map.ofEntries(
    entry(0, "Apartment"),
    entry(1, "Minishop"),
    entry(2, "Market"),
    entry(3, "Bike Shop"),
    entry(4, "Motorcycle Shop"),
    entry(5, "Automobile Shop"),
    entry(6, "Post Office"),
    entry(7, "Newspaper"),
    entry(8, "Workshop"),
    entry(9, "Gunshop"),
    entry(10, "Armoury"),
    entry(11, "Consumer Goods Workshop"),
    entry(12, "Small Weapon Workshop"),
    entry(13, "Apparel Workshop"),
    entry(14, "Logistic Center"),
    entry(15, "Minibank"),
    entry(16, "Local Bank"),
    entry(17, "Region Bank"),
    entry(18, "Central Bank"),
    entry(19, "Custom House")
    );


  static final Map<Integer, JBGConstants.Tile> RES_BUILD_MAP_ROW_TOP = Map.ofEntries(
    entry(0, JBGConstants.Tile.SCI_BLD_TOP),
    entry(1, JBGConstants.Tile.SCI_BLD_TOP),
    entry(2, JBGConstants.Tile.SCI_BLD_TOP),
    entry(3, JBGConstants.Tile.SCI_BLD_TOP),
    entry(4, JBGConstants.Tile.SCI_BLD_TOP),
    entry(5, JBGConstants.Tile.SCI_BLD_TOP),
    entry(6, JBGConstants.Tile.SCI_BLD_TOP),
    entry(7, JBGConstants.Tile.SCI_BLD_TOP),
    entry(8, JBGConstants.Tile.SCI_BLD_TOP),
    entry(9, JBGConstants.Tile.SCI_BLD_TOP),
    entry(10, JBGConstants.Tile.SCI_BLD_TOP),
    entry(11, JBGConstants.Tile.SCI_BLD_TOP),
    entry(12, JBGConstants.Tile.SCI_BLD_TOP),
    entry(13, JBGConstants.Tile.SCI_BLD_TOP),
    entry(14, JBGConstants.Tile.SCI_BLD_TOP),
    entry(15, JBGConstants.Tile.SCI_BLD_TOP),
    entry(16, JBGConstants.Tile.SCI_BLD_TOP),
    entry(17, JBGConstants.Tile.SCI_BLD_TOP),
    entry(18, JBGConstants.Tile.SCI_BLD_TOP),
    entry(19, JBGConstants.Tile.SCI_BLD_TOP)
    );

  static final Map<Integer, JBGConstants.Tile> RES_BUILD_MAP_ROW_BOTTOM = Map.ofEntries(
    entry(0, JBGConstants.Tile.SCI_BLD_BASE),
    entry(1, JBGConstants.Tile.SCI_BLD_BASE),
    entry(2, JBGConstants.Tile.SCI_BLD_BASE),
    entry(3, JBGConstants.Tile.SCI_BLD_BASE),
    entry(4, JBGConstants.Tile.SCI_BLD_BASE),
    entry(5, JBGConstants.Tile.SCI_BLD_BASE),
    entry(6, JBGConstants.Tile.SCI_BLD_BASE),
    entry(7, JBGConstants.Tile.SCI_BLD_BASE),
    entry(8, JBGConstants.Tile.SCI_BLD_BASE),
    entry(9, JBGConstants.Tile.SCI_BLD_BASE),
    entry(10, JBGConstants.Tile.SCI_BLD_BASE),
    entry(11, JBGConstants.Tile.SCI_BLD_BASE),
    entry(12, JBGConstants.Tile.SCI_BLD_BASE),
    entry(13, JBGConstants.Tile.SCI_BLD_BASE),
    entry(14, JBGConstants.Tile.SCI_BLD_BASE),
    entry(15, JBGConstants.Tile.SCI_BLD_BASE),
    entry(16, JBGConstants.Tile.SCI_BLD_BASE),
    entry(17, JBGConstants.Tile.SCI_BLD_BASE),
    entry(18, JBGConstants.Tile.SCI_BLD_BASE),
    entry(19, JBGConstants.Tile.SCI_BLD_BASE)
    );

  static final Map<Integer, String> RES_BUILD_NAMES = Map.ofEntries(
    entry(0, "Village library"),
    entry(1, "Town library"),
    entry(2, "City library"),
    entry(3, "Metropolitant library"),
    entry(4, "Region library"),
    entry(5, "Central library"),
    entry(6, "Elementary School"),
    entry(7, "Primary School"),
    entry(8, "College"),
    entry(9, "Univesity"),
    entry(10, "Experimental facility"),
    entry(11, "Sanitary lab"),
    entry(12, "Electronic lab"),
    entry(13, "Weapon lab"),
    entry(14, "Flight Research lab"),
    entry(15, "Logistic Research lab"),
    entry(16, "Mass transport lab"),
    entry(17, "Army School"),
    entry(18, "Army College"),
    entry(19, "Army University")
    );

  static final int KANJI_TOTAL_SUBSET_SIZE = 20;
  static final int KANJI_MIN_TEST_CORRECT = 10;
  static final String KANJI_COMMA_DELIMITER = ",";

  //for jCoint price calculation
  static final double BUILDING_ECO_PRICE_MULTIPLIER = 0.75;
  static final double BUILDING_RES_PRICE_MULTIPLIER = 1.0;
  static final int BUILDING_ECO_PRICE_MINIMUM = 10;
  static final int BUILDING_RES_PRICE_MINIMUM = 20;

  //turn history parser constants
  static final String HI_KOMBAT_MOVE_TITLE = "Combat Move";
  static final String HI_NONKOMBAT_MOVE_TITLE = "Non Combat Move";
  static final String HI_PURCHASE_TITLE = "Purchase Units";
  static final String HI_BATTLE_TITLE = "Combat";
  static final String HI_PLACE_TITLE = "Place Units";

  static final String HI_CSV_SEPARATOR = "|";
  static final String HI_PLACE_SEPARATOR = " placed in ";

  static final String HI_TAG_DUMMY_START = "@START";
  static final String HI_PURCHASE_PATT = " buy ";
  static final String HI_BATTLE_LOC_TITLE = "Battle in ";
  static final String HI_TAG_BATTLE_SUMM = "@BATTLESUM:";
  static final String HI_BATTLE_REM_UNIT_PAT1 = " win "; //" remaining. ";
  static final String HI_BATTLE_REM_UNIT_PAT2 = " with ";
  static final String HI_TAG_BATTLE_REMM = "@BATTLEREM:";
  static final String HI_TAG_BATTLE_CASUALTIES = "@BATTLECAT:";
  static final String HI_BATTLE_ATK_PATT = " attack with ";
  static final String HI_BATTLE_DEF_PATT = " defend with ";
  static final String HI_TAG_BATTLE_ATK = "@BATTLEATK:";
  static final String HI_TAG_BATTLE_DEF = "@BATTLEDEF:";
  static final String HI_TAG_START_BATTLE_LOC = "@BATTLELOC:";
  static final String HI_MOVE_FROM_PATT = " moved from ";
  static final String HI_MOVE_TO_PATT = " to ";
  static final String HI_MOVE_TAKE_PATT = " take ";
  static final String HI_TAG_MOVE_TAKE_PROV = "@MOVETAKEN:";
  static final String HI_ROUND_IDX_PATT = ", round ";
  static final String HI_NONE_VAL = "None";

  //player turn order types
  static final int TURN_SEQ_FIRST = 0;
  static final int TURN_SEQ_MIDDLE = 1;
  static final int TURN_SEQ_LAST = 2;

  //map units
  static final String MAP_UNIT_INFANTRY = "Infantry";
  static final String MAP_UNIT_ELITE = "Elite";
  static final String MAP_UNIT_MARINE = "Marine";
  static final String MAP_UNIT_MECH_INFANTRY = "Mech.Inf";
  static final String MAP_UNIT_TANK = "Tank";
  static final String MAP_UNIT_APC = "ArmoredCar";
  static final String MAP_UNIT_TANKETTE = "Tankette";
  static final String MAP_UNIT_FLAK = "AAGun";
  static final String MAP_UNIT_ARTILLERY = "Artillery";
  static final String MAP_UNIT_EARLY_FIGHTER = "EarlyFighter";
  static final String MAP_UNIT_FIGHTER = "L.Fighter";
  static final String MAP_UNIT_FIGHTER2 = "Fighter";
  static final String MAP_UNIT_FIGHTER3 = "Adv.Fighter";
  static final String MAP_UNIT_BOMBER = "Bomber";
  static final String MAP_UNIT_BOMBER2 = "S.Bomber";
  static final String MAP_UNIT_SHIP_TORP_BOAT = "T.Boat";
  static final String MAP_UNIT_SHIP_SUBMARINE = "Submarine";
  static final String MAP_UNIT_SHIP_SUBMARINE2 = "S.Submarine";
  static final String MAP_UNIT_SHIP_CRUISER = "Cruiser";
  static final String MAP_UNIT_SHIP_CARRIER = "Carrier";
  static final String MAP_UNIT_SHIP_CARRIER2 = "B.Carrier";
  static final String MAP_UNIT_SHIP_TRANSPORT = "Transport";
  static final String MAP_UNIT_SHIP_TRANSPORT2 = "B.Transport";
  static final String MAP_UNIT_SHIP_DESTROYER = "Destroyer";
  static final String MAP_UNIT_SHIP_BATTLESHIP = "Battleship";

  //territory frontline icon, use in TerritoryAttachment
  static final int MAX_FRONTLINE_ICON_VISIBLE_TURNS = 4;

  //turn news
  static final String JBGTURN_NEWS_SMALLATTACK_PREFIX = "JTN_SMALL_ATTK_PREFX";
  static final String JBGTURN_NEWS_PAPER_NAME = "Tien's World War Edition";

  //JBG
  static final String JBG_NO_COST_CARE_RULE = "JBG_NO_COST_CARE";
  //

}