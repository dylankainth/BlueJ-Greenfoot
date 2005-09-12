package greenfoot.core;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

import rmiextension.wrappers.RClass;
import rmiextension.wrappers.RObject;
import rmiextension.wrappers.RPackage;
import bluej.extensions.BObject;
import bluej.extensions.CompilationNotStartedException;
import bluej.extensions.MissingJavaFileException;
import bluej.extensions.PackageNotFoundException;
import bluej.extensions.ProjectNotOpenException;

/**
 * Represents a package in Greenfoot. 
 * 
 * @author Poul Henriksen
 * 
 */
public class GPackage
{
    private RPackage pkg;
    private GProject project; 
    private GClass classes;
    
    private Map classPool = new HashMap();
    
    public GPackage(RPackage pkg)
    {
        if(pkg == null) {
            throw new NullPointerException("Pkg must not be null.");
        }
        this.pkg = pkg;
    }
    
    public GPackage(RPackage pkg, GProject project)
    {
        if(pkg == null) {
            throw new NullPointerException("Pkg must not be null.");
        }
        if(project == null) {
            throw new NullPointerException("Project must not be null.");
        }
        this.pkg = pkg;
        this.project = project;
    }

    public void compile(boolean waitCompileEnd)
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException, CompilationNotStartedException
    {
        pkg.compile(waitCompileEnd);
    }

    public File getDir()
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        return pkg.getDir();
    }

    public String getName()
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        return pkg.getName();
    }

    public RObject getObject(String instanceName)
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        return pkg.getObject(instanceName);
    }

    public BObject[] getObjects()
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        return pkg.getObjects();
    }

    public GProject getProject()
        throws ProjectNotOpenException, RemoteException
    {
        if(project == null) {
            project = new GProject(pkg.getProject(), this);
        }
        return project;
    }

    public GClass[] getClasses()
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        RClass[] rClasses = pkg.getRClasses();
        GClass[] gClasses = new GClass[rClasses.length];
        for (int i = 0; i < rClasses.length; i++) {
            RClass rClass = rClasses[i];
            GClass gClass = (GClass) classPool.get(rClass);
            if(gClass == null) {
                gClass = new GClass(rClass, this);
                classPool.put(rClass, gClass);
            }
            gClasses[i] = gClass;
        }
        return gClasses;
    }

    public String invokeConstructor(String className, String[] argTypes, String[] args)
        throws RemoteException
    {
        return pkg.invokeConstructor(className, argTypes, args);
    }

    public String invokeMethod(String className, String methodName, String[] argTypes, String[] args)
        throws RemoteException
    {
        return pkg.invokeMethod(className, methodName, argTypes, args);
    }

    public GClass newClass(String className)
        throws RemoteException, ProjectNotOpenException, PackageNotFoundException, MissingJavaFileException
    {

        File pkgDir = getDir();
        File classFile = new File(pkgDir, className + ".java");
        return new GClass(pkg.newClass(className), this);
    }
    
    public GClass getClass(String className) {
        GClass cls = (GClass) classPool.get(className);
        if(cls == null) {
            try {
                RClass rClass = pkg.getRClass(className);
                if(rClass != null) {
                    cls = new GClass(rClass, this);
                    classPool.put(rClass, cls);
                }                
            }
            catch (ProjectNotOpenException e) {
                e.printStackTrace();
            }
            catch (PackageNotFoundException e) {
                e.printStackTrace();
            }
            catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return cls;
    }

    public void reload()
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        pkg.reload();
    }

    /**
     * Delete class files for all classes in the project.
     *
     */
    public void deleteClassFiles()
    {
        try {
            GClass[] classes = getClasses();
            for (int i = 0; i < classes.length; i++) {
                GClass cls = classes[i];
                File classFile = new File(getDir(), cls.getName() + ".class");
                classFile.delete();
            }

            this.reload();
        }
        catch (ProjectNotOpenException e) {
        }
        catch (PackageNotFoundException e) {
        }
        catch (RemoteException e) {
        }
    }

}
