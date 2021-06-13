package games.strategy.engine.framework.startup.ui.panels.main.game.selector;

import com.google.common.base.Preconditions;
import games.strategy.engine.ClientFileSystemHelper;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.JBGKanjiItem;
import games.strategy.triplea.ResourceLoader;
import games.strategy.engine.data.gameparser.GameParser;
import games.strategy.engine.data.gameparser.XmlGameElementMapper;
import games.strategy.engine.framework.GameDataManager;
import games.strategy.engine.framework.startup.mc.ClientModel;
import games.strategy.engine.framework.startup.mc.GameSelector;
import games.strategy.triplea.ai.pro.ProAi;
import games.strategy.triplea.settings.ClientSetting;
import java.io.File;
import java.net.URI;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.io.IOException;
import java.net.MalformedURLException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.net.URL;
import java.util.Observable;
import java.util.Optional;
import java.util.function.Function;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.logging.Level;
import org.triplea.java.UrlStreams;
import org.triplea.util.LocalizeHtml;

/**
 * Model class that tracks the currently 'selected' game. This is the info that appears in the game
 * selector panel on the staging screens, eg: map, round, filename.
 */
@SuppressWarnings("deprecation") //JBG: disable Observable warning
@Log
public class TNARJCoinSelectorModel extends Observable {

  private static final String TNAR_JCOIN_EXTENSION = ".tfmt";

  // just for host bots, so we can get the actions for loading/saving games on the bots from this
  // model
  @Setter @Getter private ClientModel clientModelForHostBots = null;

  public TNARJCoinSelectorModel() {
  }

  private InputStreamReader getInputStream(final File f) {
    InputStreamReader stream = null;

    URL url = null;
    try {
      url = f.toURI().toURL();
    }
    catch (final MalformedURLException e) {
      log.log(Level.SEVERE, "getInputStream: Cannot get URL from file object", e);
    }
    if (url != null) {
      final Optional<InputStream> inputStream = UrlStreams.openStream(url);
      if (inputStream.isPresent()) {
        try {
          //TienTN: load UTF8 tooltips, because default load is ISO-8859-1
          stream = new InputStreamReader(inputStream.get(), Charset.forName("UTF-8"));
        } catch (final Exception e) {
          log.log(Level.SEVERE, "Error opening stream: " + f.getName() , e);
          System.out.println("Error opening stream");
        }
      }
    }
    else {
      System.out.println("Can't get url for stream");
    }
    return stream;
  }

  private int loadJCoinFromFile(final File f) {
    int iValue = 0;

    InputStreamReader s = getInputStream(f);
    if (s != null) {
      try (BufferedReader br = new BufferedReader(s)) {
          String line = br.readLine();
          if (line != null) {
            try {
              iValue = Integer.parseInt(line);
            } 
            catch (Exception e) {
              System.out.println(e.getMessage());
            }
          }
      }
      catch (final IOException e) {
        log.log(Level.SEVERE, "Error reading file: " + f.getName(), e);
        System.out.println("Error reading file "+f.getName());
      }
    }
    else {
      System.out.println("Invalid input stream");
    }

    return iValue;
  }

  /**
   * Loads game data by parsing a given file.
   *
   * @throws Exception If file parsing is successful and an internal {@code GameData} was set.
   */
  public int load(final File file) throws Exception {
    int iRes = 0;
    Preconditions.checkArgument(
        file.exists(),
        "Programming error, expected file to have already been checked to exist: "
            + file.getAbsolutePath());

    //JBG
    System.out.println("TNARJCoinSelectorModel File name is: "+file.getAbsolutePath());

    // if the file name is xml, load it as a new game
    if (file.getName().toLowerCase().endsWith(TNAR_JCOIN_EXTENSION)) {
      iRes = loadJCoinFromFile(file);
      if (iRes > 0) {
        File toRemove = new File(file.getAbsolutePath());
        if (toRemove != null) {
          if (toRemove.delete()) { 
            System.out.println("Deleted the file: " + toRemove.getName());
          } else {
            System.out.println("Failed to delete the file.");
          } 
        }
      }
    }
    return iRes;
  }

}
