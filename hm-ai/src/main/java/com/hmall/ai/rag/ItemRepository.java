package com.hmall.ai.rag;

import com.hmall.api.dto.ItemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;

/**
 * 商品数据仓库：从 hm-item 库读取商品（RAG 知识库数据源）。
 * <p>
 * 商品数据量大（约 8.8 万条），RAG 抽样：每个分类取销量最高的前 N 条（status=1 正常在售）。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ItemRepository {

    /** 每个分类抽样的商品数 */
    private static final int PER_CATEGORY_LIMIT = 3;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 抽样加载商品（每个分类按销量取 top N）
     */
    public List<ItemDTO> sampleItems() {
        String sql = """
                SELECT id, name, price, stock, image, category, brand, spec, sold, comment_count, isAD, status
                FROM (
                    SELECT *, ROW_NUMBER() OVER (PARTITION BY category ORDER BY sold DESC) AS rn
                    FROM item WHERE status = 1
                ) t
                WHERE t.rn <= ?
                """;
        List<ItemDTO> items = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> ItemDTO.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .price(rs.getInt("price"))
                .stock(rs.getInt("stock"))
                .image(rs.getString("image"))
                .category(rs.getString("category"))
                .brand(rs.getString("brand"))
                .spec(rs.getString("spec"))
                .sold(rs.getInt("sold"))
                .commentCount(rs.getInt("comment_count"))
                .isAD(rs.getBoolean("isAD"))
                .status(rs.getInt("status"))
                .build()
        , PER_CATEGORY_LIMIT);
        log.info("从 hm-item 库抽样加载商品 {} 条（每分类 top {})", items.size(), PER_CATEGORY_LIMIT);
        return items;
    }
}
