/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009,2011,2013,2015,2018  Poul Henriksen and Michael Kolling
 
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
package greenfoot.guifx.export;

import static greenfoot.export.Exporter.ExportFunction;

import java.io.File;
import javafx.scene.layout.Pane;
import javafx.stage.Window;

/**
 * Export dialog pane for exporting to a standalone application.
 * 
 * @author Michael Kolling
 * @author Amjad Altadmri
 */
public class ExportAppPane extends ExportLocalPane
{
    /**
     * Creates a new instance of ExportAppPane
     *
     * @param parent            The window which will host this pane.
     * @param scenarioName      The name of the scenario to be shared.
     * @param defaultExportDir  The default directory to select from.
     */
    public ExportAppPane(Window parent, String scenarioName, File defaultExportDir)
    {
        super(parent, scenarioName, defaultExportDir, "app", ".jar");
    }

    @Override
    public ExportFunction getFunction()
    {
        return ExportFunction.APP;
    }

    @Override
    protected void makePane(final File targetFile)
    {
        super.makePane(targetFile);
        ((Pane)getContent()).getChildren().addAll(lockScenario, hideControls);
    }
}