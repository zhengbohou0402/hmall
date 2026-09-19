/* ==========================================================================
   黑马商城管理系统 · 数据概览  js/pages/index.js
   --------------------------------------------------------------------------
   重要变更（相对旧版）：
     · 旧版整页都是硬编码假数据（商品总览 100/400/50/500、订单折线图固定 2021 年数值、
       用户总览 100/200/1000/5000），已全部删除。
     · 现在唯一数据源是真实的商品服务：GET /api/items/page
         参数  pageNo / pageSize / id / key / category / brand / minPrice / maxPrice
         返回  PageDTO { total:Long, pages:Long, list: ItemDTO[] }
   数据口径说明（页面会如实标注，不做无依据的推算）：
     · 「商品总数」= 接口返回的 total，全量权威值。
     · 「分类商品数」图表 = 对每个分类各发一次 pageSize=1 的请求，读取该分类的
       total —— 这是**真实的分类全量**，不是抽样。
     · 「已上架 / 已下架 / 库存紧张 / 价格区间」—— item-service 的分页接口目前
       不支持按 status 过滤，因此这些指标基于最近 SAMPLE_SIZE 条商品做客户端统计，
       页面上会明确标注样本量，避免被误读成全量结论。
   商品状态：1 = 已上架，2 = 已下架（与商品管理页上架 / 下架行为一致）
   ========================================================================== */
(function () {
  "use strict";

  // 客户端统计所使用的样本条数（一次请求，避免 8 万+ 商品分页遍历）
  var SAMPLE_SIZE = 1000;
  // 逐分类查询真实 total 时，最多查询多少个分类
  var MAX_CATEGORY_QUERIES = 12;
  // 库存紧张阈值
  var LOW_STOCK = 10;

  var Dashboard = Vue.extend({
    template: [
      '<div>',

      // ============================== 页头 ==============================
      '  <div class="hm-panel__head" style="border:0;padding:0;margin-bottom:18px">',
      '    <div class="hm-panel__title">',
      '      数据概览',
      '      <small>数据来源：GET /api/items/page</small>',
      '    </div>',
      '    <button type="button" class="hm-btn hm-btn--ghost" @click="load" :disabled="loading">',
      '      {{ loading ? \'刷新中…\' : \'刷新数据\' }}',
      '    </button>',
      '  </div>',

      // ============================== 加载 / 失败 ==============================
      '  <div class="hm-panel" v-if="loading && !loaded">',
      '    <div class="hm-loading">',
      '      <span class="hm-spinner"></span><span>正在读取商品数据…</span>',
      '    </div>',
      '  </div>',

      '  <div class="hm-panel" v-else-if="error">',
      '    <div class="hm-empty">',
      '      <span class="hm-empty__icon">!</span>',
      '      <h3>数据加载失败</h3>',
      '      <p>{{ error }}</p>',
      '      <button type="button" class="hm-btn hm-btn--primary" @click="load">重新加载</button>',
      '    </div>',
      '  </div>',

      '  <template v-else>',

      // ============================== 统计卡 ==============================
      '    <div class="hm-stat-grid">',
      '      <div class="hm-stat">',
      '        <div class="hm-stat__label">商品总数</div>',
      '        <div class="hm-stat__value">{{ fmt.num(total) }}</div>',
      '        <div class="hm-stat__foot">接口 total 字段 · 全量权威值</div>',
      '      </div>',
      '      <div class="hm-stat hm-stat--success">',
      '        <div class="hm-stat__label">已上架</div>',
      '        <div class="hm-stat__value">{{ fmt.num(onSale) }}</div>',
      '        <div class="hm-stat__foot">{{ sampleText }}</div>',
      '      </div>',
      '      <div class="hm-stat hm-stat--violet">',
      '        <div class="hm-stat__label">已下架</div>',
      '        <div class="hm-stat__value">{{ fmt.num(offSale) }}</div>',
      '        <div class="hm-stat__foot">{{ sampleText }}</div>',
      '      </div>',
      '      <div class="hm-stat hm-stat--warn">',
      '        <div class="hm-stat__label">库存紧张（≤' + LOW_STOCK + '）</div>',
      '        <div class="hm-stat__value">{{ fmt.num(lowStockCount) }}</div>',
      '        <div class="hm-stat__foot">{{ sampleText }}</div>',
      '      </div>',
      '    </div>',

      // ============================== 口径提示 ==============================
      '    <div class="hm-notice hm-notice--warn" style="margin-top:16px" v-if="isPartial">',
      '      <span>!</span>',
      '      <span>',
      '        商品总数 {{ fmt.num(total) }} 条为接口全量值。商品分页接口暂不支持按 status 过滤，',
      '        因此「已上架 / 已下架 / 库存紧张」是基于最近 {{ fmt.num(sample.length) }} 条商品的',
      '        <b>样本统计</b>（约占全量 {{ sampleRatio }}%），与全量口径可能存在偏差；',
      '        下方「分类商品数」图表则是逐分类查询的真实全量，不受此限制。',
      '      </span>',
      '    </div>',

      // ============================== 图表 + 库存关注 ==============================
      '    <div style="display:grid;grid-template-columns:minmax(0,1.35fr) minmax(0,1fr);',
      '                gap:18px;margin-top:18px">',

      '      <div class="hm-panel">',
      '        <div class="hm-panel__head">',
      '          <div class="hm-panel__title">',
      '            分类商品数',
      '            <small>逐分类读取真实 total</small>',
      '          </div>',
      '          <span class="hm-tag hm-tag--muted" v-if="!categoryLoading">',
      '            {{ categoryStats.length }} 个分类',
      '          </span>',
      '          <span class="hm-tag hm-tag--muted" v-else>统计中…</span>',
      '        </div>',

      '        <div class="hm-loading" v-if="categoryLoading">',
      '          <span class="hm-spinner"></span><span>正在逐分类统计真实总量…</span>',
      '        </div>',

      '        <div class="hm-empty" v-else-if="!categoryStats.length" style="padding:36px 12px">',
      '          <p>暂无可用于统计的分类数据。</p>',
      '        </div>',

      '        <div v-show="!categoryLoading && categoryStats.length" ref="chart"',
      '             style="width:100%;height:340px"></div>',
      '      </div>',

      '      <div class="hm-panel hm-panel--flush">',
      '        <div class="hm-panel__head" style="padding:22px 24px 16px;margin-bottom:0">',
      '          <div class="hm-panel__title">库存最少的商品</div>',
      '          <a class="hm-tag" href="#/goods/list">去商品管理</a>',
      '        </div>',

      '        <div class="hm-empty" v-if="!lowStockItems.length" style="padding:36px 12px">',
      '          <p>当前样本内没有库存紧张的商品。</p>',
      '        </div>',

      '        <div v-else>',
      '          <div v-for="it in lowStockItems" :key="it.id"',
      '               style="display:flex;align-items:center;gap:12px;padding:12px 24px;',
      '                      border-top:1px solid var(--hm-line)">',
      '            <span style="flex:0 0 auto;width:40px;height:40px;border-radius:10px;',
      '                         overflow:hidden;background:#EEF1F6">',
      '              <img v-if="it.image" :src="it.image" alt=""',
      '                   style="width:100%;height:100%;object-fit:cover"/>',
      '            </span>',
      '            <span style="flex:1 1 auto;min-width:0">',
      '              <span style="display:block;font-size:13px;font-weight:600;color:var(--hm-ink);',
      '                           overflow:hidden;text-overflow:ellipsis;white-space:nowrap">',
      '                {{ it.name }}',
      '              </span>',
      '              <span style="font-size:11.5px;color:var(--hm-muted)">',
      '                {{ it.category || \'未分类\' }} · ￥{{ fmt.price(it.price) }}',
      '              </span>',
      '            </span>',
      '            <span class="hm-tag"',
      '                  :class="Number(it.stock) <= 0 ? \'hm-tag--danger\' : \'hm-tag--success\'">',
      '              库存 {{ it.stock }}',
      '            </span>',
      '          </div>',
      '        </div>',
      '      </div>',
      '    </div>',

      // ============================== 价格分布 ==============================
      '    <div class="hm-panel" style="margin-top:18px">',
      '      <div class="hm-panel__head">',
      '        <div class="hm-panel__title">',
      '          价格区间分布',
      '          <small>{{ sampleText }}</small>',
      '        </div>',
      '      </div>',
      '      <div style="display:flex;flex-wrap:wrap;gap:12px">',
      '        <div v-for="b in priceBuckets" :key="b.label"',
      '             style="flex:1 1 150px;padding:14px 16px;border:1px solid var(--hm-line);',
      '                    border-radius:12px;background:#FBFCFE">',
      '          <div style="font-size:12.5px;color:var(--hm-muted)">{{ b.label }}</div>',
      '          <div style="font-family:var(--hm-font-num);font-size:22px;font-weight:700;',
      '                      color:var(--hm-ink);margin-top:4px">{{ fmt.num(b.count) }}</div>',
      '          <div style="font-size:11.5px;color:var(--hm-muted);margin-top:2px">',
      '            {{ b.ratio }}%',
      '          </div>',
      '        </div>',
      '      </div>',
      '    </div>',

      '  </template>',
      '</div>'
    ].join("\n"),

    data: function () {
      return {
        loading: true,
        loaded: false,
        error: "",

        total: 0,
        sample: [],

        categoryStats: [],
        categoryLoading: false,

        chart: null
      };
    },

    created: function () {
      if (!util.isLogin()) {
        return;
      }
      this.load();
    },

    beforeDestroy: function () {
      if (this.chart) {
        this.chart.dispose();
        this.chart = null;
      }
      window.removeEventListener("resize", this.resizeChart);
    },

    methods: {
      resizeChart: function () {
        if (this.chart) {
          this.chart.resize();
        }
      },

      load: function () {
        var self = this;
        this.loading = true;
        this.error = "";

        axios.get("/items/page", { params: { pageNo: 1, pageSize: SAMPLE_SIZE } })
          .then(function (resp) {
            var data = resp && resp.data;
            if (!data) {
              throw new Error("接口未返回分页数据");
            }
            self.total = Number(data.total) || 0;
            self.sample = Array.isArray(data.list) ? data.list : [];
            self.loaded = true;

            // 图表数据来自逐分类真实 total，与样本统计相互独立
            return self.loadCategoryTotals();
          })
          .catch(function (err) {
            self.error = hmAdmErrorText(err, "商品数据加载失败，请稍后重试");
          })
          .then(function () {
            self.loading = false;
          });
      },

      /**
       * 逐分类读取真实总量：
       * 分页接口支持 category 精确过滤，因此对每个分类发一次 pageSize=1 的请求，
       * 只取返回的 total 即可得到该分类的真实全量，而不是靠样本推算。
       */
      loadCategoryTotals: function () {
        var self = this;

        var names = [];
        this.sample.forEach(function (i) {
          var c = i.category && String(i.category).trim();
          if (c && names.indexOf(c) === -1) {
            names.push(c);
          }
        });
        names = names.slice(0, MAX_CATEGORY_QUERIES);

        if (!names.length) {
          this.categoryStats = [];
          return Promise.resolve();
        }

        this.categoryLoading = true;

        return Promise.all(names.map(function (name) {
          return axios.get("/items/page", {
            params: { pageNo: 1, pageSize: 1, category: name }
          })
            .then(function (r) {
              var d = r && r.data;
              return { name: name, count: d ? Number(d.total) || 0 : null };
            })
            .catch(function () {
              // 单个分类统计失败不影响其他分类
              return { name: name, count: null };
            });
        }))
          .then(function (list) {
            self.categoryStats = list
              .filter(function (x) { return x.count != null; })
              .sort(function (a, b) { return b.count - a.count; });
            self.$nextTick(function () {
              self.renderChart();
            });
          })
          .then(function () {
            self.categoryLoading = false;
          });
      },

      renderChart: function () {
        var el = this.$refs.chart;
        if (!el || !window.echarts || !this.categoryStats.length) {
          return;
        }

        if (!this.chart) {
          this.chart = window.echarts.init(el);
          // methods 已被 Vue 绑定到实例，事件回调里 this 仍然指向组件
          window.addEventListener("resize", this.resizeChart);
        }

        var top = this.categoryStats.slice(0, 10);
        var names = top.map(function (c) { return c.name; });
        var values = top.map(function (c) { return c.count; });

        this.chart.setOption({
          grid: { left: 8, right: 56, top: 16, bottom: 8, containLabel: true },
          tooltip: {
            trigger: "axis",
            axisPointer: { type: "shadow" },
            backgroundColor: "#111B36",
            borderWidth: 0,
            textStyle: { color: "#fff", fontSize: 12 },
            formatter: function (params) {
              var p = params[0];
              return p.name + "<br/>商品总数：" + Number(p.value).toLocaleString("zh-CN") + " 条";
            }
          },
          xAxis: {
            type: "value",
            axisLine: { show: false },
            axisTick: { show: false },
            splitLine: { lineStyle: { color: "rgba(17,27,54,0.08)" } },
            axisLabel: {
              color: "#65708A",
              fontSize: 11,
              formatter: function (v) {
                return v >= 10000 ? (v / 10000) + "万" : v;
              }
            }
          },
          yAxis: {
            type: "category",
            // 倒序：数量最多的排最上方
            data: names.slice().reverse(),
            axisLine: { show: false },
            axisTick: { show: false },
            axisLabel: { color: "#4A5570", fontSize: 12 }
          },
          series: [{
            name: "商品总数",
            type: "bar",
            barWidth: 14,
            itemStyle: {
              borderRadius: [0, 7, 7, 0],
              color: new window.echarts.graphic.LinearGradient(0, 0, 1, 0, [
                { offset: 0, color: "#315EFB" },
                { offset: 1, color: "#7C4DFF" }
              ])
            },
            data: values.slice().reverse(),
            label: {
              show: true,
              position: "right",
              color: "#4A5570",
              fontSize: 11,
              fontFamily: "monospace",
              formatter: function (p) {
                return Number(p.value).toLocaleString("zh-CN");
              }
            }
          }]
        }, true);
      }
    },

    computed: {
      fmt: function () {
        return window.hmFormat;
      },

      onSale: function () {
        return this.sample.filter(function (i) { return i.status === 1; }).length;
      },

      offSale: function () {
        return this.sample.filter(function (i) { return i.status !== 1; }).length;
      },

      lowStockCount: function () {
        // 只统计已上架商品：下架商品不需要补货
        return this.sample.filter(function (i) {
          return i.status === 1 && Number(i.stock) <= LOW_STOCK;
        }).length;
      },

      lowStockItems: function () {
        return this.sample
          .filter(function (i) {
            return i.status === 1 && Number(i.stock) <= LOW_STOCK;
          })
          .sort(function (a, b) { return Number(a.stock) - Number(b.stock); })
          .slice(0, 6);
      },

      isPartial: function () {
        return this.total > this.sample.length;
      },

      sampleText: function () {
        if (!this.sample.length) {
          return "暂无数据";
        }
        return this.isPartial
          ? "基于最近 " + this.sample.length + " 条样本"
          : "基于全部 " + this.sample.length + " 条商品";
      },

      sampleRatio: function () {
        if (!this.total) {
          return "0";
        }
        return (this.sample.length / this.total * 100).toFixed(1);
      },

      priceBuckets: function () {
        var defs = [
          { label: "0 - 99 元", min: 0, max: 9900 },
          { label: "100 - 499 元", min: 9900, max: 49900 },
          { label: "500 - 1999 元", min: 49900, max: 199900 },
          { label: "2000 元以上", min: 199900, max: Infinity }
        ];
        var sample = this.sample;
        var total = sample.length || 1;
        return defs.map(function (d) {
          var count = sample.filter(function (i) {
            var p = Number(i.price) || 0;
            return p >= d.min && p < d.max;
          }).length;
          return {
            label: d.label,
            count: count,
            ratio: (count / total * 100).toFixed(1)
          };
        });
      }
    }
  });

  // 供 index.html 的 ViewRouter 使用（IIFE 内的 const 不是全局变量）
  window.Dashboard = Dashboard;
})();
