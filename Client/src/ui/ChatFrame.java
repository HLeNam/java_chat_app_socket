package ui;

import client.ChatClient;
import model.Group;
import ui.components.FileMessageComponent;
import ui.components.FileTransferPanel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
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
    private Map<String, JTextPane> chatPanels;
    private Map<String, String> tabStatus;
    private Map<String, JTextPane> groupChatPanels = new HashMap<>();

    public ChatFrame(ChatClient client) {
        this.client = client;
        this.chatPanels = new HashMap<>();
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

        // Event listeners
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Hiển thị hộp thoại xác nhận
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

        // Thêm menu chuột phải cho tab
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
    }

    // Tạo hoặc hiển thị tab chat riêng tư
    private void createOrShowPrivateChat(String username) {
        // Kiểm tra xem tab đã tồn tại chưa
        if (!chatPanels.containsKey(username)) {
            JTextPane chatArea = new JTextPane();
            chatArea.setEditable(false);

            JScrollPane scrollPane = new JScrollPane(chatArea);

            chatPanels.put(username, chatArea);
            chatTabs.addTab(username, scrollPane);
        }

        // Chuyển đến tab chat với người dùng này
        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            if (chatTabs.getTitleAt(i).equals(username)) {
                chatTabs.setSelectedIndex(i);
                break;
            }
        }

        // Reset trạng thái highlight
        resetTabHighlight(username);

        addLoadMoreButton(username);

        // Tải lịch sử chat
        client.setCurrentHistoryContext(username);
        client.getChatHistory(username, false);
    }

    private void createOrShowGroupChat(String groupName) {
        SwingUtilities.invokeLater(() -> {
            // Kiểm tra tab đã tồn tại chưa
            for (int i = 0; i < chatTabs.getTabCount(); i++) {
                if (chatTabs.getTitleAt(i).equals(groupName + " (G)")) {
                    chatTabs.setSelectedIndex(i);
                    resetTabHighlight(groupName + " (G)"); // Reset highlight khi chọn tab
                    return;
                }
            }

            // Tạo tab mới
            JTextPane chatArea = new JTextPane();
            chatArea.setEditable(false);
            chatArea.setContentType("text/plain");

            // Tạo scrollPane cho chatArea
            JScrollPane scrollPane = new JScrollPane(chatArea);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

            // Lưu vào cả hai map để đảm bảo nhất quán
            groupChatPanels.put(groupName, chatArea);
            chatPanels.put(groupName, chatArea); // Đảm bảo lưu vào cả chatPanels

            // Tạo SplitPane để chia khu vực chat và danh sách thành viên
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
            splitPane.setLeftComponent(scrollPane);

            // Thêm tab mới
            chatTabs.addTab(groupName + " (G)", splitPane);
            chatTabs.setSelectedIndex(chatTabs.getTabCount() - 1);

            // Cập nhật danh sách thành viên
            Group group = client.getGroup(groupName);
            if (group != null) {
                updateGroupMembers(group);
            }

            // Lấy lịch sử tin nhắn
            client.setCurrentHistoryContext(groupName);
            client.getChatHistory(groupName, true);

            // Thêm nút tải thêm tin nhắn
            addLoadMoreButton(groupName + " (G)");
        });
    }

    public void displayGroupMessage(String groupName, String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            JTextPane chatArea = groupChatPanels.get(groupName);

            if (chatArea == null) {
                // Nếu tab chưa được tạo, tạo mới
                createOrShowGroupChat(groupName);
                chatArea = groupChatPanels.get(groupName);
            }

            if (chatArea != null) {
                StyledDocument doc = chatArea.getStyledDocument();

                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                    String timeString = "[" + sdf.format(new Date()) + "]";

                    // Style cho thời gian
                    Style timeStyle = chatArea.addStyle("TimeStyle", null);
                    StyleConstants.setForeground(timeStyle, Color.GRAY);
                    StyleConstants.setFontSize(timeStyle, 10);

                    // Style cho người gửi
                    Style senderStyle = chatArea.addStyle("SenderStyle", null);
                    if (sender.equals(client.getCurrentUser().getUsername())) {
                        StyleConstants.setForeground(senderStyle, new Color(0, 128, 0));
                    } else {
                        StyleConstants.setForeground(senderStyle, Color.BLUE);
                    }
                    StyleConstants.setBold(senderStyle, true);

                    Style messageStyle = chatArea.addStyle("MessageStyle", null);

                    // Thêm thời gian
                    doc.insertString(doc.getLength(), timeString, timeStyle);

                    // Thêm người gửi
                    doc.insertString(doc.getLength(), sender + ": ", senderStyle);

                    // Thêm nội dung tin nhắn
                    doc.insertString(doc.getLength(), message, messageStyle);
                    doc.insertString(doc.getLength(), "\n", null);

                    // Cuộn xuống để hiển thị tin nhắn mới nhất
                    chatArea.setCaretPosition(doc.getLength());
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void displayGroupSystemMessage(String groupName, String message) {
        SwingUtilities.invokeLater(() -> {
            JTextPane chatArea = groupChatPanels.get(groupName);

            if (chatArea == null) {
                // Nếu tab chưa được tạo
                createOrShowGroupChat(groupName);
                chatArea = groupChatPanels.get(groupName);
            }

            if (chatArea != null) {
                StyledDocument doc = chatArea.getStyledDocument();

                try {
                    // Style cho thông báo hệ thống
                    Style systemStyle = chatArea.addStyle("SystemStyle", null);
                    StyleConstants.setForeground(systemStyle, Color.GRAY);
                    StyleConstants.setItalic(systemStyle, true);

                    // Thêm thông báo
                    doc.insertString(doc.getLength(), "--- " + message + " ---\n", systemStyle);

                    // Cuộn xuống để hiển thị tin nhắn mới nhất
                    chatArea.setCaretPosition(doc.getLength());
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void addGroup(Group group) {
        SwingUtilities.invokeLater(() -> {
            // Kiểm tra xem nhóm đã có trong danh sách chưa
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
            // Xóa khỏi danh sách nhóm
            for (int i = 0; i < groupListModel.getSize(); i++) {
                if (groupListModel.getElementAt(i).equals(groupName)) {
                    groupListModel.removeElementAt(i);
                    break;
                }
            }

            // Xóa tab chat
            for (int i = 0; i < chatTabs.getTabCount(); i++) {
                if (chatTabs.getTitleAt(i).equals(groupName + " (G)")) {
                    chatTabs.remove(i);
                    break;
                }
            }

            // Xóa khỏi map
            groupChatPanels.remove(groupName);
        });
    }

    public void updateGroupMembers(Group group) {
        if (group == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            String groupName = group.getName();

            // Tìm tab chat của nhóm
            JTextPane chatArea = null;
            JScrollPane scrollPane = null;
            String tabTitle = groupName + " (G)";

            for (int i = 0; i < chatTabs.getTabCount(); i++) {
                if (chatTabs.getTitleAt(i).equals(tabTitle)) {
                    scrollPane = (JScrollPane) chatTabs.getComponentAt(i);
                    chatArea = (JTextPane) scrollPane.getViewport().getView();
                    break;
                }
            }

            if (chatArea == null) {
                return;
            }

            // Tạo panel hiển thị thành viên
            JPanel memberPanel = new JPanel();
            memberPanel.setLayout(new BoxLayout(memberPanel, BoxLayout.Y_AXIS));
            memberPanel.setBorder(BorderFactory.createTitledBorder("Thành viên"));

            // Danh sách thành viên
            JList<String> memberList = new JList<>(
                    group.getMembers().toArray(new String[0])
            );
            memberList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            // Thêm chức năng click chuột phải để mở menu ngữ cảnh
            memberList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);
                    if (SwingUtilities.isRightMouseButton(e)) {
                        int index = memberList.locationToIndex(e.getPoint());
                        if (index >= 0) {
                            memberList.setSelectedIndex(index);
                            String selectMember = memberList.getSelectedValue();

                            JPopupMenu popupMenu = new JPopupMenu();

                            // Tạo chat riêng
                            JMenuItem privateChatItem = new JMenuItem("Chat riêng với " + selectMember);
                            privateChatItem.addActionListener(new ActionListener() {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    createOrShowPrivateChat(selectMember);
                                }
                            });

                            popupMenu.add(privateChatItem);

                            // Đối với người tạo nhóm, có thể thêm các tùy chọn quản lý khác
                            if (client.getCurrentUser().getUsername().equals(group.getCreator())) {
                                JMenuItem removeItem = new JMenuItem("Xóa " + selectMember + " khỏi nhóm");
                                removeItem.addActionListener(new ActionListener() {
                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        client.removeFromGroup(groupName, selectMember);
                                    }
                                });

                                popupMenu.add(removeItem);
                            }

                            popupMenu.show(memberList, e.getX(), e.getY());
                        }
                    }
                }
            });

            // Thêm JList vảo JScrollPane để hỗ trợ cuộn
            JScrollPane memberScrollPane = new JScrollPane(memberList);
            memberScrollPane.setPreferredSize(new Dimension(150, 200));

            // Thêm vào panel
            memberPanel.add(memberScrollPane);

            // Thêm các nút tương tác
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

            // Nút thêm thành viên
            JButton addMemberButton = new JButton("Thêm thành viên");
            addMemberButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    showAddMemberDialog(groupName);
                }
            });

            // Nút rời nhóm
            JButton leaveGroupButton = new JButton("Rời nhóm");
            leaveGroupButton.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "Bạn có chắc muốn rời khỏi nhóm " + groupName + "?",
                        "Xác nhận rời nhóm",
                        JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    client.leaveGroup(groupName);
                }
            });

            buttonPanel.add(addMemberButton);
            buttonPanel.add(leaveGroupButton);

            memberPanel.add(buttonPanel);

            // Tìm hoặc tạo panel bên phải để chứa danh sách thành viên
            Component rightComp = null;
            if (scrollPane.getParent() instanceof JSplitPane) {
                JSplitPane splitPane = (JSplitPane) scrollPane.getParent();
                rightComp = splitPane.getRightComponent();
            }

            if (rightComp == null || !(rightComp instanceof JPanel)) {
                // Tạo SplitPane mới nếu chưa có
                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
                splitPane.setOneTouchExpandable(true);
                splitPane.setDividerLocation(0.7);

                // Thay thế tab hiện tại
                int tabIndex = chatTabs.indexOfComponent(scrollPane);
                if (tabIndex >= 0) {
                    splitPane.setLeftComponent(scrollPane);
                    splitPane.setRightComponent(memberPanel);
                    chatTabs.setComponentAt(tabIndex, splitPane);
                }
            } else {
                // Cập nhật panel thành viên hiện có
                JPanel existingPanel = (JPanel) rightComp;
                existingPanel.removeAll();
                existingPanel.add(memberPanel);
                existingPanel.revalidate();
                existingPanel.repaint();
            }
        });
    }

    private void showMemberDialog(String groupName) {
        Group group = client.getGroup(groupName);
        if (group == null) return;

        JDialog dialog = new JDialog(this, "Quản lý thành viên nhóm " + groupName, true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(350, 400);
        dialog.setLocationRelativeTo(this);

        // Panel danh sách thành viên
        JPanel memberPanel = new JPanel(new BorderLayout());
        DefaultListModel<String> memberListModel = new DefaultListModel<>();

        for (String member : group.getMembers()) {
            memberListModel.addElement(member);
        }

        JList<String> memberList = new JList<>(memberListModel);
        memberList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Thêm menu chuột phải cho thành viên
        memberList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = memberList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        memberList.setSelectedIndex(index);
                        String selectedMember = memberList.getSelectedValue();

                        JPopupMenu popupMenu = new JPopupMenu();

                        // Chat riêng
                        JMenuItem privateChatItem = new JMenuItem("Chat riêng với " + selectedMember);
                        privateChatItem.addActionListener(event -> {
                            createOrShowPrivateChat(selectedMember);
                            dialog.dispose();
                        });

                        popupMenu.add(privateChatItem);

                        // Nếu là admin (người tạo nhóm), hiển thị tùy chọn xóa thành viên
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
                }
            }
        });

        JScrollPane memberScrollPane = new JScrollPane(memberList);
        memberPanel.add(new JLabel("Thành viên (" + memberListModel.size() + "):"), BorderLayout.NORTH);
        memberPanel.add(memberScrollPane, BorderLayout.CENTER);

        // Panel nút thao tác
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

        // Thêm vào dialog
        dialog.add(memberPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private JTextPane getOrCreateChatArea(String name) {
        if (!chatPanels.containsKey(name)) {
            if (name.endsWith(" (G)")) {
                createOrShowGroupChat(name.substring(0, name.length() - 4));
            } else {
                createOrShowPrivateChat(name);
            }
        }

        return chatPanels.get(name);
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

    public void handleFileRequest(String fileId, String sender, String fileName, long fileSize) {
        // Chuyển qua tab file
        sidePanel.setSelectedIndex(2);

        // Hiển thị yêu cầu file bằng FileTransferPanel
        fileTransferPanel.handleFileRequest(fileId, sender, fileName, fileSize);
    }

    // Xử lý khi file sẵn sàng để tải về
//    public void handleFileReady(String fileId, String sender, String fileName, long fileSize) {
//        // Chuyển qua tab file
//        sidePanel.setSelectedIndex(2);
//
//        // Hiển thị file sẵn sàng để tải về
//        fileTransferPanel.handleFileReady(fileId);
//
//        // Hiển thị trong khu vực chat
//        createOrShowPrivateChat(sender);
//        displayFileMessage(sender, sender, fileName, fileSize, fileId, "Đã sẵn sàng để tải về");
//    }

    public void displayMessage(String chatContext, String sender, String message, long timestamp) {
        SwingUtilities.invokeLater(() -> {
            JTextPane chatArea;

            if (chatContext.equals("Global")) {
                chatArea = chatPanels.get("Global");
            } else {
                String tabName = chatContext;

                if (sender.equals(client.getCurrentUser().getUsername())) {
                    tabName = chatContext;
                } else {
                    tabName = sender;
                }

                chatArea = getOrCreateChatArea(tabName);
            }

            if (chatArea != null) {
                StyledDocument doc = chatArea.getStyledDocument();

                try {
                    // Format timestamp
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                    String timeString = "[" + sdf.format(new Date(timestamp)) + "] ";

                    Style timeStyle = chatArea.addStyle("TimeStyle", null);
                    StyleConstants.setForeground(timeStyle, Color.GRAY);
                    StyleConstants.setFontSize(timeStyle, 10);

                    Style senderStyle = chatArea.addStyle("SenderStyle", null);
                    if (sender.equals(client.getCurrentUser().getUsername())) {
                        StyleConstants.setForeground(senderStyle, new Color(0, 128, 0));
                        StyleConstants.setBold(senderStyle, true);
                    } else {
                        StyleConstants.setForeground(senderStyle, Color.BLUE);
                        StyleConstants.setBold(senderStyle, true);
                    }

                    Style messageStyle = chatArea.addStyle("MessageStyle", null);

                    doc.insertString(doc.getLength(), timeString, timeStyle);
                    doc.insertString(doc.getLength(), sender + ": ", senderStyle);
                    doc.insertString(doc.getLength(), message, messageStyle);
                    doc.insertString(doc.getLength(), "\n", null);

                    chatArea.setCaretPosition(doc.getLength());

                    if (!chatContext.equals("Global") && !chatContext.equals(getCurrentTabName())) {
                        highlightTab(chatContext);
                    }
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void displayFileMessageInGroup(String groupName, String sender, String fileName,
                                          long fileSize, String fileId, String status,
                                          String filePath, long timestamp) {
        SwingUtilities.invokeLater(() -> {
            JTextPane chatArea = groupChatPanels.get(groupName);

            if (chatArea == null) {
                createOrShowGroupChat(groupName);
                chatArea = groupChatPanels.get(groupName);
            }

            boolean found = false;
            StyledDocument doc = chatArea.getStyledDocument();

            // Tìm file component đã tồn tại
            for (int i = 0; i < doc.getLength(); i++) {
                Element elem = doc.getCharacterElement(i);
                AttributeSet attrs = elem.getAttributes();

                if (StyleConstants.getComponent(attrs) instanceof FileMessageComponent) {
                    FileMessageComponent comp = (FileMessageComponent) StyleConstants.getComponent(attrs);

                    if (comp.getFileId() != null && comp.getFileId().equals(fileId)) {
                        comp.updateStatus(status);
                        comp.setFilePath(filePath);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                FileMessageComponent fileComponent = new FileMessageComponent(
                        fileName, fileSize, fileId, sender, status, filePath, client);

                try {
                    Style style = chatArea.addStyle("FileStyle", null);
                    StyleConstants.setComponent(style, fileComponent);

                    // Format timestamp
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                    String timeString = "[" + sdf.format(new Date(timestamp)) + "] ";

                    Style timeStyle = chatArea.addStyle("TimeStyle", null);
                    StyleConstants.setForeground(timeStyle, Color.GRAY);
                    StyleConstants.setFontSize(timeStyle, 10);

                    Style senderStyle = chatArea.addStyle("SenderStyle", null);
                    if (sender.equals(client.getCurrentUser().getUsername())) {
                        StyleConstants.setForeground(senderStyle, new Color(0, 128, 0));
                    } else {
                        StyleConstants.setForeground(senderStyle, Color.BLUE);
                    }
                    StyleConstants.setBold(senderStyle, true);

                    doc.insertString(doc.getLength(), timeString, timeStyle);
                    doc.insertString(doc.getLength(), sender + ": \n", senderStyle);

                    // Thêm component file
                    doc.insertString(doc.getLength(), " ", style);
                    doc.insertString(doc.getLength(), "\n\n", null);

                    chatArea.setCaretPosition(doc.getLength());
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }

            // Highlight tab if needed
            String tabTitle = groupName + " (G)";
            if (!tabTitle.equals(getCurrentTabName())) {
                highlightTab(tabTitle);
            }
        });
    }

    public void displayFileMessage(String chatContext, String sender, String fileName,
                                   long fileSize, String fileId, String status, String filePath,
                                   long timestamp) {
        SwingUtilities.invokeLater(() -> {
            JTextPane chatArea = getOrCreateChatArea(chatContext);

            boolean found = false;
            StyledDocument doc = chatArea.getStyledDocument();

            // Tìm kiếm file component đã tồn tại
            for (int i = 0; i < doc.getLength(); i++) {
                Element elem = doc.getCharacterElement(i);
                AttributeSet attrs = elem.getAttributes();

                if (StyleConstants.getComponent(attrs) instanceof FileMessageComponent) {
                    FileMessageComponent comp = (FileMessageComponent) StyleConstants.getComponent(attrs);

                    if (comp.getFileId() != null && comp.getFileId().equals(fileId)) {
                        comp.updateStatus(status);
                        comp.setFilePath(filePath);
                        found = true;
                        break;
                    }
                }
            }

            // Nếu chưa có, tạo file component mới
            if (!found) {
                FileMessageComponent fileComponent = new FileMessageComponent(
                        fileName, fileSize, fileId, sender, status, filePath, client);

                try {
                    Style style = chatArea.addStyle("FileStyle", null);
                    StyleConstants.setComponent(style, fileComponent);

                    // Format timestamp
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                    String timeString = "[" + sdf.format(new Date(timestamp)) + "] ";

                    Style timeStyle = chatArea.addStyle("TimeStyle", null);
                    StyleConstants.setForeground(timeStyle, Color.GRAY);
                    StyleConstants.setFontSize(timeStyle, 10);

                    Style senderStyle = chatArea.addStyle("SenderStyle", null);
                    if (sender.equals(client.getCurrentUser().getUsername())) {
                        StyleConstants.setForeground(senderStyle, new Color(0, 128, 0));
                    } else {
                        StyleConstants.setForeground(senderStyle, Color.BLUE);
                    }
                    StyleConstants.setBold(senderStyle, true);

                    doc.insertString(doc.getLength(), timeString, timeStyle);
                    doc.insertString(doc.getLength(), sender + ": \n", senderStyle);

                    // Thêm component file
                    doc.insertString(doc.getLength(), " ", style);
                    doc.insertString(doc.getLength(), "\n\n", null);

                    chatArea.setCaretPosition(doc.getLength());
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }

            if (!sender.equals(client.getCurrentUser().getUsername()) &&
                    !chatContext.equals(getCurrentTabName())) {
                highlightTab(chatContext);
            }
        });
    }

    // Hiển thị tin nhắn file trong chat
    public void displayFileMessage(String chatContext, String sender, String fileName,
                                   long fileSize, String fileId, String status) {
        SwingUtilities.invokeLater(() -> {
            JTextPane chatArea = getOrCreateChatArea(chatContext);

            // In thông tin debug
            System.out.println("Displaying file message: chatContext=" + chatContext +
                    ", sender=" + sender + ", fileName=" + fileName +
                    ", fileId=" + fileId + ", status=" + status);

            // Kiểm tra xem đã có các component file trong chat chưa
            boolean found = false;
            FileMessageComponent fileComponent = null;

            // Tìm kiếm trong StyledDocument thay vì trong danh sách components
            StyledDocument doc = chatArea.getStyledDocument();
            for (int i = 0; i < doc.getLength(); i++) {
                Element elem = doc.getCharacterElement(i);
                AttributeSet attrs = elem.getAttributes();

                if (StyleConstants.getComponent(attrs) instanceof FileMessageComponent) {
                    FileMessageComponent comp = (FileMessageComponent) StyleConstants.getComponent(attrs);

                    if (comp.getFileId() != null && comp.getFileId().equals(fileId)) {
                        // Cập nhật component file đã tồn tại
                        comp.updateStatus(status);
                        found = true;
                        System.out.println("Updated existing file component status to: " + status);
                        break;
                    }
                }
            }

            // Nếu chưa có component file, tạo mới
            if (!found) {
                fileComponent = new FileMessageComponent(fileName, fileSize, fileId, sender, status, null, client);
                System.out.println("Created new file component with id=" + fileId);

                try {
                    // Thêm component vào chat area
                    Style style = chatArea.addStyle("FileStyle", null);
                    StyleConstants.setComponent(style, fileComponent);

                    // Thêm thời gian và người gửi
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                    String timeString = "[" + sdf.format(new Date()) + "] ";
                    doc.insertString(doc.getLength(), timeString + sender + ": \n", null);

                    // Thêm component file
                    doc.insertString(doc.getLength(), " ", style);
                    doc.insertString(doc.getLength(), "\n\n", null);

                    // Cuộn xuống để hiển thị tin nhắn mới nhất
                    chatArea.setCaretPosition(doc.getLength());
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }

            // Nếu đây là file từ người dùng khác và không phải là current tab,
            // thông báo có tin nhắn mới
            if (!sender.equals(client.getCurrentUser().getUsername()) &&
                    !chatContext.equals(getCurrentTabName())) {
                highlightTab(chatContext);
            }
        });
    }

    public void updateUploadProgressBar(String fileId, int progress) {
        SwingUtilities.invokeLater(() -> {
            fileTransferPanel.updateUploadProgress(fileId, progress);
        });
    }

    public void updateFileComponent(String fileId, String filePath) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("Updating file path for: " + fileId + " to: " + filePath);

            // Duyệt qua tất cả các tab chat
            for (Map.Entry<String, JTextPane> entry : chatPanels.entrySet()) {
                JTextPane chatArea = entry.getValue();
                StyledDocument doc = chatArea.getStyledDocument();

                // Duyệt qua tất cả các phần tử trong document
                for (int i = 0; i < doc.getLength(); i++) {
                    Element elem = doc.getCharacterElement(i);
                    AttributeSet attrs = elem.getAttributes();

                    if (StyleConstants.getComponent(attrs) instanceof FileMessageComponent) {
                        FileMessageComponent comp = (FileMessageComponent) StyleConstants.getComponent(attrs);

                        if (comp.getFileId() != null && comp.getFileId().equals(fileId)) {
                            // Cập nhật filePath
                            comp.setFilePath(filePath);
                            System.out.println("Found and updated file path for: " + fileId);
                            return;
                        }
                    }
                }
            }

            // Kiểm tra trong các tab nhóm nếu cần
            for (Map.Entry<String, JTextPane> entry : groupChatPanels.entrySet()) {
                // Tương tự code ở trên
            }
        });
    }

    public void updateFileStatus(String fileId, String status) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("DEBUG: Trying to update file status: fileId=" + fileId + ", status=" + status);
            boolean found = false;

            // Tìm qua tất cả các chatPanel
            for (Map.Entry<String, JTextPane> entry : chatPanels.entrySet()) {
                JTextPane chatArea = entry.getValue();

                // Tìm component trong StyledDocument thay vì trong JTextPane
                StyledDocument doc = chatArea.getStyledDocument();

                // Duyệt qua tất cả các vị trí trong document
                for (int i = 0; i < doc.getLength(); i++) {
                    Element elem = doc.getCharacterElement(i);
                    AttributeSet attrs = elem.getAttributes();

                    // Kiểm tra xem tại vị trí này có component nào không
                    if (StyleConstants.getComponent(attrs) instanceof FileMessageComponent) {
                        FileMessageComponent comp = (FileMessageComponent) StyleConstants.getComponent(attrs);

                        // Kiểm tra fileId
                        if (comp.getFileId() != null && comp.getFileId().equals(fileId)) {
                            // Cập nhật trạng thái
                            comp.updateStatus(status);
                            found = true;
                            System.out.println("Found and updated file component in tab: " + entry.getKey());
                            break;
                        }
                    }
                }

                if (found) break;
            }

            // Làm tương tự cho group chat panels nếu cần
            if (!found) {
                for (Map.Entry<String, JTextPane> entry : groupChatPanels.entrySet()) {
                    // [Tương tự code ở trên cho group chat panels]
                    JTextPane chatArea = entry.getValue();
                    StyledDocument doc = chatArea.getStyledDocument();

                    for (int i = 0; i < doc.getLength(); i++) {
                        Element elem = doc.getCharacterElement(i);
                        AttributeSet attrs = elem.getAttributes();

                        if (StyleConstants.getComponent(attrs) instanceof FileMessageComponent) {
                            FileMessageComponent comp = (FileMessageComponent) StyleConstants.getComponent(attrs);

                            if (comp.getFileId() != null && comp.getFileId().equals(fileId)) {
                                comp.updateStatus(status);
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
            System.out.println("DEBUG: Trying to update file status: fileId=" + fileId + ", filePath=" + filePath);
            boolean found = false;

            // Tìm qua tất cả các chatPanel
            for (Map.Entry<String, JTextPane> entry : chatPanels.entrySet()) {
                JTextPane chatArea = entry.getValue();

                // Tìm component trong StyledDocument thay vì trong JTextPane
                StyledDocument doc = chatArea.getStyledDocument();

                // Duyệt qua tất cả các vị trí trong document
                for (int i = 0; i < doc.getLength(); i++) {
                    Element elem = doc.getCharacterElement(i);
                    AttributeSet attrs = elem.getAttributes();

                    // Kiểm tra xem tại vị trí này có component nào không
                    if (StyleConstants.getComponent(attrs) instanceof FileMessageComponent) {
                        FileMessageComponent comp = (FileMessageComponent) StyleConstants.getComponent(attrs);

                        // Kiểm tra fileId
                        if (comp.getFileId() != null && comp.getFileId().equals(fileId)) {
                            // Cập nhật trạng thái
                            comp.setFilePath(filePath);
                            found = true;
                            System.out.println("Found and updated file component in tab: " + entry.getKey());
                            break;
                        }
                    }
                }

                if (found) break;
            }

            // Làm tương tự cho group chat panels nếu cần
            if (!found) {
                for (Map.Entry<String, JTextPane> entry : groupChatPanels.entrySet()) {
                    // [Tương tự code ở trên cho group chat panels]
                    JTextPane chatArea = entry.getValue();
                    StyledDocument doc = chatArea.getStyledDocument();

                    for (int i = 0; i < doc.getLength(); i++) {
                        Element elem = doc.getCharacterElement(i);
                        AttributeSet attrs = elem.getAttributes();

                        if (StyleConstants.getComponent(attrs) instanceof FileMessageComponent) {
                            FileMessageComponent comp = (FileMessageComponent) StyleConstants.getComponent(attrs);

                            if (comp.getFileId() != null && comp.getFileId().equals(fileId)) {
                                comp.setFilePath(filePath);
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

//    public void ensureFileMessageExists(String chatContext, String sender, String fileName,
//                                        long fileSize, String fileId, String status) {
//        SwingUtilities.invokeLater(() -> {
//            JTextPane chatArea = getOrCreateChatArea(chatContext);
//            boolean found = false;
//
//            Component[] components = chatArea.getComponents();
//            for (Component comp : components) {
//                if (comp instanceof FileMessageComponent &&
//                        ((FileMessageComponent) comp).getFileId().equals(fileId)) {
//                    found = true;
//                    ((FileMessageComponent) comp).updateStatus(status);
//                    break;
//                }
//            }
//
//            if (!found) {
//                // Nếu không tìm thấy, tạo mới component
//                displayFileMessage(chatContext, sender, fileName, fileSize, fileId, status);
//            }
//        });
//    }

    // Cập nhật tiến trình upload file
    public void updateFileUploadProgress(String fileId, int progress) {
        if (progress >= 100) {
            updateFileStatus(fileId, "Đã gửi thành công");
        } else {
            updateFileStatus(fileId, "Đang gửi... " + progress + "%");
        }
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
            client.sendMessage(message);
        } else if (tabName.endsWith(" (G)")) {
            String groupName = tabName.substring(0, tabName.length() - 4);
            client.sendGroupMessage(groupName, message);
        } else {
            client.sendPrivateMessage(tabName, message);
        }

        messageInput.setText("");
        messageInput.requestFocusInWindow();
    }

    public void displayMessage(String chatContext, String sender, String message) {
        JTextPane chatArea;

        if (chatContext.equals("Global")) {
            chatArea = chatPanels.get("Global");
        } else {
            String tabName = chatContext;

            if (sender.equals(client.getCurrentUser().getUsername())) {
                tabName = chatContext;
            } else {
                tabName = sender;
            }

            chatArea = getOrCreateChatArea(tabName);
        }

        if (chatArea != null) {
            StyledDocument doc = chatArea.getStyledDocument();

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                String timeString = "[" + sdf.format(new Date()) + "] ";

                Style timeStyle = chatArea.addStyle("TimeStyle", null);
                StyleConstants.setForeground(timeStyle, Color.GRAY);
                StyleConstants.setFontSize(timeStyle, 10);

                Style senderStyle = chatArea.addStyle("SenderStyle", null);
                if (sender.equals(client.getCurrentUser().getUsername())) {
                    StyleConstants.setForeground(senderStyle, new Color(0, 128, 0));
                    StyleConstants.setBold(senderStyle, true);
                } else {
                    StyleConstants.setForeground(senderStyle, Color.BLUE);
                    StyleConstants.setBold(senderStyle, true);
                }

                Style messageStyle = chatArea.addStyle("MessageStyle", null);

                doc.insertString(doc.getLength(), timeString, timeStyle);

                doc.insertString(doc.getLength(), sender + ": ", senderStyle);

                doc.insertString(doc.getLength(), message, messageStyle);
                doc.insertString(doc.getLength(), "\n", null);

                chatArea.setCaretPosition(doc.getLength());

                if (!chatContext.equals("Global") && !chatContext.equals(getCurrentTabName())) {
                    highlightTab(chatContext);
                }
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
    }

    public void displayGlobalMessage(String sender, String message) {
        displayMessage("Global", sender, message);
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
        // Xóa đánh dấu tin nhắn mới
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
            JTextPane chatArea = getOrCreateChatArea(chatContext);

            if (chatArea != null) {
                // Xóa nội dung hiện có (nếu có)
                chatArea.setText("");

                StyledDocument doc = chatArea.getStyledDocument();

                try {
                    for (String[] messageParts : messages) {
                        System.out.println("Processing message: " + String.join(", ", messageParts));
                        // Text message
                        if (messageParts.length == 4) {
                            String sender = messageParts[0];
                            String content = messageParts[1];
                            long timestamp = Long.parseLong(messageParts[2]);

                            // Format thời gian
                            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
                            String timeString = "[" + sdf.format(new Date(timestamp)) + "] ";

                            // Style cho thời gian
                            Style timeStyle = chatArea.addStyle("TimeStyle", null);
                            StyleConstants.setForeground(timeStyle, Color.GRAY);
                            StyleConstants.setFontSize(timeStyle, 10);

                            // Style cho người gửi
                            Style senderStyle = chatArea.addStyle("SenderStyle", null);
                            if (sender.equals(client.getCurrentUser().getUsername())) {
                                StyleConstants.setForeground(senderStyle, new Color(0, 128, 0));
                                StyleConstants.setBold(senderStyle, true);
                            } else {
                                StyleConstants.setForeground(senderStyle, Color.BLUE);
                                StyleConstants.setBold(senderStyle, true);
                            }

                            // Style cho nội dung tin nhắn
                            Style messageStyle = chatArea.addStyle("MessageStyle", null);

                            // Thêm thời gian
                            doc.insertString(doc.getLength(), timeString, timeStyle);

                            // Thêm người gửi
                            doc.insertString(doc.getLength(), sender + ": ", senderStyle);

                            // Thêm nội dung tin nhắn
                            doc.insertString(doc.getLength(), content, messageStyle);
                            doc.insertString(doc.getLength(), "\n", null);
                        }
                        else if (messageParts.length == 8) {
                            // File message
                            String sender = messageParts[0];
                            String fileId = messageParts[1];
                            String fileName = messageParts[2];
                            long fileSize = Long.parseLong(messageParts[3]);
                            long timestamp = Long.parseLong(messageParts[4]);
                            String actualFileNameSave = messageParts[5];
                            String actualFileNameUpload = messageParts[6];
                            String messageType = messageParts[7];

                            String filePath;

                            if (sender.equals(client.getCurrentUser().getUsername())) {
                                filePath = ChatClient.defaultUploadFolder +
                                        File.separator + actualFileNameUpload;
                            } else {
                                filePath = ChatClient.defaultDownloadFolder +
                                        File.separator + actualFileNameSave;
                            }

                            // Tạo component file
                            FileMessageComponent fileComponent = new FileMessageComponent(
                                    fileName, fileSize, fileId, sender, "thành công", filePath, client);

                            Style style = chatArea.addStyle("FileStyle", null);
                            StyleConstants.setComponent(style, fileComponent);

                            // Thêm thời gian và người gửi
                            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                            String timeString = "[" + sdf.format(timestamp) + "] ";
                            doc.insertString(doc.getLength(), timeString + sender + ": \n", null);

                            // Thêm component file
                            doc.insertString(doc.getLength(), " ", style);
                            doc.insertString(doc.getLength(), "\n\n", null);

                            // Cuộn xuống để hiển thị tin nhắn mới nhất
                            chatArea.setCaretPosition(doc.getLength());
                        }

                    }

                    // Cuộn xuống để hiển thị tin nhắn mới nhất
                    chatArea.setCaretPosition(doc.getLength());

                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void displayOlderMessages(String chatContext, java.util.List<String[]> olderMessages) {
        SwingUtilities.invokeLater(() -> {
            JTextPane chatArea = getOrCreateChatArea(chatContext);

            if (chatArea != null) {
                StyledDocument doc = chatArea.getStyledDocument();

                try {
                    // Lưu vị trí cuộn hiện tại
                    JScrollPane scrollPane = getScrollPaneForChatArea(chatArea);
                    JViewport viewport = scrollPane.getViewport();
                    Point viewPosition = viewport.getViewPosition();
                    int oldDocLength = doc.getLength();

                    // Tạo style cho tin nhắn cũ
                    Style timeStyle = chatArea.addStyle("TimeStyle", null);
                    StyleConstants.setForeground(timeStyle, Color.GRAY);
                    StyleConstants.setFontSize(timeStyle, 10);

                    // Thêm tin nhắn cũ vào đầu document
                    StringBuilder insertText = new StringBuilder();

                    for (String[] messageParts : olderMessages) {
                        String sender = messageParts[0];
                        String content = messageParts[1];
                        long timestamp = Long.parseLong(messageParts[2]);

                        // Format thời gian
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
                        String timeString = "[" + sdf.format(new Date(timestamp)) + "] ";

                        // Tạo nội dung tin nhắn
                        String messageText = timeString + sender + ": " + content + "\n";
                        insertText.append(messageText);
                    }

                    // Thêm vào đầu document
                    doc.insertString(0, insertText.toString(), null);

                    // Áp dụng styles cho từng phần (đơn giản hóa việc xử lý styles)
                    // Trong thực tế, bạn sẽ muốn áp dụng style cho từng phần riêng biệt

                    // Điều chỉnh vị trí cuộn
                    int newDocLength = doc.getLength();
                    int diff = newDocLength - oldDocLength;
                    viewPosition.y += diff;
                    viewport.setViewPosition(viewPosition);

                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void addLoadMoreButton(String chatContext) {
        JTextPane chatArea = chatPanels.get(chatContext);
        if (chatArea == null) return;

        JScrollPane scrollPane = null;
        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            String title = chatTabs.getTitleAt(i);
            if (title.equals(chatContext) ||
                    (chatContext.endsWith(" (G)") && title.equals(chatContext)) ||
                    (!chatContext.endsWith(" (G)") && title.equals(chatContext + " (G)"))) {
                scrollPane = (JScrollPane) chatTabs.getComponentAt(i);
                break;
            }
        }

        if (scrollPane != null) {
            // Xóa nút cũ nếu có
            if (scrollPane.getColumnHeader() != null) {
                Component[] comps = scrollPane.getColumnHeader().getComponents();
                for (Component comp : comps) {
                    if (comp instanceof JButton && ((JButton) comp).getText().equals("Tải thêm")) {
                        scrollPane.getColumnHeader().remove(comp);
                    }
                }
            }

            // Tạo nút mới
            JButton loadMoreButton = new JButton("Tải thêm");
            loadMoreButton.addActionListener(e -> {
                String context = chatContext;
                boolean isGroup = false;

                // Kiểm tra xem đây có phải là chat nhóm không
                if (context.endsWith(" (G)")) {
                    context = context.substring(0, context.length() - 4);
                    isGroup = true;
                }

                client.loadMoreMessages(context, isGroup);
            });

            // Thêm vào phần header của JScrollPane
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.add(loadMoreButton, BorderLayout.CENTER);
            scrollPane.setColumnHeaderView(headerPanel);

            scrollPane.revalidate();
            scrollPane.repaint();
        }
    }

    private JScrollPane getScrollPaneForChatArea(JTextPane chatArea) {
        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            Component comp = chatTabs.getComponentAt(i);
            if (comp instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) comp;
                if (scrollPane.getViewport().getView() == chatArea) {
                    return scrollPane;
                }
            }
        }
        return null;
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

    public void updateGroupList(java.util.List<Group> groups) {
        SwingUtilities.invokeLater(() -> {
            groupListModel.clear();
            for (Group group : groups) {
                groupListModel.addElement(group.getName());
            }
        });
    }

    private void showAddMemberDialog(String groupName) {
        // Lấy danh sách người dùng đang online để thêm vào nhóm
        DefaultListModel<String> onlineUsers = userListModel;

        // Lọc những người dùng chưa có trong nhóm
        Group group = client.getGroup(groupName);
        java.util.List<String> members = group.getMembers();

        DefaultListModel<String> availableUsers = new DefaultListModel<>();
        for (int i = 0; i < onlineUsers.size(); i++) {
            String user = onlineUsers.getElementAt(i);
            if (!members.contains(user) && !user.equals(client.getCurrentUser().getUsername())) {
                availableUsers.addElement(user);
            }
        }

        // Nếu không có ai để thêm
        if (availableUsers.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Không có người dùng nào khả dụng để thêm vào nhóm.",
                    "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Tạo dialog
        JDialog dialog = new JDialog(this, "Thêm thành viên vào nhóm " + groupName, true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(300, 300);
        dialog.setLocationRelativeTo(this);

        // Tạo danh sách người dùng
        JList<String> userList = new JList<>(availableUsers);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane listScrollPane = new JScrollPane(userList);

        // Tạo panel nút
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addButton = new JButton("Thêm");
        JButton cancelButton = new JButton("Hủy");

        // Xử lý sự kiện
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

        // Thêm vào dialog
        dialog.add(new JLabel("  Chọn người dùng để thêm vào nhóm:"), BorderLayout.NORTH);
        dialog.add(listScrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void createGlobalChatTab() {
        JTextPane chatArea = new JTextPane();
        chatArea.setEditable(false);

        // Đặt ContentType để hỗ trợ các style và component
        chatArea.setContentType("text/html");

        JScrollPane scrollPane = new JScrollPane(chatArea);

        chatPanels.put("Global", chatArea);
        chatTabs.addTab("Global", scrollPane);
    }

    public void showNotification(String message) {
        // Hiển thị thông báo ở góc phải dưới màn hình
        JWindow notification = new JWindow();
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        panel.setBackground(new Color(255, 255, 225));

        JLabel label = new JLabel(message);
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(label);

        notification.add(panel);
        notification.pack();

        // Đặt vị trí ở góc phải dưới màn hình
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
