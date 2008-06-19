package greenfoot.gui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

import bluej.BlueJTheme;
import bluej.utility.DialogManager;
import bluej.utility.EscapeDialog;

/**
 * A message dialog presents the user with a message and an optional panel of
 * buttons. To find out which buttons has been pressed a method is available
 * that will show a modal dialog and return the button pressed.
 * 
 * @author Poul Henriksen
 * 
 */
public class MessageDialog extends EscapeDialog implements ActionListener
{
    private JButton[] buttons;
    private JButton pressedButton;
    
    /**
     * Creates a new dialog. The buttons will be placed right-justified at the
     * bottom of the dialog, with the first item in the array to the left.
     * 
     * @param owner The parent dialog.
     * @param message The message to display.
     * @param title Title that goes in the window.
     * @param width Width of the message in columns.
     * @param buttons Array of buttons to display.
     */
    public MessageDialog(Dialog owner, String message, String title, int width, JButton[] buttons)
    {
        super(owner, title);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        createDialog(message, buttons, width);
    }
    /**
     * Creates a new dialog. The buttons will be placed right-justified at the
     * bottom of the dialog, with the first item in the array to the left.
     * 
     * @param owner The parent frame.
     * @param message The message to display.
     * @param title Title that goes in the window.
     * @param width Width of the message in columns.
     * @param buttons Array of buttons to display.
     */
    public MessageDialog(Frame owner, String message, String title, int width, JButton[] buttons)
    {
        super(owner, title);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        createDialog(message, buttons, width);
    }
    private void createDialog(String message, JButton[] buttons, int width)
    {
        this.buttons = buttons;
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);
        contentPane.setBorder(BlueJTheme.dialogBorder);
        
        int rows = (int) Math.ceil((double)message.length() / (double) width);
        WrappingMultiLineLabel messageLabel = new WrappingMultiLineLabel(message,width);
        
        contentPane.add(messageLabel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5,5));
        for(int i=0; i < buttons.length; i++) {
            buttonPanel.add(buttons[i]);
            buttons[i].addActionListener(this);
        }
        contentPane.add(buttonPanel, BorderLayout.SOUTH);
  
        pack();
    }

    
    /**
     * Displays the dialog until a button is pressed or the dialog is closed.
     * @return The button that was used to close the dialog, or null if closed in another way.
     */
    public JButton displayModal()
    {
        setModal(true);  
        DialogManager.centreDialog(this);
        setVisible(true);
        dispose();
        return pressedButton;
    }

    /**
     * Display the dialog (non-modal). 
     */
    public void display()
    {
        setModal(false);  
        DialogManager.centreDialog(this);
        setVisible(true);
    }
    /**
     * Store the button pressed so that it can be returned. Close the dialog.
     */
    public void actionPerformed(ActionEvent e)
    {
        JButton button = (JButton) e.getSource();
        pressedButton = button;
        for(int i=0; i < buttons.length; i++) {
            buttons[i].removeActionListener(this);
        }     
        setVisible(false);
        dispose();
    }
}
