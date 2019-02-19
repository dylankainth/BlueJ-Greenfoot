/*
 This file is part of the BlueJ program. 
 Copyright (C) 2019  Michael Kolling and John Rosenberg

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
package bluej.editor.flow;

import bluej.Config;
import bluej.editor.moe.ScopeColorsBorderPane;
import bluej.utility.javafx.JavaFXUtil;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.util.Properties;

public class TempFexMain extends Application
{
    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        Properties tempCommandLineProps = new Properties();
        tempCommandLineProps.put("bluej.debug", "true");
        Config.initialise(new File("/Users/neil/intellij/bjgf/bluej/lib"), tempCommandLineProps, false);
        FlowEditorPane editorPane = new FlowEditorPane("public class Foo\n{\n    int x = 8;\n    public static void main(String[] args) {\n" +
                "        int local = 12;\n    }\n}\n");
        stage.setScene(new Scene(editorPane));
        JavaFXUtil.runAfter(Duration.seconds(1), () -> {
            ScopeColorsBorderPane scopeColors = new ScopeColorsBorderPane();
            scopeColors.scopeBackgroundColorProperty().set(Color.WHITE);
            scopeColors.scopeClassColorProperty().set(Color.LIGHTGREEN);
            scopeColors.scopeMethodColorProperty().set(Color.GOLDENROD);
            scopeColors.scopeClassOuterColorProperty().set(Color.GRAY);
            scopeColors.scopeMethodOuterColorProperty().set(Color.GRAY);
            scopeColors.scopeClassInnerColorProperty().set(Color.GRAY);
            JavaSyntaxView javaSyntaxView = new JavaSyntaxView(editorPane, scopeColors);
            javaSyntaxView.flushReparseQueue();
            javaSyntaxView.recalculateAllScopes();
            javaSyntaxView.flushReparseQueue();
        });
        stage.show();
    }
}
