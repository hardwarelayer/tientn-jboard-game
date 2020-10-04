package games.strategy.triplea.printgenerator;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Resource;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import lombok.extern.java.Log;

@Log
class PuInfo {
  private final Map<GamePlayer, Map<Resource, Integer>> infoMap = new HashMap<>();

  void saveToFile(final PrintGenerationData printData) {
    final GameData gameData = printData.getData();
    for (final GamePlayer currentPlayer : gameData.getPlayerList()) {
      infoMap.put(
          currentPlayer,
          gameData.getResourceList().getResources().stream()
              .collect(
                  Collectors.toMap(
                      Function.identity(), currentPlayer.getResources()::getQuantity)));
    }
    try {
      final File outFile = new File(printData.getOutDir(), "General Information.csv");
      try (Writer resourceWriter =
          Files.newBufferedWriter(
              outFile.toPath(),
              StandardCharsets.UTF_8,
              StandardOpenOption.CREATE,
              StandardOpenOption.APPEND)) {
        // Print Title
        final int numResources = gameData.getResourceList().size();
        for (int i = 0; i < numResources / 2 - 1 + numResources % 2; i++) {
          resourceWriter.write(",");
        }
        resourceWriter.write("Resource Chart");
        for (int i = 0; i < numResources / 2 - numResources % 2; i++) {
          resourceWriter.write(",");
        }
        resourceWriter.write("\r\n");
        // Print Resources
        resourceWriter.write(",");
        for (final Resource currentResource : gameData.getResourceList().getResources()) {
          resourceWriter.write(currentResource.getName() + ",");
        }
        resourceWriter.write("\r\n");
        // Print Player's and Resource Amount's
        for (final GamePlayer currentPlayer : gameData.getPlayerList()) {
          resourceWriter.write(currentPlayer.getName());
          final Map<Resource, Integer> resourceMap = infoMap.get(currentPlayer);
          for (final int amountResource : resourceMap.values()) {
            resourceWriter.write("," + amountResource);
          }
          resourceWriter.write("\r\n");
        }
        resourceWriter.write("\r\n");
      }
    } catch (final IOException e) {
      log.log(Level.SEVERE, "Failed to save print generation data general information", e);
    }
  }
}
