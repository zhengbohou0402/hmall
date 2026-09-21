/* ==========================================================================
   黑马商城管理系统 · 顶栏  js/pages/banner.js
   --------------------------------------------------------------------------
   重要变更（相对旧版）：
     · 更名「黑马商城管理系统」（旧版文案误用了非本项目品牌的动物名，属品牌错误）。
     · 使用 images/black-horse-mark.svg 品牌标识，不再依赖 images/1.png 旧图。
     · 去掉「主题颜色选择器」（旧版让运营自己调菜单底色，与设计系统冲突）。
     · 保留真实的登录态校验与退出登录；未登录直接跳 login.html。
     · 通过 window.adminState 与侧栏共享折叠状态。
   ========================================================================== */
(function () {
  "use strict";

  var ROUTE_META = {
    "/": { title: "数据概览", sub: "商品与库存实时概览" },
    "/index": { title: "数据概览", sub: "商品与库存实时概览" },
    "/goods/list": { title: "商品管理", sub: "查询、新增、编辑、上下架与删除商品" }
  };

  var banner = {
    template:
      '<header class="hm-admin__topbar">' +
      '  <button type="button" class="hm-admin__toggle" @click="toggleMini"' +
      '          :aria-label="adminState.mini ? \'展开侧栏\' : \'收起侧栏\'">' +
      '    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"' +
      '         stroke-width="2" stroke-linecap="round" aria-hidden="true">' +
      '      <path d="M4 6.5h16"></path><path d="M4 12h16"></path><path d="M4 17.5h16"></path>' +
      '    </svg>' +
      '  </button>' +
      '  <div class="hm-admin__title">' +
      '    <b>{{ meta.title }}</b>' +
      '    <span>{{ meta.sub }}</span>' +
      '  </div>' +
      '  <div class="hm-admin__topbar-right">' +
      '    <span class="hm-tag hm-tag--muted" title="当前环境接口前缀">接口前缀 /api</span>' +
      '    <a class="hm-btn hm-btn--ghost" href="/index.html#/index" style="height:34px"' +
      '       title="回到数据概览">刷新概览</a>' +
      '    <div class="hm-admin__user" @click.stop="menuOpen = !menuOpen" tabindex="0"' +
      '         @keydown.enter.prevent="menuOpen = !menuOpen">' +
      '      <span class="hm-admin__avatar">{{ initial }}</span>' +
      '      <b>{{ user.name }}</b>' +
      '      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"' +
      '           stroke-width="2.4" stroke-linecap="round" aria-hidden="true">' +
      '        <path d="M6 9.5l6 6 6-6"></path>' +
      '      </svg>' +
      '      <div class="hm-admin__menu" v-show="menuOpen" @click.stop>' +
      '        <button type="button" @click="goConsole">打开用户端首页</button>' +
      '        <hr/>' +
      '        <button type="button" @click="logout">退出登录</button>' +
      '      </div>' +
      '    </div>' +
      '  </div>' +
      '</header>',

    data: function () {
      return {
        adminState: window.adminState,
        user: { name: "未登录" },
        curr: "/index",
        menuOpen: false
      };
    },

    created: function () {
      // 真实的登录态校验：未登录直接回到登录页
      if (!util.isLogin()) {
        util.store.set("return-url", location.href);
        location.href = "/login.html";
        return;
      }

      var info = util.store.get("user-info") || {};
      this.user = {
        name: info.username || info.name || "管理员",
        username: info.username || ""
      };

      var self = this;
      var sync = function () {
        self.curr = location.hash ? location.hash.slice(1) : "/index";
        self.menuOpen = false;
      };
      sync();
      this._onHash = sync;
      window.addEventListener("hashchange", sync);
      document.addEventListener("click", this.closeMenu);
    },

    beforeDestroy: function () {
      window.removeEventListener("hashchange", this._onHash);
      document.removeEventListener("click", this.closeMenu);
    },

    methods: {
      closeMenu: function () {
        this.menuOpen = false;
      },

      toggleMini: function () {
        this.adminState.mini = !this.adminState.mini;
      },

      goConsole: function () {
        // 用户端门户运行在 18080 端口
        var host = location.hostname || "localhost";
        window.open(location.protocol + "//" + host + ":18080/", "_blank");
      },

      logout: function () {
        util.logout();
        location.href = "/login.html";
      }
    },

    computed: {
      meta: function () {
        return ROUTE_META[this.curr] || { title: "黑马商城管理系统", sub: "后台管理" };
      },

      initial: function () {
        var n = this.user && this.user.name ? String(this.user.name) : "A";
        return n.charAt(0).toUpperCase();
      }
    }
  };

  Vue.component("banner", banner);
})();
