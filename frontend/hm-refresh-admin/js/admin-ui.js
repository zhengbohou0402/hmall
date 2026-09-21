/* ==========================================================================
   黑马商城 · 管理后台共享脚本  admin-ui.js
   --------------------------------------------------------------------------
   提供：
     1. hmAdmToast()     统一轻提示（与后台设计系统一致，替代原生 alert）
     2. hmAdmErrorText() 统一错误文案提取（区分超时 / 401 / 404 / 5xx）
     3. hmFormat         金额（分→元）、时间、数字等格式化
   依赖：js/vue.js、js/axios.min.js、js/common.js
   注意：后台 common.js 的响应拦截器返回的是完整 response（resp.data.list），
        与用户端返回 response.data 的约定不同，不要在页面里混用。
   ========================================================================== */
(function (window, document) {
  "use strict";

  /* ---------------------- 1. 轻提示 ---------------------- */
  var HOST_ID = "hm-adm-toast-host";
  var ICONS = { success: "✓", error: "!", warn: "!", info: "i" };

  function getHost() {
    var host = document.getElementById(HOST_ID);
    if (!host) {
      host = document.createElement("div");
      host.id = HOST_ID;
      host.className = "hm-adm-toast-host";
      host.setAttribute("role", "status");
      host.setAttribute("aria-live", "polite");
      document.body.appendChild(host);
    }
    return host;
  }

  function hmAdmToast(message, type, duration) {
    type = type || "info";
    duration = typeof duration === "number" ? duration : 2400;
    var host = getHost();

    while (host.children.length >= 3) {
      host.removeChild(host.firstChild);
    }

    var el = document.createElement("div");
    el.className = "hm-adm-toast hm-adm-toast--" + type;

    var icon = document.createElement("span");
    icon.className = "hm-adm-toast__icon";
    icon.textContent = ICONS[type] || "i";

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

  /* ---------------------- 2. 错误文案 ---------------------- */
  function hmAdmErrorText(err, fallback) {
    fallback = fallback || "操作失败，请稍后重试";
    if (!err) {
      return fallback;
    }
    if (err.code === "ECONNABORTED" || /timeout/i.test(err.message || "")) {
      return "请求超时，请确认后端服务已启动后重试";
    }
    var status = err.response && err.response.status;
    if (status === 401) {
      return "登录状态已失效，请重新登录";
    }
    if (status === 403) {
      return "当前账号没有权限执行该操作";
    }
    if (status === 404) {
      return "接口不存在（404），请确认对应微服务已启动";
    }
    if (status >= 500) {
      return "服务端异常（" + status + "），请稍后重试";
    }
    var data = err.response && err.response.data;
    if (data) {
      if (typeof data === "string") {
        return data;
      }
      var msg = data.msg || data.message || data.error;
      if (msg) {
        return msg;
      }
    }
    if (status) {
      return "请求失败（" + status + "）";
    }
    return err.message || fallback;
  }

  /* ---------------------- 3. 格式化 ---------------------- */
  var hmFormat = {
    /** 分 → 元字符串（复用 common.js 的 util.formatPrice 保证与前台一致） */
    price: function (cents) {
      if (typeof util !== "undefined" && util && typeof util.formatPrice === "function") {
        return util.formatPrice(cents);
      }
      if (cents == null || isNaN(cents)) {
        return "0.00";
      }
      return (Number(cents) / 100).toFixed(2);
    },
    /** 本地时间 yyyy-MM-dd HH:mm:ss */
    time: function (t) {
      if (!t) {
        return "—";
      }
      var d = new Date(t);
      if (isNaN(d.getTime())) {
        return String(t);
      }
      var pad = function (n) {
        return n < 10 ? "0" + n : "" + n;
      };
      return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) +
        " " + pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
    },
    /** 千分位 */
    num: function (n) {
      if (n == null || isNaN(n)) {
        return "0";
      }
      return Number(n).toLocaleString("zh-CN");
    },
    /** 后端返回的 spec 是 JSON 字符串，解析失败不应让整表崩掉 */
    spec: function (str) {
      if (!str) {
        return {};
      }
      if (typeof str === "object") {
        return str;
      }
      try {
        var obj = JSON.parse(str);
        return obj && typeof obj === "object" ? obj : {};
      } catch (e) {
        return {};
      }
    },
    /** spec → "颜色：蓝色 / 尺码：26寸" */
    specText: function (str) {
      var obj = hmFormat.spec(str);
      var keys = Object.keys(obj);
      if (!keys.length) {
        return "—";
      }
      return keys.map(function (k) {
        return k + "：" + obj[k];
      }).join(" / ");
    }
  };

  window.hmAdmToast = hmAdmToast;
  window.hmAdmErrorText = hmAdmErrorText;
  window.hmFormat = hmFormat;
})(window, document);
