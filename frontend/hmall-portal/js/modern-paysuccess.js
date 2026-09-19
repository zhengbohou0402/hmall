/* ==========================================================================
   黑马商城 · 支付结果页逻辑  modern-paysuccess.js
   --------------------------------------------------------------------------
   真实接口：GET /orders/{orderId} → OrderVO
   设计原则：
     · 只有当订单状态为 2（已付款）时才展示「支付成功」；
     · 其他状态如实展示订单真实状态，绝不因为「打开了这个页面」就宣称成功；
     · 本项目没有 myOrder.html（旧版按钮指向的 /myOrder.html 实际是 404），
       因此订单明细在本页直接展示，不提供指向不存在页面的入口。
   订单状态：1 未付款 / 2 已付款 / 3 已发货 / 4 交易成功 / 5 已取消 / 6 已结束
   ========================================================================== */
(function () {
  "use strict";

  var ORDER_STATUS_TEXT = {
    1: "未付款",
    2: "已付款",
    3: "已发货",
    4: "交易成功",
    5: "已取消",
    6: "已结束"
  };

  /* 与后端 PayType 枚举严格对齐（见 modern-pay.js 的同名说明）。 */
  var PAYMENT_TYPE_TEXT = {
    1: "网页支付 JS",
    2: "小程序支付",
    3: "APP 支付",
    4: "扫码支付",
    5: "余额支付"
  };

  new Vue({
    el: "#resultApp",

    data: function () {
      return {
        orderId: null,
        order: null,
        loading: true,
        error: ""
      };
    },

    created: function () {
      util.store.set("return-url", location.href);

      if (!util.store.get("user-info")) {
        location.href = "/login.html";
        return;
      }

      this.orderId = util.getUrlParam("orderId");
      if (!this.orderId) {
        this.loading = false;
        return;
      }
      this.load();
    },

    methods: {
      price: function (cents) {
        return hmUI.price(cents);
      },

      formatTime: function (t) {
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

      load: function () {
        var self = this;
        this.loading = true;
        this.error = "";

        axios.get("/orders/" + this.orderId)
          .then(function (resp) {
            if (!resp || resp.id == null) {
              self.order = null;
              self.error = "后端未返回订单 " + self.orderId + " 的信息";
              return;
            }
            self.order = resp;
          })
          .catch(function (err) {
            self.order = null;
            self.error = hmErrorText(err, "订单信息加载失败，请稍后重试");
          })
          .then(function () {
            self.loading = false;
          });
      }
    },

    computed: {
      isPaid: function () {
        // 2 = 已付款；3 已发货 / 4 交易成功 也属于「已付款之后」的合法状态
        return !!this.order && (this.order.status === 2 ||
          this.order.status === 3 ||
          this.order.status === 4);
      },

      statusText: function () {
        if (!this.order) {
          return "—";
        }
        return ORDER_STATUS_TEXT[this.order.status] || ("未知状态 " + this.order.status);
      },

      paymentTypeText: function () {
        if (!this.order) {
          return "—";
        }
        return PAYMENT_TYPE_TEXT[this.order.paymentType] || ("支付类型 " + this.order.paymentType);
      },

      /** 预计送达：支付时间 + 3 天；无支付时间则用下单时间 */
      sendDate: function () {
        var base = this.order && (this.order.payTime || this.order.createTime);
        if (!base) {
          return "";
        }
        var d = new Date(base);
        if (isNaN(d.getTime())) {
          return "";
        }
        d.setDate(d.getDate() + 3);
        return d.getFullYear() + " 年 " + (d.getMonth() + 1) + " 月 " + d.getDate() + " 日";
      }
    }
  });
})();
