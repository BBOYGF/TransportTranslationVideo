package com.app.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

/**
 * @Author guofan
 * @Create 2022/1/22
 */
@TableName("t_queriedWords")
@Data
@ToString
public class QueriedWords {
    private int id;
    private String queryDateTime;
    private String word;
    private String translate;
    private String prevMemoryTime;
}
