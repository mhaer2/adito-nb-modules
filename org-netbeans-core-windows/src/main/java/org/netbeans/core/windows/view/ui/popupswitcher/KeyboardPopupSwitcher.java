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

package org.netbeans.core.windows.view.ui.popupswitcher;

import java.awt.AWTKeyStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.Set;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.core.windows.Switches;
import org.netbeans.core.windows.WindowManagerImpl;
import org.netbeans.core.windows.actions.RecentViewListAction;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Utilities;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;

/**
 * Represents Popup for "Keyboard document switching" which is shown after
 * pressing Ctrl+Tab (or alternatively Ctrl+`).
 * If an user releases a <code>releaseKey</code> in <code>TIME_TO_SHOW</code> ms
 * the popup won't show at all. Instead immediate switching will happen.
 *
 * @author mkrauskopf
 */
public final class KeyboardPopupSwitcher implements WindowFocusListener {
    
    /** Number of milliseconds to show popup if interruption didn't happen. */
    private static final int TIME_TO_SHOW = 200;
    
    /** Singleton */
    private static KeyboardPopupSwitcher instance;
    
    /**
     * Reference to the popup object currently showing the default instance, if
     * it is visible
     */
    private static JWindow popup;
    
    /** Indicating whether a popup is shown? */
    private static boolean shown;
    
    /**
     * Invoke popup after a specified time. Can be interrupter if an user
     * releases <code>triggerKey</code> key in that time.
     */
    private static Timer invokerTimer;
    
    /**
     * Safely indicating whether a <code>invokerTimer</code> is running or not.
     * isRunning() method doesn't work for us in all cases.
     */
    private static boolean invokerTimerRunning;
    
    /**
     * Counts the number of <code>triggerKey</code> hits before the popup is
     * shown (without first <code>triggerKey</code> press).
     * If the <code>triggerKey</code> is pressed more than twice the
     * popup will be shown immediately.
     */
    private static int hits;
    
    private PopupSwitcher switcher;
    private Table table;
    
    private static int triggerKey; // e.g. TAB
    private static int reverseKey = KeyEvent.VK_SHIFT;
    private static int releaseKey; // e.g. CTRL
    private static boolean documentsOnly = false;
    
    /** Indicates whether an item to be selected is previous or next one. */
    private boolean fwd = true;

    private final static AWTKeyStroke CTRL_TAB = AWTKeyStroke.getAWTKeyStroke( KeyEvent.VK_TAB, KeyEvent.CTRL_DOWN_MASK );
    private final static AWTKeyStroke CTRL_SHIFT_TAB = AWTKeyStroke.getAWTKeyStroke( KeyEvent.VK_TAB, KeyEvent.CTRL_DOWN_MASK+KeyEvent.SHIFT_DOWN_MASK );
        
    /**
     * Tries to process given <code>KeyEvent</code> and returns true is event
     * was successfully processed/consumed.
     */
    public static boolean processShortcut(KeyEvent kev) {
        WindowManagerImpl wmi = WindowManagerImpl.getInstance();
        // don't perform when focus is dialog
        if (!wmi.getMainWindow().isFocused() &&
            !WindowManagerImpl.isSeparateWindow(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow())) {
            return false;
        }

        if( Boolean.getBoolean("netbeans.winsys.ctrltab.editoronly") ) { //NOI18N
            Mode activeMode = wmi.getActiveMode();
            if( !wmi.isEditorMode(activeMode) )
                return false;
        }

        return doProcessShortcut( kev );
    }

    private static WeakReference<Component> lastSource;

    static boolean doProcessShortcut( KeyEvent kev ) {
        boolean isCtrlTab = kev.getKeyCode() == KeyEvent.VK_TAB &&
                kev.getModifiers() == InputEvent.CTRL_MASK;
        boolean isCtrlShiftTab = kev.getKeyCode() == KeyEvent.VK_TAB &&
                kev.getModifiers() == (InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK);
        if (KeyboardPopupSwitcher.isShown()) {
            assert instance != null;
            instance.processKeyEvent(kev);
            // be sure that events is not processed further when popup is shown
            kev.consume();
            return true;
        }
        if ((isCtrlTab || isCtrlShiftTab)) { // && !KeyboardPopupSwitcher.isShown()
            if( kev.getID() == KeyEvent.KEY_PRESSED ) {
                lastSource = new WeakReference<Component>(kev.getComponent());
            }
            if( !Switches.isCtrlTabWindowSwitchingInJTableEnabled() ) {
                Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if( c instanceof JComponent && !(c instanceof JEditorPane)) {
                    JComponent jc = ( JComponent ) c;
                    if( jc.getFocusTraversalKeysEnabled() ) {
                        Set<AWTKeyStroke> keys = jc.getFocusTraversalKeys( KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS );
                        if( keys.contains( CTRL_TAB ) || keys.contains( CTRL_SHIFT_TAB ) )
                            return false;
                        keys = jc.getFocusTraversalKeys( KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS );
                        if( keys.contains( CTRL_TAB ) || keys.contains( CTRL_SHIFT_TAB ) )
                            return false;
                    }
                }
            }

            if (KeyboardPopupSwitcher.isAlive()) {
                KeyboardPopupSwitcher.processInterruption(kev);
            } else {
                if( kev.getID() == KeyEvent.KEY_RELEASED ) {
                    Component currentSource = kev.getComponent();
                    if( null != currentSource && null != lastSource && !currentSource.equals( lastSource.get() ) ) {
                        //If the previous Ctrl+Tab event just transfered focus from JTable or JTabbedPane
                        //the KeyboardFocusManager will resend this event to the new focus owner.
                        return false;
                    }
                }
                AbstractAction rva = new RecentViewListAction();
                rva.actionPerformed(new ActionEvent(kev.getSource(),
                        ActionEvent.ACTION_PERFORMED, "C-TAB", kev.getModifiers()));
                return true;
            }
            // consume all ctrl-(shift)-tab to avoid confusion about
            // Ctrl-Tab events since those events are dedicated to document
            // switching only
            kev.consume();
            return true;
        }
        if (kev.getKeyCode() == releaseKey && KeyboardPopupSwitcher.isAlive()) {
            KeyboardPopupSwitcher.processInterruption(kev);
            return true;
        }
        return false;
    }
    
    /**
     * Creates and shows the popup with given <code>items</code>. When user
     * selects an item <code>SwitcherTableItem.Activatable.activate()</code> is
     * called. So what exactly happens depends on the concrete
     * <code>SwitcherTableItem.Activatable</code> implementation.
     * Selection is made when user releases a <code>releaseKey</code> passed on
     * as a parameter. If user releases the <code>releaseKey</code> before a
     * specified time (<code>TIME_TO_SHOW</code>) expires the popup won't show
     * at all and switch to the last used document will be performed
     * immediately.
     *
     * A popup appears on <code>x</code>, <code>y</code> coordinates.
     */
    public static void showPopup(boolean documentsOnly, int releaseKey, int triggerKey, boolean forward) {
        // reject multiple invocations
        if (invokerTimerRunning) {
            return;
        }
        KeyboardPopupSwitcher.releaseKey = releaseKey;
        KeyboardPopupSwitcher.triggerKey = triggerKey;
        invokerTimer = new Timer(TIME_TO_SHOW, new PopupInvoker(forward));
        invokerTimer.setRepeats(false);
        invokerTimer.start();
        invokerTimerRunning = true;
    }

    /**
     * For (unit) testing only.
     * @param model
     * @param releaseKey
     * @param triggerKey
     * @param forward
     */
    static void showPopup( Model model, int releaseKey, int triggerKey, boolean forward ) {
        KeyboardPopupSwitcher.releaseKey = releaseKey;
        KeyboardPopupSwitcher.triggerKey = triggerKey;
        instance = new KeyboardPopupSwitcher( model, forward );
        instance.showPopup();
        shown = true;
    }

    static void hidePopup() {
        cleanupInterrupter();
        if( null != instance )
            instance.cancelSwitching();
    }
    
    /** Stop invoker timer and detach interrupter listener. */
    private static void cleanupInterrupter() {
        invokerTimerRunning = false;
        if (invokerTimer != null) {
            invokerTimer.stop();
        }
    }
    
    /**
     * Serves to <code>invokerTimer</code>. Shows popup after specified time.
     */
    private static class PopupInvoker implements ActionListener {
        private boolean forward;
        public PopupInvoker( boolean forward ) {
            this.forward = forward;
        }
        /** Timer just hit the specified time_to_show */
        @Override
        public void actionPerformed(ActionEvent e) {
            if (invokerTimerRunning) {
                cleanupInterrupter();
                if( null != instance ) {
                    instance.hideCurrentPopup();
                }
                instance = new KeyboardPopupSwitcher( hits, forward );
                instance.showPopup();
            }
        }
    }
    
    /**
     * Returns true if popup is displayed.
     *
     * @return True if a popup was closed.
     */
    public static boolean isShown() {
        return shown;
    }
    
    /**
     * Indicate whether a popup will be or is shown. <em>Will be</em> means
     * that a popup was already triggered by first Ctrl-Tab but TIME_TO_SHOW
     * wasn't expires yet. <em>Is shown</em> means that a popup is really
     * already shown on the screen.
     */
    private static boolean isAlive() {
        return invokerTimerRunning || shown;
    }
    
    /**
     * Creates a new instance of KeyboardPopupSwitcher.
     */
    private KeyboardPopupSwitcher(int hits, boolean forward) {
        this.fwd = forward;
        switcher = new PopupSwitcher( documentsOnly, hits, forward );
        table = switcher.getTable();
    }

    /**
     * For (unit) testing only
     * @param model
     */
    private KeyboardPopupSwitcher(Model model, boolean forward) {
        this.fwd = true;
        switcher = new PopupSwitcher( model, 0, forward );
        table = switcher.getTable();
    }

    private void showPopup() {
        if (!isShown()) {
            // set popup to be always on top to be in front of all
            // floating separate windows
            popup = new JWindow();
            popup.setAlwaysOnTop(true);
            popup.getContentPane().add(switcher);
            Dimension popupDim = switcher.getPreferredSize();
            Rectangle screen = Utilities.getUsableScreenBounds();
            int x = screen.x + ((screen.width / 2) - (popupDim.width / 2));
            int y = screen.y + ((screen.height / 2) - (popupDim.height / 2));
            popup.setLocation(x, y);
            popup.pack();
            MenuSelectionManager.defaultManager().addChangeListener( new ChangeListener() {
                @Override
                public void stateChanged( ChangeEvent e ) {
                    MenuSelectionManager.defaultManager().removeChangeListener( this );
                    hidePopup();
                }
            });
            popup.setVisible(true);
            // #82743 - on JDK 1.5 popup steals focus from main window for a millisecond,
            // so we have to delay attaching of focus listener
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run () {
                    WindowManager.getDefault().getMainWindow().
                            addWindowFocusListener( KeyboardPopupSwitcher.this );
                }
            });
            shown = true;
        }
    }
    
    /**
     * Prevents showing a popup if a user releases the <code>releaseKey</code>
     * in time specified by <code>invokerTimer</code> (which is 200ms by
     * default).
     */
    private static void processInterruption(KeyEvent kev) {
        int keyCode = kev.getKeyCode();
        if (keyCode == releaseKey && kev.getID() == KeyEvent.KEY_RELEASED) {
            // if an user releases Ctrl-Tab before the time to show
            // popup expires, don't show the popup at all and switch to
            // the last used document immediately
            cleanupInterrupter();
            hits = 0;
            AbstractAction rva = new RecentViewListAction();
            rva.actionPerformed(new ActionEvent(kev.getSource(),
                    ActionEvent.ACTION_PERFORMED,
                    "immediately", kev.getModifiers())); // NOI18N
            kev.consume();
        // #88931: Need to react to KEY_PRESSED, not KEY_RELEASED, to not miss the hit    
        } else if (keyCode == triggerKey
                && kev.getModifiers() == InputEvent.CTRL_MASK
                && kev.getID() == KeyEvent.KEY_PRESSED) {
            kev.consume();
            cleanupInterrupter();
            if( null != instance )
                instance.hideCurrentPopup();
            instance = new KeyboardPopupSwitcher(hits + 1, true);
            instance.showPopup();
        }
    }
    
    /** Handles given <code>KeyEvent</code>. */
    private void processKeyEvent(KeyEvent kev) {
        switch (kev.getID()) {
            case KeyEvent.KEY_PRESSED:
                int code = kev.getKeyCode();
                if (code == reverseKey) {
                    fwd = false;
                } else if (code == triggerKey) {
                    if( fwd ) {
                        table.nextRow();
                    } else {
                        table.previousRow();
                    }
                } else {
                    switch( code ) {
                        case KeyEvent.VK_UP:
                            table.previousRow();
                            break;
                        case KeyEvent.VK_DOWN:
                            table.nextRow();
                            break;
                        case KeyEvent.VK_LEFT:
                            table.previousColumn();
                            break;
                        case KeyEvent.VK_RIGHT:
                            table.nextColumn();
                            break;
                    }
                }
                kev.consume();
                break;
            case KeyEvent.KEY_RELEASED:
                code = kev.getKeyCode();
                if (code == reverseKey) {
                    fwd = true;
                    kev.consume();
                } else if (code == KeyEvent.VK_ESCAPE) { // XXX see above
                    cancelSwitching();
                } else if (code == releaseKey || code == KeyEvent.VK_ENTER) {
                    table.performSwitching();
                    cancelSwitching();
                }
                break;
            }
        }
    
    /**
     * Cancels the popup if present, causing it to close without the active
     * document being changed.
     */
    private void cancelSwitching() {
        hideCurrentPopup();
        StatusDisplayer.getDefault().setStatusText("");
    }
    
    private synchronized void hideCurrentPopup() {
        if (popup != null) {
            // Issue 41121 - use invokeLater to allow any pending ev
            // processing against the popup contents to run before the popup is
            // hidden
            SwingUtilities.invokeLater(new PopupHider(popup));
            popup = null;
        }
    }

    @Override
    public void windowGainedFocus(WindowEvent e) {
    }

    @Override
    public void windowLostFocus(WindowEvent e) {
        //remove the switcher when the main window is deactivated, 
        //e.g. user pressed Ctrl+Esc on MS Windows which opens the Start menu
        if (e.getOppositeWindow() != popup) {
            cancelSwitching();
        }
    }
    
    /**
     * Runnable which hides the popup in a subsequent ev queue loop. This is
     * to avoid problems with BasicToolbarUI, which will try to process events
     * on the component after it has been hidden and throw exceptions.
     *
     * @see http://www.netbeans.org/issues/show_bug.cgi?id=41121
     */
    private class PopupHider implements Runnable {
        private JWindow toHide;
        public PopupHider(JWindow popup) {
            toHide = popup;
        }
        
        @Override
        public void run() {
            toHide.setAlwaysOnTop( false );
            toHide.setVisible(false);
            toHide.dispose();
            shown = false;
            hits = 0;
            // part of #82743 fix
            WindowManager.getDefault().getMainWindow().removeWindowFocusListener( KeyboardPopupSwitcher.this );
        }
    }
}
