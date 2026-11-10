package com.gao.locallife.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 探店笔记（表 tb_blog）。liked / comments 是冗余计数，为了列表页不用 count(*)。 */
@Data
public class Blog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private Long userId;
    private String title;
    private String content;
    private String images;
    private Integer liked;
    private Integer comments;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
