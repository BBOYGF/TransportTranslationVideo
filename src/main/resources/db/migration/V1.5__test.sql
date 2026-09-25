-- 开头视频的背景图路径（可选，选图片自动生成开头视频时使用）
ALTER TABLE last_time
    ADD COLUMN begin_bg_image_path TEXT;
