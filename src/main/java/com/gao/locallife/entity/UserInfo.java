package com.gao.locallife.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 用户详情（表 tb_user_info，主键是 user_id，与 tb_user.id 一致） */
@Data
public class UserInfo implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long userId;
    private String city;
    private String introduce;
    private Integer gender;
    private LocalDate birthday;
    private Integer credits;
    private Integer level;
    private Integer fans;
    private Integer followee;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
