package com.huanghy7588.xiaqiaoqiaogongjvxiang.update;

import org.json.JSONObject;

/**
 * 更新与公告数据模型。
 *
 * JSON 结构示例：
 * {
 *   "announcement": {
 *     "title": "公告标题",
 *     "content": "公告正文内容",
 *     "enabled": true
 *   },
 *   "update": {
 *     "versionCode": 2,
 *     "versionName": "1.0.1",
 *     "updateLog": "1. 修复xxx\n2. 新增yyy",
 *     "downloadUrl": "https://.../app-release.apk",
 *     "forceUpdate": false
 *   }
 * }
 */
public class UpdateModel {

    public Announcement announcement;
    public UpdateInfo update;

    /** 公告 */
    public static class Announcement {
        public String title;
        public String content;
        public boolean enabled;

        public static Announcement parse(JSONObject obj) {
            if (obj == null) return null;
            Announcement a = new Announcement();
            a.title = obj.optString("title", "");
            a.content = obj.optString("content", "");
            a.enabled = obj.optBoolean("enabled", false);
            return a;
        }
    }

    /** 更新信息 */
    public static class UpdateInfo {
        public int versionCode;
        public String versionName;
        public String updateLog;
        public String downloadUrl;
        public boolean forceUpdate;

        public static UpdateInfo parse(JSONObject obj) {
            if (obj == null) return null;
            UpdateInfo u = new UpdateInfo();
            u.versionCode = obj.optInt("versionCode", 0);
            u.versionName = obj.optString("versionName", "");
            u.updateLog = obj.optString("updateLog", "");
            u.downloadUrl = obj.optString("downloadUrl", "");
            u.forceUpdate = obj.optBoolean("forceUpdate", false);
            return u;
        }
    }

    /** 从 JSON 字符串解析 */
    public static UpdateModel parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            UpdateModel model = new UpdateModel();
            model.announcement = Announcement.parse(root.optJSONObject("announcement"));
            model.update = UpdateInfo.parse(root.optJSONObject("update"));
            return model;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
