/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.ws;

import static com.mirth.connect.connectors.ws.WebServiceConnectorServiceMethods.*;

import java.awt.Color;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jdesktop.swingx.decorator.Highlighter;
import org.jdesktop.swingx.decorator.HighlighterFactory;
import org.syntax.jedit.SyntaxDocument;
import org.syntax.jedit.tokenmarker.XMLTokenMarker;

import com.mirth.connect.client.ui.ConnectorTypeDecoration;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.Mirth;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.TextFieldCellEditor;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthTable;
import com.mirth.connect.client.ui.panels.connectors.ConnectorSettingsPanel;
import com.mirth.connect.connectors.http.SSLWarningPanel;
import com.mirth.connect.connectors.ws.DefinitionServiceMap.DefinitionPortMap;
import com.mirth.connect.connectors.ws.DefinitionServiceMap.PortInformation;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

public class WebServiceSender extends ConnectorSettingsPanel {

    private static final ImageIcon ICON_LOCK_X = new ImageIcon(Frame.class.getResource("images/lock_x.png"));
    private static final Color COLOR_SSL_NOT_CONFIGURED = new Color(0xFFF099);
    private static final String SSL_TOOL_TIP = "<html>The default system certificate store will be used for this connection.<br/>As a result, certain security options are not available and mutual<br/>authentication (two-way authentication) is not supported.</html>";

    private final int ID_COLUMN_NUMBER = 0;
    private final int CONTENT_COLUMN_NUMBER = 1;
    private final int MIME_TYPE_COLUMN_NUMBER = 2;
    private final String ID_COLUMN_NAME = "ID";
    private final String CONTENT_COLUMN_NAME = "Content";
    private final String MIME_TYPE_COLUMN_NAME = "MIME Type";
    ObjectXMLSerializer serializer = ObjectXMLSerializer.getInstance();
    private Frame parent;
    private DefinitionServiceMap currentServiceMap;
    private SSLWarningPanel sslWarningPanel;

    public WebServiceSender() {
        this.parent = PlatformUI.MIRTH_FRAME;
        initComponents();

        SyntaxDocument document = new SyntaxDocument();
        document.setTokenMarker(new XMLTokenMarker());
        soapEnvelope.setDocument(document);

        KeyListener keyListener = new KeyAdapter() {
            public void keyReleased(KeyEvent evt) {
                urlFieldChanged();
            }
        };
        wsdlUrlField.addKeyListener(keyListener);
        soapActionField.addKeyListener(keyListener);

        sslWarningPanel = new SSLWarningPanel();
    }

    @Override
    public String getConnectorName() {
        return new WebServiceDispatcherProperties().getName();
    }

    @Override
    public ConnectorProperties getProperties() {
        WebServiceDispatcherProperties properties = new WebServiceDispatcherProperties();

        properties.setWsdlUrl(wsdlUrlField.getText());
        properties.setService(StringUtils.trimToEmpty((String) serviceComboBox.getSelectedItem()));
        properties.setPort(StringUtils.trimToEmpty((String) portComboBox.getSelectedItem()));
        properties.setLocationURI(StringUtils.trimToEmpty((String) locationURIComboBox.getSelectedItem()));
        properties.setSoapAction(soapActionField.getText());

        properties.setOneWay(invocationOneWayRadio.isSelected());

        if (operationComboBox.getSelectedIndex() != -1) {
            properties.setOperation((String) operationComboBox.getSelectedItem());
        }

        properties.setUseAuthentication(authenticationYesRadio.isSelected());

        properties.setUsername(usernameField.getText());
        properties.setPassword(new String(passwordField.getPassword()));

        properties.setEnvelope(soapEnvelope.getText());

        ArrayList<String> operations = new ArrayList<String>();
        for (int i = 0; i < operationComboBox.getModel().getSize(); i++) {
            operations.add((String) operationComboBox.getModel().getElementAt(i));
        }

        properties.setWsdlDefinitionMap(currentServiceMap);

        properties.setUseMtom(useMtomYesRadio.isSelected());

        List<List<String>> attachments = getAttachments();
        properties.setAttachmentNames(attachments.get(0));
        properties.setAttachmentContents(attachments.get(1));
        properties.setAttachmentTypes(attachments.get(2));

        return properties;
    }

    @Override
    public void setProperties(ConnectorProperties properties) {
        WebServiceDispatcherProperties props = (WebServiceDispatcherProperties) properties;

        wsdlUrlField.setText(props.getWsdlUrl());
        soapActionField.setText(props.getSoapAction());
        urlFieldChanged();

        soapEnvelope.setText(props.getEnvelope());

        if (props.isUseAuthentication()) {
            authenticationYesRadio.setSelected(true);
            authenticationYesRadioActionPerformed(null);
        } else {
            authenticationNoRadio.setSelected(true);
            authenticationNoRadioActionPerformed(null);
        }

        usernameField.setText(props.getUsername());
        passwordField.setText(props.getPassword());

        if (props.isOneWay()) {
            invocationOneWayRadio.setSelected(true);
        } else {
            invocationTwoWayRadio.setSelected(true);
        }

        boolean enabled = parent.isSaveEnabled();

        currentServiceMap = props.getWsdlDefinitionMap();
        loadServiceMap();

        serviceComboBox.setSelectedItem(props.getService());
        portComboBox.setSelectedItem(props.getPort());
        locationURIComboBox.setSelectedItem(props.getLocationURI());
        operationComboBox.setSelectedItem(props.getOperation());
        generateEnvelope.setEnabled(!isDefaultOperations());

        parent.setSaveEnabled(enabled);

        List<List<String>> attachments = new ArrayList<List<String>>();

        attachments.add(props.getAttachmentNames());
        attachments.add(props.getAttachmentContents());
        attachments.add(props.getAttachmentTypes());

        setAttachments(attachments);

        if (props.isUseMtom()) {
            useMtomYesRadio.setSelected(true);
            useMtomYesRadioActionPerformed(null);
        } else {
            useMtomNoRadio.setSelected(true);
            useMtomNoRadioActionPerformed(null);
        }
    }

    @Override
    public ConnectorProperties getDefaults() {
        return new WebServiceDispatcherProperties();
    }

    @Override
    public boolean checkProperties(ConnectorProperties properties, boolean highlight) {
        WebServiceDispatcherProperties props = (WebServiceDispatcherProperties) properties;

        boolean valid = true;

        if (props.getWsdlUrl().length() == 0) {
            valid = false;
            if (highlight) {
                wsdlUrlField.setBackground(UIConstants.INVALID_COLOR);
            }
        }

        if (props.getService().length() == 0) {
            valid = false;
            if (highlight) {
                serviceComboBox.setBackground(UIConstants.INVALID_COLOR);
            }
        }

        if (props.getPort().length() == 0) {
            valid = false;
            if (highlight) {
                portComboBox.setBackground(UIConstants.INVALID_COLOR);
            }
        }

        if (props.getEnvelope().length() == 0) {
            valid = false;
            if (highlight) {
                soapEnvelope.setBackground(UIConstants.INVALID_COLOR);
            }
        }

        return valid;
    }

    @Override
    public void resetInvalidProperties() {
        wsdlUrlField.setBackground(null);
        urlFieldChanged();
        serviceComboBox.setBackground(new Color(0xDEDEDE));
        portComboBox.setBackground(new Color(0xDEDEDE));
        soapEnvelope.setBackground(null);
    }

    @Override
    public ConnectorTypeDecoration getConnectorTypeDecoration() {
        if (isUsingHttps(wsdlUrlField.getText()) || isUsingHttps(soapActionField.getText())) {
            return new ConnectorTypeDecoration("(SSL Not Configured)", ICON_LOCK_X, SSL_TOOL_TIP, sslWarningPanel, COLOR_SSL_NOT_CONFIGURED);
        } else {
            return new ConnectorTypeDecoration();
        }
    }

    @Override
    public void doLocalDecoration(ConnectorTypeDecoration connectorTypeDecoration) {
        if (connectorTypeDecoration != null) {
            wsdlUrlField.setIcon(connectorTypeDecoration.getIcon());
            wsdlUrlField.setAlternateToolTipText(connectorTypeDecoration.getIconToolTipText());
            wsdlUrlField.setIconPopupMenuComponent(connectorTypeDecoration.getIconPopupComponent());
            wsdlUrlField.setBackground(connectorTypeDecoration.getHighlightColor());
            soapActionField.setIcon(connectorTypeDecoration.getIcon());
            soapActionField.setBackground(connectorTypeDecoration.getHighlightColor());
            soapActionField.setAlternateToolTipText(connectorTypeDecoration.getIconToolTipText());
            soapActionField.setIconPopupMenuComponent(connectorTypeDecoration.getIconPopupComponent());
        }
    }

    @Override
    public void handleConnectorServiceResponse(String method, Object response) {
        if (method.equals(CACHE_WSDL_FROM_URL)) {
            invokeConnectorService(GET_DEFINITION, "Retrieving cached WSDL definition map...", "There was an error retriving the cached WSDL definition map.\n\n");
        } else if (method.equals(GET_DEFINITION)) {
            if (response != null) {
                currentServiceMap = (DefinitionServiceMap) response;
                loadServiceMap();

                if (currentServiceMap != null) {
                    serviceComboBox.setSelectedItem(currentServiceMap.getMap().keySet().iterator().next());
                }

                parent.setSaveEnabled(true);
            }
        } else if (method.equals(IS_WSDL_CACHED)) {
            if (response != null) {
                if ((Boolean) response) {
                    invokeConnectorService(GENERATE_ENVELOPE, "Generating envelope...", "There was an error generating the envelope.\n\n");
                } else {
                    parent.alertInformation(parent, "The WSDL is no longer cached on the server. Press \"Get Operations\" to fetch the latest WSDL.");
                }
            }
        } else if (method.equals(GENERATE_ENVELOPE)) {
            String generatedEnvelope = (String) response;
            if (generatedEnvelope != null) {
                soapEnvelope.setText(generatedEnvelope);
                parent.setSaveEnabled(true);
            }

            invokeConnectorService(GET_SOAP_ACTION, "Retrieving SOAP action...", "There was an error retrieving the SOAP action.\n\n");
        } else if (method.equals(GET_SOAP_ACTION)) {
            String soapAction = (String) response;
            if (soapAction != null) {
                soapActionField.setText(soapAction);
                parent.setSaveEnabled(true);
                urlFieldChanged();
            }
        }
    }

    private void loadServiceMap() {
        // First reset the service/port/operation
        serviceComboBox.setModel(new DefaultComboBoxModel());
        portComboBox.setModel(new DefaultComboBoxModel());
        locationURIComboBox.setModel(new DefaultComboBoxModel());
        operationComboBox.setModel(new DefaultComboBoxModel(new String[] { WebServiceDispatcherProperties.WEBSERVICE_DEFAULT_DROPDOWN }));

        if (currentServiceMap != null) {
            serviceComboBox.setModel(new DefaultComboBoxModel(currentServiceMap.getMap().keySet().toArray()));
        }
    }

    private boolean isUsingHttps(String url) {
        if (StringUtils.isNotBlank(url)) {
            try {
                URI hostURI = new URI(url);
                String hostScheme = hostURI.getScheme();
                if (hostScheme != null && hostScheme.toLowerCase().equals("https")) {
                    return true;
                }
            } catch (URISyntaxException e) {
                if (url.toLowerCase().startsWith("https")) {
                    return true;
                }
            }
        }

        return false;
    }

    private void urlFieldChanged() {
        decorateConnectorType();
    }

    private boolean isDefaultOperations() {
        return (operationComboBox.getItemCount() == 1 && operationComboBox.getItemAt(0).equals(WebServiceDispatcherProperties.WEBSERVICE_DEFAULT_DROPDOWN));
    }

    private void setAttachments(List<List<String>> attachments) {

        List<String> attachmentIds = attachments.get(0);
        List<String> attachmentContents = attachments.get(1);
        List<String> attachmentTypes = attachments.get(2);

        Object[][] tableData = new Object[attachmentIds.size()][3];

        attachmentsTable = new MirthTable();

        for (int i = 0; i < attachmentIds.size(); i++) {
            tableData[i][ID_COLUMN_NUMBER] = attachmentIds.get(i);
            tableData[i][CONTENT_COLUMN_NUMBER] = attachmentContents.get(i);
            tableData[i][MIME_TYPE_COLUMN_NUMBER] = attachmentTypes.get(i);
        }

        attachmentsTable.setModel(new javax.swing.table.DefaultTableModel(tableData, new String[] {
                ID_COLUMN_NAME, CONTENT_COLUMN_NAME, MIME_TYPE_COLUMN_NAME }) {

            boolean[] canEdit = new boolean[] { true, true, true };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit[columnIndex];
            }
        });

        attachmentsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent evt) {
                if (attachmentsTable.getSelectedModelIndex() != -1) {
                    deleteButton.setEnabled(true);
                } else {
                    deleteButton.setEnabled(false);
                }
            }
        });

        class AttachmentsTableCellEditor extends TextFieldCellEditor {

            boolean checkUnique;

            public AttachmentsTableCellEditor(boolean checkUnique) {
                super();
                this.checkUnique = checkUnique;
            }

            public boolean checkUnique(String value) {
                boolean exists = false;

                for (int i = 0; i < attachmentsTable.getModel().getRowCount(); i++) {
                    if (((String) attachmentsTable.getModel().getValueAt(i, ID_COLUMN_NUMBER)).equalsIgnoreCase(value)) {
                        exists = true;
                    }
                }

                return exists;
            }

            @Override
            public boolean isCellEditable(EventObject evt) {
                boolean editable = super.isCellEditable(evt);

                if (editable) {
                    deleteButton.setEnabled(false);
                }

                return editable;
            }

            @Override
            protected boolean valueChanged(String value) {
                deleteButton.setEnabled(true);

                if (checkUnique && (value.length() == 0 || checkUnique(value))) {
                    return false;
                }

                parent.setSaveEnabled(true);
                return true;
            }
        }

        attachmentsTable.getColumnModel().getColumn(attachmentsTable.getColumnModelIndex(ID_COLUMN_NAME)).setCellEditor(new AttachmentsTableCellEditor(true));
        attachmentsTable.getColumnModel().getColumn(attachmentsTable.getColumnModelIndex(CONTENT_COLUMN_NAME)).setCellEditor(new AttachmentsTableCellEditor(false));
        attachmentsTable.getColumnModel().getColumn(attachmentsTable.getColumnModelIndex(MIME_TYPE_COLUMN_NAME)).setCellEditor(new AttachmentsTableCellEditor(false));
        attachmentsTable.setCustomEditorControls(true);

        attachmentsTable.setSelectionMode(0);
        attachmentsTable.setRowSelectionAllowed(true);
        attachmentsTable.setRowHeight(UIConstants.ROW_HEIGHT);
        attachmentsTable.setDragEnabled(true);

        attachmentsTable.setTransferHandler(new TransferHandler() {

            protected Transferable createTransferable(JComponent c) {
                try {
                    MirthTable table = ((MirthTable) (c));

                    if (table == null) {
                        return null;
                    }

                    int currRow = table.convertRowIndexToModel(table.getSelectedRow());

                    String text = "";
                    if (currRow >= 0 && currRow < table.getModel().getRowCount()) {
                        text = (String) table.getModel().getValueAt(currRow, ID_COLUMN_NUMBER);
                    }

                    text = "<inc:Include href=\"cid:" + text + "\" xmlns:inc=\"http://www.w3.org/2004/08/xop/include\"/>";

                    return new StringSelection(text);
                } catch (ClassCastException cce) {
                    return null;
                }
            }

            public int getSourceActions(JComponent c) {
                return COPY;
            }

            public boolean canImport(JComponent c, DataFlavor[] df) {
                return false;
            }
        });

        attachmentsTable.setOpaque(true);
        attachmentsTable.setSortable(true);

        if (Preferences.userNodeForPackage(Mirth.class).getBoolean("highlightRows", true)) {
            Highlighter highlighter = HighlighterFactory.createAlternateStriping(UIConstants.HIGHLIGHTER_COLOR, UIConstants.BACKGROUND_COLOR);
            attachmentsTable.setHighlighters(highlighter);
        }

        attachmentsPane.setViewportView(attachmentsTable);
        deleteButton.setEnabled(false);
    }

    private List<List<String>> getAttachments() {
        List<List<String>> attachments = new ArrayList<List<String>>();

        ArrayList<String> attachmentIds = new ArrayList<String>();
        ArrayList<String> attachmentContents = new ArrayList<String>();
        ArrayList<String> attachmentTypes = new ArrayList<String>();

        for (int i = 0; i < attachmentsTable.getModel().getRowCount(); i++) {
            if (((String) attachmentsTable.getModel().getValueAt(i, ID_COLUMN_NUMBER)).length() > 0) {
                attachmentIds.add((String) attachmentsTable.getModel().getValueAt(i, ID_COLUMN_NUMBER));
                attachmentContents.add((String) attachmentsTable.getModel().getValueAt(i, CONTENT_COLUMN_NUMBER));
                attachmentTypes.add((String) attachmentsTable.getModel().getValueAt(i, MIME_TYPE_COLUMN_NUMBER));
            }
        }

        attachments.add(attachmentIds);
        attachments.add(attachmentContents);
        attachments.add(attachmentTypes);

        return attachments;
    }

    public void stopCellEditing() {
        if (attachmentsTable.isEditing()) {
            attachmentsTable.getColumnModel().getColumn(attachmentsTable.convertColumnIndexToModel(attachmentsTable.getEditingColumn())).getCellEditor().stopCellEditing();
        }
    }

    /**
     * Get the name that should be used for a new user so that it is unique.
     */
    private String getNewAttachmentId(int size) {
        String temp = "Attachment";

        for (int i = 1; i <= size; i++) {
            boolean exists = false;

            for (int j = 0; j < size - 1; j++) {
                if (((String) attachmentsTable.getModel().getValueAt(j, attachmentsTable.getColumnModelIndex(ID_COLUMN_NAME))).equalsIgnoreCase(temp + i)) {
                    exists = true;
                }
            }

            if (!exists) {
                return temp + i;
            }
        }
        return "";
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        authenticationButtonGroup = new javax.swing.ButtonGroup();
        invocationButtonGroup = new javax.swing.ButtonGroup();
        useMtomButtonGroup = new javax.swing.ButtonGroup();
        wsdlUrlLabel = new javax.swing.JLabel();
        wsdlUrlField = new com.mirth.connect.client.ui.components.MirthIconTextField();
        getOperationsButton = new javax.swing.JButton();
        operationComboBox = new com.mirth.connect.client.ui.components.MirthComboBox();
        jLabel1 = new javax.swing.JLabel();
        serviceLabel = new javax.swing.JLabel();
        soapEnvelope = new com.mirth.connect.client.ui.components.MirthSyntaxTextArea(true,false);
        portLabel = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        generateEnvelope = new javax.swing.JButton();
        attachmentsLabel = new javax.swing.JLabel();
        attachmentsPane = new javax.swing.JScrollPane();
        attachmentsTable = new com.mirth.connect.client.ui.components.MirthTable();
        newButton = new javax.swing.JButton();
        deleteButton = new javax.swing.JButton();
        authenticationLabel = new javax.swing.JLabel();
        authenticationYesRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();
        authenticationNoRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();
        usernameLabel = new javax.swing.JLabel();
        usernameField = new com.mirth.connect.client.ui.components.MirthTextField();
        passwordField = new com.mirth.connect.client.ui.components.MirthPasswordField();
        passwordLabel = new javax.swing.JLabel();
        invocationTypeLabel = new javax.swing.JLabel();
        invocationTwoWayRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();
        invocationOneWayRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();
        useMtomLabel = new javax.swing.JLabel();
        useMtomYesRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();
        useMtomNoRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();
        soapActionField = new com.mirth.connect.client.ui.components.MirthIconTextField();
        soapActionLabel = new javax.swing.JLabel();
        serviceComboBox = new com.mirth.connect.client.ui.components.MirthEditableComboBox();
        portComboBox = new com.mirth.connect.client.ui.components.MirthEditableComboBox();
        locationURILabel = new javax.swing.JLabel();
        locationURIComboBox = new com.mirth.connect.client.ui.components.MirthEditableComboBox();

        setBackground(new java.awt.Color(255, 255, 255));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));

        wsdlUrlLabel.setText("WSDL URL:");

        wsdlUrlField.setToolTipText("Enter the full URL to the WSDL describing the web service method to be called, and then click the Get Operations button.");

        getOperationsButton.setText("Get Operations");
        getOperationsButton.setToolTipText("<html>Clicking this button fetches the WSDL from the specified URL<br> and parses it to obtain a description of the data types and methods used by the web service to be called.<br>It replaces the values of all of the controls below by values taken from the WSDL.</html>");
        getOperationsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                getOperationsButtonActionPerformed(evt);
            }
        });

        operationComboBox.setToolTipText("<html>Select the web service operation to be called from this list.<br>This is only used for generating the envelope</html>");

        jLabel1.setText("Operation:");

        serviceLabel.setText("Service:");

        soapEnvelope.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        portLabel.setText("Port / Endpoint:");

        jLabel4.setText("SOAP Envelope:");

        generateEnvelope.setText("Generate Envelope");
        generateEnvelope.setToolTipText("<html>Clicking this button regenerates the contents of the SOAP Envelope control based on the<br>schema defined in the WSDL, discarding any changes that may have been made.<br>It also populates the SOAP Action field, if available.</html>");
        generateEnvelope.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generateEnvelopeActionPerformed(evt);
            }
        });

        attachmentsLabel.setText("Attachments:");

        attachmentsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Content", "MIME Type"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }
        });
        attachmentsTable.setToolTipText("<html>Attachments should be added with an ID, Base64 encoded content,<br>and a valid MIME type. Once an attachment is added<br>the row can be dropped into an argument in the envelope.</html>");
        attachmentsPane.setViewportView(attachmentsTable);

        newButton.setText("New");
        newButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newButtonActionPerformed(evt);
            }
        });

        deleteButton.setText("Delete");
        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteButtonActionPerformed(evt);
            }
        });

        authenticationLabel.setText("Authentication:");

        authenticationYesRadio.setBackground(new java.awt.Color(255, 255, 255));
        authenticationButtonGroup.add(authenticationYesRadio);
        authenticationYesRadio.setText("Yes");
        authenticationYesRadio.setToolTipText("<html>Turning on authentication uses a username and password to get the WSDL, if necessary,<br>and uses the username and password binding provider properties when calling the web service.</html>");
        authenticationYesRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));
        authenticationYesRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                authenticationYesRadioActionPerformed(evt);
            }
        });

        authenticationNoRadio.setBackground(new java.awt.Color(255, 255, 255));
        authenticationButtonGroup.add(authenticationNoRadio);
        authenticationNoRadio.setText("No");
        authenticationNoRadio.setToolTipText("<html>Turning on authentication uses a username and password to get the WSDL, if necessary,<br>and uses the username and password binding provider properties when calling the web service.</html>");
        authenticationNoRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));
        authenticationNoRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                authenticationNoRadioActionPerformed(evt);
            }
        });

        usernameLabel.setText("Username:");

        usernameField.setToolTipText("The username used to get the WSDL and call the web service.");

        passwordField.setToolTipText("The password used to get the WSDL and call the web service.");

        passwordLabel.setText("Password:");

        invocationTypeLabel.setText("Invocation Type:");

        invocationTwoWayRadio.setBackground(new java.awt.Color(255, 255, 255));
        invocationButtonGroup.add(invocationTwoWayRadio);
        invocationTwoWayRadio.setText("Two-Way");
        invocationTwoWayRadio.setToolTipText("<html>Invoke the operation using the standard two-way invocation function.<br>This will wait for some response or acknowledgement to be returned.</html>");
        invocationTwoWayRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));

        invocationOneWayRadio.setBackground(new java.awt.Color(255, 255, 255));
        invocationButtonGroup.add(invocationOneWayRadio);
        invocationOneWayRadio.setText("One-Way");
        invocationOneWayRadio.setToolTipText("<html>Invoke the operation using the one-way invocation function.<br>This will not wait for any response, and should only be used if the<br>operation is defined as a one-way operation.</html>");
        invocationOneWayRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));

        useMtomLabel.setText("Use MTOM:");

        useMtomYesRadio.setBackground(new java.awt.Color(255, 255, 255));
        useMtomButtonGroup.add(useMtomYesRadio);
        useMtomYesRadio.setText("Yes");
        useMtomYesRadio.setToolTipText("<html>Enables MTOM on the SOAP Binding. If MTOM is enabled,<br>attachments can be added to the table below and dropped into the envelope.</html>");
        useMtomYesRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));
        useMtomYesRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useMtomYesRadioActionPerformed(evt);
            }
        });

        useMtomNoRadio.setBackground(new java.awt.Color(255, 255, 255));
        useMtomButtonGroup.add(useMtomNoRadio);
        useMtomNoRadio.setSelected(true);
        useMtomNoRadio.setText("No");
        useMtomNoRadio.setToolTipText("<html>Does not enable MTOM on the SOAP Binding. If MTOM is enabled,<br>attachments can be added to the table below and dropped into the envelope.</html>");
        useMtomNoRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));
        useMtomNoRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useMtomNoRadioActionPerformed(evt);
            }
        });

        soapActionField.setBackground(new java.awt.Color(222, 222, 222));
        soapActionField.setToolTipText("<html>The SOAPAction HTTP request header field can be used to indicate the intent of the SOAP HTTP request.<br>This field is optional for most web services, and will be auto-populated when you select an operation.</html>");

        soapActionLabel.setText("SOAP Action:");

        serviceComboBox.setBackground(new java.awt.Color(222, 222, 222));
        serviceComboBox.setToolTipText("<html>The service name for the WSDL defined above. This field<br/>is filled in automatically when the Get Operations button<br/>is clicked and does not usually need to be changed,<br/>unless multiple services are defined in the WSDL.</html>");
        serviceComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                serviceComboBoxActionPerformed(evt);
            }
        });

        portComboBox.setBackground(new java.awt.Color(222, 222, 222));
        portComboBox.setToolTipText("<html>The port / endpoint name for the service defined above.<br/>This field is filled in automatically when the Get Operations<br/>button is clicked and does not usually need to be changed,<br/>unless multiple endpoints are defined for the currently<br/>selected service in the WSDL.</html>");
        portComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                portComboBoxActionPerformed(evt);
            }
        });

        locationURILabel.setText("Location URI:");

        locationURIComboBox.setBackground(new java.awt.Color(222, 222, 222));
        locationURIComboBox.setToolTipText("<html>The dispatch location for the port / endpoint defined above.<br/>This field is filled in automatically when the Get Operations<br/>button is clicked and does not usually need to be changed.</html>");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(locationURILabel)
                    .addComponent(authenticationLabel)
                    .addComponent(soapActionLabel)
                    .addComponent(attachmentsLabel)
                    .addComponent(jLabel4)
                    .addComponent(useMtomLabel)
                    .addComponent(portLabel)
                    .addComponent(usernameLabel)
                    .addComponent(jLabel1)
                    .addComponent(serviceLabel)
                    .addComponent(passwordLabel)
                    .addComponent(invocationTypeLabel)
                    .addComponent(wsdlUrlLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(wsdlUrlField, javax.swing.GroupLayout.DEFAULT_SIZE, 281, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(getOperationsButton))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(attachmentsPane, javax.swing.GroupLayout.DEFAULT_SIZE, 335, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(newButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(deleteButton)))
                    .addComponent(soapEnvelope, javax.swing.GroupLayout.DEFAULT_SIZE, 425, Short.MAX_VALUE)
                    .addComponent(soapActionField, javax.swing.GroupLayout.DEFAULT_SIZE, 425, Short.MAX_VALUE)
                    .addComponent(serviceComboBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(portComboBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(locationURIComboBox, javax.swing.GroupLayout.DEFAULT_SIZE, 425, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(usernameField, javax.swing.GroupLayout.PREFERRED_SIZE, 125, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(passwordField, javax.swing.GroupLayout.PREFERRED_SIZE, 125, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(authenticationYesRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(authenticationNoRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(invocationTwoWayRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(invocationOneWayRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(operationComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 186, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(generateEnvelope))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(useMtomYesRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(useMtomNoRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(wsdlUrlLabel)
                    .addComponent(getOperationsButton)
                    .addComponent(wsdlUrlField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(serviceLabel)
                    .addComponent(serviceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(portLabel)
                    .addComponent(portComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(locationURILabel)
                    .addComponent(locationURIComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(authenticationLabel)
                    .addComponent(authenticationYesRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(authenticationNoRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(usernameLabel)
                    .addComponent(usernameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(passwordLabel)
                    .addComponent(passwordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(invocationTypeLabel)
                    .addComponent(invocationTwoWayRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(invocationOneWayRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(operationComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(generateEnvelope))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(soapActionLabel)
                    .addComponent(soapActionField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel4)
                    .addComponent(soapEnvelope, javax.swing.GroupLayout.DEFAULT_SIZE, 35, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(useMtomLabel)
                    .addComponent(useMtomYesRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(useMtomNoRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(newButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(deleteButton))
                    .addComponent(attachmentsLabel)
                    .addComponent(attachmentsPane, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void newButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newButtonActionPerformed
        stopCellEditing();
        ((DefaultTableModel) attachmentsTable.getModel()).addRow(new Object[] {
                getNewAttachmentId(attachmentsTable.getModel().getRowCount() + 1), "" });
        int newViewIndex = attachmentsTable.convertRowIndexToView(attachmentsTable.getModel().getRowCount() - 1);
        attachmentsTable.setRowSelectionInterval(newViewIndex, newViewIndex);

        attachmentsPane.getViewport().setViewPosition(new Point(0, attachmentsTable.getRowHeight() * attachmentsTable.getModel().getRowCount()));
        parent.setSaveEnabled(true);
    }//GEN-LAST:event_newButtonActionPerformed

    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
        stopCellEditing();

        int selectedModelIndex = attachmentsTable.getSelectedModelIndex();
        int newViewIndex = attachmentsTable.convertRowIndexToView(selectedModelIndex);
        if (newViewIndex == (attachmentsTable.getModel().getRowCount() - 1)) {
            newViewIndex--;
        }

        ((DefaultTableModel) attachmentsTable.getModel()).removeRow(selectedModelIndex);

        parent.setSaveEnabled(true);

        if (attachmentsTable.getModel().getRowCount() == 0) {
            attachmentsTable.clearSelection();
            deleteButton.setEnabled(false);
        } else {
            attachmentsTable.setRowSelectionInterval(newViewIndex, newViewIndex);
        }

        parent.setSaveEnabled(true);
    }//GEN-LAST:event_deleteButtonActionPerformed

    private void getOperationsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_getOperationsButtonActionPerformed
        if (StringUtils.isNotBlank((String) serviceComboBox.getSelectedItem()) || StringUtils.isNotBlank((String) portComboBox.getSelectedItem()) || !isDefaultOperations()) {
            if (!parent.alertOkCancel(parent, "This will replace your current service, port, and operation list. Press OK to continue.")) {
                return;
            }
        }

        // Reset all of the fields
        currentServiceMap = null;
        serviceComboBox.setModel(new DefaultComboBoxModel());
        portComboBox.setModel(new DefaultComboBoxModel());
        operationComboBox.setModel(new DefaultComboBoxModel(new String[] { WebServiceDispatcherProperties.WEBSERVICE_DEFAULT_DROPDOWN }));
        operationComboBox.setSelectedIndex(0);
        generateEnvelope.setEnabled(false);

        invokeConnectorService(CACHE_WSDL_FROM_URL, "Getting operations...", "Error caching WSDL. Please check the WSDL URL and authentication settings.\n\n");
    }//GEN-LAST:event_getOperationsButtonActionPerformed

    private void authenticationYesRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_authenticationYesRadioActionPerformed
        usernameLabel.setEnabled(true);
        usernameField.setEnabled(true);

        passwordLabel.setEnabled(true);
        passwordField.setEnabled(true);
    }//GEN-LAST:event_authenticationYesRadioActionPerformed

    private void authenticationNoRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_authenticationNoRadioActionPerformed
        usernameLabel.setEnabled(false);
        usernameField.setEnabled(false);
        usernameField.setText("");

        passwordLabel.setEnabled(false);
        passwordField.setEnabled(false);
        passwordField.setText("");
    }//GEN-LAST:event_authenticationNoRadioActionPerformed

    private void useMtomYesRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useMtomYesRadioActionPerformed
        attachmentsLabel.setEnabled(true);
        attachmentsPane.setEnabled(true);
        attachmentsTable.setEnabled(true);
        newButton.setEnabled(true);

        attachmentsTable.setRowSelectionAllowed(true);
        if (attachmentsTable.getModel().getRowCount() > 0) {
            attachmentsTable.setRowSelectionInterval(0, 0);
            deleteButton.setEnabled(true);
        }

    }//GEN-LAST:event_useMtomYesRadioActionPerformed

    private void useMtomNoRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useMtomNoRadioActionPerformed
        attachmentsLabel.setEnabled(false);
        attachmentsPane.setEnabled(false);
        attachmentsTable.setEnabled(false);
        newButton.setEnabled(false);
        deleteButton.setEnabled(false);

        stopCellEditing();
        attachmentsTable.setRowSelectionAllowed(false);
        attachmentsTable.clearSelection();
    }//GEN-LAST:event_useMtomNoRadioActionPerformed

    private void generateEnvelopeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateEnvelopeActionPerformed
        if (soapEnvelope.getText().length() > 0 || soapActionField.getText().length() > 0) {
            if (!parent.alertOkCancel(parent, "This will replace your current SOAP envelope and SOAP action. Press OK to continue.")) {
                return;
            }
        }

        invokeConnectorService(IS_WSDL_CACHED, "Checking if WSDL is cached...", "Error checking if the wsdl is cached: ");
    }//GEN-LAST:event_generateEnvelopeActionPerformed

    private void serviceComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serviceComboBoxActionPerformed
        String selectedPort = (String) portComboBox.getSelectedItem();

        if (currentServiceMap != null) {
            DefinitionPortMap portMap = currentServiceMap.getMap().get((String) serviceComboBox.getSelectedItem());

            if (portMap != null) {
                portComboBox.setModel(new DefaultComboBoxModel(portMap.getMap().keySet().toArray()));
            } else {
                portComboBox.setModel(new DefaultComboBoxModel());
            }
        }

        if (StringUtils.isNotBlank(selectedPort)) {
            portComboBox.setSelectedItem(selectedPort);
        } else if (portComboBox.getModel().getSize() > 0) {
            portComboBox.setSelectedIndex(0);
        }
    }//GEN-LAST:event_serviceComboBoxActionPerformed

    private void portComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_portComboBoxActionPerformed
        if (currentServiceMap != null) {
            DefinitionPortMap portMap = currentServiceMap.getMap().get((String) serviceComboBox.getSelectedItem());

            if (portMap != null) {
                PortInformation portInformation = portMap.getMap().get((String) portComboBox.getSelectedItem());
                String selectedLocationURI = (String) locationURIComboBox.getSelectedItem();

                if (portInformation != null) {
                    List<String> operationList = portInformation.getOperations();

                    if (CollectionUtils.isNotEmpty(operationList)) {
                        String selectedOperation = (String) operationComboBox.getSelectedItem();
                        operationComboBox.setModel(new DefaultComboBoxModel(operationList.toArray()));

                        if (operationList.contains(selectedOperation)) {
                            operationComboBox.setSelectedItem(selectedOperation);
                        } else {
                            operationComboBox.setSelectedIndex(0);
                        }

                        generateEnvelope.setEnabled(!isDefaultOperations());
                    } else {
                        operationComboBox.setModel(new DefaultComboBoxModel());
                    }

                    if (StringUtils.isNotBlank(portInformation.getLocationURI())) {
                        locationURIComboBox.setModel(new DefaultComboBoxModel(new String[] { portInformation.getLocationURI() }));
                    } else {
                        locationURIComboBox.setModel(new DefaultComboBoxModel());
                    }
                } else {
                    locationURIComboBox.setModel(new DefaultComboBoxModel());
                    operationComboBox.setModel(new DefaultComboBoxModel());
                }

                if (StringUtils.isNotBlank(selectedLocationURI)) {
                    locationURIComboBox.setSelectedItem(selectedLocationURI);
                }
            }
        }
    }//GEN-LAST:event_portComboBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel attachmentsLabel;
    private javax.swing.JScrollPane attachmentsPane;
    private com.mirth.connect.client.ui.components.MirthTable attachmentsTable;
    private javax.swing.ButtonGroup authenticationButtonGroup;
    private javax.swing.JLabel authenticationLabel;
    private com.mirth.connect.client.ui.components.MirthRadioButton authenticationNoRadio;
    private com.mirth.connect.client.ui.components.MirthRadioButton authenticationYesRadio;
    private javax.swing.JButton deleteButton;
    private javax.swing.JButton generateEnvelope;
    private javax.swing.JButton getOperationsButton;
    private javax.swing.ButtonGroup invocationButtonGroup;
    private com.mirth.connect.client.ui.components.MirthRadioButton invocationOneWayRadio;
    private com.mirth.connect.client.ui.components.MirthRadioButton invocationTwoWayRadio;
    private javax.swing.JLabel invocationTypeLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel4;
    private com.mirth.connect.client.ui.components.MirthEditableComboBox locationURIComboBox;
    private javax.swing.JLabel locationURILabel;
    private javax.swing.JButton newButton;
    private com.mirth.connect.client.ui.components.MirthComboBox operationComboBox;
    private com.mirth.connect.client.ui.components.MirthPasswordField passwordField;
    private javax.swing.JLabel passwordLabel;
    private com.mirth.connect.client.ui.components.MirthEditableComboBox portComboBox;
    private javax.swing.JLabel portLabel;
    private com.mirth.connect.client.ui.components.MirthEditableComboBox serviceComboBox;
    private javax.swing.JLabel serviceLabel;
    private com.mirth.connect.client.ui.components.MirthIconTextField soapActionField;
    private javax.swing.JLabel soapActionLabel;
    private com.mirth.connect.client.ui.components.MirthSyntaxTextArea soapEnvelope;
    private javax.swing.ButtonGroup useMtomButtonGroup;
    private javax.swing.JLabel useMtomLabel;
    private com.mirth.connect.client.ui.components.MirthRadioButton useMtomNoRadio;
    private com.mirth.connect.client.ui.components.MirthRadioButton useMtomYesRadio;
    private com.mirth.connect.client.ui.components.MirthTextField usernameField;
    private javax.swing.JLabel usernameLabel;
    private com.mirth.connect.client.ui.components.MirthIconTextField wsdlUrlField;
    private javax.swing.JLabel wsdlUrlLabel;
    // End of variables declaration//GEN-END:variables
}
