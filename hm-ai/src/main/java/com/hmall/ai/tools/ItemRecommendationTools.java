package com.hmall.ai.tools;

import com.hmall.ai.config.VertexAiEmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 商品推荐工具（RAG）：基于向量相似度从商品知识库中检索最匹配的商品。
 * <p>
 * 商品知识库当前使用演示数据（本地无 MySQL/item-service 时也能验证 RAG 链路），
 * 后续可从 item-service 拉取真实商品数据替换。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemRecommendationTools {

    private final VertexAiEmbeddingModel embeddingModel;

    /** 商品知识库 */
    private final List<ItemKnowledge> items = new ArrayList<>();
    /** 商品向量（与 items 一一对应，懒加载） */
    private volatile List<float[]> itemVectors;

    @PostConstruct
    public void init() {
        items.add(new ItemKnowledge(1001L, "华为 Mate 60 Pro 手机", "华为", "手机数码",
                "12GB+512GB 雅丹黑", 699900, "旗舰智能手机，卫星通话，超强影像，昆仑玻璃，商务人士首选"));
        items.add(new ItemKnowledge(1002L, "苹果 AirPods Pro 2 耳机", "苹果", "手机数码",
                "USB-C 主动降噪", 189900, "主动降噪无线蓝牙耳机，空间音频，佩戴舒适，通勤运动都适合"));
        items.add(new ItemKnowledge(1003L, "联想拯救者 Y9000P 笔记本", "联想", "电脑办公",
                "i9+RTX4060 16G+1T", 899900, "高性能游戏本，电竞屏幕，散热强劲，适合游戏和视频剪辑"));
        items.add(new ItemKnowledge(1004L, "华为 Watch GT4 智能手表", "华为", "智能穿戴",
                "46mm 黑色氟橡胶表带", 148800, "智能运动手表，血氧心率监测，两周续航，健康管理"));
        items.add(new ItemKnowledge(1005L, "小米空气净化器 4 Pro", "小米", "家用电器",
                "适用60平米", 129900, "家用除甲醛除菌空气净化器，静音，智能联动，适合新装修家庭"));
        items.add(new ItemKnowledge(1006L, "德龙 ECAM 咖啡机", "德龙", "家用电器",
                "全自动意式", 399000, "全自动意式咖啡机，一键现磨，奶泡系统，咖啡爱好者居家必备"));
        items.add(new ItemKnowledge(1007L, "耐克 Air Zoom 跑鞋", "耐克", "运动户外",
                "42码 黑白", 79900, "轻便缓震跑步鞋，透气网面，回弹好，适合日常跑步健身"));
        items.add(new ItemKnowledge(1008L, "波司登极寒羽绒服", "波司登", "服饰鞋包",
                "黑色 180/XL", 129900, "加厚保暖羽绒服，90%白鸭绒，防风防水，适合严寒冬季"));
        log.info("商品知识库初始化完成，共 {} 件商品", items.size());
    }

    /**
     * 根据用户需求推荐商品（RAG 检索）
     */
    @Tool(description = "根据用户的需求描述，从商品库中检索并推荐最匹配的商品。输入用户的需求或偏好（如\"想买一台适合打游戏的笔记本\"），返回推荐的商品列表")
    public List<ItemKnowledge> recommendItems(@ToolParam(description = "用户的需求描述，如\"适合学生的性价比手机\"") String query) {
        try {
            List<float[]> vectors = getOrBuildVectors();
            float[] queryVector = embeddingModel.embed(query);
            // 余弦相似度排序，取 top 3
            return IntStream.range(0, items.size())
                    .boxed()
                    .sorted(Comparator.comparingDouble(i -> -cosine(queryVector, vectors.get(i))))
                    .limit(3)
                    .map(items::get)
                    .toList();
        } catch (Exception e) {
            log.warn("商品推荐检索失败，降级返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    private List<float[]> getOrBuildVectors() {
        if (itemVectors != null) {
            return itemVectors;
        }
        synchronized (this) {
            if (itemVectors == null) {
                List<float[]> vectors = new ArrayList<>(items.size());
                for (ItemKnowledge item : items) {
                    vectors.add(embeddingModel.embed(item.toEmbeddingText()));
                }
                itemVectors = vectors;
            }
            return itemVectors;
        }
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
