package util;

public class Protocol {
    // Command prefixes
    public static final String CMD_LOGIN = "/login ";
    public static final String CMD_REGISTER = "/register ";
    public static final String CMD_PRIVATE_MSG = "/private ";
    public static final String CMD_ONLINE_USERS = "/online";
    public static final String CMD_EXIT = "/exit";
    public static final String CMD_FILE_SEND = "/filesend ";
    public static final String CMD_FILE_ACCEPT = "/fileaccept ";
    public static final String CMD_FILE_REJECT = "/filereject ";
    public static final String CMD_HISTORY = "/history ";
    public static final String CMD_DELETE_HISTORY = "/deletehistory ";
    public static final String CMD_CREATE_GROUP = "/creategroup ";
    public static final String CMD_ADD_TO_GROUP = "/addtogroup ";
    public static final String CMD_LEAVE_GROUP = "/leavegroup ";
    public static final String CMD_GET_GROUPS = "/getgroups";
    public static final String CMD_GROUP_MSG = "/groupmsg ";
    public static final String CMD_REMOVE_FROM_GROUP = "/removefromgroup ";

    // Server response prefixes
    public static final String SVR_LOGIN_SUCCESS = "/loginsuccess";
    public static final String SVR_LOGIN_FAIL = "/loginfail ";
    public static final String SVR_REGISTER_SUCCESS = "/registersuccess";
    public static final String SVR_REGISTER_FAIL = "/registerfail ";
    public static final String SVR_ONLINE_USERS = "/onlineusers ";
    public static final String SVR_USER_JOINED = "/userjoined ";
    public static final String SVR_USER_LEFT = "/userleft ";
    public static final String SVR_PRIVATE_MSG = "/privatemsg ";
    public static final String SVR_GLOBAL_MSG = "/globalmsg ";
    public static final String SVR_FILE_REQUEST = "/filerequest ";
    public static final String SVR_FILE_ACCEPT = "/fileaccepted ";
    public static final String SVR_FILE_REJECT = "/filerejected ";
    public static final String SVR_HISTORY_START = "/historystart";
    public static final String SVR_HISTORY_ITEM = "/historyitem ";
    public static final String SVR_HISTORY_END = "/historyend";
    public static final String SVR_HISTORY_DELETED = "/historydeleted ";
    public static final String SVR_ERROR = "/error ";
    public static final String SVR_CREATE_GROUP_SUCCESS = "/creategroupsuccess ";
    public static final String SVR_NEW_GROUP = "/newgroup ";
    public static final String SVR_GROUP_USER_ADDED = "/groupuseradded ";
    public static final String SVR_ADDED_TO_GROUP = "/addedtogroup ";
    public static final String SVR_ADD_TO_GROUP_SUCCESS = "/addtogroupsuccess ";
    public static final String SVR_GROUP_USER_LEFT = "/groupuserleft ";
    public static final String SVR_LEFT_GROUP = "/leftgroup ";
    public static final String SVR_GROUP_LIST = "/grouplist ";
    public static final String SVR_GROUP_MSG = "/groupmsg ";
    public static final String SVR_REMOVED_FROM_GROUP = "/removedfromgroup ";
    public static final String SVR_REMOVE_FROM_GROUP_SUCCESS = "/removefromgroupsuccess ";

    // Lệnh lấy lịch sử chat khi mở tab
    public static final String CMD_GET_CHAT_HISTORY = "/history ";
    public static final String SVR_CHAT_HISTORY_START = "/historystart";
    public static final String SVR_CHAT_HISTORY_ITEM = "/historyitem ";
    public static final String SVR_CHAT_HISTORY_END = "/historyend";

    // Lệnh tải thêm tin nhắn cũ hơn
    public static final String CMD_LOAD_MORE_MESSAGES = "/loadmore ";
    public static final String SVR_LOAD_MORE_START = "/loadmorestart";
    public static final String SVR_LOAD_MORE_ITEM = "/loadmoreitem ";
    public static final String SVR_LOAD_MORE_END = "/loadmoreend";

    public static final String SVR_FILE_MESSAGE = "/filemsg ";
    public static final String CMD_FILE_DOWNLOAD = "/filedownload ";
    public static final String SVR_FILE_DOWNLOAD = "/filedownload ";

    // File trong group chat
    public static final String SVR_GROUP_FILE_REQUEST = "/groupfilerequest ";
    public static final String SVR_GROUP_FILE_ACCEPTED = "/groupfileaccepted ";
    public static final String CMD_GROUP_FILE_SEND = "/groupfilesend ";

    // Cập nhật định dạng lịch sử chat với timestamp
    // messageType|sender|content|timestamp hoặc messageType|sender|fileId|fileName|fileSize|timestamp

    // Cập nhật định dạng tải thêm tin nhắn
    // messageType|sender|content|timestamp hoặc messageType|sender|fileId|fileName|fileSize|timestamp

    public static final String CMD_CHANGE_MESSAGE_ACTUAL_FILENAME_SAVE = "/changeactualfilenamesave ";
    public static final String CMD_CHANGE_MESSAGE_ACTUAL_FILENAME_UPLOAD = "/changeactualfilenameupload ";

    public static final String CMD_GROUP_FILE_ACCEPT = "/groupfileaccept ";
    public static final String SVR_GROUP_FILE_ACCEPT = "/groupfileaccepted ";

    public static final String CMD_CHANGE_MESSAGE_GROUP_ACTUAL_FILENAME_SAVE = "/changegroupactualfilenamesave ";
    public static final String CMD_CHANGE_MESSAGE_GROUP_ACTUAL_FILENAME_UPLOAD = "/changegroupactualfilenameupload ";

    public static final String SVR_FILE_DOWNLOAD_REQUEST = "/filedownloadrequest ";
    public static final String CMD_FILE_DOWNLOAD_ACCEPT = "/filedownloadaccept ";
    public static final String SVR_FILE_DOWNLOAD_ACCEPT = "/filedownloadaccepted ";

    // Delimiter cho các tham số trong message
    public static final String PARAM_DELIMITER = "|";
}
