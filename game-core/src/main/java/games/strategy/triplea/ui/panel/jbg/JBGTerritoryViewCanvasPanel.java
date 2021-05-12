package games.strategy.triplea.ui.panel.jbg;

/**
 *
 * @author silveira, from http://silveiraneto.net/2008/04/27/simple-java-tileset-example/
 * modify by TienTN for this game
 */

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.event.*;
import javax.swing.JPanel;
import games.strategy.triplea.ResourceLoader;
import java.nio.file.Path;
import javax.swing.Timer;
import java.util.List;
import java.util.ArrayList;
import games.strategy.engine.data.JBGConstants;
import games.strategy.engine.data.JBGCanvasItem;
import java.util.concurrent.ThreadLocalRandom;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.Rectangle;
import java.awt.Point;
import lombok.Getter;
import lombok.Setter;

import games.strategy.engine.data.JBGTerritoryMapBuilder;
import games.strategy.triplea.ui.JBGTerritoryManagerPanel;
import games.strategy.triplea.util.UnitCategory;

public class JBGTerritoryViewCanvasPanel extends JPanel 
    implements ActionListener {
    private static final long serialVersionUID = 5004515340964828564L;

    private static final int MOVING_LEFT_CAR_ROW = 392;
    private static final int MOVING_RIGHT_CAR_ROW = 412;

    private JBGTerritoryManagerPanel refMaster = null;

    private static int iCanvasWidth = 0;
    private static int iCanvasHeight = 0;
    private Timer tm;
    private long lStartTm = System.currentTimeMillis();
    private static final int DEFAULT_TIMER_CLOCK = 70;
    private static final int FAST_ANIM_DURATION = 1; //minimum delay is 1, mean it always change with each TIMER_CLOCK cycle
    private Image tileset;
    private int iCloud1X, iCloud1Y;
    private int iCloud2X, iCloud2Y;
    private int iCarSetRight1X, iCarSetRight1Y;
    private int iCarSetRight2X, iCarSetRight2Y;

    private boolean flgFirstPaintDone = false;

    private String territoryName;
    public String sInstanceMessage;
    private List<JBGConstants.Tile> map;
    private String sResBuildings;
    private String sEcoBuildings; 
    private List<JBGConstants.Tile> lstResearchBuildings;
    private List<JBGConstants.Tile> lstEconomicBuildings;

    private List<JBGCanvasItem> allMovingObjects;
    private List<JBGCanvasItem> movingRightCars;
    private List<JBGCanvasItem> movingLeftCars;
    private List<JBGCanvasItem> movingClouds;

    @Getter private Rectangle rcResIcon = null;
    @Getter private Rectangle rcEcoIcon = null;
    @Getter private Rectangle rcWorkIcon = null;
    @Getter private Rectangle rcProdIcon = null;
    @Getter private Rectangle rcNewsIcon = null;

    int iEcoLevel = 0;
    int iResLevel = 0;
    int iProdLevel = 0;
    int iJCoin = 0;
    String sBasicInfo = null;
    @Setter private boolean factoryExists = false;
    @Setter private int bunkerTotal = 0;

    private List<UnitCategory> units = null;

    public JBGTerritoryViewCanvasPanel(JBGTerritoryManagerPanel master) {

        refMaster = master;
        tileset = ResourceLoader.loadImageFromJBGAssert(Path.of("territory", "territory_tile_set.png"));
 
        iCanvasWidth = JBGConstants.MAP_HORZ_TILES * JBGConstants.TILE_WIDTH;
        iCanvasHeight = JBGConstants.MAP_VERT_TILES * JBGConstants.TILE_HEIGHT;
        this.setPreferredSize(new Dimension(iCanvasWidth, iCanvasHeight));
        this.setMinimumSize(new Dimension(iCanvasWidth, iCanvasHeight));

        this.setFocusable(true);

        this.addMouseListener(new MouseAdapter() {
            private Color background;

            @Override
            public void mousePressed(MouseEvent e) {
                JBGTerritoryViewCanvasPanel panel = JBGTerritoryViewCanvasPanel.this;
                panel.sInstanceMessage = String.valueOf(e.getX()) + " " + String.valueOf(e.getY());
                panel.displayMouseInfo();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                JBGTerritoryViewCanvasPanel panel = JBGTerritoryViewCanvasPanel.this;
                panel.sInstanceMessage = String.valueOf(e.getX()) + " " + String.valueOf(e.getY());
                panel.displayMouseInfo();
                if (isInRect(e.getX(), e.getY(), panel.getRcResIcon())) {
                    refMaster.openTerritoryBuild(panel);
                    //refMaster.showAlert(panel, "Click build research");
                }
                if (isInRect(e.getX(), e.getY(), panel.getRcEcoIcon())) {
                    refMaster.showAlert(panel, "Click build economy");
                }
                if (isInRect(e.getX(), e.getY(), panel.getRcWorkIcon())) {
                    refMaster.showKanjiMatchGameDialog(panel);
                }
                if (isInRect(e.getX(), e.getY(), panel.getRcProdIcon())) {
                    refMaster.showAlert(panel, "Click production set");
                }
                if (isInRect(e.getX(), e.getY(), panel.getRcNewsIcon())) {
                    refMaster.buildNewspaper(panel);
                }
            }
        });

    }

    public void updateInfo(
        final String territoryName,
        final String sResBuildings, final String sEcoBuildings,
        final int iEcoLevel, final int iResLevel, final int iProdLevel, final int iJCoin, 
        final String sBasicInfo) {

        this.territoryName = territoryName;

        this.sResBuildings = sResBuildings;
        this.sEcoBuildings = sEcoBuildings;
        this.iEcoLevel = iEcoLevel;
        this.iResLevel = iResLevel;
        this.iProdLevel = iProdLevel;
        this.iJCoin = iJCoin;
        this.sBasicInfo = sBasicInfo;

        //regenerate
        this.map = JBGTerritoryMapBuilder.getInstance().buildMapBackground(JBGConstants.MAP_HORZ_TILES, JBGConstants.MAP_VERT_TILES, false, false);
        this.lstResearchBuildings = JBGTerritoryMapBuilder.getInstance().buildResBuildingList(JBGConstants.MAP_HORZ_TILES, JBGConstants.MAP_VERT_TILES, sResBuildings);
        this.lstEconomicBuildings = JBGTerritoryMapBuilder.getInstance().buildEcoBuildingList(JBGConstants.MAP_HORZ_TILES, JBGConstants.MAP_VERT_TILES, sEcoBuildings);

        buildMovingObjects();

        if (flgFirstPaintDone) {
            repaint();
        }
    }

    public void displayMouseInfo() {
        repaint();
    }

    private void buildMovingObjects() {
        this.allMovingObjects = new ArrayList<JBGCanvasItem>();
        this.allMovingObjects.addAll(buildCloudObjects(iEcoLevel));
        this.allMovingObjects.addAll(buildTrafficObjects(iEcoLevel));
    }

    private int getRandom(int from, int to) {
        return ThreadLocalRandom.current().nextInt(from, to+1);
    }

    private List<JBGCanvasItem> buildCloudObjects(int iEco) {
        List<JBGCanvasItem> movingClouds = new ArrayList<JBGCanvasItem>();
        int iX = 0;
        int iY = JBGConstants.TILE_HEIGHT;

        int iTotalCloud = 15;
        if (iEco <= 5) {
            iTotalCloud = 4;
        }
        else if (iEco <= 10) {
            iTotalCloud = 8;
        }
        else if (iEco <= 15) {
            iTotalCloud = 12;
        }
        for (int i = 0; i < iTotalCloud; i++) {
            //minimum delay is 1, mean it always change with the standard timer here
            JBGCanvasItem item = new JBGCanvasItem(
                getRandom(0, iCanvasWidth - JBGConstants.TILE_WIDTH), iY + (i*10), DEFAULT_TIMER_CLOCK*i, 1, 0, JBGConstants.Tile.CLOUD, 
                iCanvasWidth, iCanvasHeight, JBGConstants.TILE_WIDTH, JBGConstants.TILE_HEIGHT,
                true);
            movingClouds.add(item);
        }
        return movingClouds;
    }

    private List<JBGCanvasItem> buildTrafficObjects(int iEco) {
        List<JBGCanvasItem> movingCars = new ArrayList<JBGCanvasItem>();

        JBGConstants.Tile goRightSet = JBGConstants.Tile.CARSET_GO_RIGHT_4;
        JBGConstants.Tile goLeftSet = JBGConstants.Tile.CARSET_GO_LEFT_4;
        int iTotalCarPerDirection = 15;
        int iSpaceBetweenCarSet = 15;
        if (iEco <= 5) {
            goRightSet = JBGConstants.Tile.CARSET_GO_RIGHT_1;
            goLeftSet = JBGConstants.Tile.CARSET_GO_LEFT_1;
            iTotalCarPerDirection = 4;
            iSpaceBetweenCarSet = 60;
        }
        else if (iEco <= 10) {
            goRightSet = JBGConstants.Tile.CARSET_GO_RIGHT_2;
            goLeftSet = JBGConstants.Tile.CARSET_GO_LEFT_2;
            iTotalCarPerDirection = 8;
            iSpaceBetweenCarSet = 40;
        }
        else if (iEco <= 15) {
            goRightSet = JBGConstants.Tile.CARSET_GO_RIGHT_3;
            goLeftSet = JBGConstants.Tile.CARSET_GO_LEFT_3;
            iTotalCarPerDirection = 10;
            iSpaceBetweenCarSet = 20;
        }

        int iX = 0;
        int iY = MOVING_RIGHT_CAR_ROW;
        for (int i = 0; i < iTotalCarPerDirection; i++) {
            //minimum delay is 1, mean it always change with the standard timer here
            JBGCanvasItem item = new JBGCanvasItem(
                iX + (JBGConstants.TILE_WIDTH*i) + (i*iSpaceBetweenCarSet), iY, FAST_ANIM_DURATION, 1, 0, goRightSet, 
                iCanvasWidth, iCanvasHeight, JBGConstants.TILE_WIDTH, JBGConstants.TILE_HEIGHT,
                true);
            movingCars.add(item);
        }

        iX = iCanvasWidth-JBGConstants.TILE_WIDTH;
        iY = MOVING_LEFT_CAR_ROW;
        for (int i = 0; i < iTotalCarPerDirection; i++) {
            //minimum delay is 1, mean it always change with the standard timer here
            JBGCanvasItem item = new JBGCanvasItem(
                iX - ( (JBGConstants.TILE_WIDTH*i) + (i*iSpaceBetweenCarSet) ), iY, FAST_ANIM_DURATION, -1, 0, goLeftSet,
                iCanvasWidth, iCanvasHeight, JBGConstants.TILE_WIDTH, JBGConstants.TILE_HEIGHT,
                true);
            movingCars.add(item);
        }

        return movingCars;
    }

    public void setUnits(List<UnitCategory> units) {
        this.units = units;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);


        g.setColor(Color.black);
        g.fillRect(0, 0, iCanvasWidth, iCanvasHeight);

        drawMapBackground(g);

        //static additional images
        //drawSomeTreeOnHorizon(g);

        if (this.factoryExists) {
            drawFactory1(g, 0, 9);
            drawFactory1(g, 2, 9);
            drawFactory2(g, 4, 9);
            drawFactory2(g, 6, 9);
        }
        if (this.bunkerTotal > 0) {
            for (int i = 0; i < this.bunkerTotal; i++) {
              if (i >= JBGConstants.MAP_HORZ_TILES) break;
              drawBunker(g, i+1, 5);
            }
        }
        drawResearchBuildings(g);
        drawEconomyBuildings(g);

        drawMapUnits(g, 5);

        if (!flgFirstPaintDone) {
            flgFirstPaintDone = true;
            tm = new Timer(DEFAULT_TIMER_CLOCK, this);
            tm.start();
        }
        else {
            //draw other moving element on the background
            drawAllMovingObjects(g);
        }

        draw2DItemsOnTop(g);

        Toolkit.getDefaultToolkit().sync();
    }

    private void drawMultilineString(Graphics2D g, String text, int x, int y) {
        //this kind of text is from TerritoryAttachment.toStringForBasic ... 
        //but I don't want to change that file, for easier engine update
        for (String line : text.split("<br>", 20)) {
            if (line.indexOf("&nbsp;") >= 0) {
                line = line.replace("&nbsp;", "  ");
            }
            g.drawString(line, x, y += g.getFontMetrics().getHeight());
        }
    }

    private void drawUnitCount(Graphics2D g, int count, int x, int y) {
        g.setFont(new Font("Arial", Font.PLAIN, 8));
        g.setColor(Color.YELLOW);
        int iTextX = x - (JBGConstants.TILE_WIDTH + g.getFontMetrics().getHeight());
        int iTextY = (y + JBGConstants.TILE_HEIGHT) - (g.getFontMetrics().getHeight() / 2);
        g.drawString(String.format("(%d)", count), x, iTextY);
    }

    private void draw2DItemsOnTop(Graphics g) {
        Graphics2D eg = (Graphics2D) g;

        eg.setFont(new Font("Arial", Font.PLAIN, 12));
        eg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        eg.setColor(Color.DARK_GRAY);
 
        drawMultilineString(eg, this.sBasicInfo, 2, 0);

        if (this.sInstanceMessage != null && this.sInstanceMessage.length() > 0) {
          eg.drawString(this.sInstanceMessage, 200, 10);            
        }

        rcWorkIcon = drawIconInTile(eg, 3, 270, 0);
        eg.drawString(String.valueOf(this.iJCoin), 300, 10);

        rcResIcon = drawIconInTile(eg, 0, 350, 0);
        eg.drawString(String.valueOf(this.iResLevel), 380, 10);
        rcEcoIcon = drawIconInTile(eg, 1, 400, 0);
        eg.drawString(String.valueOf(this.iEcoLevel), 430, 10);
        rcProdIcon = drawIconInTile(eg, 2, 460, 0);
        eg.drawString(String.valueOf(this.iProdLevel), 490, 10);

        rcNewsIcon = drawIconInTile(eg, 4, 520, 0);

    }

    private boolean isInRect(int x, int y, Rectangle r) {
        if (r == null) return false;
        return r.contains(new Point(x, y));
    }

    private Rectangle drawIconInTile(Graphics g, int iconId, int x, int y) {
        int x_offset = 0;
        int y_offset = 0;
        int icon_width = 10;
        int icon_height = 10;
        int icon_show_width = 18;
        int icon_show_height = 18;
        JBGConstants.Tile t = JBGConstants.Tile.BUILD_ICONS_1;
        switch (iconId) {
        case 0:
            x_offset = 0;
            y_offset = 0;
            break;
        case 1:
            x_offset = 10;
            y_offset = 0;
            break;
        case 2: //prod
            x_offset = 20;
            y_offset = 0;
            break;
        case 3: //work
            x_offset = 0;
            y_offset = 10;
            break;
        case 4: //news
            x_offset = 10;
            y_offset = 10;
            break;
        }
        drawTilePart(g, t, x, y, x_offset, y_offset, icon_width, icon_height, icon_show_width, icon_show_height);

        return (new Rectangle(x, y, icon_show_width, icon_show_height));
    }

    private void drawTilePart(Graphics g, JBGConstants.Tile t, int x, int y, int x_offset, int y_offset, int w, int h, int show_w, int show_h){
        // map Tile from the tileset
        int mx = t.ordinal()%JBGConstants.TILESET_COLS;
        int my = t.ordinal()/JBGConstants.TILESET_ROWS;
        g.drawImage(tileset, 
            x, y, (x+show_w), (y+show_h), //where to draw and size of it
            (mx*JBGConstants.TILE_WIDTH)+x_offset, (my*JBGConstants.TILE_HEIGHT)+y_offset, //take image from  
            (mx*JBGConstants.TILE_WIDTH+w)+x_offset, (my*JBGConstants.TILE_HEIGHT+h)+y_offset, //to draw 
            this //observer
            );
    }

    protected void drawTile(Graphics g, JBGConstants.Tile t, int x, int y){
        // map Tile from the tileset
        int mx = t.ordinal()%JBGConstants.TILESET_COLS;
        int my = t.ordinal()/JBGConstants.TILESET_ROWS;
        if (mx > iCanvasWidth) return;
        if (my > iCanvasHeight) return;
        g.drawImage(tileset, 
            x, y, x+JBGConstants.TILE_WIDTH, y+JBGConstants.TILE_HEIGHT, //where to draw
            mx*JBGConstants.TILE_WIDTH, my*JBGConstants.TILE_HEIGHT,  
            mx*JBGConstants.TILE_WIDTH+JBGConstants.TILE_WIDTH, 
            my*JBGConstants.TILE_HEIGHT+JBGConstants.TILE_HEIGHT, //draw from 
            this);
    }

    protected void drawMapBackground(Graphics g) {

        Color startColor = new Color(192, 192, 192);
        Color endColor = new Color(130,187,101);
        int iStartGradY = (JBGConstants.MAP_VERT_TILES - 13) * JBGConstants.TILE_HEIGHT;
        int iEndGradY = (JBGConstants.MAP_VERT_TILES - 8) * JBGConstants.TILE_HEIGHT;
        GradientPaint gradient = new GradientPaint(0, iStartGradY, startColor, 0, iEndGradY, endColor);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setPaint(gradient);
        g2d.fillRect(0, iStartGradY, iCanvasWidth, iEndGradY-iStartGradY);

        for(int i=0;i<JBGConstants.MAP_HORZ_TILES;i++) {

            for(int j=0;j<JBGConstants.MAP_VERT_TILES;j++) {
                int iRowCols = j*JBGConstants.MAP_HORZ_TILES;
                iRowCols += i;
                JBGConstants.Tile t = map.get(iRowCols);
                if (t != JBGConstants.Tile.EMPTY) {
                    drawTile(
                        g, map.get(iRowCols), 
                        i*JBGConstants.TILE_WIDTH, j*JBGConstants.TILE_HEIGHT
                        );
                }
            }

        }
    }

    protected void drawAllMovingObjects(Graphics g) {

        for (int i = 0; i < allMovingObjects.size(); i++) {
            JBGCanvasItem item = allMovingObjects.get(i);
            drawTile(g, item.tile, item.x, item.y);
        }

    }

    protected void drawResearchBuildings(Graphics g) {
        //we use 2 row to draw possible highrise buildings (or smoke over buildings)
        //top row
        int iDrawRowOnMap = 7; //from bottom of map
        for (int i = 0; i < JBGConstants.MAP_HORZ_TILES; i++) {
            drawTile(g, this.lstResearchBuildings.get(i), 
                (i*JBGConstants.TILE_WIDTH), 
                JBGConstants.TILE_HEIGHT*JBGConstants.MAP_VERT_TILES - iDrawRowOnMap*JBGConstants.TILE_HEIGHT);
        }
        //bottom row
        iDrawRowOnMap = 6; //from bottom of map
        for (int i = JBGConstants.MAP_HORZ_TILES; i < this.lstEconomicBuildings.size(); i++) {
            drawTile(g, this.lstResearchBuildings.get(i), 
                ((i-JBGConstants.MAP_HORZ_TILES)*JBGConstants.TILE_WIDTH), 
                JBGConstants.TILE_HEIGHT*JBGConstants.MAP_VERT_TILES - iDrawRowOnMap*JBGConstants.TILE_HEIGHT);
        }
    }

    protected void drawEconomyBuildings(Graphics g) {
        //we use 2 row to draw possible highrise buildings (or smoke over buildings)
        //top row
        int iDrawRowOnMap = 4; //from bottom of map
        for (int i = 0; i < JBGConstants.MAP_HORZ_TILES; i++) {
            drawTile(g, this.lstEconomicBuildings.get(i), 
                (i*JBGConstants.TILE_WIDTH), 
                JBGConstants.TILE_HEIGHT*JBGConstants.MAP_VERT_TILES - iDrawRowOnMap*JBGConstants.TILE_HEIGHT);
        }
        //bottom row
        iDrawRowOnMap = 3; //from bottom of map
        for (int i = JBGConstants.MAP_HORZ_TILES; i < this.lstEconomicBuildings.size(); i++) {
            drawTile(g, this.lstEconomicBuildings.get(i), 
                ((i-JBGConstants.MAP_HORZ_TILES)*JBGConstants.TILE_WIDTH), 
                JBGConstants.TILE_HEIGHT*JBGConstants.MAP_VERT_TILES - iDrawRowOnMap*JBGConstants.TILE_HEIGHT);
        }
    }

    protected void drawBunker(Graphics g, int x, int y) {
        drawTile(g, JBGConstants.Tile.BUNKER, (x*JBGConstants.TILE_WIDTH), JBGConstants.TILE_HEIGHT*y-4);        
    }

    protected void drawFactory1(Graphics g, int x, int y) {
        drawTile(g, JBGConstants.Tile.FACTORY11_TOP, iCanvasWidth-((x+2)*JBGConstants.TILE_WIDTH), JBGConstants.TILE_HEIGHT*y-4);
        drawTile(g, JBGConstants.Tile.FACTORY12_TOP, iCanvasWidth-((x+1)*JBGConstants.TILE_WIDTH), JBGConstants.TILE_HEIGHT*y-4);
        drawTile(g, JBGConstants.Tile.FACTORY11_BASE, iCanvasWidth-((x+2)*JBGConstants.TILE_WIDTH), JBGConstants.TILE_HEIGHT*(y+1)-4);
        drawTile(g, JBGConstants.Tile.FACTORY12_BASE, iCanvasWidth-((x+1)*JBGConstants.TILE_WIDTH), JBGConstants.TILE_HEIGHT*(y+1)-4);
    }

    protected void drawFactory2(Graphics g, int x, int y) {
        drawTile(g, JBGConstants.Tile.FACTORY21_TOP, iCanvasWidth-((x+2)*JBGConstants.TILE_WIDTH), JBGConstants.TILE_HEIGHT*y-4);
        drawTile(g, JBGConstants.Tile.FACTORY22_TOP, iCanvasWidth-((x+1)*JBGConstants.TILE_WIDTH), JBGConstants.TILE_HEIGHT*y-4);
        drawTile(g, JBGConstants.Tile.FACTORY21_BASE, iCanvasWidth-((x+2)*JBGConstants.TILE_WIDTH), JBGConstants.TILE_HEIGHT*(y+1)-4);
        drawTile(g, JBGConstants.Tile.FACTORY22_BASE, iCanvasWidth-((x+1)*JBGConstants.TILE_WIDTH), JBGConstants.TILE_HEIGHT*(y+1)-4);
    }

    protected void drawSomeTreeOnHorizon(Graphics g) {
        for (int i = 0; i < JBGConstants.MAP_HORZ_TILES; i++) {
            drawTile(g, JBGConstants.Tile.HORZ_TREE, (i*JBGConstants.TILE_WIDTH)+2, JBGConstants.TILE_HEIGHT*2+3);
        }
    }

    protected void drawMapUnits(Graphics g, int y) {

        int iUnitX = 4;
        int iUnitY = JBGConstants.TILE_HEIGHT*y + 8;
        int iUnitRow = 0;
        int iUnitRowIncrement = JBGConstants.TILE_HEIGHT;
        int iUnitColIncrement = JBGConstants.TILE_WIDTH - 10;
        Graphics2D g2d = (Graphics2D) g;

        for (final UnitCategory item : units) {

          String itemType = item.getType().getName();
          int itemSize = item.getUnits().size();
          //System.out.println(itemType + " " + String.valueOf(itemSize));

          int iUnitCount = itemSize;
          if (itemSize > 18) {
            iUnitCount = 18;
          }
          if (itemType.equals(JBGConstants.MAP_UNIT_INFANTRY) ) {
            int iDrawUnitX = iUnitX;
            int iDrawUnitY = iUnitY + (iUnitRowIncrement*iUnitRow);
            iUnitRow++;

            for (int i = 0; i < iUnitCount; i++) {
                drawTile(g, JBGConstants.Tile.INFANTRY, iDrawUnitX, iDrawUnitY);
                iDrawUnitX += iUnitColIncrement;
            }
            drawUnitCount(g2d, itemSize, iDrawUnitX, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_ELITE) ) {
            int iDrawUnitX = iUnitX;
            int iDrawUnitY = iUnitY + (iUnitRowIncrement*iUnitRow);
            iUnitRow++;

            for (int i = 0; i < iUnitCount; i++) {
                drawTile(g, JBGConstants.Tile.ELITE_INFANTRY, iDrawUnitX, iDrawUnitY);
                iDrawUnitX += iUnitColIncrement;
            }
            drawUnitCount(g2d, itemSize, iDrawUnitX, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_MARINE) ) {
            int iDrawUnitX = iUnitX;
            int iDrawUnitY = iUnitY + (iUnitRowIncrement*iUnitRow);
            iUnitRow++;

            for (int i = 0; i < iUnitCount; i++) {
                drawTile(g, JBGConstants.Tile.MARINE, iDrawUnitX, iDrawUnitY);
                iDrawUnitX += iUnitColIncrement;
            }
            drawUnitCount(g2d, itemSize, iDrawUnitX, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_MECH_INFANTRY)) {
            int iDrawUnitX = iUnitX;
            int iDrawUnitY = iUnitY + (iUnitRowIncrement*iUnitRow);
            iUnitRow++;

            for (int i = 0; i < iUnitCount; i++) {
                drawTile(g, JBGConstants.Tile.MECH_INFANTRY, iDrawUnitX, iDrawUnitY);
                iDrawUnitX += iUnitColIncrement;
            }
            drawUnitCount(g2d, itemSize, iDrawUnitX, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_TANK)) {
            int iDrawUnitX = iUnitX;
            int iDrawUnitY = iUnitY + (iUnitRowIncrement*iUnitRow);
            iUnitRow++;

            for (int i = 0; i < iUnitCount; i++) {
                drawTile(g, JBGConstants.Tile.TANK, iDrawUnitX, iDrawUnitY);
                iDrawUnitX += iUnitColIncrement;
            }
            drawUnitCount(g2d, itemSize, iDrawUnitX, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_APC) || itemType.equals(JBGConstants.MAP_UNIT_TANKETTE)) {
            int iDrawUnitX = iUnitX;
            int iDrawUnitY = iUnitY + (iUnitRowIncrement*iUnitRow);
            iUnitRow++;

            for (int i = 0; i < iUnitCount; i++) {
                drawTile(g, JBGConstants.Tile.APC, iDrawUnitX, iDrawUnitY);
                iDrawUnitX += iUnitColIncrement;
            }
            drawUnitCount(g2d, itemSize, iDrawUnitX, iDrawUnitY);            
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_FLAK)) {
            int iDrawUnitX = iUnitX;
            int iDrawUnitY = iUnitY + (iUnitRowIncrement*iUnitRow);
            iUnitRow++;

            for (int i = 0; i < iUnitCount; i++) {
                drawTile(g, JBGConstants.Tile.FLAK, iDrawUnitX, iDrawUnitY);
                iDrawUnitX += iUnitColIncrement;
            }
            drawUnitCount(g2d, itemSize, iDrawUnitX, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_ARTILLERY)) {
            int iDrawUnitX = iUnitX;
            int iDrawUnitY = iUnitY + (iUnitRowIncrement*iUnitRow);
            iUnitRow++;

            for (int i = 0; i < iUnitCount; i++) {
                drawTile(g, JBGConstants.Tile.ARTY, iDrawUnitX, iDrawUnitY);
                iDrawUnitX += iUnitColIncrement;
            }
            drawUnitCount(g2d, itemSize, iDrawUnitX, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_EARLY_FIGHTER)) {
                int iDrawUnitX = iCanvasWidth - (JBGConstants.TILE_WIDTH * 6);
                int iDrawUnitY = JBGConstants.TILE_HEIGHT*(JBGConstants.MAP_VERT_TILES - 2)-2;;
                drawTile(g, JBGConstants.Tile.FIGHTER, iDrawUnitX, iDrawUnitY);
                drawUnitCount(g2d, itemSize, iDrawUnitX+JBGConstants.TILE_WIDTH/2, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_FIGHTER)) {
                int iDrawUnitX = iCanvasWidth - (JBGConstants.TILE_WIDTH * 5);
                int iDrawUnitY = JBGConstants.TILE_HEIGHT*(JBGConstants.MAP_VERT_TILES - 2)-2;;
                drawTile(g, JBGConstants.Tile.FIGHTER, iDrawUnitX, iDrawUnitY);
                drawUnitCount(g2d, itemSize, iDrawUnitX+JBGConstants.TILE_WIDTH/2, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_FIGHTER2)) {
                int iDrawUnitX = iCanvasWidth - (JBGConstants.TILE_WIDTH * 4);
                int iDrawUnitY = JBGConstants.TILE_HEIGHT*(JBGConstants.MAP_VERT_TILES - 2)-2;;
                drawTile(g, JBGConstants.Tile.FIGHTER, iDrawUnitX, iDrawUnitY);
                drawUnitCount(g2d, itemSize, iDrawUnitX+JBGConstants.TILE_WIDTH/2, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_FIGHTER3)) {
                int iDrawUnitX = iCanvasWidth - (JBGConstants.TILE_WIDTH * 3);
                int iDrawUnitY = JBGConstants.TILE_HEIGHT*(JBGConstants.MAP_VERT_TILES - 2)-2;;
                drawTile(g, JBGConstants.Tile.FIGHTER, iDrawUnitX, iDrawUnitY);
                drawUnitCount(g2d, itemSize, iDrawUnitX+JBGConstants.TILE_WIDTH/2, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_BOMBER)) {
                int iDrawUnitX = iCanvasWidth - (JBGConstants.TILE_WIDTH * 2);
                int iDrawUnitY = JBGConstants.TILE_HEIGHT*(JBGConstants.MAP_VERT_TILES - 2)-2;;
                drawTile(g, JBGConstants.Tile.BOMBER, iDrawUnitX, iDrawUnitY);
                drawUnitCount(g2d, itemSize, iDrawUnitX+JBGConstants.TILE_WIDTH/2, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_BOMBER2)) {
                int iDrawUnitX = iCanvasWidth - (JBGConstants.TILE_WIDTH * 1);
                int iDrawUnitY = JBGConstants.TILE_HEIGHT*(JBGConstants.MAP_VERT_TILES - 2)-2;;
                drawTile(g, JBGConstants.Tile.BOMBER, iDrawUnitX, iDrawUnitY);
                drawUnitCount(g2d, itemSize, iDrawUnitX+JBGConstants.TILE_WIDTH/2, iDrawUnitY);
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_SHIP_SUBMARINE) ||
                    itemType.equals(JBGConstants.MAP_UNIT_SHIP_SUBMARINE2)
            ) {
            
          }
          else if (itemType.equals(JBGConstants.MAP_UNIT_SHIP_TORP_BOAT)) {
            
          }

        }
    }

    private boolean updateAllMovingObjects() {
        boolean flgChange = false;
        for (int i = 0; i < allMovingObjects.size(); i++) {
            if (allMovingObjects.get(i).change()) {
                flgChange = true;
            }
        }
        return flgChange;        
    }


    @Override
    public void actionPerformed(ActionEvent e) {

        boolean flgChange = false;

        flgChange = updateAllMovingObjects();

        if (flgChange) {
            repaint();
        }

    }

/*
    private void lowLatRepaint() {
        //Swing components are already double buffered
        //actually paintImmediately is not work with default Swing mechanism, but cause performance issue
        //so I skip it (will revisit)
        int iStartClipX, iStartClipY, iEndClipX, iEndClipY;

        if ( item.change() ) {
            iStartClipX = item.x;
            iStartClipY = item.y + 22; //only 10px from bottom
            if (item.x < -JBGConstants.TILE_WIDTH) {
                item.x = iCanvasWidth;
            }

            iEndClipY = item.y+JBGConstants.TILE_HEIGHT;
            if (item.x < 0) {
                iEndClipX = JBGConstants.TILE_WIDTH;
            }
            else {
                iEndClipX = item.x+JBGConstants.TILE_WIDTH;
            }

            paintImmediately(iStartClipX, iStartClipY, iEndClipX, iEndClipY);
        }
    }
*/

}
