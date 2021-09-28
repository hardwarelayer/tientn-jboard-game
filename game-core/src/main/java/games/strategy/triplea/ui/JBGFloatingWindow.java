package games.strategy.triplea.ui;
import java.awt.Window;
import javax.swing.JWindow;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.ImageIcon;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import java.util.concurrent.CompletableFuture;
import org.triplea.java.concurrency.CompletableFutureUtils;
import javax.swing.SwingUtilities;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameDataEvent;

@SuppressWarnings( "serial" )
public class JBGFloatingWindow extends JWindow
{
    int DISPLAY_WIDTH = 200;
    int LINE_HEIGHT = 20;

    private final UiContext uiContext;
    private final GameData data;

    private final JLabel stepInfoLbl = new JLabel("xxxxxx");
    private final JLabel roundInfoLbl = new JLabel("xxxxxx");
    private final JLabel playerInfoLbl = new JLabel("xxxxxx");
    private final JLabel playerListLbl = new JLabel("?/?");
    private final JLabel eventMessageLbl = new JLabel("");
    BoundedRangeModel playerListModel = new DefaultBoundedRangeModel();
    JProgressBar playerListBar = new JProgressBar(playerListModel);
    int iPlayerListBarStep = 0;
    int iPlayerStep = 0;
    int iTotalPlayers = 0;
    int iCurrentPlayerIdx = 0;
    String lastPlayerName = "";
    static String HTML_HEADER_TOKEN = "<html>";

    public JBGFloatingWindow( Window hostWindow, UiContext uiCntx, GameData data)
    {
        super( hostWindow );

        uiContext = uiCntx;

        this.data = data;
        data.acquireReadLock();
        try {
          iTotalPlayers = data.getPlayerList().size();
          iPlayerStep = (int) 100 / iTotalPlayers;
        }
        finally {
          data.releaseReadLock();
        }

        setFocusableWindowState( false );
        setSize( 300, DISPLAY_WIDTH );
        getContentPane().setBackground( Color.WHITE );
        setAlwaysOnTop(false);

        final JPanel panel = new JPanel();

        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();

        panel.setBorder(new EtchedBorder(EtchedBorder.RAISED));
        panel.setFont(new Font("Arial", Font.PLAIN, 14));
        panel.setLayout(gridbag);

        //setHorizontalTextPosition(SwingConstants.LEADING);
        //gameSouthPanel.add(stepPanel, BorderLayout.EAST);

        //this.setFont(new Font("Arial", Font.PLAIN, 14));
        //this.setLayout(gridbag);

        c.fill = GridBagConstraints.HORIZONTAL; //natural height, maximum width
        c.weightx = 1.0; //spacing
        c.gridx = 0; //col
        c.gridy = 0; //row
        c.gridwidth = 1; //column span
        gridbag.setConstraints(playerInfoLbl, c);
        setComponentSize(playerInfoLbl, DISPLAY_WIDTH, LINE_HEIGHT);
        panel.add(playerInfoLbl);

        c.fill = GridBagConstraints.HORIZONTAL; //natural height, maximum width
        c.weightx = 1.0; //spacing
        c.gridx = 0; //col
        c.gridy = 1; //row
        c.gridwidth = 1; //column span
        gridbag.setConstraints(roundInfoLbl, c);
        setComponentSize(roundInfoLbl, DISPLAY_WIDTH, LINE_HEIGHT);
        panel.add(roundInfoLbl);

        c.fill = GridBagConstraints.HORIZONTAL; //natural height, maximum width
        c.weightx = 1.0; //spacing
        c.gridx = 0; //col
        c.gridy = 2; //row
        c.gridwidth = 1; //column span
        gridbag.setConstraints(stepInfoLbl, c);
        setComponentSize(stepInfoLbl, DISPLAY_WIDTH, LINE_HEIGHT);
        panel.add(stepInfoLbl);

        c.fill = GridBagConstraints.HORIZONTAL; //natural height, maximum width
        c.weightx = 1.0; //spacing
        c.gridx = 0; //col
        c.gridy = 3; //row
        c.gridwidth = 1; //column span
        gridbag.setConstraints(playerListLbl, c);
        setComponentSize(playerListLbl, DISPLAY_WIDTH, LINE_HEIGHT);
        panel.add(playerListLbl);

        c.fill = GridBagConstraints.HORIZONTAL; //natural height, maximum width
        c.weightx = 1.0; //spacing
        c.gridx = 0; //col
        c.gridy = 4; //row
        c.gridwidth = 1; //column span
        gridbag.setConstraints(playerListBar, c);
        setComponentSize(playerListBar, DISPLAY_WIDTH, LINE_HEIGHT);
        panel.add(playerListBar);

        Border border = BorderFactory.createLineBorder(Color.BLACK);
        eventMessageLbl.setBorder(border);

        c.fill = GridBagConstraints.HORIZONTAL; //natural height, maximum width
        c.weightx = 1.0; //spacing
        c.gridx = 0; //col
        c.gridy = 5; //row
        c.gridwidth = 1; //column span
        c.gridheight = 3; //row span
        gridbag.setConstraints(eventMessageLbl, c);
        setComponentSize(eventMessageLbl, DISPLAY_WIDTH, LINE_HEIGHT*3);
        panel.add(eventMessageLbl);


        add(panel);
    }

    public void setLoadPlayerIcon(GamePlayer player) {
      if (!isVisible()) return;

      final CompletableFuture<?> future =
          CompletableFuture.supplyAsync(() -> uiContext.getFlagImageFactory().getFlag(player))
              .thenApplyAsync(ImageIcon::new)
              .thenAccept(icon -> SwingUtilities.invokeLater(() -> {
                this.playerInfoLbl.setIcon(icon);
              }));
      CompletableFutureUtils.logExceptionWhenComplete(
          future,
          throwable -> System.out.println("Failed to set round icon for " + player));
    }

  public void resetPlayerListStep() {
      iPlayerListBarStep = 0;
      iCurrentPlayerIdx = 0;
    }

    public void setInfo(final boolean isPlaying, final GamePlayer player, final int round, final String step) {
      if (!isVisible()) return;

      this.roundInfoLbl.setText("Round:" + String.valueOf(round) + " ");
      stepInfoLbl.setText(step);
      if (player != null) {
        this.playerInfoLbl.setText((isPlaying ? "" : "AI: ") + player.getName());
      }

      if (!lastPlayerName.equals(player.getName())) {
        lastPlayerName = player.getName();
        iCurrentPlayerIdx++;
        this.eventMessageLbl.setText(HTML_HEADER_TOKEN);
        playerListLbl.setText(String.valueOf(iCurrentPlayerIdx)+"/"+String.valueOf(iTotalPlayers));
        if (iPlayerListBarStep < 100) {
          iPlayerListBarStep += iPlayerStep;
        }
        else
          iPlayerListBarStep = 0;
        playerListModel.setValue(iPlayerListBarStep);
      }
    }

    public void setEventMessage(final String msg) {
      String curMsg = this.eventMessageLbl.getText();
      if (curMsg.length() > HTML_HEADER_TOKEN.length()) 
        curMsg = (HTML_HEADER_TOKEN + msg + "<br/>" + curMsg.substring(HTML_HEADER_TOKEN.length()));
      else
        curMsg = (HTML_HEADER_TOKEN + msg);
      this.eventMessageLbl.setText(curMsg);
    }

    private void setComponentSize(JComponent jCom, final int width, final int height) {
      jCom.setMinimumSize(new Dimension(width, height));
      jCom.setPreferredSize(new Dimension(width, height));
      jCom.setMaximumSize(new Dimension(width, height));
    }

}
