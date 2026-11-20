-- 去掉 OKF 词条正文开头的一级标题（如 "# 東方志奏 12th Spell -Trick Shooter-"）。
-- 词条名已由详情页 hero 区展示，正文里的 H1 是重复的；且前端 markdown 渲染器
-- 不识别 H1，会以带 # 的普通文本显示。新生成的内容已在 OkfGenerateService 中去掉 H1，
-- 本脚本用于清理存量入库数据。幂等：重复执行不影响已清理的内容。
--
-- 条件 ^#[ \t] 确保只匹配单个 # 开头的行（"## 简介" 等二级标题不会被误删）。
UPDATE m_okf_entry
SET content = REGEXP_REPLACE(content, '^#[ \t]+[^\r\n]*(\r\n|\n|\r)*', '')
WHERE content REGEXP '^#[ \t]';
