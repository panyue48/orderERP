import { defineComponent, h } from 'vue'
import { RouterView } from 'vue-router'

export const RouteView = defineComponent({
  name: 'RouteView',
  setup() {
    // Use the actual RouterView component; using a string tag can turn into a custom element
    // and break nested route rendering.
    return () => h(RouterView)
  }
})
