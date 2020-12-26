package games.strategy.engine.data;

import java.util.List;
import java.lang.System;

public class JBGCanvasItem {
  public int x;
  public int y;
  public int delay;
  public int xChange;
  public int yChange;
  public long lastMillis;
  public JBGConstants.Tile tile;
  private int iCanvasWidth;
  private int iCanvasHeight;
  private int iTileWidth;
  private int iTileHeight;

  private boolean bLoopChange = false;
  private boolean bStopChange = false;

  public JBGCanvasItem(int x, int y, int delay, int xChange, int yChange, JBGConstants.Tile tile, int iCanvasWidth, int iCanvasHeight, int iTileWidth, int iTileHeight, boolean flgLoop) {
    this.x = x;
    this.y = y;
    this.delay = delay;
    this.xChange = xChange;
    this.yChange = yChange;
    this.tile = tile;

    this.iCanvasWidth = iCanvasWidth;
    this.iCanvasHeight = iCanvasHeight;
    this.iTileWidth = iTileWidth;
    this.iTileHeight = iTileHeight;

    this.bLoopChange = flgLoop;

    //default vals
    this.lastMillis = 0;
  }

  private boolean isReady() {
    long curMillis = System.currentTimeMillis();
    if (curMillis - lastMillis > delay) {
      return true;
    }
    return false;
  }

  public boolean change() {

    if (bStopChange == false && isReady()) {

      x = x + xChange;
      y = y + yChange;

      if (xChange > 0) {
        //move right
        if (x > this.iCanvasWidth) {
          if (bLoopChange) {
            x = -this.iTileWidth; //reset to left
          }
          else {
            bStopChange = true;
          }
        }
      }
      else if (xChange < 0) {
        //move left
        if (x < -this.iTileWidth) {
          if (bLoopChange) {
            x = this.iCanvasWidth; //reset to right
          }
          else {
            bStopChange = true;
          }
        }
      }

      if (yChange > 0) {
        //move down
        if (y > this.iCanvasHeight) {
          if (bLoopChange) {
            y = -this.iTileHeight; //reset to top
          }
          else {
            bStopChange = true;
          }
        }
      }
      else if (yChange < 0) {
        //move up
        if (y < -this.iTileHeight) {
          if (bLoopChange) {
            y = this.iCanvasHeight; //reset to bottom
          }
          else {
            bStopChange = true;
          }
        }
      }
      lastMillis = System.currentTimeMillis();
      return true;

    }
    return false;
  }

}