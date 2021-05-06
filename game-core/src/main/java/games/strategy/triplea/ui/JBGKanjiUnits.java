package games.strategy.triplea.ui;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.ResourceLoader;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.JBGConstants;
import games.strategy.engine.data.JBGKanjiItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.net.URL;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

import lombok.extern.java.Log;
import java.util.logging.Level;
import org.triplea.java.UrlStreams;
import org.triplea.util.LocalizeHtml;

/** Generates unit tooltips based on the content of the map's {@code tooltips.properties} file. */
@Log
public final class JBGKanjiUnits {
  // Properties
  private static JBGKanjiUnits kjp = null;
  private static Instant timestamp = Instant.EPOCH;
  private List<JBGKanjiItem> originRecords = null;
  private List<JBGKanjiItem> subsetRecords = null;

  private JBGKanjiUnits(GameData data) {

    try {
      try {
        data.acquireReadLock();
        this.originRecords = data.getKanjis();
      }
      finally {
        data.releaseReadLock();
      }

    } catch (final Exception ex) {
      log.log(Level.SEVERE, "Cannot init kanji properties", ex);
      System.out.println("Cannot init kanji properties");
    }
  }

  private void sortKanjisByCorrectCount(List<JBGKanjiItem> lst, boolean bReverse) {
    //java 8+
    if (!bReverse)
      lst.sort((r1, r2) -> r1.getCorrectCount() - r2.getCorrectCount());
    else
      lst.sort((r1, r2) -> r2.getCorrectCount() - r1.getCorrectCount());
  }

  //Random.nextBoolean is not working
  private boolean randomBoolean(){
    return Math.random() < 0.5;
  }

  private int randomBetween(final int min, final int max) {
    return ThreadLocalRandom.current().nextInt(min, max + 1);
  }

  //JBGConstants.KANJI_MIN_TEST_CORRECT
  private void buildSubSetRecords(final int iMinTestCorrect) {
    List<JBGKanjiItem> lstKnownItems = new ArrayList<>();
    List<JBGKanjiItem> lstNewItems = new ArrayList<>();
    int iKnownItemsCount = randomBetween(5, 10);

    this.subsetRecords = new ArrayList<>();
    //sort desc by correct count, so we'll traverse top down when load
    sortKanjisByCorrectCount(this.originRecords, true);
    int iCount = 0;
    for (JBGKanjiItem item: this.originRecords) {
      if (item.getCorrectCount() < iMinTestCorrect) {
        lstNewItems.add(item);
        iCount++;
      }
      else {
        //get all known items into one list
        lstKnownItems.add(item);
      }
      if (iCount > JBGConstants.KANJI_TOTAL_SUBSET_SIZE) {
        break;
      }
    }

    //at the beginning, this is small to zero number
    if (lstKnownItems.size() < iKnownItemsCount) {
      iKnownItemsCount = lstKnownItems.size();
    }
    //sort this list from lowest correct to highest
    //so we can get newly known items first, to repeat studying
    sortKanjisByCorrectCount(lstKnownItems, false);

    for (int i=0; i < lstKnownItems.size(); i++) {
      this.subsetRecords.add(lstKnownItems.get(i)); 
      if (this.subsetRecords.size() >= iKnownItemsCount)
        break;
    }
    //new words
    for (int i=0; i < JBGConstants.KANJI_TOTAL_SUBSET_SIZE-iKnownItemsCount; i++) {
      this.subsetRecords.add(lstNewItems.get(i));
    }

  }

  public static JBGKanjiUnits getInstance(GameData data) {
    // cache properties for 5 seconds
    if (kjp == null || timestamp.plusSeconds(5).isBefore(Instant.now())) {
      kjp = new JBGKanjiUnits(data);
      timestamp = Instant.now();
    }
    return kjp;
  }

  public List<JBGKanjiItem> getData() {
    //System.out.println("total rows: " + String.valueOf(originRecords.size()));
    buildSubSetRecords(JBGConstants.KANJI_MIN_TEST_CORRECT);
    return this.subsetRecords;
  }

  public List<JBGKanjiItem> getNewData() {
    //System.out.println("total rows: " + String.valueOf(originRecords.size()));
    buildSubSetRecords(5); //lay cac tu dung tu 5 lan tro xuong cho toi 0 (chua hoc)
    return this.subsetRecords;
  }

/*
  private static final String KANJI_FILE = "kanji.csv";
      //this.originRecords = loadKanjiFromResource(KANJI_FILE);

  private InputStreamReader getInputStream(final String sFileName) {
    InputStreamReader stream = null;

    final ResourceLoader loader = UiContext.getResourceLoader();
    final URL url = loader.getResource(sFileName);
    if (url != null) {
      final Optional<InputStream> inputStream = UrlStreams.openStream(url);
      if (inputStream.isPresent()) {
        try {
          //TienTN: load UTF8 tooltips, because default load is ISO-8859-1
          stream = new InputStreamReader(inputStream.get(), Charset.forName("UTF-8"));
        } catch (final Exception e) {
          log.log(Level.SEVERE, "Error opening stream: " + sFileName, e);
          System.out.println("Error opening stream");
        }
      }
    }
    else {
      System.out.println("Can't get url for stream");
    }
    return stream;
  }

  private List<List<String>> loadKanjiFromResource(final String sFileName) {
    List<List<String>> originRecords = new ArrayList<>();

    InputStreamReader s = getInputStream(KANJI_FILE);
    if (s != null) {
      try (BufferedReader br = new BufferedReader(s)) {
          String line;
          while ((line = br.readLine()) != null) {
            //System.out.println(line);
            String[] values = line.split(KANJI_COMMA_DELIMITER);
            originRecords.add(Arrays.asList(values));
          }
      }
      catch (final IOException e) {
        log.log(Level.SEVERE, "Error reading file: " + sFileName, e);
        System.out.println("Error reading file "+KANJI_FILE);
      }
    }
    else {
      System.out.println("Invalid input stream");
    }

    return originRecords;
  }
*/

}
