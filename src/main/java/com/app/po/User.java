package com.app.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

/**
 * @Author guofan
 * @Create 2022/1/22
 */
@TableName("t_user")
@Data
@ToString
public class User {
    private int id;
    private String name;
    private int age;
}
