// 设置后台服务地址
axios.defaults.baseURL = "/api";
axios.defaults.timeout = 2000;
// request拦截器
axios.interceptors.request.use(
  config => {
    config.headers['authorization'] = sessionStorage.getItem("token")
    return config
  }
)
// 响应拦截器
axios.interceptors.response.use(
  response => response.data,
  err => {
    // err.response 在「超时 / 断网 / 代理失败」时为 undefined，
    // 直接取 .status 会抛 TypeError 并吃掉真实错误信息，这里必须先判空。
    if (err.response && err.response.status === 401) {
      // 认证失败，需要重新登录
	  sessionStorage.removeItem("user-info")
      sessionStorage.removeItem("token")
	  // 记录来源页，登录后可自动跳回（必须用 util.store 写入，保证与读取端 JSON 格式一致）
	  if (location.pathname.indexOf("login.html") === -1) {
		util.store.set("return-url", location.href)
	  }
      location.href = "/login.html";
    }
    return Promise.reject(err)
  }
)
const util = {
  isLogin(){
    return !!sessionStorage.getItem("user-info");
  },
  logout(){
    sessionStorage.removeItem("user-info")
    sessionStorage.removeItem("token")
    location.reload();
  },
  getUrlParam(name) {
    let reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)", "i");
    let r = window.location.search.substr(1).match(reg);
    if (r != null) {
      return decodeURI(r[2]);
    }
    return "";
  },
  formatPrice(val) {
    if (typeof val === 'string') {
      if (isNaN(val)) {
        return null;
      }
      // 价格转为整数
      const index = val.lastIndexOf(".");
      let p = "";
      if (index < 0) {
        // 无小数
        p = val + "00";
      } else if (index === p.length - 2) {
        // 1位小数
        p = val.replace("\.", "") + "0";
      } else {
        // 2位小数
        p = val.replace("\.", "")
      }
      return parseInt(p);
    } else if (typeof val === 'number') {
      if (val == null) {
        return null;
      }
      const s = val + '';
      if (s.length === 0) {
        return "0.00";
      }
      if (s.length === 1) {
        return "0.0" + val;
      }
      if (s.length === 2) {
        return "0." + val;
      }
      const i = s.indexOf(".");
      if (i < 0) {
        return s.substring(0, s.length - 2) + "." + s.substring(s.length - 2)
      }
      const num = s.substring(0, i) + s.substring(i + 1);
      if (i === 1) {
        // 1位整数
        return "0.0" + num;
      }
      if (i === 2) {
        return "0." + num;
      }
      if (i > 2) {
        return num.substring(0, i - 2) + "." + num.substring(i - 2)
      }
    }
  },
  store: {
    set(key, obj) {
      sessionStorage.setItem(key, JSON.stringify(obj))
    },
    get(key) {
      return JSON.parse(sessionStorage.getItem(key))
    },
    del(key) {
      sessionStorage.removeItem(key)
    }
  }
}
