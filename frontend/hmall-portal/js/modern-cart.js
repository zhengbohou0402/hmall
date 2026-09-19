/* ==========================================================================
   黑马商城 · 购物车页逻辑  modern-cart.js
   --------------------------------------------------------------------------
   真实接口（经 /api 网关转发）：
     GET    /carts          → CartVO[]   { id, itemId, name, image, price, newPrice?, stock, num, status, spec }
     PUT    /carts          { id, num }  → void
     DELETE /carts/{id}                  → void
   结算传参：把选中的购物车对象写入 sessionStorage "selectedCarts"，
   由 order-confirm.html 读取（与旧版约定保持一致，避免破坏后端 details 组装）。
   ========================================================================== */
(function () {
  "use strict";

  /** 安全解析 spec（后端字段是 JSON 字符串，脏数据不应让整页白屏） */
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
    el: "#cartApp",

    data: function () {
      return {
        carts: [],          // 全部购物车条目
        enableCarts: [],    // 可结算条目（已上架且库存足够）
        selectedCarts: [],  // 已勾选条目
        loading: true,
        busy: false,
        error: ""
      };
    },

    created: function () {
      // 记录来源页，未登录时登录后可跳回购物车
      util.store.set("return-url", location.href);

      if (!util.isLogin()) {
        location.href = "/login.html";
        return;
      }
      this.loadCarts(false);
    },

    methods: {
      price: function (cents) {
        return hmUI.price(cents);
      },

      /** 取“实际成交单价”：有降价则用 newPrice */
      unitPrice: function (c) {
        return c.newPrice && c.newPrice < c.price ? c.newPrice : c.price;
      },

      /** spec → [{k, v}] */
      specList: function (c) {
        var obj = parseSpec(c.spec);
        return Object.keys(obj).map(function (k) {
          return { k: k, v: obj[k] };
        });
      },

      onImgError: function (c) {
        // 避免坏图反复触发 error 造成死循环
        this.$set(c, "image", "");
      },

      /**
       * 加载购物车
       * @param {boolean} preserve 是否保留当前勾选（按 id 匹配）
       */
      loadCarts: function (preserve) {
        var self = this;
        var keepIds = preserve
          ? this.selectedCarts.map(function (c) { return c.id; })
          : null;

        this.loading = true;
        this.error = "";

        axios.get("/carts")
          .then(function (resp) {
            var list = Array.isArray(resp) ? resp : [];
            self.carts = list;
            self.refreshSelection(keepIds);
          })
          .catch(function (err) {
            self.carts = [];
            self.enableCarts = [];
            self.selectedCarts = [];
            self.error = hmErrorText(err, "查询购物车失败，请稍后重试");
          })
          .then(function () {
            self.loading = false;
            self.firstLoad = false;
          });
      },

      /**
       * 重算可结算集合与勾选集合。
       * 注意：每次请求 /carts 都会返回全新对象，旧的勾选引用会失效，
       * 因此必须按 id 重新建立勾选关系，否则「全选 / 合计」会莫名清零。
       */
      refreshSelection: function (keepIds) {
        this.enableCarts = this.carts.filter(function (c) {
          // 兼容旧索引文档尚未携带 status 的场景；索引重建后会返回 1/2/3。
          return (c.status == null || c.status === 1) && c.stock >= c.num;
        });

        if (keepIds === null || keepIds === undefined) {
          // 首次加载：默认全选可结算商品
          this.selectedCarts = this.enableCarts.slice();
          return;
        }
        this.selectedCarts = this.enableCarts.filter(function (c) {
          return keepIds.indexOf(c.id) > -1;
        });
      },

      increment: function (c) {
        if (c.num >= c.stock) {
          hmToast("已达库存上限，仅剩 " + c.stock + " 件", "warn");
          return;
        }
        this.updateNum(c, c.num + 1);
      },

      decrement: function (c) {
        if (c.num <= 1) {
          return;
        }
        this.updateNum(c, c.num - 1);
      },

      /** 手动输入数量后提交（input 的 change 在失焦 / 回车时触发） */
      commitNum: function (c) {
        var n = parseInt(c.num, 10);
        if (isNaN(n) || n < 1) {
          n = 1;
        }
        if (n > c.stock) {
          n = c.stock;
          hmToast("超出库存上限，已调整为 " + c.stock + " 件", "warn");
        }
        c.num = n;
        // 与上一次成功提交到服务端的值相同 → 不重复发起请求
        if (n === c._srvNum) {
          return;
        }
        this.updateNum(c, n);
      },

      updateNum: function (c, num) {
        var self = this;
        c.num = num;
        this.busy = true;

        axios.put("/carts", { id: c.id, num: num })
          .then(function () {
            c._srvNum = num;
            return self.reload();
          })
          .catch(function (err) {
            hmToast(hmErrorText(err, "数量更新失败，已恢复为服务端数值"), "error");
            self.busy = false;
            // 回滚到服务端真实值
            self.reload();
          });
      },

      reload: function () {
        var self = this;
        var keepIds = this.selectedCarts.map(function (c) { return c.id; });
        return axios.get("/carts")
          .then(function (resp) {
            self.carts = Array.isArray(resp) ? resp : [];
            // 记录服务端真实数量，供 commitNum 做「无变化不请求」判断
            self.carts.forEach(function (c) {
              c._srvNum = c.num;
            });
            self.refreshSelection(keepIds);
            self.busy = false;
          })
          .catch(function (err) {
            self.busy = false;
            hmToast(hmErrorText(err, "购物车刷新失败，请手动刷新页面"), "error");
          });
      },

      removeOne: function (c) {
        var self = this;
        hmConfirm("确定要从购物车移除「" + hmUI.plain(c.name) + "」吗？", {
          title: "删除商品",
          okText: "删除",
          danger: true
        }).then(function (ok) {
          if (!ok) {
            return;
          }
          self.busy = true;
          axios.delete("/carts/" + c.id)
            .then(function () {
              hmToast("已移除 1 件商品", "success");
              return self.reload();
            })
            .catch(function (err) {
              hmToast(hmErrorText(err, "删除失败，请重试"), "error");
              self.busy = false;
            });
        });
      },

      removeSelected: function () {
        if (!this.selectedCarts.length) {
          hmToast("请先选择要删除的商品", "warn");
          return;
        }
        var self = this;
        var ids = this.selectedCarts.map(function (c) { return c.id; });

        hmConfirm("确定要删除选中的 " + ids.length + " 件商品吗？此操作不可撤销。", {
          title: "批量删除",
          okText: "全部删除",
          danger: true
        }).then(function (ok) {
          if (!ok) {
            return;
          }
          self.busy = true;
          // 后端提供 DELETE /carts?ids=1,2，一次请求完成，避免部分删除。
          axios.delete("/carts?ids=" + ids.join(","))
            .then(function () {
              hmToast("已删除 " + ids.length + " 件商品", "success");
              return self.reload();
            })
            .catch(function (err) {
              hmToast(hmErrorText(err, "部分商品删除失败，已刷新列表"), "error");
              return self.reload();
            });
        });
      },

      toCheckout: function () {
        if (!this.selectedCarts.length) {
          hmToast("请至少选择一件商品", "warn");
          return;
        }
        // 传给结算页：字段与旧版一致（itemId / num 会被 order-confirm 组装成 details）
        util.store.set("selectedCarts", this.selectedCarts);
        window.location.href = "/order-confirm.html";
      }
    },

    computed: {
      /** 全选（可写）：与旧版「勾选状态联动」行为一致，但用计算属性避免双 watcher 时序问题 */
      selectAll: {
        get: function () {
          return this.enableCarts.length > 0 &&
            this.selectedCarts.length === this.enableCarts.length;
        },
        set: function (val) {
          this.selectedCarts = val ? this.enableCarts.slice() : [];
        }
      },

      /** 已选商品件数（累加数量，不是种类数） */
      selectedCount: function () {
        return this.selectedCarts.reduce(function (sum, c) {
          return sum + (Number(c.num) || 0);
        }, 0);
      },

      /** 应付商品总额（分） */
      totalPrice: function () {
        return this.selectedCarts.reduce(function (sum, c) {
          var unit = c.newPrice && c.newPrice < c.price ? c.newPrice : c.price;
          return sum + unit * (Number(c.num) || 0);
        }, 0);
      },

      /** 相比加入购物车时节省的金额（分），无降价则为 0 */
      savedAmount: function () {
        return this.selectedCarts.reduce(function (sum, c) {
          if (c.newPrice && c.newPrice < c.price) {
            return sum + (c.price - c.newPrice) * (Number(c.num) || 0);
          }
          return sum;
        }, 0);
      },

      /** 是否存在不可结算商品 */
      hasUnavailable: function () {
        return this.carts.some(function (c) {
          return c.status !== 1 || c.num > c.stock;
        });
      }
    }
  });
})();
