/* ==========================================================================
   黑马商城 · 首页脚本（Vue 2，无构建工具、无 npm 依赖）
   依赖：js/vue.js、js/axios.min.js、js/common.js（提供 util 与 axios 拦截器）
   说明：接口全部经由 Nginx /api 前缀转发到网关，本文件不硬编码任何后端地址
   ========================================================================== */
(function () {
  "use strict";

  /** 与后端一致的默认分页 */
  var HOT_PAGE_SIZE = 8;

  /** 搜索建议防抖间隔（毫秒） */
  var SUGGEST_DEBOUNCE = 250;

  /** 首页静态运营位：黑马精选（价格为店内固定售价，单位元；搜索走真实接口） */
  var PICKS = [
    {
      tag: "数码影音",
      title: "轻享降噪耳机",
      desc: "沉浸音场，随时在线",
      price: 299,
      variant: "",
      href: "/search.html?key=" + encodeURIComponent("黑马精选 耳机")
    },
    {
      tag: "通勤美学",
      title: "极简双肩通勤包",
      desc: "容量与秩序，恰到好处",
      price: 189,
      variant: "hm-pick--ink",
      href: "/search.html?key=" + encodeURIComponent("黑马精选 双肩包")
    },
    {
      tag: "品质生活",
      title: "智能保温随行杯",
      desc: "把温度带在身边",
      price: 99,
      variant: "hm-pick--violet",
      href: "/search.html?key=" + encodeURIComponent("黑马精选 保温杯")
    }
  ];

  /** 分类快捷入口：全部指向真实搜索链接 */
  var CATEGORIES = [
    { title: "数码影音", sub: "耳机 · 音响 · 影音", key: "耳机" },
    { title: "品质生活", sub: "保温杯 · 家居好物", key: "保温杯" },
    { title: "手机", sub: "旗舰机型 · 全网通", key: "手机" },
    { title: "通勤好物", sub: "双肩包 · 旅行箱", key: "双肩包" }
  ];

  /** HTML 转义，避免把后端返回内容当标签渲染 */
  function escapeHtml(text) {
    return String(text === null || text === undefined ? "" : text)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  /** 去掉 ES 高亮标签，只保留纯文本（用于自定义渲染） */
  function stripTags(text) {
    return String(text === null || text === undefined ? "" : text).replace(/<\/?em>/g, "");
  }

  new Vue({
    el: "#indexApp",
    data: function () {
      return {
        util: util,                 // 暴露给模板使用（登录态、格式化价格等）

        // 顶部导航
        user: null,

        // 搜索
        key: "",
        suggestions: [],
        suggestIndex: -1,
        showSuggest: false,
        suggestLoading: false,
        suggestTimer: null,

        // 头部粘性状态
        scrolled: false,

        // 购物车
        cartCount: 0,

        // 实时热销
        hotItems: [],
        hotLoading: true,
        hotError: false,

        // 在售商品总数（来自 /search/list 的 total，避免首页写死夸大数字）
        itemTotal: null,

        // 静态运营位
        picks: PICKS,
        categories: CATEGORIES
      };
    },
    created: function () {
      // 登录后回跳地址（沿用既有约定）
      util.store.set("return-url", location.href);
      this.user = util.store.get("user-info");
      this.loadCart();
      this.loadHotItems();
    },
    mounted: function () {
      window.addEventListener("scroll", this.handleScroll, { passive: true });
      document.addEventListener("click", this.closeSuggest);
      this.handleScroll();
    },
    beforeDestroy: function () {
      window.removeEventListener("scroll", this.handleScroll);
      document.removeEventListener("click", this.closeSuggest);
      clearTimeout(this.suggestTimer);
    },
    methods: {
      /* ------------------------------ 搜索 ------------------------------ */

      /** 输入：防抖后拉取搜索建议 */
      onInput: function () {
        var self = this;
        clearTimeout(this.suggestTimer);
        var kw = (this.key || "").trim();
        if (!kw) {
          this.suggestions = [];
          this.showSuggest = false;
          return;
        }
        this.suggestTimer = setTimeout(function () {
          self.loadSuggestions(kw);
        }, SUGGEST_DEBOUNCE);
      },

      loadSuggestions: function (kw) {
        var self = this;
        this.suggestLoading = true;
        axios.get("/search/suggestion", { params: { key: kw } })
          .then(function (resp) {
            var list = Array.isArray(resp) ? resp : (resp && resp.data) || [];
            self.suggestions = list.slice(0, 8);
            self.suggestIndex = -1;
            // 返回结果的关键字与当前输入不一致时不再展示，避免过期建议
            self.showSuggest = self.suggestions.length > 0 && (self.key || "").trim() === kw;
          })
          .catch(function (err) {
            console.log(err);
            self.suggestions = [];
            self.showSuggest = false;
          })
          .then(function () {
            self.suggestLoading = false;
          });
      },

      /** 键盘：回车搜索 / ESC 关闭 / 上下键选择建议 */
      onKeyDown: function (e) {
        if (e.keyCode === 13) {
          e.preventDefault();
          if (this.showSuggest && this.suggestIndex > -1 && this.suggestions[this.suggestIndex]) {
            this.pickSuggestion(this.suggestions[this.suggestIndex]);
          } else {
            this.goSearch();
          }
          return;
        }
        if (e.keyCode === 27) {
          this.showSuggest = false;
          return;
        }
        if (!this.suggestions.length) {
          return;
        }
        if (e.keyCode === 40) {
          e.preventDefault();
          this.showSuggest = true;
          this.suggestIndex = (this.suggestIndex + 1) % this.suggestions.length;
        } else if (e.keyCode === 38) {
          e.preventDefault();
          this.showSuggest = true;
          this.suggestIndex = this.suggestIndex <= 0 ? this.suggestions.length - 1 : this.suggestIndex - 1;
        }
      },

      onFocus: function () {
        if (this.suggestions.length > 0) {
          this.showSuggest = true;
        }
      },

      closeSuggest: function () {
        this.showSuggest = false;
      },

      /** 跳转搜索页（关键字做 URL 编码，交由 search.html 解析） */
      goSearch: function () {
        var kw = (this.key || "").trim();
        window.location.href = kw
          ? "/search.html?key=" + encodeURIComponent(kw)
          : "/search.html";
      },

      pickSuggestion: function (text) {
        this.showSuggest = false;
        this.suggestions = [];
        this.suggestIndex = -1;
        window.location.href = "/search.html?key=" + encodeURIComponent(stripTags(text));
      },

      /** 建议项高亮：先转义再包 <em>，避免 XSS */
      highlight: function (text) {
        var safe = escapeHtml(stripTags(text));
        var kw = (this.key || "").trim();
        if (!kw) {
          return safe;
        }
        var safeKw = escapeHtml(kw).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        try {
          return safe.replace(new RegExp(safeKw, "gi"), function (m) {
            return "<em>" + m + "</em>";
          });
        } catch (err) {
          return safe;
        }
      },

      /* ------------------------------ 购物车 ------------------------------ */

      loadCart: function () {
        var self = this;
        if (!util.isLogin()) {
          this.cartCount = 0;
          return;
        }
        axios.get("/carts")
          .then(function (resp) {
            var list = Array.isArray(resp) ? resp : [];
            self.cartCount = list.reduce(function (sum, it) {
              var num = Number(it && it.num);
              return sum + (isNaN(num) ? 1 : num);
            }, 0);
          })
          .catch(function (err) {
            console.log(err);
            self.cartCount = 0;
          });
      },

      addToCart: function (item) {
        if (!util.isLogin()) {
          util.store.set("return-url", location.href);
          window.location.href = "/login.html";
          return;
        }
        axios.post("/carts", {
          itemId: item.id,
          name: stripTags(item.name),
          price: item.price,
          image: item.image,
          spec: item.spec || null
        })
          .then(function () {
            window.location.href = "/cart.html";
          })
          .catch(function (err) {
            var msg = "加入购物车失败，请稍后重试";
            if (err && err.response && err.response.data && err.response.data.msg) {
              msg = err.response.data.msg;
            }
            alert(msg);
          });
      },

      /** 立即购买：写入结算页使用的 selectedCarts，而不是只拼接无效的商品 id。 */
      buyNow: function (item) {
        if (!util.isLogin()) {
          util.store.set("return-url", location.href);
          window.location.href = "/login.html";
          return;
        }
        util.store.set("selectedCarts", [{
          itemId: item.id,
          num: 1,
          name: stripTags(item.name),
          price: item.price,
          newPrice: item.newPrice || item.price,
          stock: item.stock || 999,
          image: item.image,
          spec: item.spec || "{}"
        }]);
        window.location.href = "/order-confirm.html";
      },

      /* ------------------------------ 实时热销 ------------------------------ */

      loadHotItems: function () {
        var self = this;
        this.hotLoading = true;
        this.hotError = false;
        axios.get("/search/list", {
          params: {
            pageNo: 1,
            pageSize: HOT_PAGE_SIZE,
            sortBy: "sold",
            isAsc: false
          }
        })
          .then(function (resp) {
            var list = (resp && resp.list) || [];
            self.hotItems = list.map(function (it) {
              return it;
            });
          })
          .catch(function (err) {
            console.log(err);
            self.hotItems = [];
            self.hotError = true;
          })
          .then(function () {
            self.hotLoading = false;
          });
      },

      /** 图片加载失败 → 切到 CSS 占位（$set 保证 Vue 2 响应式） */
      onImgError: function (item) {
        this.$set(item, "imgFailed", true);
      },

      /** 商品名去高亮标签 */
      itemName: function (item) {
        return stripTags(item && item.name);
      },

      /** 商品名截断（占位区展示） */
      shortName: function (item) {
        var name = stripTags(item && item.name);
        return name.length > 12 ? name.slice(0, 12) + "…" : name;
      },

      /** 价格：分 → 元，复用既有 util.formatPrice */
      formatPrice: function (price) {
        var val = util.formatPrice(price);
        return val === null || val === undefined ? "0.00" : val;
      },

      /** 销量文案 */
      soldText: function (sold) {
        var num = Number(sold);
        if (isNaN(num) || num <= 0) {
          return "新品上架";
        }
        return num >= 10000 ? "已售 " + Math.floor(num / 10000) + "万+" : "已售 " + num;
      },

      /** 商品详情搜索链接（点击卡片进入搜索页） */
      itemSearchHref: function (item) {
        return "/search.html?key=" + encodeURIComponent(this.itemName(item));
      },

      /* ------------------------------ 其它 ------------------------------ */

      logout: function () {
        util.logout();
      },

      handleScroll: function () {
        this.scrolled = (window.pageYOffset || document.documentElement.scrollTop || 0) > 8;
      }
    },

    computed: {
      /* 在售好物文案：直接使用后端返回的真实 total。
         拿不到数据时返回空串，模板会整块隐藏 —— 不展示任何编造的规模数字。 */
      itemTotalText: function () {
        if (this.itemTotal == null || this.itemTotal <= 0) {
          return "";
        }
        if (this.itemTotal >= 10000) {
          // 88479 → 8.8万
          return (this.itemTotal / 10000).toFixed(1).replace(/\.0$/, "") + " 万";
        }
        return String(this.itemTotal);
      }
    }
  });
})();
