package bluej.compiler;

import bluej.utility.Debug;
import bluej.Config;

/**
 ** @version $Id: JobQueue.java 124 1999-06-14 07:26:17Z mik $
 ** @author Michael Cahill
 ** Reasonably generic interface between the BlueJ IDE and the Java
 ** compiler.
 **/

public class JobQueue
{
    private static JobQueue queue = null;

    public static JobQueue getJobQueue() 
    {
	if(queue == null)
	    queue = new JobQueue();
	return queue;
    }

    // ---- instance ----

    private CompilerThread thread = null;
    private Compiler compiler = null;
	
    /**
     * 
     */
    private JobQueue()
    {
	// determine which compiler we should be using

	String compilertype = Config.getPropString("bluej.compiler.type");

	if (compilertype.equals("internal")) {

		compiler = new JavacCompilerInternal();

	} else if (compilertype.equals("javac")) {

		compiler = new JavacCompiler(
		   Config.getPropString("bluej.compiler.executable","javac"));

	} else if (compilertype.equals("jikes")) {

		compiler = new JikesCompiler(
		   Config.getPropString("bluej.compiler.executable","jikes"));

	} else {
		Debug.message(Config.getString("compiler.invalidcompiler"));
	}

	thread = new CompilerThread();
	// Lower priority to improve GUI response time during compilation
	thread.setPriority(Thread.currentThread().getPriority() - 1);
	thread.start();
    }

    /**
     * Adds a job to the compile queue.
     */
    public void addJob(String[] sources, CompileObserver observer,
			      String classpath, String destdir)
    {
	thread.addJob(new Job(sources, compiler, observer,
			      classpath, destdir));
    }

    /** 
     * Adds a job to the compile queue.
     */
    public void addJob(String sourcefile, CompileObserver observer,
			      String classpath, String destdir)
    {
	thread.addJob(new Job(sourcefile, compiler, observer,
			      classpath, destdir));
    }
}
