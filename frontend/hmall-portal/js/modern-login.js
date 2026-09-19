/* ==========================================================================
   黑马商城 · 登录页逻辑  modern-login.js
   --------------------------------------------------------------------------
   真实接口（全部经 /api 网关转发）：
     POST /users/login        { username, password, rememberMe }  → UserLoginVO
     GET  /users/captcha                                          → { captchaId, image }
     POST /users/code         { phone, captchaId, captchaCode }   → void（发送短信）
     POST /users/login/sms    { phone, code }                     → UserLoginVO
   登录成功后：token → sessionStorage["token"]，其余字段 → sessionStorage["user-info"]
   依赖：js/common.js（axios 拦截器已把 response.data 解包）、js/modern-ui.js
   ========================================================================== */
(function () {
  "use strict";

  /** return-url 处理：登录页自身不能作为回跳目标，否则会死循环 */
  function resolveReturnUrl() {
    var stored = util.store.get("return-url");
    if (stored && String(stored).indexOf("login.html") === -1) {
      return stored;
    }
    var ref = document.referrer || "";
    if (ref && ref.indexOf(location.origin) === 0 && ref.indexOf("login.html") === -1) {
      util.store.set("return-url", ref);
      return ref;
    }
    util.store.set("return-url", "/");
    return "/";
  }

  /** 登录成功统一收尾 */
  function persistLogin(vo) {
    if (!vo || !vo.token) {
      throw new Error("登录响应缺少 token 字段");
    }
    sessionStorage.setItem("token", vo.token);
    // 只把非 token 字段写入 user-info，与旧版行为保持一致
    var user = {};
    Object.keys(vo).forEach(function (k) {
      if (k !== "token") {
        user[k] = vo[k];
      }
    });
    util.store.set("user-info", user);
  }

  new Vue({
    el: "#loginApp",

    data: function () {
      return {
        mode: "password", // password | sms
        currentUser: null, // 已登录用户（仅用于提示，不自动跳转）
        loading: false,
        sendingCode: false,
        captchaLoading: false,
        countdown: 0,
        countdownTimer: null,
        showPwd: false,
        error: "",
        fieldError: {},
        form: {
          username: "",
          password: "",
          rememberMe: true
        },
        smsForm: {
          phone: "",
          captchaId: "",
          captchaCode: "",
          code: ""
        },
        captchaImage: ""
      };
    },

    created: function () {
      this.returnUrl = resolveReturnUrl();
      this.currentUser = util.store.get("user-info");
      // 默认聚焦用户名，减少一次点击
      var self = this;
      this.$nextTick(function () {
        if (self.$refs.username) {
          self.$refs.username.focus();
        }
      });
    },

    beforeDestroy: function () {
      if (this.countdownTimer) {
        clearInterval(this.countdownTimer);
      }
    },

    methods: {
      /* ------------------------- 通用 ------------------------- */
      clearError: function () {
        this.error = "";
        this.fieldError = {};
      },

      switchMode: function (mode) {
        if (this.mode === mode) {
          return;
        }
        this.mode = mode;
        this.clearError();
        // 进入短信模式时按需拉取图形验证码
        if (mode === "sms" && !this.captchaImage) {
          this.loadCaptcha();
        }
      },

      continueAs: function () {
        window.location.href = this.returnUrl || "/";
      },

      /* ---------------------- 账号密码登录 ---------------------- */
      loginByPassword: function () {
        this.clearError();

        var fe = {};
        if (!this.form.username) {
          fe.username = true;
        }
        if (!this.form.password) {
          fe.password = true;
        }
        if (Object.keys(fe).length) {
          this.fieldError = fe;
          this.error = "请填写用户名和密码";
          return;
        }

        var self = this;
        this.loading = true;

        axios.post("/users/login", {
          username: this.form.username,
          password: this.form.password,
          rememberMe: !!this.form.rememberMe
        })
          .then(function (vo) {
            persistLogin(vo);
            hmToast("登录成功，正在前往…", "success", 1400);
            setTimeout(function () {
              window.location.href = self.returnUrl || "/";
            }, 420);
          })
          .catch(function (err) {
            self.loading = false;
            // 401/400 通常是账号密码错误，给出明确文案；其余交给统一错误翻译
            var status = err && err.response && err.response.status;
            self.error = (status === 401 || status === 400)
              ? "用户名或密码错误，请重新输入"
              : hmErrorText(err, "登录失败，请稍后重试");
          });
      },

      /* ---------------------- 图形验证码 ---------------------- */
      loadCaptcha: function () {
        var self = this;
        this.captchaLoading = true;
        axios.get("/users/captcha")
          .then(function (vo) {
            if (!vo || !vo.captchaId) {
              self.captchaImage = "";
              return;
            }
            self.smsForm.captchaId = vo.captchaId;
            // image 可能是 base64 裸串，也可能是完整 data-url，两种都兼容
            var img = vo.image || "";
            if (img && img.indexOf("data:") !== 0) {
              img = "data:image/png;base64," + img;
            }
            self.captchaImage = img;
            self.smsForm.captchaCode = "";
          })
          .catch(function (err) {
            self.captchaImage = "";
            self.error = hmErrorText(err, "图形验证码加载失败，请点击重试");
          })
          .then(function () {
            self.captchaLoading = false;
          });
      },

      /* ---------------------- 发送短信验证码 ---------------------- */
      sendSmsCode: function () {
        this.clearError();

        if (!/^1[3-9]\d{9}$/.test(this.smsForm.phone)) {
          this.fieldError = { phone: true };
          this.error = "请输入正确的 11 位手机号";
          return;
        }
        if (!this.smsForm.captchaId) {
          this.error = "图形验证码未加载，请点击图片重试";
          this.loadCaptcha();
          return;
        }
        if (!this.smsForm.captchaCode) {
          this.fieldError = { captchaCode: true };
          this.error = "请输入图形验证码";
          return;
        }

        var self = this;
        this.sendingCode = true;

        axios.post("/users/code", {
          phone: this.smsForm.phone,
          captchaId: this.smsForm.captchaId,
          captchaCode: this.smsForm.captchaCode
        })
          .then(function () {
            hmToast("验证码已发送，请查看手机短信", "success");
            self.startCountdown(60);
          })
          .catch(function (err) {
            self.error = hmErrorText(err, "验证码发送失败，请稍后重试");
            // 验证码一次性使用，失败后必须换一张
            self.loadCaptcha();
          })
          .then(function () {
            self.sendingCode = false;
          });
      },

      startCountdown: function (seconds) {
        var self = this;
        this.countdown = seconds;
        if (this.countdownTimer) {
          clearInterval(this.countdownTimer);
        }
        this.countdownTimer = setInterval(function () {
          self.countdown -= 1;
          if (self.countdown <= 0) {
            clearInterval(self.countdownTimer);
            self.countdownTimer = null;
            self.countdown = 0;
          }
        }, 1000);
      },

      /* ---------------------- 短信登录 ---------------------- */
      loginBySms: function () {
        this.clearError();

        var fe = {};
        if (!/^1[3-9]\d{9}$/.test(this.smsForm.phone)) {
          fe.phone = true;
        }
        if (!this.smsForm.code) {
          fe.code = true;
        }
        if (Object.keys(fe).length) {
          this.fieldError = fe;
          this.error = "请填写正确的手机号与短信验证码";
          return;
        }

        var self = this;
        this.loading = true;

        axios.post("/users/login/sms", {
          phone: this.smsForm.phone,
          code: this.smsForm.code
        })
          .then(function (vo) {
            persistLogin(vo);
            hmToast("登录成功，正在前往…", "success", 1400);
            setTimeout(function () {
              window.location.href = self.returnUrl || "/";
            }, 420);
          })
          .catch(function (err) {
            self.loading = false;
            var status = err && err.response && err.response.status;
            self.error = (status === 401 || status === 400)
              ? "验证码错误或已过期，请重新获取"
              : hmErrorText(err, "登录失败，请稍后重试");
          });
      }
    }
  });
})();
