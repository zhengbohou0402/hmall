/* ==========================================================================
   黑马商城管理系统 · 商品管理  js/pages/goods/list.js
   --------------------------------------------------------------------------
   真实接口（经 /api 网关转发，后台拦截器返回完整 response，数据在 resp.data）：
     GET    /items/page?pageNo&pageSize&id&key&category&brand&minPrice&maxPrice
            → PageDTO { total, pages, list: ItemDTO[] }
     POST   /items               新增商品
     PUT    /items               更新商品（后端强制忽略 status，不允许在此改状态）
     PUT    /items/status/{id}/{status}   上架(1) / 下架(2)
     DELETE /items/{id}          删除商品
   相对旧版的修正：
     1. 删除 catch 里注入 5 条假商品的兜底逻辑 —— 接口失败时展示真实错误与重试，
        否则运营会把假数据显示成真实商品（旧版还会把 total 写成 124）。
     2. 补齐「状态 / 库存 / 销量」列：旧版表格看不到状态，却提供上下架按钮。
     3. 新增关键词搜索与价格区间筛选（后端 ItemPageQuery 早就支持 key/minPrice/maxPrice，
        旧版前端一直没有暴露）。
     4. 表单加真实校验：价格、库存、规格 JSON 不再允许提交脏值
        （规格是 JSON 字符串，脏值会让用户端 order-confirm 解析异常）。
     5. 价格以「元」录入、按「分」提交，避免运营把 199 元填成 199 分。
     6. resetForm 不再把 params 清成空对象导致 pageNo/pageSize 丢失。
   ========================================================================== */
(function () {
  "use strict";

  var STATUS_TEXT = { 1: "已上架", 2: "已下架", 3: "已删除" };

  var GoodsList = Vue.extend({
    template: [
      '<div>',

      // ============================ 筛选区 ============================
      '  <div class="hm-panel">',
      '    <div class="hm-panel__head">',
      '      <div class="hm-panel__title">',
      '        商品管理',
      '        <small>数据来源：GET /api/items/page</small>',
      '      </div>',
      '      <span class="hm-tag hm-tag--muted">共 {{ total }} 条</span>',
      '    </div>',

      '    <el-form :inline="true" :model="params" size="small" @submit.native.prevent>',
      '      <el-form-item label="商品ID">',
      '        <el-input v-model="params.id" placeholder="精确匹配" style="width:130px"',
      '                  clearable @keyup.enter.native="search"/>',
      '      </el-form-item>',
      '      <el-form-item label="关键词">',
      '        <el-input v-model="params.key" placeholder="商品名称包含" style="width:190px"',
      '                  clearable @keyup.enter.native="search"/>',
      '      </el-form-item>',
      '      <el-form-item label="分类">',
      '        <el-input v-model="params.category" placeholder="分类名称" style="width:130px"',
      '                  clearable @keyup.enter.native="search"/>',
      '      </el-form-item>',
      '      <el-form-item label="品牌">',
      '        <el-input v-model="params.brand" placeholder="品牌名称" style="width:130px"',
      '                  clearable @keyup.enter.native="search"/>',
      '      </el-form-item>',
      '      <el-form-item label="价格（元）">',
      '        <el-input v-model="params.minPriceYuan" placeholder="最低" style="width:88px"',
      '                  clearable @keyup.enter.native="search"/>',
      '        <span style="margin:0 6px;color:#909399">-</span>',
      '        <el-input v-model="params.maxPriceYuan" placeholder="最高" style="width:88px"',
      '                  clearable @keyup.enter.native="search"/>',
      '      </el-form-item>',
      '      <el-form-item>',
      '        <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>',
      '        <el-button icon="el-icon-refresh-left" @click="resetForm">重置</el-button>',
      '      </el-form-item>',
      '    </el-form>',

      '    <div style="display:flex;align-items:center;justify-content:space-between;gap:12px;',
      '                margin-top:4px">',
      '      <span class="hm-hint">',
      '        「已上架」商品不能直接编辑或删除：PUT /items 会强制忽略 status，',
      '        需先下架再修改，避免上架商品被误改。',
      '      </span>',
      '      <el-button type="primary" size="small" icon="el-icon-plus" @click="beginAdd">',
      '        新增商品',
      '      </el-button>',
      '    </div>',
      '  </div>',

      // ============================ 列表区 ============================
      '  <div class="hm-panel hm-panel--flush" style="margin-top:18px">',

      '    <div class="hm-loading" v-if="loading">',
      '      <span class="hm-spinner"></span><span>正在读取商品数据…</span>',
      '    </div>',

      '    <div class="hm-empty" v-else-if="error">',
      '      <span class="hm-empty__icon">!</span>',
      '      <h3>商品列表加载失败</h3>',
      '      <p>{{ error }}</p>',
      '      <el-button type="primary" size="small" @click="query">重新加载</el-button>',
      '    </div>',

      '    <div class="hm-empty" v-else-if="!items.length">',
      '      <span class="hm-empty__icon">∅</span>',
      '      <h3>没有匹配的商品</h3>',
      '      <p>换个筛选条件试试，或点击「新增商品」创建一条。</p>',
      '      <el-button type="primary" size="small" @click="resetForm">清空筛选条件</el-button>',
      '    </div>',

      '    <template v-else>',
      '      <el-table :data="items" border style="width:100%" size="small">',
      '        <el-table-column prop="id" label="ID" width="90" align="center"/>',
      '        <el-table-column label="图片" width="76" align="center">',
      '          <template slot-scope="scope">',
      '            <el-image style="width:46px;height:46px;border-radius:8px"',
      '                      :src="scope.row.image" fit="cover">',
      '              <div slot="error" class="hm-hint">无图</div>',
      '            </el-image>',
      '          </template>',
      '        </el-table-column>',
      '        <el-table-column prop="name" label="商品名称" min-width="240">',
      '          <template slot-scope="scope">',
      '            <div style="font-weight:600;color:#1B2437">{{ scope.row.name }}</div>',
      '            <div class="hm-hint" style="margin-top:2px">',
      '              {{ fmt.specText(scope.row.spec) }}',
      '            </div>',
      '          </template>',
      '        </el-table-column>',
      '        <el-table-column prop="category" label="分类" width="100" align="center">',
      '          <template slot-scope="scope">{{ scope.row.category || "—" }}</template>',
      '        </el-table-column>',
      '        <el-table-column prop="brand" label="品牌" width="110" align="center">',
      '          <template slot-scope="scope">{{ scope.row.brand || "—" }}</template>',
      '        </el-table-column>',
      '        <el-table-column label="价格" width="110" align="right">',
      '          <template slot-scope="scope">',
      '            <span style="font-family:monospace;font-weight:700;color:#D92D20">',
      '              ￥{{ fmt.price(scope.row.price) }}',
      '            </span>',
      '          </template>',
      '        </el-table-column>',
      '        <el-table-column label="库存" width="90" align="center">',
      '          <template slot-scope="scope">',
      '            <span :style="{ color: Number(scope.row.stock) <= 10 ? \'#D92D20\' : \'#4A5570\' }">',
      '              {{ scope.row.stock }}',
      '            </span>',
      '          </template>',
      '        </el-table-column>',
      '        <el-table-column prop="sold" label="销量" width="80" align="center">',
      '          <template slot-scope="scope">{{ scope.row.sold || 0 }}</template>',
      '        </el-table-column>',
      '        <el-table-column label="状态" width="96" align="center">',
      '          <template slot-scope="scope">',
      '            <span class="hm-tag"',
      '                  :class="scope.row.status === 1 ? \'hm-tag--success\' : \'hm-tag--muted\'">',
      '              {{ statusText(scope.row.status) }}',
      '            </span>',
      '          </template>',
      '        </el-table-column>',
      '        <el-table-column label="操作" width="150" align="center" fixed="right">',
      '          <template slot-scope="scope">',
      '            <el-tooltip content="编辑（需先下架）" placement="top">',
      '              <el-button type="primary" plain icon="el-icon-edit" circle size="mini"',
      '                         @click="handleEdit(scope.row)"/>',
      '            </el-tooltip>',
      '            <el-tooltip content="删除（需先下架）" placement="top">',
      '              <el-button type="danger" plain icon="el-icon-delete" circle size="mini"',
      '                         @click="handleDelete(scope.row)"/>',
      '            </el-tooltip>',
      '            <el-tooltip :content="scope.row.status === 1 ? \'下架\' : \'上架\'" placement="top">',
      '              <el-button :type="scope.row.status === 1 ? \'info\' : \'success\'" plain',
      '                         :icon="scope.row.status === 1 ? \'el-icon-download\' : \'el-icon-upload2\'"',
      '                         circle size="mini" @click="toggleStatus(scope.row)"/>',
      '            </el-tooltip>',
      '          </template>',
      '        </el-table-column>',
      '      </el-table>',

      '      <div style="display:flex;justify-content:flex-end;padding:14px 24px;',
      '                  border-top:1px solid var(--hm-line)">',
      '        <el-pagination',
      '          background',
      '          layout="total, sizes, prev, pager, next"',
      '          :current-page="params.pageNo"',
      '          :page-size="params.pageSize"',
      '          :page-sizes="[10, 20, 50, 100]"',
      '          :total="total"',
      '          @current-change="handlePageChange"',
      '          @size-change="handleSizeChange"/>',
      '      </div>',
      '    </template>',
      '  </div>',

      // ============================ 新增 / 编辑弹窗 ============================
      '  <el-dialog :title="isEdit ? \'编辑商品\' : \'新增商品\'" :visible.sync="formVisible"',
      '             width="620px" :close-on-click-modal="false">',
      '    <el-form :model="item" :rules="rules" ref="itemForm" size="small" label-width="96px">',
      '      <el-form-item label="商品名称" prop="name">',
      '        <el-input v-model="item.name" placeholder="请输入商品名称"/>',
      '      </el-form-item>',
      '      <el-form-item label="商品分类" prop="category">',
      '        <el-input v-model="item.category" placeholder="例如：手机"/>',
      '      </el-form-item>',
      '      <el-form-item label="商品品牌" prop="brand">',
      '        <el-input v-model="item.brand" placeholder="例如：华为"/>',
      '      </el-form-item>',
      '      <el-form-item label="价格（元）" prop="priceYuan">',
      '        <el-input-number v-model="item.priceYuan" :min="0.01" :precision="2"',
      '                         :step="1" :controls="false" style="width:180px"/>',
      '        <span class="hm-hint" style="margin-left:10px">',
      '          提交时按分存储：{{ Math.round((item.priceYuan || 0) * 100) }} 分',
      '        </span>',
      '      </el-form-item>',
      '      <el-form-item label="商品库存" prop="stock">',
      '        <el-input-number v-model="item.stock" :min="0" :precision="0"',
      '                         :controls="false" style="width:180px"/>',
      '      </el-form-item>',
      '      <el-form-item label="规格 JSON" prop="spec">',
      '        <el-input v-model="item.spec" type="textarea" :rows="2"',
      '                  placeholder=\'例如：{"颜色":"蓝色","尺码":"26寸"}\'/>',
      '        <span class="hm-hint">必须是合法 JSON 对象，用户端结算页会直接解析该字段。</span>',
      '      </el-form-item>',
      '      <el-form-item label="商品图片">',
      '        <el-input v-model="item.image" placeholder="图片 URL，可留空"/>',
      '      </el-form-item>',
      '      <el-form-item label="广告推广">',
      '        <el-switch v-model="item.isAD"/>',
      '      </el-form-item>',
      '    </el-form>',
      '    <div slot="footer">',
      '      <el-button @click="formVisible = false">取 消</el-button>',
      '      <el-button type="primary" :loading="saving" @click="confirmEdit">确 定</el-button>',
      '    </div>',
      '  </el-dialog>',

      '</div>'
    ].join("\n"),

    data: function () {
      // 规格必须是合法 JSON 对象：脏值会让用户端 order-confirm 解析失败
      var validateSpec = function (rule, value, callback) {
        if (!value || !String(value).trim()) {
          callback();
          return;
        }
        try {
          var obj = JSON.parse(value);
          if (obj === null || typeof obj !== "object" || Array.isArray(obj)) {
            callback(new Error("规格必须是 JSON 对象，例如 {\"颜色\":\"蓝色\"}"));
            return;
          }
          callback();
        } catch (e) {
          callback(new Error("规格不是合法 JSON"));
        }
      };

      return {
        fmt: window.hmFormat,

        loading: true,
        saving: false,
        error: "",

        items: [],
        total: 0,

        formVisible: false,
        isEdit: false,

        item: { isAD: false, image: "", spec: "{}", priceYuan: undefined, stock: undefined },

        params: {
          pageNo: 1,
          pageSize: 10,
          id: "",
          key: "",
          category: "",
          brand: "",
          minPriceYuan: "",
          maxPriceYuan: ""
        },

        rules: {
          name: [{ required: true, message: "请输入商品名称", trigger: "blur" }],
          category: [{ required: true, message: "请输入商品分类", trigger: "blur" }],
          brand: [{ required: true, message: "请输入商品品牌", trigger: "blur" }],
          priceYuan: [{
            required: true,
            validator: function (rule, value, callback) {
              if (value == null || value === "" || isNaN(value)) {
                callback(new Error("请输入商品价格"));
              } else if (Number(value) <= 0) {
                callback(new Error("价格必须大于 0"));
              } else {
                callback();
              }
            },
            trigger: "blur"
          }],
          stock: [{
            required: true,
            validator: function (rule, value, callback) {
              if (value == null || value === "" || isNaN(value)) {
                callback(new Error("请输入商品库存"));
              } else if (Number(value) < 0) {
                callback(new Error("库存不能为负数"));
              } else if (String(value).indexOf(".") > -1) {
                callback(new Error("库存必须是整数"));
              } else {
                callback();
              }
            },
            trigger: "blur"
          }],
          spec: [{ validator: validateSpec, trigger: "blur" }]
        }
      };
    },

    created: function () {
      this.query();
    },

    methods: {
      statusText: function (s) {
        return STATUS_TEXT[s] || ("状态 " + s);
      },

      /** 元 → 分 */
      toCents: function (yuan) {
        if (yuan == null || yuan === "" || isNaN(yuan)) {
          return undefined;
        }
        return Math.round(Number(yuan) * 100);
      },

      /** 拼装查询参数：只提交有值的字段，后端按空值忽略 */
      buildParams: function () {
        var p = {
          pageNo: this.params.pageNo || 1,
          pageSize: this.params.pageSize || 10
        };
        var self = this;
        ["id", "key", "category", "brand"].forEach(function (k) {
          var v = self.params[k];
          if (v != null && String(v).trim() !== "") {
            p[k] = String(v).trim();
          }
        });
        var min = this.toCents(this.params.minPriceYuan);
        var max = this.toCents(this.params.maxPriceYuan);
        if (min != null) {
          p.minPrice = min;
        }
        if (max != null) {
          p.maxPrice = max;
        }
        return p;
      },

      query: function () {
        var self = this;
        this.loading = true;
        this.error = "";

        axios.get("/items/page", { params: this.buildParams() })
          .then(function (resp) {
            var data = resp && resp.data;
            if (!data) {
              throw new Error("接口未返回分页数据");
            }
            self.items = Array.isArray(data.list) ? data.list : [];
            self.total = Number(data.total) || 0;
          })
          .catch(function (err) {
            // 不再注入假数据：失败时明确报错并提供重试
            self.items = [];
            self.total = 0;
            self.error = hmAdmErrorText(err, "商品列表加载失败，请稍后重试");
          })
          .then(function () {
            self.loading = false;
          });
      },

      search: function () {
        this.params.pageNo = 1;
        this.query();
      },

      resetForm: function () {
        this.params = {
          pageNo: 1,
          pageSize: this.params.pageSize || 10,
          id: "",
          key: "",
          category: "",
          brand: "",
          minPriceYuan: "",
          maxPriceYuan: ""
        };
        this.query();
      },

      handlePageChange: function (p) {
        this.params.pageNo = p;
        this.query();
      },

      handleSizeChange: function (s) {
        this.params.pageSize = s;
        this.params.pageNo = 1;
        this.query();
      },

      /* ------------------------- 新增 / 编辑 ------------------------- */
      beginAdd: function () {
        this.isEdit = false;
        this.item = {
          isAD: false,
          image: "",
          spec: "{}",
          name: "",
          category: "",
          brand: "",
          priceYuan: undefined,
          stock: undefined
        };
        this.formVisible = true;
        var self = this;
        this.$nextTick(function () {
          if (self.$refs.itemForm) {
            self.$refs.itemForm.clearValidate();
          }
        });
      },

      handleEdit: function (row) {
        if (row.status === 1) {
          hmAdmToast("已上架的商品不能修改，请先下架", "warn");
          return;
        }
        this.isEdit = true;
        this.item = {
          id: row.id,
          name: row.name,
          category: row.category,
          brand: row.brand,
          // 分 → 元
          priceYuan: row.price != null ? Number(row.price) / 100 : undefined,
          stock: row.stock,
          image: row.image || "",
          spec: row.spec || "{}",
          isAD: !!row.isAD
        };
        this.formVisible = true;
        var self = this;
        this.$nextTick(function () {
          if (self.$refs.itemForm) {
            self.$refs.itemForm.clearValidate();
          }
        });
      },

      handleDelete: function (row) {
        if (row.status === 1) {
          hmAdmToast("已上架的商品不能删除，请先下架", "warn");
          return;
        }
        var self = this;
        this.$confirm("将永久删除商品「" + row.name + "」（ID " + row.id + "），是否继续？", "删除确认", {
          confirmButtonText: "确定删除",
          cancelButtonText: "取消",
          type: "warning"
        }).then(function () {
          axios.delete("/items/" + row.id)
            .then(function () {
              hmAdmToast("删除成功", "success");
              self.query();
            })
            .catch(function (err) {
              hmAdmToast(hmAdmErrorText(err, "删除失败"), "error");
            });
        }).catch(function () {
          // 用户取消，无需提示
        });
      },

      confirmEdit: function () {
        var self = this;
        this.$refs.itemForm.validate(function (valid) {
          if (!valid) {
            hmAdmToast("请先修正表单中标红的字段", "warn");
            return;
          }

          var payload = {
            name: String(self.item.name).trim(),
            category: String(self.item.category).trim(),
            brand: String(self.item.brand).trim(),
            price: self.toCents(self.item.priceYuan),
            stock: parseInt(self.item.stock, 10),
            image: (self.item.image || "").trim(),
            spec: (self.item.spec || "").trim() || "{}",
            isAD: !!self.item.isAD
          };

          self.saving = true;

          // 编辑必须带 id；状态由后端忽略，此处不提交 status
          var req = self.isEdit
            ? axios.put("/items", Object.assign({ id: self.item.id }, payload))
            : axios.post("/items", payload);

          req.then(function () {
            hmAdmToast(self.isEdit ? "更新成功" : "新增成功", "success");
            self.formVisible = false;
            self.query();
          }).catch(function (err) {
            hmAdmToast(hmAdmErrorText(err, self.isEdit ? "更新失败" : "新增失败"), "error");
          }).then(function () {
            self.saving = false;
          });
        });
      },

      /* ------------------------- 上下架 ------------------------- */
      toggleStatus: function (row) {
        var next = row.status === 1 ? 2 : 1;
        var action = next === 1 ? "上架" : "下架";
        var self = this;

        axios.put("/items/status/" + row.id + "/" + next)
          .then(function () {
            hmAdmToast(action + "成功", "success");
            self.query();
          })
          .catch(function (err) {
            hmAdmToast(hmAdmErrorText(err, action + "失败"), "error");
          });
      }
    }
  });

  // 供 index.html 的 ViewRouter 使用
  window.GoodsList = GoodsList;
})();
