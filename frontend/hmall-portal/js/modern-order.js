/* ==========================================================================
   黑马商城 · 确认订单页逻辑  modern-order.js
   --------------------------------------------------------------------------
   真实接口（经 /api 网关转发）：
     GET  /addresses                → AddressVO[]  { id, contact, mobile, province, city, town, street, isDefault:0|1 }
     POST /orders  OrderFormDTO     → Long 订单id
          { addressId, paymentType, details: [{ itemId, num }] }
   数据来源：sessionStorage "selectedCarts"（由 cart.html / 首页「立即购买」写入）。
   重要约束：
     · selectedCarts 为空 → 展示「暂无待结算商品」空状态，绝不让 Vue 渲染报错白屏；
     · /addresses 失败 → 展示真实错误文案，不使用任何假地址（旧版曾注入李佳星等假数据）；
     · 用户服务未开放新增地址接口，因此本页不提供「新增收货地址」假按钮。
   ========================================================================== */
(function () {
  "use strict";

  function parseSpec(str) {
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
  }

  new Vue({
    el: "#orderApp",

    data: function () {
      return {
        user: null,

        items: [],              // 待结算商品（来自 selectedCarts）

        addressList: [],
        addrLoading: true,
        addrError: "",

        submitting: false,

        params: {
          addressId: null,
          // 5 = PayType.BALANCE（余额支付）。
          // pay-service / user-service / hm-service 三处 PayType 枚举一致：BALANCE(5,"余额支付")。
          paymentType: 5,
          details: []
        },

        /**
         * 支付方式：trade-service 只做透传存储、不校验取值；真正的支付能力由 pay-service 决定，
         * 而 pay-service 当前只实现 BALANCE(5)。这里直接按后端 PayType 枚举的真实取值与名称呈现，
         * 未实现的渠道显式标为「暂未开通」，避免引导用户走到必然失败的下一步。
         */
        paymentTypes: [
          { value: 5, label: "余额支付", hint: "使用账号余额结算", enabled: true },
          { value: 1, label: "网页支付 JS", hint: "后端暂未开通", enabled: false },
          { value: 2, label: "小程序支付", hint: "后端暂未开通", enabled: false }
        ]
      };
    },

    created: function () {
      util.store.set("return-url", location.href);

      this.user = util.store.get("user-info");
      if (!this.user) {
        location.href = "/login.html";
        return;
      }

      this.loadItems();
      this.loadAddresses();
    },

    methods: {
      price: function (cents) {
        return hmUI.price(cents);
      },

      maskMobile: function (mobile) {
        return hmUI.mobile(mobile);
      },

      keyOf: function (item, idx) {
        return (item.id != null ? item.id : item.itemId) + "-" + idx;
      },

      unitPrice: function (item) {
        return item.newPrice && item.newPrice < item.price ? item.newPrice : item.price;
      },

      specList: function (item) {
        var obj = parseSpec(item.spec);
        return Object.keys(obj).map(function (k) {
          return { k: k, v: obj[k] };
        });
      },

      onImgError: function (item) {
        this.$set(item, "image", "");
      },

      /* ------------------------- 商品 ------------------------- */
      loadItems: function () {
        var raw = util.store.get("selectedCarts");
        // util.store.get 内部是 JSON.parse，脏数据/未写入都可能是 null
        this.items = Array.isArray(raw) ? raw : [];
      },

      incNum: function (item) {
        if (item.stock != null && item.num >= item.stock) {
          hmToast("已达库存上限", "warn");
          return;
        }
        item.num = (Number(item.num) || 1) + 1;
      },

      decNum: function (item) {
        if (item.num > 1) {
          item.num = Number(item.num) - 1;
        }
      },

      /** 手动输入后夹紧到 [1, stock] */
      clampNum: function (item) {
        var n = parseInt(item.num, 10);
        if (isNaN(n) || n < 1) {
          n = 1;
        }
        if (item.stock != null && n > item.stock) {
          n = item.stock;
          hmToast("超出库存上限，已调整为 " + item.stock + " 件", "warn");
        }
        item.num = n;
      },

      /* ------------------------- 地址 ------------------------- */
      loadAddresses: function () {
        var self = this;
        this.addrLoading = true;
        this.addrError = "";

        axios.get("/addresses")
          .then(function (resp) {
            var list = Array.isArray(resp) ? resp : [];
            self.addressList = list;

            if (!list.length) {
              self.params.addressId = null;
              return;
            }
            // 优先选中默认地址，否则选第一个
            var def = list.filter(function (a) { return a.isDefault === 1; })[0];
            self.params.addressId = def ? def.id : list[0].id;
          })
          .catch(function (err) {
            self.addressList = [];
            self.params.addressId = null;
            self.addrError = hmErrorText(err, "收货地址加载失败，请稍后重试");
          })
          .then(function () {
            self.addrLoading = false;
          });
      },

      /* ------------------------- 提交 ------------------------- */
      submitOrder: function () {
        if (this.submitting) {
          return;
        }
        if (!this.items.length) {
          hmToast("暂无待结算商品", "warn");
          return;
        }
        if (!this.params.addressId) {
          hmToast("请先选择收货地址", "warn");
          return;
        }

        var self = this;
        var details = this.items.map(function (i) {
          return {
            itemId: i.itemId != null ? i.itemId : i.id,
            num: Number(i.num) || 1
          };
        }).filter(function (d) {
          return d.itemId != null && d.num > 0;
        });

        if (!details.length) {
          hmToast("商品信息异常，请返回购物车重新选择", "error");
          return;
        }

        this.submitting = true;

        axios.post("/orders", {
          addressId: this.params.addressId,
          paymentType: this.params.paymentType,
          details: details
        })
          .then(function (orderId) {
            if (orderId == null || orderId === "") {
              throw new Error("下单响应未返回订单号");
            }
            // 下单成功后清空待结算数据，避免返回结算页时重复下单
            util.store.del("selectedCarts");
            hmToast("下单成功，正在前往收银台…", "success", 1400);
            setTimeout(function () {
              window.location.href = "/pay.html?id=" + orderId;
            }, 460);
          })
          .catch(function (err) {
            self.submitting = false;
            hmToast(hmErrorText(err, "下单失败，请稍后重试"), "error", 3600);
          });
      }
    },

    computed: {
      totalCount: function () {
        return this.items.reduce(function (sum, i) {
          return sum + (Number(i.num) || 0);
        }, 0);
      },

      totalPrice: function () {
        return this.items.reduce(function (sum, i) {
          var unit = i.newPrice && i.newPrice < i.price ? i.newPrice : i.price;
          return sum + unit * (Number(i.num) || 0);
        }, 0);
      },

      selectedAddress: function () {
        var id = this.params.addressId;
        if (id == null) {
          return null;
        }
        return this.addressList.filter(function (a) { return a.id === id; })[0] || null;
      },

      userName: function () {
        return (this.user && this.user.username) || "当前用户";
      },

      /** 余额（user-info 由登录接口写入，包含 balance） */
      balanceText: function () {
        var b = this.user && this.user.balance;
        if (b == null) {
          return "未知";
        }
        return "￥" + this.price(b);
      },

      canSubmit: function () {
        return this.items.length > 0 && !!this.params.addressId;
      }
    }
  });
})();
