import { defineComponent, h } from 'vue'
import { RouterView } from 'vue-router'

export const RouteView = defineComponent({
  name: 'RouteView',
  setup() {
    // 直接使用 RouterView 组件本体；如果用字符串 tag，可能被当作自定义元素，导致嵌套路由渲染异常。
    return () => h(RouterView)
  }
})
