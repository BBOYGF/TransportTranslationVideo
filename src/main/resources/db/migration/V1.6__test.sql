-- 开头视频用的那张图片（原来叫 begin_bg_image_path，容易让人以为是"叠一层背景"，改名统一）
ALTER TABLE last_time RENAME COLUMN begin_bg_image_path TO begin_image_path;
