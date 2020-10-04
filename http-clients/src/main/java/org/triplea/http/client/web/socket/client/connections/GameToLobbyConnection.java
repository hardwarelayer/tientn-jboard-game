package org.triplea.http.client.web.socket.client.connections;

import java.net.InetAddress;
import java.net.URI;
import java.util.function.Consumer;
import java.util.logging.Level;
import lombok.Getter;
import lombok.extern.java.Log;
import org.triplea.domain.data.ApiKey;
import org.triplea.domain.data.LobbyGame;
import org.triplea.domain.data.UserName;
import org.triplea.http.client.IpAddressParser;
import org.triplea.http.client.lobby.HttpLobbyClient;
import org.triplea.http.client.lobby.game.hosting.request.GameHostingResponse;
import org.triplea.http.client.lobby.game.lobby.watcher.ChatUploadParams;
import org.triplea.http.client.lobby.game.lobby.watcher.GamePostingRequest;
import org.triplea.http.client.lobby.game.lobby.watcher.GamePostingResponse;
import org.triplea.http.client.lobby.game.lobby.watcher.LobbyWatcherClient;
import org.triplea.http.client.web.socket.GenericWebSocketClient;
import org.triplea.http.client.web.socket.WebSocket;
import org.triplea.http.client.web.socket.WebsocketPaths;
import org.triplea.http.client.web.socket.messages.MessageType;
import org.triplea.http.client.web.socket.messages.WebSocketMessage;
import org.triplea.java.concurrency.AsyncRunner;

/**
 * Represents a connection from a hosted game to lobby. A hosted game can perform actions like send
 * a game update so that the lobby updates the game listing. A hosted game will receive messages
 * from lobby for example like a player banned notification.
 */
@Log
public class GameToLobbyConnection {

  private final HttpLobbyClient lobbyClient;
  private final LobbyWatcherClient lobbyWatcherClient;
  private final WebSocket webSocket;
  @Getter private final InetAddress publicVisibleIp;

  public GameToLobbyConnection(
      final URI lobbyUri,
      final GameHostingResponse gameHostingResponse,
      final Consumer<String> errorHandler) {
    publicVisibleIp = gameHostingResponse.getPublicVisibleIp();
    lobbyClient = HttpLobbyClient.newClient(lobbyUri, ApiKey.of(gameHostingResponse.getApiKey()));

    webSocket =
        GenericWebSocketClient.builder()
            .errorHandler(errorHandler)
            .websocketUri(URI.create(lobbyUri.toString() + WebsocketPaths.GAME_CONNECTIONS))
            .build();
    webSocket.connect();
    lobbyWatcherClient =
        LobbyWatcherClient.newClient(lobbyClient.getLobbyUri(), lobbyClient.getApiKey());
  }

  public <T extends WebSocketMessage> void addMessageListener(
      final MessageType<T> messageType, final Consumer<T> messageHandler) {
    webSocket.addListener(messageType, messageHandler);
  }

  public GamePostingResponse postGame(final GamePostingRequest gamePostingRequest) {
    return lobbyWatcherClient.postGame(gamePostingRequest);
  }

  public boolean sendKeepAlive(final String gameId) {
    return lobbyWatcherClient.sendKeepAlive(gameId);
  }

  public void updateGame(final String gameId, final LobbyGame lobbyGame) {
    lobbyWatcherClient.updateGame(gameId, lobbyGame);
  }

  public void disconnect(final String gameId) {
    AsyncRunner.runAsync(() -> lobbyWatcherClient.removeGame(gameId))
        .exceptionally(e -> log.log(Level.INFO, "Could not complete lobby game remove call", e));
  }

  public boolean isPlayerBanned(final String ip) {
    return lobbyClient
        .getRemoteActionsClient()
        .checkIfPlayerIsBanned(IpAddressParser.fromString(ip));
  }

  public void close() {
    webSocket.close();
  }

  public void sendChatMessageToLobby(final ChatUploadParams chatUploadParams) {
    lobbyWatcherClient.uploadChatMessage(lobbyClient.getApiKey(), chatUploadParams);
  }

  public void playerJoined(final String gameId, final UserName playerName) {
    AsyncRunner.runAsync(() -> lobbyWatcherClient.playerJoined(gameId, playerName))
        .exceptionally(e -> log.log(Level.INFO, "Failed to notify lobby a player connected", e));
  }

  public void playerLeft(final String gameId, final UserName playerName) {
    AsyncRunner.runAsync(() -> lobbyWatcherClient.playerLeft(gameId, playerName))
        .exceptionally(e -> log.log(Level.INFO, "Failed to notify lobby a player left", e));
  }
}
