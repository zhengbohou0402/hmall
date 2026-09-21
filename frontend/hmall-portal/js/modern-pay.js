/* ==========================================================================
   黑马商城 · 收银台逻辑  modern-pay.js
   --------------------------------------------------------------------------
   真实接口（经 /api 网关转发）：
     GET  /orders/{id}                              → OrderVO { id, totalFee, paymentType, status, createTime, payTime }
     POST /pay-orders        PayApplyDTO            → String 支付单id
          { bizOrderNo:Long, amount:Integer, payChannelCode:String, payType:Integer, orderInfo:String }
          payType 必须为 5（PayType.BALANCE），否则 pay-service 抛「目前只支持余额支付」
     POST /pay-orders/{id}   PayOrderFormDTO        → void     { id:Long, pw:String }
   订单状态（trade-service OrderStatus）：
     1 未付款 / 2 已付款 / 3 已发货 / 4 确认收货 / 5 交易取消 / 6 已评价

   【为什么不在这里做倒计时】
   pay-service 的 PayOrderServiceImpl.buildPayOrder() 会把支付单的有效期写成
   payOverTime = now + 120 分钟，但该字段在 POST /pay-orders 成功之前无法读取，
   trade-service 的 OrderStatus 枚举里也根本没有「已超时」这个状态
   （UNPAID 只允许转移到 PAID / CLOSED）。
   因此前端**不自行编造超时规则**：可付与否一律以订单真实 status 为准，
   若后端拒绝创建支付单，则原样展示后端返回的业务文案（如「订单已经支付！」）。
   注意：本页不伪造任何「支付成功」，只有后端返回成功才跳转成功页。
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

  var ORDER_STATUS_TAG = {
    1: "warn",
    2: "success",
    3: "ink",
    4: "success",
    5: "muted",
    6: "muted"
  };

  /* 与后端 PayType 枚举严格对齐（pay-service / user-service / hm-service 三处一致）：
       JSAPI(1,"网页支付JS") MINI_APP(2,"小程序支付") APP(3,"APP支付")
       NATIVE(4,"扫码支付")  BALANCE(5,"余额支付")
     其中 3 额外保留旧文案，兼容本仓库历史订单里已落库的旧取值。 */
  var PAYMENT_TYPE_TEXT = {
    1: "网页支付 JS",
    2: "小程序支付",
    3: "APP 支付",
    4: "扫码支付",
    5: "余额支付"
  };

  new Vue({
    el: "#payApp",

    data: function () {
      return {
        orderId: null,
        order: null,

        loading: true,
        loadError: "",

        // 支付单
        creatingPayOrder: false,
        payOrderError: "",
        payOrderId: null,

        // 支付表单
        password: "",
        showPwd: false,
        pwdError: false,
        paying: false,

        // 后端拒绝创建支付单（如「订单已经支付！」「订单已关闭」）时置为 true，
        // 此时右侧改为展示后端原文，不再提供支付表单
        payBlocked: false
      };
    },

    created: function () {
      util.store.set("return-url", location.href);

      if (!util.store.get("user-info")) {
        location.href = "/login.html";
        return;
      }

      this.orderId = util.getUrlParam("id");
      if (!this.orderId) {
        this.loading = false;
        return;
      }
      this.init();
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

      startTicker: function () {
        // 已废弃：原先用于每秒刷新「剩余支付时间」的编造倒计时。
        // 保留空实现仅为兼容可能存在的旧调用点，不再产生任何定时器。
      },

      /* ------------------------------ 订单 ------------------------------ */
      init: function () {
        var self = this;
        this.loading = true;
        this.loadError = "";

        axios.get("/orders/" + this.orderId)
          .then(function (resp) {
            if (!resp || resp.id == null) {
              self.order = null;
              self.loadError = "后端未返回订单 " + self.orderId + " 的信息";
              return;
            }
            self.order = resp;
            // 未付款才创建支付单；已付款 / 已取消不重复创建
            if (resp.status === 1) {
              self.createPayOrder();
            }
          })
          .catch(function (err) {
            self.order = null;
            self.loadError = hmErrorText(err, "订单信息加载失败，请稍后重试");
          })
          .then(function () {
            self.loading = false;
          });
      },

      /* ----------------------------- 支付单 ----------------------------- */
      createPayOrder: function () {
        if (this.creatingPayOrder || this.payOrderId) {
          return;
        }
        var self = this;
        this.creatingPayOrder = true;
        this.payOrderError = "";

        axios.post("/pay-orders", {
          bizOrderNo: this.order.id,
          amount: this.order.totalFee,
          // 仅余额支付：与 PayType.BALANCE(5) 对齐
          payType: 5,
          payChannelCode: "balance",
          orderInfo: "黑马商城商品"
        }, {
          // 后端返回的是纯字符串（支付单id），禁止 axios 尝试 JSON 解析
          transformResponse: function (data) {
            return data;
          }
        })
          .then(function (resp) {
            var id = String(resp == null ? "" : resp).trim().replace(/^"|"$/g, "");
            if (!id) {
              throw new Error("支付单创建失败：后端未返回支付单号");
            }
            self.payOrderId = id;
          })
          .catch(function (err) {
            self.payOrderError = hmErrorText(err, "支付单创建失败，请稍后重试");
            /* 后端明确拒绝（已支付 / 已关闭）→ 进入不可支付状态。
               这类错误来自 checkIdempotent() 的业务判断，属于确定性结论，
               重试也不会变好，因此不再展示支付表单。
               而网关 5xx / 超时属于环境问题，保留重试入口。 */
            var status = err && err.response && err.response.status;
            var bizMsg = err && err.response && err.response.data && err.response.data.msg;
            if (bizMsg && status && status < 500) {
              self.payBlocked = true;
            } else if (status === 500 && /已经支付|已关闭|不支持|非法/.test(String(bizMsg || ""))) {
              self.payBlocked = true;
            }
          })
          .then(function () {
            self.creatingPayOrder = false;
          });
      },

      /* ----------------------------- 支付 ----------------------------- */
      payByBalance: function () {
        if (this.paying) {
          return;
        }
        this.pwdError = false;

        if (!this.payOrderId) {
          hmToast(this.payOrderError || "支付单尚未创建成功，请稍后重试", "error");
          return;
        }
        if (!this.password) {
          this.pwdError = true;
          hmToast("请输入支付密码", "warn");
          return;
        }

        var self = this;
        this.paying = true;

        axios.post("/pay-orders/" + this.payOrderId, {
          id: Number(this.payOrderId),
          pw: this.password
        }).then(function () {
          hmToast("支付成功，正在跳转…", "success", 1400);
          setTimeout(function () {
            window.location.href = "/paysuccess.html?orderId=" + self.order.id;
          }, 400);
        }).catch(function (err) {
          self.paying = false;
          // 支付密码错误是高频场景，单独给出明确提示
          var status = err && err.response && err.response.status;
          var msg = (status === 400 || status === 401)
            ? "支付密码错误，或余额不足，请核对后重试"
            : hmErrorText(err, "支付失败，请稍后重试");
          hmToast(msg, "error", 4000);
        });
      }
    },

    computed: {
      statusText: function () {
        if (!this.order) {
          return "—";
        }
        return ORDER_STATUS_TEXT[this.order.status] || ("未知状态 " + this.order.status);
      },

      statusTagClass: function () {
        if (!this.order) {
          return "muted";
        }
        return ORDER_STATUS_TAG[this.order.status] || "muted";
      },

      paymentTypeText: function () {
        if (!this.order) {
          return "—";
        }
        return PAYMENT_TYPE_TEXT[this.order.paymentType] || ("支付类型 " + this.order.paymentType);
      },

      /* ------------------------------------------------------------------
         是否禁止支付 —— 完全基于后端真实状态，不做任何时间推算
         ------------------------------------------------------------------ */
      payDisabled: function () {
        if (!this.order) {
          return true;
        }
        // 订单本身已不是「未付款」
        if (this.order.status !== 1) {
          return true;
        }
        // 后端已明确拒绝创建支付单
        return this.payBlocked;
      },

      payDisabledTitle: function () {
        if (!this.order) {
          return "订单不可用";
        }
        if (this.order.status === 2) {
          return "订单已支付";
        }
        if (this.order.status === 5) {
          return "订单已关闭";
        }
        if (this.payBlocked) {
          return "无法发起支付";
        }
        return "当前订单状态为「" + this.statusText + "」，不能支付";
      },

      payDisabledHint: function () {
        if (this.payBlocked && this.payOrderError) {
          // 原样展示后端业务文案
          return this.payOrderError;
        }
        if (this.order && this.order.status === 2) {
          return "该订单已完成支付，无需重复付款。";
        }
        if (this.order && this.order.status === 5) {
          return "该订单已取消关闭，无法继续付款。";
        }
        if (this.order && this.order.status !== 1) {
          return "当前订单状态为「" + this.statusText + "」，不能发起支付。";
        }
        return this.payOrderError || "暂时无法创建支付单，请稍后重试。";
      },

      balanceText: function () {
        var user = util.store.get("user-info");
        var b = user && user.balance;
        if (b == null) {
          return "未知";
        }
        return "￥" + this.price(b);
      }
    }
  });
})();
