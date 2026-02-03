import pluginVue from 'eslint-plugin-vue'
import eslintConfigPrettier from 'eslint-config-prettier'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'

export default defineConfigWithVueTs(
  {
    ignores: ['dist/**', 'src/components.d.ts']
  },
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,
  eslintConfigPrettier,
  {
    rules: {
      'vue/multi-word-component-names': 'off'
    }
  }
)
