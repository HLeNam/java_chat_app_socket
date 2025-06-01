package util;

public class Protocol {
    public static final String CMD_LOGIN = "/login ";
    public static final String CMD_REGISTER = "/register ";
    public static final String CMD_PRIVATE_MSG = "/private ";
    public static final String CMD_ONLINE_USERS = "/online";
    public static final String CMD_EXIT = "/exit";
    public static final String CMD_FILE_SEND = "/filesend ";
    public static final String CMD_FILE_ACCEPT = "/fileaccept ";
    public static final String CMD_FILE_REJECT = "/filereject ";
    public static final String CMD_CREATE_GROUP = "/creategroup ";
    public static final String CMD_ADD_TO_GROUP = "/addtogroup ";
    public static final String CMD_LEAVE_GROUP = "/leavegroup ";
    public static final String CMD_GET_GROUPS = "/getgroups";
    public static final String CMD_GROUP_MSG = "/groupmsg ";
    public static final String CMD_REMOVE_FROM_GROUP = "/removefromgroup ";

    public static final String SVR_LOGIN_SUCCESS = "/loginsuccess";
    public static final String SVR_LOGIN_FAIL = "/loginfail ";
    public static final String SVR_REGISTER_SUCCESS = "/registersuccess";
    public static final String SVR_REGISTER_FAIL = "/registerfail ";
    public static final String SVR_ONLINE_USERS = "/onlineusers ";
    public static final String SVR_USER_JOINED = "/userjoined ";
    public static final String SVR_USER_LEFT = "/userleft ";
    public static final String SVR_PRIVATE_MSG = "/privatemsg ";
    public static final String SVR_FILE_REQUEST = "/filerequest ";
    public static final String SVR_FILE_ACCEPT = "/fileaccepted ";
    public static final String SVR_FILE_REJECT = "/filerejected ";
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

    public static final String CMD_GET_CHAT_HISTORY = "/history ";
    public static final String SVR_CHAT_HISTORY_START = "/historystart";
    public static final String SVR_CHAT_HISTORY_END = "/historyend";

    public static final String CMD_LOAD_MORE_MESSAGES = "/loadmore ";
    public static final String SVR_LOAD_MORE_START = "/loadmorestart";
    public static final String SVR_LOAD_MORE_END = "/loadmoreend";

    public static final String CMD_FILE_DOWNLOAD = "/filedownload ";
    public static final String SVR_FILE_DOWNLOAD = "/filedownload ";

    public static final String SVR_GROUP_FILE_REQUEST = "/groupfilerequest ";
    public static final String CMD_GROUP_FILE_SEND = "/groupfilesend ";

    public static final String SVR_CHAT_HISTORY_ITEM = "/historyitem ";

    public static final String SVR_LOAD_MORE_ITEM = "/loadmoreitem ";

    public static final String CMD_CHANGE_MESSAGE_ACTUAL_FILENAME_SAVE = "/changeactualfilenamesave ";
    public static final String CMD_CHANGE_MESSAGE_ACTUAL_FILENAME_UPLOAD = "/changeactualfilenameupload ";

    public static final String CMD_GROUP_FILE_ACCEPT = "/groupfileaccept ";
    public static final String SVR_GROUP_FILE_ACCEPT = "/groupfileaccepted ";

    public static final String CMD_CHANGE_MESSAGE_GROUP_ACTUAL_FILENAME_SAVE = "/changegroupactualfilenamesave ";
    public static final String CMD_CHANGE_MESSAGE_GROUP_ACTUAL_FILENAME_UPLOAD = "/changegroupactualfilenameupload ";

    public static final String SVR_FILE_DOWNLOAD_REQUEST = "/filedownloadrequest ";
    public static final String CMD_FILE_DOWNLOAD_ACCEPT = "/filedownloadaccept ";
    public static final String SVR_FILE_DOWNLOAD_ACCEPT = "/filedownloadaccepted ";

    public static final String CMD_DELETE_MESSAGE = "/deletemsg ";
    public static final String SVR_MESSAGE_DELETED = "/msgdeleted ";

    public static final String CMD_VOICE_CALL_REQUEST = "/voicecall_request|";
    public static final String CMD_VOICE_CALL_ACCEPT = "/voicecall_accept|";
    public static final String CMD_VOICE_CALL_REJECT = "/voicecall_reject|";
    public static final String CMD_VOICE_CALL_END = "/voicecall_end|";

    public static final String SVR_VOICE_CALL_REQUEST = "/svr_voicecall_request|";
    public static final String SVR_VOICE_CALL_ACCEPT = "/svr_voicecall_accept|";
    public static final String SVR_VOICE_CALL_REJECT = "/svr_voicecall_reject|";
    public static final String SVR_VOICE_CALL_END = "/svr_voicecall_end|";

    public static final String SVR_INFO = "/svr_info|";

    public static final String CMD_VIDEO_CALL_REQUEST = "/video_call_request|";
    public static final String CMD_VIDEO_CALL_ACCEPT = "/video_call_accept|";
    public static final String CMD_VIDEO_CALL_REJECT = "/video_call_reject|";
    public static final String CMD_VIDEO_CALL_END = "/video_call_end|";

    public static final String SVR_VIDEO_CALL_REQUEST = "/svr_video_call_request|";
    public static final String SVR_VIDEO_CALL_ACCEPT = "/svr_video_call_accept|";
    public static final String SVR_VIDEO_CALL_REJECT = "/svr_video_call_reject|";
    public static final String SVR_VIDEO_CALL_END = "/svr_video_call_end|";

    public static final String CMD_TOGGLE_VIDEO = "/toggle_video|";
    public static final String SVR_VIDEO_TOGGLED = "/svr_video_toggled|";

    public static final String PARAM_DELIMITER = "|";
}
