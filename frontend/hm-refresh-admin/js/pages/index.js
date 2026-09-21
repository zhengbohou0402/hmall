/* 黑马商城管理系统 · 数据概览：只使用 /api/items/admin/overview 的真实聚合数据。 */
(function () {
  "use strict";
  var Dashboard = Vue.extend({
    template: [
      '<section class="hm-dashboard">',
      ' <div class="hm-panel__head hm-dashboard__head">',
      '  <div class="hm-panel__title">经营概览 <small>实时汇总 · 商品服务</small></div>',
      '  <button class="hm-btn hm-btn--ghost" :disabled="loading" @click="load">{{loading?"刷新中…":"刷新数据"}}</button>',
      ' </div>',
      ' <div class="hm-panel" v-if="loading&&!loaded"><div class="hm-loading"><span class="hm-spinner"></span>正在读取真实商品统计…</div></div>',
      ' <div class="hm-panel" v-else-if="error"><div class="hm-empty"><span class="hm-empty__icon">!</span><h3>概览加载失败</h3><p>{{error}}</p><button class="hm-btn hm-btn--primary" @click="load">重新加载</button></div></div>',
      ' <template v-else>',
      '  <div class="hm-stat-grid">',
      '   <div class="hm-stat"><div class="hm-stat__label">商品总数</div><div class="hm-stat__value">{{fmt.num(data.total)}}</div><div class="hm-stat__foot">全量商品库</div></div>',
      '   <div class="hm-stat hm-stat--success"><div class="hm-stat__label">已上架</div><div class="hm-stat__value">{{fmt.num(data.onSale)}}</div><div class="hm-stat__foot">可正常销售</div></div>',
      '   <div class="hm-stat hm-stat--violet"><div class="hm-stat__label">已下架</div><div class="hm-stat__value">{{fmt.num(data.offSale)}}</div><div class="hm-stat__foot">暂不参与销售</div></div>',
      '   <div class="hm-stat hm-stat--warn"><div class="hm-stat__label">库存预警</div><div class="hm-stat__value">{{fmt.num(data.lowStock)}}</div><div class="hm-stat__foot">上架且库存 ≤ 10</div></div>',
      '  </div>',
      '  <div class="hm-dashboard__grid">',
      '   <div class="hm-panel"><div class="hm-panel__head"><div class="hm-panel__title">热门分类商品数 <small>按全量商品聚合</small></div><span class="hm-tag hm-tag--muted">Top {{categories.length}}</span></div>',
      '    <div class="hm-category-list" v-if="categories.length"><div v-for="(c,i) in categories" :key="c.category" class="hm-category-row"><span class="hm-category-rank">{{i+1}}</span><span class="hm-category-name">{{c.category}}</span><span class="hm-category-bar"><i :style="{width:barWidth(c.count)}"></i></span><b>{{fmt.num(c.count)}}</b></div></div>',
      '    <div class="hm-empty" v-else style="padding:34px">暂无分类数据</div></div>',
      '   <div class="hm-panel hm-panel--flush"><div class="hm-panel__head" style="padding:22px 24px 16px;margin-bottom:0"><div class="hm-panel__title">库存预警商品</div><a class="hm-tag" href="#/goods/list">去商品管理</a></div>',
      '    <div v-if="lowItems.length" class="hm-low-list"><div v-for="it in lowItems" :key="it.id" class="hm-low-row"><img v-if="it.image" :src="it.image" alt="" @error="$event.target.style.display=\'none\'"><span><b>{{it.name}}</b><small>{{it.category||"未分类"}} · ￥{{fmt.price(it.price)}}</small></span><em :class="Number(it.stock)<=0?\'is-danger\':\'\'">库存 {{it.stock}}</em></div></div>',
      '    <div class="hm-empty" v-else style="padding:34px">当前没有库存预警商品</div></div>',
      '  </div>',
      ' </template>',
      '</section>'
    ].join("\n"),
    data: function () { return { loading: true, loaded: false, error: "", data: {total:0,onSale:0,offSale:0,lowStock:0}, categories: [], lowItems: [] }; },
    created: function () { if (util.isLogin()) this.load(); },
    methods: {
      load: function () {
        var self = this; self.loading = true; self.error = "";
        axios.get("/items/admin/overview").then(function (resp) {
          var d = resp && resp.data;
          if (!d) throw new Error("接口未返回概览数据");
          self.data = d;
          self.categories = Array.isArray(d.categories) ? d.categories : [];
          self.lowItems = Array.isArray(d.lowStockItems) ? d.lowStockItems : [];
          self.loaded = true;
        }).catch(function (err) { self.error = hmAdmErrorText(err, "概览数据加载失败，请稍后重试"); }).then(function () { self.loading = false; });
      },
      barWidth: function (count) { var max = this.categories.length ? Number(this.categories[0].count) || 1 : 1; return Math.max(6, Math.round((Number(count) || 0) / max * 100)) + "%"; }
    },
    computed: { fmt: function () { return window.hmFormat; } }
  });
  window.Dashboard = Dashboard;
})();
