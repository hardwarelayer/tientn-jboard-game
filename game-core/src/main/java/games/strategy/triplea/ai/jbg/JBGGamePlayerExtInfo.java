package games.strategy.triplea.ai.jbg;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import games.strategy.engine.data.JBGConstants;

/*****
Beware: a change of this class will invalidate save games
 ******/
@Getter
@Setter
public class JBGGamePlayerExtInfo implements Serializable {
  @Getter @Setter private String playerName;
  @Getter @Setter private int tributeAmount = 0;
  @Getter @Setter private String lastTributePlayerName;
  @Getter @Setter private boolean capitolInDanger = false;

  public JBGGamePlayerExtInfo(final String name) {
    playerName = name;
    tributeAmount = 0;
    lastTributePlayerName = null;
    capitolInDanger = false;
  }
}