package com.hmall.item.domain.dto;

import com.hmall.api.dto.ItemDTO;
import com.hmall.item.domain.enums.ItemOperate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemMQDto implements Serializable {
    private ItemOperate operate; //操作类型
    private ItemDTO itemDTO;//ItemDto对象
}
