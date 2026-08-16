package com.huanghy7588.xiaqiaoqiaogongjvxiang.wuzhong;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 无中生有表格：首页入口。
 * 模式（排位/娱乐）→ 每个人物（左一/左二…）填写必填项 → 添加人物 → 选填项 → 导出到相册。
 */
public class WuzhongTableActivity extends Activity {

    private int mode = PersonData.MODE_RANK;
    private final List<PersonData> persons = new ArrayList<>();
    private final PersonData.OptionalState opt = new PersonData.OptionalState();
    private LinearLayout personContainer;
    private Button btnModeRank, btnModeFun;

    private interface OnPick { void pick(int i); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wuzhong_table);

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

        // 默认一个人物（左一）
        persons.add(new PersonData());
        updateModeButtons();
        renderPersons();
        buildOptional();
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
        tName.setText("左" + (idx + 1));
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

        // ① 蓝方/红方
        card.addView(sectionTitle("① 蓝方/红方"));
        card.addView(choiceButtons(new String[]{"蓝方", "红方"}, p.side, i -> { p.side = i; renderPersons(); }));

        // ② 英雄/皮肤 + ID名
        card.addView(sectionTitle("② 英雄、皮肤"));
        card.addView(makeEdit("请输入英雄、皮肤", p.heroSkin, s -> p.heroSkin = s));
        card.addView(sectionTitle("ID名"));
        card.addView(makeEdit("请输入ID名", p.idName, s -> p.idName = s));

        // ③ 熟练度/标
        card.addView(sectionTitle("③ 熟练度 / 标"));
        card.addView(choiceButtons(new String[]{"熟练度", "标"}, p.profMode, i -> { p.profMode = i; renderPersons(); }));
        if (p.profMode == 0) {
            card.addView(imageRow("熟练度图片", PersonData.F_PROFICIENCY, p.profImage, null, path -> p.profImage = path));
        } else if (p.profMode == 1) {
            card.addView(sectionTitle("发光 / 不发光"));
            card.addView(choiceButtons(new String[]{"发光", "不发光"}, p.badgeGlow, i -> { p.badgeGlow = i; renderPersons(); }));
            card.addView(sectionTitle("标等级"));
            card.addView(choiceButtons(PersonData.BADGE_LEVEL, p.badgeLevel, i -> { p.badgeLevel = i; renderPersons(); }));
            card.addView(sectionTitle("50强 / 100强 / 数字标"));
            card.addView(makeEdit("如：50强 / 100强 / 数字", p.badgeNum, s -> p.badgeNum = s));
        }

        // ④ 框
        card.addView(sectionTitle("④ 框"));
        card.addView(choiceButtons(new String[]{"段位框", "巅峰框"}, p.frameType, i -> { p.frameType = i; renderPersons(); }));
        if (p.frameType == 0) { // 段位框
            card.addView(sectionTitle("段位"));
            card.addView(choiceButtons(PersonData.RANK_FRAME, p.rankFrame, i -> { p.rankFrame = i; renderPersons(); }));
            if (p.rankFrame == 9) { // 百星
                card.addView(sectionTitle("百星发光 / 不发光"));
                card.addView(choiceButtons(new String[]{"发光", "不发光"}, p.rankFrameGlow, i -> { p.rankFrameGlow = i; renderPersons(); }));
            }
            card.addView(sectionTitle("是否选择框的角标？"));
            card.addView(choiceButtons(new String[]{"是", "否"}, p.frameBadge, i -> { p.frameBadge = i; renderPersons(); }));
            if (p.frameBadge == 0) {
                card.addView(sectionTitle("角标类型"));
                card.addView(choiceButtons(new String[]{"天梯排名", "巅峰角标"}, p.frameBadgeType, i -> { p.frameBadgeType = i; renderPersons(); }));
                if (p.frameBadgeType == 0) {
                    card.addView(sectionTitle("天梯排名"));
                    card.addView(makeEdit("请输入天梯排名", p.ladderRank, s -> p.ladderRank = s));
                } else if (p.frameBadgeType == 1) {
                    card.addView(sectionTitle("巅峰角标版本"));
                    card.addView(choiceButtons(new String[]{"新版", "旧版"}, p.peakBadgeVer, i -> { p.peakBadgeVer = i; renderPersons(); }));
                    if (p.peakBadgeVer >= 0) {
                        String[] filter = (p.peakBadgeVer == 0)
                                ? new String[]{"1.5", "1.6", "1.7"}
                                : new String[]{"1.1", "1.2", "1.3", "1.4"};
                        card.addView(imageRow("巅峰角标图片", PersonData.F_BADGE, p.peakBadgeImage, filter, path -> p.peakBadgeImage = path));
                    }
                }
            }
        } else if (p.frameType == 1) { // 巅峰框
            card.addView(sectionTitle("巅峰数字"));
            card.addView(makeEdit("请输入巅峰数字", p.peakNumber, s -> p.peakNumber = s));
            card.addView(imageRow("巅峰框图片", PersonData.F_PEAK_FRAME, p.peakFrameImage, null, path -> p.peakFrameImage = path));
        }

        // ⑤ 贵族标
        card.addView(sectionTitle("⑤ 贵族标"));
        card.addView(choiceButtons(PersonData.NOBLE, p.nobleLevel, i -> { p.nobleLevel = i; renderPersons(); }));
        if (p.nobleLevel >= 6) { // V7-V10 / 传承
            card.addView(sectionTitle("发光 / 不发光"));
            card.addView(choiceButtons(new String[]{"发光", "不发光"}, p.nobleGlow, i -> { p.nobleGlow = i; renderPersons(); }));
        }
        if (p.nobleLevel == 9) {
            card.addView(imageRow("V10 图片", PersonData.F_NOBLE10, p.noble10Image, null, path -> p.noble10Image = path));
        } else if (p.nobleLevel == 10) {
            card.addView(imageRow("传承标图片", PersonData.F_PASS_DOWN, p.passDownImage, null, path -> p.passDownImage = path));
        }

        // ⑦ 亲密关系
        card.addView(sectionTitle("⑦ 亲密关系"));
        card.addView(choiceButtons(PersonData.RELATION, p.relationType, i -> { p.relationType = i; renderPersons(); }));
        if (p.relationType != 7 && p.relationType != 8) {
            card.addView(sectionTitle("等级"));
            card.addView(makeEdit("请输入等级", p.relationLevel, s -> p.relationLevel = s));
        }

        // ⑧ 召唤师技能
        card.addView(sectionTitle("⑧ 召唤师技能"));
        card.addView(choiceButtons(PersonData.SUMMONER, p.summonerSkill, i -> { p.summonerSkill = i; renderPersons(); }));
        if (p.summonerSkill == 11) {
            card.addView(sectionTitle("其他技能"));
            card.addView(makeEdit("请输入其他技能", p.summonerOther, s -> p.summonerOther = s));
        }

        // ⑨ 加载进度（娱乐模式）
        if (mode == PersonData.MODE_FUN) {
            card.addView(sectionTitle("⑨ 加载进度"));
            card.addView(choiceButtons(new String[]{"100%", "其他进度"}, p.loadMode, i -> { p.loadMode = i; renderPersons(); }));
            if (p.loadMode == 1) {
                card.addView(makeEdit("若有其他进度", p.loadText, s -> p.loadText = s));
            }
        }

        // ⑩ 分路（排位模式）
        if (mode == PersonData.MODE_RANK) {
            card.addView(sectionTitle("⑩ 分路"));
            card.addView(choiceButtons(new String[]{"分路排名", "分路段数"}, p.laneMode, i -> { p.laneMode = i; renderPersons(); }));
            if (p.laneMode == 1) {
                card.addView(sectionTitle("分路段数"));
                card.addView(choiceButtons(PersonData.LANE_TIER, p.laneTier, i -> { p.laneTier = i; renderPersons(); }));
                card.addView(imageRow("分路段数图片", PersonData.F_ROLE_TIER,
                        p.laneTierImage >= 0 ? PersonData.F_ROLE_TIER + "/" + (p.laneTierImage + 1) + ".png" : null,
                        null, path -> {
                            p.laneTierImage = parseIndex(path, PersonData.F_ROLE_TIER);
                        }));
            } else if (p.laneMode == 0) {
                card.addView(imageRow("分路排名图片", PersonData.F_LANE_RANKING,
                        p.laneRankImage >= 0 ? PersonData.F_LANE_RANKING + "/" + (p.laneRankImage + 1) + ".png" : null,
                        null, path -> {
                            p.laneRankImage = parseIndex(path, PersonData.F_LANE_RANKING);
                        }));
            }
        }

        return card;
    }

    // ====================== 选填项 ======================

    private void buildOptional() {
        LinearLayout optBox = findViewById(R.id.optional_container);
        optBox.removeAllViews();
        optBox.addView(toggleRow("是否需要铭记之石", b -> opt.memoryStone = b));
        optBox.addView(toggleRow("是否需要英雄签名", b -> opt.heroSign = b));
        optBox.addView(toggleRow("是否需要职业标", b -> opt.profBadge = b));
        optBox.addView(toggleRow("是否需要全国标", b -> opt.nationalBadge = b));
    }

    private LinearLayout toggleRow(String label, OnBool cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(15);
        tv.setTextColor(Color.parseColor("#212121"));
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button yes = new Button(this); yes.setText("需要"); yes.setTextSize(13);
        Button no = new Button(this); no.setText("不需要"); no.setTextSize(13);
        yes.setOnClickListener(v -> { cb.set(true); styleBtn(yes, true); styleBtn(no, false); });
        no.setOnClickListener(v -> { cb.set(false); styleBtn(no, true); styleBtn(yes, false); });
        styleBtn(no, true);
        row.addView(yes); row.addView(no);
        return row;
    }

    private interface OnBool { void set(boolean b); }

    // ====================== UI 辅助 ======================

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(Color.parseColor("#4CAF50"));
        tv.setPadding(0, dp(10), 0, dp(4));
        return tv;
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

    private interface OnText { void on(String s); }

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

    private LinearLayout imageRow(String label, String folder, String current, String[] filter, OnText cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(6));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(Color.parseColor("#757575"));
        row.addView(tv);
        Button pick = new Button(this);
        pick.setText(current == null ? "选择图片" : "已选：" + fileName(current));
        pick.setTextSize(14);
        styleBtn(pick, false);
        pick.setOnClickListener(v -> openPicker(folder, label, filter, cb));
        row.addView(pick);
        return row;
    }

    private void openPicker(String folder, String title, String[] filter, OnText cb) {
        new ImagePickerDialog(this, folder, title, filter, (path, name) -> {
            cb.on(path);
            renderPersons();
        }).show();
    }

    private void styleBtn(Button b, boolean sel) {
        b.setBackgroundColor(sel ? Color.parseColor("#4CAF50") : Color.parseColor("#E0E0E0"));
        b.setTextColor(sel ? Color.WHITE : Color.parseColor("#212121"));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private String fileName(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private int parseIndex(String path, String folder) {
        // photo/xxx/3.png -> 2
        String name = fileName(path);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        try { return Integer.parseInt(base) - 1; } catch (NumberFormatException e) { return -1; }
    }

    // ====================== 导出 ======================

    private void exportTable() {
        if (persons.isEmpty()) {
            Toast.makeText(this, "请先添加人物", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bmp = new TableRenderer(this).render(persons, mode, opt);
        if (bmp == null) {
            Toast.makeText(this, "生成失败", Toast.LENGTH_SHORT).show();
            return;
        }
        saveToGallery(bmp);
    }

    private void saveToGallery(Bitmap bmp) {
        String name = "wuzhong_" + System.currentTimeMillis() + ".png";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/夏乔乔工具箱");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                    }
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "夏乔乔工具箱");
                dir.mkdirs();
                File f = new File(dir, name);
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                sendBroadcast(new android.content.Intent(
                        android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
            }
            Toast.makeText(this, "已保存到相册（图片/夏乔乔工具箱）", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
