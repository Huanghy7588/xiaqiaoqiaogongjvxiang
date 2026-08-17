package com.huanghy7588.xiaqiaoqiaogongjvxiang.wuzhong;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import android.util.LruCache;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 无中生有表格：首页入口。
 * 模式（排位/娱乐）→ 每个人物（左一/左二…）填写必填项 → 添加人物 → 选填项（每人一份）→ 导出到相册。
 * 图片选项直接以图片网格贴在表单上，点图即选；序号按人物内连续编号（每个人物从①开始）。
 */
public class WuzhongTableActivity extends Activity {

    private int mode = PersonData.MODE_RANK;
    private final List<PersonData> persons = new ArrayList<>();
    private LinearLayout personContainer;
    private Button btnModeRank, btnModeFun;

    /** 后台渲染/保存用的单线程池（S5 修复：2500px 大图渲染移出主线程） */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** 当前内嵌预览缩略图（S6 修复：替换时回收旧 Bitmap） */
    private Bitmap lastThumb;
    /** 存储权限请求码 */
    private static final int REQ_STORAGE = 2001;
    /** 权限授予后要执行的保存动作（S4 修复：pre-Q 需要运行时请求存储权限） */
    private Runnable pendingStorageAction;

    /** 带圈序号，按人物内顺序动态编号（1 开始连续） */
    private static final String[] CIRCLED = {"①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩"};

    /** W6 修复：asset 缩略图缓存，避免 renderPersons 每次全量重载图片 */
    private final LruCache<String, Bitmap> assetCache = new LruCache<String, Bitmap>(64) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return 1; // 按张数计，最多缓存 64 张缩略图
        }
    };

    private interface OnPick { void pick(int i); }
    private interface OnText { void on(String s); }
    private interface OnBool { void set(boolean b); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wuzhong_table);

        // W5 修复：从 savedInstanceState 恢复数据，防止进程回收后丢失
        if (savedInstanceState != null) {
            mode = savedInstanceState.getInt("mode", PersonData.MODE_RANK);
            @SuppressWarnings("unchecked")
            List<PersonData> saved = (List<PersonData>) savedInstanceState.getSerializable("persons");
            if (saved != null && !saved.isEmpty()) {
                persons.clear();
                persons.addAll(saved);
            }
        }

        personContainer = findViewById(R.id.person_container);
        btnModeRank = findViewById(R.id.btn_mode_rank);
        btnModeFun = findViewById(R.id.btn_mode_fun);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnModeRank.setOnClickListener(v -> { mode = PersonData.MODE_RANK; updateModeButtons(); renderPersons(); });
        btnModeFun.setOnClickListener(v -> { mode = PersonData.MODE_FUN; updateModeButtons(); renderPersons(); });

        findViewById(R.id.btn_add_person).setOnClickListener(v -> {
            persons.add(new PersonData());
            renderPersons();
        });

        findViewById(R.id.btn_export).setOnClickListener(v -> exportTable());
        findViewById(R.id.preview_thumb).setOnClickListener(v -> openPreview());

        // 默认一个人物（左一）
        if (persons.isEmpty()) {
            persons.add(new PersonData());
        }
        updateModeButtons();
        renderPersons();
    }

    // W5 修复：进程被回收后恢复用户填写的全部数据
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("mode", mode);
        outState.putSerializable("persons", (java.io.Serializable) persons);
    }

    private void updateModeButtons() {
        styleBtn(btnModeRank, mode == PersonData.MODE_RANK);
        styleBtn(btnModeFun, mode == PersonData.MODE_FUN);
    }

    // ====================== 人物表单 ======================

    private void renderPersons() {
        personContainer.removeAllViews();
        for (int i = 0; i < persons.size(); i++) {
            personContainer.addView(buildPersonCard(persons.get(i), i));
        }
        updatePreviewThumb();
    }

    /** 顶部内嵌实时预览：小宽度渲染省内存，内容变化即刻可见 */
    private void updatePreviewThumb() {
        ImageView thumb = findViewById(R.id.preview_thumb);
        if (thumb == null) return;
        try {
            Bitmap b = new TableRenderer(this).render(persons, mode, 500);
            if (b != null) {
                // S6 修复：先回收旧缩略图，避免反复操作内存暴涨
                Bitmap old = lastThumb;
                thumb.setImageBitmap(b);
                lastThumb = b;
                if (old != null && !old.isRecycled()) old.recycle();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private LinearLayout buildPersonCard(PersonData p, int idx) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_rounded);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cp);

        // 头部：左N + 删除
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tName = new TextView(this);
        tName.setText("左" + numberToCn(idx + 1));
        tName.setTextSize(18);
        tName.setTypeface(null, android.graphics.Typeface.BOLD);
        tName.setTextColor(Color.parseColor("#212121"));
        head.addView(tName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (persons.size() > 1) {
            Button del = new Button(this);
            del.setText("删除");
            del.setTextSize(12);
            styleBtn(del, false);
            del.setOnClickListener(v -> {
                persons.remove(idx);
                renderPersons();
            });
            head.addView(del);
        }
        card.addView(head);

        int[] sec = {0}; // 模式内连续编号计数器

        // ① 蓝方/红方
        card.addView(sectionTitle(sec, "蓝方/红方"));
        card.addView(choiceButtons(new String[]{"蓝方", "红方"}, p.side, i -> { p.side = i; renderPersons(); }));

        // ② 英雄色卡、皮肤色卡（根据队伍显示对应颜色按钮）
        card.addView(sectionTitle(sec, "英雄色卡、皮肤色卡"));
        String teamColorLabel = p.side == 1 ? "红色" : "蓝色";
        TextView colorTip = subTitle("红方：选 红色 或 金色\n蓝方：选 蓝色 或 金色\n（英雄与皮肤可分别选色）");
        colorTip.setTextColor(Color.parseColor("#F57F17"));
        card.addView(colorTip);
        card.addView(subTitle("英雄色卡"));
        card.addView(choiceButtons(new String[]{teamColorLabel, "金色"}, p.heroColor, i -> { p.heroColor = i; renderPersons(); }));
        card.addView(subTitle("皮肤色卡"));
        card.addView(choiceButtons(new String[]{teamColorLabel, "金色"}, p.skinColor, i -> { p.skinColor = i; renderPersons(); }));

        // ③ 英雄、皮肤 + ID名 + ID条
        card.addView(sectionTitle(sec, "英雄、皮肤"));
        card.addView(subTitle("英雄"));
        card.addView(makeEdit("请输入英雄名", p.hero, s -> p.hero = s));
        card.addView(subTitle("皮肤"));
        card.addView(makeEdit("请输入皮肤名", p.skin, s -> p.skin = s));
        card.addView(sectionTitle("ID名"));
        card.addView(makeEdit("请输入ID名", p.idName, s -> p.idName = s));

        // 是否需要 ID 条：左一选"是"(idBar=1) → 其他人强制"否"并锁定；左一未选/选"否" → 其他人可自由选择
        boolean idBarBlocked = idx > 0 && persons.get(0).idBar == 1;
        if (idBarBlocked) p.idBar = 0;
        card.addView(subTitle("是否需要 ID 条？"));
        card.addView(choiceButtons(new String[]{"是", "否"},
                p.idBar == 1 ? 0 : (p.idBar == 0 ? 1 : -1),
                i -> {
                    p.idBar = (i == 0) ? 1 : 0;
                    // W1 修复：左一从"是"改回"否"时，其他人被强制清成的 idBar 恢复为未选(-1)
                    if (idx == 0 && p.idBar == 0) {
                        for (int k = 1; k < persons.size(); k++) persons.get(k).idBar = -1;
                    }
                    renderPersons();
                }));
        if (idBarBlocked) {
            TextView lock = subTitle("左一已有 ID 条，其他人不能再选");
            lock.setTextColor(Color.parseColor("#BDBDBD"));
            card.addView(lock);
        }

        // ③ 熟练度/标
        card.addView(sectionTitle(sec, "熟练度 / 标"));
        card.addView(choiceButtons(new String[]{"熟练度", "标"}, p.profMode, i -> { p.profMode = i; renderPersons(); }));
        if (p.profMode == 0) {
            // 熟练度图片直接贴上来，点图即选
            card.addView(subTitle("熟练度图片（点图选择）"));
            card.addView(imageGrid(PersonData.F_PROFICIENCY, p.profImage, null, path -> p.profImage = path));
        } else if (p.profMode == 1) {
            card.addView(subTitle("带光效 / 不带光效"));
            card.addView(choiceButtons(new String[]{"带光效", "不带光效"}, p.badgeGlow, i -> { p.badgeGlow = i; renderPersons(); }));
            card.addView(subTitle("标等级"));
            card.addView(choiceButtons(PersonData.BADGE_LEVEL, p.badgeLevel, i -> {
                p.badgeLevel = i;
                // W2 修复：选中国标(4)时清空 badgeNum，避免切回非国标时旧"50强"重新出现
                if (i == 4) p.badgeNum = "";
                renderPersons();
            }));
            if (p.badgeLevel != 4) { // 国标不带 50强/100强/数字标
                card.addView(subTitle("50强 / 100强 / 数字标"));
                card.addView(makeEdit("如：50强 / 100强 / 数字", p.badgeNum, s -> p.badgeNum = s));
            }
        }

        // ④ 框
        card.addView(sectionTitle(sec, "框"));
        card.addView(choiceButtons(new String[]{"段位框", "巅峰框"}, p.frameType, i -> { p.frameType = i; renderPersons(); }));
        if (p.frameType == 0) { // 段位框
            card.addView(subTitle("段位"));
            card.addView(choiceButtons(PersonData.RANK_FRAME, p.rankFrame, i -> { p.rankFrame = i; renderPersons(); }));
            if (p.rankFrame == 9) { // 百星
                card.addView(subTitle("百星带光效 / 不带光效"));
        card.addView(choiceButtons(new String[]{"带光效", "不带光效"}, p.rankFrameGlow, i -> { p.rankFrameGlow = i; renderPersons(); }));
            }
            card.addView(subTitle("是否选择框的角标？"));
            card.addView(choiceButtons(new String[]{"是", "否"}, p.frameBadge, i -> { p.frameBadge = i; renderPersons(); }));
            if (p.frameBadge == 0) {
                card.addView(subTitle("角标类型"));
                card.addView(choiceButtons(new String[]{"天梯排名", "巅峰角标"}, p.frameBadgeType, i -> { p.frameBadgeType = i; renderPersons(); }));
                if (p.frameBadgeType == 0) {
                    card.addView(subTitle("天梯排名"));
                    card.addView(imageGrid(PersonData.F_TRANSLATION_LADDER,
                            p.ladderImage >= 0 ? PersonData.F_TRANSLATION_LADDER + "/" + (p.ladderImage + 1) + ".jpg" : null,
                            null, path -> p.ladderImage = parseIndex(path)));
                    card.addView(makeEdit("请输入天梯排名", p.ladderRank, s -> p.ladderRank = s));
                } else if (p.frameBadgeType == 1) {
                    card.addView(subTitle("巅峰角标版本"));
                    card.addView(choiceButtons(new String[]{"新版", "旧版"}, p.peakBadgeVer, i -> { p.peakBadgeVer = i; renderPersons(); }));
                    if (p.peakBadgeVer >= 0) {
                        String[] filter = (p.peakBadgeVer == 0)
                                ? new String[]{"1.5", "1.6", "1.7"}
                                : new String[]{"1.1", "1.2", "1.3", "1.4"};
                        card.addView(subTitle("巅峰角标（点图选择）"));
                        card.addView(imageGrid(PersonData.F_BADGE, p.peakBadgeImage, filter, path -> p.peakBadgeImage = path));
                    }
                }
            }
        } else if (p.frameType == 1) { // 巅峰框
            card.addView(subTitle("巅峰数字"));
            card.addView(makeEdit("请输入巅峰数字", p.peakNumber, s -> p.peakNumber = s));
            card.addView(subTitle("巅峰框（点图选择）"));
            card.addView(imageGrid(PersonData.F_PEAK_FRAME, p.peakFrameImage, null, path -> p.peakFrameImage = path));
        }

        // ⑤ 贵族标
        card.addView(sectionTitle(sec, "贵族标"));
        card.addView(choiceButtons(PersonData.NOBLE, p.nobleLevel, i -> { p.nobleLevel = i; renderPersons(); }));
        if (p.nobleLevel >= 6) { // V7-V10 / 传承
            card.addView(subTitle("带光效 / 不带光效"));
        card.addView(choiceButtons(new String[]{"带光效", "不带光效"}, p.nobleGlow, i -> { p.nobleGlow = i; renderPersons(); }));
        }
        if (p.nobleLevel == 9) {
            card.addView(subTitle("V10 样式（点图选择）"));
            card.addView(imageGrid(PersonData.F_NOBLE10, p.noble10Image, null, path -> p.noble10Image = path));
        } else if (p.nobleLevel == 10) {
            card.addView(subTitle("传承标样式（点图选择）"));
            card.addView(imageGrid(PersonData.F_PASS_DOWN, p.passDownImage, null, path -> p.passDownImage = path));
        }

        // ⑥ 亲密关系
        card.addView(sectionTitle(sec, "亲密关系"));
        card.addView(choiceButtons(PersonData.RELATION, p.relationType, i -> { p.relationType = i; renderPersons(); }));
        if (p.relationType != 7 && p.relationType != 8) {
            card.addView(subTitle("等级"));
            card.addView(makeEdit("请输入等级", p.relationLevel, s -> p.relationLevel = s));
        }

        // ⑦ 召唤师技能：技能全部用图片选择，另保留"其他"文字
        card.addView(sectionTitle(sec, "召唤师技能"));
        if (p.summonerOtherMode) p.summonerImage = null;
        card.addView(imageGrid(PersonData.F_SKILL, p.summonerImage, null, path -> {
            p.summonerImage = path;
            p.summonerOtherMode = false;
            renderPersons();
        }));
        card.addView(subTitle("或选择其他技能"));
        // 把"其他"做成跟图片选项一样大小的按钮（72dp），不再用 choiceButtons
        GridLayout otherGrid = new GridLayout(this);
        otherGrid.setColumnCount(4);
        otherGrid.setUseDefaultMargins(true);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        otherGrid.setLayoutParams(glp);

        LinearLayout otherCell = new LinearLayout(this);
        otherCell.setOrientation(LinearLayout.VERTICAL);
        otherCell.setGravity(Gravity.CENTER);
        otherCell.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable otherBg = new GradientDrawable();
        otherBg.setCornerRadius(dp(8));
        otherBg.setColor(p.summonerOtherMode ? Color.parseColor("#C8E6C9") : Color.parseColor("#F5F5F5"));
        otherBg.setStroke(dp(2), p.summonerOtherMode ? Color.parseColor("#4CAF50") : Color.parseColor("#BDBDBD"));
        otherCell.setBackground(otherBg);

        TextView otherLabel = new TextView(this);
        otherLabel.setText("其他");
        otherLabel.setGravity(Gravity.CENTER);
        otherLabel.setTextSize(14);
        otherLabel.setTextColor(Color.parseColor("#424242"));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(dp(72), dp(72));
        labelLp.gravity = Gravity.CENTER;
        otherLabel.setLayoutParams(labelLp);
        otherCell.addView(otherLabel);

        GridLayout.LayoutParams oclp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        oclp.width = 0;
        oclp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        oclp.setMargins(dp(4), dp(4), dp(4), dp(4));
        otherCell.setLayoutParams(oclp);
        otherCell.setOnClickListener(v -> {
            p.summonerOtherMode = true;
            p.summonerImage = null;
            renderPersons();
        });
        otherGrid.addView(otherCell);
        card.addView(otherGrid);
        if (p.summonerOtherMode) {
            card.addView(makeEdit("请输入其他技能", p.summonerOther, s -> p.summonerOther = s));
        }

        // ⑧ 加载进度（娱乐模式）
        if (mode == PersonData.MODE_FUN) {
            card.addView(sectionTitle(sec, "加载进度"));
            card.addView(choiceButtons(new String[]{"100%", "其他进度"}, p.loadMode, i -> { p.loadMode = i; renderPersons(); }));
            if (p.loadMode == 1) {
                card.addView(makeEdit("若有其他进度", p.loadText, s -> p.loadText = s));
            }
        }

        // ⑧ 分路（排位模式）
        if (mode == PersonData.MODE_RANK) {
            card.addView(sectionTitle(sec, "分路"));
            card.addView(choiceButtons(new String[]{"分路排名", "分路段数"}, p.laneMode, i -> { p.laneMode = i; renderPersons(); }));
            if (p.laneMode == 1) {
                card.addView(subTitle("分路段数"));
                card.addView(choiceButtons(PersonData.LANE_TIER, p.laneTier, i -> { p.laneTier = i; renderPersons(); }));
                card.addView(subTitle("段数样式（点图选择）"));
                card.addView(imageGrid(PersonData.F_ROLE_TIER,
                        p.laneTierImage >= 0 ? PersonData.F_ROLE_TIER + "/" + (p.laneTierImage + 1) + ".png" : null,
                        null, path -> p.laneTierImage = parseIndex(path)));
            } else if (p.laneMode == 0) {
                card.addView(subTitle("排名样式（点图选择）"));
                card.addView(imageGrid(PersonData.F_LANE_RANKING,
                        p.laneRankImage >= 0 ? PersonData.F_LANE_RANKING + "/" + (p.laneRankImage + 1) + ".png" : null,
                        null, path -> p.laneRankImage = parseIndex(path)));
            }
        }

        // 选填项（每个人物一份，✓/✗）
        card.addView(sectionTitle("选填项（需要就打勾）"));
        card.addView(toggleRow("铭记之石", p.opt.memoryStone, b -> p.opt.memoryStone = b));
        card.addView(toggleRow("英雄签名", p.opt.heroSign, b -> p.opt.heroSign = b));
        card.addView(toggleRow("职业标", p.opt.profBadge, b -> p.opt.profBadge = b));
        card.addView(toggleRow("全国标", p.opt.nationalBadge, b -> p.opt.nationalBadge = b));

        return card;
    }

    /** 序号节标题：带圈数字自动连续递增；相邻大项交替配色，视觉上明显分段 */
    private TextView sectionTitle(int[] counter, String text) {
        int n = counter[0]++;
        String prefix = (n < CIRCLED.length) ? CIRCLED[n] : (n + 1) + ".";
        TextView tv = new TextView(this);
        tv.setText(prefix + " " + text);
        tv.setTextSize(15);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        // 偶数项浅绿底深绿字，奇数项浅蓝底深蓝字，交替不重样
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6));
        if (n % 2 == 0) {
            bg.setColor(Color.parseColor("#E8F5E9"));
            tv.setTextColor(Color.parseColor("#1B5E20"));
        } else {
            bg.setColor(Color.parseColor("#E3F2FD"));
            tv.setTextColor(Color.parseColor("#0D47A1"));
        }
        tv.setBackground(bg);
        tv.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(14), 0, dp(6));
        tv.setLayoutParams(lp);
        return tv;
    }

    /** 普通节标题（无序号，用于 ID名 / 选填项等附属区块） */
    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(Color.parseColor("#6D4C41"));
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, dp(12), 0, dp(4));
        return tv;
    }

    /** 小标题（子选项） */
    private TextView subTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(Color.parseColor("#616161"));
        tv.setPadding(0, dp(8), 0, dp(2));
        return tv;
    }

    // ====================== 图片网格（点图选择） ======================

    /**
     * 图片选项网格：直接把图片贴在表单上，点击图片即选中（绿色边框高亮）。
     *
     * @param folder  assets 目录（如 photo/proficiency）
     * @param current 当前已选的完整 assets 路径（null 未选）
     * @param filter  文件名前缀过滤（null = 目录下全部）
     * @param cb      选中回调，参数为完整 assets 路径
     */
    private GridLayout imageGrid(String folder, String current, String[] filter, OnText cb) {
        GridLayout gl = new GridLayout(this);
        gl.setColumnCount(4);
        String[] files = listAssets(folder);
        for (String f : files) {
            if (filter != null && !matchFilter(f, filter)) continue;
            final String path = folder + "/" + f;
            final boolean selected = path.equals(current);

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(4), dp(4), dp(4), dp(4));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(8));
            if (selected) {
                bg.setStroke(dp(3), Color.parseColor("#4CAF50"));
                bg.setColor(Color.parseColor("#E8F5E9"));
            } else {
                bg.setStroke(dp(1), Color.parseColor("#BDBDBD"));
                bg.setColor(Color.WHITE);
            }
            cell.setBackground(bg);

            ImageView iv = new ImageView(this);
            Bitmap b = loadAssetBitmap(path, dp(72), dp(72));
            if (b != null) iv.setImageBitmap(b);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setAdjustViewBounds(false);
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(72), dp(72));
            iv.setLayoutParams(ivLp);
            cell.addView(iv);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            cell.setLayoutParams(lp);
            cell.setOnClickListener(v -> {
                cb.on(path);
                renderPersons();
            });
            gl.addView(cell);
        }
        return gl;
    }

    /** 列出 assets 目录下文件名（排序） */
    private String[] listAssets(String folder) {
        try {
            String[] files = getAssets().list(folder);
            if (files == null) return new String[0];
            Arrays.sort(files);
            return files;
        } catch (IOException e) {
            e.printStackTrace();
            return new String[0];
        }
    }

    /** 文件名是否以 filter 任一前缀开头 */
    private boolean matchFilter(String file, String[] filter) {
        for (String f : filter) {
            if (file.startsWith(f)) return true;
        }
        return false;
    }

    /** 从 assets 解码图片并缩放到目标尺寸（保持比例、开启抗锯齿），结果缓存到 LruCache（W6） */
    private Bitmap loadAssetBitmap(String path, int maxW, int maxH) {
        // W6 修复：先查缓存，命中则直接返回，避免每次 renderPersons 都重新解码
        String cacheKey = path + "_" + maxW + "x" + maxH;
        Bitmap cached = assetCache.get(cacheKey);
        if (cached != null) return cached;
        // W3 修复：用 try-with-resources，异常时流也关闭
        try (InputStream is = getAssets().open(path)) {
            Bitmap b = BitmapFactory.decodeStream(is);
            if (b == null) return null;
            float scale = Math.min((float) maxW / b.getWidth(), (float) maxH / b.getHeight());
            int tw = Math.max(1, Math.round(b.getWidth() * scale));
            int th = Math.max(1, Math.round(b.getHeight() * scale));
            Bitmap scaled = Bitmap.createScaledBitmap(b, tw, th, true);
            if (scaled != b) b.recycle();
            assetCache.put(cacheKey, scaled);
            return scaled;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ====================== 通用控件 ======================

    /** 选填项一行：文字 + ✓ / ✗ 按钮 */
    private LinearLayout toggleRow(String label, boolean cur, OnBool cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(15);
        tv.setTextColor(Color.parseColor("#212121"));
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button yes = new Button(this);
        yes.setText("✓");
        yes.setTextSize(16);
        Button no = new Button(this);
        no.setText("✗");
        no.setTextSize(16);
        yes.setOnClickListener(v -> { cb.set(true); styleBtn(yes, true); styleBtn(no, false); });
        no.setOnClickListener(v -> { cb.set(false); styleBtn(no, true); styleBtn(yes, false); });
        styleBtn(yes, cur);
        styleBtn(no, !cur);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(52), dp(44));
        blp.setMargins(dp(4), 0, 0, 0);
        yes.setLayoutParams(blp);
        no.setLayoutParams(blp);
        row.addView(yes);
        row.addView(no);
        return row;
    }

    private EditText makeEdit(String hint, String cur, OnText onChange) {
        EditText et = new EditText(this);
        et.setHint(hint);
        if (cur != null) et.setText(cur);
        et.setTextSize(15);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(6));
        et.setLayoutParams(lp);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { onChange.on(s.toString()); }
        });
        return et;
    }

    private View choiceButtons(String[] labels, int sel, OnPick cb) {
        if (labels.length <= 5) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                Button b = new Button(this);
                b.setText(labels[i]);
                b.setTextSize(14);
                styleBtn(b, idx == sel);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                b.setLayoutParams(lp);
                b.setOnClickListener(v -> cb.pick(idx));
                row.addView(b);
            }
            return row;
        }
        // 多选项用 GridLayout 3 列
        GridLayout gl = new GridLayout(this);
        int cols = 3;
        gl.setColumnCount(cols);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setTextSize(13);
            styleBtn(b, idx == sel);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / cols), GridLayout.spec(i % cols, 1f));
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> cb.pick(idx));
            gl.addView(b);
        }
        return gl;
    }

    private void styleBtn(Button b, boolean sel) {
        // 清除 XML 里的 backgroundTint，否则新背景会被 tint 染色，选中/未选看不出变化
        b.setBackgroundTintList(null);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(8));
        if (sel) {
            gd.setColor(Color.parseColor("#4CAF50"));
            gd.setStroke(dp(2), Color.parseColor("#2E7D32"));
            b.setTextColor(Color.WHITE);
        } else {
            gd.setColor(Color.parseColor("#ECEFF1"));
            b.setTextColor(Color.parseColor("#37474F"));
        }
        b.setBackground(gd);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private int parseIndex(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        try { return Integer.parseInt(base) - 1; } catch (NumberFormatException e) { return -1; }
    }

    /** 1..10 -> 一二…十 */
    private String numberToCn(int n) {
        String[] cn = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
        return (n >= 1 && n <= 10) ? cn[n - 1] : String.valueOf(n);
    }

    // ====================== 预览 ======================

    /** 全屏预览生成的表格（双指缩放、拖动查看），可直接保存 */
    private void openPreview() {
        if (persons.isEmpty()) {
            Toast.makeText(this, "请先添加人物", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "生成中…", Toast.LENGTH_SHORT).show();
        // S5 修复：大图渲染移出主线程，避免 ANR（宽度 1000 匹配渲染器基准）
        executor.execute(() -> {
            Bitmap bmp = new TableRenderer(this).render(persons, mode, 1000);
            runOnUiThread(() -> showPreviewDialog(bmp));
        });
    }

    private void showPreviewDialog(Bitmap bmp) {
        if (bmp == null) {
            Toast.makeText(this, "生成失败", Toast.LENGTH_SHORT).show();
            return;
        }
        final Dialog dlg = new Dialog(this);
        dlg.setContentView(R.layout.dialog_wuzhong_preview);
        ZoomImageView zoom = dlg.findViewById(R.id.zoom_view);
        zoom.setBitmap(bmp);
        dlg.findViewById(R.id.btn_preview_close).setOnClickListener(v -> dlg.dismiss());
        final boolean[] saving = {false};
        dlg.findViewById(R.id.btn_preview_save).setOnClickListener(v ->
                // S4 修复：pre-Q 需先取得存储权限再保存
                ensureStoragePermissionThen(() -> {
                    saving[0] = true;
                    executor.execute(() -> {
                        boolean ok = saveToGallery(bmp);
                        if (!bmp.isRecycled()) bmp.recycle(); // S6 修复
                        boolean saved = ok;
                        runOnUiThread(() -> {
                            Toast.makeText(this,
                                    saved ? "已保存到相册（图片/夏乔乔工具箱）" : "保存失败",
                                    Toast.LENGTH_SHORT).show();
                            dlg.dismiss();
                        });
                    });
                }));
        // S6 修复：关闭弹窗时回收大图（保存中由保存线程回收，避免竞争）
        dlg.setOnDismissListener(d -> {
            if (!saving[0] && bmp != null && !bmp.isRecycled()) bmp.recycle();
        });
        dlg.show();
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
        }
    }

    // ====================== 导出 ======================

    private void exportTable() {
        if (persons.isEmpty()) {
            Toast.makeText(this, "请先添加人物", Toast.LENGTH_SHORT).show();
            return;
        }
        // S4 修复：pre-Q 需先取得存储权限；授权后再渲染+保存
        ensureStoragePermissionThen(this::doExport);
    }

    private void doExport() {
        Toast.makeText(this, "生成中…", Toast.LENGTH_SHORT).show();
        // S5 修复：大图渲染 + PNG 压缩移出主线程，避免 ANR（宽度 1000）
        executor.execute(() -> {
            Bitmap bmp = new TableRenderer(this).render(persons, mode, 1000);
            if (bmp == null) {
                runOnUiThread(() -> Toast.makeText(this, "生成失败", Toast.LENGTH_SHORT).show());
                return;
            }
            boolean ok = saveToGallery(bmp);
            if (!bmp.isRecycled()) bmp.recycle(); // S6 修复
            boolean saved = ok;
            runOnUiThread(() -> Toast.makeText(this,
                    saved ? "已保存到相册（图片/夏乔乔工具箱）" : "保存失败",
                    Toast.LENGTH_LONG).show());
        });
    }

    /** S4 修复：pre-Q 需要 WRITE_EXTERNAL_STORAGE 运行时权限；Q+ 无需请求直接执行 */
    private void ensureStoragePermissionThen(Runnable action) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingStorageAction = action;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            Toast.makeText(this, "需要存储权限以保存图片", Toast.LENGTH_SHORT).show();
            return;
        }
        action.run();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && pendingStorageAction != null) {
                Runnable act = pendingStorageAction;
                pendingStorageAction = null;
                act.run();
            } else {
                pendingStorageAction = null;
                Toast.makeText(this, "未授权存储权限，无法保存图片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 保存 Bitmap 到相册 Pictures/夏乔乔工具箱。
     * 调用方需保证 pre-Q 已获得 WRITE_EXTERNAL_STORAGE 权限（见 ensureStoragePermissionThen）。
     * 返回 true 表示成功（不再内部弹 Toast，由调用方在主线程提示）。
     */
    private boolean saveToGallery(Bitmap bmp) {
        String name = "wuzhong_" + System.currentTimeMillis() + ".png";
        // Android 10+ 用 IS_PENDING 标记：写入完成后相册才可见，失败则删除记录，避免相册留下灰色空图
        Uri pendingUri = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/夏乔乔工具箱");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
                pendingUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (pendingUri == null) throw new IOException("相册写入位置创建失败");
                try (OutputStream os = getContentResolver().openOutputStream(pendingUri)) {
                    if (os == null || !bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                        throw new IOException("图片数据写入失败");
                    }
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                // W4 修复：校验 IS_PENDING 更新返回值，失败则删除记录避免灰色空图
                int updated = getContentResolver().update(pendingUri, done, null, null);
                if (updated == 0) {
                    getContentResolver().delete(pendingUri, null, null);
                    throw new IOException("IS_PENDING 清除失败");
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "夏乔乔工具箱");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("目录创建失败");
                File f = new File(dir, name);
                boolean ok;
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                if (!ok) {
                    f.delete();
                    throw new IOException("图片数据写入失败");
                }
                sendBroadcast(new android.content.Intent(
                        android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            if (pendingUri != null) {
                try { getContentResolver().delete(pendingUri, null, null); } catch (Exception ignore) {}
            }
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // S6 修复：回收缩略图、关闭后台线程池
        executor.shutdown();
        if (lastThumb != null && !lastThumb.isRecycled()) {
            lastThumb.recycle();
            lastThumb = null;
        }
    }
}
