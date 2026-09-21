/* ==========================================================================
   黑马商城 · 用户端共享 UI 脚本  modern-ui.js
   --------------------------------------------------------------------------
   提供：
     1. hmToast()   全局轻提示（替代原生 alert，视觉与设计系统一致）
     2. hmConfirm() 全局确认弹窗（Promise<boolean>，替代原生 confirm）
     3. hmUI        价格 / 手机号脱敏 / HTML 转义等工具函数
     4. <hm-header> 统一头部（Logo + 搜索 + 购物车 + 登录态）
     5. <hm-footer> 统一页脚
     6. <hm-steps>  结算流程步骤条
   依赖：js/vue.js、js/axios.min.js、js/common.js（提供 util / axios 拦截器）
   页面必须使用 body.hm-page 类，否则设计系统样式不生效。
   ========================================================================== */
(function (window, document) {
  "use strict";

  /* ======================================================================
     1. 全局轻提示
     ====================================================================== */
  var TOAST_HOST_ID = "hm-toast-host";
  var TOAST_ICONS = { success: "✓", error: "!", warn: "!", info: "i" };

  function getToastHost() {
    var host = document.getElementById(TOAST_HOST_ID);
    if (!host) {
      host = document.createElement("div");
      host.id = TOAST_HOST_ID;
      host.className = "hm-toast-host";
      host.setAttribute("role", "status");
      host.setAttribute("aria-live", "polite");
      document.body.appendChild(host);
    }
    return host;
  }

  /**
   * @param {string} message 提示文案
   * @param {'success'|'error'|'warn'|'info'} [type='info']
   * @param {number} [duration=2400] 毫秒
   */
  function hmToast(message, type, duration) {
    type = type || "info";
    duration = typeof duration === "number" ? duration : 2400;
    var host = getToastHost();

    // 最多同时展示 3 条，超出移除最早的
    while (host.children.length >= 3) {
      host.removeChild(host.firstChild);
    }

    var el = document.createElement("div");
    el.className = "hm-toast hm-toast--" + type;

    var icon = document.createElement("span");
    icon.className = "hm-toast__icon";
    icon.textContent = TOAST_ICONS[type] || "i";

    var text = document.createElement("span");
    text.textContent = message == null ? "" : String(message);

    el.appendChild(icon);
    el.appendChild(text);
    host.appendChild(el);

    window.setTimeout(function () {
      el.classList.add("is-leaving");
      window.setTimeout(function () {
        if (el.parentNode) {
          el.parentNode.removeChild(el);
        }
      }, 240);
    }, duration);

    return el;
  }

  /* ======================================================================
     2. 全局确认弹窗（Promise<boolean>）
     ====================================================================== */
  var CONFIRM_STYLE_ID = "hm-confirm-style";
  var CONFIRM_CSS =
    ".hm-confirm-mask{position:fixed;inset:0;z-index:9998;display:flex;align-items:center;" +
    "justify-content:center;padding:20px;background:rgba(17,27,54,.42);backdrop-filter:blur(3px);" +
    "animation:hm-toast-in .2s cubic-bezier(.22,.61,.36,1)}" +
    ".hm-confirm{width:100%;max-width:390px;padding:26px;background:#fff;border-radius:18px;" +
    "box-shadow:0 22px 60px rgba(17,27,54,.28);font-family:inherit}" +
    ".hm-confirm h3{margin:0 0 10px;font-size:17px;font-weight:600;color:#111B36}" +
    ".hm-confirm p{margin:0;font-size:13.5px;line-height:1.65;color:#4A5570}" +
    ".hm-confirm__actions{display:flex;gap:10px;margin-top:24px}" +
    ".hm-confirm__actions button{flex:1 1 0;height:44px;border:1.5px solid transparent;" +
    "border-radius:999px;font-family:inherit;font-size:14px;font-weight:600;cursor:pointer;" +
    "transition:background .18s,color .18s,border-color .18s}" +
    ".hm-confirm__cancel{background:#fff;color:#1B2437;border-color:rgba(17,27,54,.14)}" +
    ".hm-confirm__cancel:hover{border-color:#111B36}" +
    ".hm-confirm__ok{background:#315EFB;color:#fff}" +
    ".hm-confirm__ok:hover{background:#2547D0}" +
    ".hm-confirm__ok.is-danger{background:#D92D20}" +
    ".hm-confirm__ok.is-danger:hover{background:#B42318}";

  /**
   * @param {string} message 正文
   * @param {{title?:string, okText?:string, cancelText?:string, danger?:boolean}} [opts]
   * @returns {Promise<boolean>}
   */
  function hmConfirm(message, opts) {
    opts = opts || {};
    if (!document.getElementById(CONFIRM_STYLE_ID)) {
      var style = document.createElement("style");
      style.id = CONFIRM_STYLE_ID;
      style.textContent = CONFIRM_CSS;
      document.head.appendChild(style);
    }

    return new Promise(function (resolve) {
      var mask = document.createElement("div");
      mask.className = "hm-confirm-mask";

      var box = document.createElement("div");
      box.className = "hm-confirm";
      box.setAttribute("role", "dialog");
      box.setAttribute("aria-modal", "true");

      var title = document.createElement("h3");
      title.textContent = opts.title || "请确认";

      var body = document.createElement("p");
      body.textContent = message == null ? "" : String(message);

      var actions = document.createElement("div");
      actions.className = "hm-confirm__actions";

      var cancel = document.createElement("button");
      cancel.type = "button";
      cancel.className = "hm-confirm__cancel";
      cancel.textContent = opts.cancelText || "取消";

      var ok = document.createElement("button");
      ok.type = "button";
      ok.className = "hm-confirm__ok" + (opts.danger ? " is-danger" : "");
      ok.textContent = opts.okText || "确定";

      function close(result) {
        document.removeEventListener("keydown", onKey, true);
        if (mask.parentNode) {
          mask.parentNode.removeChild(mask);
        }
        resolve(result);
      }

      function onKey(e) {
        if (e.key === "Escape") {
          e.stopPropagation();
          close(false);
        } else if (e.key === "Enter") {
          e.stopPropagation();
          close(true);
        }
      }

      cancel.addEventListener("click", function () {
        close(false);
      });
      ok.addEventListener("click", function () {
        close(true);
      });
      mask.addEventListener("click", function (e) {
        if (e.target === mask) {
          close(false);
        }
      });
      document.addEventListener("keydown", onKey, true);

      actions.appendChild(cancel);
      actions.appendChild(ok);
      box.appendChild(title);
      box.appendChild(body);
      box.appendChild(actions);
      mask.appendChild(box);
      document.body.appendChild(mask);

      window.setTimeout(function () {
        ok.focus();
      }, 30);
    });
  }

  /* ======================================================================
     3. 工具函数
     ====================================================================== */
  function escapeHtml(str) {
    if (str == null) {
      return "";
    }
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function stripTags(str) {
    if (str == null) {
      return "";
    }
    return String(str).replace(/<[^>]*>/g, "");
  }

  var hmUI = {
    /** 分 → 元（复用 common.js 的 util.formatPrice，保证全站一致） */
    price: function (cents) {
      // 用 util.formatPrice 兜底，避免页面未引入 common.js 时报错
      if (typeof util !== "undefined" && util && typeof util.formatPrice === "function") {
        return util.formatPrice(cents);
      }
      if (cents == null || isNaN(cents)) {
        return "0.00";
      }
      return (Number(cents) / 100).toFixed(2);
    },
    /** 手机号脱敏：13301212233 → 1330****233 */
    mobile: function (mobile) {
      if (!mobile) {
        return "";
      }
      var m = String(mobile);
      if (m.length < 11) {
        return m;
      }
      return m.substring(0, 4) + "****" + m.substring(8, 11);
    },
    /** 纯文本（去掉后端 ES 高亮的 <em> 标签） */
    plain: stripTags,
    /** HTML 转义 */
    escape: escapeHtml,
    /**
     * 后端搜索建议是「纯文本」，但部分接口会返回带 <em> 高亮的内容。
     * 这里只允许 <em> 一种标签通过，其余全部转义，避免 XSS 与标签泄漏。
     */
    safeHighlight: function (str) {
      if (str == null) {
        return "";
      }
      return String(str)
        .split(/(<em>|<\/em>)/gi)
        .map(function (part) {
          var lower = part.toLowerCase();
          if (lower === "<em>" || lower === "</em>") {
            return lower;
          }
          return escapeHtml(part);
        })
        .join("");
    },
    /** 销量文案 */
    sold: function (sold) {
      if (!sold || sold < 0) {
        return "暂无销量";
      }
      if (sold >= 10000) {
        return "已售 " + (sold / 10000).toFixed(1) + " 万件";
      }
      return "已售 " + sold + " 件";
    },
    /** 金额格式化（分 → 带两位小数的数字字符串） */
    money: function (cents) {
      var n = Number(cents);
      if (isNaN(n)) {
        return "0.00";
      }
      return (n / 100).toFixed(2);
    },
    /** 把毫秒差格式化为「12分34秒」 */
    countdown: function (ms) {
      if (ms <= 0 || isNaN(ms)) {
        return "0分0秒";
      }
      var total = Math.floor(ms / 1000);
      var m = Math.floor(total / 60);
      var s = total % 60;
      return m + "分" + s + "秒";
    }
  };

  /* ======================================================================
     4. <hm-header> 统一头部
     props:
       active  String  当前高亮项：'' | 'cart'（用于无样式差异时的语义标记）
       search  Boolean 是否展示搜索框（默认 true）
     ====================================================================== */
  var HmHeader = {
    name: "HmHeader",
    props: {
      active: { type: String, default: "" },
      search: { type: Boolean, default: true }
    },
    data: function () {
      return {
        user: null,
        cartCount: 0,
        key: "",
        suggestions: [],
        showSuggest: false,
        suggestIndex: -1,
        suggestLoading: false,
        suggestTimer: null
      };
    },
    created: function () {
      this.user = util.store.get("user-info");
      this.loadCartCount();
      this.key = util.getUrlParam("key") || "";
    },
    beforeDestroy: function () {
      if (this.suggestTimer) {
        clearTimeout(this.suggestTimer);
      }
    },
    methods: {
      loadCartCount: function () {
        // 未登录时不要请求 /carts：401 会被拦截器重定向到登录页
        if (!util.isLogin()) {
          return;
        }
        var self = this;
        axios.get("/carts").then(function (resp) {
          if (!resp || !resp.length) {
            self.cartCount = 0;
            return;
          }
          self.cartCount = resp.reduce(function (sum, c) {
            return sum + (Number(c.num) || 0);
          }, 0);
        }).catch(function () {
          // 购物车数量属于辅助信息，失败静默处理，不打断页面
          self.cartCount = 0;
        });
      },
      onInput: function () {
        var self = this;
        var kw = (this.key || "").trim();
        if (this.suggestTimer) {
          clearTimeout(this.suggestTimer);
        }
        if (!kw) {
          this.suggestions = [];
          this.showSuggest = false;
          return;
        }
        this.suggestTimer = setTimeout(function () {
          self.fetchSuggestions(kw);
        }, 280);
      },
      fetchSuggestions: function (kw) {
        var self = this;
        this.suggestLoading = true;
        axios.get("/search/suggestion", { params: { key: kw } })
          .then(function (resp) {
            var list = Array.isArray(resp) ? resp : [];
            if ((self.key || "").trim() !== kw) {
              return;
            }
            self.suggestions = list.slice(0, 8);
            self.suggestIndex = -1;
            self.showSuggest = self.suggestions.length > 0;
          })
          .catch(function () {
            self.suggestions = [];
            self.showSuggest = false;
          })
          .then(function () {
            self.suggestLoading = false;
          });
      },
      onFocus: function () {
        if (this.suggestions.length) {
          this.showSuggest = true;
        }
      },
      hideSuggest: function () {
        this.showSuggest = false;
        this.suggestIndex = -1;
      },
      onKeyDown: function (e) {
        if (e.key === "Escape") {
          this.hideSuggest();
          return;
        }
        if (!this.showSuggest || !this.suggestions.length) {
          return;
        }
        if (e.key === "ArrowDown") {
          e.preventDefault();
          this.suggestIndex = (this.suggestIndex + 1) % this.suggestions.length;
        } else if (e.key === "ArrowUp") {
          e.preventDefault();
          this.suggestIndex = this.suggestIndex <= 0
            ? this.suggestions.length - 1
            : this.suggestIndex - 1;
        } else if (e.key === "Enter") {
          e.preventDefault();
          if (this.suggestIndex > -1) {
            this.pickSuggestion(this.suggestions[this.suggestIndex]);
          } else {
            this.submit();
          }
        }
      },
      highlight: function (s) {
        return hmUI.safeHighlight(s);
      },
      submit: function () {
        var kw = (this.key || "").trim();
        if (!kw) {
          hmToast("请输入要搜索的商品关键词", "warn");
          return;
        }
        window.location.href = "/search.html?key=" + encodeURIComponent(kw);
      },
      pickSuggestion: function (s) {
        window.location.href = "/search.html?key=" + encodeURIComponent(hmUI.plain(s));
      },
      logout: function () {
        util.logout();
      }
    },
    template: [
      '<div>',
      '  <div class="hm-topbar">',
      '    <div class="hm-shell hm-topbar__inner">',
      '      <div class="hm-topbar__brand">黑马商城 <i>BLACK HORSE MALL</i></div>',
      '      <div class="hm-topbar__links">',
      '        <template v-if="!user">',
      '          <a href="/login.html">请登录</a>',
      '          <span class="hm-topbar__sep hm-topbar__extra"></span>',
      '          <a class="hm-topbar__extra" href="/login.html">免费注册</a>',
      '        </template>',
      '        <template v-else>',
      '          <span class="hm-topbar__user">你好，{{ user.username }}</span>',
      '          <a href="javascript:;" class="hm-topbar__logout" @click.prevent="logout">退出登录</a>',
      '        </template>',
      '        <span class="hm-topbar__sep"></span>',
      '        <a href="/cart.html">我的购物车</a>',
      '        <span class="hm-topbar__sep"></span>',
      '        <a href="/">返回首页</a>',
      '      </div>',
      '    </div>',
      '  </div>',
      '',
      '  <header class="hm-header">',
      '    <div class="hm-shell hm-header__inner">',
      '      <a class="hm-logo" href="/" aria-label="黑马商城首页">',
      '        <span class="hm-logo__mark"><img src="./img/black-horse-mark.svg" alt="黑马商城"></span>',
      '        <span class="hm-logo__text"><b>黑马商城</b><i>BLACK HORSE MALL</i></span>',
      '      </a>',
      '',
      '      <div class="hm-search" v-if="search" @click.stop>',
      '        <div class="hm-search__box">',
      '          <span class="hm-search__icon" aria-hidden="true">',
      '            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"',
      '                 stroke-width="2" stroke-linecap="round">',
      '              <circle cx="11" cy="11" r="7"></circle><path d="M20 20l-3.6-3.6"></path>',
      '            </svg>',
      '          </span>',
      '          <input type="text" v-model="key" @input="onInput" @keydown="onKeyDown"',
      '                 @focus="onFocus" autocomplete="off" aria-label="搜索商品"',
      '                 placeholder="搜索商品、品牌或分类，例如：黑马精选"/>',
      '          <button class="hm-search__submit" type="button" @click="submit">搜索</button>',
      '        </div>',
      '        <div class="hm-search__panel" v-show="showSuggest && suggestions.length" role="listbox">',
      '          <div class="hm-search__panel-head">搜索建议</div>',
      '          <a class="hm-search__item" :class="{ \'is-active\': suggestIndex === i }"',
      '             v-for="(s, i) in suggestions" :key="\'s\' + i" href="javascript:;" role="option"',
      '             @mouseenter="suggestIndex = i" @click.prevent="pickSuggestion(s)">',
      '            <span aria-hidden="true">⌕</span>',
      '            <span v-html="highlight(s)"></span>',
      '          </a>',
      '        </div>',
      '      </div>',
      '      <span class="hm-spacer" v-else></span>',
      '',
      '      <a class="hm-cart" href="/cart.html">',
      '        <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"',
      '             stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">',
      '          <path d="M4 5h2l2.2 10.2a2 2 0 0 0 2 1.6h7.2a2 2 0 0 0 2-1.5L21 8H7"></path>',
      '          <circle cx="10" cy="20" r="1.3"></circle><circle cx="18" cy="20" r="1.3"></circle>',
      '        </svg>',
      '        <span class="hm-cart__label">购物车</span>',
      '        <i class="hm-cart__badge" v-if="cartCount > 0">{{ cartCount }}</i>',
      '      </a>',
      '    </div>',
      '  </header>',
      '</div>'
    ].join("\n")
  };

  /* ======================================================================
     5. <hm-footer> 统一页脚
     ====================================================================== */
  var HmFooter = {
    name: "HmFooter",
    template: [
      '<footer class="hm-footer">',
      '  <div class="hm-shell hm-footer__inner">',
      '    <div class="hm-footer__brand">',
      '      <span class="hm-logo__mark"><img src="./img/black-horse-mark.svg" alt="黑马商城"></span>',
      '      <span><b>黑马商城</b><span>BLACK HORSE MALL</span></span>',
      '    </div>',
      '    <nav class="hm-footer__links">',
      '      <a href="/">首页</a>',
      '      <a href="/search.html">全部商品</a>',
      '      <a href="/cart.html">购物车</a>',
      '      <a href="/login.html">登录</a>',
      '    </nav>',
      '    <p class="hm-footer__copy">',
      '      <span>© 2026 黑马商城 · 教学演示项目</span>',
      '      <span>正品保障 · 24h 发货 · 7 天安心售后</span>',
      '    </p>',
      '  </div>',
      '</footer>'
    ].join("\n")
  };

  /* ======================================================================
     6. <hm-steps> 结算流程步骤条
     props: current Number 当前处于第几步（从 1 开始）
     ====================================================================== */
  var HmSteps = {
    name: "HmSteps",
    props: {
      current: { type: Number, default: 1 }
    },
    data: function () {
      return {
        items: [
          { n: 1, title: "我的购物车", sub: "确认商品" },
          { n: 2, title: "确认订单", sub: "填写核对信息" },
          { n: 3, title: "付款", sub: "选择支付方式" },
          { n: 4, title: "完成", sub: "查看订单" }
        ]
      };
    },
    computed: {
      // 每步的展示状态：done / active / ''
      states: function () {
        var cur = this.current;
        return this.items.map(function (it) {
          if (it.n < cur) {
            return "is-done";
          }
          return it.n === cur ? "is-active" : "";
        });
      }
    },
    template: [
      '<nav class="hm-steps" aria-label="下单流程">',
      '  <template v-for="(it, i) in items">',
      '    <div class="hm-step" :class="states[i]" :key="\'st\' + it.n">',
      '      <span class="hm-step__dot">',
      '        <template v-if="it.n < current">✓</template>',
      '        <template v-else>{{ it.n }}</template>',
      '      </span>',
      '      <span class="hm-step__text"><b>{{ it.title }}</b><span>{{ it.sub }}</span></span>',
      '    </div>',
      '    <span class="hm-step__line" v-if="i < items.length - 1" :key="\'ln\' + it.n"></span>',
      '  </template>',
      '</nav>'
    ].join("\n")
  };

  /* ======================================================================
     7. 统一错误文案提取
     后端统一响应体形如 { code, msg }；axios 层可能抛出超时/网络错误，
     这里统一翻译成用户能看懂的一句话，避免界面出现 undefined。
     ====================================================================== */
  function hmErrorText(err, fallback) {
    fallback = fallback || "操作失败，请稍后重试";
    if (!err) {
      return fallback;
    }
    // 超时
    if (err.code === "ECONNABORTED" || /timeout/i.test(err.message || "")) {
      return "请求超时，请确认后端服务已启动后重试";
    }
    var status = err.response && err.response.status;

    /* ------------------------------------------------------------------
       优先透出后端业务文案。
       hmall 的全局异常处理器对 BizIllegalException 返回 500 + 有意义的中文 msg
       （例如「订单已经支付！」「库存不足」），若先按状态码兜底，这些文案会被
       泛化成「服务端异常（500）」，用户看到的信息量归零。因此这里把 data.msg
       提到状态码分支之前。
       ------------------------------------------------------------------ */
    var data = err.response && err.response.data;
    if (data && typeof data === "object") {
      var bizMsg = data.msg || data.message || data.error;
      if (bizMsg && String(bizMsg).trim()) {
        return String(bizMsg).trim();
      }
    }
    if (typeof data === "string" && data.trim() && data.trim().charAt(0) !== "<") {
      // 纯文本错误体才直接展示；HTML 错误页（网关 502/504）不做展示
      return data.trim();
    }

    if (status === 401) {
      return "登录状态已失效，请重新登录";
    }
    if (status === 403) {
      return "没有权限执行该操作";
    }
    if (status === 404) {
      return "接口不存在（404），请确认对应微服务已启动";
    }
    if (status === 502 || status === 504) {
      return "网关未能连接后端服务（" + status + "），请确认相关微服务已启动";
    }
    if (status >= 500) {
      return "服务端异常（" + status + "），请稍后重试";
    }
    if (status) {
      return "请求失败（" + status + "）";
    }
    return err.message || fallback;
  }

  /* ======================================================================
     8. 注册到全局
     ====================================================================== */
  window.hmToast = hmToast;
  window.hmConfirm = hmConfirm;
  window.hmUI = hmUI;
  window.hmErrorText = hmErrorText;

  if (window.Vue) {
    Vue.component("hm-header", HmHeader);
    Vue.component("hm-footer", HmFooter);
    Vue.component("hm-steps", HmSteps);
  }
})(window, document);
