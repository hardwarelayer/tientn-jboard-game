package games.strategy.engine.data;

import java.util.List;
import java.lang.System;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JBGTurnLogItem {
  private static final long serialVersionUID = -4598117601238030021L;

  @Getter @Setter private String player;
  @Getter @Setter private String block;
  @Getter @Setter private int rolls;
  private StringBuilder info;

  public JBGTurnLogItem(final String player, final String block, final String txtVal) {
    this.player = player;
    this.block = block;
    this.info = new StringBuilder();
    addInfo(txtVal);
  }

  public JBGTurnLogItem(final String player, final String block) {
    this.player = player;
    this.block = block;
    this.info = new StringBuilder();
  }

  public boolean isMatch(final String player, final String block) {
    if (this.player.equals( player )) {
      if (this.block.equals( block )) {
        return true;
      }
    }
    return false;

  }

  public String getInfo() {
    return this.info.toString();
  }

  public void addInfo(final String txtVal) {
    if (txtVal.length() > 0) {
      this.info.append(txtVal);
      this.info.append("\n");
    }
    else {
      this.info.setLength(0);
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Player: ").append(this.player).append(" - ").append("Block:").append(this.block).append("\n");
    sb.append(this.info.toString());
    return sb.toString();
  }

}