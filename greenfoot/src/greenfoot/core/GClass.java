package greenfoot.core;

import greenfoot.event.CompileListener;
import greenfoot.gui.classbrowser.ClassView;
import greenfoot.util.GreenfootUtil;

import java.rmi.RemoteException;

import rmiextension.wrappers.RClass;
import rmiextension.wrappers.RConstructor;
import rmiextension.wrappers.RField;
import rmiextension.wrappers.event.RCompileEvent;
import bluej.extensions.BField;
import bluej.extensions.BMethod;
import bluej.extensions.ClassNotFoundException;
import bluej.extensions.CompilationNotStartedException;
import bluej.extensions.PackageNotFoundException;
import bluej.extensions.ProjectNotOpenException;
import bluej.parser.ClassParser;
import bluej.parser.symtab.ClassInfo;
import bluej.runtime.ExecServer;
import bluej.utility.Debug;


/**
 * Represents a class in Greenfoot. This class wraps the RMI class and contains
 * some extra functionality. The main reason for createing this class is to have
 * a place to store information about inheritance relations between classes that
 * have not been compiled.
 * 
 * @author Poul Henriksen
 * @version $Id$
 */
public class GClass implements CompileListener
{
    private static String simObj = "greenfoot.Actor";
    private static String worldObj = "greenfoot.World";
    
    private RClass rmiClass;
    private GPackage pkg;
    private String superclassGuess;
    private boolean compiled;
    
    private ClassView classView;
    private Class realClass;

    /**
     * Constructor for GClass. You should generally not use this -
     * GPackage maintains a class pool which needs to be updated. Use
     * GPackage.getGClass().
     */
    public GClass(RClass cls, GPackage pkg)
    {
        this.rmiClass = cls;
        this.pkg = pkg;
        GreenfootMain.getInstance().addCompileListener(this);
        String savedSuperclass = getClassProperty("superclass");
        if(savedSuperclass == null) {
            guessSuperclass();
        } else {
            superclassGuess = savedSuperclass;
        }
        
        try {
            compiled = cls.isCompiled();
            if (compiled) {
                realClass = loadRealClass();
            }
        }
        catch (RemoteException re) {
            re.printStackTrace();
        }
        catch (PackageNotFoundException pnfe) {
            pnfe.printStackTrace();
        }
        catch (ProjectNotOpenException pnoe) {
            pnoe.printStackTrace();
        }
    }
    
    /**
     * Set the view to be associated with this GClass. The view is
     * notified when the compilation state changes.
     */
    public void setClassView(ClassView view)
    {
        classView = view;
    }

    /**
     * Notification that we changed name.
     */
    public void nameChanged()
    {
        classView.reloadClass();
    }
    
    /**
     * Get the value of a persistent property for this class
     * 
     * @param propertyName   The property name
     * @return   The property value (a String)
     */
    public String getClassProperty(String propertyName)
    {
        try {
            return pkg.getProject().getProjectProperties().getString("class." + getName() + "." + propertyName);
        }
        catch (ProjectNotOpenException e) {
            return null;
        }
        catch (RemoteException e) {
            return null;
        }    
    }
    
    /**
     * Set the value of a persistent property for this class
     * 
     * @param propertyName  The property name to set
     * @param value         The value to set the property to
     */
    public void setClassProperty(String propertyName, String value)
    {
        try {
            pkg.getProject().getProjectProperties().setString("class." + getName() + "." + propertyName, value);
        }
        catch (Exception exc) {
            Debug.reportError("Greenfoot: Could not set class property: " + getName() + "." + propertyName);
        }
    }
    
    public void compile(boolean waitCompileEnd)
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException, CompilationNotStartedException
    {
        rmiClass.compile(waitCompileEnd);
    }

    public void edit()
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        rmiClass.edit();
    }


    public void remove() throws ProjectNotOpenException, PackageNotFoundException, ClassNotFoundException, RemoteException
    {
        rmiClass.remove();
    }
    
    public RConstructor getConstructor(Class[] signature)
        throws ProjectNotOpenException, ClassNotFoundException, RemoteException
    {
        return rmiClass.getConstructor(signature);
    }

    public RConstructor[] getConstructors()
        throws ProjectNotOpenException, ClassNotFoundException, RemoteException
    {
        return rmiClass.getConstructors();
    }

    public BMethod getDeclaredMethod(String methodName, Class[] params)
        throws ProjectNotOpenException, ClassNotFoundException, RemoteException
    {
        return rmiClass.getDeclaredMethod(methodName, params);
    }

    public BMethod[] getDeclaredMethods()
        throws ProjectNotOpenException, ClassNotFoundException, RemoteException
    {
        return rmiClass.getDeclaredMethods();
    }

    public RField getField(String fieldName)
        throws ProjectNotOpenException, ClassNotFoundException, RemoteException
    {
        return rmiClass.getField(fieldName);
    }

    public BField[] getFields()
        throws ProjectNotOpenException, ClassNotFoundException, RemoteException
    {
        return rmiClass.getFields();
    }

    /**
     * Get the java.lang.Class object representing this class. Returns null if
     * the class cannot be loaded (including if the class is not compiled).
     */
    public Class getJavaClass()
    {
        return realClass;
    }

    public GPackage getPackage()
    {
        return pkg;
    }

    /**
     * Gets the qualified name of this class.
     * @return
     */
    public String getQualifiedName()
    {
        try {
            return rmiClass.getQualifiedName();
        }
        catch (RemoteException e) {
            // TODO error reporting
        }
        catch (ProjectNotOpenException e) {}
        catch (ClassNotFoundException e) {}
        return null;
    }

    /**
     * Gets the name of this class. NOT the qualified name.
     * @return
     */
    public String getName() {
        return GreenfootUtil.extractClassName(getQualifiedName());
    }
    /**
     * Returns the superclass or null if no superclass can be found.
     * 
     * @return superclass, or null if the superclass is not part of this
     *         project.
     */
    public GClass getSuperclass()
    {
        try {
            GProject proj = pkg.getProject();
            String superclassName = getSuperclassGuess();
            if (superclassName == null) {
                return null;
            }
            
            // The superclass could belong to a different package...
            String superclassPkg = GreenfootUtil.extractPackageName(superclassName);
            superclassName = GreenfootUtil.extractClassName(superclassName);
                        
            // Get the package, return the class
            GPackage thePkg = proj.getPackage(superclassPkg);
            if (thePkg == null) {
                return null;
            }
            return thePkg.getClass(superclassName);
        }
        catch (RemoteException re) {
            re.printStackTrace();
            return null;
        }
        catch (ProjectNotOpenException pnoe) {
            pnoe.printStackTrace();
            return null;
        }
    }
    
    /**
     * This method tries to guess which class is the superclass. This can be used for non compilable and non parseable classes.
     * <p>
     * If the class is compiled, it will return the real superclass.
     * <br>
     * If the class is parseable this information will be used to extract the superclass.
     * <br>
     * If the class is not parseable it will use the last superclass that was known.
     * <br>
     * In general, we will try to remember the last known superclass, and report that back. It also saves the superclass between different runs of the greenfoot application.
     *
     * @return Best guess of the fully qualified name of the superclass.
     */
    public String getSuperclassGuess()
    {
        return superclassGuess;
    }
    
    /**
     * Sets the superclass guess that will be returned if it is not possible to
     * find it in another way.
     * 
     * This name will be stripped of any qualifications
     * 
     * @param superclass
     */
    public void setSuperclassGuess(String superclassName)
    {
        if(superclassGuess != superclassName) {
            superclassGuess = superclassName;
            setClassProperty("superclass", superclassGuess);
        }
    }
    
    /**
     * This method tries to guess which class is the superclass. This can be used for non compilable and non parseable classes.
     * <p>
     * If the class is compiled, it will return the real superclass.
     * <br>
     * If the class is parseable this information will be used to extract the superclass.
     * <br>
     * If the is not parseable it will use the last superclass that was known.
     * <br>
     * In general, we will try to remember the last known superclass, and report that back.
     * <p>
     * 
     * The meethod will only find superclasses that is part of this project or is one of the greenfoot API class (World or Actor).
     * 
     * OBS: This method can be very slow and shouldn't be called unless needed. Especially if the class isn't compiled it can be very slow.
     * 
     * @return Best guess of the name of the superclass (NOT the qualified name).
     */
    private void guessSuperclass()
    {
        // TODO This should be called each time the source file is saved. However,
        // this is not possible at the moment, so we just do it when it is
        // compiled.
        String name = this.getName();
        if(name.equals("World") || name.equals("Actor")) {
            //We do not want to waste time on guessing the name of the superclass for these two classes.
            return;
        }
        
        //First, try to get the real super class.
        String realSuperclass = null;
        try {
            if(isCompiled()) {                
                realSuperclass = rmiClass.getSuperclass().getQualifiedName();
            }
        }
        catch (RemoteException e) {
        }
        catch (ProjectNotOpenException e) {
        }
        catch (PackageNotFoundException e) {
        }
        catch (ClassNotFoundException e) {
        }
        catch (NullPointerException e) {
        }

        if(realSuperclass != null) {
            setSuperclassGuess(realSuperclass);
            return;
        }
        
        // If the class is compiled, but we did not get a superclass back, then
        // the superclass is not from this project and we set it 
        if (realSuperclass == null && isCompiled()) {
            // no super class that we are interested in.
            setSuperclassGuess("");
            return;
        }

        
        //Second, try to parse the file
        String parsedSuperclass = null;
        try {
            ClassInfo info = ClassParser.parse(rmiClass.getJavaFile());//, classes);
            parsedSuperclass = info.getSuperclass();
            // TODO hack! If the superclass is Actor or World,
            // put it in the right package... parsing does not resolve references...
            if (parsedSuperclass.equals("Actor")) {
                parsedSuperclass = "greenfoot.Actor";
            }
            if (parsedSuperclass.equals("World")) {
                parsedSuperclass = "greenfoot.World";
            }
        }
        catch (ProjectNotOpenException e) {}
        catch (PackageNotFoundException e) {}
        catch (RemoteException e) {}
        catch (Exception e) {}

        if(parsedSuperclass != null) {
            setSuperclassGuess(parsedSuperclass);
            return;
        }
        
        //Ok, nothing more to do. We just let the superclassGuess be whatever it is.   
        
    }
    
    /**
     * Strips the name of a class for its qualified part.
     */
    private String removeQualification(String classname)
    {
        int lastDotIndex = classname.lastIndexOf(".");
        if(lastDotIndex != -1) {
            return classname.substring(lastDotIndex+1);
        } else {
            return classname;
        }
    }

    public String getToString()
    {
        try {
            return rmiClass.getToString();
        }
        catch (RemoteException e) {
            // TODO error reporting
        }
        catch (ProjectNotOpenException e) {}
        catch (ClassNotFoundException e) {}
        return "Error getting real toString. super: " + super.toString();
    }

    /**
     * Check whether this class is compiled.
     */
    public boolean isCompiled()
    {
        return compiled;
    }
    
    /**
     * Set the compiled state of this class.
     */
    public void setCompiledState(boolean isCompiled)
    {
        compiled = isCompiled;
        if (classView != null) {
            // It's safe to call repaint off the event thread
            classView.repaint();
        }
        
        if (isCompiled) {
            realClass = loadRealClass();
        }
        else {
            realClass = null;
        }
        
    }

    /**
     * Returns true if this class is a subclass of the given class.
     * 
     * A class is not considered a subclass of itself. So, if the two classes
     * are same it returns false.
     * 
     * It only looks at the name of class and not the fully qualified name.
     * 
     * @param className
     * @return
     */
    public boolean isSubclassOf(String className)
    {        
        className = removeQualification(className);
       // guessSuperclass();
        GClass superclass = this;
        if(this.getName().equals(className)) {
            return false;
        }
        //Recurse through superclasses
        while (superclass != null) {
            String superclassName = superclass.getSuperclassGuess();
            //TODO Fix this hack. Should be done when non-greenfoot classes gets support.
            //HACK to ensure that a class with no superclass has "" as superclass. This is becuase of the ClassForest building which then allows the clas to show up even though it doesn't have any superclass.
            if(superclassName == null) {
                superclassName = "";
            }
            if (superclassName != null && (className.equals(removeQualification(superclassName)))) {
                return true;
            }
            superclass = superclass.getSuperclass();
        }
        return false;
    }   

    public void compileError(RCompileEvent event)
    {
        guessSuperclass();
        classView.updateRole();
    }

    public void compileWarning(RCompileEvent event)
    {
        guessSuperclass();
        classView.updateRole();
    }

    public void compileSucceeded(RCompileEvent event)
    {
        Class newClass = loadRealClass();
        if(newClass != realClass) {
            realClass = newClass;
            guessSuperclass();
            classView.reloadClass();            
            classView.updateRole();            
        }
    }

    public void compileFailed(RCompileEvent event)
    {
        guessSuperclass();
        classView.updateRole();
    }

    public void compileStarted(RCompileEvent event)
    {   
    }

    /**
     * Try and load the "real" (java.lang.Class) class represented by this
     * GClass, using the current class loader.
     * 
     * @return The class, or null if unsuccessful
     */
    private Class loadRealClass()
    {
        Class cls = null;
        if (! isCompiled()) {
            return cls;
        }
        try {
            String className = getQualifiedName();
            //it is important that we use the right classloader
            ClassLoader classLdr = ExecServer.getCurrentClassLoader();
            cls = Class.forName(className, false, classLdr);
        }
        catch (java.lang.ClassNotFoundException cnfe) {
            // couldn't load: that's ok, we return null
        }
        catch (LinkageError e) {
            // TODO log this properly? It can happen for various reasons, not
            // necessarily a real error.
            e.printStackTrace();
        }
        return cls;
    }

    /**
     * Returns true if this GClass represents the greenfoot.Actor class.
     *
     */
    public boolean isActorClass()
    {
        return getQualifiedName().equals(simObj);
    }
    
    /**
     * Returns true if this GClass represents the greenfoot.World class.
     *
     */
    public boolean isWorldClass()
    {
        return getQualifiedName().equals(worldObj);
    }

    /**
     * Returns true if this GClass represents a class that is a subclass of the greenfoot.Actor class.
     *
     */
    public boolean isActorSubclass()
    {
        return isSubclassOf(simObj);
    }

    /**
     * Returns true if this GClass represents a class that is a subclass of the greenfoot.World class.
     *
     */
    public boolean isWorldSubclass()
    {        
        return isSubclassOf(worldObj);
    }

}
