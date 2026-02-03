# chunksize 过大告警的成因与优化记录

## 现象

构建时出现提示：
- `Some chunks are larger than 500 kB after minification`
- 之前产物里单个入口文件（如 `index-*.js`）体积明显偏大

这个提示不是错误，但它意味着：首屏可能需要下载/解析一个很大的 JS 文件，尤其在弱网或低端设备上会变慢。

## 成因（为什么会出现“大 chunk”）

在 Vite/Rollup 的默认策略下，如果你把一些依赖“集中”在入口处一次性导入，就容易形成一个很大的 chunk：

1. **UI 组件库整库引入**
   - 例如 `app.use(ElementPlus)` + `import 'element-plus/dist/index.css'`
   - 会让 Element Plus 的大量组件逻辑进入同一个（或少数几个）chunk
2. **路由页面同步导入**
   - 路由里 `import Dashboard from ...`、`import Ledger from ...`
   - 会让所有页面代码在首屏一起被打进入口 chunk
3. **第三方依赖没有明确拆分**
   - 依赖混在一个大 vendor chunk 里，入口 chunk 也会被放大，且缓存粒度不够细

## 我做了哪些优化（本仓库已落地）

### 1) 路由懒加载（把页面拆成独立 chunk）

关键修改：把页面组件从同步 import 改成动态 import。

代码位置：[router](file:///c:/traewrokplace/orderERP/frontend/src/router/index.ts)

效果：
- `Dashboard.vue`、`Ledger.vue` 变成独立的 `Dashboard-*.js`、`Ledger-*.js`
- 首屏入口 chunk 明显变小

### 2) 手动分包（把第三方依赖拆成稳定 vendor chunks）

关键修改：在 `build.rollupOptions.output.manualChunks` 中按依赖名拆分。

代码位置：[vite.config.ts](file:///c:/traewrokplace/orderERP/frontend/vite.config.ts)

效果：
- `axios`、`vue-router`、`pinia`、`vendor` 等拆成独立文件
- 浏览器缓存更友好：小版本改动通常不会导致所有依赖 chunk 都失效

### 3) Element Plus 按需引入（避免整库进入单一大 chunk）

关键修改：
- 移除 `main.ts` 里对 Element Plus 的整库安装
- 使用 `unplugin-vue-components` + `ElementPlusResolver` 自动按需导入组件与样式

代码位置：
- [main.ts](file:///c:/traewrokplace/orderERP/frontend/src/main.ts)
- [vite.config.ts](file:///c:/traewrokplace/orderERP/frontend/vite.config.ts)

效果（本次构建结果）：
- `element-plus-*.js` 降到约 **351KB**（gzip 约 **109KB**）
- 之前 >500KB 的告警已消失

## 验证方式（你也可以复现）

在 `frontend/` 下执行：

```bash
npm run build
```

关注输出中每个 chunk 的大小，以及是否还有 `> 500 kB` 的告警。

## 下一步可进一步提升性能的方向（可选）

1. 首屏渲染优化：把首屏不必要的组件拆成异步组件（`defineAsyncComponent`）
2. 表格类页面优化：大列表用分页/虚拟滚动（Element Plus 表格 + 虚拟列表方案）
3. 资源缓存策略：生产环境开启 gzip/br，合理设置静态资源缓存头
4. 依赖优化：只引入实际需要的 icon/日期库，避免大而全依赖

