package greenfoot.gui.classbrowser.role;

import greenfoot.actions.EditClassAction;
import greenfoot.actions.NewSubclassAction;
import greenfoot.actions.RemoveClassAction;
import greenfoot.core.GClass;
import greenfoot.core.GProject;
import greenfoot.core.WorldInvokeListener;
import greenfoot.event.WorldEvent;
import greenfoot.event.WorldListener;
import greenfoot.gui.classbrowser.ClassBrowser;
import greenfoot.gui.classbrowser.ClassView;

import java.awt.Color;
import java.awt.Dimension;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import bluej.Config;
import bluej.debugmgr.ConstructAction;
import bluej.extensions.ProjectNotOpenException;
import bluej.prefmgr.PrefMgr;
import bluej.utility.Debug;
import bluej.views.ConstructorView;
import bluej.views.MethodView;
import bluej.views.View;
import bluej.views.ViewFilter;


/**
 * A class role in Greenfoot. There are different roles for actors, worlds, and
 * "normal" classes.
 * 
 * @author Poul Henriksen <polle@mip.sdu.dk>
 * @version $Id: ClassRole.java 5483 2008-01-22 16:39:10Z polle $
 */
public abstract class ClassRole implements WorldListener
{
    protected final static Dimension iconSize = new Dimension(16, 16);
    
    private final Color envOpColour = Config.getItemColour("colour.menu.environOp");
    
    /**
     * Set the text and icon of a ClassView as appropriate for the given class
     */
    public abstract void buildUI(ClassView classView, GClass rClass);

    /**
     * Get the name for the template file used to create the initial source for a new class.
     */
    public abstract String getTemplateFileName();

    /**
     * Create a list of actions for invoking the constructors of the given class
     */
    public List<Action> createConstructorActions(Class<?> realClass, GProject project)
    {
        View view = View.getView(realClass);
        List<Action> actions = new ArrayList<Action>();
        ConstructorView[] constructors = view.getConstructors();

        for (int i = 0; i < constructors.length; i++) {
            try {
                ConstructorView m = constructors[constructors.length - i - 1];

                ViewFilter filter = new ViewFilter(ViewFilter.INSTANCE | ViewFilter.PUBLIC);
                if (!filter.accept(m))
                    continue;

                WorldInvokeListener invocListener = new WorldInvokeListener(realClass, project);

                String prefix = "new ";
                Action callAction = new ConstructAction(m, invocListener, prefix + m.getLongDesc());

                if (callAction != null) {
                    actions.add(callAction);
                }
            }
            catch (Exception e) {
                Debug.reportError("Exception accessing methods: " + e);
                e.printStackTrace();
            }
        }
        return actions;
    }
    
    /**
     * Create the popup menu for the given class
     */
    public JPopupMenu createPopupMenu(ClassBrowser classBrowser, ClassView classView)
    {
        GClass gClass = classView.getGClass();
        JPopupMenu popupMenu = new JPopupMenu();
        GProject project = null;
        try {
            project = gClass.getPackage().getProject();
        }
        catch (ProjectNotOpenException pnoe) {}
        catch (RemoteException re) {
            re.printStackTrace();
        }

        Class<?> realClass = gClass.getJavaClass();
        if (realClass != null) {

            // Constructors
            if (!java.lang.reflect.Modifier.isAbstract(realClass.getModifiers())) {
                List<Action> constructorItems = createConstructorActions(realClass, project);

                boolean hasEntries = false;
                for (Action callAction : constructorItems) {
                    JMenuItem item = popupMenu.add(callAction);
                    item.setFont(PrefMgr.getPopupMenuFont());
                    hasEntries = true;
                }

                if (hasEntries) {
                    popupMenu.addSeparator();
                }
            }

            // Static methods
            ViewFilter filter = new ViewFilter(ViewFilter.STATIC | ViewFilter.PUBLIC);
            View view = View.getView(realClass);
            MethodView[] allMethods = view.getAllMethods();
            WorldInvokeListener invocListener = new WorldInvokeListener(realClass, project);
            if (bluej.pkgmgr.target.role.ClassRole.createMenuItems(popupMenu, allMethods, filter, 0, allMethods.length, "", invocListener))
                popupMenu.addSeparator();
        }

        popupMenu.add(createMenuItem(new EditClassAction(classBrowser)));

        addPopupMenuItems(popupMenu, classView.isCoreClass());

        if (! classView.isCoreClass()) {
            popupMenu.add(createMenuItem(new RemoveClassAction(classView)));
        }
        popupMenu.addSeparator();
        popupMenu.add(createMenuItem(new NewSubclassAction(classView, classBrowser)));

        return popupMenu;
    }
    
    protected JMenuItem createMenuItem(Action action)
    {
        JMenuItem item = new JMenuItem(action);
        item.setFont(PrefMgr.getPopupMenuFont());
        item.setForeground(envOpColour);
        return item;
    }
    
    /**
     * Add any role-specific menu items to the given popup menu
     * @param menu  The meny to add the menu items to
     * @param coreClass  Whether the class is a "core" class (Actor, World) or not
     */
    public void addPopupMenuItems(JPopupMenu menu, boolean coreClass)
    {
        // default implementation does nothing
    }

	public void worldCreated(WorldEvent e) {
		// Do nothing - only want to handle this for actors
	}

	public void worldRemoved(WorldEvent e) {
		// Do nothing - only want to handle this for actors
	}

	
}
