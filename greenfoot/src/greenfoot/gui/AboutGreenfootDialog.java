package greenfoot.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import bluej.BlueJTheme;
import bluej.Config;
import bluej.utility.DialogManager;
import bluej.utility.MultiLineLabel;

/**
 * The BlueJ about box.
 * 
 * @author Poul Henriksen
 * @version $Id: AboutGreenfootDialog.java 5286 2007-10-04 04:18:15Z bquig $
 */
public class AboutGreenfootDialog extends JDialog
{
    private static final String dialogTitle =Config.getString("about.title");

    public AboutGreenfootDialog(JFrame parent, String version)
    {
        super(parent, dialogTitle, true);

        // Create About box text
        JPanel aboutPanel = new JPanel();
        aboutPanel.setBorder(BlueJTheme.dialogBorder);
        aboutPanel.setLayout(new BorderLayout(12, 0));
        aboutPanel.setBackground(Color.white);

        // insert logo
        URL splashURL = this.getClass().getClassLoader().getResource("greenfoot-about.png");
        Icon icon = new ImageIcon(splashURL);
        JLabel logoLabel = new JLabel(icon);
        aboutPanel.add(logoLabel, BorderLayout.WEST);

        // Create Text Panel
        MultiLineLabel text = new MultiLineLabel(LEFT_ALIGNMENT, 6);
        text.setBackground(Color.white);
        text.addText("The Greenfoot team:" + "\n ", false, true);
        text.addText("      Poul Henriksen\n");
        text.addText("      Michael K\u00F6lling\n");
        text.addText("      Davin McCall\n");
        text.addText("      Bruce Quig\n");
        text.addText("      John Rosenberg\n");

        aboutPanel.add(text, BorderLayout.CENTER);

        // footer text
        MultiLineLabel bottomtext = new MultiLineLabel(LEFT_ALIGNMENT);
        bottomtext.setBackground(Color.white);
        bottomtext.addText(" ");
        bottomtext.addText("Greenfoot version " + version + "  (" + Config.getString("about.java.version") + " "
                + System.getProperty("java.version") + ")", true, false);
        bottomtext.addText(" ");
        bottomtext.addText(Config.getString("about.vm") + " " + System.getProperty("java.vm.name") + " "
                + System.getProperty("java.vm.version") + " (" + System.getProperty("java.vm.vendor") + ")");
        bottomtext.addText(Config.getString("about.runningOn") + " " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        bottomtext.addText(Config.getString("about.javahome") + " " + System.getProperty("java.home"));
        bottomtext.addText(" ");
        bottomtext.addText(Config.getString("about.moreInfo"));
        bottomtext.addText(" ");
        bottomtext.addText(Config.getString("about.logfile") + " " + Config.getUserConfigFile(Config.greenfootDebugLogName));

        aboutPanel.add(bottomtext, BorderLayout.SOUTH);

        // Create Button Panel
        JPanel buttonPanel = new JPanel();
        // buttonPanel.setBackground(Color.white);
        buttonPanel.setLayout(new FlowLayout());
        JButton ok = BlueJTheme.getOkButton();
        buttonPanel.add(ok);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(aboutPanel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        // Close Action when OK is pressed
        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event)
            {
                setVisible(false);
                dispose();
            }
        });

        // Close Action when close button is pressed
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event)
            {
                Window win = (Window) event.getSource();
                win.setVisible(false);
                win.dispose();
            }
        });

        setResizable(false);
        pack();
        DialogManager.centreDialog(this);
    }
}
