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

package org.netbeans.modules.form;

import de.adito.aditoweb.nbm.nbide.nbaditointerface.NbAditoInterface;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.common.IAditoNetbeansTranslations;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.form.model.EModelFormType;
import org.netbeans.modules.form.adito.actions.AditoActionObject;
import org.netbeans.modules.form.adito.components.AditoNodeConnect;
import org.netbeans.modules.form.adito.perstistencemanager.NonvisContainerRADComponent;
import org.netbeans.modules.form.layoutsupport.*;
import org.netbeans.modules.form.palette.PaletteUtils;
import org.netbeans.spi.search.SearchInfoDefinition;
import org.openide.ErrorManager;
import org.openide.actions.*;
import org.openide.explorer.propertysheet.editors.NodeCustomizer;
import org.openide.loaders.DataObject;
import org.openide.nodes.*;
import org.openide.util.*;
import org.openide.util.actions.SystemAction;
import org.openide.util.datatransfer.*;
import org.openide.util.lookup.Lookups;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.beans.*;
import java.security.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;


public class RADComponentNode extends FormNode
    implements RADComponentCookie, FormPropertyCookie
{
  private final static MessageFormat nodeNameFormat =
      new MessageFormat(
          FormUtils.getBundleString("FMT_ComponentNodeName")); // NOI18N
  private final static MessageFormat nodeNoNameFormat =
      new MessageFormat(
          FormUtils.getBundleString("FMT_UnnamedComponentNodeName")); // NOI18N

  private RADComponent component;
  private boolean highlightDisplayName;
  private Map<Integer, Image> img = new HashMap<>();
  @SuppressWarnings("FieldCanBeLocal")
  private PropertyChangeListener propertyChangeListenerReference; // strong reference for weak listener
  private NodeListener nodeListenerReference; // strong reference for weak listener

  public RADComponentNode(RADComponent component)
  {
    this(component instanceof ComponentContainer ?
             new RADChildren((ComponentContainer) component) : Children.LEAF,
         component);
  }

  public RADComponentNode(Children children, RADComponent component)
  {
    this(children, component, AditoNodeConnect.getLookup(component));
  }

  public RADComponentNode(Children children, final RADComponent component, Lookup pLookup)
  {
    super(children, component.getFormModel(),
          pLookup == null ? Lookup.EMPTY : Lookups.exclude(pLookup, DataObject.class, Node.class));
    this.component = component;
    component.setNodeReference(this);
    if (component instanceof ComponentContainer)
      getInstanceContent().add(new ComponentsIndex());
    updateName();
    updateDisplayName();

    propertyChangeListenerReference = evt -> firePropertyChange(evt.getPropertyName(), null, null);
    // wenn sich die Properties �ndern soll das Sheet aktualisiert werden.
    AditoNodeConnect.addWeakPropertyChangeListener(component, propertyChangeListenerReference);

    nodeListenerReference = new NodeAdapter()
    {
      @Override
      public void propertyChange(PropertyChangeEvent evt)
      {
        if (Node.PROP_NAME.equals(evt.getPropertyName()))
        {
          updateName();
          updateDisplayName();
        }
        else if(Node.PROP_PROPERTY_SETS.equals(evt.getPropertyName()))
          firePropertySetsChange(null, null);
      }
    };
    // wenn sich der Name �ndert, soll die Node aktualisiert werden
    AditoNodeConnect.addWeakNodeListener(component, nodeListenerReference);

    Node node = pLookup == null ? null : pLookup.lookup(Node.class);
    if (node != null)
    {
      SearchInfoDefinition searchDef = node.getLookup().lookup(SearchInfoDefinition.class);
      if (searchDef != null)
        getInstanceContent().add(searchDef);
    }
  }

  void updateName()
  {
    String name = AditoNodeConnect.getName(component);
    if (name != null)
      setName(name);
  }

  void updateDisplayName()
  {
    String displayName = AditoNodeConnect.getDisplayName(component);

    if (displayName != null)
      setDisplayName(displayName);
    else
    {
      String compClassName = Utilities.getShortClassName(
          component.getBeanClass());
      if (component == component.getFormModel().getTopRADComponent())
        setDisplayName(nodeNoNameFormat.format(
            new Object[]{compClassName}));
      else
        setDisplayName(nodeNameFormat.format(
            new Object[]{getName(), compClassName}));
    }
  }

  public void fireComponentPropertiesChange()
  {
    firePropertyChange(null, null, null);
  }

  public void fireComponentPropertySetsChange()
  {
    firePropertySetsChange(null, null);
  }

  private static boolean iconsInitialized;

  @Override
  public Image getIcon(final int iconType)
  {
    Image icon = AditoNodeConnect.getIcon(component, iconType);
    if (icon != null)
      return icon;
    icon = img.get(iconType);
    if (icon != null) return icon;

    // try to get a special icon
    icon = BeanSupport.getBeanIcon(component.getBeanClass(), iconType);
    if (icon == null)
    {
      final String className = component.getBeanClass().getName();
      final String classDetails = (String) component.getAuxValue(RADComponent.AUX_VALUE_CLASS_DETAILS);
      if (!iconsInitialized)
      {
        // getIconForClass invokes getNodes(true) which cannot be called in Mutex
        EventQueue.invokeLater(() ->
                               {
                                 Image icon1 = PaletteUtils.getIconForClass(className, classDetails, iconType, true, EModelFormType.UNDEFINED);
                                 iconsInitialized = true;
                                 if (icon1 != null)
                                 {
                                   img.put(iconType, icon1);
                                   fireIconChange();
                                 }
                               });
      }
      else
      {
        icon = PaletteUtils.getIconForClass(className, classDetails, iconType, false, EModelFormType.UNDEFINED);
      }

      if (icon == null)
      {
        // get icon from BeanInfo
        java.beans.BeanInfo bi = component.getBeanInfo();
        if (bi != null)
        {
          icon = bi.getIcon(iconType);
        }

        if (icon == null)
        {
          // use default icon
          icon = super.getIcon(iconType);
        }
      }
    }
    img.put(iconType, icon);
    return icon;
  }

  @Override
  public Image getOpenedIcon(int iconType)
  {
    return getIcon(iconType);
  }

  @Override
  public HelpCtx getHelpCtx()
  {
    return new HelpCtx("gui.components.editing"); // NOI18N
  }

  @Override
  public PropertySet[] getPropertySets()
  {
    return AditoNodeConnect.getPropertySets(component);
  }

  /* List new types that can be created in this node.
   * @return new types
   */
  @Override
  public NewType[] getNewTypes()
  {
    return component.getNewTypes();
  }

  @Override
  public Action getPreferredAction()
  {
    FormEditor formEditor = FormEditor.getFormEditor(component.getFormModel());
    if (formEditor == null)
    {
      return null;
    }

    for (Action action : formEditor.getDefaultComponentActions())
    {
      if (action.isEnabled())
      {
        return action;
      }
    }

    return null;
  }

  @Override
  public Action[] getActions(boolean context)
  {

    RADComponent topComp = component.getFormModel().getTopRADComponent();

    // Liefert Actions der Oberklasse
    super.getActions(context);
    for (Object a : super.getSortedActionList().toArray())
      if(a != null)
        actions.add((AditoActionObject) a);

    // all actions from our own node.
    for (Action action : AditoNodeConnect.getActions(component, true))
    {
      if(action != null)
      {
        Object position = action.getValue("position");
        if (position instanceof Integer)
        {
          actions.add(new AditoActionObject(action, (Integer) position));
          actions.add(new AditoActionObject(null, (Integer) position + 1));
        }
        else
          actions.add(new AditoActionObject(action, Integer.MAX_VALUE));
      }
    }
    actions.add(new AditoActionObject(null, 100));

    if (component != topComp)
    {
      if (canCut())
        actions.add(new AditoActionObject(SystemAction.get(CutAction.class), 200));
      if (canCopy())
        actions.add(new AditoActionObject(SystemAction.get(CopyAction.class), 300));
    }
    if (component instanceof ComponentContainer)
    {
      actions.add(new AditoActionObject(SystemAction.get(PasteAction.class), 400));
    }
    actions.add(new AditoActionObject(null, 500));

    if (component != topComp)
    {
      if (canDestroy())
        actions.add(new AditoActionObject(SystemAction.get(DeleteAction.class), 600));
      if (canRename())
        actions.add(new AditoActionObject(SystemAction.get(ChangeVariableNameAction.class), 700));
    }
    actions.add(new AditoActionObject(null, 800));

    if (canRename() || canCut())
    {
      if (component != topComp)
      {
        actions.add(new AditoActionObject(SystemAction.get(MoveUpAction.class), 900));
        actions.add(new AditoActionObject(SystemAction.get(MoveDownAction.class), 1000));
      }
      if ((component instanceof ComponentContainer) & !(component instanceof NonvisContainerRADComponent))
      {
        actions.add(new AditoActionObject(SystemAction.get(ReorderAction.class), 1100));
      }
    }
    actions.add(new AditoActionObject(null, 1200));

    return actions.toActionArray();
  }

  /*@Override
  public Action[] getActions(boolean context) {
        if (actions == null) {
      List<Action> actions = new ArrayList<Action>(20);
      RADComponent topComp = component.getFormModel().getTopRADComponent();

      if (component.isReadOnly())
      {
        if (component == topComp)
        {
          actions.add(SystemAction.get(TestAction.class));
          actions.add(null);
        }
        Event[] events = component.getKnownEvents();
        for (int i = 0; i < events.length; i++)
        {
          if (events[i].hasEventHandlers())
          {
            actions.add(SystemAction.get(EventsAction.class));
            actions.add(null);
            break;
          }
        }

        actions.add(SystemAction.get(CopyAction.class));
      }
      else
      {
        if (InPlaceEditLayer.supportsEditingFor(component.getBeanClass(), false))
        {
          actions.add(SystemAction.get(InPlaceEditAction.class));
        }
        if (javax.swing.JTable.class.isAssignableFrom(component.getBeanClass()))
        {
          actions.add(SystemAction.get(CustomizeTableAction.class));
        }
        if (component != topComp)
        {
          actions.add(SystemAction.get(ChangeVariableNameAction.class));
        }
        else
        {
          actions.add(SystemAction.get(TestAction.class));
        }
        if (FormEditor.getBindingSupport(component.getFormModel()) != null)
        {
          actions.add(SystemAction.get(BindAction.class));
        }
        actions.add(SystemAction.get(EventsAction.class));
        actions.add(null);

        java.util.List actionProps = component.getActionProperties();
        Iterator iter = actionProps.iterator();
        while (iter.hasNext())
        {
          final RADProperty prop = (RADProperty) iter.next();
          Action action = PropertyAction.createIfEditable(prop);
          if (action != null)
          {
            actions.add(action);
          }
        }
        addSeparator(actions);

        if (component instanceof ComponentContainer)
        {
          addContainerActions(actions);
          addLayoutActions(actions);
        }
        else
        {
          addLayoutActions(actions);
          addContainerActions(actions);
        }
        if (component != topComp)
        {
          actions.add(SystemAction.get(MoveUpAction.class));
          actions.add(SystemAction.get(MoveDownAction.class));
        }
        if (component instanceof ComponentContainer)
        {
          actions.add(SystemAction.get(ReorderAction.class));
        }
        addSeparator(actions);

        if (component != topComp)
        {
          actions.add(SystemAction.get(CutAction.class));
        }
        actions.add(SystemAction.get(CopyAction.class));
        if (component instanceof ComponentContainer)
        {
          actions.add(SystemAction.get(PasteAction.class));
        }
        if (component != topComp)
        {
          actions.add(SystemAction.get(DuplicateAction.class));
          actions.add(SystemAction.get(DeleteAction.class));
        }

        actions.add(null);
        actions.add(SystemAction.get(CustomCodeAction.class));
      }
      actions.add(null);

      javax.swing.Action[] superActions = super.getActions(context);
      for (int i = 0; i < superActions.length; i++)
        actions.add(superActions[i]);

      this.actions = new Action[actions.size()];
      actions.toArray(this.actions);
    }

    return actions;
  }

  private void addLayoutActions(List<Action> actions)
  {
    if (component.getParentComponent() instanceof RADVisualContainer)
    {
      actions.add(SystemAction.get(AlignAction.class));
      actions.add(SystemAction.get(SetAnchoringAction.class));
      actions.add(SystemAction.get(SetResizabilityAction.class));
      actions.add(SystemAction.get(ChooseSameSizeAction.class));
      actions.add(SystemAction.get(DefaultSizeAction.class));
      actions.add(SystemAction.get(EncloseAction.class));
      actions.add(SystemAction.get(CustomizeEmptySpaceAction.class));
      actions.add(null);
    }
  }

  private void addContainerActions(List<Action> actions)
  {
    if (component instanceof RADVisualContainer)
    {
      if (!((RADVisualContainer) component).hasDedicatedLayoutSupport())
      {
        actions.add(SystemAction.get(SelectLayoutAction.class));
        actions.add(SystemAction.get(CustomizeLayoutAction.class));
      }
      if (MenuEditLayer.isMenuBarContainer(component))
      {
        actions.add(SystemAction.get(InsertMenuAction.class));
      }
      else if (MenuEditLayer.isMenuRelatedContainer(component))
      {
        actions.add(SystemAction.get(AddSubItemAction.class));
      }
      else
      { // only use the AddAction for non-menu containers
        actions.add(SystemAction.get(AddAction.class));
      }
    }
    if (getNewTypes().length != 0)
    {
      actions.add(null);
      actions.add(SystemAction.get(NewAction.class));
    }
    if (EditContainerAction.isEditableComponent(component))
    {
      actions.add(SystemAction.get(EditContainerAction.class));
    }
    if (DesignParentAction.isParentEditableComponent(component))
    {
      actions.add(SystemAction.get(DesignParentAction.class));
    }
    if (component.getParentComponent() == null)
    {
      if (component instanceof RADVisualComponent)
      {
        actions.add(SystemAction.get(DefaultSizeAction.class));
      }
      if (component instanceof RADVisualContainer
          && !((RADVisualContainer) component).hasDedicatedLayoutSupport())
      {
        actions.add(SystemAction.get(CustomizeEmptySpaceAction.class));
      }
    }

    addSeparator(actions);
  }

  private static void addSeparator(List<Action> actions)
  {
    int n = actions.size();
    if (n > 0 && actions.get(n - 1) != null)
    {
      actions.add(null);
    }
  }*/

  @Override
  public String getName()
  {
    return component.getName();
  }

  /**
   * Set the system name. Fires a property change event.
   * Also may change the display name according to {@link #displayFormat}.
   *
   * @param s the new name
   */
  @Override
  public void setName(String s)
  {
    component.rename(s.trim());
    // #5185:
    // wenn der displayName nicht gesetzt ist, dann �ndert sich durch setzen des Namens auch der displayName und das
    // muss gefeuert werden.
    fireDisplayNameChange(null, null);
  }

  /**
   * Can this node be renamed?
   *
   * @return <code>false</code>
   */
  @Override
  public boolean canRename()
  {
    return !component.isReadOnly()
        && component != component.getFormModel().getTopRADComponent()
        && AditoNodeConnect.getNode(component).map(Node::canRename).orElse(false);
  }

  /**
   * Can this node be destroyed?
   *
   * @return <CODE>false</CODE>
   */
  @Override
  public boolean canDestroy()
  {
    return !component.isReadOnly()
        && component != component.getFormModel().getTopRADComponent()
        && AditoNodeConnect.getNode(component).map(Node::canDestroy).orElse(false);
  }

  /**
   * Remove the node from its parent and deletes it.
   * The default
   * implementation obtains write access to
   * the {@link Children#MUTEX children's lock}, and removes
   * the node from its parent(if any). Also fires a property change.
   * <P>
   * This may be overridden by subclasses to do any additional
   * cleanup.
   *
   * @throws java.io.IOException if something fails
   */
  @Override
  public void destroy() throws java.io.IOException
  {
    if (component.getNodeReference() == this)
    {
      if (MetaComponentCreator.isTransparentLayoutComponent(component.getParentComponent()))
      {
        component = component.getParentComponent();
      }
      if (EventQueue.isDispatchThread())
      {
        component.getFormModel().removeComponent(component, true);
      }
      else
      {
        EventQueue.invokeLater(() -> component.getFormModel().removeComponent(component, true));
      }
    } // otherwise the component was likely already removed with a parent component
    propertyChangeListenerReference = null;
    nodeListenerReference = null;
    super.destroy();
  }

  /**
   * Test whether there is a customizer for this node. If true,
   * the customizer can be obtained via {@link #getCustomizer}.
   *
   * @return <CODE>true</CODE> if there is a customizer
   */
  @Override
  public boolean hasCustomizer()
  {
    return !component.isReadOnly()
        && ((component.getBeanInfo().getBeanDescriptor().getCustomizerClass() != null));
  }

  /**
   * Creates the customizer component for the node.
   *
   * @return the component, or null if there is no customizer
   */
  @Override
  protected Component createCustomizer()
  {
    Class customizerClass = component.getBeanInfo().getBeanDescriptor().getCustomizerClass();
    if (customizerClass == null)
    {
      // STRIPPED
            /*if (javax.swing.JTable.class.isAssignableFrom(component.getBeanClass())) {
                customizerClass = TableCustomizer.class;
            } else {*/
      return null;
//            }
    }

    Object customizerObject;
    try
    {
      customizerObject = customizerClass.newInstance();
    }
    catch (InstantiationException e)
    {
      ErrorManager.getDefault().notify(ErrorManager.WARNING, e);
      return null;
    }
    catch (IllegalAccessException e)
    {
      ErrorManager.getDefault().notify(ErrorManager.WARNING, e);
      return null;
    }

    if (!(customizerObject instanceof Component)
        || !(customizerObject instanceof Customizer))
      return null;

    if (customizerObject instanceof NodeCustomizer)
      ((NodeCustomizer) customizerObject)
          .attach(component.getNodeReference());

    // Issue 203352 - default values of properties must be initialized
    // before the customizer is shown/used
    //component.ensureDefaultPropertyValuesInitialization(); // STRIPPED

    Customizer customizer = (Customizer) customizerObject;

    customizer.setObject(component.getBeanInstance());

    if (customizerObject instanceof FormAwareEditor)
    {
      // Hack - returns some property
      Node.Property prop = component.getProperties()[0].getProperties()[0];
      ((FormAwareEditor) customizerObject).setContext(component.getFormModel(), (FormProperty) prop);
    }

    customizer.addPropertyChangeListener(
        evt ->
        {
          FormProperty[] properties;
          if (evt.getPropertyName() != null)
          {
            FormProperty changedProperty = component.getBeanProperty(evt.getPropertyName());
            if (changedProperty != null)
              properties = new FormProperty[]{changedProperty};
            else
              return; // non-existing property?
          }
          else
          {
            properties = component.getAllBeanProperties();
            evt = null;
          }
          updatePropertiesFromCustomizer(properties, evt);
        });
    // [undo/redo for customizer probably does not work...]

    return (Component) customizerObject;
  }

  private void updatePropertiesFromCustomizer(
      final FormProperty[] properties,
      final PropertyChangeEvent evt)
  {
    // we run this as privileged to avoid security problems - because
    // the property change is fired from untrusted bean customizer code
    AccessController.doPrivileged(new PrivilegedAction<Object>()
    {
      @Override
      public Object run()
      {
        Object oldValue = evt != null ? evt.getOldValue() : null;
        Object newValue = evt != null ? evt.getNewValue() : null;

        for (FormProperty prop : properties)
        {
          try
          {
            prop.reinstateProperty();
            //                        if (prop.isChanged()) // [what if changed to default value?]
            prop.propertyValueChanged(oldValue, newValue);
          }
          catch (Exception ex)
          { // unlikely to happen
            ex.printStackTrace();
          }
        }
        return null;
      }
    });
  }

  // -----------------------------------------------------------------------------------------
  // Clipboard operations

  /**
   * Test whether this node can be copied.
   * The default implementation returns <code>true</code>.
   *
   * @return <code>true</code> if it can
   */
  @Override
  public boolean canCopy()
  {
    return AditoNodeConnect.getNode(component).map(Node::canCopy).orElse(false);
  }

  /**
   * Test whether this node can be cut.
   * The default implementation assumes it can if this node is writeable.
   *
   * @return <code>true</code> if it can
   */
  @Override
  public boolean canCut()
  {
    return !component.isReadOnly()
        && component != component.getFormModel().getTopRADComponent()
        && AditoNodeConnect.getNode(component).map(Node::canCut).orElse(false);
  }

  /**
   * Copy this node to the clipboard.
   *
   * @return The transferable for RACComponentNode
   * @throws java.io.IOException if it could not copy
   */
  @Override
  public Transferable clipboardCopy() throws java.io.IOException
  {
    return new CopySupport.RADTransferable(
        CopySupport.getComponentCopyFlavor(),
        component);
  }

  /**
   * Cut this node to the clipboard.
   *
   * @return {@link Transferable} with one flavor, RAD_COMPONENT_COPY_FLAVOR
   * @throws java.io.IOException if it could not cut
   */
  @Override
  public Transferable clipboardCut() throws java.io.IOException
  {
    return new CopySupport.RADTransferable(
        CopySupport.getComponentCutFlavor(),
        component);
  }

  /**
   * Accumulate the paste types that this node can handle
   * for a given transferable.
   *
   * @param t a transferable containing clipboard data
   * @param s a list of {@link PasteType}s that will have added to it all
   *          types valid for this node
   */
  @Override
  protected void createPasteTypes(Transferable t, java.util.List<PasteType> s)
  {
    CopySupport.createPasteTypes(t, s, component.getFormModel(), component);
  }

  // -----------------------------------------------------------------------------
  // RADComponentCookie implementation

  @Override
  public RADComponent getRADComponent()
  {
    return component;
  }

  // -----------------------------------
  // FormPropertyCookie implementation

  @Override
  public FormProperty getProperty(String name)
  {
    return component.getPropertyByName(name, FormProperty.class, true);
    //        Node.Property prop = component.getPropertyByName(name, true);
    //        return (FormProperty) (prop instanceof FormProperty ? prop : null);
  }

  // -----------------------------------------------------------------------------
  // Innerclasses

  public static class RADChildren extends FormNodeChildren
  {
    private ComponentContainer container;
    private Object keyLayout;

    public RADChildren(ComponentContainer container)
    {
      super();
      this.container = container;
      updateKeys();
    }

    // FormNodeChildren implementation
    @Override
    protected void updateKeys()
    {
      RADComponent[] subComps = container.getSubBeans();
      List<Object> keys = new ArrayList<Object>(subComps.length + 2);

      if (container instanceof RADVisualContainer)
      {
        RADVisualContainer visualCont = (RADVisualContainer) container;

        RADComponent menuComp = visualCont.getContainerMenu();
        if (menuComp != null)
          keys.add(menuComp);

        if (visualCont.shouldHaveLayoutNode())
        {
          keyLayout = visualCont.getLayoutSupport().getLayoutDelegate(); //new Object(); // [need not be recreated every time]
          keys.add(keyLayout);
        }

        for (RADComponent subComp : subComps)
          if (subComp != menuComp && AditoNodeConnect.shouldHaveNode(visualCont, subComp)) //A
            keys.add(subComp);
      }
      else
      {
        Collections.addAll(keys, subComps);
      }

      setKeys(keys);
    }

    @Override
    protected Node[] createNodes(Object key)
    {
      Node node;
      if (key == keyLayout)
      {
        RADVisualContainer container = (RADVisualContainer) this.container;
        final RADComponent layoutRadComponent = container.getLayoutSupport().getLayoutRadComponent();
        if (layoutRadComponent != null)
          node = new LayoutRADComponentNode(layoutRadComponent);
        else
          node = new LayoutNode(container);
      }
      else
      {
        node = new RADComponentNode((RADComponent) key);
        node.getChildren().getNodes(); // enforce subnodes creation
      }
      return new Node[]{node};
    }
  }

  private final class ComponentsIndex extends org.openide.nodes.Index.Support
  {

    @Override
    public Node[] getNodes()
    {
      RADComponent[] comps;
      if (component instanceof RADVisualContainer)
        comps = ((RADVisualContainer) component).getSubComponents();
      else if (component instanceof ComponentContainer)
        comps = ((ComponentContainer) component).getSubBeans();
      else
        comps = null;

      Node[] nodes = new Node[comps != null ? comps.length : 0];
      for (int i = 0; i < nodes.length; i++)
        nodes[i] = comps[i].getNodeReference();

      return nodes;
    }

    @Override
    public int getNodesCount()
    {
      return getNodes().length;
    }

    @Override
    public void reorder(int[] perm)
    {
      if (component instanceof ComponentContainer)
      {
        ComponentContainer cont = (ComponentContainer) component;
        cont.reorderSubComponents(perm);
        component.getFormModel().fireComponentsReordered(cont, perm);
      }
    }
  }

  private static final class ChangeVariableNameAction extends RenameAction
  {
    @Override
    public String getName()
    {
      return NbAditoInterface.lookup(IAditoNetbeansTranslations.class).getRenameAction();
      //return NbBundle.getMessage(ChangeVariableNameAction.class, "ChangeVariableNameAction"); // NOI18N
    }
  }

  @Override
  public String getHtmlDisplayName()
  {
    if (highlightDisplayName)
    {
      return "<html><b>" + getDisplayName() + "</b></html>"; // NOI18N
    }
    else
    {
      return "<html>" + getDisplayName() + "</html>"; // NOI18N
    }
  }

  void highlightDisplayName(boolean highlight)
  {
    if (highlight != highlightDisplayName)
    {
      highlightDisplayName = highlight;
      fireDisplayNameChange(null, getDisplayName());
    }
  }

  private static final class CustomizeTableAction extends CustomizeAction
  {
    @Override
    public String getName()
    {
      return FormUtils.getBundleString("NAME_CustomizeTableAction"); // NOI18N
    }

    @Override
    protected boolean enable(Node[] activatedNodes)
    {
      return true;
    }
  }

}
