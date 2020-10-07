package games.strategy.triplea.ui;

import static games.strategy.triplea.image.UnitImageFactory.ImageKey;
import games.strategy.triplea.ui.UiContext;
import games.strategy.triplea.ui.TooltipProperties;

import com.google.common.collect.ImmutableList;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.TerritoryEffect;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.Properties;
import games.strategy.triplea.attachments.TechAttachment;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.TerritoryEffectHelper;
import games.strategy.triplea.delegate.AbstractMoveDelegate.MoveType;
import games.strategy.triplea.delegate.BaseEditDelegate;
import games.strategy.triplea.delegate.GameStepPropertiesHelper;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TransportTracker;
import games.strategy.triplea.delegate.UnitComparator;
import games.strategy.triplea.delegate.battle.ScrambleLogic;
import games.strategy.triplea.delegate.data.MustMoveWithDetails;
import games.strategy.triplea.delegate.move.validation.MoveValidator;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.ui.AbstractMovePanel;
import games.strategy.triplea.ui.DefaultMapSelectionListener;
import games.strategy.triplea.ui.MouseDetails;
import games.strategy.triplea.ui.SimpleUnitPanel;
import games.strategy.triplea.ui.TripleAFrame;
import games.strategy.triplea.ui.UndoableMovesPanel;
import games.strategy.triplea.ui.UnitChooser;
import games.strategy.triplea.ui.panels.map.MapPanel;
import games.strategy.triplea.ui.panels.map.MapSelectionListener;
import games.strategy.triplea.ui.panels.map.MouseOverUnitListener;
import games.strategy.triplea.ui.panels.map.UnitSelectionListener;
import games.strategy.triplea.ui.unit.scroller.UnitScroller;
import games.strategy.triplea.util.TransportUtils;
import games.strategy.triplea.util.UnitCategory;
import games.strategy.triplea.util.UnitSeparator;
import games.strategy.ui.OverlayIcon;

import games.strategy.triplea.ui.UiContext;
import games.strategy.triplea.ui.mapdata.MapData;

import games.strategy.triplea.ui.screen.drawable.BaseMapDrawable;
import games.strategy.triplea.ui.screen.drawable.BattleDrawable;
import games.strategy.triplea.ui.screen.drawable.BlockadeZoneDrawable;
import games.strategy.triplea.ui.screen.drawable.CapitolMarkerDrawable;
import games.strategy.triplea.ui.screen.drawable.ConvoyZoneDrawable;
import games.strategy.triplea.ui.screen.drawable.DecoratorDrawable;
import games.strategy.triplea.ui.screen.drawable.IDrawable;
import games.strategy.triplea.ui.screen.drawable.KamikazeZoneDrawable;
import games.strategy.triplea.ui.screen.drawable.LandTerritoryDrawable;
import games.strategy.triplea.ui.screen.drawable.ReliefMapDrawable;
import games.strategy.triplea.ui.screen.drawable.SeaZoneOutlineDrawable;
import games.strategy.triplea.ui.screen.drawable.TerritoryEffectDrawable;
import games.strategy.triplea.ui.screen.drawable.TerritoryNameDrawable;
import games.strategy.triplea.ui.screen.drawable.VcDrawable;
import games.strategy.ui.Util;
import org.triplea.util.Tuple;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
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
import lombok.Getter;
import lombok.Setter;
import org.triplea.java.ObjectUtils;
import org.triplea.java.PredicateBuilder;
import org.triplea.java.collections.CollectionUtils;
import org.triplea.java.collections.IntegerMap;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.jpanel.JPanelBuilder;
import javax.swing.SwingConstants;
import org.triplea.swing.key.binding.KeyCode;
import org.triplea.swing.key.binding.SwingKeyBinding;

/** Orchestrates the rendering of all map tiles. */
public class TerritoryManagerPanel  extends ActionPanel {

  private static final long serialVersionUID = 5004515340964828564L;
  private final UiContext uiContext;

  public TerritoryManagerPanel(final GameData data, final MapPanel map, final UiContext uiContext) {
    super(data, map);
    this.uiContext = uiContext;
  }

  @Override
  public void performDone() {
    //techRoll = null;
    release();
  }

  @Override
  public String toString() {
    return "TerritoryManagerPanel";
  }

  //JBG
  public void showJBGTerritoryInfo(
      final Territory t, final MouseDetails mouseDetails) {

      final String text = "移動するユニットを選択する、出発 " + t.getName();

      final JScrollPane unitsPane = new JScrollPane();

      final TerritoryAttachment ta = TerritoryAttachment.get(t);

      final JTextField jEcoLevel = new JTextField(3);
      final JTextField jResLevel = new JTextField(3);
      final JTextField jProdLevel = new JTextField(3);

      int iEcoLevel = 0;
      int iResLevel = 0;
      int iProdLevel = 0; 
      if (ta != null) {
        iEcoLevel = ta.getEconomyLevel();
        iResLevel = ta.getResearchLevel();
        iProdLevel = ta.getProduction();
      }
      else {
        iEcoLevel = TerritoryAttachment.getEconomyLevel(t);
        iResLevel = TerritoryAttachment.getResearchLevel(t);
        iProdLevel = TerritoryAttachment.getProduction(t);
      }
      jEcoLevel.setText(String.valueOf(iEcoLevel));
      jResLevel.setText(String.valueOf(iResLevel));
      jProdLevel.setText(String.valueOf(iProdLevel));
      final JPanel editPanel = valueModifyInTerritoryPanel(jEcoLevel, jResLevel, jProdLevel);

      unitsPane.setBorder(BorderFactory.createEmptyBorder());
      unitsPane.getVerticalScrollBar().setUnitIncrement(20);
      final JPanel terPanel = unitsInTerritoryPanel(t, editPanel, uiContext);
      unitsPane.setViewportView(terPanel);

      final int option =
          JOptionPane.showOptionDialog(
              JOptionPane.getFrameForComponent(TerritoryManagerPanel.this),
              unitsPane,
              text,
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE,
              null,
              null,
              null);
      if (option != JOptionPane.OK_OPTION) {
        return;
      }
      final String sEcoVal = jEcoLevel.getText();
      final String sResVal = jResLevel.getText();
      final String sProdVal = jProdLevel.getText();
      //int iVal = Integer.parseInt(sVal);
      if (ta != null) {
        ta.manualSetProduction(sProdVal);
        ta.manualSetEconomyLevel(sEcoVal);
        ta.manualSetResearchLevel(sResVal);              
      }
      else {
        TerritoryAttachment.manualSetProduction(t, sProdVal);
        TerritoryAttachment.manualSetEconomyLevel(t, sEcoVal);
        TerritoryAttachment.manualSetResearchLevel(t, sResVal);              
      }
  }

  //JBG
  private JPanel valueModifyInTerritoryPanel(Component objEco, Component objRes, Component objProd) {
    final JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(2, 20, 2, 2));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    final JLabel lEcoLevel = new JLabel();
    final JLabel lResLevel = new JLabel();
    final JLabel lProdLevel = new JLabel();

    lEcoLevel.setText("Economy Set:");
    lResLevel.setText("Research Set:");
    lProdLevel.setText("Production Set:");
    panel.add(lEcoLevel);
    panel.add(objEco);
    panel.add(lResLevel);
    panel.add(objRes);
    panel.add(lProdLevel);
    panel.add(objProd);

    return panel;

  }

  //JBG
  private JPanel unitsInTerritoryPanel(
      final Territory territory, 
      final Component cusObject,
      final UiContext uiContext) {
    final JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(2, 20, 2, 2));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    final List<UnitCategory> units =
        UnitSeparator.getSortedUnitCategories(territory, uiContext.getMapData());
    @Nullable GamePlayer currentPlayer = null;


    final JLabel territoryInfo = new JLabel();
    final JLabel unitInfo = new JLabel();
    final TerritoryAttachment ta = TerritoryAttachment.get(territory);
    final String labelText;
    if (ta == null) {
      labelText = "<html>" + territory.getName() + "<br>領海(りょうかい)" + "<br><br></html>";
    } else {
      labelText = "<html>" + ta.toStringForInfo(true, true) + "<br></html>";
    }
    territoryInfo.setText(labelText);
    unitInfo.setText(
        "Units: "
            + territory.getUnits().stream()
                .filter(u -> uiContext.getMapData().shouldDrawUnit(u.getType().getName()))
                .count());

    panel.add(territoryInfo);
    panel.add(cusObject);
    panel.add(unitInfo);

    for (final UnitCategory item : units) {
      // separate players with a separator
      if (!item.getOwner().equals(currentPlayer)) {
        currentPlayer = item.getOwner();
        panel.add(Box.createVerticalStrut(15));
      }
      final Optional<ImageIcon> unitIcon =
          uiContext.getUnitImageFactory().getIcon(ImageKey.of(item));
      if (unitIcon.isPresent()) {
        // overlay flag onto upper-right of icon
        final ImageIcon flagIcon =
            new ImageIcon(uiContext.getFlagImageFactory().getSmallFlag(item.getOwner()));
        final Icon flaggedUnitIcon =
            new OverlayIcon(
                unitIcon.get(),
                flagIcon,
                unitIcon.get().getIconWidth() - (flagIcon.getIconWidth() / 2),
                0);
        final JLabel label =
            new JLabel("x" + item.getUnits().size(), flaggedUnitIcon, SwingConstants.LEFT);
        final String toolTipText =
            "<html>"
                + item.getType().getName()
                + ": "
                + TooltipProperties.getInstance().getTooltip(item.getType(), currentPlayer)
                + "</html>";
        label.setToolTipText(toolTipText);
        panel.add(label);
      }
    }

    return panel;
  }


}

