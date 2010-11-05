/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

package org.netbeans.core.windows.persistence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

import org.netbeans.core.windows.Debug;
import org.netbeans.core.windows.WindowManagerImpl;
import org.openide.cookies.InstanceCookie;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;
import org.openide.loaders.DataObject;

/**
 * Handler of changes in module folder. Changes can be for example
 * generated by enabling/disabling module which defines some winsys
 * element(s).
 *
 * @author Marek Slama
 */
class ModuleChangeHandler implements FileChangeListener {
    
    private static final boolean DEBUG = Debug.isLoggable(ModuleChangeHandler.class);
    
    private boolean started = false;
    
    private FileSystem fs = null;
    
    private FileObject modesModuleFolder;
    
    private FileObject groupsModuleFolder;

    /** Keep FileObjects for folders in modes folder. */
    private Set<FileObject> modesModuleChildren = new HashSet<FileObject>();

    /** Keep FileObjects for folders in groups folder. */
    private Set<FileObject> groupsModuleChildren = new HashSet<FileObject>();
    
    private FileObject componentsModuleFolder;
    
    /** List of <FileObject> which contains file objects of tc refs waiting
     * for their related settings files to be processed */
    private List<FileObject> tcRefsWaitingOnSettings;
    
    /** Creates a new instance of ModuleChangeHandler */
    public ModuleChangeHandler() {
    }

    private void refreshModesFolder () {
        FileObject [] arr = modesModuleFolder.getChildren();

        modesModuleChildren.clear();
        for (FileObject fo : arr) {
            if (fo.isFolder()) {
                modesModuleChildren.add(fo);
                fo.getChildren();  // #156573 - to get events about children
            }
        }
    }

    private void refreshGroupsFolder () {
        FileObject [] arr = groupsModuleFolder.getChildren();

        groupsModuleChildren.clear();
        for (FileObject fo : arr) {
            if (fo.isFolder()) {
                groupsModuleChildren.add(fo);
                fo.getChildren();  // #156573 - to get events about children
            }
        }
    }

    void startHandling () {
        if (started) {
            return;
        }
        PersistenceManager pm = PersistenceManager.getDefault();
        
        try {
            modesModuleFolder = pm.getModesModuleFolder();
            refreshModesFolder();
            groupsModuleFolder = pm.getGroupsModuleFolder();
            refreshGroupsFolder();
            componentsModuleFolder = pm.getComponentsModuleFolder();
        } catch (IOException exc) {
            PersistenceManager.LOG.log(Level.WARNING,
            "[WinSys.ModuleChangeHandler.startHandling]" // NOI18N
            + " Cannot get data folders.", exc); // NOI18N
            return;
        }
        
        try {
            fs = modesModuleFolder.getFileSystem();
        } catch (FileStateInvalidException exc) {
            PersistenceManager.LOG.log(Level.WARNING,
            "[WinSys.ModuleChangeHandler.startHandling]" // NOI18N
            + " Cannot get filesystem.", exc); // NOI18N
            return;
        }
        fs.addFileChangeListener(this);
        started = true;
    }
    
    void stopHandling () {
        if (!started) {
            return;
        }
        fs.removeFileChangeListener(this);
        fs = null;
        started = false;
    }

    /** Used to detect if FileEvent event is interesting for us.
     * @return true if event should be accepted
     */
    private boolean acceptEvent (FileObject fo) {
        FileObject parent = fo.getParent();
        if (parent == null) {
            return false;
        }
        //Change of mode config file or mode folder
        if (parent.getPath().equals(modesModuleFolder.getPath())) {
            if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ MODE ++");
            return true;
        }
        //Change of group config file or group folder
        if (parent.getPath().equals(groupsModuleFolder.getPath())) {
            if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ GROUP ++");
            return true;
        }
        
        if (parent.getPath().equals(componentsModuleFolder.getPath())) {
            if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ COMPONENT ++");
            return true;
        }
        
        parent = parent.getParent();
        if (parent == null) {
            return false;
        }
        //Change of tcRef config file
        if (parent.getPath().equals(modesModuleFolder.getPath())) {
            if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ tcRef ++");
            return true;
        }
        //Change of tcGroup config file
        if (parent.getPath().equals(groupsModuleFolder.getPath())) {
            if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ tcGroup ++");
            return true;
        }
        return false;
    }

    /** 
     * Used to detect if FileEvent event happens in module modes folder.
     * @return true if event should be accepted
     */
    private boolean isInModesFolder (FileObject fo) {
        FileObject parent = fo.getParent();
        if (parent == null) {
            return false;
        }
        //Change of mode config file or mode folder
        if (parent.getPath().equals(modesModuleFolder.getPath())) {
            if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ MODE ++");
            return true;
        }
        return false;
    }
    
    /**
     * Used to detect if FileEvent event happens in module groups folder.
     * @return true if event should be accepted
     */
    private boolean isInGroupsFolder (FileObject fo) {
        FileObject parent = fo.getParent();
        if (parent == null) {
            return false;
        }
        //Change of group config file or group folder
        if (parent.getPath().equals(groupsModuleFolder.getPath())) {
            if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ GROUP ++");
            return true;
        }
        return false;
    }
    
    public void fileAttributeChanged (FileAttributeEvent fe) {
    }
    
    public void fileChanged (FileEvent fe) {
    }
    
    public void fileDataCreated (FileEvent fe) {
        FileObject fo = fe.getFile();
        boolean accepted = acceptEvent(fo);
        if (!accepted) {
            return;
        }
        if (DEBUG) {
            Debug.log(ModuleChangeHandler.class, "-- fileDataCreated fo: " + fo
            + " isFolder:" + fo.isFolder()
            + " ACCEPTED"
            + " th:" + Thread.currentThread().getName());
            if (accepted && fo.isFolder()) {
                FileObject [] files = fo.getChildren();
                for (int i = 0; i < files.length; i++) {
                    Debug.log(ModuleChangeHandler.class, "fo[" + i + "]: " + files[i]);
                }
            }
        }
        processDataOrFolderCreated(fo);
    }
    
    public void fileFolderCreated (FileEvent fe) {
        FileObject fo = fe.getFile();
        boolean accepted = acceptEvent(fo);
        if (!accepted) {
            return;
        }
        if (isInModesFolder(fo)) {
            refreshModesFolder();
        }
        if (isInGroupsFolder(fo)) {
            refreshGroupsFolder();
        }
        if (DEBUG) {
            Debug.log(ModuleChangeHandler.class, "-- fileFolderCreated fo: " + fo
            + " isFolder:" + fo.isFolder()
            + " ACCEPTED"
            + " th:" + Thread.currentThread().getName());
            if (accepted && fo.isFolder()) {
                FileObject [] files = fo.getChildren();
                for (int i = 0; i < files.length; i++) {
                    Debug.log(ModuleChangeHandler.class, "fo[" + i + "]: " + files[i]);
                }
            }
        }
        processDataOrFolderCreated(fo);
    }
    
    private void processDataOrFolderCreated (FileObject fo) {
        FileObject parent1 = fo.getParent();
        if (parent1.getPath().equals(modesModuleFolder.getPath())) {
            if (!fo.isFolder() && PersistenceManager.MODE_EXT.equals(fo.getExt())) {
                if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ process MODE ADD ++");
                addMode(fo.getName());
            }
        } else if (parent1.getPath().equals(groupsModuleFolder.getPath())) {
            if (!fo.isFolder() && PersistenceManager.GROUP_EXT.equals(fo.getExt())) {
                if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ process GROUP ADD ++");
                addGroup(fo.getName());
            }
        } else if (parent1.getPath().equals(componentsModuleFolder.getPath())) {
            if (!fo.isFolder() && PersistenceManager.COMPONENT_EXT.equals(fo.getExt())) {
                if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ process COMPONENT ADD ++");
                addComponent(fo);
            }
        }
        
        
        FileObject parent2 = parent1.getParent();
        if (parent2.getPath().equals(modesModuleFolder.getPath())) {
            if (!fo.isFolder() && PersistenceManager.TCREF_EXT.equals(fo.getExt())) {
                if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ process tcRef ADD ++");
                processTCRef(parent1.getName(), fo);
            }
        } else if (parent2.getPath().equals(groupsModuleFolder.getPath())) {
            if (!fo.isFolder() && PersistenceManager.TCGROUP_EXT.equals(fo.getExt())) {
                if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ process tcGroup ADD ++");
                addTCGroup(parent1.getName(), fo.getName());
            }
        }
    }
    
    public void fileDeleted (FileEvent fe) {
        FileObject fo = fe.getFile();
        boolean accepted = acceptEvent(fo);
        if (!accepted) {
            return;
        }
        if (isInModesFolder(fo)) {
            refreshModesFolder();
        }
        if (isInGroupsFolder(fo)) {
            refreshGroupsFolder();
        }
        
        if (DEBUG) Debug.log(ModuleChangeHandler.class, "-- fileDeleted fo: " + fo
        + " isFolder:" + fo.isFolder()
        + " isValid:" + fo.isValid()
        + " ACCEPTED"
        + " th:" + Thread.currentThread().getName());
        
        FileObject parent1 = fo.getParent();
        if (parent1.getPath().equals(modesModuleFolder.getPath())) {
            if (!fo.isFolder() && PersistenceManager.MODE_EXT.equals(fo.getExt())) {
                if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ process MODE REMOVE ++");
                removeMode(fo.getName());
            }
        } else if (parent1.getPath().equals(groupsModuleFolder.getPath())) {
            if (!fo.isFolder() && PersistenceManager.GROUP_EXT.equals(fo.getExt())) {
                if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ process GROUP REMOVE ++");
                removeGroup(fo.getName());
            }
        }
        FileObject parent2 = parent1.getParent();
        if (parent2.getPath().equals(modesModuleFolder.getPath())) {
            if (!fo.isFolder() && PersistenceManager.TCREF_EXT.equals(fo.getExt())) {
                if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ process tcRef REMOVE ++");
                removeTCRef(fo.getName());
            }
        } else if (parent2.getPath().equals(groupsModuleFolder.getPath())) {
            if (!fo.isFolder() && PersistenceManager.TCGROUP_EXT.equals(fo.getExt())) {
                if (DEBUG) Debug.log(ModuleChangeHandler.class, "++ process tcGroup REMOVE ++");
                removeTCGroup(parent1.getName(), fo.getName());
            }
        }
    }
    
    public void fileRenamed (FileRenameEvent fe) {
    }
    
    private void addMode (String modeName) {
        if (DEBUG) Debug.log(ModuleChangeHandler.class, "addMode" + " mo:" + modeName);
        WindowManagerParser wmParser = PersistenceManager.getDefault().getWindowManagerParser();
        final ModeConfig modeConfig = wmParser.addMode(modeName);
        if (modeConfig != null) {
            // #37529 WindowsAPI to be called from AWT thread only.
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    WindowManagerImpl.getInstance().getPersistenceObserver().modeConfigAdded(modeConfig);
                }
            });
        }
    }
    
    private void addGroup (String groupName) {
        if (DEBUG) Debug.log(ModuleChangeHandler.class, "addGroup group:" + groupName);
        WindowManagerParser wmParser = PersistenceManager.getDefault().getWindowManagerParser();
        final GroupConfig groupConfig = wmParser.addGroup(groupName);
        if (groupConfig != null) {
            // #37529 WindowsAPI to be called from AWT thread only.
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    WindowManagerImpl.getInstance().getPersistenceObserver().groupConfigAdded(groupConfig);
                }
            });
        }
    }
    
    /** Adds tcref if related settings file was already copied, shcedules for
     * later processing otherwise
     */
    private void processTCRef (final String modeName, FileObject tcRefFO) {
        FileObject compsFO = null;
        try {
            compsFO = PersistenceManager.getDefault().getComponentsLocalFolder();
        } catch (IOException exc) {
            PersistenceManager.LOG.log(Level.WARNING,
            "[WinSys.ModuleChangeHandler.processTCRef]" // NOI18N
            + " Cannot get components folder.", exc); // NOI18N
            return;
        }
        
        FileObject localSettings = compsFO.getFileObject(tcRefFO.getName(),
                PersistenceManager.COMPONENT_EXT);
        if (localSettings != null) {
            // OK, settings file already processed, go on and add tc ref
            addTCRef(modeName, tcRefFO.getName());
        } else {
            // alas, settings file not yet ready, postpone tc ref adding
            if (tcRefsWaitingOnSettings == null) {
                tcRefsWaitingOnSettings = new ArrayList<FileObject>(5);
            }
            tcRefsWaitingOnSettings.add(tcRefFO);
        }
    }
    
    private void addTCRef (final String modeName, String tcRefName) {
        if (DEBUG) Debug.log(ModuleChangeHandler.class, "addTCRef modeName:" + modeName + " tcRefName:" + tcRefName);
        WindowManagerParser wmParser = PersistenceManager.getDefault().getWindowManagerParser();
        List<String> tcRefNameList = new ArrayList<String>(10);
        final TCRefConfig tcRefConfig = wmParser.addTCRef(modeName, tcRefName, tcRefNameList);
        try {
            //xml file system warmup to avoid blocking of EDT when deserializing the component
            if( null != tcRefConfig ) {
                DataObject dob = PersistenceManager.getDefault().findTopComponentDataObject(tcRefConfig.tc_id);
                if( null != dob ) {
                    dob.getCookie(InstanceCookie.class);
                }
            }
        } catch( IOException ioE ) {
            //ignore, the exception will be reported later on anyway
            Logger.getLogger(ModuleChangeHandler.class.getName()).log(Level.FINER, null, ioE);
        }
        if (tcRefConfig != null) {
            final String [] tcRefNameArray = tcRefNameList.toArray(new String[tcRefNameList.size()]);
            // #37529 WindowsAPI to be called from AWT thread only.
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    WindowManagerImpl.getInstance().getPersistenceObserver().topComponentRefConfigAdded(modeName, tcRefConfig, tcRefNameArray);
                }
            });
        }
    }
    
    private void addTCGroup (final String groupName, String tcGroupName) {
        if (DEBUG) Debug.log(ModuleChangeHandler.class, "addTCGroup groupName:" + groupName + " tcGroupName:" + tcGroupName);
        WindowManagerParser wmParser = PersistenceManager.getDefault().getWindowManagerParser();
        final TCGroupConfig tcGroupConfig = wmParser.addTCGroup(groupName, tcGroupName);
        if (tcGroupConfig != null) {
            // #37529 WindowsAPI to be called from AWT thread only.
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    WindowManagerImpl.getInstance().getPersistenceObserver().topComponentGroupConfigAdded(groupName, tcGroupConfig);
                }
            });
        }
    }

    /** Copies settings file into local directory if needed and also triggers
     * related tc ref adding if needed (if tc ref was "waiting")
     */
    private void addComponent(FileObject fo) {
        if (DEBUG) Debug.log(ModuleChangeHandler.class, "addComponent settingsName:" + fo.getNameExt());
        try {
            PersistenceManager.getDefault().copySettingsFileIfNeeded(fo);
        } catch (IOException exc) {
            PersistenceManager.LOG.log(Level.WARNING,
            "[WinSys.ModuleChangeHandler.addComponent]" // NOI18N
            + " Cannot copy settings files.", exc); // NOI18N
            return;
        }
        // now process tc ref if it is waiting for us
        FileObject waitingTcRef = findWaitingTcRef(fo);
        if (waitingTcRef != null) {
            tcRefsWaitingOnSettings.remove(waitingTcRef);
            addTCRef(waitingTcRef.getParent().getName(), waitingTcRef.getName());
        }
    }

    /** Finds and returns tcRef related to given settingsFo. List of "waiting"
     * tcRefs is searched. Returns null if related tc ref is not found.
     */
    private FileObject findWaitingTcRef (FileObject settingsFo) {
        if (tcRefsWaitingOnSettings == null) {
            return null;
        }
        FileObject curTcRef;
        String settingsName = settingsFo.getName();
        for (Iterator iter = tcRefsWaitingOnSettings.iterator(); iter.hasNext(); ) {
            curTcRef = (FileObject)iter.next();
            if (settingsName.equals(curTcRef.getName())) {
                return curTcRef;
            }
        }
        return null;
    }
    
    private void removeMode (final String modeName) {
        if (DEBUG) Debug.log(ModuleChangeHandler.class, "removeMode mo:" + modeName);
        WindowManagerParser wmParser = PersistenceManager.getDefault().getWindowManagerParser();
        wmParser.removeMode(modeName);
        //Mode is not removed from model because it can already contain TCs added
        //by user using GUI eg.D&D.
        // #37529 WindowsAPI to be called from AWT thread only.
        //SwingUtilities.invokeLater(new Runnable() {
        //    public void run() {
        //      WindowManagerImpl.getInstance().getPersistenceObserver().modeConfigRemoved(modeName);
        //    }
        //});
    }
    
    private void removeGroup (final String groupName) {
        if (DEBUG) Debug.log(ModuleChangeHandler.class, "removeGroup group:" + groupName);
        WindowManagerParser wmParser = PersistenceManager.getDefault().getWindowManagerParser();
        wmParser.removeGroup(groupName);
        // #37529 WindowsAPI to be called from AWT thread only.
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                WindowManagerImpl.getInstance().getPersistenceObserver().groupConfigRemoved(groupName);
            }
        });
    }
    
    private void removeTCRef (final String tcRefName) {
        if (DEBUG) Debug.log(ModuleChangeHandler.class, "removeTCRef tcRefName:" + tcRefName);
        WindowManagerParser wmParser = PersistenceManager.getDefault().getWindowManagerParser();
        if (wmParser.removeTCRef(tcRefName)) {
            // #37529 WindowsAPI to be called from AWT thread only.
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    WindowManagerImpl.getInstance().getPersistenceObserver().topComponentRefConfigRemoved(tcRefName);
                }
            });
        }
    }
    
    private void removeTCGroup (final String groupName, final String tcGroupName) {
        if (DEBUG) Debug.log(ModuleChangeHandler.class, "removeTCGroup groupName:" + groupName + " tcGroupName:" + tcGroupName);
        WindowManagerParser wmParser = PersistenceManager.getDefault().getWindowManagerParser();
        if (wmParser.removeTCGroup(groupName, tcGroupName)) {
            // #37529 WindowsAPI to be called from AWT thread only.
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    WindowManagerImpl.getInstance().getPersistenceObserver().topComponentGroupConfigRemoved(groupName, tcGroupName);
                }
            });
        }
    }
    
    private void log (String s) {
        Debug.log(ModuleChangeHandler.class, s);
    }
    
}
