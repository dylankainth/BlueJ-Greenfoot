/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2017  Poul Henriksen and Michael Kolling 
 
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
package greenfoot.guifx.classes;

import bluej.pkgmgr.Project;
import bluej.pkgmgr.target.ClassTarget;
import bluej.pkgmgr.target.Target;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * The class diagram on the right-hand side of the Greenfoot window.
 *
 * For now, this is very primitive, but is useful for implementing other Greenfoot functionality.
 */
public class ClassDiagram extends BorderPane
{
    private final ClassDisplaySelectionManager selectionManager = new ClassDisplaySelectionManager();
    // The three groups of classes in the display: World+subclasses, Actor+subclasses, Other
    private final ClassGroup worldClasses = new ClassGroup();
    private final ClassGroup actorClasses = new ClassGroup();
    private final ClassGroup otherClasses = new ClassGroup();
    private final Project project;

    public ClassDiagram(Project project)
    {
        this.project = project;
        setTop(worldClasses);
        setCenter(actorClasses);
        setBottom(otherClasses);
        // Actor classes will expand to fill middle, but content will be positioned at the top of that area:
        BorderPane.setAlignment(actorClasses, Pos.TOP_LEFT);
        BorderPane.setAlignment(otherClasses, Pos.BOTTOM_LEFT);
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);
        
        // Organise the current classes into their groups:
        calculateGroups(project.getUnnamedPackage().getClassTargets());
    }

    /**
     * Takes a list of ClassTargets in the project, and puts them into a tree structure
     * according to their superclass relations, with Actor and World subclasses
     * going into their own group.
     */
    private void calculateGroups(ArrayList<ClassTarget> originalClassTargets)
    {
        // Start by mapping everything to false;
        HashMap<ClassTarget, Boolean> classTargets = new HashMap<>();
        for (ClassTarget originalClassTarget : originalClassTargets)
        {
            classTargets.put(originalClassTarget, false);
        }
        // Note that the classTargets list will be modified by each findAllSubclasses call,
        // so the order here is very important.  Actor and World must come before other:
        
        // First, we must take out any World and Actor classes:
        List<ClassInfo> worldSubclasses = findAllSubclasses("greenfoot.World", classTargets);
        ClassInfo worldClassesInfo = new ClassInfo("greenfoot.World", "World", null, worldSubclasses, selectionManager);
        worldClasses.setClasses(Collections.singletonList(worldClassesInfo));

        List<ClassInfo> actorSubclasses = findAllSubclasses("greenfoot.Actor", classTargets);
        ClassInfo actorClassesInfo = new ClassInfo("greenfoot.Actor", "Actor", null, actorSubclasses, selectionManager);
        actorClasses.setClasses(Collections.singletonList(actorClassesInfo));
        
        // All other classes can be found by passing null, see docs on findAllSubclasses:
        otherClasses.setClasses(findAllSubclasses(null, classTargets));
    }

    /**
     * Finds all subclasses of the given fully-qualified parent class name.  The subclass search
     * is recursive, so if you pass "Grandparent", then both "Parent" and "Child" will be found 
     * and removed.  Any found subclasses will have their boolean changed to true in the given map,
     * and only those that currently map to false will be searched.
     * 
     * @param parentClassName The fully-qualified parent class name to search.  If null, then all classes
     *                        in the classTargets list will be processed and returned.
     * @param classTargets Class targets to search -- only those mapped to false will be searched.  If
     *                     they are processed into a ClassInfo, their value will be flipped to true.
     * @return The list of ClassInfo at the requested level (there may be a deeper tree inside).
     */
    private List<ClassInfo> findAllSubclasses(String parentClassName, Map<ClassTarget, Boolean> classTargets)
    {
        List<ClassInfo> curLevel = new ArrayList<>();
        for (Entry<ClassTarget, Boolean> classTargetAndVal : classTargets.entrySet())
        {
            // Ignore anything already mapped to true:
            if (classTargetAndVal.getValue() == true)
                continue;
            
            ClassTarget classTarget = classTargetAndVal.getKey();
            String superClass = classTarget.analyseSource().getSuperclass();
            boolean includeAtThisLevel;
            if (parentClassName == null)
            {
                // We want all classes, but we still want to pick out subclass relations.  Some classes
                // may have a parent class (e.g. java.util.List) that is not in the list of class targets, but
                // the class should still be included at the top-level.  The key test for top-level is:
                //   Is the parent class either null, or not present in the list?

                includeAtThisLevel = superClass == null || !classTargets.keySet().stream().anyMatch(ct -> Objects.equals(ct.getQualifiedName(), superClass));
            }
            else
            {
                // Does it directly inherit from the requested class?
                includeAtThisLevel = Objects.equals(superClass, parentClassName);
            }

            if (includeAtThisLevel)
            {
                // Update processed status before recursing:
                classTargetAndVal.setValue(true);

                List<ClassInfo> subClasses = findAllSubclasses(classTarget.getQualifiedName(), classTargets);
                curLevel.add(new ClassInfo(classTarget.getQualifiedName(), classTarget.getBaseName(),null, subClasses, selectionManager));
            }
        }
        return curLevel;
    }

    /**
     * Adds a new class to the diagram at the appropriate place, based on its superclass.
     */
    public void addClass(ClassTarget classTarget)
    {
        String superClass = classTarget.analyseSource().getSuperclass();
        
        // The class could be nested within actor or world or other
        // If none of those apply, it will go at top-level of other
        if (superClass != null)
        {
            // It does have a parent class: may be in World, Actor or Other:
            for (ClassGroup classGroup : Arrays.asList(worldClasses, actorClasses, otherClasses))
            {
                // Look all the way down for the tree for the super class:
                boolean found = findAndAdd(classGroup.getLiveClasses(), classTarget, superClass);
                if (found)
                {
                    classGroup.updateAfterAdd();
                    // Found right place nested within the current group; done:
                    return;
                }
            }
            // If we fall through here, we do have a parent class, but it's not in the diagram
            // e.g. inheriting from java.util.List
        }
        // Otherwise, add to top of Other:
        otherClasses.getLiveClasses().add(new ClassInfo(classTarget.getQualifiedName(), classTarget.getBaseName(), null, Collections.emptyList(), selectionManager));
        otherClasses.updateAfterAdd();
    }

    /**
     * Looks within the whole tree formed by the list of class info for the right place for classTarget.  If
     * found, adds it and returns true.  If not found, returns false.
     * 
     * @param classInfos The tree to search.  The list itself will not be modified.
     * @param classTarget The class to add to the tree.
     * @param classTargetSuperClass The super-class of classTarget
     * @return True if right place found and added, false if not
     */
    private boolean findAndAdd(List<ClassInfo> classInfos, ClassTarget classTarget, String classTargetSuperClass)
    {
        for (ClassInfo classInfo : classInfos)
        {
            if (classInfo.getQualifiedName().equals(classTargetSuperClass))
            {
                classInfo.add(new ClassInfo(classTarget.getQualifiedName(), classTarget.getBaseName(), null, Collections.emptyList(), selectionManager));
                return true;
            }
            else if (findAndAdd(classInfo.getSubClasses(), classTarget, classTargetSuperClass))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Make the graphical item for a ClassTarget.  Currently just a Label.
     */
    private Node makeClassItem(ClassTarget classTarget)
    {
        Label label = new Label(classTarget.getBaseName());
        label.setOnContextMenuRequested(e -> {
            Class<?> cl = classTarget.getPackage().loadClass(classTarget.getQualifiedName());
            if (cl != null)
            {
                ContextMenu contextMenu = new ContextMenu();
                classTarget.getRole().createClassConstructorMenu(contextMenu.getItems(), classTarget, cl);
                if (!contextMenu.getItems().isEmpty())
                {
                    contextMenu.getItems().add(new SeparatorMenuItem());
                }
                classTarget.getRole().createClassStaticMenu(contextMenu.getItems(), classTarget, classTarget.hasSourceCode(), cl);
                contextMenu.show(label, e.getScreenX(), e.getScreenY());
            }
        });
        label.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2)
            {
                classTarget.open();
            }
        });
        return label;
    }

    /**
     * Gets the currently selected class in the diagram.  May be null if no selection, or if
     * the selection is a class outside the default package (e.g. greenfoot.World)
     */
    public ClassTarget getSelectedClass()
    {
        ClassDisplay selected = selectionManager.getSelected();
        if (selected == null)
        {
            return null;
        }
        
        Target target = project.getUnnamedPackage().getTarget(selected.getQualifiedName());
        
        if (target != null && target instanceof ClassTarget)
        {
            return (ClassTarget)target;
        }
        else
        {
            return null;
        }
    }
}