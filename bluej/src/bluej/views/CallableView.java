package bluej.views;

import bluej.debugger.gentype.JavaType;
import bluej.debugger.gentype.GenTypeDeclTpar;

/**
 * A "callable" is the generalisation of a Constructor and a Method. This class
 * contains aspects common to both of those.
 * 
 * @author Michael Kolling
 *  
 */
public abstract class CallableView extends MemberView
{
    /**
     * Constructor.
     */
    public CallableView(View view) {
        super(view);
    }

    /**
     * @returns a boolean indicating whether this method has parameters
     */
    public abstract boolean hasParameters();
    
    /**
     * @returns a boolean indicating whether this method uses var args
     */
    public abstract boolean isVarArgs();

    /**
     * Indicates whether the callable view has type parameters.
     */
    public abstract boolean isGeneric();

    /**
     * Count of parameters
     * @returns the number of parameters
     */
    public int getParameterCount() {
        return getParameters().length;
    }

    /**
     * Get an array of Class objects representing parameter classes
     * @return  array of Class objects
     */
    public abstract Class[] getParameters();
    
    /**
     * Get an array of GenType objects representing the parameter types of the
     * callable.
     * 
     * @param raw  whether to return raw versions of the parameter types
     * @return  the parameter types
     */
    public abstract JavaType[] getParamTypes(boolean raw);

    /**
     * Get the type paraemters for this callable as an array of GenTypeDeclTpar
     */
    public abstract GenTypeDeclTpar[] getTypeParams();
    
    /**
     * Gets an array of strings with the names of the parameters
     * @return
     */
    public String[] getParamNames()
    {
        Comment c = getComment();
        if( c == null )
            return null;
        return c.getParamNames();
    }
    
    /**
     * Gets an array of nicely formatted strings with the types of the parameters 
     */
    public abstract String[] getParamTypeStrings();
    
    public void print(FormattedPrintWriter out)
    {
        print(out, 0);
    }

    public void print(FormattedPrintWriter out, int indents)
    {
        Comment comment = getComment();
        if(comment != null)
            comment.print(out, indents);

        out.setItalic(false);
        out.setBold(true);
        for(int i=0; i<indents; i++)
            out.indentLine();
        out.println(getLongDesc());
    }

}