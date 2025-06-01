package ui;

import client.ChatClient;
import model.Group;
import ui.components.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ChatFrame extends JFrame {
    private ChatClient client;

    private JTabbedPane chatTabs;
    private JTextArea messageInput;
    private JButton sendButton;
    private JButton fileButton;
    private JTabbedPane sidePanel;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private DefaultListModel<String> groupListModel;
    private JList<String> groupList;
    private FileTransferPanel fileTransferPanel;
    private Map<String, String> tabStatus;

    private Map<String, JPanel> chatPanels;
    private Map<String, JPanel> groupChatPanels = new HashMap<>();

    private SoundPlayer soundPlayer = new SoundPlayer();
    private OutgoingCallDialog outgoingCallDialog;

    public ChatFrame(ChatClient client) {
        this.client = client;

        this.chatPanels = new HashMap<>();
        this.groupChatPanels = new HashMap<>();

        this.tabStatus = new HashMap<>();

        initGUI();
    }

    private void initGUI() {
        setTitle("Chat App - " + client.getCurrentUser().getUsername());
        setSize(900, 600);
        setMinimumSize(new Dimension(800, 500));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        // Main panel
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(650);
        mainSplitPane.setResizeWeight(1.0);

        JPanel leftPanel = new JPanel(new BorderLayout());

        chatTabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        messageInput = new JTextArea(3, 20);
        messageInput.setLineWrap(true);
        messageInput.setWrapStyleWord(true);

        JScrollPane messageScrollPane = new JScrollPane(messageInput);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));

        sendButton = new JButton("Gửi");

        fileButton = new JButton("Gửi File");

        buttonPanel.add(sendButton);
        buttonPanel.add(fileButton);

        inputPanel.add(messageScrollPane, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        leftPanel.add(chatTabs, BorderLayout.CENTER);
        leftPanel.add(inputPanel, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout());

        sidePanel = new JTabbedPane();

        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane userScrollPane = new JScrollPane(userList);
        usersPanel.add(new JLabel("Người dùng online:"), BorderLayout.NORTH);
        usersPanel.add(userScrollPane, BorderLayout.CENTER);

        JPanel groupsPanel = new JPanel(new BorderLayout());
        groupsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        groupListModel = new DefaultListModel<>();
        groupList = new JList<>(groupListModel);
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane groupScrollPane = new JScrollPane(groupList);

        JButton createGroupButton = new JButton("Tạo nhóm");

        groupsPanel.add(new JLabel("Nhóm chat:"), BorderLayout.NORTH);
        groupsPanel.add(groupScrollPane, BorderLayout.CENTER);
        groupsPanel.add(createGroupButton, BorderLayout.SOUTH);

        fileTransferPanel = new FileTransferPanel(client);

        sidePanel.addTab("Online", usersPanel);
        sidePanel.addTab("Nhóm", groupsPanel);
        sidePanel.addTab("File", fileTransferPanel);

        rightPanel.add(sidePanel, BorderLayout.CENTER);

        mainSplitPane.setLeftComponent(leftPanel);
        mainSplitPane.setRightComponent(rightPanel);

        add(mainSplitPane);

        SwingUtilities.invokeLater(() -> {
            client.getGroups();
        });

        createGlobalChatTab();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int option = JOptionPane.showConfirmDialog(
                        ChatFrame.this,
                        "Bạn có chắc muốn thoát không?",
                        "Xác nhận thoát",
                        JOptionPane.YES_NO_OPTION
                );

                if (option == JOptionPane.YES_OPTION) {
                    dispose();
                    client.shutdown();
                    System.exit(0);
                }
            }
        });

        fileButton.addActionListener(e -> {
            sendFile();
        });

        messageInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Ctrl+Enter để gửi tin nhắn
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                    e.consume();
                }
            }
        });

        chatTabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (chatTabs.getSelectedIndex() != -1) {
                    String tabName = chatTabs.getTitleAt(chatTabs.getSelectedIndex());
                    resetTabHighlight(tabName);

                    if (tabName.equals("Global")) {
                        messageInput.setEnabled(false);
                        sendButton.setEnabled(false);
                        fileButton.setEnabled(false);
                    } else {
                        messageInput.setEnabled(true);
                        sendButton.setEnabled(true);
                        fileButton.setEnabled(true);
                    }
                }
            }
        });

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (e.getClickCount() == 2) {
                    String selectedUser = userList.getSelectedValue();
                    if (selectedUser != null && !selectedUser.equals(client.getCurrentUser().getUsername())) {
                        createOrShowPrivateChat(selectedUser);
                    }
                }
            }
        });

        groupList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selectedGroup = groupList.getSelectedValue();
                    if (selectedGroup != null) {
                        createOrShowGroupChat(selectedGroup);
                    }
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    // Menu ngữ cảnh cho nhóm
                    int index = groupList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        groupList.setSelectedIndex(index);
                        String selectedGroup = groupList.getSelectedValue();

                        JPopupMenu popupMenu = new JPopupMenu();

                        // Mở chat
                        JMenuItem openChatItem = new JMenuItem("Mở chat");
                        openChatItem.addActionListener(event -> {
                            createOrShowGroupChat(selectedGroup);
                        });

                        // Thêm thành viên
                        JMenuItem addMemberItem = new JMenuItem("Thêm thành viên");
                        addMemberItem.addActionListener(event -> {
                            showAddMemberDialog(selectedGroup);
                        });

                        // Rời nhóm
                        JMenuItem leaveGroupItem = new JMenuItem("Rời nhóm");
                        leaveGroupItem.addActionListener(event -> {
                            int confirm = JOptionPane.showConfirmDialog(
                                    ChatFrame.this,
                                    "Bạn có chắc muốn rời khỏi nhóm " + selectedGroup + "?",
                                    "Xác nhận rời nhóm",
                                    JOptionPane.YES_NO_OPTION);

                            if (confirm == JOptionPane.YES_OPTION) {
                                client.leaveGroup(selectedGroup);
                            }
                        });

                        popupMenu.add(openChatItem);
                        popupMenu.add(addMemberItem);
                        popupMenu.addSeparator();
                        popupMenu.add(leaveGroupItem);

                        popupMenu.show(groupList, e.getX(), e.getY());
                    }
                }
            }
        });

        chatTabs.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int tabIndex = chatTabs.indexAtLocation(e.getX(), e.getY());
                    if (tabIndex >= 0) {
                        String tabTitle = chatTabs.getTitleAt(tabIndex);

                        JPopupMenu popupMenu = new JPopupMenu();

                        // Nếu là tab nhóm
                        if (tabTitle.endsWith(" (G)")) {
                            String groupName = tabTitle.substring(0, tabTitle.length() - 4);
                            Group group = client.getGroup(groupName);

                            if (group != null) {
                                // Quản lý thành viên
                                JMenuItem membersItem = new JMenuItem("Quản lý thành viên");
                                membersItem.addActionListener(event -> {
                                    showMemberDialog(groupName);
                                });
                                popupMenu.add(membersItem);

                                // Rời nhóm
                                JMenuItem leaveItem = new JMenuItem("Rời nhóm");
                                leaveItem.addActionListener(event -> {
                                    int confirm = JOptionPane.showConfirmDialog(
                                            ChatFrame.this,
                                            "Bạn có chắc muốn rời khỏi nhóm " + groupName + "?",
                                            "Xác nhận rời nhóm",
                                            JOptionPane.YES_NO_OPTION);

                                    if (confirm == JOptionPane.YES_OPTION) {
                                        client.leaveGroup(groupName);
                                    }
                                });
                                popupMenu.add(leaveItem);
                                popupMenu.addSeparator();
                            }
                        }

                        // Đóng tab
                        JMenuItem closeItem = new JMenuItem("Đóng tab");
                        closeItem.addActionListener(event -> {
                            chatTabs.remove(tabIndex);
                        });
                        popupMenu.add(closeItem);

                        popupMenu.show(chatTabs, e.getX(), e.getY());
                    }
                }
            }
        });

        createGroupButton.addActionListener(e -> {
            showCreateGroupDialog();
        });

        setupGroupTabPopupMenu();

        initCallButtons();
    }

    private void createOrShowPrivateChat(String username) {
        if (!chatPanels.containsKey(username)) {
            JPanel chatPanel = new JPanel();
            setupChatPanel(chatPanel);

            JScrollPane scrollPane = new JScrollPane(chatPanel);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);

            chatPanels.put(username, chatPanel);
            chatTabs.addTab(username, scrollPane);
        }

        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            if (chatTabs.getTitleAt(i).equals(username)) {
                chatTabs.setSelectedIndex(i);
                break;
            }
        }

        resetTabHighlight(username);

        addLoadMoreButton(username);

        client.setCurrentHistoryContext(username);
        client.getChatHistory(username, false);
    }

    private void createOrShowGroupChat(String groupName) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < chatTabs.getTabCount(); i++) {
                if (chatTabs.getTitleAt(i).equals(groupName + " (G)")) {
                    chatTabs.setSelectedIndex(i);
                    resetTabHighlight(groupName + " (G)");
                    return;
                }
            }

            JPanel chatPanel = new JPanel();
            setupChatPanel(chatPanel);

            JScrollPane scrollPane = new JScrollPane(chatPanel);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

            groupChatPanels.put(groupName, chatPanel);
            chatPanels.put(groupName, chatPanel);

            chatTabs.addTab(groupName + " (G)", scrollPane);
            chatTabs.setSelectedIndex(chatTabs.getTabCount() - 1);

            client.setCurrentHistoryContext(groupName);
            client.getChatHistory(groupName, true);

            addLoadMoreButton(groupName + " (G)");
        });
    }

    public void displayGroupMessage(String groupName, String sender, String message, long timestamp, String messageId) {
        SwingUtilities.invokeLater(() -> {
            JPanel chatPanel = groupChatPanels.get(groupName);

            if (chatPanel == null) {
                createOrShowGroupChat(groupName);
                chatPanel = groupChatPanels.get(groupName);
            }

            if (chatPanel != null) {
                MessagePanel messagePanel = new MessagePanel(
                        messageId, sender, message, timestamp, groupName, true, client);

                chatPanel.add(messagePanel);
                chatPanel.add(Box.createVerticalStrut(5));

                chatPanel.revalidate();
                chatPanel.repaint();

                scrollToBottom(chatPanel);

                String tabTitle = groupName + " (G)";
                if (!tabTitle.equals(getCurrentTabName())) {
                    highlightTab(tabTitle);
                }
            }
        });
    }

    public void displayGroupSystemMessage(String groupName, String message) {
        SwingUtilities.invokeLater(() -> {
            JPanel chatPanel = groupChatPanels.get(groupName);

            if (chatPanel == null) {
                createOrShowGroupChat(groupName);
                chatPanel = groupChatPanels.get(groupName);
            }

            if (chatPanel != null) {
                SystemMessagePanel systemPanel = new SystemMessagePanel(message);

                chatPanel.add(systemPanel);
                chatPanel.add(Box.createVerticalStrut(5)); // Khoảng cách

                chatPanel.revalidate();
                chatPanel.repaint();

                scrollToBottom(chatPanel);
            }
        });
    }

    public void addGroup(Group group) {
        SwingUtilities.invokeLater(() -> {
            boolean exists = false;
            for (int i = 0; i < groupListModel.getSize(); i++) {
                if (groupListModel.getElementAt(i).equals(group.getName())) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                groupListModel.addElement(group.getName());
            }
        });
    }

    public void removeGroup(String groupName) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < groupListModel.getSize(); i++) {
                if (groupListModel.getElementAt(i).equals(groupName)) {
                    groupListModel.removeElementAt(i);
                    break;
                }
            }

            for (int i = 0; i < chatTabs.getTabCount(); i++) {
                if (chatTabs.getTitleAt(i).equals(groupName + " (G)")) {
                    chatTabs.remove(i);
                    break;
                }
            }

            groupChatPanels.remove(groupName);
        });
    }

    public void updateGroupMembers(Group group) {
        if (group == null) {
            return;
        }
    }

    private void showMemberDialog(String groupName) {
        Group group = client.getGroup(groupName);
        if (group == null) return;

        JDialog dialog = new JDialog(this, "Thành viên nhóm " + groupName, true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(350, 400);
        dialog.setLocationRelativeTo(this);

        JPanel memberPanel = new JPanel(new BorderLayout());
        DefaultListModel<String> memberListModel = new DefaultListModel<>();

        for (String member : group.getMembers()) {
            memberListModel.addElement(member);
        }

        JList<String> memberList = new JList<>(memberListModel);
        memberList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        memberList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = memberList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        memberList.setSelectedIndex(index);
                        String selectedMember = memberList.getSelectedValue();

                        JPopupMenu popupMenu = new JPopupMenu();

                        JMenuItem privateChatItem = new JMenuItem("Chat riêng với " + selectedMember);
                        privateChatItem.addActionListener(event -> {
                            createOrShowPrivateChat(selectedMember);
                            dialog.dispose();
                        });

                        popupMenu.add(privateChatItem);

                        if (client.getCurrentUser().getUsername().equals(group.getCreator()) &&
                                !selectedMember.equals(client.getCurrentUser().getUsername())) {

                            JMenuItem removeItem = new JMenuItem("Xóa " + selectedMember + " khỏi nhóm");
                            removeItem.addActionListener(event -> {
                                int confirm = JOptionPane.showConfirmDialog(
                                        dialog,
                                        "Bạn có chắc muốn xóa " + selectedMember + " khỏi nhóm?",
                                        "Xác nhận xóa thành viên",
                                        JOptionPane.YES_NO_OPTION);

                                if (confirm == JOptionPane.YES_OPTION) {
                                    String _groupName = groupName.contains(" (G)") ?
                                            groupName.substring(0, groupName.length() - 4) : groupName;
                                    client.removeFromGroup(_groupName, selectedMember);
                                    memberListModel.removeElement(selectedMember);
                                }
                            });

                            popupMenu.addSeparator();
                            popupMenu.add(removeItem);
                        }

                        popupMenu.show(memberList, e.getX(), e.getY());
                    }
                } else if (e.getClickCount() == 2) {
                    String selectedMember = memberList.getSelectedValue();
                    if (selectedMember != null && !selectedMember.equals(client.getCurrentUser().getUsername())) {
                        createOrShowPrivateChat(selectedMember);
                        dialog.dispose();
                    }
                }
            }
        });

        JScrollPane memberScrollPane = new JScrollPane(memberList);
        memberPanel.add(new JLabel("Thành viên (" + memberListModel.size() + "):"), BorderLayout.NORTH);
        memberPanel.add(memberScrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton addButton = new JButton("Thêm thành viên");
        addButton.addActionListener(e -> {
            dialog.dispose();
            showAddMemberDialog(groupName);
        });

        JButton closeButton = new JButton("Đóng");
        closeButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(addButton);
        buttonPanel.add(closeButton);

        dialog.add(memberPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void sendFile() {
        int selectedIndex = chatTabs.getSelectedIndex();
        if (selectedIndex == -1 || chatTabs.getTitleAt(selectedIndex).equals("Global")) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn một người dùng hoặc nhóm để gửi file.",
                    "Lưu ý", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String tabName = chatTabs.getTitleAt(selectedIndex);
        String receiver;
        boolean isGroup = false;

        if (tabName.endsWith(" (G)")) {
            receiver = tabName.substring(0, tabName.length() - 4);
            isGroup = true;
        } else {
            receiver = tabName;
        }

        fileTransferPanel.sendFile(receiver, isGroup);
    }

    public void displayMessage(String chatContext, String sender, String message, long timestamp, String messageId) {
        SwingUtilities.invokeLater(() -> {
            JPanel chatPanel;

            if (chatContext.equals("Global")) {
                chatPanel = chatPanels.get("Global");
            } else {
                String tabName = chatContext;

                if (sender.equals(client.getCurrentUser().getUsername())) {
                    tabName = chatContext;
                } else {
                    tabName = sender;
                }

                chatPanel = getOrCreateChatPanel(tabName);
            }

            if (chatPanel != null) {
                boolean isGroup = chatContext.endsWith(" (G)");
                String actualContext = isGroup ? chatContext.substring(0, chatContext.length() - 4) : chatContext;

                MessagePanel messagePanel = new MessagePanel(
                        messageId, sender, message, timestamp, actualContext, isGroup, client);

                messagePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                messagePanel.setMaximumSize(new Dimension(chatPanel.getWidth() - 20, messagePanel.getPreferredSize().height));


                chatPanel.add(messagePanel);
                chatPanel.add(Box.createVerticalStrut(5)); // Khoảng cách giữa các tin nhắn

                chatPanel.revalidate();
                chatPanel.repaint();

                scrollToBottom(chatPanel);

                if (!chatContext.equals("Global") && !chatContext.equals(getCurrentTabName())) {
                    highlightTab(chatContext);
                }
            }
        });
    }

    public void removeMessage(String chatContext, String messageId) {
        SwingUtilities.invokeLater(() -> {
            JPanel chatPanel = chatPanels.get(chatContext);
            JPanel groupChatPanel = groupChatPanels.get(chatContext);
            if (chatContext.endsWith(" (G)")) {
                groupChatPanel = groupChatPanels.get(chatContext.substring(0, chatContext.length() - 4));
                chatPanel = chatPanels.get(chatContext.substring(0, chatContext.length() - 4));
            }
            System.out.println("Removing message with ID: " + messageId);
            System.out.println("Chat context: " + chatContext);
            System.out.println("Chat panel: " + chatPanel);
            System.out.println("Group chat panel: " + groupChatPanel);
            if (chatPanel != null) {
                Component[] components = chatPanel.getComponents();

                for (int i = 0; i < components.length; i++) {
                    Component comp = components[i];

                    if (comp instanceof MessagePanel && ((MessagePanel)comp).getMessageId().equals(messageId)) {
                        chatPanel.remove(comp);

                        if (i + 1 < components.length && components[i + 1] instanceof Box.Filler) {
                            chatPanel.remove(components[i + 1]);
                        }

                        chatPanel.revalidate();
                        chatPanel.repaint();
                        return;
                    }

                    if (comp instanceof FileMessageComponent && ((FileMessageComponent)comp).getFileId().equals(messageId)) {
                        chatPanel.remove(comp);

                        if (i + 1 < components.length && components[i + 1] instanceof Box.Filler) {
                            chatPanel.remove(components[i + 1]);
                        }

                        chatPanel.revalidate();
                        chatPanel.repaint();
                        return;
                    }
                }
            }
        });
    }

    private void scrollToBottom(JPanel chatPanel) {
        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, chatPanel);
        if (scrollPane != null) {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            SwingUtilities.invokeLater(() -> vertical.setValue(vertical.getMaximum()));
        }
    }

    private JPanel getOrCreateChatPanel(String name) {
        if (!chatPanels.containsKey(name)) {
            if (name.endsWith(" (G)")) {
                createOrShowGroupChat(name.substring(0, name.length() - 4));
            } else {
                createOrShowPrivateChat(name);
            }
        }

        return chatPanels.get(name);
    }

    public void displayFileMessageInGroup(String groupName, String sender, String fileName,
                                          long fileSize, String fileId, String status,
                                          String filePath, long timestamp) {
        SwingUtilities.invokeLater(() -> {
            JPanel chatPanel = groupChatPanels.get(groupName);

            if (chatPanel == null) {
                createOrShowGroupChat(groupName);
                chatPanel = groupChatPanels.get(groupName);
            }

            if (chatPanel != null) {
                boolean found = false;
                Component[] components = chatPanel.getComponents();

                for (Component comp : components) {
                    if (comp instanceof FileMessageComponent &&
                            ((FileMessageComponent)comp).getFileId().equals(fileId)) {
                        FileMessageComponent fileComp = (FileMessageComponent) comp;
                        fileComp.updateStatus(status);
                        if (filePath != null && !filePath.isEmpty()) {
                            fileComp.setFilePath(filePath);
                        }
                        fileComp.setChatContext(groupName, true);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    FileMessageComponent fileComponent = new FileMessageComponent(
                            fileName, fileSize, fileId, sender, status, filePath, client);
                    fileComponent.setChatContext(groupName, true);

                    chatPanel.add(fileComponent);
                    chatPanel.add(Box.createVerticalStrut(5)); // Khoảng cách

                    chatPanel.revalidate();
                    chatPanel.repaint();
                    scrollToBottom(chatPanel);
                }

                String tabTitle = groupName + " (G)";
                if (!tabTitle.equals(getCurrentTabName())) {
                    highlightTab(tabTitle);
                }
            }
        });
    }

    public void displayFileMessage(String chatContext, String sender, String fileName,
                                   long fileSize, String fileId, String status, String filePath) {
        SwingUtilities.invokeLater(() -> {
            JPanel chatPanel = getOrCreateChatPanel(chatContext);

            if (chatPanel != null) {
                System.out.println("Displaying file message: chatContext=" + chatContext +
                        ", sender=" + sender + ", fileName=" + fileName +
                        ", fileId=" + fileId + ", status=" + status);

                boolean found = false;
                Component[] components = chatPanel.getComponents();

                for (Component comp : components) {
                    if (comp instanceof FileMessageComponent) {
                        FileMessageComponent fileComp = (FileMessageComponent) comp;
                        if (fileComp.getFileId().equals(fileId)) {
                            fileComp.updateStatus(status);
                            if (filePath != null && !filePath.isEmpty()) {
                                fileComp.setFilePath(filePath);
                            }

                            boolean isGroup = chatContext.endsWith(" (G)");
                            String actualContext = isGroup ? chatContext.substring(0, chatContext.length() - 4) : chatContext;
                            fileComp.setChatContext(actualContext, isGroup);

                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    FileMessageComponent fileComponent = new FileMessageComponent(
                            fileName, fileSize, fileId, sender, status, filePath, client);

                    boolean isGroup = chatContext.endsWith(" (G)");
                    String actualContext = isGroup ? chatContext.substring(0, chatContext.length() - 4) : chatContext;
                    fileComponent.setChatContext(actualContext, isGroup);

                    fileComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
                    fileComponent.setMaximumSize(new Dimension(chatPanel.getWidth() - 20, fileComponent.getPreferredSize().height));

                    chatPanel.add(fileComponent);
                    chatPanel.add(Box.createVerticalStrut(5)); // Khoảng cách giữa các tin nhắn

                    chatPanel.revalidate();
                    chatPanel.repaint();

                    scrollToBottom(chatPanel);
                }

                if (!sender.equals(client.getCurrentUser().getUsername()) &&
                        !chatContext.equals(getCurrentTabName())) {
                    highlightTab(chatContext);
                }
            }
        });
    }

    public void displayFileMessage(String chatContext, String sender, String fileName,
                                   long fileSize, String fileId, String status) {
        displayFileMessage(chatContext, sender, fileName, fileSize, fileId, status, null);
    }

    public void updateUploadProgressBar(String fileId, int progress) {
        SwingUtilities.invokeLater(() -> {
            fileTransferPanel.updateUploadProgress(fileId, progress);
        });
    }

    public void updateFileComponent(String fileId, String filePath) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("Updating file path for: " + fileId + " to: " + filePath);

            for (Map.Entry<String, JPanel> entry : chatPanels.entrySet()) {
                JPanel chatPanel = entry.getValue();
                Component[] components = chatPanel.getComponents();

                for (Component comp : components) {
                    if (comp instanceof FileMessageComponent) {
                        FileMessageComponent fileComp = (FileMessageComponent) comp;

                        if (fileComp.getFileId() != null && fileComp.getFileId().equals(fileId)) {
                            fileComp.setFilePath(filePath);
                            System.out.println("Found and updated file path for: " + fileId);
                            return;
                        }
                    }
                }
            }
        });
    }

    public void updateFileStatus(String fileId, String status) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("DEBUG: Trying to update file status: fileId=" + fileId + ", status=" + status);
            boolean found = false;

            for (Map.Entry<String, JPanel> entry : chatPanels.entrySet()) {
                JPanel chatPanel = entry.getValue();
                Component[] components = chatPanel.getComponents();

                for (Component comp : components) {
                    if (comp instanceof FileMessageComponent) {
                        FileMessageComponent fileComp = (FileMessageComponent) comp;

                        if (fileComp.getFileId() != null && fileComp.getFileId().equals(fileId)) {
                            // Cập nhật status
                            fileComp.updateStatus(status);
                            found = true;
                            System.out.println("Found and updated file component in tab: " + entry.getKey());
                            break;
                        }
                    }
                }

                if (found) break;
            }

            if (!found) {
                for (Map.Entry<String, JPanel> entry : groupChatPanels.entrySet()) {
                    JPanel chatPanel = entry.getValue();
                    Component[] components = chatPanel.getComponents();

                    for (Component comp : components) {
                        if (comp instanceof FileMessageComponent) {
                            FileMessageComponent fileComp = (FileMessageComponent) comp;

                            if (fileComp.getFileId() != null && fileComp.getFileId().equals(fileId)) {
                                fileComp.updateStatus(status);
                                found = true;
                                System.out.println("Found and updated file component in group tab: " + entry.getKey());
                                break;
                            }
                        }
                    }

                    if (found) break;
                }
            }

            if (!found) {
                System.out.println("WARNING: No FileMessageComponent found for fileId: " + fileId);
            }
        });
    }

    public void updateFilePath(String fileId, String filePath) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("DEBUG: Trying to update file path: fileId=" + fileId + ", filePath=" + filePath);
            boolean found = false;

            for (Map.Entry<String, JPanel> entry : chatPanels.entrySet()) {
                JPanel chatPanel = entry.getValue();
                Component[] components = chatPanel.getComponents();

                for (Component comp : components) {
                    if (comp instanceof FileMessageComponent) {
                        FileMessageComponent fileComp = (FileMessageComponent) comp;

                        if (fileComp.getFileId() != null && fileComp.getFileId().equals(fileId)) {
                            fileComp.setFilePath(filePath);
                            found = true;
                            System.out.println("Found and updated file component in tab: " + entry.getKey());
                            break;
                        }
                    }
                }

                if (found) break;
            }

            if (!found) {
                for (Map.Entry<String, JPanel> entry : groupChatPanels.entrySet()) {
                    JPanel chatPanel = entry.getValue();
                    Component[] components = chatPanel.getComponents();

                    for (Component comp : components) {
                        if (comp instanceof FileMessageComponent) {
                            FileMessageComponent fileComp = (FileMessageComponent) comp;

                            if (fileComp.getFileId() != null && fileComp.getFileId().equals(fileId)) {
                                fileComp.setFilePath(filePath);
                                found = true;
                                System.out.println("Found and updated file component in group tab: " + entry.getKey());
                                break;
                            }
                        }
                    }

                    if (found) break;
                }
            }

            if (!found) {
                System.out.println("WARNING: No FileMessageComponent found for fileId: " + fileId);
            }
        });
    }

    private void sendMessage() {
        String message = messageInput.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        int selectedIndex = chatTabs.getSelectedIndex();
        if (selectedIndex == -1) {
            return;
        }

        String tabName = chatTabs.getTitleAt(selectedIndex);

        if (tabName.equals("Global")) {
            JOptionPane.showMessageDialog(this,
                    "Tab Global chỉ dùng để hiển thị thông tin, không để chat.",
                    "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        } else if (tabName.endsWith(" (G)")) {
            String groupName = tabName.substring(0, tabName.length() - 4);
            client.sendGroupMessage(groupName, message);
        } else {
            client.sendPrivateMessage(tabName, message);
        }

        messageInput.setText("");
        messageInput.requestFocusInWindow();
    }

    public void displayGlobalMessage(String sender, String message) {
        showNotification(sender + ": " + message);
    }

    private void highlightTab(String tabName) {
        tabStatus.put(tabName, "new");

        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            String title = chatTabs.getTitleAt(i);

            if ((title.equals(tabName) || (tabName + " (G)").equals(title)) &&
                    i != chatTabs.getSelectedIndex()) {
                chatTabs.setForegroundAt(i, Color.RED);
                break;
            }
        }
    }

    private void resetTabHighlight(String tabName) {
        tabStatus.remove(tabName);

        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            String title = chatTabs.getTitleAt(i);

            if (title.equals(tabName) || (tabName + " (G)").equals(title)) {
                chatTabs.setForegroundAt(i, Color.BLACK);
                break;
            }
        }
    }

    private String getCurrentTabName() {
        int index = chatTabs.getSelectedIndex();
        return index != -1 ? chatTabs.getTitleAt(index) : "";
    }

    public void updateOnlineUsers(String[] users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String user : users) {
                userListModel.addElement(user);
            }
        });
    }

    public void addOnlineUser(String username) {
        SwingUtilities.invokeLater(() -> {
            if (!userListModel.contains(username)) {
                userListModel.addElement(username);
            }
        });
    }

    public void removeOnlineUser(String username) {
        SwingUtilities.invokeLater(() -> {
            userListModel.removeElement(username);
        });
    }

    public void displayChatHistory(String chatContext, java.util.List<String[]> messages) {
        SwingUtilities.invokeLater(() -> {
            JPanel chatPanel = getOrCreateChatPanel(chatContext);

            if (chatPanel != null) {
                chatPanel.removeAll();

                boolean isGroupMessage = Boolean.parseBoolean(messages.getLast()[messages.getLast().length - 1]);

                System.out.println("Displaying chat history for: " + chatContext +
                        ", isGroupMessage: " + isGroupMessage);

                boolean isGroup = chatContext.endsWith(" (G)");
                String actualChatContext = isGroup ?
                        chatContext.substring(0, chatContext.length() - 4) : chatContext;

                for (String[] messageParts : messages) {
                    System.out.println("Processing message: " + String.join(", ", messageParts));

                    // Text message
                    if (messageParts.length == 6) {
                        String sender = messageParts[0];
                        String content = messageParts[1];
                        long timestamp = Long.parseLong(messageParts[2]);
                        String messageType = messageParts[3];
                        String messageId = messageParts[4];

                        MessagePanel messagePanel = new MessagePanel(
                                messageId, sender, content, timestamp, actualChatContext, isGroupMessage, client);

                        messagePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                        messagePanel.setMaximumSize(new Dimension(chatPanel.getWidth() - 20, messagePanel.getPreferredSize().height));

                        chatPanel.add(messagePanel);
                        chatPanel.add(Box.createVerticalStrut(5));
                    }
                    else if (messageParts.length == 10) {
                        // File message
                        String sender = messageParts[0];
                        String fileId = messageParts[1];
                        String fileName = messageParts[2];
                        long fileSize = Long.parseLong(messageParts[3]);
                        long timestamp = Long.parseLong(messageParts[4]);
                        String actualFileNameSave = messageParts[5];
                        String actualFileNameUpload = messageParts[6];
                        String messageType = messageParts[7];
                        String messageId = messageParts[8];
                        String filePath = "";

                        if (sender.equals(client.getCurrentUser().getUsername()) && !actualFileNameUpload.isEmpty()) {
                            filePath = ChatClient.defaultUploadFolder + File.separator + actualFileNameUpload;
                        } else if (!actualFileNameSave.isEmpty()) {
                            filePath = ChatClient.defaultDownloadFolder + File.separator + actualFileNameSave;
                        }

                        FileMessageComponent fileComponent = new FileMessageComponent(
                                fileName, fileSize, fileId, sender, "thành công", filePath, client);
                        fileComponent.setChatContext(actualChatContext, isGroupMessage);

                        fileComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
                        fileComponent.setMaximumSize(new Dimension(chatPanel.getWidth() - 20, fileComponent.getPreferredSize().height));

                        chatPanel.add(fileComponent);
                        chatPanel.add(Box.createVerticalStrut(5));
                    }
                }

                chatPanel.revalidate();
                chatPanel.repaint();

                scrollToBottom(chatPanel);
                updateComponentSizes(chatPanel);
            }
        });
    }

    public void displayOlderMessages(String chatContext, java.util.List<String[]> olderMessages) {
        SwingUtilities.invokeLater(() -> {
            JPanel chatPanel;

            if (chatContext.endsWith(" (G)")) {
                String groupName = chatContext.substring(0, chatContext.length() - 4);
                chatPanel = groupChatPanels.get(groupName);

                if (chatPanel == null) {
                    chatPanel = chatPanels.get(groupName);

                    if (chatPanel == null) {
                        System.out.println("Error: Could not find chat panel for group: " + groupName);
                        return;
                    }
                }
            } else {
                chatPanel = getOrCreateChatPanel(chatContext);
            }

            if (chatPanel != null) {
                boolean isGroupMessage = Boolean.parseBoolean(olderMessages.getLast()[olderMessages.getLast().length - 1]);

                System.out.println("Displaying chat history for: " + chatContext +
                        ", isGroupMessage: " + isGroupMessage);

                boolean isGroup = chatContext.endsWith(" (G)");
                String actualChatContext = isGroup ?
                        chatContext.substring(0, chatContext.length() - 4) : chatContext;

                JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(
                        JScrollPane.class, chatPanel);
                if (scrollPane == null) {
                    Container parent = chatPanel.getParent();
                    while (parent != null && !(parent instanceof JScrollPane)) {
                        parent = parent.getParent();
                    }
                    if (parent instanceof JScrollPane) {
                        scrollPane = (JScrollPane) parent;
                    }
                }

                if (scrollPane == null) {
                    System.out.println("Error: Could not find scrollPane for chat panel");
                    return;
                }

                Point viewPosition = scrollPane.getViewport().getViewPosition();
                int oldHeight = chatPanel.getPreferredSize().height;

                Component[] currentComponents = chatPanel.getComponents();

                chatPanel.removeAll();

                for (String[] messageParts : olderMessages) {
                    if (messageParts.length >= 5) {
                        String sender = messageParts[0];
                        String content = messageParts[1];
                        long timestamp = Long.parseLong(messageParts[2]);
                        String messageId = messageParts.length > 4 ? messageParts[4] :
                                (System.currentTimeMillis() + "_" + sender + "_old_" + Math.random());

                        MessagePanel messagePanel = new MessagePanel(
                                messageId, sender, content, timestamp, actualChatContext, isGroup, client);

                        messagePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                        messagePanel.setMaximumSize(new Dimension(chatPanel.getWidth() - 20, messagePanel.getPreferredSize().height));

                        chatPanel.add(messagePanel);
                        chatPanel.add(Box.createVerticalStrut(5));
                    }
                    else if (messageParts.length >= 8) { // File message
                        String sender = messageParts[0];
                        String fileId = messageParts[1];
                        String fileName = messageParts[2];
                        long fileSize = Long.parseLong(messageParts[3]);
                        long timestamp = Long.parseLong(messageParts[4]);
                        String actualFileNameSave = messageParts.length > 5 ? messageParts[5] : "";
                        String actualFileNameUpload = messageParts.length > 6 ? messageParts[6] : "";
                        String filePath = "";

                        if (sender.equals(client.getCurrentUser().getUsername()) && !actualFileNameUpload.isEmpty()) {
                            filePath = ChatClient.defaultUploadFolder + File.separator + actualFileNameUpload;
                        } else if (!actualFileNameSave.isEmpty()) {
                            filePath = ChatClient.defaultDownloadFolder + File.separator + actualFileNameSave;
                        }

                        FileMessageComponent fileComponent = new FileMessageComponent(
                                fileName, fileSize, fileId, sender, "thành công", filePath, client);
                        fileComponent.setChatContext(actualChatContext, isGroup);

                        fileComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
                        fileComponent.setMaximumSize(new Dimension(chatPanel.getWidth() - 20, fileComponent.getPreferredSize().height));

                        chatPanel.add(fileComponent);
                        chatPanel.add(Box.createVerticalStrut(5));
                    }
                }

                for (Component comp : currentComponents) {
                    chatPanel.add(comp);
                }

                chatPanel.revalidate();
                chatPanel.repaint();

                JScrollPane finalScrollPane = scrollPane;
                SwingUtilities.invokeLater(() -> finalScrollPane.getViewport().setViewPosition(viewPosition));
            }
        });
    }

    private void addLoadMoreButton(String chatContext) {
        JPanel chatPanel = chatPanels.get(chatContext);
        if (chatPanel == null) {
            if (chatContext.endsWith(" (G)")) {
                String groupName = chatContext.substring(0, chatContext.length() - 4);
                chatPanel = chatPanels.get(groupName);
                if (chatPanel == null) {
                    return;
                }
            } else {
                return;
            }
        }

        JScrollPane scrollPane = null;

        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            String title = chatTabs.getTitleAt(i);
            if (title.equals(chatContext)) {
                Component comp = chatTabs.getComponentAt(i);
                if (comp instanceof JScrollPane) {
                    scrollPane = (JScrollPane) comp;
                    break;
                } else if (comp instanceof JSplitPane) {
                    Component left = ((JSplitPane) comp).getLeftComponent();
                    if (left instanceof JScrollPane) {
                        scrollPane = (JScrollPane) left;
                        break;
                    }
                }
            }
        }

        if (scrollPane == null && !chatContext.endsWith(" (G)")) {
            String groupTabName = chatContext + " (G)";
            for (int i = 0; i < chatTabs.getTabCount(); i++) {
                String title = chatTabs.getTitleAt(i);
                if (title.equals(groupTabName)) {
                    Component comp = chatTabs.getComponentAt(i);
                    if (comp instanceof JSplitPane) {
                        Component left = ((JSplitPane) comp).getLeftComponent();
                        if (left instanceof JScrollPane) {
                            scrollPane = (JScrollPane) left;
                            break;
                        }
                    }
                }
            }
        }

        if (scrollPane != null) {
            // Xóa nút cũ nếu có
            if (scrollPane.getColumnHeader() != null) {
                Component[] comps = scrollPane.getColumnHeader().getComponents();
                for (Component comp : comps) {
                    if (comp instanceof JPanel) {
                        JPanel panel = (JPanel) comp;
                        for (Component buttonComp : panel.getComponents()) {
                            if (buttonComp instanceof JButton && ((JButton) buttonComp).getText().equals("Tải thêm")) {
                                scrollPane.setColumnHeaderView(null);
                                break;
                            }
                        }
                    }
                }
            }

            JButton loadMoreButton = new JButton("Tải thêm");
            loadMoreButton.addActionListener(e -> {
                String context = chatContext;
                boolean isGroup = false;

                if (context.endsWith(" (G)")) {
                    context = context.substring(0, context.length() - 4);
                    isGroup = true;
                }

                System.out.println("Loading more messages for context: " + context + ", isGroup: " + isGroup);

                client.loadMoreMessages(context, isGroup);
            });

            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.add(loadMoreButton, BorderLayout.CENTER);
            scrollPane.setColumnHeaderView(headerPanel);

            scrollPane.revalidate();
            scrollPane.repaint();
        } else {
            System.out.println("Warning: Could not find scrollPane for context: " + chatContext);
        }
    }

    private void showCreateGroupDialog() {
        JDialog dialog = new JDialog(this, "Tạo nhóm chat mới", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridLayout(2, 1, 5, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField groupNameField = new JTextField();
        JButton createButton = new JButton("Tạo nhóm");

        panel.add(new JLabel("Tên nhóm:"));
        panel.add(groupNameField);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(createButton);

        createButton.addActionListener(e -> {
            String groupName = groupNameField.getText().trim();
            if (!groupName.isEmpty()) {
                client.createGroup(groupName);
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog,
                        "Vui lòng nhập tên nhóm.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void setupChatPanel(JPanel chatPanel) {
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        chatPanel.setBackground(Color.WHITE);

        chatPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent e) {
                updateComponentSizes(chatPanel);
            }
        });
    }

    private void updateComponentSizes(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof MessagePanel || comp instanceof FileMessageComponent) {
                Dimension currentSize = comp.getPreferredSize();
                comp.setMaximumSize(new Dimension(container.getWidth() - 20, currentSize.height));
            }
        }
        container.revalidate();
        container.repaint();
    }

    public void setMoreMessagesAvailable(String chatContext, boolean available) {
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = null;

            for (int i = 0; i < chatTabs.getTabCount(); i++) {
                String title = chatTabs.getTitleAt(i);

                if (title.endsWith(" (G)")) {
                    title = title.substring(0, title.length() - 4);
                }

                if (title.equals(chatContext)) {
                    Component comp = chatTabs.getComponentAt(i);
                    if (comp instanceof JScrollPane) {
                        scrollPane = (JScrollPane) comp;
                    } else if (comp instanceof JSplitPane) {
                        Component left = ((JSplitPane) comp).getLeftComponent();
                        if (left instanceof JScrollPane) {
                            scrollPane = (JScrollPane) left;
                        }
                    }
                    break;
                }
            }

            if (scrollPane != null) {
                if (available) {
                    if (scrollPane.getColumnHeader() == null ||
                            scrollPane.getColumnHeader().getView() == null) {
                        addLoadMoreButton(chatContext);
                    }
                } else {
                    scrollPane.setColumnHeaderView(null);
                }
            }
        });
    }

    private void setupGroupTabPopupMenu() {
        // Thêm menu chuột phải cho tab
        chatTabs.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int tabIndex = chatTabs.indexAtLocation(e.getX(), e.getY());
                    if (tabIndex >= 0) {
                        String tabTitle = chatTabs.getTitleAt(tabIndex);

                        // Nếu là tab nhóm
                        if (tabTitle.endsWith(" (G)")) {
                            String groupName = tabTitle.substring(0, tabTitle.length() - 4);
                            Group group = client.getGroup(groupName);

                            if (group != null) {
                                JPopupMenu popupMenu = new JPopupMenu();

                                // Xem thành viên
                                JMenuItem viewMembersItem = new JMenuItem("Danh sách thành viên");
                                viewMembersItem.addActionListener(event -> {
                                    showMemberDialog(groupName);
                                });
                                popupMenu.add(viewMembersItem);

                                // Thêm thành viên
                                JMenuItem addMemberItem = new JMenuItem("Thêm thành viên");
                                addMemberItem.addActionListener(event -> {
                                    showAddMemberDialog(groupName);
                                });
                                popupMenu.add(addMemberItem);

                                // Rời nhóm
                                JMenuItem leaveItem = new JMenuItem("Rời nhóm");
                                leaveItem.addActionListener(event -> {
                                    int confirm = JOptionPane.showConfirmDialog(
                                            ChatFrame.this,
                                            "Bạn có chắc muốn rời khỏi nhóm " + groupName + "?",
                                            "Xác nhận rời nhóm",
                                            JOptionPane.YES_NO_OPTION);

                                    if (confirm == JOptionPane.YES_OPTION) {
                                        client.leaveGroup(groupName);
                                    }
                                });
                                popupMenu.add(leaveItem);

                                popupMenu.addSeparator();

                                // Đóng tab
                                JMenuItem closeItem = new JMenuItem("Đóng tab");
                                closeItem.addActionListener(event -> {
                                    chatTabs.remove(tabIndex);
                                });
                                popupMenu.add(closeItem);

                                popupMenu.show(chatTabs, e.getX(), e.getY());
                            }
                        } else {
                            JPopupMenu popupMenu = new JPopupMenu();
                            JMenuItem closeItem = new JMenuItem("Đóng tab");
                            closeItem.addActionListener(event -> {
                                chatTabs.remove(tabIndex);
                            });
                            popupMenu.add(closeItem);
                            popupMenu.show(chatTabs, e.getX(), e.getY());
                        }
                    }
                }
            }
        });
    }

    private void showAddMemberDialog(String groupName) {
        DefaultListModel<String> onlineUsers = userListModel;

        Group group = client.getGroup(groupName);
        java.util.List<String> members = group.getMembers();

        DefaultListModel<String> availableUsers = new DefaultListModel<>();
        for (int i = 0; i < onlineUsers.size(); i++) {
            String user = onlineUsers.getElementAt(i);
            if (!members.contains(user) && !user.equals(client.getCurrentUser().getUsername())) {
                availableUsers.addElement(user);
            }
        }

        if (availableUsers.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Không có người dùng nào khả dụng để thêm vào nhóm.",
                    "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Thêm thành viên vào nhóm " + groupName, true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(300, 300);
        dialog.setLocationRelativeTo(this);

        JList<String> userList = new JList<>(availableUsers);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane listScrollPane = new JScrollPane(userList);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addButton = new JButton("Thêm");
        JButton cancelButton = new JButton("Hủy");

        addButton.addActionListener(e -> {
            String selectedUser = userList.getSelectedValue();
            if (selectedUser != null) {
                client.addToGroup(groupName, selectedUser);
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog,
                        "Vui lòng chọn một người dùng để thêm vào nhóm.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);

        dialog.add(new JLabel("  Chọn người dùng để thêm vào nhóm:"), BorderLayout.NORTH);
        dialog.add(listScrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void initCallButtons() {
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = userList.locationToIndex(e.getPoint());
                    if (index != -1) {
                        userList.setSelectedIndex(index);
                        String selectedUser = userList.getSelectedValue();

                        if (!selectedUser.equals(client.getCurrentUser().getUsername())) {
                            JPopupMenu popupMenu = new JPopupMenu();

                            JMenuItem chatItem = new JMenuItem("Chat riêng");
                            chatItem.addActionListener(event -> {
                                createOrShowPrivateChat(selectedUser);
                            });

                            JMenuItem voiceCallItem = new JMenuItem("Gọi thoại");
                            voiceCallItem.addActionListener(event -> {
                                startVoiceCall(selectedUser);
                            });

                            JMenuItem videoCallItem = new JMenuItem("Gọi video");
                            videoCallItem.addActionListener(event -> {
                                startVideoCall(selectedUser);
                            });

                            popupMenu.add(chatItem);
                            popupMenu.addSeparator();
                            popupMenu.add(voiceCallItem);
                            popupMenu.add(videoCallItem);

                            popupMenu.show(userList, e.getX(), e.getY());
                        }
                    }
                }
            }
        });
    }

    private void startVoiceCall(String callParticipant) {
        // Kiểm tra xem người dùng có đang trong cuộc gọi khác không
        if (client.getVoiceCallManager().isInCall()) {
            JOptionPane.showMessageDialog(this,
                    "Bạn đang trong một cuộc gọi khác. Vui lòng kết thúc cuộc gọi đó trước.",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int option = JOptionPane.showConfirmDialog(
                this,
                "Bạn muốn bắt đầu cuộc gọi thoại với " + callParticipant + "?",
                "Xác nhận cuộc gọi",
                JOptionPane.YES_NO_OPTION
        );

        if (option == JOptionPane.YES_OPTION) {
            outgoingCallDialog = new OutgoingCallDialog(this, callParticipant, true, e -> {
                client.getVoiceCallManager().endCall();
            });
            outgoingCallDialog.setVisible(true);

            client.getVoiceCallManager().startCall(callParticipant);
        }
    }

    private void startVideoCall(String selectedUser) {
        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một người dùng để gọi.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (client.getVideoCallManager().isInCall()) {
            JOptionPane.showMessageDialog(this,
                    "Bạn đang trong một cuộc gọi khác. Vui lòng kết thúc cuộc gọi đó trước.",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        VideoCallDialog dialog = new VideoCallDialog(this, client, selectedUser, false);
        dialog.setVisible(true);

        new Thread(() -> {
            client.getVideoCallManager().startCall(selectedUser);
        }, "VideoCallStartThread").start();
    }

    public void displayIncomingVoiceCallRequest(String caller) {
        if (client.getVoiceCallManager().isInCall()) {
            client.getVoiceCallManager().rejectCall(caller);
            showNotificationV2("Đã từ chối cuộc gọi từ " + caller + " vì bạn đang trong cuộc gọi khác");
            return;
        }

        // Phát âm thanh chuông gọi
        soundPlayer.playSound("/sounds/ringtone.wav", true);

        SwingUtilities.invokeLater(() -> {
            CallNotificationDialog dialog = new CallNotificationDialog(this, caller, true);
            int result = dialog.showDialog();

            soundPlayer.stopSound();

            if (result == CallNotificationDialog.ACCEPT) {
                VoiceCallDialog callDialog = new VoiceCallDialog(this, client, caller, true);
                callDialog.setVisible(true);
                client.getVoiceCallManager().acceptCall(caller);
            } else {
                client.getVoiceCallManager().rejectCall(caller);
            }
        });
    }

    public void handleCallAccepted(String participant) {
        if (outgoingCallDialog != null && outgoingCallDialog.isVisible()) {
            outgoingCallDialog.setConnected();
        }

        VoiceCallDialog callDialog = new VoiceCallDialog(this, client, participant, false);
        callDialog.setVisible(true);
    }

    public void handleCallRejected(String participant) {
        if (outgoingCallDialog != null && outgoingCallDialog.isVisible()) {
            outgoingCallDialog.setRejected();
        }

        showNotificationV2(participant + " đã từ chối cuộc gọi của bạn");
    }

    public void handleCallEnded(String participant) {
        showNotificationV2("Cuộc gọi với " + participant + " đã kết thúc");

        if (outgoingCallDialog != null && outgoingCallDialog.isVisible()) {
            outgoingCallDialog.close();
            outgoingCallDialog = null;
        }
    }

    private void showNotificationV2(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Thông báo",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void createGlobalChatTab() {
        JPanel globalPanel = new JPanel(new BorderLayout());
        globalPanel.setBackground(Color.WHITE);

        WelcomePanel welcomePanel = new WelcomePanel();

        JPanel centeringPanel = new JPanel(new GridBagLayout());
        centeringPanel.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.CENTER;
        centeringPanel.add(welcomePanel, gbc);

        globalPanel.add(centeringPanel, BorderLayout.CENTER);

        chatPanels.put("Global", new JPanel());
        chatTabs.addTab("Global", globalPanel);
    }

    public void showInfoMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    this,
                    message,
                    "Thông báo",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });
    }

    public void showWarningMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    this,
                    message,
                    "Cảnh báo",
                    JOptionPane.WARNING_MESSAGE
            );
        });
    }

    public void showSuccessMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    this,
                    message,
                    "Thành công",
                    JOptionPane.PLAIN_MESSAGE
            );
        });
    }

    public void showNotification(String message) {
        JWindow notification = new JWindow();
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        panel.setBackground(new Color(255, 255, 225));

        JLabel label = new JLabel(message);
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(label);

        notification.add(panel);
        notification.pack();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        notification.setLocation(
                screenSize.width - notification.getWidth() - 20,
                screenSize.height - notification.getHeight() - 50
        );

        notification.setVisible(true);

        // Tự động đóng sau 3 giây
        Timer timer = new Timer(3000, e -> notification.dispose());
        timer.setRepeats(false);
        timer.start();
    }
}