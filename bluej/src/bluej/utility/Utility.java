package bluej.utility;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

import bluej.Config;

/**
 * Some generally useful utility methods available to all of bluej.
 *
 * @author  Michael Cahill
 * @author  Michael Kolling
 * @version $Id: Utility.java 2462 2004-01-27 12:34:34Z mik $
 */
public class Utility
{

    /**
     * Draw a thick rectangle - another of the things missing from the AWT
     */
    public static void drawThickRect(Graphics g, int x, int y, int width, int height, int thickness)
    {
        for(int i = 0; i < thickness; i++)
            g.drawRect(x + i, y + i, width - 2*i, height - 2*i);
    }

    /**
     * Draw stripes over a rectangle - yet another thing missing from the AWT
     */
    public static void stripeRect(Graphics g, int x, int y, int width, int height, int separation, int thickness)
    {
        for(int offset = 0; offset < width + height; offset += separation)
            for(int i = 0; i < thickness; i++, offset++) {
                int x1, y1, x2, y2;

                if(offset < height)
                    { x1 = x; y1 = y + offset; }
                else
                    { x1 = x + offset - height; y1 = y + height; }

                if(offset < width)
                    { x2 = x + offset; y2 = y; }
                else
                    { x2 = x + width; y2 = y + offset - width; }

                g.drawLine(x1, y1, x2, y2);
            }
    }

    /**
     * Draw a string at a given location on screen centered in a given rectangle.
     */
    public static void drawCentredText(Graphics g, String str, int x, int y, int width, int height)
    {
        FontMetrics fm = g.getFontMetrics();

        Shape oldClip = g.getClip();
        g.clipRect(x, y, width, height);
        g.drawString(str, x + (width - fm.stringWidth(str)) / 2,
                     y + (height + fm.getAscent()) / 2);
        g.setClip(oldClip);
    }

    /**
     * Draw a string at a given location on screen right-aligned in a given rectangle.
     */
    public static void drawRightText(Graphics g, String str, int x, int y, int width, int height)
    {
        FontMetrics fm = g.getFontMetrics();

        Shape oldClip = g.getClip();
        g.clipRect(x, y, width, height);
        g.drawString(str, x + width - fm.stringWidth(str),
                     y + (height + fm.getAscent()) / 2);
        g.setClip(oldClip);
    }

    /**
     * Splits "string" by "Delimiter"
     * @param str - the string to be split
     * @param delimiter - the field delimiter within str
     * @returns	an array of Strings
     */
    public static String[] split(String str, String delimiter)
    {
        List strings = new ArrayList();
        int	start = 0;
        int len = str.length();
        int dlen = delimiter.length();
        int offset = str.lastIndexOf(delimiter);		// First of all, find the
        // Last occurance of the Delimiter
        // Stop empty delimiters
        if (dlen < 1)
	    return null;
        else if(offset < 0)	 // one element
	    {
            String[] result = { str };
            return result;
	    }

        //
        // Append the delimiter onto the end if it doesn't already exit
        //
        if (len > offset + dlen) {
            str += delimiter;
            len += dlen;
        }

        do {
            // Get the new Offset
            offset = str.indexOf(delimiter,start);
            strings.add(str.substring(start,offset));

            // Get the new Start position
            start = offset + dlen;
        } while ((start < len) && (offset != -1));

        // Convert the list into an Array of Strings
        String result[] = new String[strings.size()];
        strings.toArray(result);
        return result;
    }

    /**
     * Splits "string" into lines (stripping end-of-line characters)
     * @param str - the string to be split
     * @returns	an array of Strings
     */
    public static String[] splitLines(String str)
    {
        return (str == null ? null : split(str, "\n"));
    }

    /**
     * return a string in which all the '\' characters of the
     * original string are quoted ('\\').
     */
    public static String quoteSloshes(String src)
    {
        StringBuffer buf = new StringBuffer();

        for(int i = 0; i < src.length(); i++)
            {
                if(src.charAt(i) == '\\')
                    buf.append('\\');
                buf.append(src.charAt(i));
            }

        return buf.toString();
    }

    /**
     * Return a string in which all the ' ' characters of the
     * original string are quoted with a passed in string.
     */
    public static String quoteSpaces(String src, String quote)
    {
        StringBuffer buf = new StringBuffer();

        for(int i=0; i < src.length(); i++) {
            if(src.charAt(i) == ' ')
                buf.append(quote);
            buf.append(src.charAt(i));
        }

        return buf.toString();
    }

    /**
     * Translate a given, qualified class name into a URL where we believe
     * its documentation to be, and display that URL in a web browser.
     */
    public static void showClassDocumentation(String classname, String suffix)
    {
        classname = classname.replace('.', '/');
        String docURL = Config.getPropString("bluej.url.javaStdLib");
        if(docURL.endsWith(".html")) {
            int lastSlash = docURL.lastIndexOf('/');
            if(lastSlash != -1)
                docURL = docURL.substring(0, lastSlash+1);
        }
        //Debug.message(docURL + classname + ".html" + suffix);
        openWebBrowser(docURL + classname + ".html" + suffix);
    }

    /**
     * Let the given URL be shown in a browser window.
     * @param url the URL to be shown.
     * @return true if the web browser could be started, false otherwise.
     */
    public static boolean openWebBrowser(String url) {

        if(Config.isMacOS()) {                           // Mac
            try {
                if((!url.startsWith("http:")) && (!url.startsWith("file:")))
                    url = encodeURLSpaces("file://" + url);
                else
                    url = encodeURLSpaces(url);
                com.apple.eio.FileManager.openURL(url);
            }
            catch(IOException e) {
                Debug.reportError("could not start web browser. exc: " + e);
                return false;
            }
        }
        else if(Config.osname.startsWith("Windows")) {                 // Windows

            String cmd;
            // catering for stupid differences in Windows shells...
            if(Config.osname.startsWith("Windows 9") || Config.osname.equals("Windows Me"))    // win95/98/Me
                cmd = "command.com";
            else                                                        // other
                cmd = "cmd.exe";

            try {
                // more stupid Windows differences...
                if(Config.osname.startsWith("Windows 98")) {
                    Process p = Runtime.getRuntime().exec(
                         new String[] { cmd, "/c", "start", '"' + url + '"' });
                }
                else {
                    Process p = Runtime.getRuntime().exec(
                        new String[] { cmd, "/c", "start", "\"\"", '"' + url + '"' });
                }
            }
            catch(IOException e) {
                Debug.reportError("could not start web browser. exc: " + e);
                return false;
            }
        }
        else {                                                      // Unix and other
        
            if((!url.startsWith("http:")) && (!url.startsWith("file:")))
                url = encodeURLSpaces("file://" + url);
            else
                url = encodeURLSpaces(url);

            String cmd = mergeStrings(Config.getPropString("browserCmd1"), url);

            try {
                Process p = Runtime.getRuntime().exec(cmd);

                // wait for exit code. 0 indicates success, otherwise
                // we try second command
                int exitCode = p.waitFor();

                cmd = Config.getPropString("browserCmd2");

                if(exitCode != 0 && cmd != null && cmd.length() > 0) {
                    cmd = mergeStrings(cmd, url);
                    // Debug.message(cmd);
                    p = Runtime.getRuntime().exec(cmd);
                }
            }
            catch(InterruptedException e) {
                Debug.reportError("cannot start web browser: " + cmd);
                Debug.reportError("caught exc " + e);
                return false;
            }
            catch(IOException e) {
                Debug.reportError("could not start web browser.  exc: " + e);
                return false;
            }
        }
        return true;
    }

    public static void bringToFront(Frame frame)
    {
        Debug.message("activate " + frame);
//        frame.dispatchEvent(new MouseEvent(frame, 1, System.currentTimeMillis(), 
//                        0, 100, 100, 1, false));
    }

    /**
     * merge s2 into s1 at position of first '$'
     */
    public static String mergeStrings (String s1, String s2)
    {
        int pos = s1.indexOf('$');
        if(pos == -1)
            return s1;
        else
            return s1.substring(0,pos) + s2 + s1.substring(pos+1);
    }

    /**
     * merge strings in s2 into s1 at positions of '$'
     */
    public static String mergeStrings (String s1, String s2[]) {
	    for (int current = 0; current < s2.length; current++) {
		    s1 = mergeStrings(s1, s2[current]);
	    }

	    return s1;
    }

    /**
     * Remove spaces in a URL - that is: replace each space with the
     * string "%20".
     */
    public static String encodeURLSpaces(String url)
    {
        // if there are any spaces...
        if(url.indexOf(' ') != -1) {
            StringBuffer buffer = new StringBuffer(url);
            for(int i = 0; i < buffer.length(); i++) {
                if(buffer.charAt(i) == ' ') {
                    buffer.deleteCharAt(i);
                    buffer.insert(i, "%20");
                }
            }
            return buffer.toString();
        }
        else
            return url;
    }

    /**
     * Converts tabs in a String into a specified number of spaces.  It assumes
     * that beginning of String is the starting point of tab offsets.
     *
     * @param original the String to convert
     * @param tabSize number of spaces to be inserted in place of tab
     * @return the String with spaces replacing tabs (if tabs present).
     */
    public static String convertTabsToSpaces(String originalString, int tabSize)
    {
        // if there are tab(s) in the String
        if(originalString.indexOf('\t') != -1) {
            StringBuffer buffer = new StringBuffer(originalString);
            for(int i = 0; i < buffer.length(); i++) {
                if(buffer.charAt(i) == '\t') {
                    buffer.deleteCharAt(i);
                    // calculate how many spaces to add
                    int numberOfSpaces = tabSize - (i % tabSize);
                    for(int j = 0; j < numberOfSpaces; j++)
                        buffer.insert(i, ' ');
                }
            }
            return buffer.toString();
        }
        else
            return originalString;
    }

}
