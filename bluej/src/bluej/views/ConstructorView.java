package bluej.views;

import java.lang.reflect.*;
import bluej.utility.JavaNames;

/**
 * A representation of a Java constructor in BlueJ
 * 
 * @version $Id: ConstructorView.java 2559 2004-05-27 12:03:26Z polle $
 * @author Michael Cahill
 * @author Michael Kolling
 */
public final class ConstructorView extends CallableView
{
    protected Constructor cons;

    /**
     * Constructor.
     */
    public ConstructorView(View view, Constructor cons) {
        super(view);
        this.cons = cons;
    }

    /**
     * Returns a string describing this Constructor.
     */
    public String toString() {
        return cons.toString();
    }

    public int getModifiers() {
        return cons.getModifiers();
    }

    /**
     * @returns a boolean indicating whether this method has parameters
     */
    public boolean hasParameters() {
        return (cons.getParameterTypes().length > 0);
    }

    /**
     * Returns a signature string in the format
     *  name(type,type,type)
     */
    public String getSignature() {
        Class[] params = cons.getParameterTypes();
        return makeSignature(JavaNames.getBase(cons.getName()), params);
    }

    /**
     * Get a short String describing this member. A description is similar
     * to the signature, but it has parameter names in it instead of types.
     */
    public String getShortDesc() {
        Class[] params = cons.getParameterTypes();
        return makeDescription(cons.getName(), params, false);
    }

    /**
     * Get a long String describing this member. A long description is
     * similar to the short description, but it has type names and parameters
     * included.
     */
    public String getLongDesc() {
        Class[] params = cons.getParameterTypes();
        return makeDescription(cons.getName(), params, true);
    }

    /**
     * Get an array of Class objects representing constructor's parameters
     * @returns array of Class objects
     */
    public Class[] getParameters() {
        return cons.getParameterTypes();
    }
}