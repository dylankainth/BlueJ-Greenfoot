/*
 This file is part of the BlueJ program. 
 Copyright (C) 2012  Michael Kolling and John Rosenberg 
 
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
package bluej.collect;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

/**
 * This class that handles submitting compilation data to the remote server.
 * 
 * The class has nothing to do with collecting the data, and deliberately
 * does not depend on any other BlueJ classes.  Package-visible.
 * 
 * @author Davin McCall
 */
class DataSubmitter
{
    private static final String submitUrl = "http://localhost:3000/master_events";
    
    private static volatile boolean givenUp = false;
    
    /**
     * isRunning is only touched while synchonized on queue
     */
    private static boolean isRunning = false;
    
    private static List<Event> queue = new LinkedList<Event>();

    /**
     * The versions of the files as we have last sent them to the server.
     * 
     * Should only be accessed by the postData method, which is running on
     * the event-sending thread
     */
    private static Map<FileKey, List<String> > fileVersions = new HashMap<FileKey, List<String> >();
    
    /**
     * An Event to be submitted to the server.
     */
    public static interface Event
    {
        /**
         * Given the current versions of the files as we have last sent them to the server,
         * forms a new record to be sent to the server
         * 
         * @param fileVersions Our local version of the files, as we have last
         * successfully sent them to the server.  Maps a file identifier to a list
         * of lines in the file
         * @return A MultipartEntity to send to the server
         */
        MultipartEntity makeData(Map<FileKey, List<String> > fileVersions);
        
        /**
         * A callback that is called after the event has been successfully sent to
         * the server.  If necessary, it should update the passed-in map with the
         * file contents
         * @param fileVersions Map, to be modified by the method
         */
        void success(Map<FileKey, List<String> > fileVersions);
    }
    
    /**
     * Submit data to be posted to the server. The data is added to a queue which is processed by
     * another thread.
     * 
     * Package-visible, only used by DataCollector
     */
    static void submitEvent(Event evt)
    {
        //This thread only reads the boolean, and is an optimisation:
        if (givenUp)
            return;
        
        synchronized (queue) {
            queue.add(evt);
            
            if (! isRunning) {
                new Thread() {
                    public void run()
                    {
                        processQueue();
                    }
                }.start();
                isRunning = true;
            }
        }
    }
    
    /**
     * Process the queue of items to be posted to the server.
     */
    private static void processQueue()
    {
        while (true) {
            Event evt;
            synchronized (queue) {
                if (queue.isEmpty()) {
                    isRunning = false;
                    queue.notifyAll(); // in case anyone is waiting for us to finish
                    return;
                }
                evt = queue.remove(0);
            }
            

            if (!givenUp)
            {
                givenUp = !postData(evt);
            }
        }
    }
    
    /**
     * Actually post the data to the server.
     * 
     * Returns false if there was an error.
     */
    private static boolean postData(Event evt)
    {        
        HttpClient client = new DefaultHttpClient();
        
        try {
            HttpPost post = new HttpPost(submitUrl);

            
            /*
            if (errFilePath != null) {
                mpe.addPart("filepath", new StringBody(errFilePath, utf8));
            }
            if (errMsg != null) {
                mpe.addPart("errormsg", new StringBody(errMsg, utf8));
            }
            if (errline != 0) {
                mpe.addPart("errorline", new StringBody("" + errline, utf8));
            }
            
            int i = 0;
            for (SourceContent changedFile : changedFiles) {
                mpe.addPart("sourcefileName" + i, new StringBody(changedFile.getFilePath(), utf8));
                mpe.addPart("sourcefileContent" + i, new ByteArrayBody(changedFile.getFileContent(),
                        changedFile.getFilePath()));
            }
            */
            MultipartEntity mpe = evt.makeData(fileVersions);
            if (mpe == null)
                return true; // nothing to send, no error
            post.setEntity(mpe);
            HttpResponse response = client.execute(post);
            
            for (Header h : response.getAllHeaders())
            {
                if ("X-Status".equals(h.getName()) && !"Created".equals(h.getValue()))
                {
                    // Temporary printing:
                    System.err.println("Problem response: " + mpe.toString() + " " + response.toString());
                    return false;
                }
            }
            
            if (response.getStatusLine().getStatusCode() != 200)
            {
                System.err.println("Problem code: " + response.getStatusLine().getStatusCode());
                return false;
            }
            
            evt.success(fileVersions);
                
            EntityUtils.consume(response.getEntity());
        }
        catch (ClientProtocolException cpe) {
            return false;
        }
        catch (IOException ioe) {
            //For now:
            ioe.printStackTrace();
            
            return false;
        }
        
        return true;
    }
    
    /**
     * Waits until all pending events have been sent to the server, or the timeout expires.  If events are still being added in parallel
     * to this call, there will be undefined behaviour.
     */
    public static void waitForQueueFlush(int maxMillis)
    {
        final long endTime = System.currentTimeMillis() + maxMillis; 
        
        try
        {
            synchronized (queue)
            {
                // Keep waiting if there is anything in the queue,
                // or the queue is empty but the submitter thread is still running.
                while (!queue.isEmpty() || isRunning)
                {
                    queue.wait(endTime - System.currentTimeMillis());
                }
            }
        }
        catch (InterruptedException e)
        {
            //Just finish anyway...
        }
    }
}
