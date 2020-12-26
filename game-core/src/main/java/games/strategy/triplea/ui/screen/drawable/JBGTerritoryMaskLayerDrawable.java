package games.strategy.triplea.ui.screen.drawable;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.triplea.ResourceLoader;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.formatter.MyFormatter;
import games.strategy.triplea.image.MapImage;
import games.strategy.triplea.ui.UiContext;
import games.strategy.triplea.ui.mapdata.MapData;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;
import java.nio.file.Path;
import java.util.Map;

import games.strategy.triplea.delegate.battle.BattleTracker;
import games.strategy.triplea.delegate.DelegateFinder;

/** Draws the name, comments, and production value for the associated territory. */
public class JBGTerritoryMaskLayerDrawable extends AbstractDrawable {
  private final String territoryName;
  private final UiContext uiContext;
  private Rectangle territoryBounds;
  private Territory territory;
  private Image frontlineImage = null;
  private TerritoryAttachment ta;

  public JBGTerritoryMaskLayerDrawable(final Territory ter, final UiContext uiContext, final TerritoryAttachment tAttach) {
    this.territory = ter;
    this.territoryName = ter.getName();
    this.uiContext = uiContext;
    this.ta = tAttach;
  }

  public JBGTerritoryMaskLayerDrawable(final Territory ter, final UiContext uiContext) {
    this.territory = ter;
    this.territoryName = ter.getName();
    this.uiContext = uiContext;
  }

  @Override
  public void draw(
      final Rectangle bounds,
      final GameData data,
      final Graphics2D graphics,
      final MapData mapData) {

    if (ta != null) {
//System.out.println("JBGTerritoryMaskLayerDrawable: draw with TA:" + territoryName);
      drawWithTA(bounds, data, graphics, mapData);
    }
    /*
    else {
System.out.println("JBGTerritoryMaskLayerDrawable: draw no TA:" + territoryName);
      drawWithGameData(bounds, data, graphics, mapData, territoryName);
    }*/
  }

  private void drawNoTA(
      final Rectangle bounds,
      final GameData data,
      final Graphics2D graphics,
      final MapData mapData) {

    final boolean drawFromTopLeft = mapData.drawNamesFromTopLeft();
    boolean drawComments = false;
    boolean wasBattleField = wasBattleField(data, territory);
    boolean enableDrawing = false;
    String commentText = null;


    graphics.setFont(MapImage.getPropertyMapFont());
    graphics.setColor(MapImage.getPropertyTerritoryNameAndPuAndCommentColor());
    final FontMetrics fm = graphics.getFontMetrics();

    // if we specify a placement point, use it otherwise try to center it
    final int x;
    final int y;
    final Optional<Point> namePlace = mapData.getNamePlacementPoint(territory);
    if (namePlace.isPresent()) {
      x = namePlace.get().x;
      y = namePlace.get().y;
    } else {
      if (territoryBounds == null) {
        // Cache the bounds since re-computing it is expensive.
        territoryBounds = getBestTerritoryNameRect(mapData, territory, fm);
      }
      x =
          territoryBounds.x
              + (int) territoryBounds.getWidth() / 2
              - fm.stringWidth(territory.getName()) / 2;
      y = territoryBounds.y + (int) territoryBounds.getHeight() / 2 + fm.getAscent() / 2;
    }

    int iCurrentInternalTurn = data.getJbgInternalTurnStep();
    if (wasBattleField) {
      //draw
      if (this.frontlineImage == null) {
        this.frontlineImage = ResourceLoader.loadImageFromJBGAssert(Path.of("territory", "frontline_icon.png"));
      } 
      draw(
            bounds,
            graphics,
            x, y - 10,
            this.frontlineImage,
            "Frontline",
            drawFromTopLeft);

      if (mapData.drawTerritoryNames()
          && mapData.shouldDrawTerritoryName(territoryName)
          && !territory.isWater()) {
        //final Image nameImage = mapData.getTerritoryNameImages().get(territory.getName());
        //draw(bounds, graphics, x, y+10, nameImage, "Frontline"/*territory.getName()*/, drawFromTopLeft);
      }

    }
  }

  private void drawWithGameData(
      final Rectangle bounds,
      final GameData data,
      final Graphics2D graphics,
      final MapData mapData,
      final String tName) {

    final boolean drawFromTopLeft = mapData.drawNamesFromTopLeft();
    boolean drawComments = false;
    boolean wasBattleField = wasBattleField(data, territory);
    boolean enableDrawing = false;
    String commentText = null;


    graphics.setFont(MapImage.getPropertyMapFont());
    graphics.setColor(MapImage.getPropertyTerritoryNameAndPuAndCommentColor());
    final FontMetrics fm = graphics.getFontMetrics();

    // if we specify a placement point, use it otherwise try to center it
    final int x;
    final int y;
    final Optional<Point> namePlace = mapData.getNamePlacementPoint(territory);
    if (namePlace.isPresent()) {
      x = namePlace.get().x;
      y = namePlace.get().y;
    } else {
      if (territoryBounds == null) {
        // Cache the bounds since re-computing it is expensive.
        territoryBounds = getBestTerritoryNameRect(mapData, territory, fm);
      }
      x =
          territoryBounds.x
              + (int) territoryBounds.getWidth() / 2
              - fm.stringWidth(territory.getName()) / 2;
      y = territoryBounds.y + (int) territoryBounds.getHeight() / 2 + fm.getAscent() / 2;
    }

    int iCurrentInternalTurn = 0;
    int iTerritoryLastBattleTurn = -1;
    try {
      data.acquireReadLock();

      iCurrentInternalTurn = data.getJbgInternalTurnStep();
      Map<String, Integer> lastBattleTurnOfTerritories = data.getLastBattleTurnOfTerritories();

      if (wasBattleField) {
        //save state for territory, so the effect can be used for a controllable number of turns
        if (lastBattleTurnOfTerritories.containsKey(tName)) {
          iTerritoryLastBattleTurn = lastBattleTurnOfTerritories.get(tName);
        }

        lastBattleTurnOfTerritories.put(tName, iCurrentInternalTurn);
        enableDrawing = true;
      }
      else if (iCurrentInternalTurn > 0 && iTerritoryLastBattleTurn > 0) {

          if (iTerritoryLastBattleTurn + 2 > iCurrentInternalTurn) {
            enableDrawing = true;
          }
          else {
            //expired and no new battle, reset
            lastBattleTurnOfTerritories.put(tName, 0);
          }

      }

    }
    finally {
      data.releaseReadLock();
    }

    if (enableDrawing) {
      //draw
      if (this.frontlineImage == null) {
        this.frontlineImage = ResourceLoader.loadImageFromJBGAssert(Path.of("territory", "frontline_icon.png"));
      } 
      draw(
            bounds,
            graphics,
            x, y - 10,
            this.frontlineImage,
            "Frontline",
            drawFromTopLeft);

      if (mapData.drawTerritoryNames()
          && mapData.shouldDrawTerritoryName(territoryName)
          && !territory.isWater()) {
        //final Image nameImage = mapData.getTerritoryNameImages().get(territory.getName());
        //draw(bounds, graphics, x, y+10, nameImage, "Frontline"/*territory.getName()*/, drawFromTopLeft);
      }

    }
  }

  private void drawWithTA(
      final Rectangle bounds,
      final GameData data,
      final Graphics2D graphics,
      final MapData mapData) {

    final boolean drawFromTopLeft = mapData.drawNamesFromTopLeft();
    boolean drawComments = false;
    boolean wasBattleField = wasBattleField(data, territory);
    boolean enableDrawing = false;
    String commentText = null;


    graphics.setFont(MapImage.getPropertyMapFont());
    graphics.setColor(MapImage.getPropertyTerritoryNameAndPuAndCommentColor());
    final FontMetrics fm = graphics.getFontMetrics();

    // if we specify a placement point, use it otherwise try to center it
    final int x;
    final int y;
    final Optional<Point> namePlace = mapData.getNamePlacementPoint(territory);
    if (namePlace.isPresent()) {
      x = namePlace.get().x;
      y = namePlace.get().y;
    } else {
      if (territoryBounds == null) {
        // Cache the bounds since re-computing it is expensive.
        territoryBounds = getBestTerritoryNameRect(mapData, territory, fm);
      }
      x =
          territoryBounds.x
              + (int) territoryBounds.getWidth() / 2
              - fm.stringWidth(territory.getName()) / 2;
      y = territoryBounds.y + (int) territoryBounds.getHeight() / 2 + fm.getAscent() / 2;
    }

    int iCurrentInternalTurn = data.getJbgInternalTurnStep();
    int iTerritoryLastBattleTurn = ta.getTurnOfLastBattle();

    if (wasBattleField) {
      //save state to territory, so the effect can be used for a controllable number of turns
      ta.manualSetTurnOfLastBattle(String.valueOf(iCurrentInternalTurn));
      enableDrawing = true;
    }
    else if (iCurrentInternalTurn >= 0) {
      if (ta.isLastBattleInEffect(iCurrentInternalTurn)) {
        enableDrawing = true;
      }
      else {
        //expired or not set
        ta.manualSetTurnOfLastBattle("0");
      }

    }

    if (enableDrawing) {
      //draw
      if (this.frontlineImage == null) {
        this.frontlineImage = ResourceLoader.loadImageFromJBGAssert(Path.of("territory", "frontline_icon.png"));
      } 
      draw(
            bounds,
            graphics,
            x, y - 10,
            this.frontlineImage,
            "Frontline",
            drawFromTopLeft);

      if (mapData.drawTerritoryNames()
          && mapData.shouldDrawTerritoryName(territoryName)
          && !territory.isWater()) {
        //final Image nameImage = mapData.getTerritoryNameImages().get(territory.getName());
        //draw(bounds, graphics, x, y+10, nameImage, "Frontline"/*territory.getName()*/, drawFromTopLeft);
      }

    }
  }
  protected boolean wasBattleField(final GameData data, final Territory t) {
    final BattleTracker tracker = DelegateFinder.battleDelegate(data).getBattleTracker();
    return tracker.wasConquered(t) ||
            tracker.wasBattleFought(t) || 
              tracker.wasBlitzed(t);
  }

  private static void draw(
      final Rectangle bounds,
      final Graphics2D graphics,
      final int x,
      final int y,
      final Image img,
      final String prod,
      final boolean drawFromTopLeft) {
    int normalizedY = y;
    if (img == null) {
      if (graphics.getFont().getSize() <= 0) {
        return;
      }
      if (drawFromTopLeft) {
        final FontMetrics fm = graphics.getFontMetrics();
        normalizedY += fm.getHeight();
      }
      graphics.drawString(prod, x - bounds.x, normalizedY - bounds.y);
    } else {
      // we want to be consistent
      // drawString takes y as the base line position
      // drawImage takes x as the top right corner
      if (!drawFromTopLeft) {
        normalizedY -= img.getHeight(null);
      }
      graphics.drawImage(img, x - bounds.x, normalizedY - bounds.y, null);
    }
  }

  /**
   * Find the best rectangle inside the territory to place the name in. Finds the rectangle that can
   * fit the name, that is the closest to the vertical center, and has a large width at that
   * location. If there isn't any rectangles that can fit the name then default back to the bounding
   * rectangle.
   */
  private static Rectangle getBestTerritoryNameRect(
      final MapData mapData, final Territory territory, final FontMetrics fontMetrics) {

    // Find bounding rectangle and parameters for creating a grid (20 x 20) across the territory
    final Rectangle territoryBounds = mapData.getBoundingRect(territory);
    Rectangle result = territoryBounds;
    final int maxX = territoryBounds.x + territoryBounds.width;
    final int maxY = territoryBounds.y + territoryBounds.height;
    final int centerY = territoryBounds.y + territoryBounds.height / 2;
    final int incrementX = (int) Math.ceil(territoryBounds.width / 20.0);
    final int incrementY = (int) Math.ceil(territoryBounds.height / 20.0);
    final int nameWidth = fontMetrics.stringWidth(territory.getName());
    final int nameHeight = fontMetrics.getAscent();
    int maxScore = 0;

    // Loop through the grid moving the starting point and determining max width at that point
    for (int x = territoryBounds.x; x < maxX - nameWidth; x += incrementX) {
      for (int y = territoryBounds.y; y < maxY - nameHeight; y += incrementY) {
        for (int endX = maxX; endX > x; endX -= incrementX) {
          final Rectangle rectangle = new Rectangle(x, y, endX - x, nameHeight);

          // Ranges from 0 when at very top or bottom of territory to height/2 when at vertical
          // center
          final int verticalDistanceFromEdge =
              territoryBounds.height / 2 - Math.abs(centerY - nameHeight - y);

          // Score rectangle based on how close to vertical center and territory width at location
          final int score = verticalDistanceFromEdge * rectangle.width;

          // Check to make sure rectangle is contained in the territory
          if (rectangle.width > nameWidth
              && score > maxScore
              && isRectangleContainedInTerritory(rectangle, territory, mapData)) {
            maxScore = score;
            result = rectangle;
            break;
          }
        }
      }
    }
    return result;
  }

  private static boolean isRectangleContainedInTerritory(
      final Rectangle rectangle, final Territory territory, final MapData mapData) {
    final List<Polygon> polygons = mapData.getPolygons(territory.getName());
    for (final Polygon polygon : polygons) {
      if (polygon.contains(rectangle)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public DrawLevel getLevel() {
    return DrawLevel.TERRITORY_OVERLAY_LEVEL;
  }
}
