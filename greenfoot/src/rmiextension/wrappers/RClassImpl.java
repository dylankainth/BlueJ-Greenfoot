/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009,2010,2011,2014,2015,2016,2017  Poul Henriksen and Michael Kolling
 
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
package rmiextension.wrappers;

import java.awt.EventQueue;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.util.List;

import bluej.editor.moe.MoeEditor;
import bluej.editor.moe.MoeIndent;
import bluej.editor.moe.MoeSyntaxDocument;
import bluej.extensions.BClass;
import bluej.extensions.BConstructor;
import bluej.extensions.BField;
import bluej.extensions.BMethod;
import bluej.extensions.BPackage;
import bluej.extensions.ClassNotFoundException;
import bluej.extensions.CompilationNotStartedException;
import bluej.extensions.PackageNotFoundException;
import bluej.extensions.ProjectNotOpenException;
import bluej.extensions.SourceType;
import bluej.extensions.editor.Editor;
import bluej.extensions.editor.EditorBridge;
import bluej.stride.framedjava.ast.Loader;
import bluej.stride.framedjava.elements.CallElement;
import bluej.stride.framedjava.elements.CodeElement;
import bluej.stride.framedjava.elements.NormalMethodElement;
import bluej.utility.Debug;
import javafx.application.Platform;

/**
 * Implementation of the remote class interface.
 * 
 * @author Poul Henriksen <polle@mip.sdu.dk>
 */
public class RClassImpl extends java.rmi.server.UnicastRemoteObject
    implements RClass
{
    private BClass bClass;
    
    private static ProjectNotOpenException pnoe;
    private static PackageNotFoundException pnfe;
    
    /**
     * Package-private constructor. Use WrapperPool to instantiate.
     */
    RClassImpl(BClass bClass)
        throws RemoteException
    {
        this.bClass = bClass;
        if (bClass == null) {
            throw new NullPointerException("Argument can't be null");
        }
    }

    @Override
    public void compile(boolean waitCompileEnd, boolean forceQuiet)
    {
        EventQueue.invokeLater(() -> {
            try
            {
                bClass.compile(waitCompileEnd, forceQuiet);
            }
            catch (ProjectNotOpenException | PackageNotFoundException | CompilationNotStartedException e)
            {
                Debug.reportError(e);
            }
        });
    }
    
    @Override
    public SourceType getSourceType() throws ProjectNotOpenException, PackageNotFoundException
    {
        return bClass.getSourceType();
    }

    @Override
    public void edit()
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        synchronized (RClassImpl.class) {
            pnoe = null;
            pnfe = null;
            
            EventQueue.invokeLater(() -> {
                try {
                    Editor editor = bClass.getEditor();
                    if (editor != null) {
                        editor.setVisible(true);
                    }
                }
                catch (ProjectNotOpenException e) {
                    pnoe = e;
                }
                catch (PackageNotFoundException e) {
                    pnfe = e;
                }
            });
            
            if (pnoe != null) throw pnoe;
            if (pnfe != null) throw pnfe;
        }
    }
    
    @Override
    public void closeEditor() throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        synchronized (RClassImpl.class) {
            pnoe = null;
            pnfe = null;

            EventQueue.invokeLater(() -> {
                try {
                    Editor editor = bClass.getEditor();
                    if (editor != null) {
                        editor.setVisible(false);
                    }
                }
                catch (ProjectNotOpenException e) {
                    pnoe = e;
                }
                catch (PackageNotFoundException e) {
                    pnfe = e;
                }
            });

            if (pnoe != null) throw pnoe;
            if (pnfe != null) throw pnfe;
        }
    }
    
    @Override
    public RPackage getPackage()
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        BPackage wrapped = bClass.getPackage();
        RPackage wrapper = WrapperPool.instance().getWrapper(wrapped);
        return wrapper;
    }

    @Override
    public RClass getSuperclass(boolean inRemoteCallback)
        throws ProjectNotOpenException, PackageNotFoundException, ClassNotFoundException, RemoteException
    {
        if (! inRemoteCallback) {
            synchronized (RClassImpl.class) {
                final BClass[] wrapped = new BClass[1];
                final ClassNotFoundException[] cnfe = new ClassNotFoundException[1];
                pnoe = null;
                pnfe = null;

                try {
                    EventQueue.invokeAndWait(() -> {
                        try {
                            wrapped[0] = bClass.getSuperclass();
                        }
                        catch (ProjectNotOpenException e) {
                            pnoe = e;
                        }
                        catch (PackageNotFoundException e) {
                            pnfe = e;
                        }
                        catch (ClassNotFoundException e) {
                            cnfe[0] = e;
                        }
                    });
                }
                catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
                catch (InvocationTargetException ite) {
                    throw new RuntimeException(ite.getCause());
                }

                if (pnoe != null) {
                    throw pnoe;
                }
                if (pnfe != null) {
                    throw pnfe;
                }
                if (cnfe[0] != null) {
                    throw cnfe[0];
                }

                return WrapperPool.instance().getWrapper(wrapped[0]);
            }
        }
        else {
            BClass sc = bClass.getSuperclass();
            return WrapperPool.instance().getWrapper(sc);
        }
    }

    @Override
    public boolean isCompiled(boolean inRemoteCallback)
        throws ProjectNotOpenException, PackageNotFoundException
    {
        synchronized (RClassImpl.class) {
            pnoe = null;
            pnfe = null;
            final boolean[] result = new boolean[1];
            try {
                Runnable r = () -> {
                    try {
                        result[0] = bClass.isCompiled();
                    } catch (ProjectNotOpenException e) {
                        pnoe = e;
                    } catch (PackageNotFoundException e) {
                        pnfe = e;
                    }
                };

                if (inRemoteCallback) {
                    r.run();
                }
                else {
                    EventQueue.invokeAndWait(r);
                }
            }
            catch (InterruptedException ie) {
                ie.printStackTrace();
            }
            catch (InvocationTargetException ite) {
                ite.printStackTrace();
            }
            
            if (pnoe != null) throw pnoe;
            if (pnfe != null) throw pnfe;
            
            return result[0];
        }
    }

    public String getToString()
    {
        return bClass.getName();
    }

    public String getQualifiedName()
        throws RemoteException
    {
        return bClass.getName();
    }

    public File getJavaFile()
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        return bClass.getJavaFile();
    }

    public void remove() throws ProjectNotOpenException, PackageNotFoundException, ClassNotFoundException, RemoteException
    {
        bClass.remove();
    }

    public void convertStrideToJava() throws ProjectNotOpenException, PackageNotFoundException, ClassNotFoundException, RemoteException
    {
        bClass.convertStrideToJava();
    }

    public void convertJavaToStride() throws ProjectNotOpenException, PackageNotFoundException, ClassNotFoundException, RemoteException
    {
        bClass.convertJavaToStride();
    }

    public void setReadOnly(boolean b) throws RemoteException, ProjectNotOpenException, PackageNotFoundException 
    {
        if(bClass != null && bClass.getEditor() != null) {
            bClass.getEditor().setReadOnly(b);
        }
    }

    @Override
    public void autoIndent() throws ProjectNotOpenException, PackageNotFoundException
    {
        final Editor e = bClass.getEditor();
        EventQueue.invokeLater(() -> {
            MoeEditor bje = (MoeEditor) EditorBridge.getEditor(e);
            MoeSyntaxDocument doc = bje.getSourceDocument();
            
            MoeIndent.calculateIndentsAndApply(doc,0);
        });
    }

    @Override
    public void cancelFreshState() throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        Platform.runLater(() ->
        {
            Editor editor = null;
            try
            {
                editor = bClass.getEditor();
            }
            catch (ProjectNotOpenException e)
            {
                e.printStackTrace();
            }
            catch (PackageNotFoundException e)
            {
                e.printStackTrace();
            }
            if (editor != null)
            {
                editor.cancelFreshState();
            }
        });
    }

    @Override
    public void removeImports(List<String> importTargets) throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        final Editor bClassEditor = bClass.getEditor();
        if (bClassEditor != null)
        {
            EventQueue.invokeLater(() ->
            {
                bluej.editor.Editor ed = EditorBridge.getEditor(bClassEditor);
                ed.removeImports(importTargets);
            });
        }
    }
}
