/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * ObjectEditor.java
 *
 * Created on 27-set-2010, 14.11.08
 */
package it.freedomotic.jfrontend;

import com.google.inject.Inject;

import it.freedomotic.api.Client;
import it.freedomotic.api.Plugin;


import it.freedomotic.environment.EnvironmentLogic;
import it.freedomotic.environment.EnvironmentPersistence;

import it.freedomotic.jfrontend.automationeditor.ReactionEditor;
import it.freedomotic.jfrontend.automationeditor.ReactionsPanel;
import it.freedomotic.jfrontend.utils.CheckBoxList;
import it.freedomotic.jfrontend.utils.PropertiesPanel_1;

import it.freedomotic.model.ds.Config;
import it.freedomotic.model.object.Behavior;
import it.freedomotic.model.object.EnvObject;
import it.freedomotic.model.object.Representation;

import it.freedomotic.objects.*;

import it.freedomotic.plugins.ClientStorage;

import it.freedomotic.reactions.Command;
import it.freedomotic.reactions.CommandPersistence;
import it.freedomotic.reactions.Trigger;
import it.freedomotic.reactions.TriggerPersistence;
import it.freedomotic.security.Auth;
import it.freedomotic.util.Info;
import it.freedomotic.util.i18n;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Map.Entry;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 *
 * @author enrico
 */
public class ObjectEditor
        extends javax.swing.JFrame {

    private final EnvObjectLogic object;
    private String oldName;
    private PropertiesPanel_1 commandsControlPanel;
    private PropertiesPanel_1 pnlTriggers;
    //private PropertiesPanel_1 controlPanel;
    ReactionsPanel reactionsPanel;
    @Inject
    private ClientStorage clients;

    /**
     * Creates new form ObjectEditor
     */
    public ObjectEditor(final EnvObjectLogic obj) {
        this.object = obj;
        oldName = object.getPojo().getName();

        EnvObject pojo = obj.getPojo();
        //AVOID the paint of the value over sliders that will conflic with double values
        UIManager.put("Slider.paintValue", false);
        initComponents();
        setSize(600, 400);

        if (obj.getPojo().getActAs().equalsIgnoreCase("virtual")) {
            btnVirtual.setSelected(true);
        }

        checkIfVirtual();
        txtName.setText(pojo.getName());
        txtDescription.setText(pojo.getDescription());
        txtTags.setText(pojo.getTagsString());
        populateProtocol();
        populateEnvironment();
        txtAddress.setText(pojo.getPhisicalAddress());

        Integer x = (int) pojo.getCurrentRepresentation().getOffset().getX();
        Integer y = (int) pojo.getCurrentRepresentation().getOffset().getY();
        Integer rotation = (int) pojo.getCurrentRepresentation().getRotation();
        SpinnerModel modelX =
                new SpinnerNumberModel(0, //initial value
                -100, //min
                (int) obj.getEnv().getPojo().getWidth() + 100, //max= env dimension + 1 meter
                1); //step
        SpinnerModel modelY =
                new SpinnerNumberModel(0, //initial value
                -100, //min
                (int) obj.getEnv().getPojo().getWidth() + 100, //max
                1); //step
        SpinnerModel modelRotation = new SpinnerNumberModel(0, //initial value
                0, //min
                360, //max
                10); //step
        spnX.setModel(modelX);
        spnY.setModel(modelY);
        spnX.setValue((Integer) x);
        spnY.setValue((Integer) y);

        SpinnerModel scaleWidthModel =
                new SpinnerNumberModel(new Double(pojo.getCurrentRepresentation().getScaleX()), //initial value
                new Double(0.1), //min
                new Double(10.0), //max
                new Double(0.1)); //step
        spnScaleWidth.setModel(scaleWidthModel);

        SpinnerModel scaleHeightModel =
                new SpinnerNumberModel(new Double(pojo.getCurrentRepresentation().getScaleY()), //initial value
                new Double(0.1), //min
                new Double(10.0), //max
                new Double(0.1)); //step
        spnScaleHeight.setModel(scaleHeightModel);
        spnRotation.setValue(rotation);

        tabObjectEditor.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (tabObjectEditor.getSelectedComponent().equals(tabAutomations)) {
                    populateAutomationsTab();
                }
            }
        });

        //population combo box representation
        DefaultComboBoxModel representationsModel = new DefaultComboBoxModel();

        for (EnvObjectLogic object : EnvObjectPersistence.getObjectList()) {
            for (Representation rep : object.getPojo().getRepresentations()) {
                representationsModel.addElement(rep);
            }
        }

        if ((pojo.getActAs() != null)
                && !pojo.getActAs().isEmpty()
                && !pojo.getActAs().equalsIgnoreCase("unimplemented")) {
            setTitle(pojo.getName() + " (" + pojo.getActAs() + " " + pojo.getSimpleType() + ")");
        } else {
            setTitle(pojo.getName() + " (" + pojo.getSimpleType() + ")");
        }

//        controlPanel = new PropertiesPanel_1(0, 1);
//        tabControls.add(controlPanel);
        tabControls.setLayout(new BoxLayout(tabControls, BoxLayout.PAGE_AXIS));

        //create an array of controllers for the object behaviors
        int row = 0;

        for (BehaviorLogic b : object.getBehaviors()) {
            if (b instanceof BooleanBehaviorLogic) {
                final BooleanBehaviorLogic bb = (BooleanBehaviorLogic) b;
                final JToggleButton button;

                if (bb.getValue()) {
                    button = new JToggleButton(i18n.msg(this, "set_PROPERTY_VALUE", new Object[]{bb.getName(), i18n.msg("false")}));
                } else {
                    button = new JToggleButton(i18n.msg(this, "set_PROPERTY_VALUE", new Object[]{bb.getName(), i18n.msg("true")}));
                }

                JLabel label = new JLabel(b.getName() + ":");
//                controlPanel.addRow();
//                controlPanel.addElement(label, row, 0);
//                controlPanel.addRow();
//                controlPanel.addElement(button, row+1, 0);
                tabControls.add(label);
                tabControls.add(button);
                button.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Config params = new Config();
                        params.setProperty("value",
                                new Boolean(!bb.getValue()).toString());
                        bb.filterParams(params, true);

                        if (bb.getValue()) {
                            button.setText(i18n.msg(this, "set_PROPERTY_VALUE", new Object[]{bb.getName(), i18n.msg("false")}));
                        } else {
                            button.setText(i18n.msg(this, "set_PROPERTY_VALUE", new Object[]{bb.getName(), i18n.msg("true")}));
                        }
                    }
                });
                button.setEnabled(!b.isReadOnly());
            }

            if (b instanceof RangedIntBehaviorLogic) {
                final RangedIntBehaviorLogic rb = (RangedIntBehaviorLogic) b;
                final JLabel doubleValue = new JLabel(rb.getValueAsString());
                final JPanel sliderPanel = new JPanel(new FlowLayout());
                final JSlider slider = new JSlider();

                slider.setValue(rb.getValue());
                slider.setMaximum(rb.getMax());
                slider.setMinimum(rb.getMin());
                slider.setPaintTicks(true);
                slider.setPaintTrack(true);
                slider.setPaintLabels(false);
                slider.setMajorTickSpacing(rb.getScale() * 10);
                slider.setMinorTickSpacing(rb.getStep());
                slider.setSnapToTicks(true);

                JLabel label = new JLabel(b.getName() + ":");
                tabControls.add(label);
                sliderPanel.add(slider);
                sliderPanel.add(doubleValue);
                tabControls.add(sliderPanel);
                slider.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        if (!slider.getValueIsAdjusting()) {
                            Config params = new Config();
                            params.setProperty("value",
                                    new Integer(slider.getValue()).toString());
                            System.out.println("Slider value: " + slider.getValue());
                            rb.filterParams(params, true);
                        }

                        if (rb.getScale() != 1) {
                            doubleValue.setText(new Double((double) slider.getValue() / rb.getScale()).toString());
                        } else {
                            doubleValue.setText(new Integer(slider.getValue()).toString());
                        }
                    }
                });
                slider.setEnabled(!b.isReadOnly());
            }

            if (b instanceof ListBehaviorLogic) {
                final ListBehaviorLogic lb = (ListBehaviorLogic) b;
                final JComboBox comboBox = new JComboBox();

                for (String listValue : lb.getValuesList()) {
                    comboBox.addItem(listValue);
                }

                comboBox.setEditable(false);
                comboBox.setSelectedItem(lb.getSelected());

                JLabel label = new JLabel(b.getName() + ":");
//                controlPanel.addRow();
//                controlPanel.addElement(label, row, 0);
//                controlPanel.addElement(comboBox, row, 1);
                tabControls.add(label);
                tabControls.add(comboBox);
                comboBox.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        Config params = new Config();
                        params.setProperty("value", (String) comboBox.getSelectedItem());
                        lb.filterParams(params, true);
                    }
                });
                comboBox.setEnabled(!b.isReadOnly());
            }

            if (b instanceof TaxonomyBehaviorLogic) {
                populateMultiselectionList(b);
            }
        }
        enableControls();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        tabObjectEditor = new javax.swing.JTabbedPane();
        tabProperties = new javax.swing.JPanel();
        jLabel14 = new javax.swing.JLabel();
        txtName = new javax.swing.JTextField();
        txtDescription = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        txtAddress = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        btnCreateObjectCopy = new javax.swing.JButton();
        btnDelete = new javax.swing.JButton();
        txtProtocol = new javax.swing.JComboBox();
        btnVirtual = new javax.swing.JCheckBox();
        jLabel8 = new javax.swing.JLabel();
        txtTags = new javax.swing.JTextField();
        tabTriggersConfig = new javax.swing.JPanel();
        tabCommandsConfig = new javax.swing.JPanel();
        tabRepresentation = new javax.swing.JPanel();
        jLabel11 = new javax.swing.JLabel();
        spnX = new javax.swing.JSpinner();
        jLabel12 = new javax.swing.JLabel();
        spnY = new javax.swing.JSpinner();
        spnRotation = new javax.swing.JSpinner();
        jLabel13 = new javax.swing.JLabel();
        chkAllRepresentations = new javax.swing.JCheckBox();
        btnChangeImage = new javax.swing.JButton();
        spnScaleWidth = new javax.swing.JSpinner();
        spnScaleHeight = new javax.swing.JSpinner();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        environmentComboBox = new javax.swing.JComboBox();
        jLabel6 = new javax.swing.JLabel();
        tabControls = new javax.swing.JPanel();
        tabAutomations = new javax.swing.JPanel();
        pnlFrameButtons = new javax.swing.JPanel();
        btnOk = new javax.swing.JButton();
        btnCancel = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Object Editor");
        setMinimumSize(new java.awt.Dimension(500, 300));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });

        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.PAGE_AXIS));

        tabObjectEditor.setTabLayoutPolicy(javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT);
        tabObjectEditor.setTabPlacement(javax.swing.JTabbedPane.LEFT);
        tabObjectEditor.setPreferredSize(new java.awt.Dimension(500, 457));

        jLabel14.setText(i18n.msg("name")+":");

        txtName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtNameActionPerformed(evt);
            }
        });

        jLabel15.setText(i18n.msg("description")+":");

        jPanel1.setLayout( new javax.swing.BoxLayout( jPanel1, javax.swing.BoxLayout.PAGE_AXIS ) );

        tabObjectEditor.setTabLayoutPolicy( javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT );
        tabObjectEditor.setTabPlacement( javax.swing.JTabbedPane.LEFT );
        tabObjectEditor.setPreferredSize( new java.awt.Dimension( 500, 457 ) );

        jLabel14.setText( i18n.msg( "name" ) + ":" );

        txtName.addActionListener( new java.awt.event.ActionListener(  )
            {
                public void actionPerformed( java.awt.event.ActionEvent evt )
                {
                    txtNameActionPerformed( evt );
                }
            } );

        jLabel15.setText( i18n.msg( "description" ) + ":" );

        jLabel1.setText( i18n.msg( "protocol" ) + ":" );

        jLabel8.setText("Tags");

        txtTags.setText("txtTags");
        txtTags.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtTagsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout tabPropertiesLayout = new javax.swing.GroupLayout(tabProperties);
        tabProperties.setLayout(tabPropertiesLayout);
        tabPropertiesLayout.setHorizontalGroup(
            tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabPropertiesLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tabPropertiesLayout.createSequentialGroup()
                        .addGroup(tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel15)
                            .addComponent(jLabel14))
                        .addGap(22, 22, 22)
                        .addGroup(tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(txtDescription, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 311, Short.MAX_VALUE)
                            .addComponent(txtName, javax.swing.GroupLayout.Alignment.LEADING)))
                    .addComponent(btnVirtual)
                    .addGroup(tabPropertiesLayout.createSequentialGroup()
                        .addComponent(btnCreateObjectCopy, javax.swing.GroupLayout.PREFERRED_SIZE, 166, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDelete, javax.swing.GroupLayout.PREFERRED_SIZE, 158, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(tabPropertiesLayout.createSequentialGroup()
                        .addGroup(tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addComponent(jLabel2)
                            .addComponent(jLabel8))
                        .addGap(38, 38, 38)
                        .addGroup(tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel3)
                            .addComponent(txtAddress)
                            .addComponent(txtProtocol, 0, 311, Short.MAX_VALUE)
                            .addComponent(txtTags))))
                .addContainerGap(2156, Short.MAX_VALUE))
        );
        tabPropertiesLayout.setVerticalGroup(
            tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabPropertiesLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel14)
                    .addComponent(txtName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel15)
                    .addComponent(txtDescription, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(btnVirtual)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtProtocol, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtAddress, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(txtTags, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 157, Short.MAX_VALUE)
                .addGroup(tabPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnCreateObjectCopy)
                    .addComponent(btnDelete))
                .addContainerGap())
        );

        tabObjectEditor.addTab(i18n.msg("properties"), tabProperties);

        tabTriggersConfig.setLayout(new java.awt.BorderLayout());
        tabObjectEditor.addTab(i18n.msg(this,"data_sources"), tabTriggersConfig);

        tabCommandsConfig.setLayout(new java.awt.BorderLayout());
        tabObjectEditor.addTab(i18n.msg("actions"), tabCommandsConfig);

        jLabel11.setText(i18n.msg("position_X",new Object[]{"X"})+":");

        spnX.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spnXStateChanged(evt);
            }
        });

        jLabel3.setForeground( new java.awt.Color( 121, 121, 121 ) );
        jLabel3.setText( i18n.msg( this, "plugins_more_info" ) );

        btnCreateObjectCopy.setText( i18n.msg( "create_a_copy" ) );
        btnCreateObjectCopy.addActionListener( new java.awt.event.ActionListener(  )
            {
                public void actionPerformed( java.awt.event.ActionEvent evt )
                {
                    btnCreateObjectCopyActionPerformed( evt );
                }
            } );

        btnDelete.setText( i18n.msg( "delete" ) + i18n.msg( "object" ) );
        btnDelete.addActionListener( new java.awt.event.ActionListener(  )
            {
                public void actionPerformed( java.awt.event.ActionEvent evt )
                {
                    btnDeleteActionPerformed( evt );
                }
            } );

        btnVirtual.setText( i18n.msg( this, "is_virtual_object" ) );
        btnVirtual.addActionListener( new java.awt.event.ActionListener(  )
            {
                public void actionPerformed( java.awt.event.ActionEvent evt )
                {
                    btnVirtualActionPerformed( evt );
                }
            } );

        tabObjectEditor.addTab( i18n.msg( "properties" ),
                                tabProperties );

        tabTriggersConfig.setLayout( new java.awt.BorderLayout(  ) );
        tabObjectEditor.addTab( i18n.msg( this, "data_sources" ),
                                tabTriggersConfig );

        tabCommandsConfig.setLayout( new java.awt.BorderLayout(  ) );
        tabObjectEditor.addTab( i18n.msg( "actions" ),
                                tabCommandsConfig );

        jLabel11.setText( i18n.msg( "position_X",
                                    new Object[] { "X" } ) + ":" );

        spnX.addChangeListener( new javax.swing.event.ChangeListener(  )
            {
                public void stateChanged( javax.swing.event.ChangeEvent evt )
                {
                    spnXStateChanged( evt );
                }
            } );

        jLabel12.setText( i18n.msg( "position_X",
                                    new Object[] { "Y" } ) + ":" );

        spnY.addChangeListener( new javax.swing.event.ChangeListener(  )
            {
                public void stateChanged( javax.swing.event.ChangeEvent evt )
                {
                    spnYStateChanged( evt );
                }
            } );

        spnRotation.addChangeListener( new javax.swing.event.ChangeListener(  )
            {
                public void stateChanged( javax.swing.event.ChangeEvent evt )
                {
                    spnRotationStateChanged( evt );
                }
            } );

        jLabel13.setText( i18n.msg( "rotation" ) + ":" );

        chkAllRepresentations.setText( i18n.msg( this, "apply_changes_all_representations" ) );
        chkAllRepresentations.addActionListener( new java.awt.event.ActionListener(  )
            {
                public void actionPerformed( java.awt.event.ActionEvent evt )
                {
                    chkAllRepresentationsActionPerformed( evt );
                }
            } );

        btnChangeImage.setText( i18n.msg( "change_X",
                                          new Object[] { i18n.msg( "image" ) } ) );
        btnChangeImage.addActionListener( new java.awt.event.ActionListener(  )
            {
                public void actionPerformed( java.awt.event.ActionEvent evt )
                {
                    btnChangeImageActionPerformed( evt );
                }
            } );

        spnScaleWidth.addChangeListener( new javax.swing.event.ChangeListener(  )
            {
                public void stateChanged( javax.swing.event.ChangeEvent evt )
                {
                    spnScaleWidthStateChanged( evt );
                }
            } );

        jLabel4.setText(i18n.msg("width")+":");

        jLabel5.setText(i18n.msg("height")+":");

        jLabel6.setText(i18n.msg("environment") + ":");

        javax.swing.GroupLayout tabRepresentationLayout = new javax.swing.GroupLayout(tabRepresentation);
        tabRepresentation.setLayout(tabRepresentationLayout);
        tabRepresentationLayout.setHorizontalGroup(
            tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabRepresentationLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tabRepresentationLayout.createSequentialGroup()
                        .addComponent(chkAllRepresentations, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(18, 18, 18))
                    .addGroup(tabRepresentationLayout.createSequentialGroup()
                        .addGroup(tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(tabRepresentationLayout.createSequentialGroup()
                                .addGroup(tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel11)
                                    .addComponent(jLabel12)
                                    .addComponent(jLabel4)
                                    .addComponent(jLabel5)
                                    .addComponent(jLabel6))
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(tabRepresentationLayout.createSequentialGroup()
                                .addGroup(tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(spnRotation, javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(spnScaleHeight, javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(spnScaleWidth, javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(spnY)
                                    .addComponent(spnX, javax.swing.GroupLayout.Alignment.LEADING))
                                .addGap(18, 18, 18)
                                .addComponent(btnChangeImage, javax.swing.GroupLayout.PREFERRED_SIZE, 93, Short.MAX_VALUE))
                            .addComponent(environmentComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(2343, 2343, 2343))))
        );
        tabRepresentationLayout.setVerticalGroup(
            tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabRepresentationLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(chkAllRepresentations)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel11)
                    .addComponent(spnX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnChangeImage))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12)
                    .addComponent(spnY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(8, 8, 8)
                .addGroup(tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tabRepresentationLayout.createSequentialGroup()
                        .addComponent(spnRotation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(spnScaleWidth, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel4)))
                    .addComponent(jLabel13))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(spnScaleHeight, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(tabRepresentationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(environmentComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6))
                .addContainerGap(197, Short.MAX_VALUE))
        );

        tabObjectEditor.addTab(i18n.msg("appearance"), tabRepresentation);

        tabControls.setLayout(new java.awt.BorderLayout());
        tabObjectEditor.addTab(i18n.msg(this,"control_panel"), tabControls);

        tabAutomations.setLayout(new java.awt.BorderLayout());
        tabObjectEditor.addTab(i18n.msg("automations"), tabAutomations);

        jPanel1.add(tabObjectEditor);

        btnOk.setText(i18n.msg("ok"));
        btnOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOkActionPerformed(evt);
            }
        });
        pnlFrameButtons.add(btnOk);

        btnCancel.setText(i18n.msg("cancel"));
        btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });
        pnlFrameButtons.add(btnCancel);

        getContentPane(  ).add( jPanel1, java.awt.BorderLayout.CENTER );

        pack(  );
    } // </editor-fold>//GEN-END:initComponents

    private void btnOkActionPerformed(java.awt.event.ActionEvent evt)    {//GEN-FIRST:event_btnOkActionPerformed

        if ((!txtProtocol.getSelectedItem().toString().equals(""))
                && (!txtAddress.getText().equals(""))) {
            EnvObject pojo = object.getPojo();
            pojo.setProtocol(txtProtocol.getSelectedItem().toString());
            pojo.setPhisicalAddress(txtAddress.getText());
            pojo.setDescription(txtDescription.getText());
            pojo.getTagsList().clear();
            object.addTags(txtTags.getText());
            txtTags.setText(pojo.getTagsString());
            if (!(oldName.equals(txtName.getText().trim()))) {
                object.rename(txtName.getText().trim());
            }

            if (btnVirtual.isSelected()) {
                pojo.setActAs("virtual");
            } else {
                pojo.setActAs("");
            }

            //object.setChanged(true);
            saveRepresentationChanges();

            if (reactionsPanel != null) {
                for (Component component : reactionsPanel.getPanel().getComponents()) {
                    if (component instanceof ReactionEditor) {
                        ReactionEditor editor = (ReactionEditor) component;
                        editor.finalizeEditing();
                    }
                }
            }
            if (environmentComboBox.getSelectedItem() != null) {
                EnvironmentLogic selEnv = (EnvironmentLogic) environmentComboBox.getSelectedItem();
                object.setEnv(selEnv);
                object.setChanged(true);
            }

            this.dispose();
        }
    }//GEN-LAST:event_btnOkActionPerformed

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt)    {//GEN-FIRST:event_btnCancelActionPerformed
        this.dispose();
    }//GEN-LAST:event_btnCancelActionPerformed

    private void btnChangeImageActionPerformed(java.awt.event.ActionEvent evt)    {//GEN-FIRST:event_btnChangeImageActionPerformed

        JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(new File(Info.getResourcesPath()));

        int returnVal = fc.showDialog(this, "Use as object icon");

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            object.getPojo().getCurrentRepresentation().setIcon(file.getName());
        }
    }//GEN-LAST:event_btnChangeImageActionPerformed

    private void chkAllRepresentationsActionPerformed(java.awt.event.ActionEvent evt) {
//GEN-FIRST:event_chkAllRepresentationsActionPerformed
    }//GEN-LAST:event_chkAllRepresentationsActionPerformed

    private void spnRotationStateChanged(javax.swing.event.ChangeEvent evt)    {//GEN-FIRST:event_spnRotationStateChanged
        saveRepresentationChanges();
    }//GEN-LAST:event_spnRotationStateChanged

    private void spnYStateChanged(javax.swing.event.ChangeEvent evt)    {//GEN-FIRST:event_spnYStateChanged
        saveRepresentationChanges();
    }//GEN-LAST:event_spnYStateChanged

    private void spnXStateChanged(javax.swing.event.ChangeEvent evt)    {//GEN-FIRST:event_spnXStateChanged
        saveRepresentationChanges();
    }//GEN-LAST:event_spnXStateChanged

    private void spnScaleWidthStateChanged(javax.swing.event.ChangeEvent evt)    {//GEN-FIRST:event_spnScaleWidthStateChanged

        Representation rep = object.getPojo().getCurrentRepresentation();
//        Shape shape = AWTConverter.convertToAWT(rep.getShape(), 1.5, 1.5);
//                new Double(spnScaleWidth.getValue().toString()).doubleValue(), 
//                new Double(spnScaleHeight.getValue().toString()).doubleValue());
        rep.setScaleX(new Double(spnScaleWidth.getValue().toString()).doubleValue());

        //rep.setScaleX(new Double(spnScaleWidth.getValue().toString()).doubleValue());
        //object.setChanged(true);
    }//GEN-LAST:event_spnScaleWidthStateChanged

    private void spnScaleHeightStateChanged(javax.swing.event.ChangeEvent evt)    {//GEN-FIRST:event_spnScaleHeightStateChanged
        saveRepresentationChanges();
    }//GEN-LAST:event_spnScaleHeightStateChanged

    private void formWindowClosed(java.awt.event.WindowEvent evt) {
//GEN-FIRST:event_formWindowClosed
        // TODO add your handling code here:
    }//GEN-LAST:event_formWindowClosed

    private void btnDeleteActionPerformed(java.awt.event.ActionEvent evt)    {//GEN-FIRST:event_btnDeleteActionPerformed

        int result =
                JOptionPane.showConfirmDialog(null,
                i18n.msg("confirm_object_delete"),
                i18n.msg("configm_deletion_title"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            EnvObjectPersistence.remove(object);
            this.dispose();
        }
    }//GEN-LAST:event_btnDeleteActionPerformed

    private void btnCreateObjectCopyActionPerformed(java.awt.event.ActionEvent evt)    {//GEN-FIRST:event_btnCreateObjectCopyActionPerformed
        //        EnvObject pojoCopy = null;
        //
        //        pojoCopy = SerialClone.clone(object.getPojo());
        //
        //        pojoCopy.setName(object.getPojo().getName() + UidGenerator.getNextStringUid());
        //        pojoCopy.setProtocol(object.getPojo().getProtocol());
        //        pojoCopy.setPhisicalAddress("unknown");
        //        for (Representation view : pojoCopy.getRepresentations()) {
        //            view.setOffset(0, 0);
        //        }
        //        EnvObjectLogic envObjectLogic = EnvObjectFactory.create(pojoCopy);
        //        envObjectLogic.getPojo().setUUID("");
        EnvObjectPersistence.add(object, EnvObjectPersistence.MAKE_UNIQUE);
        //object.setChanged(true);
        this.dispose();
    }//GEN-LAST:event_btnCreateObjectCopyActionPerformed

    private void txtNameActionPerformed(java.awt.event.ActionEvent evt) {
//GEN-FIRST:event_txtNameActionPerformed
        // TODO addAndRegister your handling code here:
    }//GEN-LAST:event_txtNameActionPerformed

    private void btnVirtualActionPerformed(java.awt.event.ActionEvent evt)    {//GEN-FIRST:event_btnVirtualActionPerformed
        checkIfVirtual();
    }//GEN-LAST:event_btnVirtualActionPerformed

    private void txtTagsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtTagsActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtTagsActionPerformed

    private void checkIfVirtual() {
        if (btnVirtual.isSelected()) {
            tabObjectEditor.remove(tabTriggersConfig);
            tabObjectEditor.remove(tabCommandsConfig);
            txtProtocol.setEnabled(false);
            txtAddress.setEnabled(false);
        } else {
            txtProtocol.setEnabled(true);
            txtAddress.setEnabled(true);
            populateCommandsTab();
            populateTriggersTab();
            tabObjectEditor.add(tabTriggersConfig, 1);
            tabObjectEditor.add(tabCommandsConfig, 2);
        }
    }

    private void changeCurrentRepresentation() {
        try {
            Representation b = object.getPojo().getCurrentRepresentation();
            int x = (Integer) spnX.getValue();
            int y = (Integer) spnY.getValue();
            int rotation = (Integer) spnRotation.getValue();
            b.setOffset(x, y);
            b.setRotation(rotation);

            //object.setChanged(true);
        } catch (NumberFormatException numberFormatException) {
        }
    }

    private void saveRepresentationChanges() {
        if (chkAllRepresentations.isSelected()) {
            changeAllRepresentations();
        } else {
            changeCurrentRepresentation();
        }
    }

    private void changeAllRepresentations() {
        try {
            for (Representation b : this.object.getPojo().getRepresentations()) {
                int x = (Integer) spnX.getValue();
                int y = (Integer) spnY.getValue();
                int rotation = (Integer) spnRotation.getValue();
                b.setOffset(x, y);
                b.setRotation(rotation);
            }

            //object.setChanged(true);
        } catch (NumberFormatException numberFormatException) {
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnChangeImage;
    private javax.swing.JButton btnCreateObjectCopy;
    private javax.swing.JButton btnDelete;
    private javax.swing.JButton btnOk;
    private javax.swing.JCheckBox btnVirtual;
    private javax.swing.JCheckBox chkAllRepresentations;
    private javax.swing.JComboBox environmentComboBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel pnlFrameButtons;
    private javax.swing.JSpinner spnRotation;
    private javax.swing.JSpinner spnScaleHeight;
    private javax.swing.JSpinner spnScaleWidth;
    private javax.swing.JSpinner spnX;
    private javax.swing.JSpinner spnY;
    private javax.swing.JPanel tabAutomations;
    private javax.swing.JPanel tabCommandsConfig;
    private javax.swing.JPanel tabControls;
    private javax.swing.JTabbedPane tabObjectEditor;
    private javax.swing.JPanel tabProperties;
    private javax.swing.JPanel tabRepresentation;
    private javax.swing.JPanel tabTriggersConfig;
    private javax.swing.JTextField txtAddress;
    private javax.swing.JTextField txtDescription;
    private javax.swing.JTextField txtName;
    private javax.swing.JComboBox txtProtocol;
    private javax.swing.JTextField txtTags;
    // End of variables declaration//GEN-END:variables

    // End of variables declaration//GEN-END:variables
    private void populateCommandsTab() {
        //addAndRegister a properties panel
        commandsControlPanel = new PropertiesPanel_1(0, 2);
        tabCommandsConfig.setName(i18n.msg("actions"));
        tabCommandsConfig.add(commandsControlPanel);

        //creates an array of mapping behavior to hardware command
        int row = 0;

        for (final String action : object.getPojo().getActions().stringPropertyNames()) {
            //addAndRegister a combo box with the list of alla available commands
            DefaultComboBoxModel allHardwareCommands = new DefaultComboBoxModel();

            for (Command command : CommandPersistence.getHardwareCommands()) {
                allHardwareCommands.addElement(command);
            }

            final JComboBox cmbCommand = new JComboBox();
            cmbCommand.setModel(allHardwareCommands);

            EnvObjectLogic objLogic = (EnvObjectLogic) object;
            Command relatedCommand = objLogic.getHardwareCommand(action);

            if (relatedCommand != null) {
                //related harware command is already defined
                cmbCommand.setSelectedItem(relatedCommand);
            } else {
                cmbCommand.setSelectedIndex(-1); //no mapping
            }

            cmbCommand.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Command command = (Command) cmbCommand.getSelectedItem();
                    object.setAction(action, command);
                }
            });
            commandsControlPanel.addRow();

            JLabel lblAction = new JLabel(action + ": ");
            commandsControlPanel.addElement(lblAction, row, 0);
            commandsControlPanel.addElement(cmbCommand, row, 1);
            //lblAction.setPreferredSize(new Dimension(350, 30));
            row++;
        }

        commandsControlPanel.layoutPanel();
    }

    private void populateTriggersTab() {
        //addAndRegister a properties panel
        pnlTriggers = new PropertiesPanel_1(0, 2);
        tabTriggersConfig.setName(i18n.msg(this, "data_sources"));
        tabTriggersConfig.add(pnlTriggers);

        //creates an array of mapping behavior to hardware trigger
        int row = 0;

        for (final Behavior behavior : object.getPojo().getBehaviors()) {
            //addAndRegister a combo box with the list of all available hardware triggers
            DefaultComboBoxModel model = new DefaultComboBoxModel();

            for (Trigger trigger : TriggerPersistence.getTriggers()) {
                if (trigger.isHardwareLevel()) {
                    model.addElement(trigger);
                }
            }

            final JComboBox comboSelectTrigger = new JComboBox();
            comboSelectTrigger.setModel(model);

            //check if the object has already defined a mapping trigger -> behavior
            //if true select it
            String relatedTriggerName = "";

            for (Entry e : object.getPojo().getTriggers().entrySet()) {
                if (e.getValue().equals(behavior.getName())) {
                    //it has already a mapping
                    relatedTriggerName = (String) e.getKey();
                }
            }

            Trigger relatedTrigger = TriggerPersistence.getTrigger(relatedTriggerName);

            //if related harware trigger is already defined
            if (relatedTrigger != null) {
                comboSelectTrigger.setSelectedItem(relatedTrigger);
            } else {
                //no selection
                comboSelectTrigger.setSelectedIndex(-1);
            }

            //addAndRegister a listener on trigger combobox item selection
            comboSelectTrigger.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    object.addTriggerMapping((Trigger) comboSelectTrigger.getSelectedItem(),
                            behavior.getName());
                    tabTriggersConfig.removeAll();
                    pnlTriggers = null;
                    populateTriggersTab();
                }
            });

            JLabel behaviorLabel = new JLabel(behavior.getName() + ": ");
            pnlTriggers.addRow();
            pnlTriggers.addElement(behaviorLabel, row, 0);
            pnlTriggers.addElement(comboSelectTrigger, row, 1);
            //comboSelectTrigger.setPreferredSize(new Dimension(350, 30));
            row++;
        }

        pnlTriggers.layoutPanel();
    }

    private void populateAutomationsTab() {
        tabAutomations.removeAll();
        tabAutomations.setLayout(new BorderLayout());
        reactionsPanel = new ReactionsPanel(object);
        tabAutomations.add(reactionsPanel);
        tabAutomations.validate();
    }

    private void populateProtocol() {
        txtProtocol.addItem("unknown");

        for (Client client : clients.getClients("Plugin")) {
            Plugin plugin = (Plugin) client;
            String protocol = plugin.getConfiguration().getStringProperty("protocol.name", "");

            if (!protocol.isEmpty()) {
                txtProtocol.addItem(protocol);

                //set the current protocol value
                if (object.getPojo().getProtocol().equalsIgnoreCase(protocol)) {
                    txtProtocol.setSelectedItem(protocol);
                }
            }
        }
    }

    private void populateEnvironment() {
        for (EnvironmentLogic env : EnvironmentPersistence.getEnvironments()) {
            environmentComboBox.addItem(env);
        }

        environmentComboBox.setSelectedItem(object.getEnv());
    }

    private void populateMultiselectionList(BehaviorLogic b) {
        final TaxonomyBehaviorLogic lb = (TaxonomyBehaviorLogic) b;
        JLabel label = new JLabel(b.getName() + ":");
        final CheckBoxList list = new CheckBoxList();
        final JTextField newItem = new JTextField(i18n.msg(this, "add_new_item"));
        JButton btnAdd = new JButton(i18n.msg("add"));
        btnAdd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!newItem.getText().isEmpty()) {
                    refreshMultiselectionList(lb, list);

                    Config params = new Config();
                    params.setProperty("item",
                            newItem.getText());
                    params.setProperty("value", "add");
                    lb.filterParams(params, true);
                }
            }
        });

        JButton btnRemove = new JButton(i18n.msg("remove"));
        btnRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!newItem.getText().isEmpty()) {
                    Config params = new Config();
                    params.setProperty("item",
                            newItem.getText());
                    params.setProperty("value", "remove");
                    lb.filterParams(params, true);
                    refreshMultiselectionList(lb, list);
                }
            }
        });
        refreshMultiselectionList(lb, list);
        tabControls.add(label);
        tabControls.add(list);
        tabControls.add(newItem);
        tabControls.add(btnAdd);
        tabControls.add(btnRemove);
        tabControls.validate();
    }

    private void refreshMultiselectionList(final TaxonomyBehaviorLogic source, CheckBoxList list) {
        final DefaultListModel model = new DefaultListModel();

        for (String item : source.getList()) {
            final JCheckBox box = new JCheckBox(item);

            if (!model.contains(box)) { //no duplicates allowed
                box.addChangeListener(new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        Config params = new Config();
                        params.setProperty("item",
                                box.getText());
                        params.setProperty("value",
                                new Boolean(box.isSelected()).toString());
                        source.filterParams(params, true);
                    }
                });
                box.setSelected(source.getSelected().contains(item));
                model.addElement(box);
            }
        }

        list.setModel(model);
        tabControls.validate();
    }

    private void enableControls() {
        txtDescription.setEnabled(Auth.isPermitted("objects:update"));
        txtName.setEnabled(Auth.isPermitted("objects:update"));
        txtProtocol.setEnabled(!btnVirtual.isSelected() && Auth.isPermitted("objects:update"));
        txtAddress.setEnabled(!btnVirtual.isSelected() && Auth.isPermitted("objects:update"));
        btnVirtual.setEnabled(Auth.isPermitted("objects:update"));
        btnChangeImage.setEnabled(Auth.isPermitted("objects:update"));
        spnRotation.setEnabled(Auth.isPermitted("objects:update"));
        spnScaleHeight.setEnabled(Auth.isPermitted("objects:update"));
        spnScaleWidth.setEnabled(Auth.isPermitted("objects:update"));
        spnX.setEnabled(Auth.isPermitted("objects:update"));
        spnY.setEnabled(Auth.isPermitted("objects:update"));
        environmentComboBox.setEditable(Auth.isPermitted("objects:update"));
        btnDelete.setEnabled(Auth.isPermitted("objects:delete"));
    }
}