package rmiextension;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;

import bluej.Config;
import bluej.extensions.BProject;
import bluej.extensions.BlueJ;
import bluej.extensions.Extension;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.utility.Debug;

/**
 * 
 * 
 * This is the starting point of greenfoot as a BlueJ Extension.
 * 
 * @author Poul Henriksen <polle@mip.sdu.dk>
 * @version $Id: RMIExtension.java 4281 2006-05-16 16:46:42Z polle $
 */
public class RMIExtension extends Extension
    implements Runnable
{
    private BlueJ theBlueJ;

    /**
     * started as soon as we get the go signal from BlueJ...
     */
    public void run()
    {
        waitForPkgMgrFrame();

        // Now we need to find out if a greenfoot project is automatically
        // opening. If not we must open the dummy project
        boolean openOrphans = "true".equals(Config.getPropString("bluej.autoOpenLastProject"));
        if (openOrphans && PkgMgrFrame.hadOrphanPackages()) {
        }
        else {
            openStartupProject();
        }
    }

    /**
     * Opens a dummy project This is necessary to use the direct invoke as this
     * needs to have a blueJ-package
     *  
     */
    private void openStartupProject()
    {
        if (theBlueJ.getOpenProjects().length == 0) {
            File blueJLibDir = theBlueJ.getSystemLibDir();
            File startupProject = new File(blueJLibDir, "greenfoot/startupProject");
            BProject project = theBlueJ.openProject(startupProject);
            if(project == null) {
                Debug.reportError("Could not open startup project");
            }
        }
    }

    /**
     * Waits for the packageMgrFrame to be ready. TODO this is not quite stable
     * enough. Reinvestigate how to ensure BlueJ is properly started-
     *  
     */
    private void waitForPkgMgrFrame()
    {
        while (PkgMgrFrame.getAllFrames() == null) {
            try {
                Thread.sleep(200);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * When this method is called, the extension may start its work.
     *  
     */
    public void startup(BlueJ bluej)
    {
        theBlueJ = bluej;
        //theBlueJ.addPackageListener(ProjectLauncher.instance());
        ProjectManager.init(bluej);

        try {
			new BlueJRMIServer(theBlueJ);
		} catch (RemoteException e) {
			Debug.reportError("Could not launch RMI server", e);
			//This is bad, lets exit.
			System.exit(1);
		}
        
        Thread t = new Thread(this);
        t.start();
    }

    /**
     * This method must decide if this Extension is compatible with the current
     * release of the BlueJ Extensions API
     */
    public boolean isCompatible()
    {
        return true;
    }

    /**
     * Returns the version number of this extension
     */
    public String getVersion()
    {
        return ("2003.03");
    }

    /**
     * Returns the user-visible name of this extension
     */
    public String getName()
    {
        return ("greenfoot Extension");
    }

    public String getDescription()
    {
        return ("greenfoot extension");
    }

    /**
     * Returns a URL where you can find info on this extension. The real problem
     * is making sure that the link will still be alive in three years...
     */
    public URL getURL()
    {
        try {
            return new URL("http://www.greenfoot.org");
        }
        catch (MalformedURLException e) {
            return null;
        }

    }

}