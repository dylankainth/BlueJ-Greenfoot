package greenfoot.gui.classbrowser.role;

import greenfoot.core.GClass;
import greenfoot.gui.classbrowser.ClassView;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.FileWriter;
import java.rmi.RemoteException;

import javax.swing.JLabel;


/**
 * @author Poul Henriksen <polle@mip.sdu.dk>
 * @version $Id: NormalClassRole.java 3556 2005-09-09 13:40:58Z polle $
 */
public class NormalClassRole extends ClassRole
{

    public void buildUI(ClassView classView, GClass gClass)
    {

        //TODO get this color from the bluej config
        //component.setBackground(new Color(245, 204, 155));
        classView.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;

        String name = "";
        name = gClass.getQualifiedName();
        JLabel className = new JLabel(name);

        classView.add(className, c);
    }

    public void createSkeleton(String className, String superClassName, FileWriter writer)
    {

    }

}