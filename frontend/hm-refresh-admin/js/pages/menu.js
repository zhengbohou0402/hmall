/* ==========================================================================
   黑马商城管理系统 · 侧栏菜单  js/pages/menu.js
   --------------------------------------------------------------------------
   重要变更（相对旧版）：
     · 不再调用 /users/{id}/menus。该接口在本项目并不存在，旧版因此会让侧栏
       变成一整条不可点击的空黑栏。现在菜单是静态声明，与真实路由一一对应。
     · 菜单由 Element 的 el-menu 改为自绘深色导航，视觉与后台设计系统统一。
   菜单项：
     /index      数据概览
     /goods/list 商品管理
   ========================================================================== */
(function () {
  "use strict";

  var ICONS = {
    dashboard:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"' +
      ' stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<rect x="3" y="3" width="7.5" height="7.5" rx="2"></rect>' +
      '<rect x="13.5" y="3" width="7.5" height="4.8" rx="2"></rect>' +
      '<rect x="3" y="13.5" width="7.5" height="7.5" rx="2"></rect>' +
      '<rect x="13.5" y="10.3" width="7.5" height="10.7" rx="2"></rect></svg>',
    goods:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"' +
      ' stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="M20.5 7.2 12 3 3.5 7.2v9.6L12 21l8.5-4.2z"></path>' +
      '<path d="M3.5 7.2 12 11.4l8.5-4.2"></path>' +
      '<path d="M12 11.4V21"></path></svg>'
  };

  var menu = {
    template:
      '<nav class="hm-admin__nav" aria-label="后台主导航">' +
      '  <div class="hm-admin__nav-group">导航</div>' +
      '  <a class="hm-nav-item" v-for="m in menus" :key="m.path"' +
      '     :href="\'#\' + m.path"' +
      '     :class="{ \'is-active\': isActive(m.path) }"' +
      '     :title="m.name">' +
      '    <span class="hm-nav-item__icon" v-html="m.icon"></span>' +
      '    <span class="hm-nav-item__text">{{ m.name }}</span>' +
      '    <span class="hm-nav-item__badge" v-if="m.path === \'/goods/list\' && goodsTotal > 0">' +
      '      {{ goodsTotal }}' +
      '    </span>' +
      '  </a>' +
      '</nav>',

    data: function () {
      return {
        menus: [
          { name: "数据概览", path: "/index", icon: ICONS.dashboard },
          { name: "商品管理", path: "/goods/list", icon: ICONS.goods }
        ],
        goodsTotal: 0,
        curr: "/index"
      };
    },

    created: function () {
      var self = this;

      // 与自研 ViewRouter 保持一致：直接读 hash
      var sync = function () {
        self.curr = location.hash ? location.hash.slice(1) : "/index";
      };
      sync();
      this._onHash = sync;
      window.addEventListener("hashchange", sync);

      this.loadGoodsTotal();
    },

    beforeDestroy: function () {
      window.removeEventListener("hashchange", this._onHash);
    },

    methods: {
      isActive: function (path) {
        if (this.curr === path) {
          return true;
        }
        // "/" 视为数据概览
        return path === "/index" && (this.curr === "/" || this.curr === "");
      },

      /** 侧栏徽标：真实的商品总数（GET /items/page 的 total 字段） */
      loadGoodsTotal: function () {
        var self = this;
        if (!util.isLogin()) {
          return;
        }
        // 只取 1 条，仅为了拿到 total，避免拉全量数据
        axios.get("/items/page", { params: { pageNo: 1, pageSize: 1 } })
          .then(function (resp) {
            var data = resp && resp.data;
            if (data && data.total != null) {
              self.goodsTotal = Number(data.total) || 0;
            }
          })
          .catch(function () {
            // 徽标属于辅助信息，失败静默降级为不显示
            self.goodsTotal = 0;
          });
      }
    }
  };

  Vue.component("menu-list", menu);
})();
