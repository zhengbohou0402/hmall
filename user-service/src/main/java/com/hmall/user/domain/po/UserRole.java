package com.hmall.user.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
@TableName("user_role")
public class UserRole implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long roleId;
}
