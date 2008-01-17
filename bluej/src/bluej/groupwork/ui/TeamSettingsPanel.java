package bluej.groupwork.ui;

import java.awt.FocusTraversalPolicy;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import bluej.BlueJTheme;
import bluej.Config;
import bluej.groupwork.CvsProvider;
import bluej.groupwork.SubversionProvider;
import bluej.groupwork.TeamSettingsController;
import bluej.groupwork.TeamworkProvider;
import bluej.groupwork.actions.ValidateConnectionAction;


/**
 * A panel for team settings.
 * 
 * @author fisker
 * @version $Id: TeamSettingsPanel.java 5456 2008-01-17 05:06:33Z davmac $
 */
public class TeamSettingsPanel extends JPanel 
{
    private static final int fieldsize = 20;
    private final String pserverLabel = Config.getString("team.settings.pserver");
    private final String extLabel = Config.getString("team.settings.ext");
    private final String[] conTypes = {extLabel, pserverLabel};
    private TeamSettingsController teamSettingsController;
    private TeamSettingsDialog teamSettingsDialog;
    
    private static ArrayList teamProviders;
    static {
        teamProviders = new ArrayList(2);
        teamProviders.add(new CvsProvider());
        teamProviders.add(new SubversionProvider());
    }
    
    JTextField userField;
    JPasswordField passwordField;
    JTextField groupField;
    JTextField prefixField;
    JComboBox serverTypeComboBox;
    JTextField serverField;
    JComboBox protocolComboBox;
    JButton validateButton;
    JCheckBox useAsDefault;
    
    private JLabel serverTypeLabel;
    private JLabel groupLabel;
    private JLabel prefixLabel;
    private JLabel serverLabel;
    private JLabel protocolLabel;
    
    private int selectedServerType = -1;
    private boolean okEnabled = true;
    
    public TeamSettingsPanel(TeamSettingsController teamSettingsController, TeamSettingsDialog dialog)
    {
        this.teamSettingsController = teamSettingsController;
        this.teamSettingsDialog = dialog;
        
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BlueJTheme.generalBorder);
        add(Box.createVerticalGlue());
        
        add(makePersonalPanel());
        add(Box.createVerticalStrut(BlueJTheme.generalSpacingWidth));
        add(makeLocationPanel());
        add(Box.createVerticalStrut(BlueJTheme.generalSpacingWidth));
        useAsDefault = new JCheckBox(Config.getString("team.settings.rememberSettings"));
        add(useAsDefault);
        add(Box.createVerticalStrut(BlueJTheme.generalSpacingWidth));
        validateButton = new JButton(new ValidateConnectionAction(
                Config.getString("team.settings.checkConnection"), this, dialog));
        add(validateButton);
        
        DocumentListener changeListener = new DocumentListener() {
            public void changedUpdate(DocumentEvent e)
            {
                checkOkEnabled();
            }
            
            public void insertUpdate(DocumentEvent e)
            {
                checkOkEnabled();
            }
            
            public void removeUpdate(DocumentEvent e)
            {
                checkOkEnabled();
            }
        };
        
        userField.getDocument().addDocumentListener(changeListener);
        serverField.getDocument().addDocumentListener(changeListener);
        
        //add(new JSeparator());
        add(Box.createVerticalGlue());
        setupContent();
        checkOkEnabled();
        if (!teamSettingsController.hasProject()){
            useAsDefault.setSelected(true);
            // useAsDefault.setEnabled(false);
        }
    }
    
    /**
     * Get the focus traversal policy for the parent window. The new policy
     * overrides some functionality and delegates everything else back to
     * the original policy (the delegate).
     * 
     * @param delegate  The original traversal policy
     */
    public FocusTraversalPolicy getTraversalPolicy(FocusTraversalPolicy delegate)
    {
        if (getUser().length() != 0) {
            return new TeamPanelFocusPolicy(passwordField, delegate);
        }
        else {
            return delegate;
        }
    }
    
    /**
     * Disable the fields used to specify the repository:
     * group, prefix, server and protocol
     */
    public void disableRepositorySettings()
    {
        serverTypeComboBox.setEnabled(false);
        groupField.setEnabled(false);
        prefixField.setEnabled(false);
        serverField.setEnabled(false);
        protocolComboBox.setEnabled(false);
        
        // useAsDefault.setEnabled(false);
        
        serverTypeLabel.setEnabled(false);
        groupLabel.setEnabled(false);
        prefixLabel.setEnabled(false);
        serverLabel.setEnabled(false);
        protocolLabel.setEnabled(false);
    }
    
    private JPanel makePersonalPanel()
    {
        JPanel authentificationPanel = new JPanel();
        {
            authentificationPanel.setLayout(new MiksGridLayout(3,2,10,5));
            String docTitle = Config.getString("team.settings.personal");
            authentificationPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(docTitle),
                    BlueJTheme.generalBorder));
            authentificationPanel.setAlignmentX(LEFT_ALIGNMENT);
            
            JLabel userLabel = new JLabel(Config.getString("team.settings.user"));
            userField = new JTextField(fieldsize);
            JLabel passwordLabel = new JLabel(Config.getString("team.settings.password"));
            passwordField = new JPasswordField(fieldsize);
            groupLabel = new JLabel(Config.getString("team.settings.group"));
            groupField = new JTextField(fieldsize);
            
            userLabel.setMaximumSize(userLabel.getMinimumSize());
            userField.setMaximumSize(userField.getMinimumSize());
            passwordLabel.setMaximumSize(passwordLabel.getMinimumSize());
            passwordField.setMaximumSize(passwordField.getMinimumSize());
            groupLabel.setMaximumSize(groupLabel.getMinimumSize());
            groupField.setMaximumSize(groupField.getMinimumSize());
                        
            authentificationPanel.add(userLabel);
            authentificationPanel.add(userField);
            authentificationPanel.add(passwordLabel);
            authentificationPanel.add(passwordField);
            authentificationPanel.add(groupLabel);
            authentificationPanel.add(groupField);
            
        }
        return authentificationPanel;
    }
    
    private JPanel makeLocationPanel()
    {
        JPanel locationPanel = new JPanel(new MiksGridLayout(4,2,10,5));
        {
            String docTitle2 = Config.getString("team.settings.location");
            locationPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(docTitle2),
                    BlueJTheme.generalBorder));
            locationPanel.setAlignmentX(LEFT_ALIGNMENT);
            
            serverTypeLabel = new JLabel(Config.getString("team.settings.serverType"));
            serverTypeComboBox = new JComboBox();
            for (Iterator i = teamProviders.iterator(); i.hasNext(); ) {
                TeamworkProvider provider = (TeamworkProvider) i.next();
                serverTypeComboBox.addItem(provider.getProviderName());
            }
            
            serverTypeComboBox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    fillProtocolSelections();
                    setProviderSettings();
                }
            });
            
            serverLabel = new JLabel(Config.getString("team.settings.server"));
            serverField = new JTextField(fieldsize);
            
            prefixLabel = new JLabel(Config.getString("team.settings.prefix"));
            prefixField = new JTextField(fieldsize);
            
            protocolLabel = new JLabel(Config.getString("team.settings.protocol"));
            protocolComboBox = new JComboBox();
            protocolComboBox.setEditable(false);
            fillProtocolSelections();
            
            prefixLabel.setMaximumSize(prefixLabel.getMinimumSize());
            prefixField.setMaximumSize(prefixField.getMinimumSize());
            serverLabel.setMaximumSize(serverLabel.getMinimumSize());
            serverField.setMaximumSize(serverField.getMinimumSize());
            serverTypeLabel.setMaximumSize(serverTypeLabel.getMinimumSize());
            serverTypeComboBox.setMaximumSize(serverTypeComboBox.getMinimumSize());
            
            locationPanel.add(serverTypeLabel);
            locationPanel.add(serverTypeComboBox);
            locationPanel.add(serverLabel);
            locationPanel.add(serverField);
            locationPanel.add(prefixLabel);
            locationPanel.add(prefixField);
            locationPanel.add(protocolLabel);
            locationPanel.add(protocolComboBox);
        }
        return locationPanel;
    }
    
    /**
     * Empty the protocol selection box, then fill it with the available protocols
     * from the currently selected teamwork provider.
     */
    private void fillProtocolSelections()
    {
        int selected = serverTypeComboBox.getSelectedIndex();
        if (selected != selectedServerType) {
            selectedServerType = selected;
            protocolComboBox.removeAllItems();
            TeamworkProvider provider = (TeamworkProvider) teamProviders.get(selected);
            String [] protocols = provider.getProtocols();
            for (int i = 0; i < protocols.length; i++) {
                protocolComboBox.addItem(protocols[i]);
            }
        }
    }
        
    private void setupContent()
    {
        String user = teamSettingsController.getPropString("bluej.teamsettings.user");
        if (user != null){
            setUser(user);
        }
        String password = teamSettingsController.getPasswordString();
        if (password != null){
            setPassword(password);
        }
        String group = teamSettingsController.getPropString("bluej.teamsettings.groupname");
        if(group != null) {
            setGroup(group);
        }
        String useAsDefault = teamSettingsController.getPropString("bluej.teamsettings.useAsDefault");
        if (useAsDefault != null) {
            setUseAsDefault(Boolean.getBoolean(useAsDefault));
        }
        setProviderSettings();
    }
    
    /**
     * Set settings to provider-specific values (repository prefix, server, protocol).
     * The values are remembered on a per-provider basis; this sets the fields to show
     * the remembered values for the selected provider. 
     */
    private void setProviderSettings()
    {
        String keyBase = "bluej.teamsettings."
            + getSelectedProvider().getProviderName().toLowerCase() + "."; 
        
        String prefix = teamSettingsController.getPropString(keyBase + "repositoryPrefix");
        if (prefix != null) {
            setPrefix(prefix);
        }
        String server = teamSettingsController.getPropString(keyBase + "server");
        if (server != null) {
            setServer(server);
        }
        String protocol = teamSettingsController.getPropString(keyBase + "protocol");
        if (protocol != null){
            setProtocol(protocol);
        }
    }
    
    /**
     * Check whether the "ok" button should be enabled or disabled according
     * to whether required fields have been provided.
     */
    private void checkOkEnabled()
    {
        boolean newOkEnabled = userField.getText().length() != 0;
        newOkEnabled &= serverField.getText().length() != 0;
        if (newOkEnabled != okEnabled) {
            okEnabled = newOkEnabled;
            teamSettingsDialog.setOkButtonEnabled(okEnabled);
        }
    }
    
    private void setUser(String user)
    {
        userField.setText(user);
    }
    
    private void setPassword(String password)
    {
        passwordField.setText(password);
    }
    
    private void setGroup(String group)
    {
        groupField.setText(group);
    }
    
    private void setPrefix(String prefix)
    {
        prefixField.setText(prefix);
    }
    
    private void setServer(String server)
    {
        serverField.setText(server);
    }
    
    /**
     * Set the protocol to that identified by the given protocol key.
     */
    private void setProtocol(String protocolKey)
    {
        String protocolLabel = getSelectedProvider().getProtocolLabel(protocolKey);
        protocolComboBox.setSelectedItem(protocolLabel);
    }
    
    private void setUseAsDefault(boolean use)
    {
        useAsDefault.setSelected(use);
    }
    
    public TeamworkProvider getSelectedProvider()
    {
        int selected = serverTypeComboBox.getSelectedIndex();
        TeamworkProvider provider = (TeamworkProvider) teamProviders.get(selected);
        return provider;
    }
    
    public String getUser()
    {
        return userField.getText();
    }
    
    public String getPassword()
    {
        return new String(passwordField.getPassword());
    }
    
    public String getGroup()
    {
        return groupField.getText();
    }
    
    public String getPrefix()
    {
        return prefixField.getText();
    }
    
    public String getServer()
    {
        return serverField.getText();
    }
    
    public String getProtocol()
    {
        return (String) protocolComboBox.getSelectedItem();
    }
    
    public String getProtocolKey()
    {
        int protocol = protocolComboBox.getSelectedIndex();
        return getSelectedProvider().getProtocolKey(protocol);
    }
    
    public boolean getUseAsDefault()
    {
        return useAsDefault.isSelected();
    }
    
}
