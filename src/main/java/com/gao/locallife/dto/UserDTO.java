package com.gao.locallife.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录用户信息（存在 Redis Hash 里，也放在 ThreadLocal 里）。
 *
 * <p>字段很少，所以用 Hash 存而不是 String(JSON)：鉴权只要 HGET id，改昵称只要 HSET 一个字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO implements Serializable {

    private Long id;
    private String nickName;
    private String icon;
}
