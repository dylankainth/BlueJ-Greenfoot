package greenfoot.actions;

import bluej.Config;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 * Action to display a copyright notice.
 *
 * @author mik
 */
public class ShowCopyrightAction extends AbstractAction
{
    private static ShowCopyrightAction instance;
    
     /**
     * Singleton factory method for action.
     */
   public static ShowCopyrightAction getInstance(JFrame parent)
    {
        if(instance == null)
            instance = new ShowCopyrightAction(parent);
        return instance;
    }
    

   private JFrame parent;
    
    /** 
     *  Creates a new instance of ShowCopyrightAction 
     */
    private ShowCopyrightAction(JFrame parent) 
    {
        super(Config.getString("greenfoot.copyright"));
        this.parent = parent;
    }

    /**
     * The action was fired...
     */
    public void actionPerformed(ActionEvent e)
    {
            JOptionPane.showMessageDialog(parent, new String[]{
                "Greenfoot \u00a9 2005-2007 Michael K\u00F6lling, Poul Henriksen.", " ",
                "Greenfoot is available 'as is' free of charge for use and non-commercial", 
                "redistribution. Disassembly of the system is prohibited.",
                "This software may not be sold for profit or included in other", 
                "packages which are sold for profit without written authorisation."}, 
                "Copyright, License and Redistribution", JOptionPane.INFORMATION_MESSAGE);
    }
}
