package com.huanghy7588.xiaqiaoqiaogongjvxiang.wuzhong;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 无中生有表格：单个人物（一列）的数据模型。
 * 同时负责把选择结果计算成表格行（label + 单元格内容），供 TableRenderer 绘制。
 * 实现 Serializable 以支持 onSaveInstanceState 恢复（W5）。
 */
public class PersonData implements Serializable {

    /** assets 图片文件夹 */
    public static final String F_PROFICIENCY = "photo/proficiency";
    public static final String F_BADGE = "photo/badge";
    public static final String F_PEAK_FRAME = "photo/peak_frame";
    public static final String F_NOBLE10 = "photo/noble10";
    public static final String F_PASS_DOWN = "photo/pass_down";
    public static final String F_ROLE_TIER = "photo/role_tier";
    public static final String F_LANE_RANKING = "photo/lane_ranking";
    public static final String F_TRANSLATION_LADDER = "photo/translation_ladder";
    public static final String F_SKILL = "photo/skill";

    /** 模式 */
    public static final int MODE_RANK = 0; // 排位
    public static final int MODE_FUN = 1;  // 娱乐

    /** ① 蓝方/红方 */
    public int side = -1; // 0 蓝方, 1 红方

    /** ② 英雄色卡、皮肤色卡（各自独立选颜色） */
    public int heroColor = -1; // 0 队伍色(红方=红/蓝方=蓝), 1 金色
    public int skinColor = -1; // 0 队伍色(红方=红/蓝方=蓝), 1 金色

    /** ③ 英雄、皮肤输入 */
    public String hero = "";
    public String skin = "";
    public String idName = "";
    public int idBar = -1; // -1 未选, 0 无, 1 有（仅第一个选"有"后其他人不可选）

    /** ③ 熟练度/标 */
    public int profMode = -1; // 0 熟练度, 1 标
    public String profImage = null; // assets 路径（熟练度图片）
    public int badgeGlow = -1; // 0 带光效, 1 不带光效
    public int badgeLevel = -1; // 0 区标,1 市,2 省,3 小国标,4 国标
    public String badgeNum = ""; // 50强/100强/数字

    /** ④ 框 */
    public int frameType = -1; // 0 段位框, 1 巅峰框
    public int rankFrame = -1; // 0 青铜(无框)..9 百星
    public int rankFrameGlow = -1; // 百星专属：0 带光效,1 不带光效
    public int frameBadge = -1; // -1 未选, 0 是(有角标), 1 否(无角标)
    public int frameBadgeType = -1; // 0 天梯排名, 1 巅峰角标（frameBadge==0 时）
    public String ladderRank = ""; // 天梯排名文本
    public int ladderImage = -1; // 天梯排名图片索引（0-based，对应 photo/translation_ladder/1.jpg）
    public int peakBadgeVer = -1; // 0 新版, 1 旧版（巅峰角标）
    public String peakBadgeImage = null; // 巅峰角标图片（assets 路径）
    public String peakNumber = ""; // 巅峰数字（巅峰框）
    public String peakFrameImage = null; // assets 路径（巅峰框图片）

    /** ⑤ 贵族标 */
    public int nobleLevel = -1; // 0..9 = V1..V10, 10 = 传承标
    public int nobleGlow = -1; // 0 带光效,1 不带光效（V7-V10 / 传承标）
    public String noble10Image = null; // V10 图片
    public String passDownImage = null; // 传承标图片

    /** ⑦ 亲密关系 */
    public int relationType = -1; // 0 恋人..8 开黑挚友
    public String relationLevel = "";

    /** ⑧ 召唤师技能：图片选择（photo/skill），另保留"其他"文字 */
    public String summonerImage = null; // 技能图片 assets 路径
    public boolean summonerOtherMode = false; // 是否选择了"其他"
    public String summonerOther = "";

    /** ⑨ 加载进度（娱乐模式） */
    public int loadMode = -1; // 0 100%, 1 其他进度
    public String loadText = "";

    /** ⑩ 分路（排位模式） */
    public int laneMode = -1; // 0 分路排名, 1 分路段数
    public int laneRankImage = -1; // Lane Ranking 图片索引
    public int laneTier = -1; // 0 对抗..4 射手
    public int laneTierImage = -1; // Role Tier 图片索引

    /** 选填项（每个人物独立一份） */
    public final OptionalState opt = new OptionalState();

    public static class OptionalState implements Serializable {
        public boolean memoryStone; // 需要铭记之石
        public boolean heroSign;    // 需要英雄签名
        public boolean profBadge;   // 需要职业标
        public boolean nationalBadge; // 需要全国标
    }

    /** 单元格内容 */
    public static class Cell {
        public String text;         // 文字（可为 null）
        public String imageAsset;   // assets 图片路径（可为 null）
        public Integer textColor;   // 文字颜色（null = 默认黑色）
        public String text2;        // 第二行文字（可为 null，用于"英雄/皮肤"合并行）
        public Integer text2Color;  // 第二行文字颜色
        public Cell(String t, String img) { text = t; imageAsset = img; }
        public Cell(String t, String img, Integer color) { text = t; imageAsset = img; textColor = color; }
        public Cell(String t, String img, Integer color, String t2, Integer c2) {
            text = t; imageAsset = img; textColor = color; text2 = t2; text2Color = c2;
        }
    }

    /** 表格行 */
    public static class Row {
        public String label;
        public Cell cell;
        public Row(String l, Cell c) { label = l; cell = c; }
    }

    /** 标签常量（与表单保持一致） */
    public static final String[] BADGE_LEVEL = {"区标", "市标", "省标", "小国标", "国标"};
    public static final String[] RANK_FRAME = {"青铜(无框)", "白银", "黄金", "铂金", "钻石",
            "星耀", "王者", "无双", "荣耀", "百星"};
    public static final String[] NOBLE = {"V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9", "V10", "传承标"};
    public static final String[] RELATION = {"恋人", "闺蜜", "姐妹", "基友", "兄弟", "兄妹", "姐弟", "找搭子", "开黑挚友"};
    public static final String[] LANE_TIER = {"对抗", "打野", "中路", "辅助", "射手"};

    private String s(int v, String[] arr) {
        return (v >= 0 && v < arr.length) ? arr[v] : "";
    }

    /** 英雄/皮肤颜色常量 */
    public static final int COLOR_RED = 0xFFD32F2F;   // 红方队伍色
    public static final int COLOR_BLUE = 0xFF1565C0;   // 蓝方队伍色
    public static final int COLOR_GOLD = 0xFFFF8F00;   // 金色

    /** 色卡颜色：0=队伍色(红/蓝)，1=金色，未选=黑色 */
    private Integer colorOf(int color) {
        if (color == 1) return COLOR_GOLD;
        if (color == 0) return side == 1 ? COLOR_RED : COLOR_BLUE;
        return null;
    }

    /** 英雄/皮肤合并单元格：第一行英雄名（英雄色卡着色），第二行皮肤名（皮肤色卡着色） */
    private Cell heroSkinCell() {
        String h = hero == null ? "" : hero.trim();
        String s = skin == null ? "" : skin.trim();
        Cell c = new Cell(h.isEmpty() ? null : h, null, colorOf(heroColor));
        if (!s.isEmpty()) {
            c.text2 = s;
            c.text2Color = colorOf(skinColor);
        }
        return c;
    }

    /** 选填项标签（固定顺序，供渲染器做行并集） */
    public static final String[] OPTIONAL_LABELS = {"铭记之石", "英雄签名", "职业标", "全国标"};

    /** 计算该人物在指定模式下的表格行（含选填项，选填项按勾选状态显示） */
    public List<Row> computeRows(int mode) {
        List<Row> rows = new ArrayList<>();

        // ① 蓝方/红方不再单独成行：表格里改由每列表头按所选边染红/蓝
        // ② 英雄色卡、皮肤色卡
        String teamColorName = side == 1 ? "红色" : side == 0 ? "蓝色" : "";
        rows.add(new Row("英雄色卡", new Cell(
                heroColor == 1 ? "金色" : heroColor == 0 ? teamColorName : "", null)));
        rows.add(new Row("皮肤色卡", new Cell(
                skinColor == 1 ? "金色" : skinColor == 0 ? teamColorName : "", null)));
        // ③ 英雄/皮肤合并成一行（两行文字，各自按色卡着色）
        rows.add(new Row("英雄/皮肤", heroSkinCell()));
        rows.add(new Row("ID名", new Cell(idName == null ? "" : idName, null)));
        if (idBar == 1) rows.add(new Row("ID条", new Cell("有ID条", null)));

        // ③ 熟练度/标（国标不带 50强/100强/数字标）
        if (profMode == 0) {
            rows.add(new Row("熟练度/标", new Cell("", profImage)));
        } else if (profMode == 1) {
            StringBuilder sb = new StringBuilder();
            if (badgeGlow == 0) sb.append("带光效");
            else if (badgeGlow == 1) sb.append("不带光效");
            sb.append(s(badgeLevel, BADGE_LEVEL));
            if (badgeLevel != 4 && badgeNum != null && !badgeNum.isEmpty()) sb.append(badgeNum);
            rows.add(new Row("熟练度/标", new Cell(sb.toString(), null)));
        } else {
            rows.add(new Row("熟练度/标", new Cell("", null)));
        }

        // ④ 框
        if (frameType == 0) { // 段位框
            StringBuilder sb = new StringBuilder(s(rankFrame, RANK_FRAME));
            if (rankFrame == 9) { // 百星
                if (rankFrameGlow == 0) sb.append("·带光效");
            else if (rankFrameGlow == 1) sb.append("·不带光效");
            }
            if (frameBadge == 0) { // 有角标
                if (frameBadgeType == 0) sb.append("·天梯").append(ladderRank == null ? "" : ladderRank);
                else if (frameBadgeType == 1) sb.append("·巅峰角标").append(peakBadgeVer == 0 ? "新版" : peakBadgeVer == 1 ? "旧版" : "");
            }
            String frameImg = null;
            if (frameBadge == 0 && frameBadgeType == 1) frameImg = peakBadgeImage;
            else if (frameBadge == 0 && frameBadgeType == 0 && ladderImage >= 0)
                frameImg = F_TRANSLATION_LADDER + "/" + (ladderImage + 1) + ".jpg";
            rows.add(new Row("框", new Cell(sb.toString(), frameImg)));
        } else if (frameType == 1) { // 巅峰框
            String t = "巅峰" + (peakNumber == null ? "" : peakNumber);
            rows.add(new Row("框", new Cell(t, peakFrameImage)));
        } else {
            rows.add(new Row("框", new Cell("", null)));
        }

        // ⑤ 贵族标
        if (nobleLevel >= 0 && nobleLevel <= 10) {
            StringBuilder sb = new StringBuilder(s(nobleLevel, NOBLE));
            if (nobleLevel >= 6) { // V7-V10 / 传承
                if (nobleGlow == 0) sb.append("·带光效");
            else if (nobleGlow == 1) sb.append("·不带光效");
            }
            String img = null;
            if (nobleLevel == 9) img = noble10Image;       // V10
            else if (nobleLevel == 10) img = passDownImage; // 传承标
            rows.add(new Row("贵族标", new Cell(sb.toString(), img)));
        } else {
            rows.add(new Row("贵族标", new Cell("", null)));
        }

        // ⑦ 亲密关系
        String rel = s(relationType, RELATION);
        if (relationType != 7 && relationType != 8 && relationLevel != null && !relationLevel.isEmpty()) {
            rel += " Lv." + relationLevel;
        }
        rows.add(new Row("亲密关系", new Cell(rel, null)));

        // ⑧ 召唤师技能（图片或"其他"文字）
        if (summonerImage != null) {
            rows.add(new Row("召唤师技能", new Cell("", summonerImage)));
        } else if (summonerOtherMode) {
            String t = summonerOther == null || summonerOther.isEmpty() ? "其他" : "其他：" + summonerOther;
            rows.add(new Row("召唤师技能", new Cell(t, null)));
        } else {
            rows.add(new Row("召唤师技能", new Cell("", null)));
        }

        // ⑨ 加载进度（娱乐）
        if (mode == MODE_FUN) {
            String lt = loadMode == 0 ? "100%" : (loadText == null ? "" : loadText);
            rows.add(new Row("加载进度", new Cell(lt, null)));
        }

        // ⑩ 分路（排位）
        if (mode == MODE_RANK) {
            if (laneMode == 1) { // 分路段数
                String t = s(laneTier, LANE_TIER);
                String img = laneTierImage >= 0 ? F_ROLE_TIER + "/" + (laneTierImage + 1) + ".png" : null;
                rows.add(new Row("分路", new Cell(t, img)));
            } else if (laneMode == 0) { // 分路排名
                String img = laneRankImage >= 0 ? F_LANE_RANKING + "/" + (laneRankImage + 1) + ".png" : null;
                rows.add(new Row("分路", new Cell("", img)));
            } else {
                rows.add(new Row("分路", new Cell("", null)));
            }
        }

        // 选填项（最底部，勾选了才显示）
        if (opt.memoryStone) rows.add(new Row("铭记之石", new Cell("需要铭记之石", null)));
        if (opt.heroSign) rows.add(new Row("英雄签名", new Cell("需要英雄签名", null)));
        if (opt.profBadge) rows.add(new Row("职业标", new Cell("需要职业标", null)));
        if (opt.nationalBadge) rows.add(new Row("全国标", new Cell("需要全国标", null)));

        return rows;
    }
}
