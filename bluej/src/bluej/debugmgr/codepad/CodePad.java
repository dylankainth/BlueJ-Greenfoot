/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2010,2011,2012,2013,2016  Michael Kolling and John Rosenberg
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */

package bluej.debugmgr.codepad;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;

import javafx.application.Platform;

import bluej.BlueJEvent;
import bluej.Config;
import bluej.collect.DataCollector;
import bluej.debugger.DebuggerField;
import bluej.debugger.DebuggerObject;
import bluej.debugger.ExceptionDescription;
import bluej.debugger.gentype.JavaType;
import bluej.debugmgr.ExecutionEvent;
import bluej.debugmgr.IndexHistory;
import bluej.debugmgr.Invoker;
import bluej.debugmgr.NamedValue;
import bluej.debugmgr.ResultWatcher;
import bluej.debugmgr.ValueCollection;
import bluej.parser.TextAnalyzer;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;
import bluej.testmgr.record.InvokerRecord;
import bluej.utility.Debug;
import bluej.utility.Utility;
import threadchecker.OnThread;
import threadchecker.Tag;

import javafx.collections.ListChangeListener;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * A modified editor pane for the text evaluation area.
 * The standard JEditorPane is adjusted to take the tag line to the left into
 * account in size computations.
 * 
 * @author Michael Kolling
 */
@OnThread(Tag.FXPlatform)
public class CodePad extends ListView<CodePad.CodePadRow>
    implements ValueCollection, ResultWatcher
{

    private final EditRow editRow;

    @OnThread(Tag.FX)
    public static class CodePadRow { protected String text = ""; public String getText() { return text; } }
    @OnThread(Tag.FX)
    private static class CommandRow extends CodePadRow
    {
        public CommandRow(String text)
        {
            this.text = text;
        }
    }
    @OnThread(Tag.FX)
    private static class EditRow extends CodePadRow
    {
        public EditRow(String text)
        {
            this.text = text;
        }
    }
    @OnThread(Tag.FX)
    private static class OutputRow extends CodePadRow
    {
        public OutputRow(String text)
        {
            this.text = text;
        }
    }
    @OnThread(Tag.FX)
    private static class ErrorRow extends CodePadRow
    {
        public ErrorRow(String text)
        {
            this.text = text;
        }
    }

    private static final String nullLabel = "null";
    
    private static final String uninitializedWarning = Config.getString("pkgmgr.codepad.uninitialized");
    
    private final PkgMgrFrame frame;
    @OnThread(Tag.Swing)
    private String currentCommand = "";
    @OnThread(Tag.Swing)
    private IndexHistory history;
    @OnThread(Tag.Swing)
    private Invoker invoker = null;
    @OnThread(Tag.Swing)
    private TextAnalyzer textParser = null;
    
    // Keeping track of invocation
    @OnThread(Tag.Swing)
    private boolean firstTry;
    @OnThread(Tag.Swing)
    private boolean wrappedResult;
    @OnThread(Tag.Swing)
    private String errorMessage;

    @OnThread(Tag.Swing)
    private boolean busy = false;
    private Action softReturnAction;

    @OnThread(Tag.Swing)
    private List<CodepadVar> localVars = new ArrayList<CodepadVar>();
    @OnThread(Tag.Swing)
    private List<CodepadVar> newlyDeclareds;
    @OnThread(Tag.Swing)
    private List<String> autoInitializedVars;
    // The action which removes the hover state on the object icon
    private Runnable removeHover;

    public CodePad(PkgMgrFrame frame)
    {
        super();
        this.frame = frame;
        //defineKeymap();
        history = new IndexHistory(20);

        StringConverter<CodePadRow> converter = new StringConverter<CodePadRow>()
        {
            @Override
            @OnThread(Tag.FX)
            public String toString(CodePadRow object)
            {
                return object == null ? "" : object.getText();
            }

            @Override
            @OnThread(Tag.FX)
            public CodePadRow fromString(String string)
            {
                // This is only called on commitEdit, in which case it must be an edit row:
                return new EditRow(string);
            }
        };
        setCellFactory(lv -> new TextFieldListCell<CodePadRow>(converter) {

            @Override
            @OnThread(Tag.FX)
            public void commitEdit(CodePadRow newValue)
            {
                super.commitEdit(newValue);
                String text = newValue.getText();
                setEditable(false);    // don't allow input while we're thinking
                command(text);
                SwingUtilities.invokeLater(() -> executeCommand(text));
            }

            @Override
            @OnThread(Tag.FX)
            public void updateItem(CodePadRow item, boolean empty)
            {
                super.updateItem(item, empty);
                if (item != null)
                {
                    setText(item.getText());
                    setEditable(item instanceof EditRow);
                    if (isEditable())
                        super.startEdit();
                }
                else
                {
                    setText("");
                    setEditable(false);
                    super.cancelEdit();
                }
            }

            @Override
            @OnThread(Tag.FX)
            public void cancelEdit()
            {
            }

            @Override
            @OnThread(Tag.FX)
            public void startEdit()
            {
            }
        });

        editRow = new EditRow("");
        getItems().add(editRow);
        setEditable(true);
    }
    /**
     * Clear the local variables.
     */
    @OnThread(Tag.Swing)
    public void clearVars()
    {
        localVars.clear();
        if (textParser != null && frame.getProject() != null) {
            textParser.newClassLoader(frame.getProject().getClassLoader());
        }
    }
    
    //   --- ValueCollection interface ---
    
    /*
     * @see bluej.debugmgr.ValueCollection#getValueIterator()
     */
    @OnThread(Tag.Swing)
    public Iterator<CodepadVar> getValueIterator()
    {
        return localVars.iterator();
    }
    
    /*
     * @see bluej.debugmgr.ValueCollection#getNamedValue(java.lang.String)
     */
    @OnThread(Tag.Swing)
    public NamedValue getNamedValue(String name)
    {
        Class<Object> c = Object.class;
        NamedValue nv = getLocalVar(name);
        if (nv != null) {
            return nv;
        }
        else {
            return frame.getObjectBench().getNamedValue(name);
        }
    }
    
    /**
     * Search for a named local variable, but do not fall back to the object
     * bench if it cannot be found (return null in this case).
     * 
     * @param name  The name of the variable to search for
     * @return    The named variable, or null
     */
    @OnThread(Tag.Swing)
    private NamedValue getLocalVar(String name)
    {
        Iterator<CodepadVar> i = localVars.iterator();
        while (i.hasNext()) {
            NamedValue nv = (NamedValue) i.next();
            if (nv.getName().equals(name))
                return nv;
        }
        
        // not found
        return null;
    }
    
    //   --- ResultWatcher interface ---

    /*
     * @see bluej.debugmgr.ResultWatcher#beginExecution()
     */
    @Override
    @OnThread(Tag.Swing)
    public void beginCompile() { }
    
    /*
     * @see bluej.debugmgr.ResultWatcher#beginExecution()
     */
    @Override
    @OnThread(Tag.Swing)
    public void beginExecution(InvokerRecord ir)
    { 
        BlueJEvent.raiseEvent(BlueJEvent.METHOD_CALL, ir);
    }
    
    /*
     * @see bluej.debugmgr.ResultWatcher#putResult(bluej.debugger.DebuggerObject, java.lang.String, bluej.testmgr.record.InvokerRecord)
     */
    @Override
    @OnThread(Tag.Swing)
    public void putResult(final DebuggerObject result, final String name, final InvokerRecord ir)
    {
        frame.getObjectBench().addInteraction(ir);
        updateInspectors();

        // Newly declared variables are now initialized
        if (newlyDeclareds != null) {
            Iterator<CodepadVar> i = newlyDeclareds.iterator();
            while (i.hasNext()) {
                CodepadVar cpv = (CodepadVar) i.next();
                cpv.setInitialized();
            }
            newlyDeclareds = null;
        }
        
        boolean giveUninitializedWarning = autoInitializedVars != null && autoInitializedVars.size() != 0; 
        
        if (giveUninitializedWarning && Utility.firstTimeThisRun("TextEvalPane.uninitializedWarning")) {
            // Some variables were automatically initialized - warn the user that
            // this won't happen in "real" code.
            
            String warning = uninitializedWarning;
            
            int findex = 0;
            while (findex < warning.length()) {
                int nindex = warning.indexOf('\n', findex);
                if (nindex == -1)
                    nindex = warning.length();
                
                String warnLine = warning.substring(findex, nindex);
                Platform.runLater(() -> error(warnLine));
                findex = nindex + 1; // skip the newline character
            }
            
            autoInitializedVars.clear();
        }
        
        if (!result.isNullObject()) {
            DebuggerField resultField = result.getField(0);
            String resultString = resultField.getValueString();
            
            if(resultString.equals(nullLabel)) {
                DataCollector.codePadSuccess(frame.getPackage(), ir.getOriginalCommand(), resultString);
                Platform.runLater(() -> output(resultString));
            }
            else {
                boolean isObject = resultField.isReferenceType();
                
                if(isObject) {
                    DebuggerObject resultObject = resultField.getValueObject(null);
                    String resultType = resultObject.getGenType().toString(true);
                    String resultOutputString = resultString + "   (" + resultType + ")";
                    DataCollector.codePadSuccess(frame.getPackage(), ir.getOriginalCommand(), resultOutputString);
                    Platform.runLater(() -> objectOutput(resultOutputString,  new ObjectInfo(resultObject, ir)));
                }
                else {
                    String resultType = resultField.getType().toString(true);
                    String resultOutputString = resultString + "   (" + resultType + ")";
                    DataCollector.codePadSuccess(frame.getPackage(), ir.getOriginalCommand(), resultOutputString);
                    Platform.runLater(() -> output(resultOutputString));
                }
            }            
        } 
        else {
            //markCurrentAs(TextEvalSyntaxView.OUTPUT, false);
        }
        
        ExecutionEvent executionEvent = new ExecutionEvent(frame.getPackage());
        executionEvent.setCommand(currentCommand);
        executionEvent.setResult(ExecutionEvent.NORMAL_EXIT);
        executionEvent.setResultObject(result);
        BlueJEvent.raiseEvent(BlueJEvent.EXECUTION_RESULT, executionEvent);
        
        currentCommand = "";
        textParser.confirmCommand();
        Platform.runLater(() -> setEditable(true));    // allow next input
        busy = false;
    }

    @OnThread(Tag.Swing)
    private void updateInspectors()
    {
        Project proj = frame.getPackage().getProject();
        Platform.runLater(() -> proj.updateInspectors());
    }

    /**
     * An invocation has failed - here is the error message
     */
    @Override
    @OnThread(Tag.Swing)
    public void putError(String message, InvokerRecord ir)
    {
        if(firstTry) {
            if (wrappedResult) {
                // We thought we knew what the result type should be, but there
                // was a compile time error. So try again, assuming that we
                // got it wrong, and we'll use the dynamic result type (meaning
                // we won't get type arguments).
                wrappedResult = false;
                errorMessage = null; // use the error message from this second attempt
                invoker = new Invoker(frame, this, currentCommand, CodePad.this);
                invoker.setImports(textParser.getImportStatements());
                invoker.doFreeFormInvocation("");
            }
            else {
                // We thought there was going to be a result, but compilation failed.
                // Try again, but assume we have a statement this time.
                firstTry = false;
                invoker = new Invoker(frame, this, currentCommand, CodePad.this);
                invoker.setImports(textParser.getImportStatements());
                invoker.doFreeFormInvocation(null);
                if (errorMessage == null) {
                    errorMessage = message;
                }
            }
        }
        else {
            if (errorMessage == null) {
                errorMessage = message;
            }
            
            // An error. Remove declared variables.
            if (autoInitializedVars != null) {
                autoInitializedVars.clear();
            }
            
            removeNewlyDeclareds();
            DataCollector.codePadError(frame.getPackage(), ir.getOriginalCommand(), errorMessage);
            showErrorMsg(errorMessage);
            errorMessage = null;
        }
    }
    
    /**
     * A runtime exception occurred.
     */
    @Override
    @OnThread(Tag.Swing)
    public void putException(ExceptionDescription exception, InvokerRecord ir)
    {
        ExecutionEvent executionEvent = new ExecutionEvent(frame.getPackage());
        executionEvent.setCommand(currentCommand);
        executionEvent.setResult(ExecutionEvent.EXCEPTION_EXIT);
        executionEvent.setException(exception);
        BlueJEvent.raiseEvent(BlueJEvent.EXECUTION_RESULT, executionEvent);
        updateInspectors();

        if (autoInitializedVars != null) {
            autoInitializedVars.clear();
        }
        
        removeNewlyDeclareds();
        String message = exception.getClassName() + " (" + exception.getText() + ")";
        DataCollector.codePadException(frame.getPackage(), ir.getOriginalCommand(), message);
        showExceptionMsg(message);
    }
    
    /**
     * The remote VM terminated before execution completed (or as a result of
     * execution).
     */
    @Override
    @OnThread(Tag.Swing)
    public void putVMTerminated(InvokerRecord ir)
    {
        if (autoInitializedVars != null)
            autoInitializedVars.clear();
        
        removeNewlyDeclareds();
        
        
        String message = Config.getString("pkgmgr.codepad.vmTerminated");
        DataCollector.codePadError(frame.getPackage(), ir.getOriginalCommand(), message);
        Platform.runLater(() -> error(message));

        completeExecution();
    }
    
    /**
     * Remove the newly declared variables from the value collection.
     * (This is needed if compilation fails, or execution bombs with an exception).
     */
    @OnThread(Tag.Swing)
    private void removeNewlyDeclareds()
    {
        if (newlyDeclareds != null) {
            Iterator<CodepadVar> i = newlyDeclareds.iterator();
            while (i.hasNext()) {
                localVars.remove(i.next());
            }
            newlyDeclareds = null;
        }
    }
    
    //   --- end of ResultWatcher interface ---
    
    /**
     * Show an error message, and allow further command input.
     */
    @OnThread(Tag.Swing)
    private void showErrorMsg(final String message)
    {
        Platform.runLater(() -> error("Error: " + message));
        completeExecution();
    }
    
    /**
     * Show an exception message, and allow further command input.
     */
    @OnThread(Tag.Swing)
    private void showExceptionMsg(final String message)
    {
        Platform.runLater(() -> error("Exception: " + message));
        completeExecution();
    }
    
    /**
     * Execution of the current command has finished (one way or another).
     * Allow further command input.
     */
    @OnThread(Tag.Swing)
    private void completeExecution()
    {
        currentCommand = "";
        Platform.runLater(() -> setEditable(true));
        busy = false;
    }

    /**
     * We had a click in the tag area. Handle it appropriately.
     * Specifically: If the click (or double click) is on an object, then
     * start an object drag (or inspect).
     * @param pos   The text position where we got the click.
     * @param clickCount  Number of consecutive clicks
     */
    public void tagAreaClick(int pos, int clickCount)
    {
        /*
        ObjectInfo objInfo = objectAtPosition(pos);
        if(objInfo != null) {
            // Eugh: thread-hop just to get the window:
            Platform.runLater(() -> {
                Stage fxWindow = frame.getFXWindow();
                SwingUtilities.invokeLater(() -> {
                    frame.getPackage().getEditor().raisePutOnBenchEvent(fxWindow, objInfo.obj, objInfo.obj.getGenType(), objInfo.ir);
                });
            });
        }
        */
    }

    /**
     * Record part of a command
     * @param s
     */
    private void command(String s)
    {
        getItems().add(getItems().size() - 1, new CommandRow(s));
    }

    /**
     * Write a (non-error) message to the text area.
     * @param s The message
     */
    private void output(String s)
    {
        getItems().add(getItems().size() - 1, new OutputRow(s));
    }
    
    /**
     * Write a (non-error) message to the text area.
     * @param s The message
     */
    private void objectOutput(String s, ObjectInfo objInfo)
    {
        getItems().add(getItems().size() - 1, new OutputRow(s));
//        markAs(TextEvalSyntaxView.OBJECT, objInfo);
    }
    
    /**
     * Write an error message to the text area.
     * @param s The message
     */
    private void error(String s)
    {
        getItems().add(getItems().size() - 1, new ErrorRow(s));
    }

    /**
     * Return the object stored with the line at position 'pos'.
     * If that line does not have an object, return null.
     */
    private ObjectInfo objectAtPosition(int pos)
    {
        //Element line = getLineAt(pos);
        //return (ObjectInfo) line.getAttributes().getAttribute(TextEvalSyntaxView.OBJECT);
        return null;
    }

    public void clear()
    {
        SwingUtilities.invokeLater(this::clearVars);
    }

    public void resetFontSize()
    {

    }


    // ---- end of MouseMotionListener interface ----

    /**
     * Set the keymap for this text area. Especially: take care that cursor 
     * movement is restricted so that the cursor remains in the last line,
     * and interpret Return keys to evaluate commands.
     */ /*
    private void defineKeymap()
    {
        Keymap newmap = JTextComponent.addKeymap("codepad", getKeymap());

        // Note that we rely on behavior of the current DefaultEditorKit default key typed
        // handler to actually insert characters (it calls replaceSelection to do so,
        // which we've overridden).

        Action action = new ExecuteCommandAction();
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), action);

        softReturnAction = new ContinueCommandAction();
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Event.SHIFT_MASK), softReturnAction);

        action = new BackSpaceAction();
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), action);
        
        action = new CursorLeftAction();
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), action);
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0), action);

        if (PrefMgr.getFlag(PrefMgr.ACCESSIBILITY_SUPPORT) == false)
        {
            action = new HistoryBackAction();
            newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), action);
            newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, 0), action);

            action = new HistoryForwardAction();
            newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), action);
            newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, 0), action);
        }
        
        action = new TransferFocusAction(true);
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), action);

        action = new TransferFocusAction(false);
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, Event.SHIFT_MASK), action);
        
        action = new CursorHomeAction();
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), action);

        setKeymap(newmap);
    }*/

    @OnThread(Tag.Swing)
    private void executeCommand(String line)
        {
            if (busy) {
                return;
            }

            currentCommand = (currentCommand + line).trim();
            if(currentCommand.length() != 0) {
                       
                history.add(line);
                //append("\n");
                firstTry = true;
                busy = true;
                if (textParser == null) {
                    textParser = new TextAnalyzer(frame.getProject().getEntityResolver(),
                            frame.getPackage().getQualifiedName(), CodePad.this);
                }
                String retType;
                retType = textParser.parseCommand(currentCommand);
                wrappedResult = (retType != null && retType.length() != 0);
                
                // see if any variables were declared
                if (retType == null) {
                    firstTry = false; // Only try once.
                    currentCommand = textParser.getAmendedCommand();
                    List<DeclaredVar> declaredVars = textParser.getDeclaredVars();
                    if (declaredVars != null) {
                        Iterator<DeclaredVar> i = declaredVars.iterator();
                        while (i.hasNext()) {
                            if (newlyDeclareds == null) {
                                newlyDeclareds = new ArrayList<CodepadVar>();
                            }
                            if (autoInitializedVars == null) {
                                autoInitializedVars = new ArrayList<String>();
                            }
                            
                            DeclaredVar dv = i.next();
                            String declaredName = dv.getName();
                            
                            if (getLocalVar(declaredName) != null) {
                                // The variable has already been declared
                                String errMsg = Config.getString("pkgmgr.codepad.redefinedVar");
                                errMsg = Utility.mergeStrings(errMsg, declaredName);
                                showErrorMsg(errMsg);
                                removeNewlyDeclareds();
                                return;
                            }
                            
                            CodepadVar cpv = new CodepadVar(dv.getName(), dv.getDeclaredType(), dv.isFinal());
                            newlyDeclareds.add(cpv);
                            localVars.add(cpv);

                            // If the variable was declared but not initialized, the codepad
                            // auto-initializes it. We add to a list so that we can display
                            // a warning to that effect, once the command has completed.
                            if (! dv.isInitialized()) {
                                autoInitializedVars.add(dv.getName());
                            }
                        }
                    }
                }
                
                invoker = new Invoker(frame, CodePad.this, currentCommand, CodePad.this);
                invoker.setImports(textParser.getImportStatements());
                if (!invoker.doFreeFormInvocation(retType)) {
                    // Invocation failed
                    firstTry = false;
                    putError("Invocation failed.", null);
                }
            }
            else {
                //markAs(TextEvalSyntaxView.OUTPUT, Boolean.TRUE);
            }
        }

    final class ContinueCommandAction extends AbstractAction {
        
        /**
         * Create a new action object. This action reads the current
         * line as a start for a new command and continues reading the 
         * command in the next line.
         */
        public ContinueCommandAction()
        {
            super("ContinueCommand");
        }
        
        /**
         * Read the text of the current line in the text area as the
         * start of a Java command and continue reading in the next line.
         */
        final public void actionPerformed(ActionEvent event)
        {
            /*
            if (busy)
                return;
            
            String line = getCurrentLine();
            currentCommand += line + " ";
            history.add(line);
            //markAs(TextEvalSyntaxView.CONTINUE, Boolean.TRUE);
            */
        }
    }


    final class HistoryBackAction extends AbstractAction {

        /**
         * Create a new action object.
         */
        public HistoryBackAction()
        {
            super("HistoryBack");
        }
        
        /**
         * Set the current line to the previous input history entry.
         */
        final public void actionPerformed(ActionEvent event)
        {
            /*
            if (busy)
                return;
            
            String line = history.getPrevious();
            if(line != null) {
                setInput(line);
            }
            */
        }

    }

    private void setInput(String line)
    {

    }

    final class HistoryForwardAction extends AbstractAction {

        /**
         * Create a new action object.
         */
        public HistoryForwardAction()
        {
            super("HistoryForward");
        }
        
        /**
         * Set the current line to the next input history entry.
         */
        final public void actionPerformed(ActionEvent event)
        {
            /*
            if (busy)
                return;
            
            String line = history.getNext();
            if(line != null) {
                setInput(line);
            }
            */
        }

    }

    @OnThread(Tag.Any)
    final class ObjectInfo {
        DebuggerObject obj;
        InvokerRecord ir;
        
        /**
         * Create an object holding information about an invocation.
         */
        public ObjectInfo(DebuggerObject obj, InvokerRecord ir) {
            this.obj = obj;
            this.ir = ir;
        }
    }
    
    final class CodepadVar implements NamedValue {
        
        String name;
        boolean finalVar;
        boolean initialized = false;
        JavaType type;
        
        public CodepadVar(String name, JavaType type, boolean finalVar)
        {
            this.name = name;
            this.finalVar = finalVar;
            this.type = type;
        }
        
        public String getName()
        {
            return name;
        }
        
        public JavaType getGenType()
        {
            return type;
        }
        
        public boolean isFinal()
        {
            return finalVar;
        }
        
        public boolean isInitialized()
        {
            return initialized;
        }
        
        public void setInitialized()
        {
            initialized = true;
        }
    }
    
}