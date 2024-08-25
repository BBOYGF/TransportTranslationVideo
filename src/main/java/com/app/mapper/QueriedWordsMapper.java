package com.app.mapper;

import com.app.po.QueriedWords;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 查询过的单词对象
 *
 * @Author guofan
 * @Create 2022/1/22
 */
@Mapper
public interface QueriedWordsMapper extends BaseMapper<QueriedWords> {
    List<QueriedWords> selectBy(Map<String, Object> map);
}
