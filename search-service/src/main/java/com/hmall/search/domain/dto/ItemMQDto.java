package com.hmall.search.domain.dto;

import com.hmall.api.dto.ItemDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemMQDto implements Serializable {
    private String operate; // 操作类型：UPDATE 或 REMOVE
    private ItemDTO itemDTO;//ItemDto对象
}
